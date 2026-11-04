package fr.bosketball.ui;

import fr.bosketball.game.GameManager;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import fr.bosketball.model.TeamSide;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class TabListManager {

    private static final Component STATIC_LOGO_BLOCK = buildStaticLogoBlock();

    private static Component buildStaticLogoBlock() {
        TextComponent.Builder b = Component.text();
        b.append(Component.newline());
        for (int i = 0; i < Glyphs.CHAT_LOGO_STRIPS.length; i++) {
            b.append(Glyphs.logoStripComponent(i)).append(Component.newline());
        }
        b.append(Component.newline());
        return b.asComponent();
    }

    private final StatsManager statsManager;
    private final String seasonName;
    private final String serverDomain;
    private final Map<UUID, String> lastHeaderFooterSig = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastEntrySig = new ConcurrentHashMap<>();

    public TabListManager(StatsManager statsManager, String seasonName, String serverDomain) {
        this.statsManager = statsManager;
        this.seasonName = seasonName;
        this.serverDomain = serverDomain;
    }

    public void clearPlayerCache(UUID playerId) {
        if (playerId != null) {
            lastHeaderFooterSig.remove(playerId);
            lastEntrySig.remove(playerId);
        }
    }

    private java.util.function.Function<UUID, fr.bosketball.model.PlayerStatus> presenceProvider;

    private java.util.function.Function<UUID, fr.bosketball.social.Guild> guildProvider;

    public void attachGuildProvider(java.util.function.Function<UUID, fr.bosketball.social.Guild> provider) {
        this.guildProvider = provider;
        this.lastEntrySig.clear();
    }

    private fr.bosketball.social.Guild guildOf(Player p) {
        if (guildProvider == null) {
            return null;
        }
        try {
            return guildProvider.apply(p.getUniqueId());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public void attachPresenceProvider(java.util.function.Function<UUID, fr.bosketball.model.PlayerStatus> provider) {
        this.presenceProvider = provider;
    }

    private fr.bosketball.model.PlayerStatus statusOf(Player p) {
        if (presenceProvider == null) {
            return fr.bosketball.model.PlayerStatus.LOBBY;
        }
        try {
            fr.bosketball.model.PlayerStatus st = presenceProvider.apply(p.getUniqueId());
            return st == null ? fr.bosketball.model.PlayerStatus.LOBBY : st;
        } catch (RuntimeException ex) {
            return fr.bosketball.model.PlayerStatus.LOBBY;
        }
    }

    public void updateAll(GameManager gameManager) {
        int online = Bukkit.getOnlinePlayers().size();
        int inMatch = gameManager.activePlayerCount();
        int inQueue = gameManager.queuedCount();

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID pid = p.getUniqueId();
            PlayerProfile prof = statsManager.getOrCreate(p);
            MatchSession mySession = gameManager.getSessionOf(pid);

            int pingDisplay = p.getPing();
            int pingBucket = (pingDisplay / 5) * 5;
            Rank effRank = prof.effectiveRank();
            fr.bosketball.model.PlayerStatus presence = statusOf(p);

            String hfSig;
            if (mySession != null) {
                hfSig = "M|" + mySession.teamDisplayName(TeamSide.IGNIS) + "|" + mySession.score(TeamSide.IGNIS)
                    + "|" + mySession.score(TeamSide.FROST) + "|" + mySession.teamDisplayName(TeamSide.FROST)
                    + "|" + mySession.formattedClock() + "|" + effRank.ordinal() + "|" + prof.elo() + "|" + prof.coins() + "|" + pingBucket;
            } else {
                hfSig = "L|" + online + "|" + inMatch + "|" + inQueue
                    + "|" + effRank.ordinal() + "|" + prof.elo() + "|" + prof.coins() + "|" + pingBucket;
            }

            if (!hfSig.equals(lastHeaderFooterSig.get(pid))) {
                lastHeaderFooterSig.put(pid, hfSig);
                TextComponent.Builder hb = Component.text().append(STATIC_LOGO_BLOCK);
                if (mySession != null) {
                    String ignisName = mySession.teamDisplayName(TeamSide.IGNIS).toUpperCase(java.util.Locale.ROOT);
                    String frostName = mySession.teamDisplayName(TeamSide.FROST).toUpperCase(java.util.Locale.ROOT);
                    hb.append(Msg.parse(
                        "<#FF6B4A><bold>" + ignisName + " " + mySession.score(TeamSide.IGNIS) + "</bold></#FF6B4A>"
                            + "  <dark_gray>—</dark_gray>  "
                            + "<#38BDF8><bold>" + mySession.score(TeamSide.FROST) + " " + frostName + "</bold></#38BDF8>"
                            + "  <dark_gray>•</dark_gray>  <white>" + mySession.formattedClock() + "</white>\n"
                    ));
                } else {
                    hb.append(Msg.parse(
                        "<#4ADE80>●</#4ADE80> <gray>Online:</gray> <white><bold>" + online + "</bold></white>"
                            + "   <dark_gray>•</dark_gray>   "
                            + "<#FBBF24>🏀</#FBBF24> <gray>In Match:</gray> <white><bold>" + inMatch + "</bold></white>"
                            + "   <dark_gray>•</dark_gray>   "
                            + "<#38BDF8>⏳</#38BDF8> <gray>Queue:</gray> <white><bold>" + inQueue + "</bold></white>\n"
                    ));
                }

                TextComponent.Builder fb = Component.text();
                fb.append(Component.newline());
                fb.append(Msg.parse(
                    "<gray>Rank:</gray> <!shadow><white>" + effRank.pillGlyph() + "</white></!shadow>"
                        + "  <dark_gray>•</dark_gray>  <#FBBF24><bold>" + prof.elo() + " ELO</bold></#FBBF24>"
                        + "  <dark_gray>•</dark_gray>  <!shadow><white>" + Glyphs.CHAT_ICON_COIN + "</white></!shadow> <#FDE047><bold>" + prof.coins() + " Coins</bold></#FDE047>"
                        + "  <dark_gray>•</dark_gray>  <#94A3B8>" + pingDisplay + "ms</#94A3B8>\n"
                        + "<#FF8C1A><bold>" + serverDomain + "</bold></#FF8C1A>"
                        + "  <dark_gray>•</dark_gray>  <#818CF8>/discord</#818CF8>\n"
                ));
                p.sendPlayerListHeaderAndFooter(hb.asComponent(), fb.asComponent());
            }

            StatsManager.LeaderboardBadges badges = statsManager.getBadges(pid);
            fr.bosketball.model.ChatTag tag = prof.selectedTag();
            boolean tagAllowed = tag != null && tag.canBeUsedBy(prof, p.getName(), p.hasPermission("bosketball.tags.all"));
            String entryKey = (mySession == null ? "L" : ("M:" + mySession.getTeam(pid) + ":" + mySession.getBoxScore(pid).points))
                + "|" + effRank.ordinal() + "|" + badges.eloPlace() + "|" + (tagAllowed ? tag.ordinal() : -1)
                + "|" + p.getName() + "|" + prof.elo() + "|" + presence.ordinal();
            if (!entryKey.equals(lastEntrySig.get(pid))) {
                lastEntrySig.put(pid, entryKey);
                p.playerListName(buildTabEntry(p, prof, mySession, badges, tag, tagAllowed));
            }
        }
    }

    private Component buildTabEntry(
        Player player,
        PlayerProfile profile,
        MatchSession session,
        StatsManager.LeaderboardBadges badges,
        fr.bosketball.model.ChatTag tag,
        boolean tagAllowed
    ) {
        TextComponent.Builder row = Component.text();
        String gap = Glyphs.shift(2);

        if (session != null) {
            TeamSide side = session.getTeam(player.getUniqueId());
            if (side != TeamSide.SPECTATOR) {
                row.append(side.pillComponent()).append(Component.text(gap, Glyphs.BITMAP_STYLE));
            }
            if (badges.eloPlace() >= 1 && badges.eloPlace() <= 3) {
                row.append(Glyphs.topBadgePill(Glyphs.TopBadgeKind.ELO, badges.eloPlace()))
                    .append(Component.text(gap, Glyphs.BITMAP_STYLE));
            }
            if (tagAllowed) {
                row.append(tag.pillComponent()).append(Component.text(gap, Glyphs.BITMAP_STYLE));
            }
            if (side == TeamSide.SPECTATOR) {
                row.append(Component.text(" " + player.getName(), TextColor.color(0x94A3B8)));
                row.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
                row.append(Component.text("👁 SPECTATING", TextColor.color(0x38BDF8)));
                return row.asComponent();
            }
            int matchPts = session.getBoxScore(player.getUniqueId()).points;
            row.append(Component.text(" " + player.getName(), side.textColor()));
            row.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
            row.append(Component.text(matchPts + " PTS", Style.style(TextColor.color(0xFDE047), TextDecoration.BOLD)));
            return row.asComponent();
        }

        Rank rank = profile.effectiveRank();
        row.append(rank.pillComponent());

        if (badges.eloPlace() >= 1 && badges.eloPlace() <= 3) {
            row.append(Component.text(gap, Glyphs.BITMAP_STYLE))
                .append(Glyphs.topBadgePill(Glyphs.TopBadgeKind.ELO, badges.eloPlace()));
        }
        if (tagAllowed) {
            row.append(Component.text(gap, Glyphs.BITMAP_STYLE))
                .append(tag.pillComponent());
        }

        fr.bosketball.social.Guild guild = guildOf(player);
        Component nameComponent = Component.text(" " + player.getName(), TextColor.color(0xF1F5F9))
            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Msg.parse(
                "<#FF8C1A><bold>" + player.getName() + "</bold></#FF8C1A>\n"
                    + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                    + "<gray>Guild:</gray> " + (guild == null
                        ? "<dark_gray>None</dark_gray>"
                        : guild.displayName() + " " + guild.displayTag()
                            + " <dark_gray>(" + guild.elo() + " ELO)</dark_gray>") + "\n"
                    + "<gray>Rank:</gray> <white>" + rank.plainDisplayName() + "</white>\n"
                    + "<gray>ELO:</gray> <#FBBF24>" + profile.elo() + "</#FBBF24>\n"
                    + "<gray>Record:</gray> <#4ADE80>" + profile.wins() + "W</#4ADE80>"
                    + "<gray>/</gray><#F87171>" + profile.losses() + "L</#F87171>\n"
                    + "<#38BDF8>▸ Click to challenge</#38BDF8>")));
        row.append(nameComponent);
        row.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
        row.append(Component.text(profile.elo() + " ELO", TextColor.color(0xFBBF24)));

        boolean showStatus = true;
        var prefProfile = statsManager.getIfPresent(player.getUniqueId());
        if (prefProfile != null && prefProfile.settings() != null) {
            showStatus = prefProfile.settings().showStatusInTab();
        }
        fr.bosketball.model.PlayerStatus presence = showStatus
            ? statusOf(player)
            : fr.bosketball.model.PlayerStatus.LOBBY;
        if (presence != fr.bosketball.model.PlayerStatus.LOBBY) {
            row.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
            row.append(Component.text(presence.symbol() + " " + presence.smallCapsTitle(),
                TextColor.color(presence.colorRgb())));
        }
        return row.asComponent();
    }
}
