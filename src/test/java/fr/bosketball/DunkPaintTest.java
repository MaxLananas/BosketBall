package fr.bosketball;

import fr.bosketball.arena.CourtGeometry;
import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The restricted-area line has to be the finishing line.
 *
 * <p>The paint drew a circle of 1.22 blocks around the rim while the game allowed a finish from
 * 2.85, with a shoulder at 2.15 either side. A player standing in the key, outside the little arc
 * and inside the real zone, was told by the floor that they were not there. The line is now built
 * from {@link CourtGeometry#dunkZoneHalfWidth()} and {@link CourtGeometry#dunkZoneShoulderDepth()},
 * the same two numbers the test uses.
 */
public class DunkPaintTest {

    private static final double EPS = 1.0e-9;

    private static CourtGeometry court() {
        return new CourtGeometry(0.0, 64.0, 0.0);
    }

    @Test
    public void theShoulderSitsOnTheFinishingRadius() {
        CourtGeometry c = court();
        double w = c.dunkZoneHalfWidth();
        double d = c.dunkZoneShoulderDepth();
        assertEquals("the shoulder must meet the arc on the radius",
            CourtGeometry.DUNK_PAINT_RADIUS * CourtGeometry.DUNK_PAINT_RADIUS, d * d + w * w, 1.0e-6);
        assertTrue("the shoulder has to reach into the key", d > 0.5);
    }

    @Test
    public void insideThePaintedLineIsInsideTheRule() {
        CourtGeometry c = court();
        Vector rim = c.southHoopRim();
        double w = c.dunkZoneHalfWidth();
        double d = c.dunkZoneShoulderDepth();

        assertTrue("a step inside the arc is a finish",
            c.isInDunkZone(rim.getX(), rim.getZ() - 1.2, rim));
        assertTrue("on the shoulder line is a finish",
            c.isInDunkZone(rim.getX() + w - 0.02, rim.getZ() - (d * 0.5), rim));
        assertFalse("a step outside the shoulder is not",
            c.isInDunkZone(rim.getX() + w + 0.10, rim.getZ() - (d * 0.5), rim));
        assertEquals("the join point is on the arc", 0.0,
            Math.hypot(d, w) - CourtGeometry.DUNK_PAINT_RADIUS, EPS);
    }

    @Test
    public void thePaintIsNotTheOldDecorativeCircle() {
        CourtGeometry c = court();
        assertTrue("the old 1.22 circle understated the zone by more than a block",
            c.dunkZoneShoulderDepth() > 1.5);
        assertTrue("the zone reaches further than it used to be drawn",
            CourtGeometry.DUNK_PAINT_RADIUS > 1.22 + 1.0);
    }

    @Test
    public void forgivenessStillWorksTheSameWay() {
        CourtGeometry c = court();
        Vector rim = c.southHoopRim();
        double w = c.dunkZoneHalfWidth();
        double z = rim.getZ() - 1.0;
        assertFalse("outside the shoulder by a hair is a miss", c.isInDunkZone(rim.getX() + w + 0.30, z, rim));
        assertTrue("the forgiveness margin still lets it in",
            c.isInDunkZone(rim.getX() + w + 0.30, z, rim, 0.5));
    }
}
