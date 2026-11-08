package fr.bosketball.physics;

/**
 * Pure timing rules for the jump-shot charge bar.
 *
 * <p>The bar used to be driven exclusively by the vanilla bow draw
 * ({@code Player#isHandRaised()} / {@code getHandRaisedTime()}). Any server-side hiccup
 * on that draw (item sync, cancelled interaction packet, teleport by the arena clamp…)
 * made the charge drop by itself while the player was still holding the button.
 *
 * <p>These rules make the wall clock the source of truth and only use the vanilla draw
 * as a "still holding?" hint, with a short grace window.
 */
public final class ShotChargeRules {

    /** A bow reaches full pull in 20 ticks (1 s). */
    public static final int FULL_CHARGE_TICKS = 20;

    /** Ticks the charge survives after the vanilla draw was lost (before being dropped). */
    public static final int RELEASE_GRACE_TICKS = 5;

    /** Minimum power a release can produce, even on a tap. */
    public static final double MIN_RELEASE_POWER = 0.10;

    /** HUD threshold marking a shot as charged enough to enter the green release window. */
    private static final double MIN_CHARGED_PROGRESS = 0.86;

    /** Number of real milliseconds represented by one server tick. */
    private static final long MILLIS_PER_TICK = 50L;

    private ShotChargeRules() {
    }

    public static int heldTicks(long heldMs) {
        long ticks = Math.max(0L, heldMs / MILLIS_PER_TICK);
        return (int) Math.min(Integer.MAX_VALUE, ticks);
    }

    public static double progressForTicks(int ticksHeld) {
        return PhysicsMath.computeBowPullProgress(ticksHeld);
    }

    public static double progressForMs(long heldMs) {
        return progressForTicks(heldTicks(heldMs));
    }

    /** True once the bar is at/over the "green" release window used by the HUD. */
    public static boolean chargedEnough(double progress) {
        return Double.isFinite(progress) && progress >= MIN_CHARGED_PROGRESS;
    }

    /**
     * Power actually used by the shot. The tracked (server-side) progress always wins over
     * the event force so a spurious release can never fire a weak shot or a free full one.
     */
    public static double resolvePower(boolean hasTracked, double trackedProgress, double eventForce) {
        double power = hasTracked ? Math.max(trackedProgress, eventForce) : eventForce;
        if (!Double.isFinite(power)) {
            power = MIN_RELEASE_POWER;
        }
        return Math.max(MIN_RELEASE_POWER, Math.min(1.0, power));
    }
}
