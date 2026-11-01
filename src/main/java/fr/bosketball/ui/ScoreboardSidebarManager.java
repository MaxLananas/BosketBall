package fr.bosketball.ui;

import fr.bosketball.game.GameManager;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.TeamSide;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Msg;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Flicker-Free Paper 26.2 Custom Sidebar Scoreboard using NumberFormat.blank()!
 */
@SuppressWarnings("deprecation")
public final class ScoreboardSidebarManager {

    private static final String OBJ_NAME = "bb_sidebar";
    private static final ChatColor[] LINE_KEYS = {
        ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA,
        ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY,
        ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA,
        ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.YELLOW
    };

    private final StatsManager statsManager;
    private final String serverDomain;

    public ScoreboardSidebarManager(StatsManager statsManager, String serverDomain) {
        this.statsManager = statsManager;
        this.serverDomain = serverDomain;
    }

    public void updatePlayerSidebar(Player player, GameManager gameManager) {
        Scoreboard sb = player.getScoreboard();
        if (sb == Bukkit.getScoreboardManager().getMainScoreboard()) {
            sb = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(sb);
        }

        Objective obj = sb.getObjective(OBJ_NAME);
        if (obj == null) {
            obj = sb.registerNewObjective(
                OBJ_NAME,
                Criteria.DUMMY,
                Msg.parse("<gradient:#FF7A18:#FFD43B><bold>✦ ʙᴏꜱᴋᴇᴛʙᴀʟʟ ✦</bold></gradient>")
            );
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(NumberFormat.blank());
        }

        PlayerProfile profile = statsManager.getOrCreate(player);
        MatchSession match = gameManager.getSessionOf(player.getUniqueId());

        List<String> lines;
        if (match != null) {
            TeamSide side = match.getTeam(player.getUniqueId());
            MatchSession.MatchBoxScore box = match.getBoxScore(player.getUniqueId());
            String carrier = match.currentBallCarrierName();
            lines = List.of(
                "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>",
                "<gray>ᴍᴏᴅᴇ:</gray> <gold>" + match.mode().smallCapsTitle() + "</gold>",
                "<gray>ᴘᴇʀɪᴏᴅᴇ:</gray> <white>" + match.formattedClock() + "</white> <dark_gray>•</dark_gray> <yellow>" + match.shotClockSeconds() + "ꜱ</yellow>",
                "",
                "<#FF4D3D><bold>🔥 ɪɢɴɪꜱ:</bold></#FF4D3D> <white><bold>" + match.score(TeamSide.IGNIS) + "</bold></white>",
                "<#3DB8FF><bold>❄ ꜰʀᴏꜱᴛ:</bold></#3DB8FF> <white><bold>" + match.score(TeamSide.FROST) + "</bold></white>",
                "",
                "<gray>ᴘᴏʀᴛᴇᴜʀ:</gray> <aqua>" + carrier + "</aqua>",
                "<gray>ᴇǫᴜɪᴘᴇ:</gray> " + "<#" + Integer.toHexString(side.textColor().value()) + ">" + side.symbol() + " " + side.smallCapsName(),
                "<gray>ᴍᴀᴛᴄʜ:</gray> <yellow>" + box.points + "ᴘᴛꜱ</yellow> <dark_gray>•</dark_gray> <aqua>" + box.assists + "ᴀꜱᴛ</aqua> <dark_gray>•</dark_gray> <green>" + box.rebounds + "ʀᴇʙ</green>",
                box.hotStreak >= 3
                    ? "<gradient:#FF3B00:#FFD43B><bold>🔥 ᴏɴ ꜰɪʀᴇ ! (x" + box.hotStreak + ")</bold></gradient>"
                    : "<gray>ꜱᴇʀɪᴇ:</gray> <gold>" + box.hotStreak + " ᴘᴀɴɪᴇʀ(ꜱ)</gold>",
                "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>",
                "<gradient:#FF8A28:#FFE259>" + serverDomain + "</gradient>"
            );
        } else {
            lines = List.of(
                "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>",
                "<gray>ʀᴀɴɢ:</gray> <!shadow><white>" + profile.effectiveRank().pillGlyph() + "</white></!shadow> <gold>" + profile.elo() + " ᴇʟᴏ</gold>",
                "<gray>ʙɪʟᴀɴ:</gray> <green>" + profile.wins() + "ᴠ</green> <dark_gray>/</dark_gray> <red>" + profile.losses() + "ᴅ</red> <dark_gray>(🔥" + profile.winStreak() + ")</dark_gray>",
                "",
                "<gray>ᴘᴏɪɴᴛꜱ:</gray> <yellow>" + profile.points() + "</yellow> <dark_gray>•</dark_gray> <gold>" + profile.dunks() + " ᴅᴜɴᴋꜱ</gold>",
                "<gray>ᴘᴀꜱꜱᴇꜱ:</gray> <aqua>" + profile.assists() + "</aqua> <dark_gray>•</dark_gray> <light_purple>" + String.format("%.0f%%", profile.threePointPercentage()) + " 3ᴘᴛ</light_purple>",
                "",
                "<gray>ʙᴀʟʟᴏɴ:</gray> <white>" + profile.selectedBallSkin().name().toLowerCase() + "</white>",
                "<gray>ᴅᴜɴᴋ:</gray> <gold>" + profile.selectedDunkStyle().displayName() + "</gold>",
                "",
                "<yellow>▸ /ʙʙ ᴘʟᴀʏ</yellow> <gray>ᴘᴏᴜʀ ᴊᴏᴜᴇʀ !</gray>",
                "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>",
                "<gradient:#FF8A28:#FFE259>" + serverDomain + "</gradient>"
            );
        }

        applyLines(sb, obj, lines);
    }

    private void applyLines(Scoreboard sb, Objective obj, List<String> lines) {
        int count = Math.min(lines.size(), LINE_KEYS.length);
        for (int i = 0; i < LINE_KEYS.length; i++) {
            String teamName = "bb_ln_" + i;
            String entry = LINE_KEYS[i].toString();
            Team lineTeam = sb.getTeam(teamName);
            if (i < count) {
                if (lineTeam == null) {
                    lineTeam = sb.registerNewTeam(teamName);
                    lineTeam.addEntry(entry);
                }
                lineTeam.prefix(Msg.parse(lines.get(i)));
                Score score = obj.getScore(entry);
                score.setScore(count - i);
                score.numberFormat(NumberFormat.blank());
            } else if (lineTeam != null) {
                sb.resetScores(entry);
            }
        }
    }
}
