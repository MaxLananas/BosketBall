package fr.bosketball.model;

import fr.bosketball.util.Glyphs;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * Competitive ELO & Staff Ranks with matching custom 26.2 pixel-art pill badges (\uE210..\uE218).
 */
public enum Rank {
    ROOKIE(0, false, Glyphs.RANK_ROOKIE, "ʀᴏᴏᴋɪᴇ", 0x9CA7BD, 0xC6D0E3),
    HOOPER(1050, false, Glyphs.RANK_HOOPER, "ʜᴏᴏᴘᴇʀ", 0x2ECC71, 0x7DFFAF),
    PRO(1200, false, Glyphs.RANK_PRO, "ᴘʀᴏ", 0x3498DB, 0x7CE0FF),
    ALL_STAR(1400, false, Glyphs.RANK_ALLSTAR, "ᴀʟʟ-ꜱᴛᴀʀ", 0xF5B928, 0xFFF078),
    SUPERSTAR(1650, false, Glyphs.RANK_SUPERSTAR, "ꜱᴜᴘᴇʀꜱᴛᴀʀ", 0xA855F7, 0xE29CFF),
    LEGEND(1900, false, Glyphs.RANK_LEGEND, "ʟᴇɢᴇɴᴅ", 0xFF3E55, 0xFF96A5),
    MVP(2200, false, Glyphs.RANK_MVP, "ᴍᴠᴘ", 0xFF9B1E, 0xFFE869),
    REFEREE(0, true, Glyphs.RANK_REFEREE, "ᴀʀʙɪᴛʀᴇ", 0xDCE4F2, 0xFFFFFF),
    ADMIN(0, true, Glyphs.RANK_ADMIN, "ᴀᴅᴍɪɴ", 0xFF2828, 0xFFD43B);

    private final int minElo;
    private final boolean staffOnly;
    private final char pillGlyph;
    private final String smallCapsName;
    private final TextColor primaryColor;
    private final TextColor accentColor;

    Rank(int minElo, boolean staffOnly, char pillGlyph, String smallCapsName, int primaryRgb, int accentRgb) {
        this.minElo = minElo;
        this.staffOnly = staffOnly;
        this.pillGlyph = pillGlyph;
        this.smallCapsName = smallCapsName;
        this.primaryColor = TextColor.color(primaryRgb);
        this.accentColor = TextColor.color(accentRgb);
    }

    public int minElo() {
        return minElo;
    }

    public boolean staffOnly() {
        return staffOnly;
    }

    public char pillGlyph() {
        return pillGlyph;
    }

    public String smallCapsName() {
        return smallCapsName;
    }

    public TextColor primaryColor() {
        return primaryColor;
    }

    public TextColor accentColor() {
        return accentColor;
    }

    public Component pillComponent() {
        return Glyphs.bitmap(pillGlyph);
    }

    public static Rank fromElo(int elo) {
        Rank best = ROOKIE;
        for (Rank r : values()) {
            if (!r.staffOnly && elo >= r.minElo) {
                best = r;
            }
        }
        return best;
    }

    public static Rank byNameOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String norm = raw.trim().toUpperCase().replace('-', '_');
        for (Rank r : values()) {
            if (r.name().equals(norm)) {
                return r;
            }
        }
        return null;
    }
}
