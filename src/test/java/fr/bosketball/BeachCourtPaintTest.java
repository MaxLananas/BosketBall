package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import fr.bosketball.arena.Arena;
import fr.bosketball.arena.CourtGeometry;
import fr.bosketball.arena.CourtPaint;
import java.util.List;
import org.bukkit.util.Vector;
import org.junit.Test;

/**
 * A beach court is a sand deck the plugin dresses itself: our blue floor covers the playable
 * rectangle and our own lines are painted on it. Everything visible there is ours, so everything
 * visible there can be checked, block by block, without a client.
 *
 * <p>The invariants: no tape of any preset ever leaves the blue slab; the boundary tape's outer
 * edge is the slab edge; the three-point corner strip and its arc meet on one point of the arc;
 * the paint is flat and uniform; and a deck saved sideways gets the same real three-point line as
 * one saved the usual way.
 */
public class BeachCourtPaintTest {

    private static final double LW = 0.095;

    /** The rectangle the plugin would fit to a sand deck of {@code spanX} by {@code spanZ}. */
    private static CourtGeometry sandDeck(double spanX, double spanZ, boolean hoopsAlongX) {
        double hx = spanX / 2.0;
        double hz = spanZ / 2.0;
        CourtGeometry g = new CourtGeometry(0, 64, 0, hz, hx);
        g.setCourtBounds(-hx, -hz, hx, hz, 64);
        if (hoopsAlongX) {
            g.setNorthHoop(new Vector(-hx + 1.25, 67, 0), new Vector(1, 0, 0), true);
            g.setSouthHoop(new Vector(hx - 1.25, 67, 0), new Vector(-1, 0, 0), true);
        } else {
            g.setNorthHoop(new Vector(0, 67, -hz + 1.25), new Vector(0, 0, 1), true);
            g.setSouthHoop(new Vector(0, 67, hz - 1.25), new Vector(0, 0, -1), true);
        }
        return g;
    }

    private static double nearestRim(CourtGeometry g, double x, double z) {
        Vector n = g.northHoopRim();
        Vector s = g.southHoopRim();
        return Math.min(Math.hypot(x - n.getX(), z - n.getZ()), Math.hypot(x - s.getX(), z - s.getZ()));
    }

    private static double overhang(CourtGeometry g, CourtPaint.Tape t) {
        double minX = Math.min(t.x0(), t.x1()) - t.width() * 0.5;
        double maxX = Math.max(t.x0(), t.x1()) + t.width() * 0.5;
        double minZ = Math.min(t.z0(), t.z1()) - t.width() * 0.5;
        double maxZ = Math.max(t.z0(), t.z1()) + t.width() * 0.5;
        double ox = Math.max(0, Math.max(g.minX() - minX, maxX - g.maxX()));
        double oz = Math.max(0, Math.max(g.minZ() - minZ, maxZ - g.maxZ()));
        return Math.max(ox, oz);
    }

    @Test
    public void noTapeEverLeavesTheBlueSlab() {
        double[][] decks = {{18, 30}, {30, 18}, {16, 34}, {20, 20}, {40, 60}, {8, 10}, {8, 8}};
        for (double[] deck : decks) {
            for (boolean alongX : new boolean[]{false, true}) {
                CourtGeometry g = sandDeck(deck[0], deck[1], alongX);
                for (Arena.CourtLinesPreset preset : Arena.CourtLinesPreset.values()) {
                    for (CourtPaint.Tape t : CourtPaint.plan(g, preset, LW)) {
                        double over = overhang(g, t);
                        assertTrue("a " + deck[0] + "x" + deck[1] + (alongX ? " (hoops on X)" : "")
                                + " deck, " + preset + " preset: the " + t.role()
                                + " hangs " + String.format("%.3f", over) + " block(s) over the sand",
                            over <= 1e-9);
                    }
                }
            }
        }
    }

    @Test
    public void thePaintIsFlatOnTheFloorAndUniform() {
        CourtGeometry g = sandDeck(18, 30, false);
        List<CourtPaint.Tape> plan = CourtPaint.plan(g, Arena.CourtLinesPreset.FULL, LW);
        assertTrue("a full court has paint", plan.size() > 100);
        for (CourtPaint.Tape t : plan) {
            assertEquals("every tape is 4.5 mm above the deck, on the same plane",
                g.floorY() + CourtPaint.PAINT_LIFT, t.y(), 1e-9);
            assertTrue("a tape has a width the client can draw", t.width() >= 0.05 && t.width() <= 0.30);
            assertTrue("a tape has a length", t.length() > 1e-3);
        }
    }

    @Test
    public void theThreePointLineClosesOnItsOwnRadius() {
        for (double[] deck : new double[][]{{18, 30}, {20, 24}, {16, 34}}) {
            for (boolean alongX : new boolean[]{false, true}) {
                CourtGeometry g = sandDeck(deck[0], deck[1], alongX);
                double radius = g.threePointArcRadius();
                double corner = g.threePointCornerLateral();
                assertTrue("the corner strip must stand inside the arc it joins (r=" + radius
                    + ", corner=" + corner + ")", corner < radius);

                List<CourtPaint.Tape> plan = CourtPaint.plan(g, Arena.CourtLinesPreset.FULL, LW);
                CourtPaint.Tape strip = null;
                CourtPaint.Tape firstArc = null;
                for (CourtPaint.Tape t : plan) {
                    if (t.role().equals("three-point corner") && strip == null) {
                        strip = t;
                    }
                    if (t.role().equals("three-point arc") && firstArc == null) {
                        firstArc = t;
                    }
                }
                assertTrue("the corner strip exists", strip != null);
                assertTrue("the arc exists", firstArc != null);

                // The strip ends where the arc starts: one of the two strip ends is an arc end.
                double join = Math.min(
                    Math.hypot(strip.x1() - firstArc.x0(), strip.z1() - firstArc.z0()),
                    Math.hypot(strip.x0() - firstArc.x0(), strip.z0() - firstArc.z0()));
                assertEquals("the corner strip and the arc meet on one point", 0.0, join, 1e-9);

                for (CourtPaint.Tape t : plan) {
                    if (!t.role().equals("three-point arc")) {
                        continue;
                    }
                    // Both ends of every arc piece sit exactly one radius from one of the two
                    // rims: the paint is a pair of circles, not a decoration.
                    assertEquals("every arc point is one radius from a rim", radius,
                        nearestRim(g, t.x0(), t.z0()), 1e-9);
                    assertEquals(radius, nearestRim(g, t.x1(), t.z1()), 1e-9);
                }
            }
        }
    }

    @Test
    public void aDeckSavedSidewaysGetsTheRealThreePointLine() {
        // Baselines east and west, rims 30 blocks apart: same court, same rules.
        CourtGeometry normal = sandDeck(18, 30, false);
        CourtGeometry sideways = sandDeck(30, 18, true);
        assertEquals("a sideways deck keeps the full arc",
            CourtGeometry.THREE_POINT_ARC_RADIUS, sideways.threePointArcRadius(), 1e-9);
        assertEquals(CourtGeometry.THREE_POINT_CORNER_X, sideways.threePointCornerLateral(), 1e-9);
        assertEquals(normal.threePointArcRadius(), sideways.threePointArcRadius(), 1e-9);
        assertEquals(normal.threePointCornerLateral(), sideways.threePointCornerLateral(), 1e-9);

        // A court whose rims are only 16 blocks apart cannot hold a 7.25 arc: it shrinks, but the
        // corner must shrink with it and stay inside.
        CourtGeometry tight = sandDeck(16, 30, true);
        assertTrue(tight.threePointArcRadius() < CourtGeometry.THREE_POINT_ARC_RADIUS);
        assertTrue(tight.threePointCornerLateral() < tight.threePointArcRadius());
        Vector rim = tight.southHoopRim();
        double r = tight.threePointArcRadius();
        assertTrue("a point on the arc toward centre court is a three",
            tight.isThreePointShot(rim.getX() - r, rim.getZ(), rim));
        assertFalse("one block inside it is not",
            tight.isThreePointShot(rim.getX() - r + 1.0, rim.getZ(), rim));
    }

    @Test
    public void theKeyAndTheRestrictedAreaStayInsideThePaint() {
        CourtGeometry g = sandDeck(18, 30, false);
        List<CourtPaint.Tape> plan = CourtPaint.plan(g, Arena.CourtLinesPreset.FULL, LW);
        double keyHalf = Math.min(2.5, g.halfWidth() * 0.34);
        int key = 0;
        for (CourtPaint.Tape t : plan) {
            if (t.role().equals("key")) {
                key++;
                assertTrue("the key is 5 blocks wide at most",
                    Math.abs(t.x0() - t.x1()) <= 2 * keyHalf + 1e-9);
            }
            if (t.role().equals("restricted arc") || t.role().equals("restricted shoulder")) {
                Vector rim = t.z0() < 0 || t.z1() < 0 ? g.northHoopRim() : g.southHoopRim();
                assertTrue("the restricted area belongs to the court",
                    Math.abs(t.x0() - rim.getX()) <= CourtGeometry.DUNK_PAINT_RADIUS + 1e-9
                        && Math.abs(t.z0() - rim.getZ()) <= CourtGeometry.DUNK_PAINT_RADIUS + 1e-9);
            }
        }
        assertEquals("two keys, three lines each", 6, key);

        // The paint draws the zone the dunk test honours, no decorative circle.
        CourtPaint.Tape shoulder = null;
        for (CourtPaint.Tape t : plan) {
            if (t.role().equals("restricted shoulder")) {
                shoulder = t;
                break;
            }
        }
        assertTrue(shoulder != null);
        assertEquals("the shoulder stands at the width the dunk test allows",
            g.dunkZoneHalfWidth(),
            Math.abs(Math.abs(shoulder.x0() - g.northHoopRim().getX())), 1e-9);
    }

    @Test
    public void thePresetsOnlyEverRemoveDetail() {
        CourtGeometry g = sandDeck(18, 30, false);
        int minimal = CourtPaint.plan(g, Arena.CourtLinesPreset.MINIMAL, LW).size();
        int street = CourtPaint.plan(g, Arena.CourtLinesPreset.STREET, LW).size();
        int full = CourtPaint.plan(g, Arena.CourtLinesPreset.FULL, LW).size();
        assertTrue("minimal is the sparsest", minimal < street);
        assertTrue("street is sparser than full", street < full);
        assertTrue("a minimal court still has its perimeter and its three-point line", minimal >= 14);
    }

    @Test
    public void aBrokenLineWidthIsReplacedNotPropagated() {
        CourtGeometry g = sandDeck(18, 30, false);
        for (double width : new double[]{Double.NaN, -3, 0, 99}) {
            for (CourtPaint.Tape t : CourtPaint.plan(g, Arena.CourtLinesPreset.FULL, width)) {
                assertTrue("a " + width + " line width must not reach the display",
                    Double.isFinite(t.width()) && t.width() >= 0.01 && t.width() <= 0.30);
                assertTrue("and the tape still sits on the slab", overhang(g, t) <= 1e-9);
            }
        }
    }
}
