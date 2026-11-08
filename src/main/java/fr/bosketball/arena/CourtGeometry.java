package fr.bosketball.arena;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.util.Vector;

public final class CourtGeometry {

    public static final double DEFAULT_HALF_LENGTH = 15.0;
    public static final double DEFAULT_HALF_WIDTH  = 9.0;
    public static final double RIM_HEIGHT_ABOVE_FLOOR = 3.05;
    public static final double THREE_POINT_RADIUS = 7.25;
    public static final double THREE_POINT_ARC_RADIUS = THREE_POINT_RADIUS;
    public static final double THREE_POINT_CORNER_X = 6.60;
    public static final double DUNK_PAINT_RADIUS = 2.85;

    private double minX;
    private double maxX;
    private double minZ;
    private double maxZ;
    private double floorY;

    private double barrierMinX;
    private double barrierMaxX;
    private double barrierMinZ;
    private double barrierMaxZ;
    private double ceilingY;

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

    public double floorY() {
        return floorY;
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

    public Vector northHoopRim() {
        return northRim.clone();
    }

    public Vector northHoopForward() {
        return northHoopForward.clone();
    }

    /**
     * Distance between the two rims. A real court measures roughly 26 to 28 blocks; anything
     * below {@link #MINIMUM_BASKET_TO_BASKET} means the calibration put both hoops on top of each
     * other, and every shot then flies to the same basket no matter where the player aims.
     */
    public static final double MINIMUM_BASKET_TO_BASKET = 8.0;

    public double basketToBasketDistance() {
        return northRim.distance(southRim);
    }

    /**
     * True when the two logical rims are so close that the court cannot be played: both baskets
     * score at the same spot and every shot looks like it is aimed at the wrong end.
     */
    public boolean rimsCoincide() {
        return basketToBasketDistance() < MINIMUM_BASKET_TO_BASKET;
    }

    public boolean northSpawn3dHoop() {
        return northSpawn3dHoop;
    }

    public void setNorthHoop(Vector rimCenter, Vector forwardDir, boolean spawn3dHoop) {
        this.northRim = rimCenter.clone();
        this.northHoopForward = normalizeHorizontal(forwardDir, new Vector(0, 0, 1));
        this.northSpawn3dHoop = spawn3dHoop;
    }

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

    public Vector rimForTeam(fr.bosketball.model.TeamSide attackingSide) {
        return attackingSide == fr.bosketball.model.TeamSide.FROST ? northRim : southRim;
    }

    public Vector hoopForwardForRim(Vector targetRim) {
        if (targetRim.distanceSquared(northRim) <= targetRim.distanceSquared(southRim)) {
            return northHoopForward.clone();
        }
        return southHoopForward.clone();
    }

    private boolean bakedCourtLines = false;

    public boolean bakedCourtLines() {
        return bakedCourtLines;
    }

    public void setBakedCourtLines(boolean bakedCourtLines) {
        this.bakedCourtLines = bakedCourtLines;
    }

    public boolean isInBounds(double x, double z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /**
     * Lateral offset of the straight corner segment of the three-point line.
     *
     * <p>The paint and the scoring rule both read this number, so they can no longer disagree.
     * They used to: the arc was drawn with a corner at 7.00 blocks while the rule flipped at
     * 6.60, and a corner shooter standing a hand inside the painted line was paid the value of
     * the other side of it. A line that lies is worse than no line at all.
     */
    public double threePointCornerLateral() {
        double room = halfWidth() - 0.30;
        return Math.max(2.0, Math.min(Math.min(THREE_POINT_CORNER_X, room), threePointArcRadius() * 0.95));
    }

    /** Radius of the arc, clamped so the paint never runs off the hardwood on a small court. */
    public double threePointArcRadius() {
        // The arc may not reach the half-court line, and it may not reach the sideline either:
        // the tighter of the two wins, so the line always closes inside the floor.
        double lengthRoom = halfLength() * 0.72;
        double widthRoom = halfWidth() - 0.30;
        return Math.max(3.2, Math.min(THREE_POINT_ARC_RADIUS, Math.min(lengthRoom, widthRoom)));
    }

    /**
     * Distance from the rim, toward centre court, at which the corner line meets the arc.
     *
     * <p>The corner strip ends exactly where the painted corner ends, so the two halves of the
     * line join on the point that is both {@code cornerLateral} away sideways and a full arc
     * radius away from the rim.
     */
    public double threePointCornerDepth() {
        double c = threePointCornerLateral();
        double r = threePointArcRadius();
        return Math.sqrt(Math.max(0.0, r * r - c * c));
    }

    public boolean isThreePointShot(double x, double z, Vector targetRim) {
        Vector fwd = hoopForwardForRim(targetRim);
        Vector right = new Vector(-fwd.getZ(), 0, fwd.getX());
        double dx = x - targetRim.getX();
        double dz = z - targetRim.getZ();
        double forwardDist = dx * fwd.getX() + dz * fwd.getZ();
        double lateralDist = Math.abs(dx * right.getX() + dz * right.getZ());

        // The corner strip runs from behind the baseline out to the join point, and only as far
        // sideways as the paint actually sits. The old lower bound started half a block in front
        // of the rim, so the most common corner three of all — feet on the line, deep in the
        // corner — scored two.
        if (forwardDist >= -1.75 && forwardDist <= threePointCornerDepth()
            && lateralDist >= threePointCornerLateral()) {
            return true;
        }
        return Math.hypot(dx, dz) >= threePointArcRadius();
    }

    /**
     * Points a basket is worth for a shot released from {@code (x, z)} into the rim that credits
     * {@code scoringSide}.
     *
     * <p>The rim that credits a side is the rim returned by {@link #rimForTeam(TeamSide)} for that
     * very side: the north rim credits FROST, the south rim credits IGNIS. Measuring the shot
     * against the other team's rim turned every layup into a three, because the far hoop is
     * always more than seven blocks away.
     */
    public int shotValueAt(double x, double z, fr.bosketball.model.TeamSide scoringSide) {
        Vector rim = rimForTeam(scoringSide);
        if (rim == null) {
            return 2;
        }
        return isThreePointShot(x, z, rim) ? 3 : 2;
    }

    public boolean isInDunkZone(double x, double z, Vector targetRim) {
        return isInDunkZone(x, z, targetRim, 0.0);
    }

    /**
     * Same test with a forgiveness margin on the paint radius, so a player standing right on
     * the line (or a hair behind it) still gets the dunk.
     */
    /**
     * Half-width of the finishing zone, in blocks either side of the rim's axis.
     *
     * <p>This is the shoulder the paint draws. The two numbers here and
     * {@link #dunkZoneShoulderDepth()} are what the restricted-area line is made of, so the paint
     * says exactly where a dunk is allowed instead of drawing a decorative circle at 1.22 blocks
     * while the game allowed a finish from 2.85.
     */
    public double dunkZoneHalfWidth() {
        return 2.15;
    }

    /** Distance from the rim, toward centre court, where the shoulder meets the arc. */
    public double dunkZoneShoulderDepth() {
        double w = dunkZoneHalfWidth();
        return Math.sqrt(Math.max(0.0, DUNK_PAINT_RADIUS * DUNK_PAINT_RADIUS - w * w));
    }

    public boolean isInDunkZone(double x, double z, Vector targetRim, double radiusBonus) {
        double dx = x - targetRim.getX();
        double dz = z - targetRim.getZ();
        double dist = Math.hypot(dx, dz);
        if (dist < 0.25 || dist > DUNK_PAINT_RADIUS + Math.max(0.0, radiusBonus)) {
            return false;
        }
        Vector fwd = hoopForwardForRim(targetRim);
        double forwardDist = dx * fwd.getX() + dz * fwd.getZ();
        double lateralDist = Math.abs(-dx * fwd.getZ() + dz * fwd.getX());
        double bonus = Math.max(0.0, radiusBonus);
        return forwardDist >= -0.20 - bonus * 0.35
            && lateralDist <= dunkZoneHalfWidth() + bonus * 0.75;
    }

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
