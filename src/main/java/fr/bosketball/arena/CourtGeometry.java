package fr.bosketball.arena;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.util.Vector;

/**
 * Pure NBA/FIBA-scaled Court Coordinate & Zone Evaluator.
 * Court canonical axes:
 *   Origin (centerJump): X = centerX, Y = floorY, Z = centerZ
 *   Length runs along Z: [-halfLength .. +halfLength] (default halfLength = 15.0 -> 30x17 block court)
 *   Width runs along X:  [-halfWidth  .. +halfWidth ] (default halfWidth  = 8.5)
 *   IGNIS defends North Hoop (-Z, facing +Z); attacks South Hoop (+Z).
 *   FROST defends South Hoop (+Z, facing -Z); attacks North Hoop (-Z).
 */
public final class CourtGeometry {

    public static final double DEFAULT_HALF_LENGTH = 15.0; // 30 blocks endline-to-endline
    public static final double DEFAULT_HALF_WIDTH = 8.5;   // 17 blocks sideline-to-sideline
    public static final double RIM_HEIGHT_ABOVE_FLOOR = 3.05; // Exact regulation 3.05m!
    public static final double HOOP_INSET_FROM_ENDLINE = 1.55;
    public static final double THREE_POINT_ARC_RADIUS = 7.25;
    public static final double CORNER_THREE_X_THRESHOLD = 6.60;
    public static final double DUNK_ZONE_RADIUS = 4.60;
    public static final double PAINT_HALF_WIDTH = 2.50;
    public static final double PAINT_LENGTH = 5.80;

    private final double centerX;
    private final double floorY;
    private final double centerZ;
    private final double halfLength;
    private final double halfWidth;

    public CourtGeometry(double centerX, double floorY, double centerZ, double halfLength, double halfWidth) {
        this.centerX = centerX;
        this.floorY = floorY;
        this.centerZ = centerZ;
        this.halfLength = halfLength;
        this.halfWidth = halfWidth;
    }

    public CourtGeometry(double centerX, double floorY, double centerZ) {
        this(centerX, floorY, centerZ, DEFAULT_HALF_LENGTH, DEFAULT_HALF_WIDTH);
    }

    public double centerX() {
        return centerX;
    }

    public double floorY() {
        return floorY;
    }

    public double centerZ() {
        return centerZ;
    }

    public double halfLength() {
        return halfLength;
    }

    public double halfWidth() {
        return halfWidth;
    }

    /** North Rim (-Z), defended by IGNIS, attacked by FROST. */
    public Vector northHoopRim() {
        return new Vector(centerX, floorY + RIM_HEIGHT_ABOVE_FLOOR, centerZ - halfLength + HOOP_INSET_FROM_ENDLINE);
    }

    /** South Rim (+Z), defended by FROST, attacked by IGNIS. */
    public Vector southHoopRim() {
        return new Vector(centerX, floorY + RIM_HEIGHT_ABOVE_FLOOR, centerZ + halfLength - HOOP_INSET_FROM_ENDLINE);
    }

    public boolean isInBounds(double x, double z) {
        return Math.abs(x - centerX) <= halfWidth + 0.30
            && Math.abs(z - centerZ) <= halfLength + 0.30;
    }

    /**
     * Evaluates whether a shot launched from (shooterX, shooterZ) at targetRim is a 3-Pointer!
     * Accounts for both the curved 7.25m 3PT arc AND the straight Corner-3 sidelines (|dx| >= 6.60)!
     */
    public boolean isThreePointShot(double shooterX, double shooterZ, Vector targetRim) {
        double dx = shooterX - targetRim.getX();
        double dz = shooterZ - targetRim.getZ();
        double horizDist = Math.hypot(dx, dz);

        // Corner 3 zone near baseline
        if (Math.abs(dx) >= CORNER_THREE_X_THRESHOLD && Math.abs(dz) <= 3.30) {
            return true;
        }
        return horizDist >= THREE_POINT_ARC_RADIUS;
    }

    public boolean isInDunkZone(double playerX, double playerZ, Vector targetRim) {
        double dist = Math.hypot(playerX - targetRim.getX(), playerZ - targetRim.getZ());
        return dist <= DUNK_ZONE_RADIUS && dist >= 0.45;
    }

    public boolean isInsidePaint(double x, double z, Vector targetRim) {
        double dx = Math.abs(x - targetRim.getX());
        double dz = Math.abs(z - targetRim.getZ());
        return dx <= PAINT_HALF_WIDTH && dz <= PAINT_LENGTH;
    }

    /**
     * Returns the 5 official NBA All-Star 3-Point Contest Rack positions around the South Hoop (+Z):
     * Left Corner, Left Wing (45°), Top of the Key (0°), Right Wing (45°), Right Corner.
     */
    public List<Vector> threePointContestRacks() {
        Vector hoop = southHoopRim();
        double r = THREE_POINT_ARC_RADIUS + 0.45;
        List<Vector> spots = new ArrayList<>(5);
        // 1. Left Corner
        spots.add(new Vector(hoop.getX() - (CORNER_THREE_X_THRESHOLD + 0.4), floorY, hoop.getZ() - 0.8));
        // 2. Left 45° Wing
        spots.add(new Vector(hoop.getX() - r * 0.7071, floorY, hoop.getZ() - r * 0.7071));
        // 3. Top of Key
        spots.add(new Vector(hoop.getX(), floorY, hoop.getZ() - r));
        // 4. Right 45° Wing
        spots.add(new Vector(hoop.getX() + r * 0.7071, floorY, hoop.getZ() - r * 0.7071));
        // 5. Right Corner
        spots.add(new Vector(hoop.getX() + (CORNER_THREE_X_THRESHOLD + 0.4), floorY, hoop.getZ() - 0.8));
        return spots;
    }
}
