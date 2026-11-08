package fr.bosketball.physics;

/**
 * The one place that decides whether a player may be denied the sight of the ball.
 *
 * <p>The ball is an {@code ItemDisplay} that the plugin hides from its holder while a shot is
 * charging, so the shooter does not see a ball stuck in the bow. That hide is a client-side
 * decision ({@code Player#hideEntity}) and nothing on the physics side can undo it: if the ball
 * keeps flying while the shooter is still in the hidden set, the shooter watches their own shot
 * vanish. Every hide therefore goes through these rules, and everything that is not a live charge
 * must be visible to everybody.
 */
public final class BallVisibilityRules {

    /** How often, in ticks, the ball re-declares itself visible to every player around it. */
    public static final int RESYNC_INTERVAL_TICKS = 10;

    private BallVisibilityRules() {
    }

    /**
     * The only case where a viewer may not see the ball: they hold it, it is dribbling in their
     * hands, and they are pulling the string. Anything else - a shot, a pass, a loose ball, a dunk,
     * a viewer who is not the holder - must show the ball.
     */
    public static boolean hideFromViewer(
        boolean viewerHoldsBall,
        boolean ballIsDribbling,
        boolean viewerChargingShot
    ) {
        return viewerHoldsBall && ballIsDribbling && viewerChargingShot;
    }

    /** Convenience inverse, used where a "show it" decision reads better than a "hide it" one. */
    public static boolean mustBeVisibleTo(
        boolean viewerHoldsBall,
        boolean ballIsDribbling,
        boolean viewerChargingShot
    ) {
        return !hideFromViewer(viewerHoldsBall, ballIsDribbling, viewerChargingShot);
    }
}
