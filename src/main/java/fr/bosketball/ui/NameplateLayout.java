package fr.bosketball.ui;

import fr.bosketball.model.Rank;
import fr.bosketball.util.Glyphs;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Builds the rank nameplate of a player, layout included, without touching the server.
 *
 * <p>Round 37 — how the plate is assembled.
 *
 * <p>The pack draws a nameplate as three bitmap glyphs that are laid out left to right: a 16 px
 * left cap, a 16 px tile that repeats, and a 16 px right cap. The nickname has to sit <em>on</em>
 * the bar, and the whole line has to stay centred above the head, which gives three constraints:
 *
 * <ol>
 *   <li>the number of tiles is the smallest one that can hold the content plus a padding on both
 *       sides, so the plate hugs the name instead of being a fixed blob;</li>
 *   <li>the content starts at {@code (barWidth - contentWidth) / 2}, which is a <b>negative space
 *       glyph</b>: the client draws the caps first, then the pen is moved back over the bar and
 *       the nickname is painted on top of it;</li>
 *   <li>the line must still advance by exactly {@code barWidth} at the end, otherwise a centred
 *       display would push the plate off the player's head. The tail shift puts the pen back on
 *       the right edge of the bar.</li>
 * </ol>
 *
 * <p>Everything here is pure: {@code NameplateLayoutTest} runs it headlessly for every rank,
 * every name length and every suffix, and checks those three invariants.
 */
public final class NameplateLayout {

    /** Breathing room on each side of the nickname inside the bar. */
    public static final int PADDING_PX = 9;
    /** Width of the separator between the nickname and the stat inside the plate. */
    private static final String SEPARATOR = "  \u2022  ";
    /** Highest number of tiles we ever emit (a 16-character nickname plus a long stat). */
    public static final int MAX_TILES = 24;

    private NameplateLayout() {
    }

    /**
     * One rendered plate.
     *
     * @param plate      the component to hand to the display entity (bar + nickname + stat)
     * @param crest      the crest glyph drawn above the plate
     * @param tiles      how many middle tiles the bar uses
     * @param widthPx    total advance of the line, i.e. the width of the bar
     * @param contentPx  width of the nickname + stat block drawn inside the bar
     * @param startPx    negative shift applied before the content, i.e. where it starts in the bar
     * @param tailPx     positive shift applied after the content, back to the right edge of the bar
     * @param signature  cheap change detector for the manager
     */
    public record Plate(
        Component plate,
        Component crest,
        int tiles,
        int widthPx,
        int contentPx,
        int startPx,
        int tailPx,
        String signature
    ) {
    }

    /**
     * Builds the plate of one player.
     *
     * @param rank   rank of the player, decides the skin
     * @param name   nickname shown inside the plate (already sanitised by the caller)
     * @param suffix text shown after the nickname, e.g. {@code "1234 ELO"} or {@code "12 PTS"};
     *               an empty/null suffix simply leaves the slot out
     */
    public static Plate build(Rank rank, String name, String suffix) {
        NameplateSkin skin = NameplateSkin.of(rank);
        String safeName = sanitize(name);
        String safeSuffix = sanitize(suffix);

        Rank effectiveRank = rank == null ? Rank.ROOKIE : rank;
        int pillPx = Glyphs.textAdvancePx(String.valueOf(effectiveRank.pillGlyph()), false);
        int namePx = Glyphs.textAdvancePx(safeName, true);
        int suffixPx = safeSuffix.isEmpty() ? 0 : Glyphs.textAdvancePx(SEPARATOR, false) + Glyphs.textAdvancePx(safeSuffix, false);
        int contentPx = pillPx + Glyphs.textAdvancePx(" ", false) + namePx + suffixPx;

        int needed = contentPx + PADDING_PX * 2;
        int tiles = Math.max(1, Math.min(MAX_TILES, ceilDiv(needed, Glyphs.NAMEPLATE_TILE_PX) - 2));
        int widthPx = Glyphs.NAMEPLATE_TILE_PX * (2 + tiles);
        int startPx = Math.max(0, (widthPx - contentPx) / 2);
        int tailPx = widthPx - (startPx + contentPx);

        TextComponent.Builder bar = Component.text();
        bar.append(Component.text(String.valueOf(skin.left()), Glyphs.BITMAP_STYLE));
        if (tiles > 0) {
            bar.append(Component.text(String.valueOf(skin.middle()).repeat(tiles), Glyphs.BITMAP_STYLE));
        }
        bar.append(Component.text(String.valueOf(skin.right()), Glyphs.BITMAP_STYLE));

        // Move the pen back onto the bar (negative space glyphs) and paint the content over it.
        bar.append(Component.text(Glyphs.shift(startPx - widthPx), Glyphs.BITMAP_STYLE));
        bar.append(effectiveRank.pillComponent());
        bar.append(Component.text(" "));
        bar.append(Component.text(safeName, skin.nameColor()).decoration(TextDecoration.BOLD, true));
        if (!safeSuffix.isEmpty()) {
            bar.append(Component.text(SEPARATOR, skin.statColor()));
            bar.append(Component.text(safeSuffix, skin.statColor()));
        }
        // Put the pen back on the right edge so the centred display stays centred on the player.
        bar.append(Component.text(Glyphs.shift(tailPx), Glyphs.BITMAP_STYLE));

        Component crest = Component.text(String.valueOf(skin.crest()), Glyphs.BITMAP_STYLE);
        String signature = skin.index() + "|" + safeName + "|" + safeSuffix + "|" + tiles;
        return new Plate(bar.asComponent(), crest, tiles, widthPx, contentPx, startPx, tailPx, signature);
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

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
