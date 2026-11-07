package fr.bosketball.model;

import fr.bosketball.util.Glyphs;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;

public enum TeamSide {
    IGNIS("IGNIS", "ɪɢɴɪꜱ", "🔥", Glyphs.TEAM_IGNIS, TextColor.color(0xFF6B4A), Color.fromRGB(224, 52, 46)),
    FROST("FROST", "ꜰʀᴏꜱᴛ", "❄", Glyphs.TEAM_FROST, TextColor.color(0x3DB8FF), Color.fromRGB(36, 128, 224)),
    SPECTATOR("SPEC", "ꜱᴘᴇᴄᴛᴀᴛᴏʀ", "👁", Glyphs.RANK_ROOKIE, TextColor.color(0x9AA5B8), Color.fromRGB(120, 130, 145));

    private final String shortCode;
    private final String smallCapsName;
    private final String symbol;
    private final char pillGlyph;
    private final TextColor textColor;
    private final Color bukkitColor;

    TeamSide(String shortCode, String smallCapsName, String symbol, char pillGlyph, TextColor textColor, Color bukkitColor) {
        this.shortCode = shortCode;
        this.smallCapsName = smallCapsName;
        this.symbol = symbol;
        this.pillGlyph = pillGlyph;
        this.textColor = textColor;
        this.bukkitColor = bukkitColor;
    }

    public String shortCode() {
        return shortCode;
    }

    public String smallCapsName() {
        return smallCapsName;
    }

    public String colorTag() {
        return String.format(java.util.Locale.ROOT, "<#%06X>", textColor.value() & 0xFFFFFF);
    }

    public String chatLabel() {
        String tag = colorTag();
        return tag + shortCode + fr.bosketball.util.Msg.closeTag(tag);
    }

    public String plainName() {
        String raw = name();
        if (raw.isEmpty()) {
            return raw;
        }
        return raw.charAt(0) + raw.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    /** Never {@code null}: an unknown or missing name is read as a spectator. */
    public static TeamSide byName(String raw) {
        if (raw == null || raw.isBlank()) {
            return SPECTATOR;
        }
        String clean = raw.trim();
        for (TeamSide side : values()) {
            if (side.name().equalsIgnoreCase(clean) || side.shortCode.equalsIgnoreCase(clean)) {
                return side;
            }
        }
        return SPECTATOR;
    }

    public String symbol() {
        return symbol;
    }

    public char pillGlyph() {
        return pillGlyph;
    }

    public TextColor textColor() {
        return textColor;
    }

    public Color bukkitColor() {
        return bukkitColor;
    }

    public Component pillComponent() {
        return Glyphs.bitmap(pillGlyph);
    }

    public TeamSide opposite() {
        return switch (this) {
            case IGNIS -> FROST;
            case FROST -> IGNIS;
            case SPECTATOR -> SPECTATOR;
        };
    }
}
