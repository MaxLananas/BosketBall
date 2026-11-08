package fr.bosketball;

import fr.bosketball.arena.Arena;
import fr.bosketball.arena.CourtGeometry;
import fr.bosketball.model.TeamSide;
import fr.bosketball.physics.BasketballEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for the two things a player notices within ten seconds of a match: the ball
 * leaving their hands, and which team a basket counts for.
 *
 * <p>Both were reported from a live server after the ten-agent merge: "I shoot, I never see the
 * ball leave, and it is already at the other end of the court", "both hoops score for the same
 * team". These tests drive the production tick loop headlessly and assert what a spectator would
 * see, not what the code intends.
 */
public class ShotFlightRegressionTest {

    private static CourtGeometry geometry() {
        return new CourtGeometry(0.0, 64.0, 0.0);
    }

    private static Arena arena() {
        return new Arena("flight", "Flight Test", "world", geometry());
    }

    private static final class Recorder implements BasketballEntity.ScoreListener {
        final List<TeamSide> scoredFor = new ArrayList<>();
        final List<Vector> outOfBounds = new ArrayList<>();

        @Override
        public void onBasketScored(TeamSide hoopScoringSide, UUID shooterId, UUID assisterId,
                                   boolean threePointer, boolean slamDunk, boolean pureSwish,
                                   boolean bankShot, double shotDistanceBlocks) {
            scoredFor.add(hoopScoringSide);
        }

        @Override
        public void onOutOfBounds(Vector lastPos, UUID lastTouchPlayerId) {
            outOfBounds.add(lastPos.clone());
        }

        @Override
        public void onRimContact() {
            // The rim was touched: not asserted here, but the listener contract needs it.
        }
    }

    private static BasketballEntity ballAt(Arena arena, Vector spawn) {
        org.bukkit.Location loc = new org.bukkit.Location(null, spawn.getX(), spawn.getY(), spawn.getZ());
        return new BasketballEntity(arena, loc, 0.046, 0.991, 0.7, 0.6, 0.6);
    }

    /**
     * A shot must travel: every tick moves it by a speed a player can follow, and it must never
     * teleport. A jump of more than two blocks in one tick is a teleport, not a shot.
     */
    @Test
    public void aShotNeverTeleportsAcrossTheCourt() {
        Arena arena = arena();
        Vector start = new Vector(0.0, 65.6, -8.0);
        BasketballEntity ball = ballAt(arena, start);

        // A real arc: 26 ticks to the south rim, launched from the shooter's hands.
        Vector rim = arena.geometry().southHoopRim();
        Vector velocity = fr.bosketball.physics.PhysicsMath.solveBallisticArc(start, rim, 0.046, 0.991,
            fr.bosketball.model.ShotReleaseQuality.PERFECT_GREEN, null);
        assertNotNull(velocity);
        assertTrue("the solver must produce a real velocity", velocity.length() > 0.05);

        ball.launchShotVirtual(start, velocity, false, start.distance(rim), UUID.randomUUID());

        Vector previous = ball.position().clone();
        double biggestStep = 0.0;
        for (int tick = 0; tick < 40; tick++) {
            ball.tick(new Recorder());
            Vector now = ball.position().clone();
            biggestStep = Math.max(biggestStep, now.distance(previous));
            previous = now;
        }
        assertTrue("a shot moved " + biggestStep + " blocks in one tick: that is a teleport, not a shot",
            biggestStep <= 2.0);
    }

    /**
     * The ball has to start where the player is and end where the rim is. If either is wrong the
     * player sees a ball that never left their hands and a basket that happened at the far end.
     */
    @Test
    public void aShotStartsAtTheShooterAndEndsAtTheRim() {
        Arena arena = arena();
        Vector start = new Vector(0.0, 65.6, -8.0);
        BasketballEntity ball = ballAt(arena, start);
        Vector rim = arena.geometry().southHoopRim();
        Vector velocity = fr.bosketball.physics.PhysicsMath.solveBallisticArc(start, rim, 0.046, 0.991,
            fr.bosketball.model.ShotReleaseQuality.PERFECT_GREEN, null);

        ball.launchShotVirtual(start, velocity, false, start.distance(rim), UUID.randomUUID());
        assertTrue("the ball must leave from the shooter's hands, not from " + ball.position(),
            ball.position().distance(start) < 1.0);

        // The solver sizes the arc from the distance (13.5 + 1.48 ticks per block, capped at 44),
        // so a shot from nine blocks out flies for roughly forty-five ticks, not twenty-six.
        double closestToRim = Double.MAX_VALUE;
        for (int tick = 0; tick < 50; tick++) {
            ball.tick(new Recorder());
            closestToRim = Math.min(closestToRim, ball.position().distance(rim));
        }
        assertTrue("the ball never came within two blocks of the rim it was aimed at: closest "
            + closestToRim, closestToRim < 2.0);
    }

    /**
     * The core complaint: whatever the hoop, the point has to go to the side that hoop belongs to.
     * A ball dropped through the north rim credits FROST, through the south rim credits IGNIS.
     */
    @Test
    public void eachHoopCreditsItsOwnSide() {
        for (boolean north : new boolean[]{true, false}) {
            Arena arena = arena();
            Vector rim = north ? arena.geometry().northHoopRim() : arena.geometry().southHoopRim();
            Vector above = rim.clone().add(new Vector(0.0, 0.85, 0.0));
            BasketballEntity ball = ballAt(arena, above);

            Recorder recorder = new Recorder();
            ball.launchShotVirtual(above, new Vector(0.0, -0.22, 0.0), false, 3.0, UUID.randomUUID());
            for (int tick = 0; tick < 30 && recorder.scoredFor.isEmpty(); tick++) {
                ball.tick(recorder);
            }

            assertTrue("a ball dropped straight through the " + (north ? "north" : "south")
                + " rim scored for nobody", !recorder.scoredFor.isEmpty());
            assertEquals("the " + (north ? "north" : "south") + " hoop must credit "
                    + (north ? "FROST" : "IGNIS"),
                north ? TeamSide.FROST : TeamSide.IGNIS, recorder.scoredFor.get(0));
        }
    }

    /**
     * A ball caught inside a block steps out along the shortest axis instead of being thrown to
     * the far face of the block, which is what a raycast started inside geometry reports.
     */
    @Test
    public void aBallStuckInAGeometryBlockStepsOutByTheShortestSide() {
        Vector boxMin = new Vector(10.0, 64.0, 10.0);
        Vector boxMax = new Vector(11.0, 65.0, 11.0);
        Vector justInside = new Vector(10.9, 64.5, 10.5);
        Vector freed = fr.bosketball.physics.PhysicsMath.escapeFromBox(boxMin, boxMax, justInside, 0.285);

        assertTrue("the ball must leave the block, not stay inside it: " + freed,
            freed.getX() >= boxMax.getX() + 0.285 - 1e-6 || freed.getX() <= boxMin.getX() - 0.285 + 1e-6
                || freed.getZ() >= boxMax.getZ() + 0.285 - 1e-6 || freed.getZ() <= boxMin.getZ() - 0.285 + 1e-6
                || freed.getY() >= boxMax.getY() + 0.285 - 1e-6 || freed.getY() <= boxMin.getY() - 0.285 + 1e-6);
        assertEquals("0.1 blocks from the east face is the shortest way out",
            boxMax.getX() + 0.285, freed.getX(), 1e-6);
        assertEquals("the other axes must not move", 64.5, freed.getY(), 1e-6);
        assertEquals(10.5, freed.getZ(), 1e-6);
    }

    /** A court whose rims sit on top of each other is unplayable and must say so. */
    @Test
    public void aCourtWithBothRimsInTheSameSpotIsFlagged() {
        CourtGeometry healthy = new CourtGeometry(0.0, 64.0, 0.0);
        assertTrue("a normal court must not be flagged: "
                + healthy.basketToBasketDistance(), !healthy.rimsCoincide());
        assertTrue("a normal court measures a real distance between baskets",
            healthy.basketToBasketDistance() > CourtGeometry.MINIMUM_BASKET_TO_BASKET);

        CourtGeometry broken = new CourtGeometry(0.0, 64.0, 0.0);
        broken.setNorthHoop(new Vector(0.0, 66.0, 1.0), new Vector(0, 0, 1), true);
        broken.setSouthHoop(new Vector(0.0, 66.0, 1.4), new Vector(0, 0, -1), true);
        assertTrue("both rims half a block apart can never be a real court", broken.rimsCoincide());
    }

    /** The banner has to name a player, not "someone", on the first out of bounds of a possession. */
    @Test
    public void anOutOfBoundsAfterATipOffStillNamesTheShooter() {
        Arena arena = arena();
        Vector start = new Vector(0.0, 65.6, -13.2);
        BasketballEntity ball = ballAt(arena, start);
        UUID shooter = UUID.randomUUID();
        Recorder recorder = new Recorder();

        ball.launchShotVirtual(start, new Vector(0.0, 0.05, -0.9), false, 4.0, shooter);
        for (int tick = 0; tick < 40 && recorder.outOfBounds.isEmpty(); tick++) {
            ball.tick(recorder);
        }
        assertTrue("the ball must eventually leave the court", !recorder.outOfBounds.isEmpty());
        assertTrue("the banner must be able to name the shooter", ball.lastTouchId() != null);
    }
}
