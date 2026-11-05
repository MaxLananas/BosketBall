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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

@SuppressWarnings("deprecation")
public final class ScoreboardSidebarManager {

    public static final String OBJ_NAME = "bb_sidebar";
    private static final String IGNIS_TEAM = "bb_glow_ignis";
    private static final String FROST_TEAM = "bb_glow_frost";
    private static final String NPC_TEAM = "bb_npc_noname";
    private static final Component HEADER_COMPONENT = Msg.parse(" "
        + Msg.gradBold(Msg.GRAD_BRAND, fr.bosketball.util.Glyphs.smallCaps("BOSKETBALL")) + " ");

    private static final ChatColor[] LINE_KEYS = {
        ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA,
        ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY,
        ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA,
        ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.YELLOW
    };

    private record ActiveTeamEntries(Set<String> ignis, Set<String> frost, Set<String> npcs) {}

    private final StatsManager statsManager;
    private final SocialPartyManager socialManager;
    private final String serverDomain;
    private final Map<UUID, String[]> lastRenderedLines = new ConcurrentHashMap<>();

    public ScoreboardSidebarManager(StatsManager statsManager, SocialPartyManager socialManager, String serverDomain) {
        this.statsManager = statsManager;
        this.socialManager = socialManager;
        this.serverDomain = serverDomain;
    }

    @FunctionalInterface
    public interface SeasonLineProvider {
        String line(fr.bosketball.model.PlayerProfile profile);
    }

    private volatile SeasonLineProvider seasonLineProvider;

    public void attachSeasonProvider(SeasonLineProvider provider) {
        this.seasonLineProvider = provider;
        this.lastRenderedLines.clear();
    }

    private String seasonLine(fr.bosketball.model.PlayerProfile profile) {
        SeasonLineProvider provider = this.seasonLineProvider;
        if (provider == null) {
            return "<#94A3B8>Season off</#94A3B8>";
        }
        try {
            String line = provider.line(profile);
            return line == null || line.isBlank() ? "<#94A3B8>Season off</#94A3B8>" : line;
        } catch (RuntimeException ex) {
            return "<#94A3B8>Season off</#94A3B8>";
        }
    }

    public void clearPlayerCache(UUID playerId) {
        if (playerId != null) {
            lastRenderedLines.remove(playerId);
        }
    }

    public void updateAllSidebars(GameManager gameManager) {
        ActiveTeamEntries rosters = computeActiveTeamEntries(gameManager);
        for (Player p : Bukkit.getOnlinePlayers()) {
            // One player's broken profile must never blank everybody else's sidebar.
            try {
                updatePlayerSidebarInternal(p, gameManager, rosters);
            } catch (RuntimeException brokenProfile) {
                lastRenderedLines.remove(p.getUniqueId());
            }
        }
    }

    public void updatePlayerSidebar(Player player, GameManager gameManager) {
        updatePlayerSidebarInternal(player, gameManager, computeActiveTeamEntries(gameManager));
    }

    /** True when the player currently owns our sidebar objective (used by /bb displays). */
    public boolean hasSidebar(Player player) {
        if (player == null) {
            return false;
        }
        Scoreboard board = player.getScoreboard();
        return board != null && board.getObjective(OBJ_NAME) != null;
    }

    private static Scoreboard ownBoard(Player player) {
        Scoreboard sb = player.getScoreboard();
        org.bukkit.scoreboard.ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr == null) {
            return null;
        }
        if (sb == null || sb == mgr.getMainScoreboard()) {
            sb = mgr.getNewScoreboard();
            player.setScoreboard(sb);
        }
        return sb;
    }

    private static ActiveTeamEntries computeActiveTeamEntries(GameManager gameManager) {
        Set<String> ignis = new HashSet<>();
        Set<String> frost = new HashSet<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            MatchSession s = gameManager.getSessionOf(online.getUniqueId());
            if (s == null) {
                continue;
            }
            TeamSide side = s.getTeam(online.getUniqueId());
            if (side == TeamSide.IGNIS) {
                ignis.add(online.getName());
            } else if (side == TeamSide.FROST) {
                frost.add(online.getName());
            }
        }
        for (MatchSession s : gameManager.activeSessions()) {
            if (s.state() != MatchSession.State.FINISHED) {
                UUID botId = s.sparringBotEntityId();
                if (botId != null) {
                    frost.add(botId.toString());
                }
            }
        }
        return new ActiveTeamEntries(ignis, frost, LobbyHologramManager.activeNpcScoreboardEntries());
    }

    private void updatePlayerSidebarInternal(Player player, GameManager gameManager, ActiveTeamEntries rosters) {
        Scoreboard sb = ownBoard(player);
        if (sb == null) {
            return;
        }

        Objective obj = sb.getObjective(OBJ_NAME);
        if (obj == null) {
            obj = sb.registerNewObjective(
                OBJ_NAME,
                Criteria.DUMMY,
                HEADER_COMPONENT
            );
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(NumberFormat.blank());
            lastRenderedLines.remove(player.getUniqueId());
        }

        syncCourtTeamRosterAndNametags(sb, rosters);

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
            lines = buildLobbyLines(profile, player.getUniqueId(), player.getName(), gameManager);
        }

        applyLines(player.getUniqueId(), sb, obj, lines);
    }

    /**
     * Lobby sidebar lines for a profile. Extracted so /bb stress can measure the exact same
     * renderer at 50 simulated players without a real connection (no packets, same CPU work).
     */
    public List<String> buildLobbyLines(PlayerProfile profile, UUID playerId, String playerName,
                                        GameManager gameManager) {
        String coinBadge = "<!shadow><white>" + Glyphs.CHAT_ICON_COIN + "</white></!shadow>";
        fr.bosketball.model.Rank curRank = profile.effectiveRank();
        fr.bosketball.model.Rank nextRank = profile.competitiveRank().nextCompetitiveRank();
        StatsManager.LeaderboardBadges topBadges = statsManager.getBadges(playerId);
        String topEloSuffix = (topBadges.eloPlace() >= 1 && topBadges.eloPlace() <= 3)
            ? " <!shadow><white>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, topBadges.eloPlace()) + "</white></!shadow>"
            : "";
        String eloProgressLine = nextRank != null
            ? "<#FBBF24><bold>" + profile.elo() + "</bold></#FBBF24><gray>/" + nextRank.minElo() + " → " + nextRank.plainDisplayName() + "</gray>"
            : "<#FBBF24><bold>" + profile.elo() + " ELO</bold></#FBBF24> <gold>(MAX)</gold>";

        GameModeType qMode = gameManager.getQueuedMode(playerId);
        String partyOrQueueLine;
        if (qMode != null) {
            partyOrQueueLine = "<#4ADE80>Queued: " + qMode.id().toUpperCase(java.util.Locale.ROOT) + "</#4ADE80>";
        } else if (socialManager != null && socialManager.getParty(playerId) != null) {
            SocialPartyManager.Party party = socialManager.getParty(playerId);
            int pSize = party.members().size();
            if (party.hasCustomTeamName()) {
                partyOrQueueLine = "<#FBBF24><bold>" + party.teamName() + "</bold></#FBBF24> <gray>(" + pSize + "/4)</gray>";
            } else {
                partyOrQueueLine = "<white>Party (" + pSize + "/4)</white>";
            }
        } else {
            int onlineFriends = socialManager != null ? socialManager.countOnlineFriends(playerId) : 0;
            if (onlineFriends > 0) {
                partyOrQueueLine = "<#4ADE80>" + onlineFriends + " Friend" + (onlineFriends == 1 ? "" : "s") + " Online</#4ADE80>";
            } else {
                partyOrQueueLine = "<#94A3B8>Solo Ready</#94A3B8>";
            }
        }

        String playtimeLine = "<!shadow><white>" + Glyphs.SB_PLAYTIME + "</white></!shadow> "
            + "<white>" + profile.formattedPlayTime() + "</white>";
        // Header and value share one line (like playtime) so there is room for a blank
        // separator between the season block and the party block: 15 lines is the hard cap.
        String seasonBlock = "<!shadow><white>" + Glyphs.SB_CLOCK + "</white></!shadow> "
            + seasonLine(profile);
        return List.of(
            "<!shadow><white>" + Glyphs.SB_PLAYER + "</white></!shadow>",
            "<!shadow><white>" + curRank.pillGlyph() + "</white></!shadow>" + topEloSuffix + " <white>" + playerName + "</white>",
            " ",
            "<!shadow><white>" + Glyphs.SB_COINS + "</white></!shadow>",
            coinBadge + " <#FDE047><bold>" + profile.coins() + "</bold></#FDE047>",
            "  ",
            "<!shadow><white>" + Glyphs.SB_ELO + "</white></!shadow>",
            eloProgressLine,
            "   ",
            playtimeLine,
            "    ",
            seasonBlock,
            "     ",
            "<!shadow><white>" + Glyphs.SB_FRIENDS + "</white></!shadow>",
            partyOrQueueLine
        );
    }

    private static void syncCourtTeamRosterAndNametags(Scoreboard sb, ActiveTeamEntries rosters) {
        Team ignis = sb.getTeam(IGNIS_TEAM);
        if (ignis == null) {
            ignis = sb.registerNewTeam(IGNIS_TEAM);
            ignis.color(net.kyori.adventure.text.format.NamedTextColor.RED);
            ignis.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            ignis.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        Team frost = sb.getTeam(FROST_TEAM);
        if (frost == null) {
            frost = sb.registerNewTeam(FROST_TEAM);
            frost.color(net.kyori.adventure.text.format.NamedTextColor.AQUA);
            frost.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            frost.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        Team npcTeam = sb.getTeam(NPC_TEAM);
        if (npcTeam == null) {
            npcTeam = sb.registerNewTeam(NPC_TEAM);
            npcTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            npcTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }

        syncSingleTeamEntries(ignis, rosters.ignis());
        syncSingleTeamEntries(frost, rosters.frost());
        syncSingleTeamEntries(npcTeam, rosters.npcs());
    }

    private static void syncSingleTeamEntries(Team team, Set<String> desired) {
        Set<String> current = team.getEntries();
        if (!current.equals(desired)) {
            for (String existing : new ArrayList<>(current)) {
                if (!desired.contains(existing)) {
                    team.removeEntry(existing);
                }
            }
            for (String target : desired) {
                if (!team.hasEntry(target)) {
                    team.addEntry(target);
                }
            }
        }
    }

    /**
     * Stress-test entry point: renders the lobby sidebar for a profile with no real Player, using
     * exactly the same line builders and scoreboard churn as {@link #updatePlayerSidebarInternal}
     * (only the packet delivery is missing). Nothing is persisted for the simulated UUID.
     */
    public void simulateRender(PlayerProfile profile, UUID playerId, String playerName,
                               GameManager gameManager, Scoreboard board) {
        if (board == null) {
            return;
        }
        Objective obj = board.getObjective(OBJ_NAME);
        if (obj == null) {
            obj = board.registerNewObjective(OBJ_NAME, Criteria.DUMMY, HEADER_COMPONENT);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(NumberFormat.blank());
            lastRenderedLines.remove(playerId);
        }
        applyLines(playerId, board, obj, buildLobbyLines(profile, playerId, playerName, gameManager));
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
                    lineTeam.prefix(Msg.parse(fr.bosketball.util.Glyphs.smallCapsLabels(targetLine)));
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
