package fr.bosketball.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TickProfiler {

    private static final class Stat {
        long totalNs;
        long maxNs;
    }

    private static final Map<String, Stat> STATS = new LinkedHashMap<>();
    private static boolean enabled = true;
    private static long tickStartNs;
    private static long tickTotalNs;
    private static long tickMaxNs;
    private static long tickLastNs;
    private static long ticks;
    private static final boolean BUDGET_DEBUG = Boolean.getBoolean("bosketball.debug.tick-budget");
    private static final int BUDGET_WINDOW = 1024;
    private static fr.bosketball.arena.TickBudgetRecorder budget =
        new fr.bosketball.arena.TickBudgetRecorder(BUDGET_WINDOW);
    private static Thread tickThread;
    private static boolean inTick;

    private TickProfiler() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            reset();
        }
    }

    public static long ticks() {
        return ticks;
    }

    public static void beginTick() {
        if (enabled) {
            tickStartNs = System.nanoTime();
            tickThread = Thread.currentThread();
            inTick = true;
        }
    }

    public static void endTick() {
        if (!enabled) {
            return;
        }
        tickLastNs = System.nanoTime() - tickStartNs;
        if (BUDGET_DEBUG && inTick && Thread.currentThread() == tickThread) {
            budget.endTick();
        }
        inTick = false;
        tickTotalNs += tickLastNs;
        ticks++;
        if (tickLastNs > tickMaxNs) {
            tickMaxNs = tickLastNs;
        }
    }

    public static long begin(String section) {
        return enabled ? System.nanoTime() : 0L;
    }

    public static void end(String section, long startedNs) {
        if (!enabled || startedNs == 0L) {
            return;
        }
        long delta = System.nanoTime() - startedNs;
        Stat stat = STATS.computeIfAbsent(section, key -> new Stat());
        stat.totalNs += delta;
        if (BUDGET_DEBUG && inTick && Thread.currentThread() == tickThread) {
            if (section.equals("match") || section.equals("schematic")) budget.record(section, delta);
            if (section.equals("sidebar") || section.equals("tablist")) budget.record("hud", delta);
        }
        if (delta > stat.maxNs) {
            stat.maxNs = delta;
        }
    }

    /** Nested costs are counted only in the measured main-thread scheduler tick. */
    public static long beginBudget() {
        return BUDGET_DEBUG && enabled && inTick && Thread.currentThread() == tickThread
            ? System.nanoTime() : 0L;
    }

    public static void endBudget(String subsystem, long startedNs) {
        if (startedNs != 0L && BUDGET_DEBUG && enabled && inTick && Thread.currentThread() == tickThread) {
            budget.record(subsystem, Math.max(0L, System.nanoTime() - startedNs));
        }
    }

    public static boolean shouldReportBudget() {
        return BUDGET_DEBUG && enabled && ticks > 0 && ticks % 1200 == 0;
    }

    public static String budgetReport() {
        return budget.report();
    }

    public static void reset() {
        budget = new fr.bosketball.arena.TickBudgetRecorder(BUDGET_WINDOW);
        inTick = false;
        STATS.clear();
        tickTotalNs = 0L;
        tickMaxNs = 0L;
        tickLastNs = 0L;
        ticks = 0L;
    }

    public static long lastTickNanos() {
        return tickLastNs;
    }

    public static List<Map.Entry<String, Double>> averageMillis() {
        List<Map.Entry<String, Double>> out = new ArrayList<>(STATS.size());
        if (ticks == 0L) {
            return out;
        }
        for (Map.Entry<String, Stat> entry : STATS.entrySet()) {
            out.add(Map.entry(entry.getKey(), entry.getValue().totalNs / 1_000_000.0 / ticks));
        }
        out.sort(Comparator.comparingDouble((Map.Entry<String, Double> e) -> e.getValue()).reversed());
        return out;
    }

    public static List<String> report(double tps) {
        List<String> lines = new ArrayList<>();
        if (ticks == 0L) {
            lines.add(" <#94A3B8>No tick sampled yet — wait a second and retry.</#94A3B8>");
            return lines;
        }
        double avgTick = tickTotalNs / 1_000_000.0 / ticks;
        lines.add(" <#94A3B8>▸ Ticks:</#94A3B8> <#F8FAFC>" + ticks + "</#F8FAFC>"
            + " <#64748B>\u00b7</#64748B> <#94A3B8>avg:</#94A3B8> <#F8FAFC>" + ms(avgTick) + " ms</#F8FAFC>"
            + " <#64748B>\u00b7</#64748B> <#94A3B8>worst:</#94A3B8> <#F8FAFC>" + ms(tickMaxNs / 1_000_000.0) + " ms</#F8FAFC>"
            + " <#64748B>\u00b7</#64748B> <#94A3B8>TPS:</#94A3B8> <#F8FAFC>" + String.format(Locale.ROOT, "%.2f", tps) + "</#F8FAFC>");

        List<Map.Entry<String, Double>> ranked = averageMillis();
        double budget = 50.0;
        for (int i = 0; i < ranked.size() && i < 14; i++) {
            Map.Entry<String, Double> entry = ranked.get(i);
            Stat stat = STATS.get(entry.getKey());
            double share = entry.getValue() / budget * 100.0;
            String color = share >= 20.0 ? "<#F87171>" : share >= 8.0 ? "<#FDE047>" : "<" + Palette.SUCCESS + ">";
            lines.add(" <#64748B>" + (i + 1) + ".</#64748B> <#F8FAFC>" + entry.getKey() + "</#F8FAFC>"
                + " <#64748B>\u00b7</#64748B> " + color + ms(entry.getValue()) + " ms" + Msg.closeTag(color)
                + " <#94A3B8>(max " + ms(stat.maxNs / 1_000_000.0) + ", " + String.format(Locale.ROOT, "%.1f", share) + "% of 50ms)</#94A3B8>");
        }
        return lines;
    }

    private static String ms(double value) {
        if (value >= 10.0) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        if (value >= 1.0) {
            return String.format(Locale.ROOT, "%.2f", value);
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
