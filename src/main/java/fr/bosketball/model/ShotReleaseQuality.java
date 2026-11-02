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
        double halfGreen = Math.max(0.045, 0.095 - Math.max(0.0, distanceBlocks - 5.0) * 0.0035);
        if (hotStreak) {
            halfGreen += 0.028;
        }
        double center = 0.92;
        double delta = charge - center;

        if (Math.abs(delta) <= halfGreen) {
            return PERFECT_GREEN;
        }
        if (delta < 0) {
            return (delta >= -0.24) ? SLIGHTLY_EARLY : VERY_EARLY;
        } else {
            return (delta <= 0.16) ? SLIGHTLY_LATE : VERY_LATE;
        }
    }
}
