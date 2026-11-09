package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import fr.bosketball.i18n.Lang;
import java.util.Map;
import org.junit.Test;

/**
 * The translation catalogue itself, not the surfaces that read it.
 *
 * <p>Every language shipped with the plugin is complete — the French, Spanish and German tables
 * override all 151 English keys, which is what the settings card advertises — and every one of them
 * printed through {@code flag + " " + displayName} for years with an empty flag, so the card read
 * "Current:  Français" with a double space. These tests pin the catalogue so a new language or a new
 * key cannot quietly break either promise.</p>
 */
public final class LangCatalogueTest {

    @Test
    public void everyShippedLanguageTranslatesEveryKey() {
        assertTrue("the catalogue must declare its keys", Lang.keyCount() > 100);
        for (Lang.Locale_ locale : Lang.Locale_.values()) {
            assertEquals(locale.code() + " is only " + Lang.coverage(locale) + "% translated",
                100, Lang.coverage(locale));
        }
    }

    @Test
    public void everyLanguageNamesItself() {
        for (Lang.Locale_ locale : Lang.Locale_.values()) {
            assertFalse(locale.code() + " has no display name", locale.displayName().isBlank());
            assertEquals("the name must not need trimming", locale.displayName().trim(), locale.displayName());
        }
    }

    @Test
    public void aTranslatedLineNeverShowsTwoSpacesInARow() {
        for (Lang.Locale_ locale : Lang.Locale_.values()) {
            Lang lang = Lang.of(locale);
            String next = locale.next().displayName();
            String hint = lang.get("gui.language.hint", Map.of("locale", next));
            assertFalse(locale.code() + " pads its hint twice: " + hint, hint.contains("  "));
            assertTrue(locale.code() + " forgot the language name: " + hint, hint.contains(next));
        }
    }

    @Test
    public void noTranslatedValueSitsOnATrailingSpace() {
        for (Lang.Locale_ locale : Lang.Locale_.values()) {
            Lang lang = Lang.of(locale);
            for (String key : new String[] {"gui.language", "gui.language.hint", "generic.coins",
                "generic.elo", "generic.player_only", "admin.saved"}) {
                String value = lang.get(key);
                assertEquals(locale.code() + "/" + key + " trails a space", value.trim(), value);
            }
        }
    }
}
