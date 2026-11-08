package fr.bosketball.progression;

import fr.bosketball.model.PlayerQuest;
import fr.bosketball.model.ProgressStat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The quest catalog: what a player is asked to do, and what they get for it.
 *
 * <h2>Three dailies, one weekly, one sprint</h2>
 * The rule the quest design is built on: <em>three is a habit, seven is a chore</em>. A daily set
 * that takes 15–30 minutes forms a habit; one that takes two hours is a second job. So:
 * <ul>
 *   <li><b>Three dailies</b>, each completable inside one session, none of them depending on a
 *       teammate.</li>
 *   <li><b>One weekly</b> — the catch-up valve for a player who missed a Tuesday. One, not three:
 *       the boards then total three repeatable claims a day plus one a week, which is a routine
 *       rather than a checklist.</li>
 *   <li><b>One sprint</b> (the old "contract"), on a six-hour clock. A sprint is the hard end of
 *       the ladder — it is a burst, not a second daily board.</li>
 * </ul>
 *
 * <h2>Why no quest depends on a teammate</h2>
 * An assist is a two-participant event. A daily that says "record 2 assists" asks the player to
 * hope somebody else passes to them, and a daily that fails because a stranger did not show up
 * is the fastest way to make a player stop opening the quest board. Every target here is
 * reachable by one player's own play — the <em>stat</em> may be a team stat, but the
 * <em>target</em> never needs a second person.
 *
 * <h2>Why the weekly is one quest</h2>
 * It used to be three, on top of three dailies and two contracts: eight repeatable claims per
 * cycle. The research on time-gating is unambiguous that the daily cap has to be completable in
 * one sitting, and eight claims is not one sitting.
 *
 * <h2>Rewards</h2>
 * A daily pays 80–170 coins, the weekly 700, a sprint 260–300. The weekly is worth roughly five
 * dailies, which is the ratio that makes a week of consistent play feel better than seven rushed
 * days — and the ratio that stops a player from ignoring the weekly entirely.
 */
public final class QuestCatalog {

    public record Template(
        String id,
        String title,
        String description,
        ProgressStat stat,
        int target,
        int rewardCoins,
        int rewardXp,
        String iconModel,
        Board board
    ) {
    }

    public enum Board {
        DAILY,
        WEEKLY,
        CONTRACT
    }

    /**
     * The three daily boards' worth of targets, in one pool.
     *
     * <p>Every target below is reachable inside one session — eight matches for an engaged player
     * — and none of them needs a second person. The comment after each one says what a typical
     * line looks like, because a target nobody can reach is worse than no target.
     */
    private static final List<Template> DAILY = List.of(
        // --- scoring ---------------------------------------------------------------
        new Template("d_points_20", "Scoring Touch", "Score 20 points", ProgressStat.POINTS_SCORED, 20, 90, 60, "icon_crossover", Board.DAILY),
        // ~11 points a match: two and a half matches.
        new Template("d_points_45", "Bucket Run", "Score 45 points", ProgressStat.POINTS_SCORED, 45, 170, 120, "icon_crossover", Board.DAILY),
        // ~11 a match: four matches, still one session.
        new Template("d_threes_5", "Catch & Shoot", "Make 5 three-pointers", ProgressStat.THREE_POINTERS, 5, 100, 70, "ref_calendar_check", Board.DAILY),
        // ~0.8 a match: six matches.
        new Template("d_threes_10", "Splash Hour", "Make 10 three-pointers", ProgressStat.THREE_POINTERS, 10, 160, 110, "ref_calendar_check", Board.DAILY),
        // ~1.25 a match: eight matches — a full session, and no more.
        new Template("d_twos_12", "Paint Artist", "Make 12 two-pointers", ProgressStat.TWO_POINTERS, 12, 110, 75, "icon_crossover", Board.DAILY),
        // ~1.5 a match: eight matches.
        new Template("d_dunks_3", "Rim Rattler", "Land 3 slam dunks", ProgressStat.DUNKS, 3, 90, 60, "icon_dunk", Board.DAILY),
        // ~0.6 a match: five matches.
        new Template("d_dunks_5", "Above The Rim", "Land 5 slam dunks", ProgressStat.DUNKS, 5, 140, 90, "icon_dunk", Board.DAILY),
        // ~0.6 a match: eight matches.

        // --- playmaking ------------------------------------------------------------
        new Template("d_assists_4", "Dime Time", "Record 4 assists", ProgressStat.ASSISTS, 4, 100, 70, "ref_trophy", Board.DAILY),
        // ~0.7 a match: six matches.
        new Template("d_rebounds_5", "Board Man", "Grab 5 rebounds", ProgressStat.REBOUNDS, 5, 90, 60, "icon_team_duo", Board.DAILY),
        // ~0.8 a match: six matches.

        // --- defence ---------------------------------------------------------------
        new Template("d_steals_3", "Pickpocket", "Record 3 steals", ProgressStat.STEALS, 3, 100, 70, "icon_defense", Board.DAILY),
        // ~0.6 a match: five matches.
        new Template("d_blocks_3", "Send It Back", "Record 3 blocks", ProgressStat.BLOCKS, 3, 100, 70, "icon_star_shield", Board.DAILY),
        // ~0.6 a match: five matches.
        new Template("d_ankles_2", "Ankle Taker", "Break 2 ankles with crossovers", ProgressStat.ANKLE_BREAKERS, 2, 110, 80, "icon_crossover", Board.DAILY),
        // ~0.4 a match: five matches.

        // --- results ---------------------------------------------------------------
        new Template("d_wins_2", "Back-To-Back", "Win 2 matches", ProgressStat.MATCHES_WON, 2, 130, 90, "ref_check", Board.DAILY),
        // ~1 in 4 for an average player: eight matches.
        new Template("d_play_5", "Get Reps In", "Play 5 matches", ProgressStat.MATCHES_PLAYED, 5, 80, 60, "icon_battle", Board.DAILY),
        // Five matches — well inside one session.
        new Template("d_casual_2", "Casual Reps", "Play 2 casual matches", ProgressStat.CASUAL_MATCHES, 2, 90, 60, "ref_dice", Board.DAILY),
        new Template("d_ranked_2", "Ranked Reps", "Play 2 ranked matches", ProgressStat.RANKED_MATCHES, 2, 120, 80, "ref_arrow_big_right", Board.DAILY),
        new Template("d_contest_1", "Quick Contest", "Complete a 3-Point Contest", ProgressStat.THREE_POINT_CONTESTS, 1, 110, 70, "ref_calendar_check", Board.DAILY)
    );

    /**
     * The weekly board: one quest, worth about five dailies.
     *
     * <p>Each target is roughly a week of steady play, not a week of frantic grinding.
     */
    private static final List<Template> WEEKLY = List.of(
        new Template("w_points_200", "Scoring Crown", "Score 200 points", ProgressStat.POINTS_SCORED, 200, 700, 450, "icon_medal_gold", Board.WEEKLY),
        // ~11 a match: 18 matches, about a week.
        new Template("w_threes_60", "Volume Shooter", "Make 60 three-pointers", ProgressStat.THREE_POINTERS, 60, 700, 450, "ref_calendar_check", Board.WEEKLY),
        // ~0.8 a match: 75 matches across a week — a stretch, but one match at a time.
        new Template("w_dunks_30", "Dunk Marathon", "Land 30 slam dunks", ProgressStat.DUNKS, 30, 700, 450, "icon_dunk", Board.WEEKLY),
        // ~0.6 a match: 50 matches across a week.
        new Template("w_assists_20", "Floor General", "Record 20 assists", ProgressStat.ASSISTS, 20, 700, 450, "ref_trophy", Board.WEEKLY),
        // ~0.7 a match: 29 matches across a week.
        new Template("w_wins_10", "Ten Spot", "Win 10 matches", ProgressStat.MATCHES_WON, 10, 700, 450, "ref_trophy", Board.WEEKLY),
        // ~1 in 4: 40 matches across a week.
        new Template("w_ranked_10", "Ranked Week", "Play 10 ranked matches", ProgressStat.RANKED_MATCHES, 10, 700, 450, "ref_arrow_big_right", Board.WEEKLY),
        new Template("w_blocks_15", "No Fly Zone", "Record 15 blocks", ProgressStat.BLOCKS, 15, 700, 450, "icon_star_shield", Board.WEEKLY),
        new Template("w_steals_15", "Clamp Week", "Record 15 steals", ProgressStat.STEALS, 15, 700, 450, "icon_defense", Board.WEEKLY)
    );

    /**
     * The sprint board: one hard objective on a six-hour clock.
     *
     * <p>These are the burst end of the ladder. A sprint asks for real production inside one
     * sitting — six hours is long enough to play a run of matches and short enough that the
     * window means something.
     */
    private static final List<Template> CONTRACT = List.of(
        new Template("c_dunks_8", "Contract: Dunk Spree", "Land 8 slam dunks in 6 hours", ProgressStat.DUNKS, 8, 260, 160, "icon_dunk", Board.CONTRACT),
        new Template("c_threes_15", "Contract: Sharpshooter", "Make 15 three-pointers in 6 hours", ProgressStat.THREE_POINTERS, 15, 280, 170, "ref_calendar_check", Board.CONTRACT),
        new Template("c_wins_4", "Contract: Win Streak", "Win 4 matches in 6 hours", ProgressStat.MATCHES_WON, 4, 300, 180, "ref_check", Board.CONTRACT),
        new Template("c_points_60", "Contract: Volume", "Score 60 points in 6 hours", ProgressStat.POINTS_SCORED, 60, 270, 160, "icon_crossover", Board.CONTRACT),
        new Template("c_assists_10", "Contract: Playmaking", "Record 10 assists in 6 hours", ProgressStat.ASSISTS, 10, 260, 150, "ref_trophy", Board.CONTRACT),
        new Template("c_steals_8", "Contract: Lockdown", "Record 8 steals in 6 hours", ProgressStat.STEALS, 8, 260, 150, "icon_defense", Board.CONTRACT),
        new Template("c_rebounds_12", "Contract: Glass", "Grab 12 rebounds in 6 hours", ProgressStat.REBOUNDS, 12, 260, 150, "icon_team_duo", Board.CONTRACT)
    );

    private QuestCatalog() {
    }

    public static List<Template> pool(Board board) {
        return switch (board) {
            case DAILY -> DAILY;
            case WEEKLY -> WEEKLY;
            case CONTRACT -> CONTRACT;
        };
    }

    public static List<PlayerQuest> roll(Board board, int count, long seed, long assignedAt, long expiresAt) {
        List<Template> pool = new ArrayList<>(pool(board));
        Random rng = new Random(seed);

        for (int i = pool.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            Template tmp = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, tmp);
        }
        List<PlayerQuest> out = new ArrayList<>(count);
        for (int i = 0; i < Math.min(count, pool.size()); i++) {
            Template t = pool.get(i);
            out.add(new PlayerQuest(
                t.id(), t.title(), t.description(), t.stat(), t.target(),
                t.rewardCoins(), t.rewardXp(), t.iconModel(), assignedAt, expiresAt
            ));
        }
        return out;
    }

    public static PlayerQuest rerollContract(java.util.Set<String> excludedIds, long seed, long assignedAt, long expiresAt) {
        List<Template> pool = new ArrayList<>(CONTRACT);
        pool.removeIf(t -> excludedIds.contains(t.id()));
        if (pool.isEmpty()) {
            pool = new ArrayList<>(CONTRACT);
        }
        Random rng = new Random(seed);
        Template t = pool.get(rng.nextInt(pool.size()));
        return new PlayerQuest(
            t.id() + "_" + (seed & 0xFFFF), t.title(), t.description(), t.stat(), t.target(),
            t.rewardCoins(), t.rewardXp(), t.iconModel(), assignedAt, expiresAt
        );
    }

    /**
     * Whether a quest's target can be reached without a teammate.
     *
     * <p>An assist still counts as a solo stat — the player creates it, they do not wait for
     * somebody to pass to them. The rule is about the target, not the stat: no target may be so
     * high that it needs a second player to make the number move.
     */
    public static boolean isSoloAchievable(Template template, int matchesPerSession) {
        if (template == null || matchesPerSession <= 0) {
            return false;
        }
        int sessions = Math.max(1, matchesPerSession);
        int target = template.target();
        switch (template.stat()) {
            case POINTS_SCORED:
                return target <= sessions * 12;
            case THREE_POINTERS:
                return target <= sessions * 1.3;
            case TWO_POINTERS:
                return target <= sessions * 1.6;
            case DUNKS:
                return target <= sessions * 0.7;
            case ASSISTS:
                return target <= sessions * 0.8;
            case REBOUNDS:
                return target <= sessions * 0.9;
            case STEALS:
                return target <= sessions * 0.7;
            case BLOCKS:
                return target <= sessions * 0.7;
            case ANKLE_BREAKERS:
                return target <= sessions * 0.5;
            case MATCHES_WON:
                return target <= sessions * 0.3;
            case MATCHES_PLAYED:
                return target <= sessions;
            case CASUAL_MATCHES:
                return target <= sessions;
            case RANKED_MATCHES:
                return target <= sessions;
            case THREE_POINT_CONTESTS:
                return target <= 1;
            case MVPS:
                return target <= sessions * 0.2;
            case PARTY_MATCHES:
                // A party match needs a second player, so it is never a solo daily.
                return false;
            case WIN_STREAK_BEST:
                // A streak of eight is not one session's work.
                return false;
            case FRIENDS_ADDED:
            case COINS_EARNED:
            case QUEUE_JOINS:
            default:
                return true;
        }
    }
}
