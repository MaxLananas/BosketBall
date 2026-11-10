package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import fr.bosketball.model.PlayerSettings;
import org.junit.Test;

/**
 * Player settings are read from a file an operator can edit and a merge can mangle. A volume is
 * the one of them that ends up in a sound packet, where a NaN is not a quieter sound: it is a
 * number the client cannot interpret.
 */
public class SettingsRobustnessTest {

    @Test
    public void aVolumeThatIsNotANumberKeepsTheOldOne() {
        PlayerSettings settings = new PlayerSettings();
        settings.setUiSoundVolume(0.4f);
        settings.setUiSoundVolume(Float.NaN);
        assertEquals("a NaN volume must not replace a real one", 0.4f, settings.uiSoundVolume(), 1e-6f);
        settings.setUiSoundVolume(Float.POSITIVE_INFINITY);
        assertEquals(0.4f, settings.uiSoundVolume(), 1e-6f);
        settings.setUiSoundVolume(Float.NEGATIVE_INFINITY);
        assertEquals(0.4f, settings.uiSoundVolume(), 1e-6f);
    }

    @Test
    public void aVolumeOutsideTheRangeIsClamped() {
        PlayerSettings settings = new PlayerSettings();
        settings.setUiSoundVolume(4.0f);
        assertEquals(1.0f, settings.uiSoundVolume(), 1e-6f);
        settings.setUiSoundVolume(-2.0f);
        assertEquals(0.0f, settings.uiSoundVolume(), 1e-6f);
        settings.setUiSoundVolume(0.65f);
        assertEquals("a real volume is kept exactly", 0.65f, settings.uiSoundVolume(), 1e-6f);
        assertTrue(Double.isFinite(settings.uiSoundVolume()));
    }
}
