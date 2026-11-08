package fr.bosketball.physics;

import org.bukkit.util.Vector;

/** Headless swept-point/AABB helper used to verify fast ball motion against thin block geometry. */
public final class SweptCollision {

    // Parallel movement is tested as stationary on that axis to avoid unstable division by tiny values.
    private static final double PARALLEL_EPSILON = 1.0e-10;

    private SweptCollision() {
    }

    /**
     * Returns the moving sphere centre at its first contact with an AABB, or {@code null} when
     * there is no contact. Expanding by the sphere radius is the axis-aligned Minkowski sweep;
     * it is exact for a face-on wall and conservative near box corners.
     *
     * @param start       sphere centre at the start of this movement interval
     * @param displacement full displacement over the interval, not a velocity
     * @param minX        obstacle lower X bound
     * @param minY        obstacle lower Y bound
     * @param minZ        obstacle lower Z bound
     * @param maxX        obstacle upper X bound
     * @param maxY        obstacle upper Y bound
     * @param maxZ        obstacle upper Z bound
     * @param radius      sphere radius; negative values are treated as zero
     * @return centre location at the first contact, or {@code null}
     */
    public static Vector firstContactWithExpandedAabb(
        Vector start,
        Vector displacement,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        double radius
    ) {
        if (!isFinite(start) || !isFinite(displacement)
            || !Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
            || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
            || !Double.isFinite(radius)) {
            return null;
        }

        double safeRadius = Math.max(0.0, radius);
        double boxMinX = Math.min(minX, maxX) - safeRadius;
        double boxMinY = Math.min(minY, maxY) - safeRadius;
        double boxMinZ = Math.min(minZ, maxZ) - safeRadius;
        double boxMaxX = Math.max(minX, maxX) + safeRadius;
        double boxMaxY = Math.max(minY, maxY) + safeRadius;
        double boxMaxZ = Math.max(minZ, maxZ) + safeRadius;

        double tEnter = 0.0;
        double tExit = 1.0;

        double dx = displacement.getX();
        if (Math.abs(dx) < PARALLEL_EPSILON) {
            if (start.getX() < boxMinX || start.getX() > boxMaxX) {
                return null;
            }
        } else {
            double t1 = (boxMinX - start.getX()) / dx;
            double t2 = (boxMaxX - start.getX()) / dx;
            tEnter = Math.max(tEnter, Math.min(t1, t2));
            tExit = Math.min(tExit, Math.max(t1, t2));
            if (tEnter > tExit) {
                return null;
            }
        }

        double dy = displacement.getY();
        if (Math.abs(dy) < PARALLEL_EPSILON) {
            if (start.getY() < boxMinY || start.getY() > boxMaxY) {
                return null;
            }
        } else {
            double t1 = (boxMinY - start.getY()) / dy;
            double t2 = (boxMaxY - start.getY()) / dy;
            tEnter = Math.max(tEnter, Math.min(t1, t2));
            tExit = Math.min(tExit, Math.max(t1, t2));
            if (tEnter > tExit) {
                return null;
            }
        }

        double dz = displacement.getZ();
        if (Math.abs(dz) < PARALLEL_EPSILON) {
            if (start.getZ() < boxMinZ || start.getZ() > boxMaxZ) {
                return null;
            }
        } else {
            double t1 = (boxMinZ - start.getZ()) / dz;
            double t2 = (boxMaxZ - start.getZ()) / dz;
            tEnter = Math.max(tEnter, Math.min(t1, t2));
            tExit = Math.min(tExit, Math.max(t1, t2));
            if (tEnter > tExit) {
                return null;
            }
        }

        if (tEnter < 0.0 || tEnter > 1.0) {
            return null;
        }
        return start.clone().add(displacement.clone().multiply(tEnter));
    }

    private static boolean isFinite(Vector vector) {
        return vector != null
            && Double.isFinite(vector.getX())
            && Double.isFinite(vector.getY())
            && Double.isFinite(vector.getZ());
    }
}
