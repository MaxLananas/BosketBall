package fr.bosketball.util;

import net.kyori.adventure.text.Component;

/**
 * The one width contract of the HUD.
 *
 * <p>Every line the plugin draws — an action-bar toast, a pill, a tab-list row — is measured in
 * font pixels before it is sent, and nothing wider than {@link #MAX_LINE_PX} ever leaves the
 * plugin. Before this class existed the width was a number that each renderer re-derived: the pill
 * stopped at 176 px, the tab card padded itself to 300 px, and everything in between was assumed
 * to fit because it usually did. A long nickname or a long sentence therefore "worked" until it
 * did not, and the failure was invisible in code review because no single place owned the number.
 *
 * <h2>Where the numbers come from</h2>
 * {@link #MAX_LINE_PX} is 300 font pixels, the width of the tab card ({@code TabListManager.CARD_PX}).
 * Vanilla wraps chat at 320 px and the action bar does not wrap at all, so 300 keeps a 20 px margin
 * on the narrowest GUI scale the server supports. It is deliberately the same number as the card:
 * one width for the whole interface rather than one per surface.
 *
 * <p>{@link #MAX_PILL_TEXT_PX} is the widest text the pack's pill artwork can hold, 170 px: the
 * pill tops out at {@link Glyphs#MAX_PILL_WIDTH} (176 px) and its two six-pixel caps eat twelve. The
 * twenty pixels the old guard reserved were never paddings — the artwork is drawn around the text —
 * and reserving them cost the longest HUD sentences their pill for no visual gain. Past 170 px the
 * pill is dropped and the line renders as plain text: a pill wider than its own artwork is worse
 * than no pill, and {@link HudCopy} is written so the case never comes up.
 *
 * <p>Measurement is {@link Glyphs#markupAdvancePx(String)}, i.e. the real client advance: shift
 * glyphs count with their signed value, {@code <bold>} adds its pixel, tags count for nothing.
 *
 * <p>{@link #fit(String)} is the safety net, not the design. Copy is written to fit (see
 * {@link #MAX_READABLE_CHARS}); {@code fit} exists so that a player name the server never saw can
 * push a line over the edge without it bleeding off the screen.
 */
public final class HudWidth {

    /** Hard ceiling for one line of HUD text, in font pixels. */
    public static final int MAX_LINE_PX = 300;

    /** Widest text the pack's pill can hold, in font pixels. */
    public static final int MAX_PILL_TEXT_PX = Glyphs.MAX_PILL_WIDTH - 6;

    /**
     * Longest authored HUD line, in characters.
     *
     * <p>An action-bar toast is read in a glance while the player is moving. Past roughly forty
     * characters the eye has to re-read the line, and a message that has to be re-read is worse than
     * no message: the player misses the next pass. This is a copy budget, not a measurement — the
     * measurement is {@link #MAX_LINE_PX}.
     */
    public static final int MAX_READABLE_CHARS = 44;

    /**
     * Ceiling for a line that carries two events.
     *
     * <p>A merge is the one time two sentences share the line, and it happens when two things
     * genuinely happened at once (a steal and the assist it turned into). It gets more room than a
     * single authored line — 56 characters, which still measures inside {@link #MAX_LINE_PX} — but
     * not unlimited room: past that the second event keeps its own turn.
     */
    public static final int MERGE_MAX_CHARS = 56;

    private static final String ELLIPSIS = "\u2026";

    private HudWidth() {
    }

    /**
     * Advance of an already-parsed component, in font pixels.
     *
     * <p>The meters of the aim HUD are built as components, not as markup, so measuring their
     * {@code toString()} would count the serialiser's own syntax. This walks the tree the way the
     * client does and adds one pixel for a bold run, which is the whole difference between a bar
     * that stays inside the pill and one that is one glyph too wide.
     */
    public static int measure(Component component) {
        return component == null ? 0 : Glyphs.advanceOfComponent(component);
    }

    /** True when a built component is inside the contract. */
    public static boolean fits(Component component) {
        return measure(component) <= MAX_LINE_PX;
    }

    /**
     * A built component, cut to the contract if it has to be.
     *
     * <p>Only the plain-text fallback truncates: a meter is a drawing, and half a drawing is a
     * rendering fault. When a meter is too wide the honest answer is its visible text, fitted, so
     * the player still reads the number.
     */
    public static Component fit(Component component) {
        if (fits(component)) {
            return component;
        }
        return MiniMessageHolder.parse(fit(MiniMessageHolder.plain(component)));
    }

    /** Keeps the Adventure import out of the contract's own signature list. */
    private static final class MiniMessageHolder {
        static net.kyori.adventure.text.Component parse(String mini) {
            return Msg.parse(mini);
        }

        static String plain(net.kyori.adventure.text.Component component) {
            StringBuilder out = new StringBuilder();
            collect(component, out);
            return "<" + Palette.TEXT + ">" + out + "</" + Palette.TEXT + ">";
        }

        private static void collect(net.kyori.adventure.text.Component component, StringBuilder out) {
            if (component instanceof net.kyori.adventure.text.TextComponent text) {
                out.append(text.content());
            }
            for (net.kyori.adventure.text.Component child : component.children()) {
                collect(child, out);
            }
        }
    }

    /** Advance of a MiniMessage line in font pixels, tags stripped and shifts kept signed. */
    public static int measure(String miniMessage) {
        return Glyphs.markupAdvancePx(miniMessage);
    }

    /** True when the line is inside the contract. */
    public static boolean fits(String miniMessage) {
        return fits(miniMessage, MAX_LINE_PX);
    }

    /** True when the line is inside {@code maxPx}. */
    public static boolean fits(String miniMessage, int maxPx) {
        return measure(miniMessage) <= Math.max(0, maxPx);
    }

    /** True when the line can live inside the pack's pill instead of plain text. */
    public static boolean fitsPill(String miniMessage) {
        return fits(miniMessage, MAX_PILL_TEXT_PX);
    }

    /** Visible characters of a HUD line, markup removed. */
    public static int visibleChars(String miniMessage) {
        return MiniSyntax.visibleText(miniMessage).length();
    }

    /** True when the line is no longer than a player can read at a glance. */
    public static boolean readable(String miniMessage) {
        return visibleChars(miniMessage) <= MAX_READABLE_CHARS;
    }

    /** The line, or the same line ellipsised until it is inside {@link #MAX_LINE_PX}. */
    public static String fit(String miniMessage) {
        return fit(miniMessage, MAX_LINE_PX);
    }

    /**
     * Returns {@code miniMessage} untouched when it fits, and otherwise its visible text cut on a
     * word boundary with a trailing ellipsis, in the primary text colour.
     *
     * <p>Cutting on a word boundary rather than on a glyph is the difference between "Stuffed by
     * Kobe…" and "Stuffed by Ko…": the second one reads as a bug. The styling of a truncated line is
     * dropped on purpose — half a gradient is worse than a plain sentence — and the colour that
     * replaces it is the palette's primary text, never an ad-hoc one.
     */
    public static String fit(String miniMessage, int maxPx) {
        if (miniMessage == null || miniMessage.isEmpty()) {
            return "";
        }
        if (fits(miniMessage, maxPx)) {
            return miniMessage;
        }
        String plain = MiniSyntax.visibleText(miniMessage);
        return Palette.of(Palette.TEXT, trimToWidth(plain, maxPx));
    }

    /** The widest prefix of {@code plain} that fits {@code maxPx}, ending with an ellipsis. */
    static String trimToWidth(String plain, int maxPx) {
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        int budget = maxPx - Glyphs.textAdvancePx(ELLIPSIS, false);
        if (budget <= 0) {
            return ELLIPSIS;
        }
        if (Glyphs.textAdvancePx(plain, false) <= budget) {
            return plain + ELLIPSIS;
        }
        StringBuilder kept = new StringBuilder(plain.length());
        int used = 0;
        int wordStart = 0;
        while (wordStart < plain.length()) {
            int wordEnd = wordStart;
            while (wordEnd < plain.length() && plain.charAt(wordEnd) != ' ') {
                wordEnd++;
            }
            String word = plain.substring(wordStart, wordEnd);
            int spacePx = kept.length() == 0 ? 0 : Glyphs.textAdvancePx(" ", false);
            int wordPx = Glyphs.textAdvancePx(word, false);
            if (used + spacePx + wordPx > budget) {
                if (kept.length() == 0) {
                    // A single word longer than the whole line: cut it on a code point, not mid-pair.
                    int codePoints = 0;
                    int cursor = 0;
                    while (cursor < word.length()) {
                        int next = cursor + Character.charCount(word.codePointAt(cursor));
                        if (Glyphs.textAdvancePx(word.substring(0, next), false) > budget) {
                            break;
                        }
                        cursor = next;
                        codePoints++;
                    }
                    return word.substring(0, cursor) + ELLIPSIS;
                }
                break;
            }
            if (spacePx > 0) {
                kept.append(' ');
                used += spacePx;
            }
            kept.append(word);
            used += wordPx;
            wordStart = wordEnd + 1;
        }
        return kept + ELLIPSIS;
    }
}
