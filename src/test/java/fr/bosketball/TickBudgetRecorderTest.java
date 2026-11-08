package fr.bosketball;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import fr.bosketball.arena.TickBudgetRecorder;
import org.junit.Test;

public class TickBudgetRecorderTest {
    @Test public void callsAreSummedPerTickAndIdleTicksAreIncluded() {
        TickBudgetRecorder recorder = new TickBudgetRecorder(4);
        recorder.record("bots", 3_000_000L);
        recorder.record("bots", 7_000_000L);
        recorder.record("match", 60_000_000L);
        recorder.endTick();
        recorder.endTick();
        String report = recorder.report();
        assertTrue(report, report.contains("bots=p50 0.000 p99 10.000 max 10.000 >=50ms 0 n=2"));
        assertTrue(report, report.contains("match=p50 0.000 p99 60.000 max 60.000 >=50ms 1 n=2"));
        assertTrue(report, report.contains("hud=not sampled"));
    }

    @Test public void rejectsUnknownSectionsAndNegativeDurations() {
        TickBudgetRecorder recorder = new TickBudgetRecorder(2);
        try {
            recorder.record("unknown", 1);
            fail("unknown section");
        } catch (IllegalArgumentException expected) { }
        try {
            recorder.record("hud", -1);
            fail("negative duration");
        } catch (IllegalArgumentException expected) { }
    }
}
