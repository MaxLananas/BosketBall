package fr.bosketball.arena;

import java.util.Arrays;

/** Fixed-size, server-thread-owned rolling latency samples, stored in nanoseconds. */
public final class TickBudgetSamples {
    private final long[] samples;
    private int next;
    private int size;

    public TickBudgetSamples(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        samples = new long[capacity];
    }

    public void add(long nanos) {
        if (nanos < 0) {
            throw new IllegalArgumentException("duration cannot be negative");
        }
        samples[next] = nanos;
        next = (next + 1) % samples.length;
        size = Math.min(size + 1, samples.length);
    }

    public int size() {
        return size;
    }

    /** Count retained samples at or above the specified budget in nanoseconds. */
    public int countAtLeast(long budgetNanos) {
        if (budgetNanos < 0) {
            throw new IllegalArgumentException("budget cannot be negative");
        }
        int count = 0;
        for (int i = 0; i < size; i++) {
            if (samples[i] >= budgetNanos) count++;
        }
        return count;
    }

    /** Nearest-rank percentile of the retained window, not of the whole server lifetime. */
    public long percentile(int percentile) {
        if (percentile < 1 || percentile > 100 || size == 0) {
            throw new IllegalArgumentException("percentile needs samples and must be 1..100");
        }
        long[] sorted = Arrays.copyOf(samples, size);
        Arrays.sort(sorted);
        int rank = (int) (((long) percentile * size + 99) / 100);
        return sorted[rank - 1];
    }
}
