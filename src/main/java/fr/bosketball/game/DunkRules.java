package fr.bosketball.game;

import java.util.Locale;

/**
 * Pure rules for the slam-dunk system (no Bukkit types, unit-testable).
 *
 * <p>Design goals of the rewrite:
 * <ul>
 *   <li>one single click is enough — no green-zone mini-game any more;</li>
 *   <li>the aim cone is forgiving, so a player facing the rim always gets the dunk;</li>
 *   <li>the outcome is decided at take-off from distance / contest / fatigue, so a
 *       started dunk always resolves cleanly;</li>
 *   <li>a hard 20&nbsp;s cooldown per player.</li>
 * </ul>
 */
public final class DunkRules {

    /** Hard cooldown between two dunk attempts. */
    public static final long COOLDOWN_MS = 20_000L;

    /** Half-angle of the aim cone accepted for a dunk (degrees). */
    public static final double MAX_AIM_YAW_ERROR_DEG = 42.0;

    /** Extra paint radius granted so the very edge of the paint still dunks. */
    public static final double PAINT_RADIUS_BONUS = 0.55;

    // Distances beyond a full court are treated as the harshest supported attempt, not infinity.
    private static final double MAX_DISTANCE_BLOCKS = 1_000.0;

    // Shared score range keeps any future tuning from creating automatic or impossible outcomes.
    private static final double MIN_SUCCESS_CHANCE = 0.50;
    private static final double MAX_SUCCESS_CHANCE = 0.97;

    // Close clean dunks start generous, while distance, contest and fatigue all lower completion.
    private static final double BASE_SUCCESS_CHANCE = 0.94;
    private static final double DISTANCE_PENALTY_START_BLOCKS = 1.0;
    private static final double DISTANCE_PENALTY_PER_BLOCK = 0.055;
    private static final double CONTEST_PENALTY = 0.28;
    private static final double FATIGUE_PENALTY = 0.15;
    private static final double AIRBORNE_BONUS = 0.04;

    // Dunk flight is an authored animation, kept inside an 11–16 tick window.
    private static final int MIN_FLIGHT_TICKS = 11;
    private static final int MAX_FLIGHT_TICKS = 16;
    private static final double FLIGHT_TICKS_PER_BLOCK = 4.2;
    private static final double FLIGHT_TICK_BASE = 5.0;

    private DunkRules() {
    }

    public static long cooldownRemainingMs(long nowMs, long readyAtMs) {
        if (readyAtMs <= nowMs) {
            return 0L;
        }
        try {
            return Math.subtractExact(readyAtMs, nowMs);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    public static boolean offCooldown(long nowMs, long readyAtMs) {
        return cooldownRemainingMs(nowMs, readyAtMs) <= 0L;
    }

    public static boolean aimedAtRim(double yawErrorDeg) {
        return Double.isFinite(yawErrorDeg) && Math.abs(yawErrorDeg) <= MAX_AIM_YAW_ERROR_DEG;
    }

    /**
     * Chance that a started dunk finishes at the rim. Inputs are clamped before arithmetic, and
     * the result always remains in [0.50, 0.97].
     */
    public static double successChance(double distanceBlocks, double contestRatio, double fatigue, boolean airborne) {
        double distance = Double.isFinite(distanceBlocks)
            ? Math.clamp(distanceBlocks, 0.0, MAX_DISTANCE_BLOCKS) : MAX_DISTANCE_BLOCKS;
        double contest = Double.isFinite(contestRatio) ? Math.clamp(contestRatio, 0.0, 1.0) : 1.0;
        double safeFatigue = Double.isFinite(fatigue) ? Math.clamp(fatigue, 0.0, 1.0) : 1.0;

        double chance = BASE_SUCCESS_CHANCE;
        chance -= Math.max(0.0, distance - DISTANCE_PENALTY_START_BLOCKS) * DISTANCE_PENALTY_PER_BLOCK;
        chance -= contest * CONTEST_PENALTY;
        chance -= safeFatigue * FATIGUE_PENALTY;
        if (airborne) {
            chance += AIRBORNE_BONUS;
        }
        return Math.clamp(chance, MIN_SUCCESS_CHANCE, MAX_SUCCESS_CHANCE);
    }

    /** Short human label used in the action bar, e.g. {@code "14.2s"}. */
    public static String cooldownLabel(long remainingMs) {
        return String.format(Locale.ROOT, "%.1fs", Math.max(0L, remainingMs) / 1000.0);
    }

    /** Number of flight ticks for a dunk started {@code distanceBlocks} away from the rim. */
    public static int flightTicks(double distanceBlocks) {
        double distance = Double.isFinite(distanceBlocks)
            ? Math.clamp(distanceBlocks, 0.0, MAX_DISTANCE_BLOCKS) : MAX_DISTANCE_BLOCKS;
        double rawTicks = distance * FLIGHT_TICKS_PER_BLOCK + FLIGHT_TICK_BASE;
        return (int) Math.clamp(Math.round(rawTicks), MIN_FLIGHT_TICKS, MAX_FLIGHT_TICKS);
    }
}
