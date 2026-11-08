package fr.bosketball.physics;

import org.bukkit.util.Vector;

/**
 * Where a charging shooter's ball is, and how big it is drawn.
 *
 * <p>The ball used to be hidden from the shooter while they charged, because a ball held at the
 * eye fills the screen: a 0.6-block ball half a block from the camera covers nearly sixty degrees
 * of a seventy degree field of view. The hide is what made a released shot vanish, so the pose has
 * to be the thing that fixes it instead - the ball is held out and to the side of the shooting
 * hand, where a player actually holds a ball, and it is drawn a little smaller as the string comes
 * back.
 *
 * <p>The numbers below are the whole contract, so they live in one pure class with a test: the ball
 * has to stay out of the crosshair, it has to stay close enough to read as held, and it has to be
 * in front of the shooter at all times.
 */
public final class BallHandPose {

    /** How far in front of the eye the ball is held, in blocks. */
    public static final double FORWARD = 0.78;
    /** How far to the shooting-hand side of the look axis, in blocks. */
    public static final double LATERAL = 0.40;
    /** The ball sits a touch below the eye line, so it never covers the horizon. */
    public static final double EYE_DROP = 0.05;
    /** The gather rises over the charge: this is how far, at a full pull. */
    public static final double CHARGE_RISE = 0.20;
    /** Drawn size when the gather starts, and at a full pull. */
    public static final float SCALE_AT_START = 0.40f;
    public static final float SCALE_AT_FULL = 0.34f;

    /**
     * The point a charging shooter holds the ball at.
     *
     * @param eye       the shooter's eye position
     * @param forward   a horizontal unit vector the shooter is looking along
     * @param right     a horizontal unit vector to the shooter's right
     * @param handSign  {@code +1} for a right-handed release, {@code -1} for a left-handed one
     * @param charge    0 at the start of the gather, 1 at a full pull (clamped)
     */
    public static Vector pocket(Vector eye, Vector forward, Vector right, double handSign, double charge) {
        if (eye == null) {
            return new Vector();
        }
        Vector fwd = horizontalOr(forward, new Vector(0.0, 0.0, 1.0));
        Vector side = horizontalOr(right, new Vector(1.0, 0.0, 0.0));
        double pull = Math.clamp(Double.isFinite(charge) ? charge : 0.0, 0.0, 1.0);
        double sign = handSign >= 0.0 ? 1.0 : -1.0;
        return eye.clone()
            .add(fwd.multiply(FORWARD))
            .add(side.multiply(LATERAL * sign))
            .add(new Vector(0.0, -EYE_DROP + CHARGE_RISE * pull, 0.0));
    }

    /** The size the ball is drawn at while the gather is held at {@code charge}. */
    public static float scale(double charge) {
        double pull = Math.clamp(Double.isFinite(charge) ? charge : 0.0, 0.0, 1.0);
        return (float) (SCALE_AT_START + (SCALE_AT_FULL - SCALE_AT_START) * pull);
    }

    /** How far the pocket is from the eye: the number that decides how much of the screen it eats. */
    public static double distanceFromEye(Vector eye, Vector forward, Vector right, double handSign, double charge) {
        if (eye == null) {
            return 0.0;
        }
        return pocket(eye, forward, right, handSign, charge).distance(eye);
    }

    /** The angle, in degrees, between where the shooter looks and where the ball is held. */
    public static double degreesOffAxis(Vector forward, Vector right, double handSign, double charge) {
        Vector eye = new Vector(0.0, 0.0, 0.0);
        Vector toBall = pocket(eye, forward, right, handSign, charge).normalize();
        Vector look = horizontalOr(forward, new Vector(0.0, 0.0, 1.0));
        double dot = Math.clamp(toBall.dot(look), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    private static Vector horizontalOr(Vector candidate, Vector fallback) {
        if (candidate == null || !MovementMath.isFinite(candidate) || candidate.lengthSquared() < 1.0e-6) {
            return fallback.clone();
        }
        Vector flat = candidate.clone().setY(0.0);
        if (flat.lengthSquared() < 1.0e-6) {
            return fallback.clone();
        }
        return flat.normalize();
    }

    private BallHandPose() {
    }
}
