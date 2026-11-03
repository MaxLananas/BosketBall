package fr.bosketball.model;

import net.kyori.adventure.text.format.TextColor;

/**
 * Hold-and-Release Shot Timing Evaluation.
 */
public enum ShotReleaseQuality {
    VERY_EARLY("EARLY", TextColor.color(0xFF5C5C), 0.14),
    SLIGHTLY_EARLY("SLIGHTLY EARLY", TextColor.color(0xFFD24C), 0.045),
    PERFECT_GREEN("✦ GREEN RELEASE ✦", TextColor.color(0x42FF84), 0.0),
    SLIGHTLY_LATE("SLIGHTLY LATE", TextColor.color(0xFFA94D), 0.05),
    VERY_LATE("LATE", TextColor.color(0xFF4242), 0.16);

    private final String displayLabel;
    private final TextColor color;
    private final double maxLateralScatterBlocks;

    ShotReleaseQuality(String displayLabel, TextColor color, double maxLateralScatterBlocks) {
        this.displayLabel = displayLabel;
        this.color = color;
        this.maxLateralScatterBlocks = maxLateralScatterBlocks;
    }

    public String displayLabel() {
        return displayLabel;
    }

    public TextColor color() {
        return color;
    }

    public double maxLateralScatterBlocks() {
        return maxLateralScatterBlocks;
    }

    /**
     * Low edge of the perfect release window in bow-pull units, and the shared source of truth for
     * both the HUD badge and the shot solver. The window is deliberately generous on the low side:
     * a bow reaches full draw in 20 ticks (1.0 s) and players naturally let go between 18 and 24
     * ticks. Being early was punished twice (it also flew short), which felt like a bug.
     */
    public static double greenWindowLow(boolean hotStreak, double distanceBlocks) {
        return Math.max(0.80, 0.845 - (hotStreak ? 0.035 : 0.0) + Math.max(0.0, distanceBlocks - 10.0) * 0.003);
    }

    /** High edge of the perfect release window in bow-pull units. A bow caps at 1.0, so this is reachable. */
    public static double greenWindowHigh(boolean hotStreak, double distanceBlocks) {
        return 1.025 + (hotStreak ? 0.02 : 0.0);
    }

    /**
     * Rim-plane error, in blocks, introduced by releasing outside the green window.
     *
     * <p>Measured with {@code tools/timing_study.py}: a 0.80 charge (0.045 early) scored exactly as
     * well as a green release, and a 1.08 (late) release scored <em>better</em> than a green at
     * every contested distance, because the old per-unit depth offset stayed inside the 0.39-block
     * scoring radius. A squat square-root curve keeps a barely-missed release forgiving (the "never
     * punish normal input" rule) while making a clearly mistimed one cost real accuracy.</p>
     */
    public static double timingPenaltyBlocks(double chargeProgress, double distanceBlocks) {
        double lo = greenWindowLow(false, distanceBlocks);
        double hi = greenWindowHigh(false, distanceBlocks);
        double delta;
        if (chargeProgress < lo) {
            delta = lo - chargeProgress;
        } else if (chargeProgress > hi) {
            delta = chargeProgress - hi;
        } else {
            return 0.0;
        }
        return Math.min(1.10, 0.70 * Math.sqrt(delta));
    }

    public static ShotReleaseQuality evaluate(double charge, double distanceBlocks, boolean hotStreak) {
        double minGreen = greenWindowLow(hotStreak, distanceBlocks);
        double maxGreen = greenWindowHigh(hotStreak, distanceBlocks);

        if (charge >= minGreen && charge <= maxGreen) {
            return PERFECT_GREEN;
        }
        // Graded on how far outside the window the release actually was, so "slightly early" now
        // means a shot that only just missed the window (and can still drop) instead of a 15%
        // short brick. Threshold is the physical tolerance of the rim: roughly 0.06 blocks.
        double delta = charge < minGreen ? (minGreen - charge) : (charge - maxGreen);
        boolean borderline = delta <= 0.03;
        if (charge < minGreen) {
            return borderline ? SLIGHTLY_EARLY : VERY_EARLY;
        }
        return borderline ? SLIGHTLY_LATE : VERY_LATE;
    }
}
