package fr.bosketball.physics;

import org.bukkit.util.Vector;

/**
 * The numbers behind a pass, in one testable place.
 *
 * <p>Two things used to be taken on trust. The pass was aimed at the receiver's chest plus six
 * ticks of their own velocity, and a receiver who had just been given a dash, a knockback or a
 * teleport by another plugin handed the ball a target tens of blocks away: the ball flew off the
 * court and the passer never saw it again. And a receiver standing on the release point produced a
 * zero-length delta, whose normalized form is the zero vector - a pass that simply fell at the
 * passer's feet.
 */
public final class PassRules {

    /** A pass leads its receiver, but never further than this per tick of their movement. */
    public static final double MAX_LEAD_BLOCKS_PER_TICK = 0.6;

    /** How many ticks of the receiver's movement the pass aims ahead of them. */
    public static final double LEAD_TICKS = 6.0;

    public static final double MIN_PASS_SPEED = 0.78;
    public static final double MAX_PASS_SPEED = 1.35;
    public static final double SPEED_PER_BLOCK = 0.14;
    public static final double PASS_LIFT = 0.08;

    private PassRules() {
    }

    /**
     * Where the pass is aimed: the receiver's chest, led by their movement, with the lead capped.
     */
    public static Vector leadTarget(Vector chest, Vector receiverVelocity) {
        Vector target = chest == null ? new Vector(0.0, 0.0, 0.0) : chest.clone();
        if (!MovementMath.isFinite(target)) {
            return new Vector(0.0, 0.0, 0.0);
        }
        Vector lead = receiverVelocity == null ? new Vector(0.0, 0.0, 0.0) : receiverVelocity.clone();
        if (!MovementMath.isFinite(lead)) {
            return target;
        }
        if (lead.length() > MAX_LEAD_BLOCKS_PER_TICK) {
            lead.normalize().multiply(MAX_LEAD_BLOCKS_PER_TICK);
        }
        return target.add(lead.multiply(LEAD_TICKS));
    }

    /**
     * The velocity of a pass from the passer to {@code delta}. A receiver standing on the passer has
     * no direction to give, so the passer's own look direction is used instead of a zero vector.
     */
    public static Vector velocityTo(Vector delta, Vector fallbackDirection) {
        Vector fallback = fallbackDirection == null ? new Vector(0.0, 0.0, 1.0) : fallbackDirection.clone();
        if (!MovementMath.isFinite(fallback) || fallback.lengthSquared() < 1.0e-6) {
            fallback = new Vector(0.0, 0.0, 1.0);
        }
        fallback.normalize();

        if (delta == null || !MovementMath.isFinite(delta) || delta.lengthSquared() < 1.0e-6) {
            return fallback.multiply(MIN_PASS_SPEED).add(new Vector(0.0, PASS_LIFT, 0.0));
        }
        double speed = Math.min(MAX_PASS_SPEED,
            Math.max(MIN_PASS_SPEED, Math.sqrt(delta.lengthSquared()) * SPEED_PER_BLOCK));
        return delta.clone().normalize().multiply(speed).add(new Vector(0.0, PASS_LIFT, 0.0));
    }
}
