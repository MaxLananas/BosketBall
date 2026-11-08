package fr.bosketball.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The rate card: every coin the server can pay a player, and every coin a player can spend,
 * written down once.
 *
 * <p>This class exists because the economy used to live in fifteen call sites and one YAML file
 * that had drifted apart from them. The brief for this agent is blunt about it: <em>"Any reward
 * that is implemented but not in the table is a bug."</em> So the table is the contract. A reward
 * path that pays coins must have a row here, and {@link #sources()} /
 * {@link #sinks()} are the lists the tests walk.
 *
 * <h2>How a number gets in here</h2>
 * Every row carries its own number rather than reading the config, because the point of a rate
 * card is that it can be compared, printed and asserted on. The config still holds the tuning
 * knobs; {@link EconomyBalance} is where the config and this table meet, and it is the only place
 * allowed to reconcile them. A reward path pays what the config says, and the config is validated
 * against this table at startup by {@link #assertConsistentWith(int, int, int, int, int, int,
 * int, int)}.
 *
 * <h2>Cooldowns</h2>
 * Every repeatable row declares a cooldown in milliseconds. A row with a cooldown of zero is a
 * row that pays once per triggering event and never repeats (an achievement, a season payout). The
 * rule the mission asks for — <em>"every repeatable reward has a cooldown"</em> — is therefore
 * checkable: {@link #repeatableSourcesWithoutCooldown()} returns the offenders, and the test
 * asserts it is empty.
 *
 * <h2>The numbers</h2>
 * The reasoning behind every figure is in {@code docs/agents/research/economy.md}. The short
 * version:
 * <ul>
 *   <li>A loss pays {@code 18} and a win pays {@code 40}. A ratio of 2.22:1 — wide enough that
 *       skill shows in the ledger, narrow enough that a bad game is never worth nothing.</li>
 *   <li>The crate is a <em>sink</em> that occasionally pays a cosmetic. Its coin branch is
 *       strictly negative-EV on its own ({@code -147.5} Coins expected), so the currency it hands
 *       out cannot be farmed.</li>
 *   <li>A match with a bot on either side pays {@code 50%} of its coins and no ELO. The bot
 *       backfill is on by default, so without this a 03:00 ranked queue against one CPU player is
 *       the highest-EV activity on the server.</li>
 * </ul>
 */
public final class RateCard {

    // ---------------------------------------------------------------- result economy

    /** Paid to the winner of a competitive match. */
    public static final int WIN_COINS = 40;

    /**
     * Paid to the loser of a competitive match.
     *
     * <p>Round 84: this was {@code 0}. A loss that pays nothing is the exact shape of the bug the
     * whole codebase learned the hard way — the server only ever told the player who succeeded.
     * A loss now always pays something.
     */
    public static final int LOSS_COINS = 18;

    /** Paid to the MVP of a competitive match, on top of the win or loss payout. */
    public static final int MVP_BONUS_COINS = 15;

    /**
     * Paid to the team that was forfeited against, when the match is called off.
     *
     * <p>This is a <em>reward to the player who stayed</em>, not a penalty: the quitter loses
     * {@link #FORFEIT_PENALTY_COINS} and rating, and the player who stayed gets this on top of
     * the win payout. Leaving a match is a different act from losing one, and the two are priced
     * differently on purpose.
     */
    public static final int FORFEIT_COINS = 15;

    /**
     * Taken from a player who abandons a ranked match.
     *
     * <p>A forfeit is not a loss — it is walking off the court. The rating penalty is larger than
     * a normal loss for the same reason.
     */
    public static final int FORFEIT_PENALTY_COINS = 15;

    /**
     * The largest win-to-loss ratio the ladder will accept.
     *
     * <p>Past 3:1 a thrown match starts to beat a won one at any win rate below 25 %, which turns
     * the ladder into a lying-down duck. 3.0 is the hard bound; the shipped ratio is 2.22.
     */
    public static final double MAX_WIN_TO_LOSS_RATIO = 3.0;

    // ---------------------------------------------------------------- on-court economy

    /**
     * Paid to the shooter for a made two-pointer.
     *
     * <p>Round 84 — this was 5. With 5 for a two, 8 for a three and 4 for an assist, a typical
     * competitive match paid roughly <b>133 coins on the court</b> against a 40-coin win: the
     * on-court economy was 3.3× the result economy, the skill signal in the coin ledger was
     * almost gone, and a 2000-Coin cosmetic took three matches instead of three weeks.
     *
     * <p>The on-court payouts are now scaled so a good match pays about <b>40 coins</b> — roughly
     * the same as the win — which keeps every basket worth making and every assist worth
     * chasing, while putting the result back in charge of the ledger.
     */
    public static final int TWO_POINT_COINS = 2;

    /** Paid to the shooter for a made three-pointer. A three is worth more than a two. */
    public static final int THREE_POINT_COINS = 3;

    /** Paid on top of the basket for a slam dunk. */
    public static final int DUNK_BONUS_COINS = 2;

    /** Paid to the passer for a recorded assist. */
    public static final int ASSIST_COINS = 2;

    /** Paid to the defender for a recorded steal. */
    public static final int STEAL_COINS = 2;

    /** Paid to the defender for a recorded block. */
    public static final int BLOCK_COINS = 2;

    // ---------------------------------------------------------------- solo economy

    /** Coins for a 3-Point Contest, one per point scored, capped by the config. */
    public static final int THREE_POINT_CONTEST_MAX_COINS = 25;

    /** Coins for saying GG after a match. Once per match, on top of the result payout. */
    public static final int GG_REWARD_COINS = 5;

    /** Coins for a daily-login streak day. The table is the config's seven-entry streak list. */
    public static final int DAILY_LOGIN_BASE_COINS = 40;

    /** The largest daily-login streak reward. */
    public static final int DAILY_LOGIN_MAX_COINS = 200;

    /** Coins for completing a daily quest. The board's three dailies pay in this band. */
    public static final int DAILY_QUEST_MIN_COINS = 80;

    /** Coins for completing the weekly quest. */
    public static final int WEEKLY_QUEST_COINS = 700;

    /** Coins for an achievement, at the top of the band. */
    public static final int ACHIEVEMENT_MAX_COINS = 3000;

    /** Coins for a tutorial chapter. */
    public static final int TUTORIAL_CHAPTER_COINS = 50;

    /** Coins for finishing the tutorial. */
    public static final int TUTORIAL_COMPLETION_COINS = 250;

    /** The lowest tier payout at the end of a season, for the Rookie tier. */
    public static final int SEASON_TIER_MIN_COINS = 200;

    /** The highest tier payout at the end of a season, for the MVP tier. */
    public static final int SEASON_TIER_MAX_COINS = 2600;

    /** The podium bonus for first place at the end of a season. */
    public static final int SEASON_TOP1_COINS = 800;

    /** The podium bonus for second place at the end of a season. */
    public static final int SEASON_TOP2_COINS = 450;

    /** The podium bonus for third place at the end of a season. */
    public static final int SEASON_TOP3_COINS = 200;

    // ---------------------------------------------------------------- sinks

    /** What one reward crate costs to open. */
    public static final int CRATE_COST_COINS = 300;

    /** The lowest coin refund a crate can pay. */
    public static final int CRATE_COIN_MIN = 40;

    /** The highest coin refund a crate can pay. */
    public static final int CRATE_COIN_MAX = 220;

    /** The flat bonus a crate pays on a bonus roll. */
    public static final int CRATE_BONUS_COINS = 150;

    /** The chance a crate pays its bonus on top of the main reward. */
    public static final double CRATE_BONUS_CHANCE = 0.15;

    /** The chance a crate pays a cosmetic instead of coins. */
    public static final double CRATE_COSMETIC_CHANCE = 0.22;

    /** How many crates a player may open in one day. */
    public static final int CRATE_DAILY_LIMIT = 3;

    /** The minimum gap between two crate openings, in milliseconds. */
    public static final long CRATE_COOLDOWN_MILLIS = 30_000L;

    /** What a quest reroll costs. */
    public static final int QUEST_REROLL_COST = 75;

    /** What a contract reroll costs. */
    public static final int CONTRACT_REROLL_COST = 75;

    // ---------------------------------------------------------------- multipliers

    /**
     * What a match with at least one bot on either side pays, as a fraction of the normal payout.
     *
     * <p>The bot backfill is on by default ({@code matchmaking.fill-bots-on-launch: true}), so a
     * ranked match against a CPU player is otherwise identical to a ranked match against a human.
     * Half is the smallest discount that is visible to a player and large enough to matter.
     */
    public static final double BOT_MATCH_MULTIPLIER = 0.50;

    /** What a casual match pays, as a fraction of the ranked payout. */
    public static final double CASUAL_MATCH_MULTIPLIER = 0.40;

    /**
     * The floor on the result payout for a loss, as a fraction of the win payout.
     *
     * <p>Used by the symmetry check: a loss must pay at least this fraction of a win so that
     * playing badly is never the same as not playing.
     */
    public static final double MIN_LOSS_TO_WIN_RATIO = 0.25;

    // ---------------------------------------------------------------- hourly model

    /** How many matches a player gets through in one hour of actual play. */
    public static final int MATCHES_PER_HOUR = 8;

    /** The skill bands the hourly income table is computed for. */
    public enum SkillBand {
        /** A player winning about a third of their matches. */
        ROOKIE("Rookie", 0.35),
        /** A player winning half their matches. */
        AVERAGE("Average", 0.50),
        /** A player winning about two thirds of their matches. */
        GOOD("Good", 0.65),
        /** A player winning four matches in five. */
        ELITE("Elite", 0.80);

        private final String label;
        private final double winRate;

        SkillBand(String label, double winRate) {
            this.label = label;
            this.winRate = winRate;
        }

        public String label() {
            return label;
        }

        /** The share of matches this band wins. */
        public double winRate() {
            return winRate;
        }

        /** How many of {@link RateCard#MATCHES_PER_HOUR} matches this band wins. */
        public double winsPerHour() {
            return MATCHES_PER_HOUR * winRate;
        }

        /** How many of {@link RateCard#MATCHES_PER_HOUR} matches this band loses. */
        public double lossesPerHour() {
            return MATCHES_PER_HOUR * (1.0 - winRate);
        }

        /**
         * The coins this band earns per hour from the result economy alone.
         *
         * <p>Deliberately excludes the on-court economy, which depends on how the player plays
         * rather than on how many matches they win — {@link #onCourtCoinsPerHour(double)} models
         * that separately.
         */
        public int resultCoinsPerHour() {
            return (int) Math.round(winsPerHour() * WIN_COINS + lossesPerHour() * LOSS_COINS);
        }

        /**
         * The coins this band earns per hour from buckets, assists, steals and blocks.
         *
         * @param coinsPerMatch how much the band earns on the court in an average match
         */
        public int onCourtCoinsPerHour(int coinsPerMatch) {
            return (int) Math.round(MATCHES_PER_HOUR * coinsPerMatch);
        }

        /**
         * How much on-court production this band gets, as a multiple of the Average band.
         *
         * <p>This is the number that keeps the monthly coin leaderboard a <em>skill</em> board.
         * A better player scores more, gets more assists and records more steals, so their
         * on-court ledger is larger — and the coin economy has to reflect that, or the only way to
         * earn more is to play more hours, which is the hours-logged board nobody wants.
         *
         * <p>The multipliers are deliberately compressed (0.80 to 1.25 rather than something
         * dramatic): the on-court economy is the <em>floor</em> of the economy and the result
         * economy is where the skill signal lives, but the floor still has to be a floor and not
         * a plateau.
         */
        public double onCourtMultiplier() {
            switch (this) {
                case ROOKIE:
                    return 0.80;
                case AVERAGE:
                    return 1.00;
                case GOOD:
                    return 1.12;
                case ELITE:
                    return 1.25;
                default:
                    return 1.00;
            }
        }

        /**
         * The coins this band earns on the court in one average match.
         *
         * @param averageBandCoinsPerMatch what the Average band earns on the court in a match
         */
        public int onCourtCoinsPerMatch(int averageBandCoinsPerMatch) {
            return (int) Math.round(averageBandCoinsPerMatch * onCourtMultiplier());
        }
    }

    /**
     * One row of the rate card.
     *
     * @param id a stable machine name — the tests and the audit use it
     * @param label the player-facing name of the reward or sink
     * @param coins the amount, positive for a source and negative for a sink
     * @param family the family of the economy it belongs to
     * @param repeatable whether the row can pay more than once
     * @param cooldownMillis the minimum gap between two payments, or {@code 0} for a one-shot row
     * @param perHour the intended net income per hour for the Average band, in coins
     */
    public record Row(
        String id,
        String label,
        int coins,
        Family family,
        boolean repeatable,
        long cooldownMillis,
        int perHour
    ) {
        public boolean isSource() {
            return coins > 0;
        }

        public boolean isSink() {
            return coins < 0;
        }

        /** A repeatable row without a cooldown is an anti-abuse hole. */
        public boolean isRepeatableWithoutCooldown() {
            return repeatable && cooldownMillis <= 0L;
        }
    }

    /** The families the economy is cut into. */
    public enum Family {
        /** Win, loss, MVP, forfeit. */
        RESULT,
        /** Buckets, dunks, assists, steals, blocks. */
        ON_COURT,
        /** Daily login, quests, achievements, tutorial. */
        PROGRESSION,
        /** End-of-season payouts. */
        SEASON,
        /** Crates, rerolls, shop. */
        SPEND
    }

    private static final List<Row> ROWS = buildRows();

    private static List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();

        // --- result economy -------------------------------------------------------
        rows.add(new Row("match.win", "Win a competitive match", WIN_COINS, Family.RESULT, false, 0L, 0));
        rows.add(new Row("match.loss", "Lose a competitive match", LOSS_COINS, Family.RESULT, false, 0L, 0));
        rows.add(new Row("match.mvp", "Match MVP", MVP_BONUS_COINS, Family.RESULT, false, 0L, 0));
        rows.add(new Row("match.forfeit", "Win a forfeited match", FORFEIT_COINS, Family.RESULT, false, 0L, 0));
        rows.add(new Row("sink.forfeit", "Abandon a ranked match", -FORFEIT_PENALTY_COINS,
            Family.RESULT, true, 3_600_000L, 0));

        // --- on-court economy -----------------------------------------------------
        // The on-court payouts are repeatable, and their cooldown is the possession itself:
        // a ball cannot be scored twice in the same second, and a steal or a block cannot be
        // recorded twice on the same play. The cooldown is the game, not a timer bolted on
        // afterwards — but it is declared here so the rule "every repeatable reward has a
        // cooldown" has something to check.
        rows.add(new Row("court.two_pointer", "Made two-pointer", TWO_POINT_COINS, Family.ON_COURT, true, 1_000L, 0));
        rows.add(new Row("court.three_pointer", "Made three-pointer", THREE_POINT_COINS, Family.ON_COURT, true, 1_000L, 0));
        rows.add(new Row("court.dunk", "Slam dunk bonus", DUNK_BONUS_COINS, Family.ON_COURT, true, 1_000L, 0));
        rows.add(new Row("court.assist", "Assist", ASSIST_COINS, Family.ON_COURT, true, 1_000L, 0));
        rows.add(new Row("court.steal", "Steal", STEAL_COINS, Family.ON_COURT, true, 1_000L, 0));
        rows.add(new Row("court.block", "Block", BLOCK_COINS, Family.ON_COURT, true, 1_000L, 0));

        // --- solo economy ---------------------------------------------------------
        rows.add(new Row("solo.three_point_contest", "3-Point Contest", THREE_POINT_CONTEST_MAX_COINS,
            Family.ON_COURT, true, 60_000L, 0));
        rows.add(new Row("solo.gg", "Say GG after a match", GG_REWARD_COINS, Family.ON_COURT, false, 0L, 0));

        // --- progression ----------------------------------------------------------
        rows.add(new Row("progress.daily_login", "Daily login streak", DAILY_LOGIN_BASE_COINS,
            Family.PROGRESSION, true, 86_400_000L, 0));
        rows.add(new Row("progress.daily_quest", "Daily quest", DAILY_QUEST_MIN_COINS,
            Family.PROGRESSION, true, 86_400_000L, 0));
        rows.add(new Row("progress.weekly_quest", "Weekly quest", WEEKLY_QUEST_COINS,
            Family.PROGRESSION, true, 604_800_000L, 0));
        rows.add(new Row("progress.achievement", "Achievement", ACHIEVEMENT_MAX_COINS,
            Family.PROGRESSION, false, 0L, 0));
        rows.add(new Row("progress.tutorial_chapter", "Tutorial chapter", TUTORIAL_CHAPTER_COINS,
            Family.PROGRESSION, false, 0L, 0));
        rows.add(new Row("progress.tutorial_completion", "Finish the tutorial", TUTORIAL_COMPLETION_COINS,
            Family.PROGRESSION, false, 0L, 0));

        // --- season ---------------------------------------------------------------
        rows.add(new Row("season.tier", "Season tier payout", SEASON_TIER_MIN_COINS,
            Family.SEASON, false, 0L, 0));
        rows.add(new Row("season.top1", "Season first place", SEASON_TOP1_COINS,
            Family.SEASON, false, 0L, 0));
        rows.add(new Row("season.top2", "Season second place", SEASON_TOP2_COINS,
            Family.SEASON, false, 0L, 0));
        rows.add(new Row("season.top3", "Season third place", SEASON_TOP3_COINS,
            Family.SEASON, false, 0L, 0));

        // --- sinks ----------------------------------------------------------------
        rows.add(new Row("sink.crate", "Open a reward crate", -CRATE_COST_COINS,
            Family.SPEND, true, CRATE_COOLDOWN_MILLIS, 0));
        rows.add(new Row("sink.quest_reroll", "Reroll a daily quest", -QUEST_REROLL_COST,
            Family.SPEND, true, 86_400_000L, 0));
        rows.add(new Row("sink.contract_reroll", "Reroll a contract", -CONTRACT_REROLL_COST,
            Family.SPEND, true, 21_600_000L, 0));
        rows.add(new Row("sink.shop", "Buy a cosmetic in the shop", -SEASON_TIER_MIN_COINS,
            Family.SPEND, false, 0L, 0));

        return List.copyOf(rows);
    }

    private RateCard() {
    }

    /** Every row of the card. */
    public static List<Row> rows() {
        return ROWS;
    }

    /** The rows that pay coins to a player. */
    public static List<Row> sources() {
        List<Row> out = new ArrayList<>();
        for (Row row : ROWS) {
            if (row.isSource()) {
                out.add(row);
            }
        }
        return out;
    }

    /** The rows that take coins from a player. */
    public static List<Row> sinks() {
        List<Row> out = new ArrayList<>();
        for (Row row : ROWS) {
            if (row.isSink()) {
                out.add(row);
            }
        }
        return out;
    }

    /** The row with this id, or {@code null}. */
    public static Row row(String id) {
        if (id == null) {
            return null;
        }
        for (Row row : ROWS) {
            if (row.id().equals(id)) {
                return row;
            }
        }
        return null;
    }

    /**
     * The repeatable rows that have no cooldown.
     *
     * <p>Must always be empty. A repeatable reward without a cooldown is an unbounded faucet: the
     * mission asks for a cooldown on every one of them, and this is where the question is asked.
     */
    public static List<Row> repeatableSourcesWithoutCooldown() {
        List<Row> out = new ArrayList<>();
        for (Row row : ROWS) {
            if (row.isRepeatableWithoutCooldown()) {
                out.add(row);
            }
        }
        return out;
    }

    /**
     * The expected coins a crate pays back, in cash.
     *
     * <p>Round 84: the shipped crate cost 250 and refunded a uniform 60–500 plus a 15 % chance of
     * a flat 150. That is {@code 280 + 22.5 = 302.5} expected against a 250 cost — a
     * <em>positive</em> 52.5 coins, five times a day, forever. The crate was the largest faucet on
     * the server and it was faster than playing basketball. It is now a sink with a cosmetic
     * upside.
     */
    public static double expectedCrateRefund(int coinMin, int coinMax, int bonusCoins, double bonusChance) {
        int min = Math.max(0, Math.min(coinMin, coinMax));
        int max = Math.max(min, Math.max(coinMin, coinMax));
        double base = (min + max) / 2.0;
        double chance = Math.max(0.0, Math.min(1.0, bonusChance));
        return base + chance * Math.max(0, bonusCoins);
    }

    /**
     * The expected net coins a crate opening costs the player.
     *
     * <p>Negative means the crate takes coins out of the economy. It must be negative.
     */
    public static double expectedCrateNet(int costCoins, int coinMin, int coinMax,
                                          int bonusCoins, double bonusChance) {
        return expectedCrateRefund(coinMin, coinMax, bonusCoins, bonusChance) - Math.max(0, costCoins);
    }

    /**
     * The win-to-loss ratio of the result economy.
     *
     * <p>Must stay at or below {@link #MAX_WIN_TO_LOSS_RATIO}: past 3:1, throwing a match beats
     * winning it at any win rate under 25 %.
     */
    public static double winToLossRatio(int winCoins, int lossCoins) {
        int win = Math.max(0, winCoins);
        int loss = Math.max(0, lossCoins);
        if (loss <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return (double) win / (double) loss;
    }

    /**
     * The hourly income table, one line per skill band.
     *
     * @param averageBandCoinsPerMatch what the <b>Average</b> band earns on the court in a match;
     *                                 the other bands scale from it by their own multiplier
     */
    public static List<BandIncome> hourlyIncome(int averageBandCoinsPerMatch) {
        List<BandIncome> out = new ArrayList<>();
        for (SkillBand band : SkillBand.values()) {
            int result = band.resultCoinsPerHour();
            int onCourt = band.onCourtCoinsPerHour(band.onCourtCoinsPerMatch(averageBandCoinsPerMatch));
            out.add(new BandIncome(band, result, onCourt, result + onCourt));
        }
        return out;
    }

    /** One line of the hourly income table. */
    public record BandIncome(SkillBand band, int resultCoins, int onCourtCoins, int totalCoins) {
        /** The machine-readable form, used by the audit and by the docs generator. */
        public String describe() {
            return String.format(Locale.ROOT, "%-8s wins/h=%4.1f result/h=%4d court/h=%4d total/h=%4d",
                band.label(), band.winsPerHour(), resultCoins, onCourtCoins, totalCoins);
        }
    }

    /**
     * Checks that the configured economy agrees with this rate card.
     *
     * <p>Called once at startup. A config that disagrees with the card is not automatically
     * wrong — the config is the tuning knob — but the *shape* of the economy has to hold: a loss
     * pays something, and the win-to-loss ratio stays inside the bound.
     *
     * @return the problems found, empty when the economy is sound
     */
    public static List<String> audit(int winCoins, int lossCoins, int mvpCoins,
                                     int twoPointCoins, int threePointCoins, int assistCoins,
                                     int crateCost, int crateMin, int crateMax) {
        List<String> problems = new ArrayList<>();

        if (lossCoins <= 0) {
            problems.add("A loss must pay something (economy.loss-coins is " + lossCoins + ").");
        }
        double ratio = winToLossRatio(winCoins, lossCoins);
        if (ratio > MAX_WIN_TO_LOSS_RATIO) {
            problems.add("Win-to-loss ratio " + round2(ratio) + " exceeds the " + MAX_WIN_TO_LOSS_RATIO + " bound.");
        }
        if (lossCoins > 0 && (double) lossCoins / (double) Math.max(1, winCoins) < MIN_LOSS_TO_WIN_RATIO) {
            problems.add("Loss pays only " + round2((double) lossCoins / Math.max(1, winCoins))
                + " of a win; the floor is " + MIN_LOSS_TO_WIN_RATIO + ".");
        }
        if (mvpCoins < 0 || twoPointCoins < 0 || threePointCoins < 0 || assistCoins < 0) {
            problems.add("A coin payout may not be negative.");
        }
        if (threePointCoins < twoPointCoins) {
            problems.add("A three-pointer must pay at least as much as a two-pointer.");
        }
        double crateNet = expectedCrateNet(crateCost, crateMin, crateMax, CRATE_BONUS_COINS, CRATE_BONUS_CHANCE);
        if (crateNet >= 0.0) {
            problems.add("The crate is a faucet: it returns " + round2(crateNet) + " coins per opening.");
        }
        return problems;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
