package fr.bosketball.arena;

import java.util.Locale;

/** Aggregates all calls for each subsystem once per scheduler tick, then records a rolling window. */
public final class TickBudgetRecorder {
    private static final String[] NAMES = {"match", "schematic", "hud", "bots"};
    private static final long BUDGET_NANOS = 50_000_000L;
    private final TickBudgetSamples[] samples = new TickBudgetSamples[NAMES.length];
    private final long[] current = new long[NAMES.length];
    private final boolean[] observed = new boolean[NAMES.length];

    public TickBudgetRecorder(int window) {
        for (int i = 0; i < samples.length; i++) samples[i] = new TickBudgetSamples(window);
    }

    public void record(String section, long nanos) {
        if (nanos < 0) throw new IllegalArgumentException("negative duration");
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(section)) {
                current[i] = Long.MAX_VALUE - current[i] < nanos ? Long.MAX_VALUE : current[i] + nanos;
                observed[i] = true;
                return;
            }
        }
        throw new IllegalArgumentException("unknown tick subsystem: " + section);
    }

    public void endTick() {
        for (int i = 0; i < samples.length; i++) {
            if (observed[i]) samples[i].add(current[i]);
            current[i] = 0;
        }
    }

    /** Inclusive timings overlap: HUD and bots are already included in match. */
    public String report() {
        StringBuilder out = new StringBuilder("[Tick budget] rolling per-tick subsystem costs (ms): ");
        for (int i = 0; i < NAMES.length; i++) {
            if (i > 0) out.append(", ");
            out.append(NAMES[i]).append('=');
            TickBudgetSamples window = samples[i];
            if (window.size() == 0) {
                out.append("not sampled");
            } else {
                out.append(String.format(Locale.ROOT, "p50 %.3f p99 %.3f max %.3f >=50ms %d n=%d",
                    window.percentile(50) / 1_000_000.0, window.percentile(99) / 1_000_000.0,
                    window.percentile(100) / 1_000_000.0, window.countAtLeast(BUDGET_NANOS), window.size()));
            }
        }
        return out.toString();
    }
}
