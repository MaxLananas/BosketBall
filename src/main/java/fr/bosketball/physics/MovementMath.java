package fr.bosketball.physics;

import org.bukkit.util.Vector;

/**
 * Pure movement helpers (no server state) used by the directional dash.
 */
public final class MovementMath {

    /** Below this horizontal speed (blocks/tick) the velocity no longer reflects the WASD input. */
    public static final double MIN_INPUT_SPEED = 0.045;

    private MovementMath() {
    }

    /**
     * Direction the player is actually steering toward.
     *
     * <p>Server side we cannot read WASD directly, but the horizontal velocity of a
     * walking / strafing player is driven by that input, so it is the most faithful
     * proxy: press A and the dash goes left, press S and it goes backwards, release
     * everything and it follows the crosshair instead.
     *
     * @param velocity  current player velocity (may be null)
     * @param lookDir   current look direction (may be null)
     * @return a normalised horizontal vector, or a zero vector when nothing is usable
     */
    public static Vector dashDirection(Vector velocity, Vector lookDir) {
        if (velocity != null) {
            Vector flat = new Vector(velocity.getX(), 0.0, velocity.getZ());
            if (flat.lengthSquared() >= MIN_INPUT_SPEED * MIN_INPUT_SPEED) {
                return flat.normalize();
            }
        }
        if (lookDir != null) {
            Vector flat = new Vector(lookDir.getX(), 0.0, lookDir.getZ());
            if (flat.lengthSquared() > 1.0e-6) {
                return flat.normalize();
            }
        }
        return new Vector(0.0, 0.0, 0.0);
    }

    /**
     * Final dash impulse for a steered direction. The sideways/forward squash keeps the
     * feel of a basketball burst while still honouring exactly where the player steers.
     */
    public static Vector dashVelocity(Vector direction, double strength, double lift) {
        Vector dir = direction == null ? new Vector(0, 0, 0)
            : new Vector(direction.getX(), 0.0, direction.getZ());
        if (dir.lengthSquared() > 1.0e-6) {
            dir.normalize().multiply(strength);
        }
        dir.setY(lift);
        return dir;
    }
}
