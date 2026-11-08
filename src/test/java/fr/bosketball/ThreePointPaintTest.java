package fr.bosketball;

import fr.bosketball.arena.CourtGeometry;
import fr.bosketball.model.TeamSide;
import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The three-point line has to be one line, not two.
 *
 * <p>The paint and the scoring rule were derived separately: {@code Arena} drew the corner at
 * {@code min(halfWidth - 0.85, radius - 0.25)} — 7.00 blocks on the default court — while
 * {@link CourtGeometry#isThreePointShot} flipped at {@link CourtGeometry#THREE_POINT_CORNER_X},
 * 6.60. A corner shooter standing a hand inside the painted line was paid for the other side of
 * it, and a line that lies is worse than no line at all. Both sides now read
 * {@link CourtGeometry#threePointCornerLateral()}, {@link CourtGeometry#threePointArcRadius()} and
 * {@link CourtGeometry#threePointCornerDepth()}.
 */
public class ThreePointPaintTest {

    private static final double EPS = 1.0e-9;
    private static final TeamSide[] SIDES = {TeamSide.IGNIS, TeamSide.FROST};

    private static CourtGeometry court() {
        return new CourtGeometry(0.0, 64.0, 0.0);
    }

    /** Floor coordinates for a shooter standing {@code forward} out and {@code across} aside. */
    private static double[] floor(Vector rim, double forward, double across) {
        Vector fwd = rim.getZ() < 0 ? new Vector(0, 0, 1) : new Vector(0, 0, -1);
        Vector right = new Vector(-fwd.getZ(), 0, fwd.getX());
        return new double[]{
            rim.getX() + fwd.getX() * forward + right.getX() * across,
            rim.getZ() + fwd.getZ() * forward + right.getZ() * across
        };
    }

    private static boolean three(CourtGeometry c, Vector rim, double forward, double across) {
        double[] p = floor(rim, forward, across);
        return c.isThreePointShot(p[0], p[1], rim);
    }

    /** The number the paint draws and the number the rule honours are the same number. */
    @Test
    public void thePaintedCornerSitsOnTheCornerTheRuleHonours() {
        CourtGeometry c = court();
        assertEquals("the painted corner must be the corner the rule uses",
            CourtGeometry.THREE_POINT_CORNER_X, c.threePointCornerLateral(), EPS);
        assertEquals("the painted arc must be the arc the rule uses",
            CourtGeometry.THREE_POINT_RADIUS, c.threePointArcRadius(), EPS);
    }

    /** The straight corner and the arc meet on one point: no gap, no overlap, no kink. */
    @Test
    public void theCornerAndTheArcMeetOnTheBallRadius() {
        CourtGeometry c = court();
        double corner = c.threePointCornerLateral();
        double radius = c.threePointArcRadius();
        double depth = c.threePointCornerDepth();
        assertEquals("the join point must sit exactly on the arc",
            radius * radius, depth * depth + corner * corner, 1.0e-6);
        assertTrue("the corner segment has to point somewhere", depth > 0.5);
    }

    /** Feet behind the painted corner is a three; feet inside it is a two. */
    @Test
    public void aCornerShooterGetsWhatThePaintSays() {
        CourtGeometry c = court();
        double corner = c.threePointCornerLateral();
        for (TeamSide side : SIDES) {
            Vector rim = c.rimForTeam(side);
            assertTrue(side + ": a hand behind the painted corner is a three",
                three(c, rim, 1.0, corner + 0.10));
            assertFalse(side + ": a hand inside the painted corner is a two",
                three(c, rim, 1.0, corner - 0.30));
        }
    }

    /** Deep in the corner, level with the baseline, the shot is still worth three. */
    @Test
    public void aCornerThreeFromTheBaselineStillCounts() {
        CourtGeometry c = court();
        for (TeamSide side : SIDES) {
            Vector rim = c.rimForTeam(side);
            assertTrue(side + ": the most common corner three there is must count",
                three(c, rim, -1.0, c.threePointCornerLateral() + 0.25));
        }
    }

    /** Beyond the join the arc decides, exactly as it is painted. */
    @Test
    public void pastTheJoinTheArcTakesOver() {
        CourtGeometry c = court();
        double corner = c.threePointCornerLateral();
        double depth = c.threePointCornerDepth();
        Vector rim = c.southHoopRim();
        assertTrue("on the join the shot is a three", three(c, rim, depth, corner));
        assertTrue("past the join the arc still owns the same lateral distance",
            three(c, rim, depth + 0.15, corner));
        assertFalse("a step inside the arc past the join is a two",
            three(c, rim, depth + 0.15, corner - 0.30));
        assertTrue("a step outside the arc past the join is a three",
            three(c, rim, depth + 0.15, corner + 0.20));
    }

    /** A court too narrow for the real line still has a line, and the rule still honours it. */
    @Test
    public void aNarrowCourtKeepsItsLineConsistent() {
        CourtGeometry narrow = new CourtGeometry(0.0, 64.0, 0.0, 12.0, 6.0);
        double corner = narrow.threePointCornerLateral();
        double radius = narrow.threePointArcRadius();
        double depth = narrow.threePointCornerDepth();
        assertTrue("the corner has to fit inside the arc", corner < radius);
        assertTrue("the corner still has to hold a foot", corner >= 2.0);
        assertTrue("the join point must exist", depth > 0.0);
        assertEquals("the join point still sits on the arc", radius * radius,
            depth * depth + corner * corner, 1.0e-6);

        Vector rim = narrow.southHoopRim();
        assertTrue("the narrower paint is still honest", three(narrow, rim, depth * 0.5, corner + 0.05));
        assertFalse("and its inside is still a two", three(narrow, rim, depth * 0.5, corner - 0.30));
    }

    /** Both rims answer the same way for the same distance: the side decides the rim, not the shot. */
    @Test
    public void theArcIsSymmetricForBothRims() {
        CourtGeometry c = court();
        for (TeamSide side : SIDES) {
            Vector rim = c.rimForTeam(side);
            assertEquals(side + ": eight blocks straight out is a three", 3,
                c.shotValueAt(floor(rim, 8.0, 0.0)[0], floor(rim, 8.0, 0.0)[1], side));
            assertEquals(side + ": a step off the iron is a two", 2,
                c.shotValueAt(floor(rim, 1.5, 0.5)[0], floor(rim, 1.5, 0.5)[1], side));
        }
    }
}
