package fr.bosketball.config;

import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/** Validation of shipped config value types and selected operational ranges. */
public final class ConfigValidator {
    private ConfigValidator() {
    }

    /** Check explicitly supplied values against bundled defaults, before legacy upgrades. */
    public static void validateTypes(ConfigurationSection live, ConfigurationSection defaults) {
        for (String key : defaults.getKeys(true)) {
            if (!live.contains(key)) {
                continue;
            }
            Object expected = defaults.get(key);
            Object value = live.get(key);
            if (expected instanceof ConfigurationSection) {
                if (!(value instanceof ConfigurationSection)) {
                    fail(key, "must be a section");
                }
            } else if (expected instanceof Boolean) {
                if (!(value instanceof Boolean)) {
                    fail(key, "must be a boolean");
                }
            } else if (expected instanceof Number number) {
                boolean integral = number instanceof Byte || number instanceof Short
                    || number instanceof Integer || number instanceof Long;
                if (!(value instanceof Number actual) || !Double.isFinite(actual.doubleValue())
                    || (integral && (actual.doubleValue() != Math.rint(actual.doubleValue())
                        || actual.doubleValue() < (number instanceof Long ? Long.MIN_VALUE : Integer.MIN_VALUE)
                        || actual.doubleValue() >= (number instanceof Long ? 0x1.0p63 : 0x1.0p31)))) {
                    fail(key, integral ? "must be a finite integer" : "must be a finite number");
                }
            } else if (expected instanceof String) {
                if (!(value instanceof String)) {
                    fail(key, "must be a string");
                }
            } else if (expected instanceof List<?> expectedList) {
                if (!(value instanceof List<?>)) {
                    fail(key, "must be a list");
                }
                List<?> actualList = (List<?>) value;
                if (!expectedList.isEmpty()) {
                    Object sample = expectedList.get(0);
                    for (Object element : actualList) {
                        if ((sample instanceof String && !(element instanceof String))
                            || (sample instanceof Number && (!(element instanceof Number n)
                                || !Double.isFinite(n.doubleValue())))
                            || (sample instanceof Map<?, ?> && !(element instanceof Map<?, ?>))) {
                            fail(key, "contains an invalid list element");
                        }
                    }
                }
            }
        }
    }

    /** Reject selected dangerous operational values rather than silently clamping them. */
    public static void validateRanges(ConfigurationSection live) {
        range(live, "resource-pack.embedded-http-server.port", 1, 65535);
        range(live, "storage.database.port", 1, 65535);
        range(live, "match.quarters", 1, 8);
        range(live, "match.quarter-seconds", 15, 1800);
        range(live, "match.overtime-seconds", 10, 600);
        range(live, "schematic-courts.instances", 1, 256);
        range(live, "schematic-courts.spacing-blocks", 64, 4096);
        range(live, "schematic-courts.half-width", 4, 64);
        range(live, "schematic-courts.half-length", 4, 96);
        range(live, "schematic-courts.max-blocks-per-tick", 128, 4000);
    }

    private static void range(ConfigurationSection cfg, String key, double min, double max) {
        if (cfg.contains(key)) {
            Object value = cfg.get(key);
            if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())
                || n.doubleValue() < min || n.doubleValue() > max) {
                fail(key, "must be between " + (long) min + " and " + (long) max);
            }
        }
    }

    private static void fail(String key, String reason) {
        throw new IllegalArgumentException("Invalid config key '" + key + "': " + reason);
    }
}
