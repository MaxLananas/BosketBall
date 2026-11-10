package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import fr.bosketball.model.ShotReleaseQuality;
import fr.bosketball.util.Glyphs;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.Test;

/**
 * The action bar is the one surface a player reads every second of a match. A NaN reaching
 * {@code String.format("%.1fm")} is the literal word "NaNm" in the middle of it, and a NaN bar is
 * a bar that draws nothing: the numbers are sanitised where they are drawn.
 */
public class HudNumberTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    public void hudNumberReplacesWhatTheScreenCannotShow() {
        assertEquals(2.5, Glyphs.hudNumber(2.5, 0.0), 0.0);
        assertEquals(0.0, Glyphs.hudNumber(Double.NaN, 0.0), 0.0);
        assertEquals(0.0, Glyphs.hudNumber(Double.POSITIVE_INFINITY, 0.0), 0.0);
        assertEquals(0.0, Glyphs.hudNumber(Double.NEGATIVE_INFINITY, 0.0), 0.0);
    }

    @Test
    public void theChargeBarSurvivesANaNProgress() {
        assertEquals("a NaN charge draws the empty bar, not a broken glyph",
            Glyphs.shotBarGlyph(0.0), Glyphs.shotBarGlyph(Double.NaN));
        assertEquals(Glyphs.shotBarWidth(0.0), Glyphs.shotBarWidth(Double.NaN));
        assertTrue(Glyphs.shotBarGlyph(Double.POSITIVE_INFINITY) == Glyphs.shotBarGlyph(0.0));
    }

    @Test
    public void theAimHudNeverPrintsNaN() {
        String text = plain(Glyphs.composeProAimHud(
            Double.NaN, true, ShotReleaseQuality.PERFECT_GREEN, true,
            Double.NaN, Double.NaN, false, Double.NaN));
        assertFalse("charge bar: " + text, text.contains("NaN"));
        assertFalse(text.contains("Infinity"));

        String idle = plain(Glyphs.composeIdleAimCompass(
            true, Double.NaN, Double.NaN, true, false, Double.NaN));
        assertFalse("idle compass: " + idle, idle.contains("NaN"));
        assertFalse(idle.contains("Infinity"));
    }

    @Test
    public void theDunkHudNeverPrintsNaN() {
        assertFalse(plain(Glyphs.composeDunkSlamHud(Double.NaN, true)).contains("NaN"));
        assertFalse(plain(Glyphs.composeDunkTimingHud(Double.NaN, true, true)).contains("NaN"));
    }

    @Test
    public void realValuesAreStillPrinted() {
        String text = plain(Glyphs.composeProAimHud(
            0.9, false, null, true, 1.5, 6.4, true, 0.2));
        assertTrue("a real distance is still shown: " + text, text.contains("6.4m"));
        assertTrue("and the points value with it", text.contains("3PT"));
    }
}
