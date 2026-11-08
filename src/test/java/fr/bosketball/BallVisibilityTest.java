package fr.bosketball;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * The ball is a display entity. A display entity can be made invisible by things no server can
 * observe: a client-side hidden set, a culling box the client computed as empty, a spawn packet it
 * dropped. The score never notices, because scoring is pure physics on the ball's position - which
 * is exactly how a player ends up watching an empty court while the basket counts.
 *
 * <p>So the answer is not another patch on the hiding logic. It is a rule: <b>this plugin never
 * hides the ball</b>, a shot starts on a brand new display entity, that entity carries a real
 * culling box, and it re-declares itself visible on a timer for whoever else might have hidden it.
 * These tests pin those four things.
 */
public class BallVisibilityTest {

    private static final Path BALL =
        Path.of("src/main/java/fr/bosketball/physics/BasketballEntity.java");
    private static final Path ARENA = Path.of("src/main/java/fr/bosketball/arena/Arena.java");
    private static final Path CINEMATIC =
        Path.of("src/main/java/fr/bosketball/ui/CinematicCameraManager.java");

    private static String ball() throws Exception {
        return Files.readString(BALL);
    }

    private static String between(String source, int start, int length) {
        return source.substring(start, Math.min(source.length(), start + length));
    }

    @Test
    public void theBallIsNeverHiddenFromAnyPlayer() throws Exception {
        String ball = ball();
        assertFalse("nothing in the ball may put a viewer in a hidden set",
            ball.contains(".hideEntity("));
        assertFalse("the ball keeps no hidden-viewer bookkeeping at all",
            ball.contains("hiddenGatherViewerId"));
    }

    @Test
    public void aChargingShooterStillSeesTheirBall() throws Exception {
        String ball = ball();
        int gather = ball.indexOf("boolean activelyDrawingBow");
        assertTrue("the gather must exist", gather > 0);
        String body = between(ball, gather, 1800);
        assertFalse("the gather must not hide the ball from the shooter", body.contains("hideEntity("));
        assertFalse("nor reach for a visibility helper", body.contains("syncGatherVisibility"));
        assertTrue("it moves the ball into the shooting hand instead",
            body.contains("pocketTarget") && body.contains("playerRight"));
        assertTrue("and draws it slightly smaller while the string is pulled",
            body.contains("gatherScale"));
    }

    @Test
    public void theDisplayCannotBeCulledAway() throws Exception {
        String ball = ball();
        int spawn = ball.indexOf("private void spawnVisualBall()");
        assertTrue(spawn > 0);
        String body = between(ball, spawn, 2400);
        assertTrue("a display with a zero-size culling box is a point, and a point can leave the frame",
            body.contains("disp.setDisplayWidth(VISUAL_BALL_CULL_SIZE);"));
        assertTrue("both dimensions, or the client has a flat box to cull with",
            body.contains("disp.setDisplayHeight(VISUAL_BALL_CULL_SIZE);"));

        Matcher size = Pattern.compile("VISUAL_BALL_CULL_SIZE = ([0-9.]+)f").matcher(ball);
        assertTrue("the culling size must be a named constant", size.find());
        float value = Float.parseFloat(size.group(1));
        assertTrue("a ball a block across needs a box at least that big, got " + value, value >= 1.0f);
        assertTrue("and not a box so large it defeats culling, got " + value, value <= 3.0f);
    }

    @Test
    public void everyLaunchStartsWithAFreshDisplay() throws Exception {
        String ball = ball();
        for (String signature : new String[]{
            "public void launchShot(",
            "public void launchPass(Player passer",
            "public void launchShotVirtual(",
            "public void launchPassVirtual(",
            "public void launchDunkVirtual(",
            "public void startSlamDunk(\n        Player dunker,"}) {
            int start = ball.indexOf(signature);
            assertTrue(signature + " must exist", start > 0);
            String body = between(ball, start, 900);
            int rebuild = body.indexOf("presentBallForLaunch();");
            int guard = body.indexOf("return;");
            assertTrue(signature + " must present the ball", rebuild > 0);
            assertTrue(signature + " must do it before the guard can bail out", rebuild < guard);
        }

        int present = ball.indexOf("private void presentBallForLaunch()");
        assertTrue(present > 0);
        String body = between(ball, present, 900);
        assertTrue("a ball that leaves a hand is a new entity, never a trusted one",
            body.contains("respawnVisualBall();"));
        assertTrue("and a missing display is rebuilt whole",
            body.contains("spawnEntities();"));
    }

    @Test
    public void aFreshDisplayIsASpawnNotAReshuffle() throws Exception {
        String ball = ball();
        int respawn = ball.indexOf("private void respawnVisualBall()");
        assertTrue(respawn > 0);
        String body = between(ball, respawn, 900);
        assertTrue("the old display goes", body.contains("visualBall.remove();"));
        assertTrue("the new one is spawned", body.contains("spawnVisualBall();"));
        assertTrue("and everybody is told about it", body.contains("revealToEveryone();"));
    }

    @Test
    public void aBallInTheAirRedeclaresItselfOftenEnough() throws Exception {
        String ball = ball();
        Matcher interval = Pattern.compile("VISIBLE_REDECLARE_TICKS = (\\d+);").matcher(ball);
        assertTrue("the re-declare interval must be a named constant", interval.find());
        int ticks = Integer.parseInt(interval.group(1));
        assertTrue("a hidden ball must come back in under a second, got " + ticks, ticks <= 20);
        assertTrue("but not every tick, got " + ticks, ticks >= 2);

        int tick = ball.indexOf("private void tickUnsafe(ScoreListener listener)");
        assertTrue(tick > 0);
        String body = between(ball, tick, 1400);
        assertTrue("the tick re-declares the ball visible", body.contains("revealToEveryone();"));
        assertTrue("a shot is re-declared twice as often as a dribble",
            body.contains("VISIBLE_REDECLARE_TICKS * 3"));
    }

    @Test
    public void aRebuildStormIsSaidOutLoud() throws Exception {
        String ball = ball();
        int note = ball.indexOf("private void noteRenderRebuild()");
        assertTrue("a rebuild must be counted", note > 0);
        String body = between(ball, note, 1200);
        assertTrue("a rebuild loop means an invisible ball", body.contains("invisible"));
        assertTrue("and it must reach the console", body.contains("logWarning("));
        assertTrue("once per ball", body.contains("renderStormLogged"));
        assertTrue("the rebuild is counted where it happens",
            ball.contains("noteRenderRebuild();") && ball.contains("RENDER_REBUILD_STORM"));
    }

    @Test
    public void theSpinQuaternionCannotShrinkTheBall() throws Exception {
        String ball = ball();
        int normalize = ball.indexOf("private void normalizeSpin()");
        assertTrue("the spin is kept unit length", normalize > 0);
        String body = between(ball, normalize, 700);
        assertTrue("a broken quaternion is reset, not used", body.contains("spinRotation.identity();"));
        assertTrue("a drifted one is normalised", body.contains("spinRotation.normalize();"));

        int spawn = ball.indexOf("private void spawnVisualBall()");
        assertTrue("a display is never planted from an unchecked quaternion",
            between(ball, spawn, 400).contains("normalizeSpin();"));
        int sync = ball.indexOf("private void syncEntities(float sx, float sy, float sz)");
        assertTrue("nor moved by one", between(ball, sync, 400).contains("normalizeSpin();"));
    }

    @Test
    public void aReplayHidesTheBallEveryFrameNotOnce() throws Exception {
        String cinematic = Files.readString(CINEMATIC);
        int step = cinematic.indexOf("private void stepReplayFrame(Player viewer, ViewerCamState st)");
        assertTrue(step > 0);
        String body = between(cinematic, step, 400);
        assertTrue("the live ball re-declares itself, so a replay must say no again every frame",
            body.contains("keepLiveBallHidden(viewer, st);"));
        assertTrue("the replay hides whatever display the ball has now",
            cinematic.contains("st.liveBallRef"));
        assertTrue("and gives it back at the end",
            cinematic.contains("toShow = current;"));
    }

    @Test
    public void aBrokenHitboxNeverCostsTheDisplay() throws Exception {
        String ball = ball();
        int repair = ball.indexOf("private void repairRenderIfBroken()");
        assertTrue("the repair path must exist", repair > 0);
        String body = between(ball, repair, 1400);
        assertTrue("the display and the hitbox are diagnosed separately",
            body.contains("String displayFault = displayFault();")
                && body.contains("String hitboxFault = hitboxFault();"));
        assertTrue("a missing hitbox spawns a hitbox", body.contains("spawnHitbox();"));
        int hitboxBranch = body.indexOf("if (hitboxFault != null) {");
        String after = body.substring(hitboxBranch, Math.min(body.length(), hitboxBranch + 500));
        assertFalse("and never rebuilds the display on its way",
            after.contains("respawnVisualBall();") || after.contains("spawnEntities();"));
    }

    @Test
    public void noRepairMayStrobeTheBall() throws Exception {
        String ball = ball();
        Matcher interval = Pattern.compile("REPAIR_MIN_INTERVAL_TICKS = (\\d+);").matcher(ball);
        assertTrue("the rate limit must be a named constant", interval.find());
        int ticks = Integer.parseInt(interval.group(1));
        assertTrue("a display has to live long enough to be drawn, got " + ticks, ticks >= 2);
        assertTrue("but a repair must not be pushed back half a second, got " + ticks, ticks <= 10);

        int repair = ball.indexOf("private void repairRenderIfBroken()");
        String body = between(ball, repair, 1400);
        assertTrue("the display rebuild is gated by that clock",
            body.contains("ticksSinceDisplaySpawn >= REPAIR_MIN_INTERVAL_TICKS"));
        assertTrue("and the ones held back are counted", body.contains("displayRepairsSuppressed++"));
        assertTrue("a display rebuilt every tick is never on screen - say it in the code",
            ball.contains("strobe"));
    }

    @Test
    public void aBallInTheAirRefreshesItsDisplayOnAClock() throws Exception {
        String ball = ball();
        Matcher refresh = Pattern.compile("FLIGHT_REFRESH_TICKS = (\\d+);").matcher(ball);
        assertTrue("the flight refresh must be a named constant", refresh.find());
        int ticks = Integer.parseInt(refresh.group(1));
        assertTrue("a lost ball must come back in well under a second, got " + ticks, ticks <= 10);
        assertTrue("without churning an entity per tick, got " + ticks, ticks >= 3);

        int tick = ball.indexOf("private void tickUnsafe(ScoreListener listener)");
        String body = between(ball, tick, 900);
        assertTrue("a ball that is not dribbling refreshes its own display",
            body.contains("mode != BallMode.DRIBBLING && ticksSinceDisplaySpawn >= FLIGHT_REFRESH_TICKS"));
        assertTrue("with a fresh spawn, not a re-show", body.contains("respawnVisualBall();"));
    }

    @Test
    public void aRepairSaysWhatItRepaired() throws Exception {
        String ball = ball();
        int note = ball.indexOf("private void noteRepair(");
        assertTrue("a repair must name its fault", note > 0);
        String body = between(ball, note, 900);
        assertTrue("one line per distinct fault", body.contains("repairReasonsLogged.add(key)"));
        assertTrue("with the position and the mode", body.contains("fmtLoc(position)") && body.contains("mode "));
        assertTrue("the display faults are named, not guessed at",
            ball.contains("display missing") && ball.contains("display invalid")
                && ball.contains("display in another world") && ball.contains("display drifted"));
        assertTrue("and so are the hitbox ones",
            ball.contains("hitbox missing") && ball.contains("hitbox invalid"));
    }

    @Test
    public void theHitboxIsNeverSpawnedInsideTheFloor() throws Exception {
        String ball = ball();
        int spawn = ball.indexOf("private void spawnHitbox()");
        assertTrue(spawn > 0);
        String body = between(ball, spawn, 1200);
        assertTrue("a ball resting on the floor asked for a hitbox centred inside the floor block",
            body.contains("clearOfFloor") && body.contains("hitboxLoc.setY(clearOfFloor)"));
    }

    @Test
    public void theBallIsNotAnOrphanTheCourtSweepCanEat() throws Exception {
        String ball = ball();
        assertTrue("the visual display carries the live-ball tag",
            ball.contains("disp.addScoreboardTag(LIVE_BALL_TAG);"));
        assertTrue("and so does the interaction hitbox",
            ball.contains("inter.addScoreboardTag(LIVE_BALL_TAG);"));

        String arena = Files.readString(ARENA);
        int purge = arena.indexOf("private void purgeOrphanedCourtEntities(World w)");
        assertTrue("the arena sweep must exist", purge > 0);
        String sweep = between(arena, purge, 2200);
        int skip = sweep.indexOf("BasketballEntity.LIVE_BALL_TAG");
        int kill = sweep.indexOf("ent.remove();");
        assertTrue("the sweep must know the live ball", skip > 0);
        assertTrue("and skip it before removing anything", skip < kill);
    }

    @Test
    public void aBrokenTickNeverLeavesTheBallDead() throws Exception {
        String ball = ball();
        int tick = ball.indexOf("public void tick(ScoreListener listener)");
        assertTrue(tick > 0);
        String body = between(ball, tick, 900);
        assertTrue("the tick body must be guarded", body.contains("try {") && body.contains("tickUnsafe(listener)"));
        assertTrue("a failed tick resets the ball instead of freezing it",
            body.contains("recoverFromInvalidState();"));
        assertTrue("and rebuilds what the players see", body.contains("spawnEntities();"));
    }

    @Test
    public void aBrokenSkinStillGivesAPlayableBall() throws Exception {
        String ball = ball();
        int spawn = ball.indexOf("private void spawnVisualBall()");
        assertTrue(spawn > 0);
        String body = between(ball, spawn, 2400);
        assertTrue("building the display can fail without losing the ball", body.contains("catch (RuntimeException e)"));
        assertTrue("the classic ball is the fallback", ball.contains("BallSkin.CLASSIC.createBallItem(true)"));
        assertTrue("and the operator is told once", body.contains("warnRenderOnce("));
    }

    @Test
    public void theBallIsBornEvenWhenTheCentreIsUnusable() throws Exception {
        String session = Files.readString(
            Path.of("src/main/java/fr/bosketball/game/MatchSession.java"));
        int spawn = session.indexOf("private void spawnOrResetBallEntity()");
        assertTrue("the ball must be created by one method", spawn > 0);
        String body = between(session, spawn, 2000);
        assertTrue("an unusable centre spot falls back to the geometry centre",
            body.contains("arena.geometry().centerX()") && body.contains("Double.isFinite(spawn.getX())"));
        assertTrue("and a failed ball never leaves the session without one",
            body.contains("this.ball = new BasketballEntity("));

        String ball = ball();
        int reveal = ball.indexOf("public void revealToEveryone()");
        assertTrue(reveal > 0);
        String revealBody = between(ball, reveal, 900);
        assertTrue("one refusing viewer must not stop the others",
            revealBody.contains("catch (RuntimeException e)"));
    }

    @Test
    public void aDesyncedDisplayIsRebuilt() throws Exception {
        String ball = ball();
        assertTrue("a display left far from the simulated ball must be named as a fault",
            ball.contains("renderDriftedBeyond(ballLoc)"));
        assertTrue("and repaired, not ignored",
            ball.contains("respawnVisualBall();"));
        assertTrue("the drift threshold is small enough to catch a real desync",
            ball.contains("RENDER_DRIFT_BLOCKS_SQ = 4.0"));
    }
}
