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
        invalidType("motd.maintenance-whitelist", List.of(5), "list element");
        invalidType("season.rewards.tier-coins", List.of(1.5), "list element");
    }

    @Test public void rangeFailuresNameEachKey() {
        Object[][] cases = {
            {"resource-pack.embedded-http-server.port", 0},
            {"storage.database.port", 65536},
            {"match.quarters", 0},
            {"match.quarter-seconds", 1801},
            {"match.shot-clock-seconds", 4},
            {"physics.gravity", 0.004},
            {"economy.win-coins", -1},
            {"season.soft-reset.keep-ratio", 1.1},
            {"schematic-courts.instances", 257},
            {"schematic-courts.half-width", 3.0},
            {"schematic-courts.max-blocks-per-tick", 127}
        };
        for (Object[] test : cases) {
            invalidRange((String) test[0], test[1]);
        }
    }

    @Test public void everyLiteralClampHasAStartupRangeCheck() throws Exception {
        String source = Files.readString(Path.of("src/main/java/fr/bosketball/config/BosketConfig.java"));
        String validator = Files.readString(Path.of("src/main/java/fr/bosketball/config/ConfigValidator.java"));
        var pattern = java.util.regex.Pattern.compile(
            "Math\\.(?:clamp\\(cfg\\.get(?:Int|Long|Double)\\(|max\\(0, cfg\\.getInt\\()\"([a-z0-9.-]+)\"");
        var matcher = pattern.matcher(source);
        int seen = 0;
        while (matcher.find()) {
            String key = matcher.group(1);
            assertTrue("Missing startup range for " + key, validator.contains("range(live, \"" + key + "\","));
            seen++;
        }
        assertTrue("The range guard did not scan the expected config fields", seen >= 100);
    }

    @Test public void crossFieldAndEnumChecksNameBadKeys() {
        YamlConfiguration live = new YamlConfiguration();
        live.set("crates.coin-min", 300);
        live.set("crates.coin-max", 200);
        try {
            ConfigValidator.validateRanges(live);
            fail("expected invalid coin range");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("crates.coin-min"));
            assertTrue(ex.getMessage().contains("crates.coin-max"));
        }
        live.set("crates.coin-min", 100);
        live.set("storage.backend", "not-a-backend");
        try {
            ConfigValidator.validateRanges(live);
            fail("expected invalid backend");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("storage.backend"));
        }
        live.set("storage.backend", "sqlite");
        live.set("matchmaking.bot-difficulty", "hard");
        ConfigValidator.validateRanges(live);
    }

    @Test public void semanticInvalidValuesNameTheirKeys() {
        Object[][] cases = {
            {"general.server-domain", "  "},
            {"schematic-courts.head-skins.someone", 42},
            {"schematic-courts.rotation", 45},
            {"schematic-courts.hoop-facing", "diagonal"},
            {"schematic-courts.origin-y", 400},
            {"schematic-courts.rim-inset", -3.0},
            {"progression.daily-login.streak-rewards", List.of(10, -1)},
            {"season.rewards.tier-xp", List.of(10, -5)},
            {"season.start-date", "2026-13-40"},
            {"season.start-date", "+999999999-12-31"}
        };
        for (Object[] entry : cases) {
            YamlConfiguration live = new YamlConfiguration();
            live.set((String) entry[0], entry[1]);
            try {
                ConfigValidator.validateRanges(live);
                fail("expected invalid " + entry[0]);
            } catch (IllegalArgumentException ex) {
                assertTrue(ex.getMessage(), ex.getMessage().contains((String) entry[0]));
            }
        }
        YamlConfiguration dates = new YamlConfiguration();
        dates.set("season.start-date", "2026-11-01");
        dates.set("season.end-date", "2026-10-01");
        try {
            ConfigValidator.validateRanges(dates);
            fail("expected reversed date range");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage(), ex.getMessage().contains("season.end-date"));
            assertTrue(ex.getMessage(), ex.getMessage().contains("season.start-date"));
        }
    }

    @Test public void startupChecksCrossFieldsAgainstBundledDefaults() throws Exception {
        Path file = Files.createTempFile("bosket-invalid-cross-field", ".yml");
        String input = "config-version: 7\ncrates:\n  coin-max: 1\n";
        try {
            Files.writeString(file, input);
            try {
                BosketConfig.loadAndUpgrade(file.toFile(), null);
                fail("expected invalid coin range");
            } catch (IllegalArgumentException ex) {
                assertTrue(ex.getMessage(), ex.getMessage().contains("crates.coin-min"));
                assertTrue(ex.getMessage(), ex.getMessage().contains("crates.coin-max"));
            }
            assertEquals(input, Files.readString(file));
        } finally {
            Files.deleteIfExists(file);
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
