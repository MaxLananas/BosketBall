package fr.bosketball.ui;

import fr.bosketball.util.Glyphs;
import fr.bosketball.util.HudWidth;
import fr.bosketball.util.MiniSyntax;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A sidebar that cannot be drawn wrong.
 *
 * <p>The sidebar is fifteen rows of a scoreboard, and three separate bugs lived in that budget:
 *
 * <ul>
 *   <li><b>An empty middle row.</b> {@code applyLines} wrote whatever list it was handed. When a
 *       value came back blank — a profile with no season, a party with no name, a line provider that
 *       returned {@code ""} — the row existed but said nothing, and the client drew a gap in the
 *       middle of the panel. A gap reads as a rendering fault, not as "this player has no guild".</li>
 *   <li><b>A bare delete.</b> When a line disappeared the team prefix stayed and only its score was
 *       reset, so a row could linger one tick with stale text; the next render then re-created it
 *       with a different position and the panel visibly jittered.</li>
 *   <li><b>A wide row.</b> Player names, guild names and season labels are not width-checked by the
 *       client, which just draws past the panel edge.</li>
 * </ul>
 *
 * <p>So the sidebar no longer receives {@code List<String>}. It receives a {@code SidebarLines}:
 * the same list after it has been validated. Blank rows are filled with a visible placeholder in the
 * dim colour (never deleted — the row is a slot, and moving every row under it is the jitter),
 * oversized rows are ellipsised to the panel width, the gold flourish stays first and last, and the
 * client's fifteen-row ceiling is applied here instead of at the packet boundary.
 *
 * <p>Pure: no Bukkit, so the whole invariant is unit-tested without a server.
 */
public final class SidebarLines {

    /** Rows the client will draw. Anything past this is dropped by the client anyway. */
    public static final int MAX_ROWS = 15;

    /**
     * Width the sidebar panel gives a line, in font pixels.
     *
     * <p>The same 176 px ceiling as the action-bar pill ({@link Glyphs#MAX_PILL_WIDTH}), on purpose:
     * two panels that hold the same kind of thing — a label and a value — should not disagree about
     * how long a line may get. A line wider than this is ellipsised, never left to run past the
     * panel's own padding, where the client draws it flat against the world.
     */
    public static final int CONTENT_PX = Glyphs.MAX_PILL_WIDTH;

    /** What a row says when the value it wanted is not there. Dim, never blank. */
    public static final String PLACEHOLDER = "<" + fr.bosketball.util.Palette.DIM + ">\u2014</"
        + fr.bosketball.util.Palette.DIM + ">";

    private final List<String> rows;

    private SidebarLines(List<String> rows) {
        this.rows = Collections.unmodifiableList(rows);
    }

    /**
     * Validates a rendered list.
     *
     * <p>Order of operations matters and is the whole point: the flourish is honoured first (the
     * owner asked for it by name and it is allowed to be the only thing on its row), then blanks are
     * replaced, then widths are fitted, then the ceiling is applied.
     */
    public static SidebarLines of(List<String> rawRows) {
        if (rawRows == null || rawRows.isEmpty()) {
            return new SidebarLines(List.of(flourish(), PLACEHOLDER, flourish()));
        }
        List<String> out = new ArrayList<>(Math.min(rawRows.size(), MAX_ROWS));
        int last = Math.min(rawRows.size(), MAX_ROWS);
        for (int i = 0; i < last; i++) {
            out.add(normalise(rawRows.get(i), isFlourishRow(rawRows, i, last)));
        }
        return new SidebarLines(out);
    }

    private static boolean isFlourishRow(List<String> rows, int index, int last) {
        if (index != 0 && index != last - 1) {
            return false;
        }
        String row = rows.get(index);
        return row != null && row.indexOf(Glyphs.SB_FLOURISH) >= 0;
    }

    private static String normalise(String row, boolean flourishRow) {
        if (row == null) {
            return flourishRow ? flourish() : PLACEHOLDER;
        }
        // A row made only of markup ("<#64748B></#64748B>") is blank on screen: treat it as blank.
        if (MiniSyntax.visibleText(row).isBlank()) {
            return flourishRow ? flourish() : PLACEHOLDER;
        }
        return HudWidth.fits(row, CONTENT_PX) ? row : HudWidth.fit(row, CONTENT_PX);
    }

    /** The pack's gold delimiter, used as the first and last row of every sidebar. */
    public static String flourish() {
        return "<!shadow><#F8FAFC>" + Glyphs.SB_FLOURISH + "</#F8FAFC></!shadow>";
    }

    /** True when the row is only the gold delimiter. */
    public static boolean isDelimiter(String row) {
        return row != null && row.contains(String.valueOf(Glyphs.SB_FLOURISH))
            && MiniSyntax.visibleText(row).length() <= 1;
    }

    /** The rows to send, in score order. */
    public List<String> rows() {
        return rows;
    }

    /** How many rows the client will draw. */
    public int size() {
        return rows.size();
    }

    /** True when no row in the middle of the panel is blank. */
    public boolean hasNoEmptyMiddle() {
        for (int i = 1; i < rows.size() - 1; i++) {
            if (MiniSyntax.visibleText(rows.get(i)).isBlank()) {
                return false;
            }
        }
        return true;
    }

    /** True when the panel is framed by the gold delimiter at both ends. */
    public boolean isFramed() {
        return !rows.isEmpty() && isDelimiter(rows.get(0)) && isDelimiter(rows.get(rows.size() - 1));
    }

    /** Rows wider than the panel — must always be empty. */
    public List<String> overflowing() {
        List<String> wide = new ArrayList<>();
        for (String row : rows) {
            if (!HudWidth.fits(row, CONTENT_PX)) {
                wide.add(row);
            }
        }
        return wide;
    }

    @Override
    public String toString() {
        return String.join(" / ", rows);
    }
}
