package fr.bosketball.ui;

import fr.bosketball.game.GameManager;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import fr.bosketball.model.TeamSide;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    /**
     * Round 34 — why the rows swapped between two players every second.
     *
     * <p>Every tab-list entry carries a {@code list order} (Minecraft 1.21.2+), exposed by Paper as
     * {@link Player#setPlayerListOrder(int)}. Everybody defaulted to {@code 0}, i.e. every row was
     * tied with every other one, and the client is then free to lay tied rows out in whatever order
     * its internal map hands them over — exactly what the owner saw: two nicknames trading places
     * twice a second. We now assign one explicit, total order and only push the number when it
     * really changed.
     */
    private final Map<UUID, Integer> lastListOrder = new ConcurrentHashMap<>();

    public TabListManager(StatsManager statsManager, String seasonName, String serverDomain) {
        this.statsManager = statsManager;
        this.seasonName = seasonName;
        this.serverDomain = serverDomain;
    }

    public void clearPlayerCache(UUID playerId) {
        if (playerId != null) {
            lastHeaderFooterSig.remove(playerId);
            lastEntrySig.remove(playerId);
            lastListOrder.remove(playerId);
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

    private java.util.function.Supplier<String> seasonFooterProvider;

    private java.util.function.Function<UUID, String> seasonRankLabelProvider;

    public void attachSeasonProvider(
        java.util.function.Supplier<String> footerProvider,
        java.util.function.Function<UUID, String> rankLabelProvider
    ) {
        this.seasonFooterProvider = footerProvider;
        this.seasonRankLabelProvider = rankLabelProvider;
        this.lastHeaderFooterSig.clear();
    }

    private String seasonFooter() {
        if (seasonFooterProvider == null) {
            return seasonName;
        }
        try {
            String label = seasonFooterProvider.get();
            return label == null || label.isBlank() ? seasonName : label;
        } catch (RuntimeException ex) {
            return seasonName;
        }
    }

    private String seasonRankLabel(UUID id) {
        if (seasonRankLabelProvider == null) {
            return null;
        }
        try {
            return seasonRankLabelProvider.apply(id);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Component parseMultiline(String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) {
            return Component.empty();
        }
        String normalized = fr.bosketball.util.Glyphs.smallCapsLabels(miniMessage).replace("\\n", "\n");
        if (normalized.indexOf('\n') < 0) {
            return Msg.parse(normalized);
        }
        java.util.List<Component> parts = new java.util.ArrayList<>();
        String[] chunks = normalized.split("\n", -1);
        for (int i = 0; i < chunks.length; i++) {
            if (i > 0) {
                parts.add(Component.newline());
            }
            if (!chunks[i].isEmpty()) {
                parts.add(Msg.parse(chunks[i]));
            }
        }
        return Component.join(net.kyori.adventure.text.JoinConfiguration.noSeparators(), parts);
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

            int tabRows = Math.max(1, Math.min(20, online));
            String hfSig;
            if (mySession != null) {
                hfSig = "M|" + tabRows + "|" + mySession.teamDisplayName(TeamSide.IGNIS) + "|" + mySession.score(TeamSide.IGNIS)
                    + "|" + mySession.score(TeamSide.FROST) + "|" + mySession.teamDisplayName(TeamSide.FROST)
                    + "|" + mySession.formattedClock() + "|" + effRank.ordinal() + "|" + prof.elo() + "|" + prof.coins() + "|" + pingBucket;
            } else {
                hfSig = "L|" + online + "|" + tabRows + "|" + inMatch + "|" + inQueue
                    + "|" + effRank.ordinal() + "|" + prof.elo() + "|" + prof.coins() + "|" + pingBucket;
            }

            if (!hfSig.equals(lastHeaderFooterSig.get(pid))) {
                lastHeaderFooterSig.put(pid, hfSig);
                // The owner's tab frame is drawn by the header's first line. Round 29 fixed the
                // assumption that had been made here: the client paints header -> player rows ->
                // footer, so the frame is *behind* the names (the owner's nick stays #F1F5F9, a 25%
                // veil would have dimmed it). The frame texture is now opaque, which is only safe
                // because of that order: nothing of the frame ever covers a name.
                TextComponent.Builder hb = Component.text()
                    .append(Glyphs.tabFrameComponent(tabRows))
                    .append(STATIC_LOGO_BLOCK);
                if (mySession != null) {
                    String ignisName = mySession.teamDisplayName(TeamSide.IGNIS).toUpperCase(java.util.Locale.ROOT);
                    String frostName = mySession.teamDisplayName(TeamSide.FROST).toUpperCase(java.util.Locale.ROOT);
                    hb.append(parseMultiline(
                        Msg.gradBold("#FF6B4A:#FFB088", ignisName + " " + mySession.score(TeamSide.IGNIS))
                            + "  <dark_gray>—</dark_gray>  "
                            + "<#38BDF8><bold>" + mySession.score(TeamSide.FROST) + " " + frostName + "</bold></#38BDF8>"
                            + "  <dark_gray>•</dark_gray>  <white>" + mySession.formattedClock() + "</white>\n"
                    ));
                } else {
                    hb.append(parseMultiline(
                        "<#4ADE80>●</#4ADE80> <gray>Online:</gray> <white><bold>" + online + "</bold></white>"
                            + "   <dark_gray>•</dark_gray>   "
                            + "<#FBBF24>🏀</#FBBF24> <gray>In Match:</gray> <white><bold>" + inMatch + "</bold></white>"
                            + "   <dark_gray>•</dark_gray>   "
                            + "<#38BDF8>⏳</#38BDF8> <gray>Queue:</gray> <white><bold>" + inQueue + "</bold></white>\n"
                    ));
                }

                TextComponent.Builder fb = Component.text();
                String seasonRankLabel = seasonRankLabel(pid);
                String rankSegment = seasonRankLabel != null
                    ? seasonRankLabel
                    : "<#FBBF24><bold>" + prof.elo() + " ELO</bold></#FBBF24>";
                fb.append(Component.newline());
                fb.append(parseMultiline(
                    "<gray>Rank:</gray> <!shadow><white>" + effRank.pillGlyph() + "</white></!shadow>"
                        + "  <dark_gray>•</dark_gray>  " + rankSegment
                        + "  <dark_gray>•</dark_gray>  <!shadow><white>" + Glyphs.CHAT_ICON_COIN + "</white></!shadow> <#FDE047><bold>" + prof.coins() + " Coins</bold></#FDE047>"
                        + "  <dark_gray>•</dark_gray>  <#94A3B8>" + pingDisplay + "ms</#94A3B8>\n"
                        + Msg.gradBold(Msg.GRAD_BRAND, seasonFooter())
                        + "  <dark_gray>•</dark_gray>  <white>" + serverDomain + "</white>"
                        + "  <dark_gray>•</dark_gray>  <#818CF8>/season</#818CF8>\n"
                ));
                // Round 32: two empty trailing lines reserve the room for the frame's bottom rail
                // and its crest. Without them the last footer line sat *on* the rail — one of the
                // two reasons the panel looked like it was missing a piece. The frame height is
                // derived from the very same line count (15 lines = 135 px for one player).
                fb.append(Component.newline());
                fb.append(Component.newline());
                p.sendPlayerListHeaderAndFooter(hb.asComponent(), fb.asComponent());
            }

            StatsManager.LeaderboardBadges badges = statsManager.getBadges(pid);
            fr.bosketball.model.ChatTag tag = prof.selectedTag();
            boolean tagAllowed = tag != null && tag.canBeUsedBy(prof, p.getName(),
                fr.bosketball.util.Perms.has(p, fr.bosketball.util.Perms.TAGS_ALL),
                fr.bosketball.util.Perms.has(p, fr.bosketball.util.Perms.STAFF));
            String entryKey = (mySession == null ? "L" : ("M:" + mySession.getTeam(pid) + ":" + mySession.getBoxScore(pid).points))
                + "|" + effRank.ordinal() + "|" + badges.eloPlace() + "|" + (tagAllowed ? tag.ordinal() : -1)
                + "|" + p.getName() + "|" + prof.elo() + "|" + presence.ordinal();
            if (!entryKey.equals(lastEntrySig.get(pid))) {
                lastEntrySig.put(pid, entryKey);
                p.playerListName(buildTabEntry(p, prof, mySession, badges, tag, tagAllowed));
            }
        }
        enforceTabOrder(gameManager);
    }

    /** Applies that total order to the player list (one packet per row that actually moved). */
    private void enforceTabOrder(GameManager gameManager) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }
        if (online.size() > 1) {
            online.sort(tabComparator(gameManager));
        }
        for (int i = 0; i < online.size(); i++) {
            Player p = online.get(i);
            Integer previous = lastListOrder.get(p.getUniqueId());
            if (previous != null && previous == i) {
                continue;
            }
            lastListOrder.put(p.getUniqueId(), i);
            p.setPlayerListOrder(i);
        }
    }

    /** Total order: no tie is ever possible, so the list can never oscillate between two layouts. */
    private Comparator<Player> tabComparator(GameManager gameManager) {
        return (a, b) -> {
            MatchSession sa = gameManager.getSessionOf(a.getUniqueId());
            MatchSession sb = gameManager.getSessionOf(b.getUniqueId());
            int rankA = sa == null ? 2 : teamRank(sa.getTeam(a.getUniqueId()));
            int rankB = sb == null ? 2 : teamRank(sb.getTeam(b.getUniqueId()));
            if (rankA != rankB) {
                return Integer.compare(rankA, rankB);
            }
            if (sa != null && sb != null) {
                int ptsA = sa.getBoxScore(a.getUniqueId()).points;
                int ptsB = sb.getBoxScore(b.getUniqueId()).points;
                if (ptsA != ptsB) {
                    return Integer.compare(ptsB, ptsA);
                }
            }
            int byName = a.getName().compareToIgnoreCase(b.getName());
            if (byName != 0) {
                return byName;
            }
            return a.getUniqueId().toString().compareTo(b.getUniqueId().toString());
        };
    }

    private static int teamRank(TeamSide side) {
        if (side == TeamSide.IGNIS) {
            return 0;
        }
        if (side == TeamSide.FROST) {
            return 1;
        }
        return 2;
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
