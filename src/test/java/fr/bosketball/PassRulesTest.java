package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import fr.bosketball.physics.PassRules;
import fr.bosketball.util.Distances;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.Test;

/**
 * A pass is aimed at a teammate who is moving, and both numbers that describe them used to be
 * taken on trust: the receiver's velocity and the length of the aim vector. A receiver with a dash
 * or a knockback handed the pass a target tens of blocks away, and a receiver standing on the
 * passer handed it a zero vector.
 */
public class PassRulesTest {

    @Test
    public void theLeadIsCappedNoMatterHowFastTheReceiverMoves() {
        Vector chest = new Vector(10.0, 65.0, 0.0);
        Vector sprinting = PassRules.leadTarget(chest, new Vector(3.0, 0.0, 0.0));
        double lead = sprinting.getX() - chest.getX();
        assertEquals("three blocks per tick is a dash, the lead is capped",
            PassRules.MAX_LEAD_BLOCKS_PER_TICK * PassRules.LEAD_TICKS, lead, 1e-6);
        assertTrue("and it is never negative", lead > 0.0);

        Vector walking = PassRules.leadTarget(chest, new Vector(0.2, 0.0, 0.0));
        assertEquals(0.2 * PassRules.LEAD_TICKS, walking.getX() - chest.getX(), 1e-6);
    }

    @Test
    public void aBadReceiverVelocityLeavesTheTargetOnTheirChest() {
        Vector chest = new Vector(4.0, 66.0, -2.0);
        assertEquals(chest.getX(), PassRules.leadTarget(chest, null).getX(), 1e-9);
        assertEquals(chest.getZ(),
            PassRules.leadTarget(chest, new Vector(Double.NaN, 0.0, 0.0)).getZ(), 1e-9);
        assertTrue("a NaN chest cannot become a NaN target",
            Double.isFinite(PassRules.leadTarget(new Vector(Double.NaN, 0, 0), new Vector(1, 0, 0)).length()));
        assertTrue(Double.isFinite(PassRules.leadTarget(null, null).length()));
    }

    @Test
    public void aReceiverStandingOnThePasserStillGetsAPass() {
        Vector look = new Vector(0.0, 0.0, 1.0);
        for (Vector delta : new Vector[]{
            new Vector(0.0, 0.0, 0.0), null, new Vector(Double.NaN, 0.0, 0.0)}) {
            Vector velocity = PassRules.velocityTo(delta, look);
            assertTrue("the pass must have a real speed", velocity.length() > 0.5);
            assertTrue("and it must go somewhere the passer is looking", velocity.getZ() > 0.5);
        }
    }

    @Test
    public void thePassSpeedIsBoundedOnBothEnds() {
        Vector look = new Vector(0.0, 0.0, 1.0);
        Vector shortPass = PassRules.velocityTo(new Vector(0.0, 0.0, 0.4), look);
        assertTrue("a very short pass is not slower than the floor",
            shortPass.length() >= PassRules.MIN_PASS_SPEED - 1e-6);

        Vector longPass = PassRules.velocityTo(new Vector(0.0, 0.0, 60.0), look);
        assertTrue("and a very long one is not a cannon",
            longPass.length() <= PassRules.MAX_PASS_SPEED + PassRules.PASS_LIFT + 1e-6);
        assertTrue(Double.isFinite(longPass.getZ()));
    }

    @Test
    public void aPlayerInAnotherWorldCannotInterceptOrContest() throws Exception {
        String session = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/fr/bosketball/game/MatchSession.java"));

        int intercept = session.indexOf("private void checkPlayerBallInterceptionAndRebounds()");
        assertTrue(intercept > 0);
        String interceptBody = session.substring(intercept, Math.min(session.length(), intercept + 2600));
        assertTrue("an interception needs the interceptor on this court",
            interceptBody.contains("courtWorld") && interceptBody.contains("!p.getWorld().equals(courtWorld)"));

        int contest = session.indexOf("private double computeLiveContestRatio(");
        assertTrue(contest > 0);
        String contestBody = session.substring(contest, Math.min(session.length(), contest + 2600));
        assertTrue("a contest needs the defender on this court",
            contestBody.contains("!other.getWorld().equals(courtWorld)"));
    }

    @Test
    public void aPassAcrossWorldsIsNotAPass() {
        Location a = new Location(null, 0.0, 64.0, 0.0);
        Location b = new Location(null, 3.0, 64.0, 4.0);
        assertEquals("same (headless) world, a real distance", 25.0, Distances.squared(a, b), 1e-9);
        assertEquals(5.0, Distances.between(a, b), 1e-9);
        assertEquals("no location is never close", Double.MAX_VALUE, Distances.squared(null, b), 0.0);
        assertEquals(Double.MAX_VALUE, Distances.squared(a, null), 0.0);
        assertEquals("a NaN coordinate cannot pass for close",
            Double.MAX_VALUE, Distances.squared(new Location(null, Double.NaN, 0, 0), b), 0.0);
        assertTrue("a hundred blocks away is a hundred blocks away",
            Distances.squared(a, new Location(null, 100.0, 64.0, 0.0)) > 4.0);
    }
}
