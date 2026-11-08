package fr.bosketball;

import fr.bosketball.arena.Arena;
import fr.bosketball.arena.CourtGeometry;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The floor is the court, and the court is the floor.
 *
 * <p>A custom map made of sand used to show white tape lying on sand: there was no surface, only
 * lines, and any map whose sand did not happen to match the geometry read as "the lines are in the
 * wrong place". The arena now lays one flat display over the whole in-bounds rectangle, and the
 * perimeter is drawn on that rectangle instead of half a block inside it, so what the player sees
 * is exactly what {@code isInBounds} and the out-of-bounds rule use.
 */
public class CourtSurfaceTest {

    private static final Path ARENA = Path.of("src/main/java/fr/bosketball/arena/Arena.java");

    @Test
    public void thePlayableRectangleIsTheOneThatIsDrawn() {
        CourtGeometry c = new CourtGeometry(0.0, 64.0, 0.0);
        assertEquals("the floor must end 0.5 blocks past the half-width",
            c.halfWidth() + 0.5, c.maxX() - c.centerX(), 1.0e-9);
        assertEquals("the floor must end 0.5 blocks past the half-length",
            c.halfLength() + 0.5, c.maxZ() - c.centerZ(), 1.0e-9);
        assertTrue("the left edge matches the right", c.isInBounds(c.minX() + 0.01, c.centerZ()));
        assertTrue("and one hair past it is out", !c.isInBounds(c.maxX() + 0.01, c.centerZ()));
    }

    @Test
    public void theFloorIsPaintedOnTheBoundsNotOnAHalfWidthGuess() throws Exception {
        String arena = Files.readString(ARENA);
        int floor = arena.indexOf("private void spawnCourtFloor(");
        assertTrue("the court floor must exist", floor > 0);
        String body = arena.substring(floor, Math.min(arena.length(), floor + 1800));
        assertTrue("the floor must span the real bounds", body.contains("geometry.minX()")
            && body.contains("geometry.maxX()") && body.contains("geometry.minZ()")
            && body.contains("geometry.maxZ()"));
        assertTrue("it must be one display, not a block per square", body.contains("BlockDisplay"));
    }

    @Test
    public void thePerimeterIsDrawnOnThePlayableEdge() throws Exception {
        String arena = Files.readString(ARENA);
        assertTrue("the outer line must sit on the bounds, not inside them",
            arena.contains("double edgeU = halfU + 0.5;") && arena.contains("double edgeV = halfV + 0.5;"));
        assertTrue("the centre line reaches the perimeter",
            arena.contains("-edgeU, 0.0, +edgeU, 0.0"));
    }

    @Test
    public void theCourtIsBlueByDefault() throws Exception {
        String arena = Files.readString(ARENA);
        assertTrue("a court ships blue, not transparent",
            arena.contains("private Material courtFloorMaterial = Material.BLUE_CONCRETE;"));
        assertTrue("the floor is auto until an operator decides",
            arena.contains("private Boolean courtFloorOverride = null;"));
        assertTrue("auto means: paint a custom map that brings no court of its own",
            arena.contains("return customMap && !geometry.hasPaintedCourt();"));
    }

    @Test
    public void theFloorIsAutoAndFollowsTheMap() {
        CourtGeometry geo = new CourtGeometry(0.0, 64.0, 0.0);
        Arena map = new Arena("t", "Test", "world", geo);
        map.setCustomMap(true);
        assertTrue("on auto, a sand court with no paint of its own gets the blue floor",
            map.isCourtFloorAuto() && map.isCourtFloorEnabled());
        geo.setPaintedCourt(true);
        assertFalse("a map that paints its own court keeps it", map.isCourtFloorEnabled());
        map.setCourtFloorEnabled(true);
        assertFalse("an explicit on beats the map", map.isCourtFloorAuto());
        assertTrue("and it repaints the court", map.isCourtFloorEnabled());
        map.setCourtFloorAuto();
        assertTrue("auto comes back", map.isCourtFloorAuto());
        assertFalse("and the painted court wins again", map.isCourtFloorEnabled());
    }

    @Test
    public void ourOwnArenaKeepsItsDeck() {
        Arena built = new Arena("t", "Test", "world", new CourtGeometry(0.0, 64.0, 0.0));
        assertFalse("the arena we generate has a floor of its own", built.isCourtFloorEnabled());
    }

    @Test
    public void aMapWithItsOwnCourtKeepsIt() {
        CourtGeometry geo = new CourtGeometry(0.0, 64.0, 0.0);
        assertFalse("a sand court with no paint of its own gets the blue floor from us",
            geo.hasPaintedCourt());
        geo.setPaintedCourt(true);
        assertTrue("a painted court belongs to the map", geo.hasPaintedCourt());
    }
}
