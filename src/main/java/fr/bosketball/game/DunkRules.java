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

    private DunkRules() {
    }

    public static long cooldownRemainingMs(long nowMs, long readyAtMs) {
        return Math.max(0L, readyAtMs - nowMs);
    }

    public static boolean offCooldown(long nowMs, long readyAtMs) {
        return cooldownRemainingMs(nowMs, readyAtMs) <= 0L;
    }

    public static boolean aimedAtRim(double yawErrorDeg) {
        return Double.isFinite(yawErrorDeg) && Math.abs(yawErrorDeg) <= MAX_AIM_YAW_ERROR_DEG;
    }

    /**
     * Chance that a started dunk finishes at the rim. Always generous: even a badly
     * contested dunk keeps a decent chance, and a clean one nearly always drops.
     */
    public static double successChance(double distanceBlocks, double contestRatio, double fatigue, boolean airborne) {
        double dist = Math.max(0.0, distanceBlocks);
        double chance = 0.94;
        chance -= Math.max(0.0, dist - 1.0) * 0.055;
        chance -= Math.min(1.0, Math.max(0.0, contestRatio)) * 0.28;
        chance -= Math.min(1.0, Math.max(0.0, fatigue)) * 0.15;
        if (airborne) {
            chance += 0.04;
        }
        return Math.max(0.50, Math.min(0.97, chance));
    }

    /** Short human label used in the action bar, e.g. {@code "14.2s"}. */
    public static String cooldownLabel(long remainingMs) {
        return String.format(Locale.ROOT, "%.1fs", Math.max(0L, remainingMs) / 1000.0);
    }

    /** Number of flight ticks for a dunk started {@code distanceBlocks} away from the rim. */
    public static int flightTicks(double distanceBlocks) {
        double dist = Math.max(0.0, distanceBlocks);
        return Math.max(11, Math.min(16, (int) Math.round(dist * 4.2) + 5));
    }
}
