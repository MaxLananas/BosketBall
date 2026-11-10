package fr.bosketball;

import fr.bosketball.arena.CourtGeometry;
import fr.bosketball.model.TeamSide;
import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * What a basket is worth.
 *
 * <p>Two rules decide the number on the board: which rim credited the side, and whether the shot
 * was released behind the arc or in the corner. Both were wrong: the scorer measured the shot
 * against the *other* team's rim, so a layup came out worth three, and the corner strip started
 * half a block in front of the iron, so a corner three with your feet on the line scored two.
 */
public class CourtScoringTest {

    private static CourtGeometry court() {
        return new CourtGeometry(0.0, 64.0, 0.0);
    }

    @Test
    public void aLayupAtEitherRimIsWorthTwo() {
        CourtGeometry c = court();
        for (TeamSide side : new TeamSide[]{TeamSide.FROST, TeamSide.IGNIS}) {
            Vector rim = c.rimForTeam(side);
            assertEquals("a shot taken right under the " + side + " rim is a layup", 2,
                c.shotValueAt(rim.getX() + 0.6, rim.getZ(), side));
        }
    }

    @Test
    public void aShotFromBehindTheArcIsWorthThree() {
        CourtGeometry c = court();
        for (TeamSide side : new TeamSide[]{TeamSide.FROST, TeamSide.IGNIS}) {
            Vector rim = c.rimForTeam(side);
            Vector forward = c.hoopForwardForRim(rim);
            double x = rim.getX() + forward.getX() * 9.0;
            double z = rim.getZ() + forward.getZ() * 9.0;
            assertEquals("nine blocks straight out is a three", 3, c.shotValueAt(x, z, side));
        }
    }

    @Test
    public void aShotFromMidRangeIsWorthTwo() {
        CourtGeometry c = court();
        for (TeamSide side : new TeamSide[]{TeamSide.FROST, TeamSide.IGNIS}) {
            Vector rim = c.rimForTeam(side);
            Vector forward = c.hoopForwardForRim(rim);
            // Five blocks out, straight at the rim: comfortably inside the arc.
            double x = rim.getX() + forward.getX() * 5.0;
            double z = rim.getZ() + forward.getZ() * 5.0;
            assertEquals("five blocks out is a mid-range two", 2, c.shotValueAt(x, z, side));
        }
    }

    /**
     * The corner three: feet on the baseline, seven blocks to the side. The straight strip is
     * shorter than the arc, so the distance test alone says two. It is the most common three in
     * the game and it has to count.
     */
    @Test
    public void aCornerThreeFromTheBaselineCounts() {
        CourtGeometry c = court();
        for (TeamSide side : new TeamSide[]{TeamSide.FROST, TeamSide.IGNIS}) {
            Vector rim = c.rimForTeam(side);
            Vector forward = c.hoopForwardForRim(rim);
            // One block behind the rim, on the baseline side, seven blocks across.
            Vector right = new Vector(-forward.getZ(), 0, forward.getX());
            double x = rim.getX() - forward.getX() + right.getX() * 7.0;
            double z = rim.getZ() - forward.getZ() + right.getZ() * 7.0;
            double toRim = Math.hypot(x - rim.getX(), z - rim.getZ());
            assertTrue("this case must sit inside the arc, or it does not test the corner rule: "
                + toRim, toRim < CourtGeometry.THREE_POINT_RADIUS);
            assertEquals("a corner three has to count as three", 3, c.shotValueAt(x, z, side));
        }
    }

    /** Just inside the corner line is still a two. */
    @Test
    public void justInsideTheCornerLineIsATwo() {
        CourtGeometry c = court();
        Vector rim = c.rimForTeam(TeamSide.FROST);
        Vector forward = c.hoopForwardForRim(rim);
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        double x = rim.getX() - forward.getX() + right.getX() * 5.4;
        double z = rim.getZ() - forward.getZ() + right.getZ() * 5.4;
        assertEquals("5.4 blocks across the baseline is short of the corner line", 2,
            c.shotValueAt(x, z, TeamSide.FROST));
    }

    /** The rim that credits a side is that side's own rim, not the opponent's. */
    @Test
    public void theCreditingRimIsTheSidesOwnRim() {
        CourtGeometry c = court();
        assertTrue("the north rim credits FROST",
            c.rimForTeam(TeamSide.FROST).distanceSquared(c.northHoopRim()) < 1e-9);
        assertTrue("the south rim credits IGNIS",
            c.rimForTeam(TeamSide.IGNIS).distanceSquared(c.southHoopRim()) < 1e-9);
    }
}
