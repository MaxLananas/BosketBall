package fr.bosketball.progression;

import fr.bosketball.model.PlayerQuest;
import fr.bosketball.model.ProgressStat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic quest pool. Each board (daily / weekly / contract) picks a stable slice of
 * templates keyed by the player's UUID + the current period, so:
 * <ul>
 *   <li>a player never rerolls their board by reconnecting,</li>
 *   <li>the board automatically rotates at the next period boundary,</li>
 *   <li>two different players get different objectives, so the server feels alive.</li>
 * </ul>
 */
public final class QuestCatalog {

    /** A single quest template. */
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

    /** Which board a template belongs to. */
    public enum Board {
        DAILY,
        WEEKLY,
        CONTRACT
    }

    private static final List<Template> DAILY = List.of(
        new Template("d_dunks_3", "Rim Rattler", "Land 3 slam dunks", ProgressStat.DUNKS, 3, 90, 60, "icon_dunk", Board.DAILY),
        new Template("d_dunks_5", "Above The Rim", "Land 5 slam dunks", ProgressStat.DUNKS, 5, 140, 90, "icon_dunk", Board.DAILY),
        new Template("d_threes_5", "Catch & Shoot", "Make 5 three-pointers", ProgressStat.THREE_POINTERS, 5, 100, 70, "icon_target", Board.DAILY),
        new Template("d_threes_10", "Splash Hour", "Make 10 three-pointers", ProgressStat.THREE_POINTERS, 10, 160, 110, "icon_target", Board.DAILY),
        new Template("d_points_20", "Scoring Touch", "Score 20 total points", ProgressStat.POINTS_SCORED, 20, 90, 60, "icon_crossover", Board.DAILY),
        new Template("d_points_45", "Bucket Run", "Score 45 total points", ProgressStat.POINTS_SCORED, 45, 170, 120, "icon_crossover", Board.DAILY),
        new Template("d_wins_2", "Back-To-Back", "Win 2 matches", ProgressStat.MATCHES_WON, 2, 130, 90, "icon_check", Board.DAILY),
        new Template("d_wins_3", "Three In The Bag", "Win 3 matches", ProgressStat.MATCHES_WON, 3, 180, 120, "icon_trophy_gold", Board.DAILY),
        new Template("d_play_3", "Get Reps In", "Play 3 matches", ProgressStat.MATCHES_PLAYED, 3, 80, 60, "icon_battle", Board.DAILY),
        new Template("d_play_5", "Full Session", "Play 5 matches", ProgressStat.MATCHES_PLAYED, 5, 130, 90, "icon_battle", Board.DAILY),
        new Template("d_assists_4", "Dime Time", "Record 4 assists", ProgressStat.ASSISTS, 4, 100, 70, "icon_pass", Board.DAILY),
        new Template("d_steals_3", "Pickpocket", "Record 3 steals", ProgressStat.STEALS, 3, 100, 70, "icon_defense", Board.DAILY),
        new Template("d_blocks_3", "Send It Back", "Record 3 blocks", ProgressStat.BLOCKS, 3, 100, 70, "icon_star_shield", Board.DAILY),
        new Template("d_rebounds_5", "Board Man", "Grab 5 rebounds", ProgressStat.REBOUNDS, 5, 90, 60, "icon_team_duo", Board.DAILY),
        new Template("d_ankles_2", "Ankle Taker", "Break 2 ankles with crossovers", ProgressStat.ANKLE_BREAKERS, 2, 110, 80, "icon_crossover", Board.DAILY),
        new Template("d_casual_2", "Casual Reps", "Play 2 casual matches", ProgressStat.CASUAL_MATCHES, 2, 90, 60, "icon_training", Board.DAILY),
        new Template("d_ranked_2", "Ranked Reps", "Play 2 ranked matches", ProgressStat.RANKED_MATCHES, 2, 120, 80, "icon_arena", Board.DAILY),
        new Template("d_contest_1", "Quick Contest", "Complete a 3-Point Contest", ProgressStat.THREE_POINT_CONTESTS, 1, 110, 70, "icon_target", Board.DAILY)
    );

    private static final List<Template> WEEKLY = List.of(
        new Template("w_dunks_20", "Dunk Marathon", "Land 20 slam dunks", ProgressStat.DUNKS, 20, 600, 400, "icon_dunk", Board.WEEKLY),
        new Template("w_threes_40", "Volume Shooter", "Make 40 three-pointers", ProgressStat.THREE_POINTERS, 40, 650, 420, "icon_target", Board.WEEKLY),
        new Template("w_points_200", "Scoring Crown", "Score 200 total points", ProgressStat.POINTS_SCORED, 200, 700, 450, "icon_medal_gold", Board.WEEKLY),
        new Template("w_wins_10", "Ten Spot", "Win 10 matches", ProgressStat.MATCHES_WON, 10, 800, 500, "icon_trophy_gold", Board.WEEKLY),
        new Template("w_play_20", "Iron Week", "Play 20 matches", ProgressStat.MATCHES_PLAYED, 20, 550, 380, "icon_battle", Board.WEEKLY),
        new Template("w_assists_20", "Floor General", "Record 20 assists", ProgressStat.ASSISTS, 20, 600, 400, "icon_pass", Board.WEEKLY),
        new Template("w_steals_15", "Clamp Week", "Record 15 steals", ProgressStat.STEALS, 15, 600, 400, "icon_defense", Board.WEEKLY),
        new Template("w_blocks_15", "No Fly Zone", "Record 15 blocks", ProgressStat.BLOCKS, 15, 600, 400, "icon_star_shield", Board.WEEKLY),
        new Template("w_ranked_10", "Ranked Week", "Play 10 ranked matches", ProgressStat.RANKED_MATCHES, 10, 650, 420, "icon_arena", Board.WEEKLY),
        new Template("w_mvp_3", "Star Of The Week", "Earn 3 MVP awards", ProgressStat.MVPS, 3, 900, 600, "icon_podium", Board.WEEKLY),
        new Template("w_friends_2", "Recruiter", "Add 2 new friends", ProgressStat.FRIENDS_ADDED, 2, 400, 250, "icon_friend_add", Board.WEEKLY),
        new Template("w_party_5", "Squad Chemistry", "Play 5 matches with a Party", ProgressStat.PARTY_MATCHES, 5, 700, 450, "icon_party_invites", Board.WEEKLY)
    );

    private static final List<Template> CONTRACT = List.of(
        new Template("c_dunks_8", "Contract: Dunk Spree", "Land 8 slam dunks within 6 hours", ProgressStat.DUNKS, 8, 260, 160, "icon_dunk", Board.CONTRACT),
        new Template("c_threes_15", "Contract: Sharpshooter", "Make 15 three-pointers within 6 hours", ProgressStat.THREE_POINTERS, 15, 280, 170, "icon_target", Board.CONTRACT),
        new Template("c_wins_4", "Contract: Win Streak", "Win 4 matches within 6 hours", ProgressStat.MATCHES_WON, 4, 300, 180, "icon_check", Board.CONTRACT),
        new Template("c_play_6", "Contract: Grind", "Play 6 matches within 6 hours", ProgressStat.MATCHES_PLAYED, 6, 220, 140, "icon_battle", Board.CONTRACT),
        new Template("c_points_60", "Contract: Volume", "Score 60 total points within 6 hours", ProgressStat.POINTS_SCORED, 60, 270, 160, "icon_crossover", Board.CONTRACT),
        new Template("c_assists_10", "Contract: Playmaking", "Record 10 assists within 6 hours", ProgressStat.ASSISTS, 10, 260, 150, "icon_pass", Board.CONTRACT),
        new Template("c_steals_8", "Contract: Lockdown", "Record 8 steals within 6 hours", ProgressStat.STEALS, 8, 260, 150, "icon_defense", Board.CONTRACT)
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

    /**
     * Picks {@code count} distinct templates for a board, deterministically seeded by
     * player UUID + period index so reconnecting never rerolls the board.
     *
     * @param seed       stable seed (uuid hash combined with the period index)
     * @param assignedAt assignment timestamp written into each quest
     * @param expiresAt  expiry timestamp written into each quest
     */
    public static List<PlayerQuest> roll(Board board, int count, long seed, long assignedAt, long expiresAt) {
        List<Template> pool = new ArrayList<>(pool(board));
        Random rng = new Random(seed);
        // Fisher-Yates with the seeded RNG
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

    /** Reroll pool for a single contract slot (excludes ids already on the board). */
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
}
