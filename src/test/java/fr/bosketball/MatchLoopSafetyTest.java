package fr.bosketball;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * MatchSession has no unit seam (it needs a live Bukkit server), so its containment is checked on
 * the source: the display work a tick performs must be guarded, and the guard must still let a
 * dying JVM through.
 */
public class MatchLoopSafetyTest {

    private static final Path SESSION =
        Path.of("src/main/java/fr/bosketball/game/MatchSession.java");

    @Test
    public void aFailingDisplayDoesNotAbortTheTick() throws Exception {
        String source = Files.readString(SESSION);

        int tick = source.indexOf("public void tick()");
        assertTrue(tick > 0);
        String tickBody = source.substring(tick, Math.min(source.length(), tick + 1200));
        assertTrue("the display pipeline runs guarded", tickBody.contains("tickVisualsSafely()"));
        assertFalse("tickDisplays must not be called bare inside tick()",
            tickBody.contains("arena.tickDisplays("));
        assertFalse("player rigs must not be ticked bare inside tick()",
            tickBody.contains("tickPlayerVisualRigs();"));
    }

    @Test
    public void theVisualGuardContainsEverythingButAFatalError() throws Exception {
        String source = Files.readString(SESSION);
        int helper = source.indexOf("private void tickVisualSafely(String what, Runnable step)");
        assertTrue(helper > 0);
        String body = source.substring(helper, Math.min(source.length(), helper + 1200));
        assertTrue("it catches Throwable", body.contains("catch (Throwable failure)"));
        assertTrue("a dying JVM is rethrown", body.contains("VirtualMachineError"));
        assertTrue("ThreadDeath is rethrown", body.contains("ThreadDeath"));
        assertTrue("the failure is reported with the court", body.contains("arena.id()"));
        assertTrue("it says the match survives", body.contains("the match keeps running"));
    }
}
