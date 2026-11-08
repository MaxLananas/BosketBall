package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import fr.bosketball.config.BosketConfig;
import fr.bosketball.config.ConfigValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

public class ConfigValidatorTest {
    private static YamlConfiguration defaults() {
        return YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());
    }

    private static void invalidType(String key, Object value, String reason) {
        YamlConfiguration live = new YamlConfiguration();
        live.set(key, value);
        try {
            ConfigValidator.validateTypes(live, defaults());
            fail("expected rejection of " + key);
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage(), ex.getMessage().contains(key));
            assertTrue(ex.getMessage(), ex.getMessage().contains(reason));
        }
    }

    private static void invalidRange(String key, Object value) {
        YamlConfiguration live = new YamlConfiguration();
        live.set(key, value);
        try {
            ConfigValidator.validateRanges(live);
            fail("expected rejection of " + key);
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage(), ex.getMessage().contains(key));
            assertTrue(ex.getMessage(), ex.getMessage().contains("between"));
        }
    }

    @Test public void shippedDefaultsAreValid() {
        YamlConfiguration cfg = defaults();
        ConfigValidator.validateTypes(cfg, cfg);
        ConfigValidator.validateRanges(cfg);
    }

    @Test public void booleanTypeNamesKey() {
        invalidType("schematic-courts.enabled", "yes", "boolean");
    }

    @Test public void integerTypeNamesKey() {
        invalidType("match.quarters", 1.5, "integer");
        invalidType("match.quarters", 2_147_483_648L, "integer");
    }

    @Test public void nonFiniteNumberNamesKey() {
        invalidType("physics.gravity", Double.NaN, "finite number");
        invalidType("physics.gravity", Double.POSITIVE_INFINITY, "finite number");
    }

    @Test public void scalarInsteadOfSectionNamesKey() {
        invalidType("schematic-courts", "wrong", "section");
    }

    @Test public void mixedStringListNamesKey() {
        invalidType("chat.highlight-keywords", List.of("GG", 5), "list element");
    }

    @Test public void rangeFailuresNameEachKey() {
        Object[][] cases = {
            {"resource-pack.embedded-http-server.port", 0},
            {"storage.database.port", 65536},
            {"match.quarters", 0},
            {"match.quarter-seconds", 1801},
            {"schematic-courts.instances", 257},
            {"schematic-courts.half-width", 3.0},
            {"schematic-courts.max-blocks-per-tick", 127}
        };
        for (Object[] test : cases) {
            invalidRange((String) test[0], test[1]);
        }
    }

    @Test public void missingValuesRemainEligibleForDefaults() {
        YamlConfiguration live = new YamlConfiguration();
        ConfigValidator.validateTypes(live, defaults());
        ConfigValidator.validateRanges(live);
    }

    @Test public void startupRejectsInvalidConfigWithoutRewritingIt() throws Exception {
        Path file = Files.createTempFile("bosket-invalid-config", ".yml");
        String input = "config-version: 7\nmatch:\n  quarter-seconds: 1\n";
        try {
            Files.writeString(file, input);
            try {
                BosketConfig.loadAndUpgrade(file.toFile(), null);
                fail("expected invalid config");
            } catch (IllegalArgumentException ex) {
                assertTrue(ex.getMessage().contains("match.quarter-seconds"));
            }
            assertEquals(input, Files.readString(file));
            String malformed = "match: [\n";
            Files.writeString(file, malformed);
            try {
                BosketConfig.loadAndUpgrade(file.toFile(), null);
                fail("expected invalid YAML");
            } catch (IllegalArgumentException ex) {
                assertTrue(ex.getMessage(), ex.getMessage().contains(file.getFileName().toString()));
            }
            assertEquals(malformed, Files.readString(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
