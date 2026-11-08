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
                if (!expectedList.isEmpty() || key.equals("motd.maintenance-whitelist")) {
                    Object sample = expectedList.isEmpty() ? "" : expectedList.get(0);
                    for (Object element : actualList) {
                        if ((sample instanceof String && !(element instanceof String))
                            || (sample instanceof Number && (!(element instanceof Number n)
                                || !Double.isFinite(n.doubleValue())
                                || (sample instanceof Integer && (n.doubleValue() != Math.rint(n.doubleValue())
                                    || n.doubleValue() < Integer.MIN_VALUE || n.doubleValue() >= 0x1.0p31))))
                            || (sample instanceof Map<?, ?> && !(element instanceof Map<?, ?>))) {
                            fail(key, "contains an invalid list element");
                        }
                    }
                }
            }
        }
    }

    /** Validate the ranges of current configs, retaining legacy migration compatibility. */
    public static void validateRanges(ConfigurationSection live) {
        validateRanges(live, false);
    }

    static void validateRanges(ConfigurationSection live, boolean legacy) {
        range(live, "resource-pack.embedded-http-server.port", 1, 65535, legacy);
        range(live, "storage.database.port", 1, 65535, legacy);
        range(live, "match.quarters", 1, 8, legacy);
        range(live, "match.quarter-seconds", 15, 1800, legacy);
        range(live, "match.overtime-seconds", 10, 600, legacy);
        range(live, "schematic-courts.instances", 1, 256, legacy);
        range(live, "schematic-courts.spacing-blocks", 64, 4096, legacy);
        range(live, "schematic-courts.half-width", 4, 64, legacy);
        range(live, "schematic-courts.half-length", 4, 96, legacy);
        range(live, "schematic-courts.max-blocks-per-tick", 512, 4000, legacy);
        // Mirrors the bounds used by BosketConfig, so bad current values fail at load time.
        range(live, "general.starting-coins", 0, 1_000_000, legacy);
        range(live, "motd.max-players-display", 0, 100_000, legacy);
        range(live, "protection.void-rescue-y", -256.0, 64.0, legacy);
        range(live, "match.shot-clock-seconds", 5, 120, legacy);
        range(live, "match.offensive-rebound-shot-clock", 5, 120, legacy);
        range(live, "match.countdown-seconds", 1, 30, legacy);
        range(live, "match.celebration-ticks", 20, 300, legacy);
        range(live, "match.on-fire-streak-threshold", 2, 20, legacy);
        range(live, "match.target-score-1v1", 5, 100, legacy);
        range(live, "match.remake-window-seconds", 0, 600, legacy);
        range(live, "match.remake-min-players", 1, 10, legacy);
        range(live, "practice.max-instances", 1, 256, legacy);
        range(live, "practice.grid-spacing-blocks", 96, 1024, legacy);
        range(live, "economy.casual-reward-multiplier", 0.0, 2.0, legacy);
        range(live, "physics.gravity", 0.005, 0.25, legacy);
        range(live, "physics.air-drag", 0.85, 0.9999, legacy);
        range(live, "physics.floor-restitution", 0.10, 0.98, legacy);
        range(live, "physics.backboard-restitution", 0.10, 0.98, legacy);
        range(live, "physics.rim-restitution", 0.10, 0.98, legacy);
        range(live, "physics.steal-cooldown-ms", 100L, 15_000L, legacy);
        range(live, "physics.crossover-cooldown-ms", 100L, 15_000L, legacy);
        range(live, "physics.block-cooldown-ms", 100L, 15_000L, legacy);
        range(live, "nameplates.scale", 0.25, 3.0, legacy);
        range(live, "nameplates.view-range", 0.3, 4.0, legacy);
        range(live, "storage.auto-save-interval-seconds", 5, 600, legacy);
        range(live, "storage.max-match-history", 1, 100, legacy);
        range(live, "storage.database.pool-size", 1, 32, legacy);
        range(live, "storage.database.sync-interval-seconds", 10, 3600, legacy);
        range(live, "storage.backups.interval-hours", 1, 168, legacy);
        range(live, "storage.backups.keep", 1, 100, legacy);
        range(live, "matchmaking.elo-window-start", 0, 5000, legacy);
        range(live, "matchmaking.elo-window-growth", 0, 1000, legacy);
        range(live, "matchmaking.expand-every-seconds", 1, 300, legacy);
        range(live, "matchmaking.elo-window-max", 0, 10_000, legacy);
        range(live, "matchmaking.max-wait-seconds", 5, 3600, legacy);
        range(live, "matchmaking.accept-prompt.timeout-seconds", 3, 60, legacy);
        range(live, "matchmaking.dodge-penalty-seconds", 0, 86_400, legacy);
        range(live, "matchmaking.dodge-penalty-multiplier", 1, 10, legacy);
        range(live, "matchmaking.dodge-counter-reset-minutes", 1, 1440, legacy);
        range(live, "matchmaking.backfill-bot-after-seconds", 0, 3600, legacy);
        range(live, "progression.quests.daily-count", 1, 6, legacy);
        range(live, "progression.quests.weekly-count", 1, 6, legacy);
        range(live, "progression.quests.contract-count", 1, 4, legacy);
        range(live, "progression.quests.contract-reroll-cost", 0, 100_000, legacy);
        range(live, "crates.cost-coins", 0, 1_000_000, legacy);
        range(live, "crates.daily-limit", 1, 100, legacy);
        range(live, "crates.cooldown-seconds", 0, 600, legacy);
        range(live, "crates.coin-min", 0, 1_000_000, legacy);
        range(live, "crates.coin-max", 0, 1_000_000, legacy);
        range(live, "crates.cosmetic-chance", 0.0, 1.0, legacy);
        range(live, "crates.bonus-chance", 0.0, 1.0, legacy);
        range(live, "moderation.profanity-filter.warn-points", 1, 3, legacy);
        range(live, "moderation.profanity-filter.severe-warn-points", 1, 3, legacy);
        range(live, "moderation.auto-ban-at-warnings", 2, 10, legacy);
        range(live, "moderation.ban-duration-hours", 1, 8760, legacy);
        range(live, "moderation.anti-spam.min-seconds-between-messages", 0, 60, legacy);
        range(live, "moderation.anti-spam.messages-per-window", 1, 50, legacy);
        range(live, "moderation.anti-spam.window-seconds", 1, 300, legacy);
        range(live, "moderation.anti-spam.mute-seconds", 1, 3600, legacy);
        range(live, "moderation.caps-min-length", 3, 64, legacy);
        range(live, "moderation.default-mute-minutes", 1, 10_080, legacy);
        range(live, "moderation.insults-before-mute", 1, 20, legacy);
        range(live, "moderation.mute-hours", 1, 720, legacy);
        range(live, "moderation.mutes-before-ban", 2, 20, legacy);
        range(live, "leaderboards.holo-refresh-seconds", 1, 300, legacy);
        range(live, "leaderboards.cache-size", 3, 50, legacy);
        range(live, "guilds.max-members", 2, 100, legacy);
        range(live, "guilds.tag-max-length", 2, 12, legacy);
        range(live, "guilds.name-min-length", 2, 32, legacy);
        range(live, "guilds.name-max-length", 4, 32, legacy);
        range(live, "guilds.starting-elo", 0, 5000, legacy);
        range(live, "season.duration-days", 1, 365, legacy);
        range(live, "season.placement-matches", 0, 50, legacy);
        range(live, "season.soft-reset.anchor", 100, 5000, legacy);
        range(live, "season.soft-reset.keep-ratio", 0.0, 1.0, legacy);
        range(live, "economy.win-coins", 0, Integer.MAX_VALUE, legacy);
        range(live, "economy.loss-coins", 0, Integer.MAX_VALUE, legacy);
        range(live, "economy.forfeit-coins", 0, Integer.MAX_VALUE, legacy);
        range(live, "economy.mvp-bonus-coins", 0, Integer.MAX_VALUE, legacy);
        range(live, "economy.two-point-coins", 0, Integer.MAX_VALUE, legacy);
        range(live, "economy.three-point-coins", 0, Integer.MAX_VALUE, legacy);
        range(live, "economy.dunk-bonus-coins", 0, Integer.MAX_VALUE, legacy);
        range(live, "economy.assist-coins", 0, Integer.MAX_VALUE, legacy);
        range(live, "economy.steal-coins", 0, Integer.MAX_VALUE, legacy);
        range(live, "economy.block-coins", 0, Integer.MAX_VALUE, legacy);
        range(live, "economy.three-point-contest-max-coins", 0, Integer.MAX_VALUE, legacy);
        range(live, "economy.gg-reward-coins", 0, Integer.MAX_VALUE, legacy);
        range(live, "crates.bonus-coins", 0, Integer.MAX_VALUE, legacy);
        range(live, "tutorial.completion-coins", 0, Integer.MAX_VALUE, legacy);
        range(live, "tutorial.completion-xp", 0, Integer.MAX_VALUE, legacy);
        range(live, "guilds.create-cost-coins", 0, Integer.MAX_VALUE, legacy);
        range(live, "season.rewards.top-1-coins", 0, Integer.MAX_VALUE, legacy);
        range(live, "season.rewards.top-2-coins", 0, Integer.MAX_VALUE, legacy);
        range(live, "season.rewards.top-3-coins", 0, Integer.MAX_VALUE, legacy);
        range(live, "schematic-courts.unpaste-delay-seconds", 0, 3600, legacy);
        range(live, "motd.startup-gate-seconds", 0, 120, legacy);
        range(live, "discord.daily-report-hour", 0, 23, legacy);
        ordered(live, "crates.coin-min", "crates.coin-max");
        ordered(live, "guilds.name-min-length", "guilds.name-max-length");
        choice(live, "storage.backend", "yaml", "sqlite", "mysql");
        choice(live, "matchmaking.bot-difficulty", "easy", "facile", "1", "normal", "medium", "2", "pro", "hard", "difficile", "3");
    }

    private static void ordered(ConfigurationSection cfg, String lowKey, String highKey) {
        if (cfg.contains(lowKey) && cfg.contains(highKey)
            && cfg.getDouble(lowKey) > cfg.getDouble(highKey)) {
            fail(lowKey, "must not exceed '" + highKey + "'");
        }
    }

    private static void choice(ConfigurationSection cfg, String key, String... choices) {
        if (!cfg.contains(key)) return;
        String value = cfg.getString(key, "");
        for (String choice : choices) {
            if (choice.equalsIgnoreCase(value)) return;
        }
        fail(key, "must be one of " + String.join(", ", choices));
    }

    private static void range(ConfigurationSection cfg, String key, double min, double max, boolean legacy) {
        if (!cfg.contains(key)) return;
        Object value = cfg.get(key);
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) {
            fail(key, "must be between " + decimal(min) + " and " + decimal(max));
        }
        Number n = (Number) value;
        double actual = n.doubleValue();
        if (actual >= min && actual <= max) return;
        if (!legacy) {
            fail(key, "must be between " + decimal(min) + " and " + decimal(max));
        }
        // Existing pre-v7 configurations historically clamped on load. Persist the same effective
        // value so an upgraded file does not fail at the next restart after its version changes.
        double clamped = Math.clamp(actual, min, max);
        if (n instanceof Double || n instanceof Float) {
            cfg.set(key, clamped);
        } else if (n instanceof Long) {
            cfg.set(key, (long) clamped);
        } else {
            cfg.set(key, (int) clamped);
        }
    }

    private static String decimal(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static void fail(String key, String reason) {
        throw new IllegalArgumentException("Invalid config key '" + key + "': " + reason);
    }
}
