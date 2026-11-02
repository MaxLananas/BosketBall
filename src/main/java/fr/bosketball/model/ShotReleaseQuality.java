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

    public static ShotReleaseQuality evaluate(double charge, double distanceBlocks, boolean hotStreak) {
        // Bow-draw full power green zone: 0.84 .. 1.02 (both 0.92 and 1.00 full bow pullback are PERFECT_GREEN!)
        double minGreen = Math.max(0.81, 0.855 - (hotStreak ? 0.035 : 0.0) + Math.max(0.0, distanceBlocks - 10.0) * 0.003);
        double maxGreen = 1.025 + (hotStreak ? 0.02 : 0.0);

        if (charge >= minGreen && charge <= maxGreen) {
            return PERFECT_GREEN;
        }
        if (charge < minGreen) {
            return (charge >= 0.56) ? SLIGHTLY_EARLY : VERY_EARLY;
        }
        return (charge <= 1.12) ? SLIGHTLY_LATE : VERY_LATE;
    }
}
