package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import fr.bosketball.util.Glyphs;
import org.junit.Test;

/**
 * The value column of a card is padded in pixels.
 *
 * <p>The five labelled rows of the player card (Rank, Record, Career, Shooting, Playtime) used to
 * end in a hand-counted run of spaces. A space is four pixels wide where the letters are five or
 * six, so counting spaces left the value column five pixels apart from row to row, which the eye
 * reads as a crooked card. {@code Glyphs.padToColumn} counts the pixels and lands every row on the
 * same column to within two pixels.</p>
 */
public final class GlyphsColumnTest {

    private static final String[] CARD_LABELS = {"Rank", "Record", "Career", "Shooting", "Playtime"};

    private static int column() {
        int widest = 0;
        for (String label : CARD_LABELS) {
            widest = Math.max(widest, Glyphs.textAdvancePx(label, false) + 4);
        }
        return widest;
    }

    private static int startOf(String label, int column) {
        String padding = Glyphs.padToColumn(label, column);
        return Glyphs.textAdvancePx(label, false) + Glyphs.textAdvancePx(padding, false);
    }

    @Test
    public void everyRowStartsItsValueColumnOnTheSamePixel() {
        int column = column();
        int first = startOf(CARD_LABELS[0], column);
        for (String label : CARD_LABELS) {
            int start = startOf(label, column);
            assertTrue(label + " starts its value " + Math.abs(start - first) + " px off the column",
                Math.abs(start - first) <= 2);
        }
    }

    @Test
    public void noRowOverflowsTheColumnByMoreThanHalfASpace() {
        int column = column();
        for (String label : CARD_LABELS) {
            assertTrue(label + " runs past its column", startOf(label, column) - column <= 2);
        }
    }

    @Test
    public void theWidestLabelKeepsOneSpaceBeforeItsValue() {
        int column = column();
        for (String label : CARD_LABELS) {
            assertTrue(label + " would touch its value",
                startOf(label, column) - Glyphs.textAdvancePx(label, false) >= 4);
        }
    }

    @Test
    public void aLabelAlreadyAtTheColumnIsNotPadded() {
        assertEquals("", Glyphs.padToColumn("Shooting", 10));
        assertEquals("", Glyphs.padToColumn(null, 0));
        assertEquals("    ", Glyphs.padToColumn("Rank", Glyphs.textAdvancePx("Rank", false) + 16));
    }
}
