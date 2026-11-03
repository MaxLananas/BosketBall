package fr.bosketball.arena;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.util.Vector;

/**
 * High-Precision 3D Basketball Court & Custom Map Geometry (Supports both Generated Stadiums & Custom User Maps in 360°!).
 * Default generated regulation court: 30x18 blocks (halfLength=15, halfWidth=9), rim height +3.05 blocks above floorY.
 */
public final class CourtGeometry {

    public static final double DEFAULT_HALF_LENGTH = 15.0;
    public static final double DEFAULT_HALF_WIDTH  = 9.0;
    public static final double RIM_HEIGHT_ABOVE_FLOOR = 3.05;
    public static final double THREE_POINT_RADIUS = 7.25;
    public static final double THREE_POINT_ARC_RADIUS = THREE_POINT_RADIUS;
    public static final double THREE_POINT_CORNER_X = 6.60;
    public static final double DUNK_PAINT_RADIUS = 2.85;

    // Playable out-of-bounds court limits
    private double minX;
    private double maxX;
    private double minZ;
    private double maxZ;
    private double floorY;

    // Invisible 3D containment barrier (keeps ball & players inside the map!)
    private double barrierMinX;
    private double barrierMaxX;
    private double barrierMinZ;
    private double barrierMaxZ;
    private double ceilingY;

    // Two hoops:
    //  - northRim = target hoop where FROST scores (defended by IGNIS)
    //  - southRim = target hoop where IGNIS scores (defended by FROST)
    private Vector northRim;
    private Vector northHoopForward;
    private boolean northSpawn3dHoop = true;

    private Vector southRim;
    private Vector southHoopForward;
    private boolean southSpawn3dHoop = true;

    public CourtGeometry(double centerX, double floorY, double centerZ) {
        this(centerX, floorY, centerZ, DEFAULT_HALF_LENGTH, DEFAULT_HALF_WIDTH);
    }

    public CourtGeometry(double centerX, double floorY, double centerZ, double halfLength, double halfWidth) {
        this.floorY = floorY;
        this.minX = centerX - halfWidth - 0.50;
        this.maxX = centerX + halfWidth + 0.50;
        this.minZ = centerZ - halfLength - 0.50;
        this.maxZ = centerZ + halfLength + 0.50;

        // Default stadium containment envelope (includes courtside apron, bleachers & overhead jumbotron clearance)
        this.barrierMinX = centerX - halfWidth - 8.5;
        this.barrierMaxX = centerX + halfWidth + 8.5;
        this.barrierMinZ = centerZ - halfLength - 8.0;
        this.barrierMaxZ = centerZ + halfLength + 8.0;
        this.ceilingY = floorY + 16.5;

        double rimY = floorY + RIM_HEIGHT_ABOVE_FLOOR;
        this.northRim = new Vector(centerX, rimY, centerZ - halfLength + 1.25);
        this.northHoopForward = new Vector(0, 0, 1);

        this.southRim = new Vector(centerX, rimY, centerZ + halfLength - 1.25);
        this.southHoopForward = new Vector(0, 0, -1);
    }

    public double centerX() {
        return (minX + maxX) * 0.5;
    }

    public double centerZ() {
        return (minZ + maxZ) * 0.5;
    }

    /** Exact Y coordinate of the top surface of the court floor where the ball bounces. */
    public double floorY() {
        return floorY;
    }

    public void setFloorY(double floorY) {
        this.floorY = floorY;
    }

    public double halfLength() {
        return Math.max(3.0, (maxZ - minZ - 1.0) * 0.5);
    }

    public double halfWidth() {
        return Math.max(3.0, (maxX - minX - 1.0) * 0.5);
    }

    public double minX() {
        return minX;
    }

    public double maxX() {
        return maxX;
    }

    public double minZ() {
        return minZ;
    }

    public double maxZ() {
        return maxZ;
    }

    public void setCourtBounds(double x1, double z1, double x2, double z2, double topFloorY) {
        this.minX = Math.min(x1, x2);
        this.maxX = Math.max(x1, x2);
        this.minZ = Math.min(z1, z2);
        this.maxZ = Math.max(z1, z2);
        this.floorY = topFloorY;
    }

    public double barrierMinX() {
        return barrierMinX;
    }

    public double barrierMaxX() {
        return barrierMaxX;
    }

    public double barrierMinZ() {
        return barrierMinZ;
    }

    public double barrierMaxZ() {
        return barrierMaxZ;
    }

    public double ceilingY() {
        return ceilingY;
    }

    public void setBarrierBounds(double x1, double z1, double x2, double z2, double ceilingY) {
        this.barrierMinX = Math.min(x1, x2);
        this.barrierMaxX = Math.max(x1, x2);
        this.barrierMinZ = Math.min(z1, z2);
        this.barrierMaxZ = Math.max(z1, z2);
        this.ceilingY = Math.max(this.floorY + 5.0, ceilingY);
    }

    /** North/Frost-target Rim center (defended by IGNIS, scored on by FROST). */
    public Vector northHoopRim() {
        return northRim.clone();
    }

    public Vector northHoopForward() {
        return northHoopForward.clone();
    }

    public boolean northSpawn3dHoop() {
        return northSpawn3dHoop;
    }

    public void setNorthHoop(Vector rimCenter, Vector forwardDir, boolean spawn3dHoop) {
        this.northRim = rimCenter.clone();
        this.northHoopForward = normalizeHorizontal(forwardDir, new Vector(0, 0, 1));
        this.northSpawn3dHoop = spawn3dHoop;
    }

    /** South/Ignis-target Rim center (defended by FROST, scored on by IGNIS). */
    public Vector southHoopRim() {
        return southRim.clone();
    }

    public Vector southHoopForward() {
        return southHoopForward.clone();
    }

    public boolean southSpawn3dHoop() {
        return southSpawn3dHoop;
    }

    public void setSouthHoop(Vector rimCenter, Vector forwardDir, boolean spawn3dHoop) {
        this.southRim = rimCenter.clone();
        this.southHoopForward = normalizeHorizontal(forwardDir, new Vector(0, 0, -1));
        this.southSpawn3dHoop = spawn3dHoop;
    }

    /** The rim the given team attacks (IGNIS scores on the south hoop, FROST on the north one). */
    public Vector rimForTeam(fr.bosketball.model.TeamSide attackingSide) {
        return attackingSide == fr.bosketball.model.TeamSide.FROST ? northRim : southRim;
    }

    public Vector hoopForwardForRim(Vector targetRim) {
        if (targetRim.distanceSquared(northRim) <= targetRim.distanceSquared(southRim)) {
            return northHoopForward.clone();
        }
        return southHoopForward.clone();
    }

    public boolean isInBounds(double x, double z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean isInsideBarrier(double x, double y, double z) {
        return x >= barrierMinX && x <= barrierMaxX
            && z >= barrierMinZ && z <= barrierMaxZ
            && y >= floorY - 2.2 && y <= ceilingY;
    }

    /**
     * Evaluates whether a floor coordinate (x, z) is behind the 3-point arc for the given target rim (360° orientation-aware!).
     */
    public boolean isThreePointShot(double x, double z, Vector targetRim) {
        Vector fwd = hoopForwardForRim(targetRim);
        Vector right = new Vector(-fwd.getZ(), 0, fwd.getX());
        double dx = x - targetRim.getX();
        double dz = z - targetRim.getZ();
        double forwardDist = dx * fwd.getX() + dz * fwd.getZ();
        double lateralDist = Math.abs(dx * right.getX() + dz * right.getZ());
        // Corner 3-point lines near baseline
        if (forwardDist >= -0.5 && forwardDist <= 2.95 && lateralDist >= THREE_POINT_CORNER_X) {
            return true;
        }
        return Math.hypot(dx, dz) >= THREE_POINT_RADIUS;
    }

    public boolean isInDunkZone(double x, double z, Vector targetRim) {
        double dx = x - targetRim.getX();
        double dz = z - targetRim.getZ();
        double dist = Math.hypot(dx, dz);
        if (dist < 0.25 || dist > DUNK_PAINT_RADIUS) {
            return false;
        }
        Vector fwd = hoopForwardForRim(targetRim);
        double forwardDist = dx * fwd.getX() + dz * fwd.getZ();
        double lateralDist = Math.abs(-dx * fwd.getZ() + dz * fwd.getX());
        return forwardDist >= -0.20 && lateralDist <= 2.15;
    }

    /**
     * Returns the 5 official NBA All-Star 3-Point Contest rack positions around the Ignis-target hoop (works in any 360° orientation!).
     */
    public List<Vector> threePointContestRacks() {
        Vector rim = southHoopRim();
        Vector fwd = southHoopForward();
        Vector right = new Vector(-fwd.getZ(), 0, fwd.getX());
        double r = THREE_POINT_RADIUS + 0.55;
        double[] anglesDeg = {-82.0, -45.0, 0.0, 45.0, 82.0};
        List<Vector> spots = new ArrayList<>(5);
        for (double deg : anglesDeg) {
            double rad = Math.toRadians(deg);
            double alongFwd = Math.cos(rad) * r;
            double alongRight = Math.sin(rad) * r;
            double sx = rim.getX() + fwd.getX() * alongFwd + right.getX() * alongRight;
            double sz = rim.getZ() + fwd.getZ() * alongFwd + right.getZ() * alongRight;
            sx = Math.max(barrierMinX + 0.8, Math.min(barrierMaxX - 0.8, sx));
            sz = Math.max(barrierMinZ + 0.8, Math.min(barrierMaxZ - 0.8, sz));
            spots.add(new Vector(sx, floorY, sz));
        }
        return spots;
    }

    public static Vector snapCardinalHorizontal(Vector dir) {
        if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) {
            return new Vector(dir.getX() >= 0 ? 1.0 : -1.0, 0.0, 0.0);
        }
        return new Vector(0.0, 0.0, dir.getZ() >= 0 ? 1.0 : -1.0);
    }

    public static String cardinalLabel(Vector fwd) {
        if (Math.abs(fwd.getX()) > Math.abs(fwd.getZ())) {
            return fwd.getX() >= 0 ? "ᴇᴀꜱᴛ (+X)" : "ᴡᴇꜱᴛ (-X)";
        }
        return fwd.getZ() >= 0 ? "ꜱᴏᴜᴛʜ (+Z)" : "ɴᴏʀᴛʜ (-Z)";
    }

    private static Vector normalizeHorizontal(Vector v, Vector fallback) {
        if (v == null) {
            return fallback.clone();
        }
        Vector h = new Vector(v.getX(), 0.0, v.getZ());
        if (h.lengthSquared() < 1e-6) {
            return fallback.clone();
        }
        return h.normalize();
    }
}
