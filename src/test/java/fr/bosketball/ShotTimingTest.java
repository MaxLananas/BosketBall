package fr.bosketball;

import fr.bosketball.model.ShotReleaseQuality;
import fr.bosketball.physics.PhysicsMath;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The badge on the shot and the ball in the air have to tell the same story.
 *
 * <p>{@link ShotReleaseQuality#evaluate} widens the green window on a hot streak, but the timing
 * offset in {@link PhysicsMath} measured the release against the cold window. A hot player who
 * released at 0.82 was shown "Green" — and the ball was then scattered by a penalty the badge said
 * did not exist. The quality now drives both: a green release carries no timing offset at all,
 * whatever the charge was, and the sign of the offset comes from the name of the release.
 */
public class ShotTimingTest {

    private static final Vector RIM = new Vector(0.0, 67.05, -13.75);
    private static final Vector HOOP_FORWARD = new Vector(0.0, 0.0, 1.0);
    private static final Vector EYE = new Vector(0.4, 65.62, -5.0);

    private static Vector look() {
        return RIM.clone().subtract(EYE).normalize();
    }

    private static Vector shoot(double charge, ShotReleaseQuality quality, long seed) {
        Vector look = look();
        PhysicsMath.AimEvaluation aim = PhysicsMath.evaluateShooterAim(EYE, look, RIM);
        assertTrue("the test shot must be aimed at the rim", aim.aimingAtHoop());
        return PhysicsMath.solveSkillShotVelocity(
            EYE, look, RIM, HOOP_FORWARD, aim, charge, 0.046, 0.991, quality, 0.0, new Random(seed));
    }

    /** A green is a green: the charge that earned the badge must not leave a hidden penalty. */
    @Test
    public void aGreenReleaseIsTheSameShotWhateverTheChargeWas() {
        Vector hotGreen = shoot(0.82, ShotReleaseQuality.PERFECT_GREEN, 4242L);
        Vector coldGreen = shoot(0.92, ShotReleaseQuality.PERFECT_GREEN, 4242L);
        assertEquals("x", coldGreen.getX(), hotGreen.getX(), 1.0e-9);
        assertEquals("y", coldGreen.getY(), hotGreen.getY(), 1.0e-9);
        assertEquals("z", coldGreen.getZ(), hotGreen.getZ(), 1.0e-9);
    }

    /** And a miss is not a green: an early release still lands short of a perfect one. */
    @Test
    public void anEarlyReleaseDoesNotFlyLikeAGreen() {
        Vector green = shoot(0.90, ShotReleaseQuality.PERFECT_GREEN, 77L);
        Vector early = shoot(0.60, ShotReleaseQuality.VERY_EARLY, 77L);
        assertFalse("an early release must not be the perfect shot",
            Math.abs(green.getY() - early.getY()) < 1.0e-6
                && Math.abs(green.getZ() - early.getZ()) < 1.0e-6);
    }

    /** Early and late pull the ball in opposite directions; the badge decides which. */
    @Test
    public void earlyAndLateAreNotTheSameWildShot() {
        Vector early = shoot(0.78, ShotReleaseQuality.SLIGHTLY_EARLY, 9L);
        Vector late = shoot(1.09, ShotReleaseQuality.SLIGHTLY_LATE, 9L);
        assertFalse("early and late cannot both be the same flight",
            Math.abs(early.getY() - late.getY()) < 1.0e-6
                && Math.abs(early.getZ() - late.getZ()) < 1.0e-6);
    }

    /** A release the label calls green is inside the window that label was built from. */
    @Test
    public void theHotWindowIsTheOneTheBadgeUses() {
        double hotLow = ShotReleaseQuality.greenWindowLow(true, 6.0);
        double coldLow = ShotReleaseQuality.greenWindowLow(false, 6.0);
        assertTrue("a hot streak must open the window, not close it", hotLow <= coldLow);
        assertEquals("0.82 is green on a hot streak", ShotReleaseQuality.PERFECT_GREEN,
            ShotReleaseQuality.evaluate(0.82, 6.0, true));
        assertEquals("0.82 is not green cold", ShotReleaseQuality.SLIGHTLY_EARLY,
            ShotReleaseQuality.evaluate(0.82, 6.0, false));
    }
}
