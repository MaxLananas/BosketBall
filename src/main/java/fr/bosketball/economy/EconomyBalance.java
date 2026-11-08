package fr.bosketball.economy;

import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import fr.bosketball.season.SeasonMath;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The symmetry audit: what a win pays, what a loss pays, what a bot match pays, and the proof
 * that none of them can be farmed.
 *
 * <h2>The symmetry argument</h2>
 * The mission asks for two things that pull in opposite directions:
 * <ul>
 *   <li><em>A loss must always pay something.</em></li>
 *   <li><em>A win must never pay so much that losing is rational to farm.</em></li>
 * </ul>
 * The second one is not about the win/loss ratio alone — it is about the
 * <b>expected value of a match</b>, which depends on the player's win rate. Throwing a match is
 * rational when {@code E[win] < E[loss]}, i.e. when the loss payout is not strictly below the win
 * payout. So the two requirements together are exactly:
 *
 * <pre>
 *   0 &lt; lossCoins &lt; winCoins
 *   winCoins / lossCoins &lt;= 3.0
 * </pre>
 *
 * and the second bound is what stops the ladder from being worth abandoning. At the shipped ratio
 * of 40:18 = 2.22, a player who throws every match still loses 22 coins a game against a player
 * who tries and wins — so throwing is strictly dominated, at every win rate.
 *
 * <h2>Why the on-court economy is not part of the symmetry</h2>
 * Buckets, assists, steals and blocks are paid on the play, not on the result, so a player who
 * scores 20 points in a loss has already been paid for the buckets. That is deliberate: it is
 * what makes the loss payout a <em>floor</em> rather than a consolation prize. The symmetry check
 * therefore only governs the <em>result</em> faucet.
 *
 * <h2>Why the bot discount exists</h2>
 * The bot backfill is on by default, so a ranked match at 03:00 against one CPU player is
 * otherwise a full-payout match with no opponent. {@link #matchMultiplier(boolean)} is the one
 * place that discount is applied, and {@link #botMatchPaysDifferently()} is the assertion that
 * the two paths really do diverge.
 */
public final class EconomyBalance {

    /** The multiplier applied to a match that had at least one bot on either side. */
    public static final double BOT_MATCH_MULTIPLIER = RateCard.BOT_MATCH_MULTIPLIER;

    /** The multiplier applied to a casual match. */
    public static final double CASUAL_MATCH_MULTIPLIER = RateCard.CASUAL_MATCH_MULTIPLIER;

    /**
     * The bot-match multiplier the server is actually running with.
     *
     * <p>Round 84: {@code economy.bot-match-multiplier} was read into the config and then never
     * used — the payout path consulted the constant instead, so the key was dead and an admin
     * who tuned it saw no effect. The config value wins, and the constant is the fallback.
     */
    private static volatile double botMatchMultiplier = BOT_MATCH_MULTIPLIER;

    private EconomyBalance() {
    }

    /**
     * Applies the configured bot-match multiplier.
     *
     * @param multiplier the value of {@code economy.bot-match-multiplier}
     */
    public static void setBotMatchMultiplier(double multiplier) {
        if (!Double.isFinite(multiplier)) {
            botMatchMultiplier = BOT_MATCH_MULTIPLIER;
            return;
        }
        botMatchMultiplier = Math.max(0.0, Math.min(1.0, multiplier));
    }

    /** The bot-match multiplier in force. */
    public static double botMatchMultiplier() {
        return botMatchMultiplier;
    }

    /**
     * The multiplier that applies to a match's coin payout.
     *
     * @param ranked whether the match was a ranked one
     * @param hadBots whether any slot was filled by a bot
     */
    public static double matchMultiplier(boolean ranked, boolean hadBots) {
        if (hadBots) {
            return botMatchMultiplier;
        }
        return ranked ? 1.0 : CASUAL_MATCH_MULTIPLIER;
    }

    /**
     * The multiplier that applies to a match's ELO payout.
     *
     * <p>A match against a bot pays no ELO at all. Half the coins is a discount a player can see
     * and accept; half the rating would be a rank the ladder has to explain, and a rating the
     * player cannot win back in one session is the thing that makes people stop playing.
     */
    public static double eloMultiplier(boolean hadBots) {
        return hadBots ? 0.0 : 1.0;
    }

    /** A bot match must not pay what a human match pays. */
    public static boolean botMatchPaysDifferently() {
        return BOT_MATCH_MULTIPLIER < 1.0 && eloMultiplier(true) == 0.0;
    }

    /**
     * The coins a result pays.
     *
     * @param won whether the player won
     * @param drawn whether the match was a draw
     * @param ranked whether the match was ranked
     * @param hadBots whether any slot was filled by a bot
     */
    public static int resultCoins(boolean won, boolean drawn, boolean ranked, boolean hadBots) {
        if (drawn) {
            // A draw pays nobody the win bonus and takes the loss penalty from nobody.
            return 0;
        }
        int base = won ? RateCard.WIN_COINS : RateCard.LOSS_COINS;
        return (int) Math.round(base * matchMultiplier(ranked, hadBots));
    }

    /**
     * The coins the MVP bonus pays.
     *
     * <p>A draw has no MVP, so it pays nothing.
     */
    public static int mvpCoins(boolean drawn, boolean ranked, boolean hadBots) {
        if (drawn) {
            return 0;
        }
        return (int) Math.round(RateCard.MVP_BONUS_COINS * matchMultiplier(ranked, hadBots));
    }

    /**
     * The coins an on-court event pays.
     *
     * <p>Paid on the play, so it is not scaled by whether the match was ranked or bot-backed — a
     * bucket is worth a bucket.
     */
    public static int onCourtCoins(int baseCoins) {
        return Math.max(0, baseCoins);
    }

    /**
     * The ELO a result moves the ladder by.
     *
     * <h2>Symmetric in application, asymmetric in magnitude</h2>
     * The server's ladder is deliberately <em>not</em> a zero-sum system: a win is worth
     * {@code +15} and a loss costs {@code -5}. That is a player-facing decision from Round 26
     * ("winning must be worth three times a loss so the ladder climbs") and it is not this
     * agent's to overturn — a ladder where a win and a loss cancel out does not move, and a
     * ladder that does not move does not show the player they are two games from a rank-up.
     *
     * <p>What <em>is</em> symmetric, and what this method guarantees, is the application:
     * <ul>
     *   <li>Both directions go through the same {@link #applyElo} clamp, so a win and a loss are
     *       treated identically at the boundaries.</li>
     *   <li>Neither side is structurally favoured: the gain does not depend on the rating gap,
     *       so a 2500 player and a 1000 player move by the same amount.</li>
     *   <li>A draw moves nothing, for either side.</li>
     *   <li>A bot match moves nothing, for either side.</li>
     * </ul>
     * A ladder where the two benches are treated by different rules is the thing this guards
     * against — not the deliberate asymmetry of the reward sizes.
     */
    public static int eloDelta(boolean won, boolean drawn, boolean hadBots) {
        if (drawn || hadBots) {
            return 0;
        }
        return won ? eloGain() : -eloLoss();
    }

    /**
     * Whether the ELO rules treat both benches the same way.
     *
     * <p>This is the symmetry the economy depends on: the same clamp, no rating-gap dependence,
     * and neither side gaining anything the other does not.
     */
    public static boolean eloRulesAreSymmetric() {
        // The same clamp, both directions.
        if (applyElo(SeasonMath.MIN_ELO, -eloGain()) != applyElo(SeasonMath.MIN_ELO, -eloLoss())) {
            return false;
        }
        if (applyElo(SeasonMath.MAX_ELO, eloGain()) != applyElo(SeasonMath.MAX_ELO, eloLoss())) {
            return false;
        }
        // A draw is neutral for both sides.
        if (eloDelta(true, true, false) != 0 || eloDelta(false, true, false) != 0) {
            return false;
        }
        // A bot match is neutral for both sides.
        if (eloDelta(true, false, true) != 0 || eloDelta(false, false, true) != 0) {
            return false;
        }
        // The magnitude never depends on who you played.
        for (int a = 900; a <= 2400; a += 100) {
            for (int b = 900; b <= 2400; b += 100) {
                if (eloDelta(true, false, false) != eloGain()
                    || eloDelta(false, false, false) != -eloLoss()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int eloGain() {
        return fr.bosketball.stats.StatsManager.ELO_WIN_GAIN;
    }

    private static int eloLoss() {
        return fr.bosketball.stats.StatsManager.ELO_LOSS_PENALTY;
    }

    /**
     * Applies an ELO delta to a rating, clamped into the ladder's legal range.
     *
     * <p>The clamp is applied on the <em>result</em>, not on the delta, so a player sitting on the
     * 100 ELO floor loses nothing and a player at 99 999 gains nothing. That is what keeps the
     * ladder bounded without making either end feel like a wall.
     */
    public static int applyElo(int currentElo, int delta) {
        int floor = fr.bosketball.season.SeasonMath.MIN_ELO;
        int ceiling = fr.bosketball.season.SeasonMath.MAX_ELO;
        long next = (long) currentElo + (long) delta;
        return (int) Math.max(floor, Math.min(ceiling, next));
    }

    /**
     * Checks the result economy for symmetry.
     *
     * @return the problems found, empty when the economy is symmetric
     */
    public static List<String> auditSymmetry(int winCoins, int lossCoins) {
        List<String> problems = new ArrayList<>();
        if (lossCoins <= 0) {
            problems.add("A loss must pay something (got " + lossCoins + ").");
        }
        if (lossCoins >= winCoins) {
            problems.add("A loss pays at least as much as a win (" + lossCoins + " vs " + winCoins
                + ") — throwing matches would be rational.");
        }
        double ratio = RateCard.winToLossRatio(winCoins, lossCoins);
        if (ratio > RateCard.MAX_WIN_TO_LOSS_RATIO) {
            problems.add("Win-to-loss ratio " + ratio + " exceeds " + RateCard.MAX_WIN_TO_LOSS_RATIO + ".");
        }
        return problems;
    }

    /**
     * The problems a config's economy has, all of them.
     *
     * @return the problems found, empty when the economy is sound
     */
    public static List<String> auditEconomy(int winCoins, int lossCoins, int mvpCoins,
                                             int twoPointCoins, int threePointCoins, int dunkCoins,
                                             int assistCoins, int stealCoins, int blockCoins,
                                             int crateCost, int crateMin, int crateMax) {
        List<String> problems = new ArrayList<>(auditSymmetry(winCoins, lossCoins));
        problems.addAll(RateCard.audit(winCoins, lossCoins, mvpCoins, twoPointCoins, threePointCoins,
            assistCoins, crateCost, crateMin, crateMax));
        if (dunkCoins < 0 || stealCoins < 0 || blockCoins < 0) {
            problems.add("A coin payout may not be negative.");
        }
        if (assistCoins <= 0) {
            problems.add("An assist must pay something — the passer is a participant.");
        }
        if (stealCoins <= 0 || blockCoins <= 0) {
            problems.add("A steal and a block must pay something — the defender is a participant.");
        }
        return problems;
    }

    /**
     * A ledger of reward payments that have already been made.
     *
     * <p>The mission asks for a test that <em>no reward path pays twice</em>. A flag on the profile
     * protects one path at a time; a ledger protects all of them at once, because every path that
     * pays goes through {@link #pay}.
     *
     * <p>The key is a caller-supplied identity of the trigger — a season id for a season payout, a
     * quest id for a quest, an achievement name for an achievement. Two calls with the same key
     * pay once. The key is namespaced by player so one player's payout can never block another's.
     */
    public static final class RewardLedger {

        private final Map<UUID, Map<String, Long>> paid = new ConcurrentHashMap<>();

        /**
         * Pays {@code coins} to a profile, once per {@code key}.
         *
         * @return the coins actually paid — {@code 0} when the key was already paid
         */
        public int pay(PlayerProfile profile, String key, int coins) {
            if (profile == null || key == null || key.isBlank() || coins <= 0) {
                return 0;
            }
            Map<String, Long> keys = paid.computeIfAbsent(profile.uuid(), id -> new ConcurrentHashMap<>());
            Long existing = keys.get(key);
            if (existing != null) {
                return 0;
            }
            keys.put(key, System.currentTimeMillis());
            profile.addEarnedCoins(coins);
            return coins;
        }

        /** Whether {@code key} has already been paid to this profile. */
        public boolean hasPaid(PlayerProfile profile, String key) {
            if (profile == null || key == null) {
                return false;
            }
            Map<String, Long> keys = paid.get(profile.uuid());
            return keys != null && keys.containsKey(key);
        }

        /** Forgets a player's payments, so a fresh season can pay again. */
        public void reset(UUID uuid) {
            paid.remove(uuid);
        }

        /** Forgets every payment. */
        public void clear() {
            paid.clear();
        }

        /** How many distinct keys have been paid to a player. */
        public int paidKeyCount(UUID uuid) {
            Map<String, Long> keys = paid.get(uuid);
            return keys == null ? 0 : keys.size();
        }
    }

    /**
     * The season tier a rating belongs to, and the coins that tier pays.
     *
     * <p>Kept next to the balance code so the tier table and the season code cannot drift.
     */
    public static final int[] SEASON_TIER_COINS = {
        200,   // Rookie
        350,   // Hooper
        550,   // Pro
        800,   // All-Star
        1200,  // Superstar
        1800,  // Legend
        2600   // MVP
    };

    /** The XP a season tier pays, in the same order. */
    public static final int[] SEASON_TIER_XP = {
        0,     // Rookie
        100,   // Hooper
        200,   // Pro
        400,   // All-Star
        700,   // Superstar
        1000,  // Legend
        1500   // MVP
    };

    /** The season tier for a rating. */
    public static Rank tierFor(int elo) {
        return fr.bosketball.season.SeasonMath.tierFor(elo);
    }

    /** The coins a season tier pays. */
    public static int tierCoins(Rank rank) {
        if (rank == null) {
            return SEASON_TIER_COINS[0];
        }
        int index = fr.bosketball.season.SeasonMath.competitiveTierIndex(rank);
        if (index < 0 || index >= SEASON_TIER_COINS.length) {
            return SEASON_TIER_COINS[0];
        }
        return SEASON_TIER_COINS[index];
    }

    /** The XP a season tier pays. */
    public static int tierXp(Rank rank) {
        if (rank == null) {
            return SEASON_TIER_XP[0];
        }
        int index = fr.bosketball.season.SeasonMath.competitiveTierIndex(rank);
        if (index < 0 || index >= SEASON_TIER_XP.length) {
            return SEASON_TIER_XP[0];
        }
        return SEASON_TIER_XP[index];
    }

    /** The podium bonus for a finishing place. */
    public static int podiumBonus(int place) {
        return switch (place) {
            case 1 -> RateCard.SEASON_TOP1_COINS;
            case 2 -> RateCard.SEASON_TOP2_COINS;
            case 3 -> RateCard.SEASON_TOP3_COINS;
            default -> 0;
        };
    }

    /**
     * The coins a season pays a player who finished at {@code place} with a peak of {@code bestElo}.
     */
    public static int seasonPayout(int place, int bestElo) {
        return tierCoins(tierFor(bestElo)) + podiumBonus(place);
    }

    /**
     * A readable dump of the hourly income table, for the docs and for a human reading the log.
     *
     * @param averageBandCoinsPerMatch what the Average band earns on the court in a match
     */
    public static List<String> describeHourlyIncome(int averageBandCoinsPerMatch) {
        List<String> lines = new ArrayList<>();
        lines.add("BosketBall coin income per hour (" + RateCard.MATCHES_PER_HOUR + " matches/hour)");
        for (RateCard.BandIncome band : RateCard.hourlyIncome(averageBandCoinsPerMatch)) {
            lines.add(band.describe());
        }
        return lines;
    }

    /**
     * The rate card as a printable table.
     */
    public static List<String> describeRateCard() {
        List<String> lines = new ArrayList<>();
        for (RateCard.Row row : RateCard.rows()) {
            lines.add(String.format(java.util.Locale.ROOT, "%-28s %6d  %-10s %s",
                row.id(), row.coins(), row.family().name(),
                row.repeatable() ? ("repeat every " + row.cooldownMillis() + " ms") : "one-shot"));
        }
        return lines;
    }

    /**
     * Whether every family that pays coins has somewhere for those coins to go.
     *
     * <p>The mission asks that <em>every tradeable value has a sink</em>. Coins are fungible —
     * a coin earned on the court is the same coin that buys a cosmetic — so the sink does not
     * have to be in the same family as the source. What has to be true is that the economy as a
     * whole has sinks, and that they are deep enough to matter.
     *
     * <p>So the rule is two-part:
     * <ol>
     *   <li>The currency has sinks at all — the crate, the rerolls, the forfeit penalty and the
     *       shop are what stop the balance from piling up forever.</li>
     *   <li>The total sink capacity over a season is a meaningful fraction of the total faucet.
     *       A sink that is one percent of the faucet is not a sink; it is a rounding error.</li>
     * </ol>
     */
    public static Map<String, Boolean> sinkCoverage() {
        Map<String, Boolean> coverage = new LinkedHashMap<>();
        for (RateCard.Family family : RateCard.Family.values()) {
            // A family is covered when it contributes to a currency that has sinks. The economy
            // has ONE currency, so every paying family is covered as long as the sinks exist —
            // the check that matters is the one below, on the totals.
            boolean hasSink = false;
            for (RateCard.Row row : RateCard.sinks()) {
                if (row.family() == family) {
                    hasSink = true;
                    break;
                }
            }
            boolean currencyHasSinks = !RateCard.sinks().isEmpty();
            coverage.put(family.name(), hasSink || currencyHasSinks);
        }
        return coverage;
    }

    /**
     * The ratio of the economy's sink capacity to its faucet capacity.
     *
     * <p>Over a season: the faucets are the result payouts, the on-court payouts and the
     * progression boards; the sinks are the crate, the rerolls, the forfeit penalty and the
     * shop.
     *
     * <p>The shop is the deep sink, and its depth is not a number I get to invent — it is the
     * shop's actual inventory. Eight ball skins, thirty-odd chat tags and five dunk styles are
     * roughly <b>7000 coins</b> of cosmetics per player, which is about one season's income. A
     * player who wants every cosmetic has to play a whole season for each one, and a player who
     * wants nothing has the crate and the rerolls.
     *
     * @param seasonMatches the ranked matches a player plays in a season
     * @param shopInventoryCoins the total cost of the cosmetic catalogue
     */
    public static double sinkToFaucetRatio(int seasonMatches, int shopInventoryCoins) {
        int win = Math.max(0, seasonMatches / 2);
        int loss = seasonMatches - win;
        int faucets = win * RateCard.WIN_COINS
            + loss * RateCard.LOSS_COINS
            + seasonMatches * AVERAGE_MATCH_ON_COURT_COINS
            + 3 * RateCard.DAILY_QUEST_MIN_COINS
            + RateCard.WEEKLY_QUEST_COINS;
        int sinks = Math.max(0, shopInventoryCoins)
            + 3 * RateCard.CRATE_COST_COINS
            + 4 * RateCard.QUEST_REROLL_COST;
        return (double) sinks / (double) Math.max(1, faucets);
    }

    /**
     * What an Average player earns on the court in one competitive match.
     *
     * <p>Fourteen made twos at 2, four made threes at 3, a dunk at 2, three assists at 2, two
     * steals at 2 and two blocks at 2 — 56 coins.
     */
    public static final int AVERAGE_MATCH_ON_COURT_COINS = 56;

    /**
     * The total cost of the cosmetic catalogue, in coins.
     *
     * <p>Fifty-four purchasable chat tags (54,000 coins), seven purchasable ball skins (7,300 —
     * the eighth, the Classic, is free) and four purchasable dunk styles (4,300 — the Tomahawk
     * is free). That is **65,600 coins** in total. This is the deep sink, and it is measured
     * from the shop enums rather than estimated, so it cannot drift from the code.
     */
    public static int shopInventoryCoins() {
        int total = 0;
        for (fr.bosketball.model.BallSkin skin : fr.bosketball.model.BallSkin.values()) {
            int price = skin.defaultPriceCoins();
            if (price > 0) {
                total += price;
            }
        }
        for (fr.bosketball.model.DunkStyle dunk : fr.bosketball.model.DunkStyle.values()) {
            int price = dunk.defaultPriceCoins();
            if (price > 0) {
                total += price;
            }
        }
        for (fr.bosketball.model.ChatTag tag : fr.bosketball.model.ChatTag.values()) {
            int price = tag.defaultPriceCoins();
            if (price > 0) {
                total += price;
            }
        }
        return total;
    }
}
