package fr.bosketball.ui;

import fr.bosketball.game.GameManager;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import fr.bosketball.model.TeamSide;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Clean, Non-Overlapping Server [TAB] List Manager (Cytooxien-style):
 *   - Top Crowned BOSKETBALL Logo with proper spacing (zero overlap!)
 *   - Live Online / Match / Queue status bar
 *   - Crisp single-head player rows (native Minecraft head + Rank + Active Tag + Name + ELO/PTS)
 *   - Clean server footer with Rank, ELO, Coins & Discord!
 */
public final class TabListManager {

    private final StatsManager statsManager;
    private final String seasonName;
    private final String serverDomain;

    public TabListManager(StatsManager statsManager, String seasonName, String serverDomain) {
        this.statsManager = statsManager;
        this.seasonName = seasonName;
        this.serverDomain = serverDomain;
    }

    public void updateAll(GameManager gameManager) {
        int online = Bukkit.getOnlinePlayers().size();
        int inMatch = gameManager.activePlayerCount();
        int inQueue = gameManager.queuedCount();

        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerProfile prof = statsManager.getOrCreate(p);
            MatchSession mySession = gameManager.getSessionOf(p.getUniqueId());
            int ping = p.getPing();

            TextComponent.Builder hb = Component.text();
            hb.append(Component.newline());
            for (char strip : Glyphs.CHAT_LOGO_STRIPS) {
                hb.append(Glyphs.bitmap(strip)).append(Component.newline());
            }
            hb.append(Component.newline());

            if (mySession != null) {
                hb.append(Msg.parse(
                    "<#FF6B4A><bold>IGNIS " + mySession.score(TeamSide.IGNIS) + "</bold></#FF6B4A>"
                        + "  <dark_gray>—</dark_gray>  "
                        + "<#38BDF8><bold>" + mySession.score(TeamSide.FROST) + " FROST</bold></#38BDF8>"
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
                "<gray>Rank:</gray> <!shadow><white>" + prof.effectiveRank().pillGlyph() + "</white></!shadow>"
                    + "  <dark_gray>•</dark_gray>  <#FBBF24><bold>" + prof.elo() + " ELO</bold></#FBBF24>"
                    + "  <dark_gray>•</dark_gray>  <!shadow><white>" + Glyphs.CHAT_ICON_COIN + "</white></!shadow> <#FDE047><bold>" + prof.coins() + " Coins</bold></#FDE047>"
                    + "  <dark_gray>•</dark_gray>  <#94A3B8>" + ping + "ms</#94A3B8>\n"
                    + "<#FF8C1A><bold>" + serverDomain.toUpperCase() + "</bold></#FF8C1A>"
                    + "  <dark_gray>•</dark_gray>  <#818CF8>/discord</#818CF8>\n"
            ));

            p.sendPlayerListHeaderAndFooter(hb.asComponent(), fb.asComponent());
            p.playerListName(buildTabEntry(p, prof, mySession));
        }
    }

    private Component buildTabEntry(Player player, PlayerProfile profile, MatchSession session) {
        TextComponent.Builder row = Component.text();
        String gap = Glyphs.shift(2);

        if (session != null) {
            TeamSide side = session.getTeam(player.getUniqueId());
            if (side != TeamSide.SPECTATOR) {
                row.append(side.pillComponent()).append(Component.text(gap, Glyphs.BITMAP_STYLE));
            }
            if (profile.selectedTag() != null) {
                row.append(profile.selectedTag().pillComponent()).append(Component.text(gap, Glyphs.BITMAP_STYLE));
            }
            int matchPts = session.getBoxScore(player.getUniqueId()).points;
            row.append(Component.text(" " + player.getName(), side.textColor()));
            row.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
            row.append(Component.text(matchPts + " PTS", Style.style(TextColor.color(0xFDE047), TextDecoration.BOLD)));
            return row.asComponent();
        }

        Rank rank = profile.effectiveRank();
        row.append(rank.pillComponent());

        StatsManager.LeaderboardBadges badges = statsManager.getBadges(player.getUniqueId());
        if (badges.eloPlace() == 1) {
            row.append(Component.text(gap, Glyphs.BITMAP_STYLE))
                .append(Glyphs.topBadgePill(Glyphs.TopBadgeKind.ELO, 1));
        }
        if (profile.selectedTag() != null) {
            row.append(Component.text(gap, Glyphs.BITMAP_STYLE))
                .append(profile.selectedTag().pillComponent());
        }

        row.append(Component.text(" " + player.getName(), TextColor.color(0xF1F5F9)));
        row.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
        row.append(Component.text(profile.elo() + " ELO", TextColor.color(0xFBBF24)));
        return row.asComponent();
    }
}
