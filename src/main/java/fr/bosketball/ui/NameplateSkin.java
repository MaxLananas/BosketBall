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
 * <p>This enum is the only place that knows which glyph group belongs to which rank, and which
 * colour the nickname has to be drawn in to stay readable on top of the plate. The plate fills are
 * measured from the generated PNGs (see the tool's output): light plates get a near black
 * nickname, the saturated Legend red and the black MVP core get a white one.
 */
public enum NameplateSkin {
    ROOKIE(0, 0x141A26, 0xE8EDF7),
    HOOPER(1, 0x08210F, 0xE8FFF0),
    PRO(2, 0x071C2E, 0xE6F6FF),
    ALL_STAR(3, 0x2A1B03, 0xFFF6DC),
    SUPERSTAR(4, 0x1B0733, 0xF6EBFF),
    LEGEND(5, 0xFFF1F2, 0xFFE1E4),
    MVP(6, 0xFFF7E4, 0xFFEFC7);

    private final int index;
    private final TextColor nameColor;
    private final TextColor statColor;

    NameplateSkin(int index, int nameRgb, int statRgb) {
        this.index = index;
        this.nameColor = TextColor.color(nameRgb);
        this.statColor = TextColor.color(statRgb);
    }

    /** Slot inside the generated glyph block (0 = Rookie … 6 = MVP). */
    public int index() {
        return index;
    }

    /** Colour of the nickname drawn inside the plate. */
    public TextColor nameColor() {
        return nameColor;
    }

    /** Colour of the ELO / points suffix drawn inside the plate. */
    public TextColor statColor() {
        return statColor;
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

    public char crest() {
        return Glyphs.nameplateGlyph(index, Glyphs.NAMEPLATE_CREST);
    }

    /**
     * Plate of a rank. The two staff ranks are not part of the ELO ladder and have no plate of
     * their own: a referee uses the Pro plate, an admin the Legend one, so no staff member ever
     * shows a rank they do not have.
     */
    public static NameplateSkin of(Rank rank) {
        if (rank == null) {
            return ROOKIE;
        }
        return switch (rank) {
            case ROOKIE -> ROOKIE;
            case HOOPER -> HOOPER;
            case PRO -> PRO;
            case ALL_STAR -> ALL_STAR;
            case SUPERSTAR -> SUPERSTAR;
            case LEGEND -> LEGEND;
            case MVP -> MVP;
            case REFEREE -> PRO;
            case ADMIN -> LEGEND;
        };
    }
}
