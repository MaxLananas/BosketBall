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
 * 100% Custom Center-Positioned Graphical [TAB] Scoreboard (Requirement #1):
 * Pushes the Tab overlay down to the vertical center of the screen and renders:
 *   - 5-Strip Comic BOSKETBALL Header + 224px Graphical Broadcast Header (IGNIS | BOSKETBALL TAB | FROST)
 *   - Smoked-Glass Team/Gold Row Cards with Real Signed 8x8 Inline Player Heads, Team Colors, Glossy Badges & Live Points
 *   - 224px Graphical Footer Card + Live Match/Lobby Telemetry!
 * Works hand-in-hand with CenterTabOverlayManager ([F] / /tab / /bb tab)!
 */
public final class TabListManager {

    private final StatsManager statsManager;
    private final String seasonName;
    private final String serverDomain;
    private int animPhase = 0;

    public TabListManager(StatsManager statsManager, String seasonName, String serverDomain) {
        this.statsManager = statsManager;
        this.seasonName = seasonName;
        this.serverDomain = serverDomain;
    }

    public void updateAll(GameManager gameManager) {
        animPhase = (animPhase + 1) % 3;
        String accent = switch (animPhase) {
            case 0 -> "<gradient:#FF7A18:#FFD43B>";
            case 1 -> "<gradient:#FFD43B:#52FFB8>";
            default -> "<gradient:#48EAFF:#FF7A18>";
        };

        int online = Bukkit.getOnlinePlayers().size();
        int inMatch = gameManager.activePlayerCount();
        int inQueue = gameManager.queuedCount();

        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerProfile prof = statsManager.getOrCreate(p);
            MatchSession mySession = gameManager.getSessionOf(p.getUniqueId());
            int ping = p.getPing();

            TextComponent.Builder hb = Component.text();
            // Vertically shift [TAB] straight into the middle of the screen!
            hb.append(Component.text("\n\n\n\n\n\n"));
            for (char strip : Glyphs.CHAT_LOGO_STRIPS) {
                hb.append(Glyphs.bitmap(strip)).append(Component.newline());
            }
            hb.append(Msg.parse(accent + "<bold>✦ " + seasonName + " ✦</bold></gradient>\n"));
            if (mySession != null) {
                hb.append(Glyphs.composeLiveScorebug(
                    "ɪɢɴɪꜱ", mySession.score(TeamSide.IGNIS),
                    mySession.formattedClock(), mySession.shotClockSeconds(),
                    mySession.score(TeamSide.FROST), "ꜰʀᴏꜱᴛ"
                )).append(Component.newline());
            } else {
                hb.append(Msg.parse(
                    "<gray>ᴇɴ ʟɪɢɴᴇ:</gray> <#52FFB8><bold>" + online + "</bold></#52FFB8>  <dark_gray>•</dark_gray>  "
                        + "<gray>ᴇɴ ᴍᴀᴛᴄʜ:</gray> <gold><bold>" + inMatch + "</bold></gold>  <dark_gray>•</dark_gray>  "
                        + "<gray>ꜰɪʟᴇ:</gray> <aqua><bold>" + inQueue + "</bold></aqua>\n"
                ));
            }
            hb.append(Glyphs.bitmap(Glyphs.TAB_HEADER_CARD));

            TextComponent.Builder fb = Component.text();
            fb.append(Glyphs.bitmap(Glyphs.TAB_FOOTER_CARD)).append(Component.newline());
            fb.append(Msg.parse(
                "<gray>ʀᴀɴɢ:</gray> <!shadow><white>" + prof.effectiveRank().pillGlyph() + "</white></!shadow> "
                    + "<yellow><bold>" + prof.elo() + " ELO</bold></yellow> <dark_gray>•</dark_gray> "
                    + "<gold><bold>" + prof.points() + " PTS</bold></gold> <dark_gray>•</dark_gray> "
                    + "<aqua>" + ping + "ms</aqua>\n"
                    + "<gradient:#FF9A28:#FFE259><bold>" + serverDomain + "</bold></gradient>"
                    + " <dark_gray>•</dark_gray> <gray>[F] ᴏᴜ /ᴛᴀʙ : ᴛᴀʙ 3ᴅ ꜰʟᴏᴛᴛᴀɴᴛ</gray>\n"
            ));

            p.sendPlayerListHeaderAndFooter(hb.asComponent(), fb.asComponent());
            p.playerListName(buildTabEntry(p, prof, mySession));
        }
    }

    private Component buildTabEntry(Player player, PlayerProfile profile, MatchSession session) {
        TextComponent.Builder row = Component.text();
        // Real 8x8 Signed Inline Player Head!
        row.append(CenterTabOverlayManager.buildInlinePlayerHead(player)).append(Component.text(" "));

        StatsManager.LeaderboardBadges badges = statsManager.getBadges(player.getUniqueId());
        if (badges.winsPlace() == 1) {
            row.append(Glyphs.topBadgePill(Glyphs.TopBadgeKind.WINS, 1)).append(Component.text(" "));
        } else if (badges.eloPlace() == 1) {
            row.append(Glyphs.topBadgePill(Glyphs.TopBadgeKind.ELO, 1)).append(Component.text(" "));
        }

        if (profile.selectedTag() != null) {
            row.append(profile.selectedTag().pillComponent()).append(Component.text(" "));
        }

        if (session != null) {
            TeamSide side = session.getTeam(player.getUniqueId());
            char rowCard = side == TeamSide.FROST
                ? Glyphs.TAB_ROW_FROST
                : (side == TeamSide.IGNIS ? Glyphs.TAB_ROW_IGNIS : Glyphs.TAB_ROW_GOLD);
            if (side != TeamSide.SPECTATOR) {
                row.append(side.pillComponent()).append(Component.text(" "));
            }
            int matchPts = session.getBoxScore(player.getUniqueId()).points;
            row.append(Component.text(player.getName(), Style.style(side.textColor(), TextDecoration.BOLD)));
            row.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
            row.append(Component.text(matchPts + " PTS", Style.style(TextColor.color(0xFFE052), TextDecoration.BOLD)));
            return Glyphs.composeCenterTabRow(rowCard, row.asComponent());
        } else {
            Rank rank = profile.effectiveRank();
            row.append(rank.pillComponent()).append(Component.text(" "));
            row.append(Component.text(player.getName(), Style.style(rank.accentColor(), TextDecoration.BOLD)));
            row.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
            row.append(Component.text(profile.points() + " PTS", Style.style(TextColor.color(0xFFE052), TextDecoration.BOLD)));
            return Glyphs.composeCenterTabRow(Glyphs.TAB_ROW_GOLD, row.asComponent());
        }
    }
}
