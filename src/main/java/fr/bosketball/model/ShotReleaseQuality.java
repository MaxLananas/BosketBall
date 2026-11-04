package fr.bosketball.model;

import net.kyori.adventure.text.format.TextColor;

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

    public static double greenWindowLow(boolean hotStreak, double distanceBlocks) {
        return Math.max(0.80, 0.845 - (hotStreak ? 0.035 : 0.0) + Math.max(0.0, distanceBlocks - 10.0) * 0.003);
    }

    public static double greenWindowHigh(boolean hotStreak, double distanceBlocks) {
        return 1.025 + (hotStreak ? 0.02 : 0.0);
    }

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

        double delta = charge < minGreen ? (minGreen - charge) : (charge - maxGreen);
        boolean borderline = delta <= 0.03;
        if (charge < minGreen) {
            return borderline ? SLIGHTLY_EARLY : VERY_EARLY;
        }
        return borderline ? SLIGHTLY_LATE : VERY_LATE;
    }
}
