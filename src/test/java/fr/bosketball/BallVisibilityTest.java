package fr.bosketball;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import fr.bosketball.physics.BallVisibilityRules;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * The ball is a display entity the plugin hides from its holder while a shot charges. A shot that
 * keeps the shooter in that hidden set is a shot the shooter never sees: it leaves the hands and
 * the ball is gone. These tests pin the rule and the three places that have to obey it.
 */
public class BallVisibilityTest {

    private static final Path BALL =
        Path.of("src/main/java/fr/bosketball/physics/BasketballEntity.java");
    private static final Path ARENA = Path.of("src/main/java/fr/bosketball/arena/Arena.java");

    @Test
    public void onlyALiveChargeMayHideTheBall() {
        // The one true case: the viewer holds the ball, it is dribbling in their hands, they charge.
        assertTrue(BallVisibilityRules.hideFromViewer(true, true, true));

        // Every other combination must leave the ball on screen.
        assertFalse("a shot in the air is never hidden", BallVisibilityRules.hideFromViewer(true, false, true));
        assertFalse("a ball on the floor is never hidden", BallVisibilityRules.hideFromViewer(true, true, false));
        assertFalse("someone else's ball is never hidden",
            BallVisibilityRules.hideFromViewer(false, true, true));
        assertFalse(BallVisibilityRules.hideFromViewer(false, false, false));
        assertFalse(BallVisibilityRules.hideFromViewer(false, true, false));
        assertFalse(BallVisibilityRules.hideFromViewer(false, false, true));
        assertFalse(BallVisibilityRules.hideFromViewer(true, false, false));

        for (boolean holds : new boolean[]{true, false}) {
            for (boolean dribbling : new boolean[]{true, false}) {
                for (boolean charging : new boolean[]{true, false}) {
                    assertTrue("the two entry points must agree",
                        BallVisibilityRules.mustBeVisibleTo(holds, dribbling, charging)
                            == !BallVisibilityRules.hideFromViewer(holds, dribbling, charging));
                }
            }
        }
    }

    @Test
    public void theResyncIntervalIsFrequentEnoughToBeInvisible() {
        assertTrue("re-declaring visibility must happen more than once a second",
            BallVisibilityRules.RESYNC_INTERVAL_TICKS <= 20);
        assertTrue("but not every tick", BallVisibilityRules.RESYNC_INTERVAL_TICKS >= 2);
    }

    @Test
    public void aFlightNeverStaysHidden() throws Exception {
        String ball = Files.readString(BALL);
        int tick = ball.indexOf("public void tick(ScoreListener listener)");
        assertTrue("the ball must tick", tick > 0);
        String body = ball.substring(tick, Math.min(ball.length(), tick + 1600));
        assertTrue("leaving the hands must rebuild the ball if anyone was hidden",
            body.contains("if (mode != BallMode.DRIBBLING) {") && body.contains("respawnVisualBall();"));
        assertTrue("and re-declare the ball visible on a timer",
            body.contains("revealToEveryone();"));
    }

    @Test
    public void aLaunchRevealsTheBallBeforeItCanBeRejected() throws Exception {
        String ball = Files.readString(BALL);
        for (String signature : new String[]{
            "public void launchShot(",
            "public void launchPass(Player passer",
            "public void launchShotVirtual(",
            "public void launchPassVirtual(",
            "public void launchDunkVirtual(",
            "public void startSlamDunk(\n        Player dunker,"}) {
            int start = ball.indexOf(signature);
            assertTrue(signature + " must exist", start > 0);
            String body = ball.substring(start, Math.min(ball.length(), start + 900));
            int reveal = body.indexOf("makeVisibleForLaunch();");
            int guard = body.indexOf("return;");
            assertTrue(signature + " must reveal the ball", reveal > 0);
            assertTrue(signature + " must reveal it before the guard can bail out", reveal < guard);
        }
    }

    @Test
    public void aHiddenBallIsRebuiltNotReshown() throws Exception {
        String ball = Files.readString(BALL);
        int method = ball.indexOf("private void makeVisibleForLaunch()");
        assertTrue("the launch guarantee must exist", method > 0);
        String body = ball.substring(method, Math.min(ball.length(), method + 900));
        int rebuild = body.indexOf("respawnVisualBall();");
        int reshown = body.indexOf("revealToEveryone();");
        assertTrue("a player who was hidden gets a new entity", rebuild > 0);
        assertTrue("the plain case is only a re-show", reshown > 0);
        assertTrue("the rebuild is the branch that covers the hidden case", rebuild < reshown);
        assertTrue("rebuilding the display keeps the same simulated ball",
            ball.contains("private void respawnVisualBall()")
                && !ball.contains("this.position.set(") );
    }

    @Test
    public void theBallIsNotAnOrphanTheCourtSweepCanEat() throws Exception {
        String ball = Files.readString(BALL);
        assertTrue("the visual display carries the live-ball tag",
            ball.contains("disp.addScoreboardTag(LIVE_BALL_TAG);"));
        assertTrue("and so does the interaction hitbox",
            ball.contains("inter.addScoreboardTag(LIVE_BALL_TAG);"));

        String arena = Files.readString(ARENA);
        int purge = arena.indexOf("private void purgeOrphanedCourtEntities(World w)");
        assertTrue("the arena sweep must exist", purge > 0);
        String sweep = arena.substring(purge, Math.min(arena.length(), purge + 2200));
        int skip = sweep.indexOf("BasketballEntity.LIVE_BALL_TAG");
        int kill = sweep.indexOf("ent.remove();");
        assertTrue("the sweep must know the live ball", skip > 0);
        assertTrue("and skip it before removing anything", skip < kill);
    }

    @Test
    public void aDesyncedDisplayIsRebuilt() throws Exception {
        String ball = Files.readString(BALL);
        assertTrue("a display left far from the simulated ball must be rebuilt",
            ball.contains("renderDriftedBeyond(ballLocOrNull())"));
        assertTrue("the drift threshold is small enough to catch a real desync",
            ball.contains("RENDER_DRIFT_BLOCKS_SQ = 4.0"));
    }
}
