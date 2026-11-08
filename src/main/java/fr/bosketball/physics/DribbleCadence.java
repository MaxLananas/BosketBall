package fr.bosketball.physics;

/** Pure cadence curve for a held-ball dribble, driven by movement speed and crossover recovery. */
public final class DribbleCadence {

    /** Mirrors the shipped 1,650 ms crossover cooldown at 20 server ticks per second. */
    public static final int DEFAULT_CROSSOVER_COOLDOWN_TICKS = 33;

    // A stationary, unprotected dribble keeps the existing readable beat of about one bounce per ten ticks.
    private static final double BASE_CYCLE_STEP = 0.095;

    // Faster horizontal movement advances the cycle; this is the maximum added phase per tick.
    private static final double SPEED_CYCLE_GAIN = 0.055;

    // Speeds above this are treated as the fastest normal player run, avoiding unbounded cadence.
    private static final double MAX_NORMAL_SPEED_BLOCKS_PER_TICK = 0.55;

    // Protecting changes hand placement and slightly increases the cadence so the ball stays close.
    private static final double PROTECTING_CYCLE_BONUS = 0.020;

    // A fresh crossover eases the bounce for a short recovery rather than snapping to full speed.
    private static final double COOLDOWN_SLOWDOWN = 0.18;

    // Keep the phase advance positive and below a full cycle to prevent skipped beat crossings.
    private static final double MIN_CYCLE_STEP = 0.045;
    private static final double MAX_CYCLE_STEP = 0.16;

    private DribbleCadence() {
    }

    /**
     * Returns a finite phase increment in [0.045, 0.16]. Invalid speed is treated as standing
     * still; invalid cooldown fraction is treated as a fresh crossover (the conservative case).
     *
     * @param horizontalSpeedBlocksPerTick holder's horizontal speed in blocks per server tick
     * @param protecting                    whether the holder is protecting the ball
     * @param remainingCooldownFraction     remaining crossover cooldown in [0, 1]
     */
    public static double cycleStep(double horizontalSpeedBlocksPerTick, boolean protecting,
                                   double remainingCooldownFraction) {
        double speed = Double.isFinite(horizontalSpeedBlocksPerTick)
            ? Math.clamp(horizontalSpeedBlocksPerTick, 0.0, MAX_NORMAL_SPEED_BLOCKS_PER_TICK)
            : 0.0;
        double cooldown = Double.isFinite(remainingCooldownFraction)
            ? Math.clamp(remainingCooldownFraction, 0.0, 1.0)
            : 1.0;
        double speedFraction = speed / MAX_NORMAL_SPEED_BLOCKS_PER_TICK;
        double cadence = BASE_CYCLE_STEP + SPEED_CYCLE_GAIN * speedFraction
            + (protecting ? PROTECTING_CYCLE_BONUS : 0.0);
        cadence *= 1.0 - COOLDOWN_SLOWDOWN * cooldown;
        return Math.clamp(cadence, MIN_CYCLE_STEP, MAX_CYCLE_STEP);
    }
}
