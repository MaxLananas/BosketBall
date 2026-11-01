package fr.bosketball.model;

import net.kyori.adventure.text.format.TextColor;

/**
 * NBA 2K-style Hold-and-Release Shot Timing Evaluation!
 * Sweet spot (Green Window) sits at charge [0.85 .. 0.99] (slightly wider for close jumpers, tighter from deep 3PT!).
 */
public enum ShotReleaseQuality {
    VERY_EARLY("ᴛʀᴏᴘ ᴛᴏᴛ", TextColor.color(0xFF5C5C), 0.14),
    SLIGHTLY_EARLY("ʟᴇɢᴇʀᴇᴍᴇɴᴛ ᴛᴏᴛ", TextColor.color(0xFFD24C), 0.045),
    PERFECT_GREEN("✦ ɢʀᴇᴇɴ ʀᴇʟᴇᴀꜱᴇ 100% ✦", TextColor.color(0x42FF84), 0.0),
    SLIGHTLY_LATE("ʟᴇɢᴇʀᴇᴍᴇɴᴛ ᴛᴀʀᴅ", TextColor.color(0xFFA94D), 0.05),
    VERY_LATE("ᴛʀᴏᴘ ᴛᴀʀᴅ", TextColor.color(0xFF4242), 0.16);

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
     * Evaluates a shot release given charge progress [0.0 .. 1.25] and distance to hoop in blocks.
     */
    public static ShotReleaseQuality evaluate(double charge, double distanceBlocks, boolean hotStreak) {
        // Base center of Green Window is 0.92; hot streak widens the green window!
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
