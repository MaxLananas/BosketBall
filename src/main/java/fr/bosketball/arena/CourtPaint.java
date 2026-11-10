package fr.bosketball.arena;

import java.util.ArrayList;
import java.util.List;

/**
 * The paint of a court, as pure geometry: a list of flat tape segments in world coordinates.
 *
 * <p>It used to live inside {@code Arena}, mixed with display spawning, so nothing could check it:
 * the only way to know whether a line was in the right place was to start a server and look. The
 * layout is now a value, computed from {@link CourtGeometry} and a preset, and {@code Arena} only
 * turns it into {@code BlockDisplay}s. That makes the beach court — a sand deck with our blue
 * floor and our own lines on it — verifiable without a client.
 *
 * <p>Every tape lies on the floor, never half of it: the boundary tape's <em>outer</em> edge is the
 * floor edge, so the white line is entirely painted on the blue slab instead of hanging over the
 * sand, and the visible rectangle edge is still exactly the number the out-of-bounds rule reads.
 */
public final class CourtPaint {

    /** Lift above the block surface. The floor slab sits at 0.0015, the paint above it. */
    public static final double PAINT_LIFT = 0.0045;

    /** One flat line segment, centred on the line {@code (x0,z0)}-{@code (x1,z1)}. */
    public record Tape(String role, double x0, double z0, double x1, double z1,
                       double y, double width, boolean accent) {

        public double length() {
            return Math.hypot(x1 - x0, z1 - z0);
        }
    }

    private CourtPaint() {
    }

    public static List<Tape> plan(CourtGeometry geometry, Arena.CourtLinesPreset preset, double lineWidth) {
        return plan(geometry, preset, lineWidth, 0);
    }

    /**
     * The whole painted surface of one court.
     *
     * @param steps multiplier for the number of segments per arc ({@code 1} is the shipped density);
     *              tests and previews use a higher value without touching the game.
     */
    public static List<Tape> plan(CourtGeometry geometry, Arena.CourtLinesPreset preset,
                                  double lineWidth, int arcDetail) {
        List<Tape> tapes = new ArrayList<>(160);
        if (geometry == null) {
            return tapes;
        }
        double lw = Double.isFinite(lineWidth) ? Math.clamp(lineWidth, 0.02, 0.30) : 0.095;
        double detail = arcDetail > 0 ? arcDetail : 1;

        double cx = geometry.centerX();
        double cz = geometry.centerZ();
        double fy = geometry.floorY() + PAINT_LIFT;

        org.bukkit.util.Vector nRim = geometry.northHoopRim();
        org.bukkit.util.Vector sRim = geometry.southHoopRim();
        boolean eastWest = Math.abs(sRim.getX() - nRim.getX()) > Math.abs(sRim.getZ() - nRim.getZ());
        double ux = eastWest ? 0 : 1;
        double uz = eastWest ? 1 : 0;
        double vx = eastWest ? 1 : 0;
        double vz = eastWest ? 0 : 1;

        double halfU = eastWest ? geometry.halfLength() : geometry.halfWidth();
        double halfV = eastWest ? geometry.halfWidth() : geometry.halfLength();

        // The floor ends here: minX..maxX by minZ..maxZ is halfWidth+0.5 / halfLength+0.5.
        double floorU = halfU + 0.5;
        double floorV = halfV + 0.5;
        // Boundary tape is inset by half its width so the line sits ON the slab, not over the sand.
        double edgeU = floorU - lw * 0.5;
        double edgeV = floorV - lw * 0.5;

        Line out = new Line(tapes, cx, cz, ux, uz, vx, vz, fy);

        out.tape("perimeter", -edgeU, -edgeV, +edgeU, -edgeV, lw, false);
        out.tape("perimeter", -edgeU, +edgeV, +edgeU, +edgeV, lw, false);
        out.tape("perimeter", -edgeU, -edgeV, -edgeU, +edgeV, lw, false);
        out.tape("perimeter", +edgeU, -edgeV, +edgeU, +edgeV, lw, false);
        out.tape("half-court", -edgeU, 0.0, +edgeU, 0.0, lw, false);

        if (preset != Arena.CourtLinesPreset.MINIMAL) {
            double cRad = Math.min(2.35, Math.min(halfU, halfV) * 0.28);
            circle(out, "centre-circle", 0.0, 0.0, cRad, Math.max(18, (int) Math.round(18 * detail)), lw, true);
        }

        double nV = (nRim.getX() - cx) * vx + (nRim.getZ() - cz) * vz;
        org.bukkit.util.Vector negRim = nV <= 0 ? nRim : sRim;
        org.bukkit.util.Vector posRim = nV <= 0 ? sRim : nRim;
        double negRimU = (negRim.getX() - cx) * ux + (negRim.getZ() - cz) * uz;
        double negRimV = (negRim.getX() - cx) * vx + (negRim.getZ() - cz) * vz;
        double posRimU = (posRim.getX() - cx) * ux + (posRim.getZ() - cz) * uz;
        double posRimV = (posRim.getX() - cx) * vx + (posRim.getZ() - cz) * vz;

        double keyHalfW = Math.min(2.5, halfU * 0.34);
        double keyDepth = Math.min(5.5, halfV * 0.42);

        if (preset != Arena.CourtLinesPreset.MINIMAL) {
            for (double sign : new double[]{-1, +1}) {
                double rimU = sign < 0 ? negRimU : posRimU;
                double baseV = sign * edgeV;
                double ftV = baseV - sign * keyDepth;
                out.tape("key", rimU - keyHalfW, baseV, rimU - keyHalfW, ftV, lw, false);
                out.tape("key", rimU + keyHalfW, baseV, rimU + keyHalfW, ftV, lw, false);
                out.tape("key", rimU - keyHalfW, ftV, rimU + keyHalfW, ftV, lw, false);
            }
        }

        threePoint(out, geometry, negRimU, negRimV, +1.0, -edgeV, lw);
        threePoint(out, geometry, posRimU, posRimV, -1.0, +edgeV, lw);

        if (preset == Arena.CourtLinesPreset.FULL) {
            paintDetails(out, geometry, negRimU, negRimV, +1.0, -edgeV + keyDepth, keyHalfW,
                floorV, lw, arcDetail);
            paintDetails(out, geometry, posRimU, posRimV, -1.0, +edgeV - keyDepth, keyHalfW,
                floorV, lw, arcDetail);
        }
        return tapes;
    }

    /** Straight corner strips plus the arc that closes them, both around the same rim. */
    private static void threePoint(Line out, CourtGeometry geometry, double rimU, double rimV,
                                   double intoCourtSignV, double baselineV, double lw) {
        double radius = geometry.threePointArcRadius();
        double cornerHalfU = geometry.threePointCornerLateral();
        double maxAngle = Math.asin(Math.min(0.98, cornerHalfU / radius));
        double joinV = rimV + Math.cos(maxAngle) * radius * intoCourtSignV;

        out.tape("three-point corner", rimU - cornerHalfU, baselineV, rimU - cornerHalfU, joinV, lw, false);
        out.tape("three-point corner", rimU + cornerHalfU, baselineV, rimU + cornerHalfU, joinV, lw, false);
        arc(out, "three-point arc", rimU, rimV, radius, -maxAngle, maxAngle,
            intoCourtSignV, 20, lw, false);
    }

    private static void paintDetails(Line out, CourtGeometry geometry, double rimU, double rimV,
                                     double intoCourtSignV, double ftLineV, double keyHalfW,
                                     double floorV, double lw, int arcDetail) {
        // The restricted area, drawn from the numbers the dunk test uses.
        double finishRadius = CourtGeometry.DUNK_PAINT_RADIUS;
        double shoulder = geometry.dunkZoneHalfWidth();
        double shoulderDepth = geometry.dunkZoneShoulderDepth();
        out.tape("restricted shoulder", rimU - shoulder, rimV,
            rimU - shoulder, rimV + shoulderDepth * intoCourtSignV, lw * 0.85, true);
        out.tape("restricted shoulder", rimU + shoulder, rimV,
            rimU + shoulder, rimV + shoulderDepth * intoCourtSignV, lw * 0.85, true);

        double finishAngle = Math.asin(Math.min(0.99, shoulder / finishRadius));
        arc(out, "restricted arc", rimU, rimV, finishRadius, -finishAngle, finishAngle,
            intoCourtSignV, Math.max(14, (int) Math.round(14 * Math.max(1, arcDetail))), lw * 0.85, true);

        double ftr = Math.min(1.80, keyHalfW * 0.72);
        // Free-throw semicircle: the half on the centre-court side of the line.
        int ftSteps = Math.max(12, (int) Math.round(12 * Math.max(1, arcDetail)));
        for (int i = 0; i < ftSteps; i++) {
            double t0 = -Math.PI * 0.5 + (Math.PI * i) / ftSteps;
            double t1 = -Math.PI * 0.5 + (Math.PI * (i + 1)) / ftSteps;
            out.tape("free-throw circle",
                rimU + Math.sin(t0) * ftr, ftLineV + Math.cos(t0) * ftr * intoCourtSignV,
                rimU + Math.sin(t1) * ftr, ftLineV + Math.cos(t1) * ftr * intoCourtSignV,
                lw * 0.85, false);
        }

        // Lane marks sit between the free-throw line and the baseline. On a short key the third
        // mark would be drawn past the baseline, over the sand: it is skipped instead.
        double baselineLimit = floorV - lw * 1.5;
        for (double distFromFt : new double[]{1.2, 2.3, 3.4}) {
            double hv = ftLineV - distFromFt * intoCourtSignV;
            if (Math.abs(hv) >= Math.abs(ftLineV) + 0.2 && Math.abs(hv) <= baselineLimit) {
                out.tape("lane mark", rimU - keyHalfW - 0.32, hv, rimU - keyHalfW, hv, lw * 0.85, false);
                out.tape("lane mark", rimU + keyHalfW, hv, rimU + keyHalfW + 0.32, hv, lw * 0.85, false);
            }
        }
    }

    private static void circle(Line out, String role, double centreU, double centreV, double radius,
                               int steps, double lw, boolean accent) {
        for (int i = 0; i < steps; i++) {
            double a0 = (Math.PI * 2.0 * i) / steps;
            double a1 = (Math.PI * 2.0 * (i + 1)) / steps;
            out.tape(role,
                centreU + Math.cos(a0) * radius, centreV + Math.sin(a0) * radius,
                centreU + Math.cos(a1) * radius, centreV + Math.sin(a1) * radius,
                lw, accent);
        }
    }

    /**
     * A cone of a circle around a rim: {@code ±maxAngle} is measured from the direction pointing
     * into the court, which is what makes the corner strip and the arc meet on one point.
     */
    private static void arc(Line out, String role, double rimU, double rimV, double radius,
                            double fromAngle, double toAngle, double intoCourtSignV,
                            int steps, double lw, boolean accent) {
        for (int i = 0; i < steps; i++) {
            double t0 = fromAngle + ((toAngle - fromAngle) * i) / steps;
            double t1 = fromAngle + ((toAngle - fromAngle) * (i + 1)) / steps;
            out.tape(role,
                rimU + Math.sin(t0) * radius, rimV + Math.cos(t0) * radius * intoCourtSignV,
                rimU + Math.sin(t1) * radius, rimV + Math.cos(t1) * radius * intoCourtSignV,
                lw, accent);
        }
    }

    /** Court-basis (u, v) to world (x, z), so every helper above stays readable. */
    private static final class Line {
        private final List<Tape> tapes;
        private final double cx;
        private final double cz;
        private final double ux;
        private final double uz;
        private final double vx;
        private final double vz;
        private final double y;

        Line(List<Tape> tapes, double cx, double cz, double ux, double uz, double vx, double vz, double y) {
            this.tapes = tapes;
            this.cx = cx;
            this.cz = cz;
            this.ux = ux;
            this.uz = uz;
            this.vx = vx;
            this.vz = vz;
            this.y = y;
        }

        void tape(String role, double u0, double v0, double u1, double v1, double width, boolean accent) {
            tapes.add(new Tape(role,
                cx + ux * u0 + vx * v0, cz + uz * u0 + vz * v0,
                cx + ux * u1 + vx * v1, cz + uz * u1 + vz * v1,
                y, width, accent));
        }
    }
}
