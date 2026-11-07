package fr.bosketball.physics;

/**
 * Pure rules of the ball-steal system (Round 27).
 *
 * <p>The old implementation mixed four bugs in one block: the reach gate used an {@code AND}
 * (so a defender standing six blocks away could strip the ball as long as the ball itself was
 * close), the success chance was clamped twice and then multiplied by 0.58, the "poked loose"
 * deflection counted as a steal, and the streak was incremented on every attempt — including
 * misses — so spamming right-click permanently destroyed your own odds.
 *
 * <p>Everything here is deterministic and unit-tested; {@code MatchSession} only applies the
 * result.
 */
public final class StealRules {

    /** Absurd distances are rejected before any roll. */
    public static final double MAX_PLAYER_DISTANCE = 3.0;
    public static final double MAX_BALL_DISTANCE = 3.15;
    public static final double MIN_CLEAN_CHANCE = 0.10;
    public static final double MAX_CLEAN_CHANCE = 0.62;
    public static final double POKE_BONUS = 0.24;
    public static final double MAX_POKE_CHANCE = 0.88;
    public static final int MAX_STREAK = 3;

    private StealRules() {
    }

    /** The defender must be close to the handler <b>and</b> to the ball — never "one of them". */
    public static boolean inReach(double playerDistance, double ballDistance) {
        if (!Double.isFinite(playerDistance) || !Double.isFinite(ballDistance)) {
            return false;
        }
        return playerDistance <= MAX_PLAYER_DISTANCE && ballDistance <= MAX_BALL_DISTANCE;
    }

    /**
     * Chance of a clean steal.
     *
     * @param exposedSide defender attacks the dribbling hand
     * @param sprinting   the handler is sprinting (worse control)
     * @param charging    the handler is charging a shot
     * @param protecting  the handler is sneaking (ball protected)
     * @param streak      consecutive clean steals already made by this defender
     */
    public static double cleanChance(
        boolean exposedSide,
        boolean sprinting,
        boolean charging,
        boolean protecting,
        int streak
    ) {
        int safeStreak = Math.clamp(streak, 0, MAX_STREAK);
        double chance = 0.40
            + (exposedSide ? 0.18 : 0.0)
            + (sprinting ? 0.10 : 0.0)
            + (charging ? 0.12 : 0.0)
            - (protecting ? 0.16 : 0.0)
            - 0.07 * safeStreak;
        return Math.clamp(chance, MIN_CLEAN_CHANCE, MAX_CLEAN_CHANCE);
    }

    /** A deflection (ball knocked loose, nobody gains it) is easier than a clean steal. */
    public static double pokeChance(double cleanChance) {
        return Math.min(MAX_POKE_CHANCE, cleanChance + POKE_BONUS);
    }

    /** Cooldown between two attempts; a defender who just stole waits a little longer. */
    public static long cooldownMs(long baseCooldownMs, int streak) {
        int safeStreak = Math.clamp(streak, 0, MAX_STREAK);
        return Math.max(300L, baseCooldownMs) + 450L * safeStreak;
    }

    /** The streak only survives while the defender keeps stealing (6 s window). */
    public static int decayStreak(int streak, long sinceLastSuccessMs) {
        if (streak <= 0) {
            return 0;
        }
        return sinceLastSuccessMs > 6000L ? 0 : Math.clamp(streak, 0, MAX_STREAK);
    }

    /** Result of a roll. */
    public enum Outcome { CLEAN_STEAL, POKED_LOOSE, MISSED }

    public static Outcome resolve(double roll, double cleanChance, double pokeChance) {
        if (roll <= cleanChance) {
            return Outcome.CLEAN_STEAL;
        }
        return roll <= pokeChance ? Outcome.POKED_LOOSE : Outcome.MISSED;
    }
}
