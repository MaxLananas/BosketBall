package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import fr.bosketball.arena.Arena;
import fr.bosketball.arena.CourtGeometry;
import org.bukkit.util.Vector;
import org.junit.Test;

/**
 * One bad number must cost one feature, never the court.
 *
 * <p>Everything here comes from the same class of failure: a configuration line, a schematic or a
 * caller hands the geometry a value it cannot use, the value is stored anyway, and every derived
 * number - the floor, the paint, the out-of-bounds rule, the ball's own position - turns into NaN
 * or an inverted rectangle. On a live server that reads as an empty court and a lost ball.
 */
public class GeometryRobustnessTest {

    private static CourtGeometry defaultCourt() {
        return new CourtGeometry(0.0, 64.0, 0.0);
    }

    @Test
    public void aCourtWithABadNumberKeepsTheBoundsItHad() {
        assertBoundsKept(g -> g.setCourtBounds(Double.NaN, 0, 10, 10, 64));
        assertBoundsKept(g -> g.setCourtBounds(0, Double.POSITIVE_INFINITY, 10, 10, 64));
        assertBoundsKept(g -> g.setCourtBounds(0, 0, Double.NEGATIVE_INFINITY, 10, 64));
        assertBoundsKept(g -> g.setCourtBounds(0, 0, 10, Double.NaN, 64));
    }

    private static void assertBoundsKept(java.util.function.Consumer<CourtGeometry> badCall) {
        CourtGeometry geo = defaultCourt();
        double minX = geo.minX();
        double maxX = geo.maxX();
        double minZ = geo.minZ();
        double maxZ = geo.maxZ();
        badCall.accept(geo);
        assertEquals("a non-finite corner must not move the west edge", minX, geo.minX(), 1e-9);
        assertEquals("nor the east edge", maxX, geo.maxX(), 1e-9);
        assertEquals(minZ, geo.minZ(), 1e-9);
        assertEquals(maxZ, geo.maxZ(), 1e-9);
        assertTrue("the court is still playable", geo.isInBounds(geo.centerX(), geo.centerZ()));
    }

    @Test
    public void aCourtTooSmallToBeOneIsIgnored() {
        CourtGeometry geo = defaultCourt();
        double maxX = geo.maxX();
        geo.setCourtBounds(5.0, 5.0, 5.5, 5.5, 64.0);
        assertEquals("half a block is not a court", maxX, geo.maxX(), 1e-9);
        assertFalse("and the paint must not try to draw on it",
            geo.maxX() - geo.minX() < CourtGeometry.MIN_COURT_EDGE);
    }

    @Test
    public void swappedCornersAreNormalised() {
        CourtGeometry geo = defaultCourt();
        geo.setCourtBounds(20.0, 30.0, -4.0, 6.0, 70.0);
        assertTrue(geo.minX() < geo.maxX());
        assertTrue(geo.minZ() < geo.maxZ());
        assertEquals(-4.0, geo.minX(), 1e-9);
        assertEquals(20.0, geo.maxX(), 1e-9);
        assertEquals(6.0, geo.minZ(), 1e-9);
        assertEquals(30.0, geo.maxZ(), 1e-9);
        assertEquals(70.0, geo.floorY(), 1e-9);
    }

    @Test
    public void aNaNCageNeverDisablesTheBallClamp() {
        CourtGeometry geo = defaultCourt();
        double minX = geo.barrierMinX();
        double maxX = geo.barrierMaxX();
        double ceiling = geo.ceilingY();

        geo.setBarrierBounds(Double.NaN, 0, 10, 10, 80);
        geo.setBarrierBounds(0, 0, 10, Double.NaN, 80);

        assertEquals(minX, geo.barrierMinX(), 1e-9);
        assertEquals(maxX, geo.barrierMaxX(), 1e-9);

        CourtGeometry roofed = defaultCourt();
        double roof = roofed.ceilingY();
        roofed.setBarrierBounds(0, 0, 10, 10, Double.NaN);
        assertEquals("a NaN ceiling must not remove the roof", roof, roofed.ceilingY(), 1e-9);

        // The clamp is the thing that keeps a shot inside the arena, so prove it still bites.
        org.bukkit.util.Vector inside = new org.bukkit.util.Vector(geo.centerX(), geo.floorY() + 2, geo.centerZ());
        Arena arena = new Arena("robust", "Robust", "world", geo);
        assertFalse("a ball in the middle is inside the cage",
            arena.clampBallInsideBarrier(inside, new Vector(0, 0, 0), 0.72));
    }

    @Test
    public void aBadRimIsIgnoredAndADirectionIsAlwaysAvailable() {
        CourtGeometry geo = defaultCourt();
        Vector north = geo.northHoopRim();
        Vector south = geo.southHoopRim();

        geo.setNorthHoop(new Vector(Double.NaN, 66, 0), new Vector(0, 0, 1), true);
        geo.setSouthHoop(null, new Vector(0, 0, -1), true);

        assertEquals("a NaN rim must not move the north hoop", north.getZ(), geo.northHoopRim().getZ(), 1e-9);
        assertEquals(south.getX(), geo.southHoopRim().getX(), 1e-9);
        assertNotNull("a shot with no target still gets a direction",
            geo.hoopForwardForRim(null));
        assertEquals("and that direction is a real unit vector", 1.0,
            geo.hoopForwardForRim(null).length(), 1e-6);
    }

    @Test
    public void anUnreadableFloorSettingFallsBackToAuto() {
        assertNull("auto is the default", Arena.parseCourtFloorMode("auto"));
        assertNull("and so is a value nobody spelled right", Arena.parseCourtFloorMode("ture"));
        assertNull(Arena.parseCourtFloorMode(""));
        assertNull(Arena.parseCourtFloorMode(null));
        assertEquals(Boolean.TRUE, Arena.parseCourtFloorMode("on"));
        assertEquals(Boolean.TRUE, Arena.parseCourtFloorMode("TRUE"));
        assertEquals(Boolean.TRUE, Arena.parseCourtFloorMode(" enabled "));
        assertEquals(Boolean.FALSE, Arena.parseCourtFloorMode("off"));
        assertEquals(Boolean.FALSE, Arena.parseCourtFloorMode("false"));
        assertEquals(Boolean.FALSE, Arena.parseCourtFloorMode("Disabled"));
    }

    @Test
    public void theFloorModeSurvivesAReloadAsWritten() {
        Arena arena = new Arena("floor", "Floor", "world", defaultCourt());
        arena.setCustomMap(true);
        assertTrue("with no setting the floor follows the map", arena.isCourtFloorAuto());

        Boolean parsed = Arena.parseCourtFloorMode("off");
        assertNotNull(parsed);
        arena.setCourtFloorEnabled(parsed);
        assertFalse("an explicit off is honoured", arena.isCourtFloorEnabled());
        assertFalse(arena.isCourtFloorAuto());

        arena.setCourtFloorEnabled(Arena.parseCourtFloorMode("on"));
        assertTrue(arena.isCourtFloorEnabled());

        arena.setCourtFloorAuto();
        assertTrue("and auto comes back", arena.isCourtFloorAuto());
    }
}
