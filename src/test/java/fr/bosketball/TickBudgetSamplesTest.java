package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import fr.bosketball.arena.TickBudgetSamples;
import org.junit.Test;

public class TickBudgetSamplesTest {
    @Test public void percentilesUseNearestRank() {
        TickBudgetSamples samples = new TickBudgetSamples(8);
        for (long n : new long[]{40, 10, 30, 20}) samples.add(n);
        assertEquals(4, samples.size());
        assertEquals(20, samples.percentile(50));
        assertEquals(40, samples.percentile(99));
        assertEquals(2, samples.countAtLeast(30));
    }

    @Test public void rollingWindowEvictsOldestSample() {
        TickBudgetSamples samples = new TickBudgetSamples(3);
        samples.add(100);
        samples.add(2);
        samples.add(3);
        samples.add(1);
        assertEquals(3, samples.size());
        assertEquals(2, samples.percentile(50));
        assertEquals(3, samples.percentile(99));
        assertEquals(0, samples.countAtLeast(50));
    }

    @Test public void rejectsBadInputs() {
        try {
            new TickBudgetSamples(0);
            fail("capacity");
        } catch (IllegalArgumentException expected) { }
        TickBudgetSamples samples = new TickBudgetSamples(1);
        try {
            samples.percentile(50);
            fail("empty");
        } catch (IllegalArgumentException expected) { }
        try {
            samples.add(-1);
            fail("duration");
        } catch (IllegalArgumentException expected) { }
        samples.add(1);
        try {
            samples.percentile(0);
            fail("percentile");
        } catch (IllegalArgumentException expected) { }
    }
}
