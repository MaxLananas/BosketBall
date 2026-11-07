package fr.bosketball.ui;

import fr.bosketball.model.Rank;
import fr.bosketball.util.Glyphs;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Builds the nameplate of a player: a pixel-art bar with the nickname written on it.
 *
 * <p>Round 38 — what changed since Round 37, and why.
 *
 * <p>The first version put the rank pill, a separator, the ELO (or the match points) and a crest
 * glyph around the nickname. On a real head that was far too much: the line ended up wider than the
 * bar, the bar stopped covering the text, and the rank was already shown in the tab list and the
 * sidebar anyway. The plate now carries <b>the nickname and nothing else</b>.
 *
 * <h2>How the bar is assembled</h2>
 *
 * <p>A plate is three kinds of bitmap glyph laid out left to right: a left cap, a middle tile that
 * repeats, and a right cap. The caps are not full-width: the pack draws a tier-1 cap three pixels
 * wide at the outer edge of its 16 px cell (see {@link NameplateSkin#edgeLeft()}), so the part of the
 * line that can hold a name is
 *
 * <pre>{@code usable = 16 * (2 + tiles) - edgeLeft - edgeRight}</pre>
 *
 * <p>The name is then painted over the bar with <b>negative space glyphs</b>: the pen is moved back
 * from the right edge to {@code startPx}, the nickname is drawn, and the pen is pushed back onto the
 * right edge so the whole line advances by exactly {@code widthPx}. That last step matters: the
 * display entity is centred, and a line that advanced by anything but the bar width would drift off
 * the head.
 *
 * <h2>Round 42 — the holes between the tiles</h2>
 *
 * <p>Two neighbouring bitmap glyphs are drawn as two quads, and the client samples the font texture
 * with filtering and rounds every glyph onto its own position. As soon as the plate is scaled by
 * anything but a whole number — {@code nameplates.scale} is 1.15 by default — a fraction of a pixel
 * of transparency opens at every junction, and the plate reads as a bar cut into four blocks with
 * holes between them. That is exactly what the owner kept seeing.
 *
 * <p>Each tile is therefore pulled {@link #TILE_OVERLAP_PX} pixel to the left with a negative space
 * glyph, so consecutive tiles overlap instead of merely touching: a rounded half pixel lands on the
 * opaque art of the previous tile and the seam cannot open, whatever the scale, whatever the GUI
 * size and whatever the distance. The overlap is subtracted from the line's advance, so the plate
 * stays exactly as wide as the bar it draws.
 */
public final class NameplateLayout {

    /** Breathing room on each side of the nickname, measured inside the bar's art. */
    public static final int PADDING_PX = 12;
    /** Smallest bar we ever emit, in middle tiles (three tiles of art look like a real plate). */
    public static final int MIN_TILES = 2;
    /** Highest number of tiles we ever emit (a 16-character nickname plus its padding). */
    public static final int MAX_TILES = 18;
    /**
     * Pixels of overlap between two consecutive tiles.
     *
     * <p>One pixel is enough to swallow the sub-pixel seam the client opens between two glyph quads
     * at a fractional scale, and it is small enough that the bar keeps its bevel: the middle tiles
     * are a uniform fill, so overlapping two of them changes nothing visible.
     */
    public static final int TILE_OVERLAP_PX = 1;

    /**
     * One rendered plate.
     *
     * @param plate      the component to hand to the display entity (bar + nickname)
     * @param tiles      how many middle tiles the bar uses
     * @param widthPx    total advance of the line, i.e. the width of the bar
     * @param contentPx  width of the nickname as the client draws it
     * @param startPx    where the nickname starts inside the bar (measured from its left edge)
     * @param tailPx     how far the pen is pushed after the nickname, back to the right edge
     * @param signature  cheap change detector for the manager
     */
    public record Plate(
        Component plate,
        int tiles,
        int widthPx,
        int contentPx,
        int startPx,
        int tailPx,
        String signature
    ) {

        /** Width the plate's art actually covers, i.e. what the player sees. */
        public int visiblePx(NameplateSkin skin) {
            return widthPx - skin.edgeLeft() - skin.edgeRight();
        }
    }

    private NameplateLayout() {
    }

    /**
     * Builds the plate of one player.
     *
     * @param rank rank of the player, decides the plate and the ink
     * @param name nickname shown inside the plate (sanitised here)
     */
    public static Plate build(Rank rank, String name) {
        NameplateSkin skin = NameplateSkin.of(rank);
        String safeName = sanitize(name);
        if (safeName.isEmpty()) {
            safeName = "?";
        }
        int contentPx = Glyphs.textAdvancePx(safeName, true);

        // The nickname plus its padding has to fit between the two caps' art, not inside the raw
        // cell width: the caps hide part of the outer tiles. Round 42 measures the bar with the
        // tile overlap included, so the width the loop below reserves is the width that is drawn.
        int needed = contentPx + PADDING_PX * 2;
        int tiles = MIN_TILES;
        while (tiles < MAX_TILES && artWidthPx(skin, tiles) < needed) {
            tiles++;
        }
        int widthPx = barWidthPx(tiles);
        int usablePx = artWidthPx(skin, tiles);
        int startPx = skin.edgeLeft() + Math.max(0, (usablePx - contentPx) / 2);
        int tailPx = widthPx - (startPx + contentPx);

        String overlap = Glyphs.shift(-TILE_OVERLAP_PX);
        TextComponent.Builder bar = Component.text();
        bar.append(Component.text(String.valueOf(skin.left()), Glyphs.BITMAP_STYLE));
        for (int i = 0; i < tiles; i++) {
            bar.append(Component.text(overlap, Glyphs.BITMAP_STYLE));
            bar.append(Component.text(String.valueOf(skin.middle()), Glyphs.BITMAP_STYLE));
        }
        bar.append(Component.text(overlap, Glyphs.BITMAP_STYLE));
        bar.append(Component.text(String.valueOf(skin.right()), Glyphs.BITMAP_STYLE));

        // Move the pen back over the bar (negative space) and write the nickname on it.
        bar.append(Component.text(Glyphs.shift(startPx - widthPx), Glyphs.BITMAP_STYLE));
        bar.append(Component.text(safeName, skin.ink()).decoration(TextDecoration.BOLD, true));
        // Put the pen back on the right edge so the centred display stays on the player's head.
        bar.append(Component.text(Glyphs.shift(tailPx), Glyphs.BITMAP_STYLE));

        String signature = skin.index() + "|" + safeName + "|" + tiles + "|" + TILE_OVERLAP_PX;
        return new Plate(bar.asComponent(), tiles, widthPx, contentPx, startPx, tailPx, signature);
    }

    /** Advance of a bar made of {@code tiles} middle tiles, overlaps included. */
    public static int barWidthPx(int tiles) {
        int clamped = Math.max(MIN_TILES, Math.min(MAX_TILES, tiles));
        // Every junction (left -> first middle, middle -> middle, last middle -> right) is pulled
        // one pixel into the next tile, so the line is that much shorter than tiles + 2 caps.
        return Glyphs.NAMEPLATE_TILE_PX * (2 + clamped)
            - TILE_OVERLAP_PX * (1 + clamped);
    }

    /** Visible art of a bar: its advance minus the transparent margins hidden inside the caps. */
    public static int artWidthPx(NameplateSkin skin, int tiles) {
        return barWidthPx(tiles) - skin.edgeLeft() - skin.edgeRight();
    }

    /** Strips anything that could be parsed as markup and keeps the plate to one line. */
    public static String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '<' || c == '>' || c == '\n' || c == '\r' || c == '\u00a7') {
                continue;
            }
            out.append(c);
        }
        return out.toString().trim();
    }

    /** Lowercase rank key used by the generator tool and the tests. */
    public static String skinKey(Rank rank) {
        return NameplateSkin.of(rank).name().toLowerCase(Locale.ROOT);
    }
}
