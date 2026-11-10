package fr.bosketball.util;

import org.bukkit.Location;

/**
 * Distance between two locations, without the crash.
 *
 * <p>{@code Location#distance} and {@code Location#distanceSquared} throw when the two locations
 * are in different worlds. That exception lands in the middle of a tick or an event handler, where
 * it costs a whole court or a whole interaction: a player in the lobby measured against a court
 * entity is an internal error, not a distance.
 */
public final class Distances {

    private Distances() {
    }

    /**
     * Squared distance, or {@link Double#MAX_VALUE} when the two locations are not comparable
     * (different worlds, a missing world). Every "is it close enough" test keeps working, because
     * MAX_VALUE never compares as close.
     */
    public static double squared(Location a, Location b) {
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        boolean aWorld = a.getWorld() != null;
        boolean bWorld = b.getWorld() != null;
        if (aWorld != bWorld) {
            return Double.MAX_VALUE;
        }
        if (aWorld && !a.getWorld().equals(b.getWorld())) {
            return Double.MAX_VALUE;
        }
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        double squared = dx * dx + dy * dy + dz * dz;
        return Double.isFinite(squared) ? squared : Double.MAX_VALUE;
    }

    /** Plain distance, {@link Double#MAX_VALUE} when the two locations are not comparable. */
    public static double between(Location a, Location b) {
        double squared = squared(a, b);
        return squared == Double.MAX_VALUE ? Double.MAX_VALUE : Math.sqrt(squared);
    }
}
