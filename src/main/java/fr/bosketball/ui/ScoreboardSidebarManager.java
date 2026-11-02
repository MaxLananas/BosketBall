package fr.bosketball.ui;

import fr.bosketball.game.GameManager;
import fr.bosketball.game.GameModeType;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.TeamSide;
import fr.bosketball.social.SocialPartyManager;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
 * Clean Cytooxien-Inspired Server Sidebar Scoreboard with Coins Economy, Rank, ELO & Live Match Stats.
 */
@SuppressWarnings("deprecation")
public final class ScoreboardSidebarManager {

    private static final String OBJ_NAME = "bb_sidebar";
    private static final String IGNIS_TEAM = "bb_glow_ignis";
    private static final String FROST_TEAM = "bb_glow_frost";

    private static final ChatColor[] LINE_KEYS = {
        ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA,
        ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY,
        ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA,
        ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.YELLOW
    };

    private final StatsManager statsManager;
    private final SocialPartyManager socialManager;
    private final String serverDomain;
    private final Map<UUID, String[]> lastRenderedLines = new ConcurrentHashMap<>();

    public ScoreboardSidebarManager(StatsManager statsManager, SocialPartyManager socialManager, String serverDomain) {
        this.statsManager = statsManager;
        this.socialManager = socialManager;
        this.serverDomain = serverDomain;
    }

    public void clearPlayerCache(UUID playerId) {
        if (playerId != null) {
            lastRenderedLines.remove(playerId);
        }
    }

    public void updatePlayerSidebar(Player player, GameManager gameManager) {
        Scoreboard sb = player.getScoreboard();
        if (sb == Bukkit.getScoreboardManager().getMainScoreboard()) {
            sb = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(sb);
        }

        Objective obj = sb.getObjective(OBJ_NAME);
        String headerTitle = " <#FF9E2C><bold>BOSKETBALL</bold></#FF9E2C> ";
        if (obj == null) {
            obj = sb.registerNewObjective(
                OBJ_NAME,
                Criteria.DUMMY,
                Msg.parse(headerTitle)
            );
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(NumberFormat.blank());
        } else {
            obj.displayName(Msg.parse(headerTitle));
        }

        syncCourtTeamRosterAndNametags(sb, gameManager);

        PlayerProfile profile = statsManager.getOrCreate(player);
        MatchSession match = gameManager.getSessionOf(player.getUniqueId());

        String flourish = "<!shadow><white>" + Glyphs.SB_FLOURISH + "</white></!shadow>";
        String coinBadge = "<!shadow><white>" + Glyphs.CHAT_ICON_COIN + "</white></!shadow>";
        List<String> lines;
        if (match != null) {
            MatchSession.MatchBoxScore box = match.getBoxScore(player.getUniqueId());
            String streakVal = box.hotStreak >= 3
                ? "<#FF6B35><bold>🔥 On Fire x" + box.hotStreak + "</bold></#FF6B35>"
                : "<white>" + box.hotStreak + (box.hotStreak == 1 ? " bucket" : " buckets") + "</white>";

            String ignisName = match.teamDisplayName(TeamSide.IGNIS);
            String frostName = match.teamDisplayName(TeamSide.FROST);
            lines = List.of(
                flourish,
                "<!shadow><white>" + Glyphs.SB_MATCH + "</white></!shadow>",
                "<#FF5C4D><bold>" + match.score(TeamSide.IGNIS) + "</bold></#FF5C4D> <gray>" + ignisName + "</gray> <dark_gray>—</dark_gray> <gray>" + frostName + "</gray> <#3DB8FF><bold>" + match.score(TeamSide.FROST) + "</bold></#3DB8FF>",
                " ",
                "<!shadow><white>" + Glyphs.SB_CLOCK + "</white></!shadow>",
                "<white>" + match.formattedClock() + " • </white><#FFD43B>" + match.shotClockSeconds() + "s</#FFD43B>",
                "  ",
                "<!shadow><white>" + Glyphs.SB_STATS + "</white></!shadow>",
                "<white>" + box.points + " PTS • " + box.assists + " AST • " + box.steals + " STL</white>",
                "   ",
                "<!shadow><white>" + Glyphs.SB_COINS + "</white></!shadow>",
                coinBadge + " <#FDE047><bold>" + profile.coins() + "</bold></#FDE047>",
                "    ",
                "<!shadow><white>" + Glyphs.SB_STREAK + "</white></!shadow>",
                streakVal
            );
        } else {
            fr.bosketball.model.Rank curRank = profile.effectiveRank();
            fr.bosketball.model.Rank nextRank = profile.competitiveRank().nextCompetitiveRank();
            StatsManager.LeaderboardBadges topBadges = statsManager.getBadges(player.getUniqueId());
            String topEloSuffix = (topBadges.eloPlace() >= 1 && topBadges.eloPlace() <= 3)
                ? " <!shadow><white>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, topBadges.eloPlace()) + "</white></!shadow>"
                : "";
            String eloProgressLine = nextRank != null
                ? "<#FBBF24><bold>" + profile.elo() + "</bold></#FBBF24><gray>/" + nextRank.minElo() + " → " + nextRank.plainDisplayName() + "</gray>"
                : "<#FBBF24><bold>" + profile.elo() + " ELO</bold></#FBBF24> <gold>(MAX)</gold>";

            GameModeType qMode = gameManager.getQueuedMode(player.getUniqueId());
            String partyOrQueueLine;
            if (qMode != null) {
                partyOrQueueLine = "<#4ADE80>Queued: " + qMode.id().toUpperCase() + "</#4ADE80>";
            } else if (socialManager != null && socialManager.getParty(player.getUniqueId()) != null) {
                SocialPartyManager.Party party = socialManager.getParty(player.getUniqueId());
                int pSize = party.members().size();
                if (party.hasCustomTeamName()) {
                    partyOrQueueLine = "<#FBBF24><bold>" + party.teamName() + "</bold></#FBBF24> <gray>(" + pSize + "/4)</gray>";
                } else {
                    partyOrQueueLine = "<white>Party (" + pSize + "/4)</white>";
                }
            } else if (socialManager != null && socialManager.countOnlineFriends(player.getUniqueId()) > 0) {
                int onlineFriends = socialManager.countOnlineFriends(player.getUniqueId());
                partyOrQueueLine = "<#4ADE80>" + onlineFriends + " Friend" + (onlineFriends == 1 ? "" : "s") + " Online</#4ADE80>";
            } else {
                partyOrQueueLine = "<#94A3B8>Solo Ready</#94A3B8>";
            }

            lines = List.of(
                flourish,
                "<!shadow><white>" + Glyphs.SB_PLAYER + "</white></!shadow>",
                "<!shadow><white>" + curRank.pillGlyph() + "</white></!shadow>" + topEloSuffix + " <white>" + player.getName() + "</white>",
                " ",
                "<!shadow><white>" + Glyphs.SB_COINS + "</white></!shadow>",
                coinBadge + " <#FDE047><bold>" + profile.coins() + "</bold></#FDE047>",
                "  ",
                "<!shadow><white>" + Glyphs.SB_ELO + "</white></!shadow>",
                eloProgressLine,
                "   ",
                "<!shadow><white>" + Glyphs.SB_PLAYTIME + "</white></!shadow>",
                "<white>" + profile.formattedPlayTime() + "</white>",
                "    ",
                "<!shadow><white>" + Glyphs.SB_FRIENDS + "</white></!shadow>",
                partyOrQueueLine
            );
        }

        applyLines(player.getUniqueId(), sb, obj, lines);
    }

    private void syncCourtTeamRosterAndNametags(Scoreboard sb, GameManager gameManager) {
        Team ignis = sb.getTeam(IGNIS_TEAM);
        if (ignis == null) {
            ignis = sb.registerNewTeam(IGNIS_TEAM);
            ignis.color(net.kyori.adventure.text.format.NamedTextColor.RED);
            ignis.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            ignis.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        Team frost = sb.getTeam(FROST_TEAM);
        if (frost == null) {
            frost = sb.registerNewTeam(FROST_TEAM);
            frost.color(net.kyori.adventure.text.format.NamedTextColor.AQUA);
            frost.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            frost.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            String name = online.getName();
            MatchSession s = gameManager.getSessionOf(online.getUniqueId());
            if (s == null) {
                ignis.removeEntry(name);
                frost.removeEntry(name);
            } else {
                TeamSide side = s.getTeam(online.getUniqueId());
                if (side == TeamSide.IGNIS) {
                    frost.removeEntry(name);
                    if (!ignis.hasEntry(name)) {
                        ignis.addEntry(name);
                    }
                } else if (side == TeamSide.FROST) {
                    ignis.removeEntry(name);
                    if (!frost.hasEntry(name)) {
                        frost.addEntry(name);
                    }
                }
            }
            if (s != null) {
                UUID botId = s.sparringBotEntityId();
                if (botId != null) {
                    String botEntry = botId.toString();
                    if (!frost.hasEntry(botEntry)) {
                        frost.addEntry(botEntry);
                    }
                }
            }
        }
    }

    private void applyLines(UUID playerId, Scoreboard sb, Objective obj, List<String> lines) {
        int count = Math.min(lines.size(), LINE_KEYS.length);
        String[] prev = lastRenderedLines.computeIfAbsent(playerId, k -> new String[LINE_KEYS.length]);
        for (int i = 0; i < LINE_KEYS.length; i++) {
            String teamName = "bb_ln_" + i;
            String entry = LINE_KEYS[i].toString();
            Team lineTeam = sb.getTeam(teamName);
            if (i < count) {
                String targetLine = lines.get(i);
                boolean teamCreated = false;
                if (lineTeam == null) {
                    lineTeam = sb.registerNewTeam(teamName);
                    lineTeam.addEntry(entry);
                    teamCreated = true;
                }
                if (teamCreated || !targetLine.equals(prev[i])) {
                    lineTeam.prefix(Msg.parse(targetLine));
                    prev[i] = targetLine;
                }
                Score score = obj.getScore(entry);
                if (score.getScore() != (count - i)) {
                    score.setScore(count - i);
                    score.numberFormat(NumberFormat.blank());
                }
            } else if (lineTeam != null && prev[i] != null) {
                sb.resetScores(entry);
                prev[i] = null;
            }
        }
    }
}
