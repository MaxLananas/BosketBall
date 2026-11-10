package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import fr.bosketball.physics.BallHandPose;
import org.bukkit.util.Vector;
import org.junit.Test;

/**
 * The ball is never hidden from its shooter any more, so the pose has to do the job the hide used
 * to do badly: keep a 0.6-block ball out of a face that is 0.4 blocks away from it.
 *
 * <p>These tests are angles and distances, not source text - the same numbers the game uses, read
 * the same way. If a future round moves the ball closer, one of them goes red and says why.
 */
public class BallHandPoseTest {

    private static final Vector LOOK = new Vector(0.0, 0.0, 1.0);
    private static final Vector RIGHT = new Vector(1.0, 0.0, 0.0);
    /** The drawn radius of the ball at the gather scale: a 1.18-block model at ~0.4 scale. */
    private static final double BALL_RADIUS_AT_GATHER = 0.24;

    private static final double[] CHARGES = {0.0, 0.15, 0.35, 0.6, 0.9, 1.0};
    private static final double[] HANDS = {1.0, -1.0};

    @Test
    public void theBallNeverSitsInTheShootersFace() {
        for (double hand : HANDS) {
            for (double charge : CHARGES) {
                double distance = BallHandPose.distanceFromEye(new Vector(0, 65, 0), LOOK, RIGHT, hand, charge);
                assertTrue("the ball must be held well clear of the eye, got " + distance,
                    distance >= 0.80);
                assertTrue("and within arm's reach, got " + distance, distance <= 1.05);

                double angularRadius = Math.toDegrees(Math.atan(BALL_RADIUS_AT_GATHER / distance));
                assertTrue("a ball that covers a third of the screen is the old bug with the opposite"
                        + " sign, got " + angularRadius + " degrees of radius",
                    angularRadius <= 17.0);
            }
        }
    }

    @Test
    public void theBallIsHeldToTheSideNotOverTheCrosshair() {
        for (double hand : HANDS) {
            for (double charge : CHARGES) {
                double offAxis = BallHandPose.degreesOffAxis(LOOK, RIGHT, hand, charge);
                assertTrue("the ball has to sit outside the aiming line, got " + offAxis,
                    offAxis >= 22.0);
                assertTrue("but still in the shooter's field of view, got " + offAxis,
                    offAxis <= 32.0);
            }
        }
    }

    @Test
    public void theBallIsAlwaysInFrontOfTheShooter() {
        for (double hand : HANDS) {
            for (double charge : CHARGES) {
                Vector eye = new Vector(0, 65, 0);
                Vector pocket = BallHandPose.pocket(eye, LOOK, RIGHT, hand, charge);
                Vector toBall = pocket.clone().subtract(eye);
                assertTrue("a ball behind the shooter is a ball they cannot see, got " + toBall.getZ(),
                    toBall.getZ() >= 0.6);
            }
        }
    }

    @Test
    public void theGatherRisesWithTheCharge() {
        double low = BallHandPose.pocket(new Vector(0, 65, 0), LOOK, RIGHT, 1.0, 0.0).getY() - 65.0;
        double high = BallHandPose.pocket(new Vector(0, 65, 0), LOOK, RIGHT, 1.0, 1.0).getY() - 65.0;
        assertEquals("the ball starts just under the eye line", -BallHandPose.EYE_DROP, low, 1.0e-6);
        assertEquals("and lifts by the charge rise over a full pull",
            BallHandPose.CHARGE_RISE - BallHandPose.EYE_DROP, high, 1.0e-6);
        assertTrue("a gather that does not rise is a ball being pushed, not shot", high > low);
    }

    @Test
    public void theBallShrinksAsTheStringComesBack() {
        double previous = Double.MAX_VALUE;
        for (double charge : CHARGES) {
            float scale = BallHandPose.scale(charge);
            assertTrue("the ball gets smaller as the pull deepens", scale < previous + 1.0e-6);
            assertTrue("without disappearing, got " + scale, scale >= 0.30f);
            assertTrue("and without going the other way, got " + scale, scale <= 0.42f);
            previous = scale;
        }
        assertEquals("start and full pull are the constants",
            BallHandPose.SCALE_AT_FULL, BallHandPose.scale(1.0), 1.0e-6);
        assertEquals(BallHandPose.SCALE_AT_START, BallHandPose.scale(0.0), 1.0e-6);
        assertEquals("a charge past full is a full pull", BallHandPose.SCALE_AT_FULL,
            BallHandPose.scale(4.0), 1.0e-6);
    }

    @Test
    public void theHandSideIsRespected() {
        Vector eye = new Vector(0, 65, 0);
        Vector right = BallHandPose.pocket(eye, LOOK, RIGHT, 1.0, 0.5).subtract(eye);
        Vector left = BallHandPose.pocket(eye, LOOK, RIGHT, -1.0, 0.5).subtract(eye);
        assertTrue("a right-handed release holds the ball to the right", right.getX() > 0.3);
        assertTrue("a left-handed one holds it to the left", left.getX() < -0.3);
        assertEquals("the two hands mirror each other", right.getX(), -left.getX(), 1.0e-9);
        assertEquals("and hold it at the same height", right.getY(), left.getY(), 1.0e-9);
    }

    @Test
    public void badNumbersDoNotPutTheBallOnMars() {
        Vector eye = new Vector(0, 65, 0);
        Vector safe = BallHandPose.pocket(eye, LOOK, RIGHT, 1.0, 0.5);

        assertEquals("a null eye is a zero vector, not a crash", new Vector(), BallHandPose.pocket(null, LOOK, RIGHT, 1.0, 0.5));
        assertEquals("a degraded look direction falls back to south", safe.getZ(),
            BallHandPose.pocket(eye, null, RIGHT, 1.0, 0.5).getZ(), 1.0e-9);
        assertEquals("a look direction with no horizontal part falls back too", safe.getZ(),
            BallHandPose.pocket(eye, new Vector(0, 1, 0), RIGHT, 1.0, 0.5).getZ(), 1.0e-9);
        assertEquals("a NaN charge is a gather that has not started",
            BallHandPose.SCALE_AT_START, BallHandPose.scale(Double.NaN), 1.0e-6);
        assertEquals("a negative charge is not a backwards pull",
            BallHandPose.SCALE_AT_START, BallHandPose.scale(-3.0), 1.0e-6);

        Vector nanLook = new Vector(Double.NaN, 0.0, Double.NaN);
        Vector pocket = BallHandPose.pocket(eye, nanLook, RIGHT, 1.0, 0.0);
        assertTrue("a NaN look direction still gives a finite pocket",
            Double.isFinite(pocket.getX()) && Double.isFinite(pocket.getY()) && Double.isFinite(pocket.getZ()));
    }
}
