package fr.bosketball.ui;

import fr.bosketball.util.Palette;
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
    private final Map<UUID, String[]> lastRenderedLines = new ConcurrentHashMap<>();

    public ScoreboardSidebarManager(StatsManager statsManager, SocialPartyManager socialManager) {
        this.statsManager = statsManager;
        this.socialManager = socialManager;
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
            return "<" + Palette.MUTED + ">Season off</" + Palette.MUTED + ">";
        }
        try {
            String line = provider.line(profile);
            return line == null || line.isBlank() ? "<" + Palette.MUTED + ">Season off</" + Palette.MUTED + ">" : line;
        } catch (RuntimeException ex) {
            return "<" + Palette.MUTED + ">Season off</" + Palette.MUTED + ">";
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

        String flourish = "<!shadow><#F8FAFC>" + Glyphs.SB_FLOURISH + "</#F8FAFC></!shadow>";
        String coinBadge = "<!shadow><#F8FAFC>" + Glyphs.CHAT_ICON_COIN + "</#F8FAFC></!shadow>";
        List<String> lines;
        if (match != null) {
            MatchSession.MatchBoxScore box = match.getBoxScore(player.getUniqueId());
            String streakVal = box.hotStreak >= 3
                ? "<" + Palette.WARN + "><bold> On Fire x" + box.hotStreak + "</bold></" + Palette.WARN + ">"
                : (box.hotStreak == 0
                    ? "<#64748B>no buckets yet</#64748B>"
                    : "<#F8FAFC>" + box.hotStreak + (box.hotStreak == 1 ? " bucket" : " buckets") + "</#F8FAFC>");

            String ignisName = match.teamDisplayName(TeamSide.IGNIS);
            String frostName = match.teamDisplayName(TeamSide.FROST);
            lines = List.of(
                flourish,
                "<!shadow><#F8FAFC>" + Glyphs.SB_MATCH + "</#F8FAFC></!shadow>",
                "<" + Palette.EMBER + "><bold>" + match.score(TeamSide.IGNIS) + "</bold></" + Palette.EMBER + "> <#94A3B8>" + ignisName + "</#94A3B8> <#64748B>·</#64748B> <#94A3B8>" + frostName + "</#94A3B8> <" + Palette.ICE + "><bold>" + match.score(TeamSide.FROST) + "</bold></" + Palette.ICE + ">",
                "<!shadow><#F8FAFC>" + Glyphs.SB_CLOCK + "</#F8FAFC></!shadow>",
                "<#F8FAFC>" + match.formattedClock() + " \u00b7 </#F8FAFC><" + Palette.GOLD_SOFT + ">" + match.shotClockSeconds() + "s</" + Palette.GOLD_SOFT + ">",
                "<!shadow><#F8FAFC>" + Glyphs.SB_STATS + "</#F8FAFC></!shadow>",
                "<#F8FAFC>" + box.points + " PTS \u00b7 " + box.assists + " AST \u00b7 " + box.steals + " STL</#F8FAFC>",
                "<!shadow><#F8FAFC>" + Glyphs.SB_COINS + "</#F8FAFC></!shadow>",
                coinBadge + " <" + Palette.GOLD_SOFT + "><bold>" + Msg.count(profile.coins()) + "</bold></" + Palette.GOLD_SOFT + ">",
                "<!shadow><#F8FAFC>" + Glyphs.SB_STREAK + "</#F8FAFC></!shadow>",
                streakVal,
                flourish
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
        String coinBadge = "<!shadow><#F8FAFC>" + Glyphs.CHAT_ICON_COIN + "</#F8FAFC></!shadow>";
        fr.bosketball.model.Rank curRank = profile.effectiveRank();
        fr.bosketball.model.Rank nextRank = profile.competitiveRank().nextCompetitiveRank();
        StatsManager.LeaderboardBadges topBadges = statsManager.getBadges(playerId);
        String topEloSuffix = (topBadges.eloPlace() >= 1 && topBadges.eloPlace() <= 3)
            ? " <!shadow><#F8FAFC>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, topBadges.eloPlace()) + "</#F8FAFC></!shadow>"
            : "";
        String eloProgressLine = nextRank != null
            ? "<" + Palette.GOLD + "><bold>" + Msg.count(profile.elo()) + "</bold></" + Palette.GOLD + "><#94A3B8>/" + Msg.count(nextRank.minElo()) + " · " + nextRank.plainDisplayName() + "</#94A3B8>"
            : "<" + Palette.GOLD + "><bold>" + Msg.count(profile.elo()) + " ELO</bold></" + Palette.GOLD + "> <#FBBF24>MAX</#FBBF24>";

        GameModeType qMode = gameManager.getQueuedMode(playerId);
        String partyOrQueueLine;
        if (qMode != null) {
            partyOrQueueLine = "<" + Palette.SUCCESS + ">Queued: " + qMode.id().toUpperCase(java.util.Locale.ROOT) + "</" + Palette.SUCCESS + ">";
        } else if (socialManager != null && socialManager.getParty(playerId) != null) {
            SocialPartyManager.Party party = socialManager.getParty(playerId);
            int pSize = party.members().size();
            if (party.hasCustomTeamName()) {
                partyOrQueueLine = "<" + Palette.GOLD + "><bold>" + party.teamName() + "</bold></" + Palette.GOLD + "> <#94A3B8>(" + pSize + "/4)</#94A3B8>";
            } else {
                partyOrQueueLine = "<#F8FAFC>Party (" + pSize + "/4)</#F8FAFC>";
            }
        } else {
            int onlineFriends = socialManager != null ? socialManager.countOnlineFriends(playerId) : 0;
            if (onlineFriends > 0) {
                partyOrQueueLine = "<" + Palette.SUCCESS + ">" + onlineFriends + " Friend" + (onlineFriends == 1 ? "" : "s") + " Online</" + Palette.SUCCESS + ">";
            } else {
                partyOrQueueLine = "<" + Palette.MUTED + ">Solo Ready</" + Palette.MUTED + ">";
            }
        }

        // Round 43 — one shape for every block: a section tag, then its value underneath, framed by
        // the golden flourish the owner missed. The lobby sidebar used to mix two-line blocks
        // (COINS, RANKED) with one-line blocks (PLAYTIME, CLOCK, PARTY) and had no flourish at all,
        // because the 15-line client cap was already spent on blank separators. Six tags x 2 lines
        // + two flourishes = 14 lines, and nothing is squeezed: the flourishes ARE the separators.
        String flourish = "<!shadow><#F8FAFC>" + Glyphs.SB_FLOURISH + "</#F8FAFC></!shadow>";
        String playtimeValue = "<#F8FAFC>" + profile.formattedPlayTime() + "</#F8FAFC>";
        String seasonValue = seasonLine(profile);
        return List.of(
            flourish,
            "<!shadow><#F8FAFC>" + Glyphs.SB_PLAYER + "</#F8FAFC></!shadow>",
            "<!shadow><#F8FAFC>" + curRank.pillGlyph() + "</#F8FAFC></!shadow>" + topEloSuffix + " <#F8FAFC>" + playerName + "</#F8FAFC>",
            "<!shadow><#F8FAFC>" + Glyphs.SB_COINS + "</#F8FAFC></!shadow>",
            coinBadge + " <" + Palette.GOLD_SOFT + "><bold>" + Msg.count(profile.coins()) + "</bold></" + Palette.GOLD_SOFT + ">",
            "<!shadow><#F8FAFC>" + Glyphs.SB_ELO + "</#F8FAFC></!shadow>",
            eloProgressLine,
            "<!shadow><#F8FAFC>" + Glyphs.SB_PLAYTIME + "</#F8FAFC></!shadow>",
            playtimeValue,
            "<!shadow><#F8FAFC>" + Glyphs.SB_CLOCK + "</#F8FAFC></!shadow>",
            seasonValue,
            "<!shadow><#F8FAFC>" + Glyphs.SB_FRIENDS + "</#F8FAFC></!shadow>",
            partyOrQueueLine,
            flourish
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

    /**
     * Writes a validated list into the scoreboard.
     *
     * <p>Three rules this method exists to enforce, in this order:
     *
     * <ol>
     *   <li><b>No row is ever left blank.</b> The list arrives already normalised
     *       ({@link SidebarLines}), so a missing value is a visible placeholder rather than a hole
     *       in the panel.</li>
     *   <li><b>No bare delete.</b> A row that leaves the panel is cleared with {@link Team#unregister}
     *       and its score reset <em>through the same pass</em> that writes the rows above it, so the
     *       panel is redrawn in one consistent state instead of decaying row by row.</li>
     *   <li><b>Nothing is written twice.</b> The prefix is replaced only when its text actually
     *       changed, and the score only when the row moved — a byte-identical panel costs no
     *       packets.</li>
     * </ol>
     */
    private void applyLines(UUID playerId, Scoreboard sb, Objective obj, List<String> rendered) {
        // One door into the scoreboard: whatever the builders produced, the panel is validated
        // here — blank rows replaced, wide rows fitted, the client's row ceiling applied.
        List<String> lines = SidebarLines.of(rendered).rows();
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
            } else if (lineTeam != null) {
                // Replace, never bare-delete: the row is emptied and removed in one step, and the
                // cached text goes with it, so the next render cannot re-emit a stale prefix.
                lineTeam.unregister();
                sb.resetScores(entry);
                prev[i] = null;
            }
        }
    }
}
