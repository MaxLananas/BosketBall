package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import fr.bosketball.arena.Arena;
import fr.bosketball.arena.CourtGeometry;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.ShotReleaseQuality;
import fr.bosketball.model.TeamSide;
import fr.bosketball.physics.BasketballEntity;
import fr.bosketball.physics.PhysicsMath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.Test;

/**
 * The ball as a client would receive it, tick by tick.
 *
 * <p>Every existing ball test runs against a null world, which is why they can prove the physics of
 * a shot and say nothing about the ball being on screen. The complaint this test answers is the
 * other half: "when you throw it, it disappears". A ball can vanish while the server simulates it
 * perfectly, and there are only five ways for that to happen - the display was never spawned, it
 * was spawned with an empty item, it was scaled to nothing, it kept a zero-size culling box, or it
 * was deleted with nothing spawned in its place. This test drives the production launch and tick
 * code against {@link FakeBukkitWorld} and fails on any of the five, on any tick.
 */
public final class BallDisplayHarnessTest {

    private static final double GRAVITY = 0.046;
    private static final double DRAG = 0.991;

    private static final class Recorder implements BasketballEntity.ScoreListener {
        final List<TeamSide> scoredFor = new ArrayList<>();

        @Override
        public void onBasketScored(TeamSide hoopScoringSide, UUID shooterId, UUID assisterId,
                                   boolean threePointer, boolean slamDunk, boolean pureSwish,
                                   boolean bankShot, double shotDistanceBlocks) {
            scoredFor.add(hoopScoringSide);
        }

        @Override
        public void onOutOfBounds(Vector lastPos, UUID lastTouchPlayerId) {
        }

        @Override
        public void onRimContact() {
        }
    }

    private static Arena arena() {
        return new Arena("harness", "Harness Court", "world", new CourtGeometry(0.0, 64.0, 0.0));
    }

    /** What a client can tell about the ball from one tick's packets. */
    private static void assertOnScreen(String when, BasketballEntity ball, FakeBukkitWorld fake) {
        ItemDisplay display = ball.visualDisplayEntity();
        assertNotNull(when + ": the ball has no display entity at all", display);

        FakeBukkitWorld.Spawned state = null;
        for (FakeBukkitWorld.Spawned candidate : fake.spawned()) {
            if (candidate.proxy == display) {
                state = candidate;
                break;
            }
        }
        assertNotNull(when + ": the display the ball holds was never spawned by the world", state);
        assertTrue(when + ": the display is already removed", state.valid);

        // Presence is all that can be checked offline: ItemStack#getType and friends go through a
        // craft delegate that only a live server installs. A display that was handed an item stack
        // is drawn; a display that was handed nothing is not.
        assertNotNull(when + ": the display carries no item, so the client draws nothing", state.item);
        assertTrue(when + ": the display was handed something that is not an item stack",
            state.item instanceof ItemStack);

        assertNotNull(when + ": the display has no transformation", state.transformation);
        float sx = state.scaleX();
        float sy = state.scaleY();
        float sz = state.transformation.getScale().z;
        assertTrue(when + ": the ball is scaled to " + sx + "/" + sy + "/" + sz,
            sx > 0.20f && sy > 0.20f && sz > 0.20f);

        assertTrue(when + ": the culling box is " + state.width + "x" + state.height
            + " - a zero box is a point the client can drop", state.width >= 1.0f && state.height >= 1.0f);
        assertTrue(when + ": view range is " + state.viewRange, state.viewRange >= 1.0f);

        assertNotNull(when + ": the display was never placed anywhere", state.location);
        double drift = state.location.toVector().distance(ball.position());
        assertTrue(when + ": the display sits " + drift + " blocks from the ball", drift <= 2.5);
    }

    private static BasketballEntity ballIn(Arena arena, FakeBukkitWorld fake, Vector at) {
        Location spawn = new Location(fake.world(), at.getX(), at.getY(), at.getZ());
        return new BasketballEntity(arena, spawn, GRAVITY, DRAG, 0.7, 0.6, 0.6);
    }

    @Test
    public void aThrownBallStaysOnScreenForTheWholeFlight() {
        Arena arena = arena();
        FakeBukkitWorld fake = new FakeBukkitWorld();
        Vector start = new Vector(0.0, 65.6, -8.0);
        BasketballEntity ball = ballIn(arena, fake, start);
        assertOnScreen("before the throw", ball, fake);

        Vector rim = arena.geometry().southHoopRim();
        Vector velocity = PhysicsMath.solveBallisticArc(start, rim, GRAVITY, DRAG,
            ShotReleaseQuality.PERFECT_GREEN, null);
        assertNotNull(velocity);
        ball.launchShotVirtual(start, velocity, false, start.distance(rim), UUID.randomUUID());

        Recorder recorder = new Recorder();
        for (int tick = 0; tick < 60; tick++) {
            ball.tick(recorder);
            assertOnScreen("tick " + tick + " of the flight (ball at " + ball.position() + ")", ball, fake);
        }
    }

    @Test
    public void theThrowSpawnsExactlyOneDisplayAndNeverStrobesIt() {
        Arena arena = arena();
        FakeBukkitWorld fake = new FakeBukkitWorld();
        Vector start = new Vector(0.0, 65.6, -4.0);
        BasketballEntity ball = ballIn(arena, fake, start);

        Vector rim = arena.geometry().southHoopRim();
        Vector velocity = PhysicsMath.solveBallisticArc(start, rim, GRAVITY, DRAG,
            ShotReleaseQuality.PERFECT_GREEN, null);
        ball.launchShotVirtual(start, velocity, false, start.distance(rim), UUID.randomUUID());

        int displaysAfterThrow = fake.aliveOf(ItemDisplay.class).size();
        assertEquals("a throw must leave exactly one ball display alive", 1, displaysAfterThrow);

        Recorder recorder = new Recorder();
        int removals = 0;
        for (int tick = 0; tick < 60; tick++) {
            int before = fake.aliveOf(ItemDisplay.class).size();
            ball.tick(recorder);
            int after = fake.aliveOf(ItemDisplay.class).size();
            if (after < before) {
                removals++;
                assertEquals("the ball display was deleted mid-flight and not replaced on tick " + tick,
                    before, after);
            }
            assertEquals("the flight spawned a second ball display on tick " + tick, 1,
                fake.aliveOf(ItemDisplay.class).size());
        }
        assertEquals("the flight removed the display", 0, removals);
    }

    @Test
    public void theHitboxTravelsWithTheBall() {
        Arena arena = arena();
        FakeBukkitWorld fake = new FakeBukkitWorld();
        Vector start = new Vector(0.0, 65.6, -4.0);
        BasketballEntity ball = ballIn(arena, fake, start);

        Vector rim = arena.geometry().southHoopRim();
        Vector velocity = PhysicsMath.solveBallisticArc(start, rim, GRAVITY, DRAG,
            ShotReleaseQuality.PERFECT_GREEN, null);
        ball.launchShotVirtual(start, velocity, false, start.distance(rim), UUID.randomUUID());

        Recorder recorder = new Recorder();
        for (int tick = 0; tick < 40; tick++) {
            ball.tick(recorder);
            List<FakeBukkitWorld.Spawned> hitboxes = fake.aliveOf(Interaction.class);
            assertEquals("tick " + tick + " lost the ball's hitbox", 1, hitboxes.size());
            double drift = hitboxes.get(0).location.toVector().distance(ball.position());
            assertTrue("tick " + tick + ": the hitbox is " + drift + " blocks from the ball", drift <= 2.5);
        }
    }
    /**
     * The server, a plugin, or a chunk unload can delete the display under a ball that is in the
     * air. The repair has to put it back on the next repair tick, not on the next launch: a ball
     * that only comes back when it is thrown again is a ball that is invisible for the rest of the
     * possession.
     */
    @Test
    public void aDisplayDeletedMidFlightIsBackOnScreenWithinAFewTicks() {
        Arena arena = arena();
        FakeBukkitWorld fake = new FakeBukkitWorld();
        Vector start = new Vector(0.0, 65.6, -4.0);
        BasketballEntity ball = ballIn(arena, fake, start);

        Vector rim = arena.geometry().southHoopRim();
        Vector velocity = PhysicsMath.solveBallisticArc(start, rim, GRAVITY, DRAG,
            ShotReleaseQuality.PERFECT_GREEN, null);
        ball.launchShotVirtual(start, velocity, false, start.distance(rim), UUID.randomUUID());

        Recorder recorder = new Recorder();
        ball.tick(recorder);
        Object doomed = ball.visualDisplayEntity();
        // What a vanished display looks like from the server's side.
        for (FakeBukkitWorld.Spawned spawned : fake.spawned()) {
            if (spawned.proxy == doomed) {
                spawned.valid = false;
                spawned.removed = true;
            }
        }

        boolean back = false;
        for (int tick = 0; tick < 10 && !back; tick++) {
            ball.tick(recorder);
            ItemDisplay display = ball.visualDisplayEntity();
            back = display != null && !display.isDead()
                && fake.aliveOf(ItemDisplay.class).size() == 1 && display != doomed;
        }
        assertTrue("a deleted ball display never came back: the ball stays invisible for the"
            + " rest of the possession", back);
        assertOnScreen("after the repair", ball, fake);
    }

    /** A skin change recolours the ball. It must never be the thing that deletes it. */
    @Test
    public void swappingSkinMidFlightNeverDeletesTheBall() {
        Arena arena = arena();
        FakeBukkitWorld fake = new FakeBukkitWorld();
        Vector start = new Vector(0.0, 65.6, -4.0);
        BasketballEntity ball = ballIn(arena, fake, start);

        Vector rim = arena.geometry().southHoopRim();
        Vector velocity = PhysicsMath.solveBallisticArc(start, rim, GRAVITY, DRAG,
            ShotReleaseQuality.PERFECT_GREEN, null);
        ball.launchShotVirtual(start, velocity, false, start.distance(rim), UUID.randomUUID());

        Recorder recorder = new Recorder();
        ball.tick(recorder);
        for (BallSkin skin : new BallSkin[] {BallSkin.FIRE, BallSkin.CLASSIC, BallSkin.FIRE}) {
            ball.setSkin(skin, skin == BallSkin.FIRE);
            assertEquals("swapping to " + skin + " left " + fake.aliveOf(ItemDisplay.class).size()
                + " ball displays alive", 1, fake.aliveOf(ItemDisplay.class).size());
            assertOnScreen("right after swapping to " + skin, ball, fake);
            ball.tick(recorder);
            assertOnScreen("one tick after swapping to " + skin, ball, fake);
        }
    }
}
