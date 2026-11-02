package fr.bosketball.config;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Immutable, validated, O(1) memory-cached configuration snapshot for BosketBall.
 * Eliminates all per-tick YAML HashMap string lookups in physics, match, chat, MOTD, protection, and UI loops,
 * and automatically backfills new keys when upgrading an existing server's config.yml.
 */
public record BosketConfig(
    int configVersion,
    General general,
    Motd motd,
    Protection protection,
    Welcome welcome,
    Links links,
    ResourcePack resourcePack,
    Match match,
    Practice practice,
    Economy economy,
    Physics physics,
    Chat chat,
    Storage storage
) {

    public record General(
        String serverDomain,
        String seasonName,
        boolean autoEquipLobbyPlaybook,
        int startingCoins
    ) {
    }

    public record Motd(
        boolean enabled,
        boolean autoInstallServerIcon,
        int maxPlayersDisplay,
        String header,
        List<String> subtitles,
        boolean maintenanceMode,
        String maintenanceSubtitle
    ) {
    }

    public record Protection(
        boolean enabled,
        boolean disableBlockGravity,
        boolean disableFireAndSpread,
        boolean disableExplosions,
        boolean disableWeatherAndDecay,
        boolean blockIllegalItems,
        boolean allowFaweAndAxiomBypass,
        double voidRescueY
    ) {
    }

    public record Welcome(
        boolean enabled,
        String headline,
        String subtitle
    ) {
    }

    public record Links(
        String website,
        String shop,
        String discord
    ) {
    }

    public record ResourcePack(
        boolean autoSendOnJoin,
        boolean required,
        boolean httpEnabled,
        String bindAddress,
        int port,
        String publicHost,
        String externalUrl,
        String externalSha1
    ) {
    }

    public record Match(
        int quarters,
        int quarterSeconds,
        int overtimeSeconds,
        int shotClockSeconds,
        int offensiveReboundShotClock,
        int countdownSeconds,
        int celebrationTicks,
        int onFireStreakThreshold,
        int targetScore1v1,
        boolean goalReplayEnabled
    ) {
        public Match(
            int quarters,
            int quarterSeconds,
            int overtimeSeconds,
            int shotClockSeconds,
            int offensiveReboundShotClock,
            int countdownSeconds,
            int celebrationTicks,
            int onFireStreakThreshold,
            int targetScore1v1
        ) {
            this(quarters, quarterSeconds, overtimeSeconds, shotClockSeconds, offensiveReboundShotClock,
                countdownSeconds, celebrationTicks, onFireStreakThreshold, targetScore1v1, true);
        }
    }

    public record Practice(
        String worldName,
        int maxInstances,
        int gridSpacingBlocks
    ) {
    }

    public record Economy(
        int winCoins,
        int lossCoins,
        int forfeitCoins,
        int mvpBonusCoins,
        int twoPointCoins,
        int threePointCoins,
        int dunkBonusCoins,
        int assistCoins,
        int stealCoins,
        int blockCoins,
        int threePointContestMaxCoins,
        int ggRewardCoins
    ) {
    }

    public record Physics(
        double gravity,
        double airDrag,
        double floorRestitution,
        double backboardRestitution,
        double rimRestitution,
        long stealCooldownMs,
        long crossoverCooldownMs,
        long blockCooldownMs
    ) {
    }

    public record Chat(
        boolean enabled,
        boolean joinQuitMessages,
        String joinFormat,
        String quitFormat,
        boolean clearAfterMatch,
        boolean showTopStatBadges,
        List<String> highlightKeywords
    ) {
        public Chat(
            boolean enabled,
            boolean clearAfterMatch,
            boolean showTopStatBadges,
            List<String> highlightKeywords
        ) {
            this(
                enabled,
                true,
                "<dark_gray>[</dark_gray><#4ADE80><bold>+</bold></#4ADE80><dark_gray>]</dark_gray> <white>{player}</white>",
                "<dark_gray>[</dark_gray><#F87171><bold>-</bold></#F87171><dark_gray>]</dark_gray> <gray>{player}</gray>",
                clearAfterMatch,
                showTopStatBadges,
                highlightKeywords
            );
        }
    }

    public record Storage(
        int autoSaveIntervalSeconds,
        int maxMatchHistory
    ) {
    }

    public static BosketConfig defaults() {
        return fromYaml(loadBundledDefaultYaml());
    }

    /**
     * Loads {@code config.yml} safely, automatically merges any newly introduced default keys
     * when upgrading from an older version, and returns a clamped immutable {@link BosketConfig}.
     */
    public static BosketConfig loadAndUpgrade(File configFile, Logger logger) {
        YamlConfiguration bundled = loadBundledDefaultYaml();
        if (configFile == null || !configFile.exists()) {
            return fromYaml(bundled);
        }

        YamlConfiguration live = AtomicYamlWriter.loadSafe(configFile, logger);
        if (live.contains("general.welcome-card-enabled") && !live.contains("welcome.enabled")) {
            live.set("welcome.enabled", live.getBoolean("general.welcome-card-enabled", true));
        }
        // Auto-sync bundled resource-pack SHA-1 when external-url is unused so local upgrades never retain an outdated hash
        String extUrl = live.getString("resource-pack.external-url", "");
        String bundledSha1 = bundled.getString("resource-pack.external-sha1", "");
        if ((extUrl == null || extUrl.isBlank()) && !bundledSha1.isBlank()
            && !bundledSha1.equalsIgnoreCase(live.getString("resource-pack.external-sha1", ""))) {
            live.set("resource-pack.external-sha1", bundledSha1);
            AtomicYamlWriter.writeYamlAtomically(configFile, null, live, logger);
        }

        boolean addedMissingKeys = false;
        Set<String> defaultKeys = bundled.getKeys(true);
        for (String key : defaultKeys) {
            if (bundled.isConfigurationSection(key)) {
                continue;
            }
            if (!live.contains(key)) {
                live.set(key, bundled.get(key));
                addedMissingKeys = true;
            }
        }

        if (addedMissingKeys) {
            live.set("config-version", bundled.getInt("config-version", 3));
            String header = "# ===========================================================================\n"
                + "#   BOSKETBALL — MAIN SERVER CONFIGURATION\n"
                + "#   Reload changes live at any time with: /bb reload\n"
                + "#   Shop item prices (Tags, Basketballs, Dunk Styles) live in: shop.yml\n"
                + "#   Permissions & staff groups live in: permissions.yml\n"
                + "#   Arenas & custom maps live in: arenas.yml\n"
                + "#   Player profiles live in: players/<uuid>.yml (indexed in _name_index.yml)\n"
                + "# ===========================================================================\n";
            AtomicYamlWriter.writeYamlAtomically(configFile, header, live, logger);
            if (logger != null) {
                logger.info("Updated config.yml with newly added configuration keys.");
            }
        }

        return fromYaml(live);
    }

    public static BosketConfig fromYaml(YamlConfiguration cfg) {
        int version = cfg.getInt("config-version", 3);

        General general = new General(
            nonBlank(cfg.getString("general.server-domain"), "BOSKETBALL.NET"),
            nonBlank(cfg.getString("general.season-name"), "ꜱᴇᴀꜱᴏɴ ɪ"),
            cfg.getBoolean("general.auto-equip-lobby-playbook", true),
            Math.clamp(cfg.getInt("general.starting-coins", 100), 0, 1_000_000)
        );

        List<String> motdSubs = cfg.getStringList("motd.subtitles");
        if (motdSubs.isEmpty()) {
            motdSubs = List.of(
                "<#22D3EE>✦</#22D3EE> <gradient:#FF5E5E:#FFB830:#6EE755:#2CE6FF:#5B8CFF><bold>COMPETITIVE 3D BASKETBALL</bold></gradient> <#22D3EE>✦</#22D3EE>"
            );
        }
        Motd motd = new Motd(
            cfg.getBoolean("motd.enabled", true),
            cfg.getBoolean("motd.auto-install-server-icon", true),
            Math.clamp(cfg.getInt("motd.max-players-display", 100), 0, 100_000),
            nonBlank(cfg.getString("motd.header"), "<gradient:#FFD43B:#FF8C1A><bold>BOSKETBALL.NET</bold></gradient>"),
            List.copyOf(motdSubs),
            cfg.getBoolean("motd.maintenance-mode", false),
            nonBlank(cfg.getString("motd.maintenance-subtitle"), "<#F87171>⚠ <bold>SCHEDULED MAINTENANCE IN PROGRESS</bold> ⚠</#F87171>")
        );

        Protection protection = new Protection(
            cfg.getBoolean("protection.enabled", true),
            cfg.getBoolean("protection.disable-block-gravity", true),
            cfg.getBoolean("protection.disable-fire-and-spread", true),
            cfg.getBoolean("protection.disable-explosions", true),
            cfg.getBoolean("protection.disable-weather-and-decay", true),
            cfg.getBoolean("protection.block-illegal-items", true),
            cfg.getBoolean("protection.allow-fawe-and-axiom-bypass", true),
            Math.clamp(cfg.getDouble("protection.void-rescue-y", -16.0), -256.0, 64.0)
        );

        boolean welcomeEnabled = cfg.contains("welcome.enabled")
            ? cfg.getBoolean("welcome.enabled", true)
            : cfg.getBoolean("general.welcome-card-enabled", true);
        Welcome welcome = new Welcome(
            welcomeEnabled,
            nonBlank(cfg.getString("welcome.headline"), "Welcome to BosketBall!"),
            nonBlank(cfg.getString("welcome.subtitle"), "Queue 1v1–5v5, earn Coins on the court & unlock cosmetics in /bb shop.")
        );

        Links links = new Links(
            nonBlank(cfg.getString("links.website"), "https://bosketball.net"),
            nonBlank(cfg.getString("links.shop"), "https://store.bosketball.net"),
            nonBlank(cfg.getString("links.discord"), "https://discord.gg/pnJhKuU2QK")
        );

        ResourcePack rp = new ResourcePack(
            cfg.getBoolean("resource-pack.auto-send-on-join", true),
            cfg.getBoolean("resource-pack.required", false),
            cfg.getBoolean("resource-pack.embedded-http-server.enabled", false),
            nonBlank(cfg.getString("resource-pack.embedded-http-server.bind-address"), "0.0.0.0"),
            Math.clamp(cfg.getInt("resource-pack.embedded-http-server.port", 8765), 1, 65535),
            nonBlank(cfg.getString("resource-pack.embedded-http-server.public-host"), "127.0.0.1"),
            cfg.getString("resource-pack.external-url", "").trim(),
            cfg.getString("resource-pack.external-sha1", "").trim()
        );

        Match match = new Match(
            Math.clamp(cfg.getInt("match.quarters", 4), 1, 8),
            Math.clamp(cfg.getInt("match.quarter-seconds", 90), 15, 1800),
            Math.clamp(cfg.getInt("match.overtime-seconds", 45), 10, 600),
            Math.clamp(cfg.getInt("match.shot-clock-seconds", 24), 5, 120),
            Math.clamp(cfg.getInt("match.offensive-rebound-shot-clock", 14), 5, 120),
            Math.clamp(cfg.getInt("match.countdown-seconds", 5), 1, 30),
            Math.clamp(cfg.getInt("match.celebration-ticks", 80), 20, 300),
            Math.clamp(cfg.getInt("match.on-fire-streak-threshold", 3), 2, 20),
            Math.clamp(cfg.getInt("match.target-score-1v1", 21), 5, 100),
            cfg.getBoolean("match.goal-replay-enabled", true)
        );

        Practice practice = new Practice(
            nonBlank(cfg.getString("practice.world-name"), "bosketball_practice"),
            Math.clamp(cfg.getInt("practice.max-instances", 64), 1, 256),
            Math.clamp(cfg.getInt("practice.grid-spacing-blocks", 200), 96, 1024)
        );

        Economy economy = new Economy(
            Math.max(0, cfg.getInt("economy.win-coins", 40)),
            Math.max(0, cfg.getInt("economy.loss-coins", 20)),
            Math.max(0, cfg.getInt("economy.forfeit-coins", 15)),
            Math.max(0, cfg.getInt("economy.mvp-bonus-coins", 15)),
            Math.max(0, cfg.getInt("economy.two-point-coins", 5)),
            Math.max(0, cfg.getInt("economy.three-point-coins", 8)),
            Math.max(0, cfg.getInt("economy.dunk-bonus-coins", 3)),
            Math.max(0, cfg.getInt("economy.assist-coins", 4)),
            Math.max(0, cfg.getInt("economy.steal-coins", 4)),
            Math.max(0, cfg.getInt("economy.block-coins", 4)),
            Math.max(0, cfg.getInt("economy.three-point-contest-max-coins", 25)),
            Math.max(0, cfg.getInt("economy.gg-reward-coins", 5))
        );

        Physics physics = new Physics(
            Math.clamp(cfg.getDouble("physics.gravity", 0.046), 0.005, 0.25),
            Math.clamp(cfg.getDouble("physics.air-drag", 0.991), 0.85, 0.9999),
            Math.clamp(cfg.getDouble("physics.floor-restitution", 0.76), 0.10, 0.98),
            Math.clamp(cfg.getDouble("physics.backboard-restitution", 0.66), 0.10, 0.98),
            Math.clamp(cfg.getDouble("physics.rim-restitution", 0.56), 0.10, 0.98),
            Math.clamp(cfg.getLong("physics.steal-cooldown-ms", 1150L), 100L, 15_000L),
            Math.clamp(cfg.getLong("physics.crossover-cooldown-ms", 1650L), 100L, 15_000L),
            Math.clamp(cfg.getLong("physics.block-cooldown-ms", 900L), 100L, 15_000L)
        );

        List<String> keywords = cfg.getStringList("chat.highlight-keywords");
        if (keywords.isEmpty()) {
            keywords = List.of("GG", "NICE SHOT", "SPLASH", "SWISH", "DUNK", "ANKLES", "GREEN");
        }
        Chat chat = new Chat(
            cfg.getBoolean("chat.enabled", true),
            cfg.getBoolean("chat.join-quit-messages", true),
            nonBlank(cfg.getString("chat.join-format"), "<dark_gray>[</dark_gray><#4ADE80><bold>+</bold></#4ADE80><dark_gray>]</dark_gray> <white>{player}</white>"),
            nonBlank(cfg.getString("chat.quit-format"), "<dark_gray>[</dark_gray><#F87171><bold>-</bold></#F87171><dark_gray>]</dark_gray> <gray>{player}</gray>"),
            cfg.getBoolean("chat.clear-after-match", true),
            cfg.getBoolean("chat.show-top-stat-badges", true),
            List.copyOf(keywords)
        );

        Storage storage = new Storage(
            Math.clamp(cfg.getInt("storage.auto-save-interval-seconds", 30), 5, 600),
            Math.clamp(cfg.getInt("storage.max-match-history", 15), 1, 100)
        );

        return new BosketConfig(
            version, general, motd, protection, welcome, links, rp, match, practice, economy, physics, chat, storage
        );
    }

    private static YamlConfiguration loadBundledDefaultYaml() {
        YamlConfiguration yml = new YamlConfiguration();
        try (InputStream in = BosketConfig.class.getClassLoader().getResourceAsStream("config.yml")) {
            if (in != null) {
                yml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                return yml;
            }
        } catch (Exception ignored) {
        }
        File localResource = new File("src/main/resources/config.yml");
        if (localResource.exists()) {
            return AtomicYamlWriter.loadSafe(localResource, null);
        }
        return yml;
    }

    private static String nonBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }
}
