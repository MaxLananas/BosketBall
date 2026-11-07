package fr.bosketball.ui;

import fr.bosketball.model.Rank;
import fr.bosketball.util.Glyphs;
import net.kyori.adventure.text.format.TextColor;

/**
 * Colourimetry of the rank nameplates.
 *
 * <p>Round 37 — the owner shipped a CustomNameplates content pack with six materials (wood, bronze,
 * silver, gold, diamond, netherite). BosketBall has seven ranks, so
 * {@code tools/generate_nameplates.py} recolours one plate per rank onto the rank's own palette,
 * and draws the seventh (MVP) from scratch: gold rim, obsidian core, lit gems.
 *
 * <p>Round 38 — the plate now carries the nickname and nothing else, so the only thing this enum
 * has to get right is legibility. The five lowest ranks are drawn on a <em>light</em> fill (the
 * pack's polished metal), so their nickname is a near-black ink; Legend (deep red) and MVP (near
 * black core) are dark plates and take a cream ink. The values below are measured on the generated
 * PNGs, not guessed: {@code nameplateInkContrastsWithItsPlate} asserts each ink against the median
 * colour of the tile it is drawn on.
 *
 * <p>{@link #edgeLeft()} and {@link #edgeRight()} are the transparent margins inside the caps: the
 * pack draws a tier-1 cap three pixels wide at the right edge of its 16 px cell, so 13 px of that
 * cell show nothing. The layout has to subtract them, otherwise the bar looks shorter than the
 * nickname it is supposed to hold.
 */
public enum NameplateSkin {
    //                  index ink        edgeLeft edgeRight
    ROOKIE(0, 0x141A26, 13, 13),
    HOOPER(1, 0x0A2E19, 13, 13),
    PRO(2, 0x071C2E, 8, 8),
    ALL_STAR(3, 0x2A1B03, 8, 8),
    SUPERSTAR(4, 0x1B0733, 4, 4),
    LEGEND(5, 0xFFF1F2, 4, 4),
    MVP(6, 0xFFF7E4, 4, 4);

    private final int index;
    private final TextColor ink;
    private final int edgeLeft;
    private final int edgeRight;

    NameplateSkin(int index, int inkRgb, int edgeLeft, int edgeRight) {
        this.index = index;
        this.ink = TextColor.color(inkRgb);
        this.edgeLeft = edgeLeft;
        this.edgeRight = edgeRight;
    }

    /** Slot inside the generated glyph block (0 = Rookie … 6 = MVP). */
    public int index() {
        return index;
    }

    /** Colour the nickname is drawn in: readable on top of this plate's fill. */
    public TextColor ink() {
        return ink;
    }

    /** Transparent columns between the left edge of the plate and the start of its art. */
    public int edgeLeft() {
        return edgeLeft;
    }

    /** Transparent columns between the end of the plate's art and its right edge. */
    public int edgeRight() {
        return edgeRight;
    }

    public char left() {
        return Glyphs.nameplateGlyph(index, Glyphs.NAMEPLATE_LEFT);
    }

    public char middle() {
        return Glyphs.nameplateGlyph(index, Glyphs.NAMEPLATE_MIDDLE);
    }

    public char right() {
        return Glyphs.nameplateGlyph(index, Glyphs.NAMEPLATE_RIGHT);
    }

    /** The plate of a rank; staff ranks wear the plate of a rank they actually have. */
    public static NameplateSkin of(Rank rank) {
        if (rank == null) {
            return ROOKIE;
        }
        return switch (rank) {
            case ROOKIE -> ROOKIE;
            case HOOPER -> HOOPER;
            case PRO -> PRO;
            case REFEREE -> PRO;
            case ALL_STAR -> ALL_STAR;
            case SUPERSTAR -> SUPERSTAR;
            case LEGEND -> LEGEND;
            case ADMIN -> LEGEND;
            case MVP -> MVP;
        };
    }
}
