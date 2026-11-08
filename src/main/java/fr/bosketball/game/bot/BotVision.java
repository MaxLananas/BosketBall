package fr.bosketball.game.bot;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/** A simple forward field of view plus an unobstructed block ray. */
public final class BotVision {
    private static final double MIN_FORWARD_DOT = 0.0;

    private BotVision() {
    }

    public static boolean inFieldOfView(Vector forward, Vector offset) {
        if (forward == null || offset == null || !finite(forward) || !finite(offset)
            || forward.lengthSquared() < 1e-8 || offset.lengthSquared() < 1e-8) {
            return false;
        }
        return forward.dot(offset) > MIN_FORWARD_DOT;
    }

    private static boolean finite(Vector vector) {
        return Double.isFinite(vector.getX()) && Double.isFinite(vector.getY())
            && Double.isFinite(vector.getZ());
    }

    /** Limit a yaw turn to a human-readable step across the -180/180 boundary. */
    public static float turnYaw(float current, float desired, float maxStep) {
        float delta = (desired - current) % 360f;
        if (delta > 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        return current + Math.clamp(delta, -maxStep, maxStep);
    }

    public static boolean canSee(Location eye, Vector target) {
        if (eye == null || eye.getWorld() == null || target == null) {
            return false;
        }
        Vector offset = target.clone().subtract(eye.toVector());
        if (!inFieldOfView(eye.getDirection(), offset)) {
            return false;
        }
        double distance = offset.length();
        return eye.getWorld().rayTraceBlocks(eye, offset.multiply(1.0 / distance), distance,
            FluidCollisionMode.NEVER, true) == null;
    }
}
