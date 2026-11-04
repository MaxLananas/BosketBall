package fr.bosketball.config;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

public record BosketConfig(
    int configVersion,
    General general,
    Lobby lobby,
    Motd motd,
    Protection protection,
    Welcome welcome,
    Links links,
    ResourcePack resourcePack,
    Match match,
    Practice practice,
    SchematicCourts schematicCourts,
    Economy economy,
    Physics physics,
    Chat chat,
    Storage storage,
    Matchmaking matchmaking,
    Progression progression,
    Crates crates,
    Moderation moderation,
    Discord discord,
    Leaderboards leaderboards,
    Tutorial tutorial,
    Guilds guilds,
    I18n i18n
) {

    public record General(
        String serverDomain,
        String seasonName,
        boolean autoEquipLobbyPlaybook,
        int startingCoins,
        String language
    ) {
    }

    public record LobbyPoint(
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
    ) {
    }

    public record Lobby(
        boolean enforceSpawn,
        LobbyPoint spawn,
        boolean enforceStations,
        LobbyPoint playStation,
        LobbyPoint shopStation,
        StationsExtra stationsExtra
    ) {
    }

    public record StationsExtra(
        boolean enforce,
        StationSpec crate,
        StationSpec tutorial,
        StationSpec hallOfFame
    ) {
        public StationSpec station(String key) {
            return switch (key) {
                case "crate" -> crate;
                case "tutorial" -> tutorial;
                case "hall-of-fame" -> hallOfFame;
                default -> null;
            };
        }
    }

    public record StationSpec(
        boolean enabled,
        String world,
        double x,
        double y,
        double z,
        String facing,
        float lift
    ) {
        public boolean usable() {
            return enabled && world != null && !world.isBlank();
        }

        public float yaw() {
            return stationYaw(facing);
        }

        public org.bukkit.Location location() {
            return new org.bukkit.Location(org.bukkit.Bukkit.getWorld(world), x, y + lift, z);
        }

        public static float stationYaw(String facing) {
            String f = facing == null ? "west" : facing.trim().toLowerCase(java.util.Locale.ROOT);
            return switch (f) {
                case "north", "-z" -> 180.0f;
                case "south", "+z", "z" -> 0.0f;
                case "east", "+x", "x" -> -90.0f;
                case "west", "-x" -> 90.0f;
                default -> 90.0f;
            };
        }
    }

    public record Motd(
        boolean enabled,
        boolean autoInstallServerIcon,
        int maxPlayersDisplay,
        String header,
        List<String> subtitles,
        boolean maintenanceMode,
        String maintenanceSubtitle,
        List<String> maintenanceWhitelist,
        String maintenanceKickMessage
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
        boolean goalReplayEnabled,
        int remakeWindowSeconds,
        int remakeMinPlayers
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
                countdownSeconds, celebrationTicks, onFireStreakThreshold, targetScore1v1, true, 60, 2);
        }
    }

    public record Practice(
        String worldName,
        int maxInstances,
        int gridSpacingBlocks
    ) {
    }

    public record SchematicCourts(
        boolean enabled,
        String schematic,
        String worldName,
        int instances,
        int spacingBlocks,
        int originX,
        int originY,
        int originZ,
        double centerOffsetX,
        double centerOffsetZ,
        String facing,
        double halfWidth,
        double halfLength,
        String hoopFacing,
        String rimMaterial,
        int floorRelativeY,
        int rimRelativeY,
        double rimInset,
        boolean flushFloor,
        boolean skipEmptyAir,
        boolean pasteOnEnable,
        boolean placeholderUntilPasted,
        int maxBlocksPerTick,
        int unpasteDelaySeconds,
        boolean retireLegacyPracticeWorld
    ) {
        public static final int DEFAULT_INSTANCES = 50;

        public static final int HARD_MAX_INSTANCES = 256;

        public SchematicCourts {
            instances = Math.clamp(instances, 1, HARD_MAX_INSTANCES);
            spacingBlocks = Math.clamp(spacingBlocks, 64, 4096);
            halfWidth = Math.clamp(halfWidth, 4.0, 64.0);
            halfLength = Math.clamp(halfLength, 4.0, 96.0);
            maxBlocksPerTick = Math.clamp(maxBlocksPerTick, 512, 262_144);
            unpasteDelaySeconds = Math.clamp(unpasteDelaySeconds, 0, 3600);
        }

        public boolean hasSchematic() {
            return schematic != null && !schematic.isBlank();
        }
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
        int ggRewardCoins,
        double casualRewardMultiplier
    ) {
        public Economy(
            int winCoins, int lossCoins, int forfeitCoins, int mvpBonusCoins,
            int twoPointCoins, int threePointCoins, int dunkBonusCoins,
            int assistCoins, int stealCoins, int blockCoins,
            int threePointContestMaxCoins, int ggRewardCoins
        ) {
            this(winCoins, lossCoins, forfeitCoins, mvpBonusCoins, twoPointCoins, threePointCoins,
                dunkBonusCoins, assistCoins, stealCoins, blockCoins, threePointContestMaxCoins, ggRewardCoins, 0.40);
        }
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
        List<String> highlightKeywords,
        boolean matchChatIsolated
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
                highlightKeywords,
                true
            );
        }
    }

    public record Storage(
        int autoSaveIntervalSeconds,
        int maxMatchHistory,
        String backend,
        String sqliteFile,
        String dbHost,
        int dbPort,
        String dbName,
        String dbUser,
        String dbPassword,
        int dbPoolSize,
        int dbSyncIntervalSeconds,
        boolean backupsEnabled,
        int backupIntervalHours,
        int backupKeep
    ) {
        public Storage(int autoSaveIntervalSeconds, int maxMatchHistory) {
            this(autoSaveIntervalSeconds, maxMatchHistory, "yaml", "bosketball.db",
                "127.0.0.1", 3306, "bosketball", "root", "", 4, 60, true, 12, 10);
        }

        public boolean usesDatabase() {
            return "sqlite".equalsIgnoreCase(backend) || "mysql".equalsIgnoreCase(backend);
        }
    }

    public record Matchmaking(
        boolean eloWeighted,
        int eloWindowStart,
        int eloWindowGrowth,
        int expandEverySeconds,
        int eloWindowMax,
        int maxWaitSeconds,
        boolean acceptPromptEnabled,
        int acceptTimeoutSeconds,
        int dodgePenaltySeconds,
        int dodgePenaltyMultiplier,
        int dodgeCounterResetMinutes,
        int backfillBotAfterSeconds,
        boolean fillBotsOnLaunch,
        String botDifficulty
    ) {
    }

    public record Progression(
        boolean enabled,
        boolean questsEnabled,
        int dailyCount,
        int weeklyCount,
        int contractCount,
        int contractRerollCost,
        boolean announceQuestCompletions,
        boolean achievementsEnabled,
        boolean broadcastAchievements,
        boolean dailyLoginEnabled,
        List<Integer> streakRewards
    ) {
    }

    public record Crates(
        boolean enabled,
        int costCoins,
        int dailyLimit,
        int cooldownSeconds,
        int coinMin,
        int coinMax,
        double cosmeticChance,
        double bonusChance,
        int bonusCoins
    ) {
    }

    public record Moderation(
        boolean profanityEnabled,
        String censorCharacter,
        int warnPoints,
        int severeWarnPoints,
        boolean notifyAuthor,
        boolean exemptStaff,
        int autoBanAtWarnings,
        int banDurationHours,
        String banReason,
        boolean antiSpamEnabled,
        int minSecondsBetweenMessages,
        int messagesPerWindow,
        int windowSeconds,
        int antiSpamMuteSeconds,
        boolean blockRepeatedMessages,
        boolean blockCaps,
        int capsMinLength,
        int defaultMuteMinutes
    ) {
    }

    public record Discord(
        boolean enabled,
        String webhookUrl,
        boolean postMatchStart,
        boolean postScoreUpdates,
        boolean postMatchResults,
        boolean postRankUps,
        boolean postCrateOpenings,
        boolean postReports,
        boolean postModerationActions,
        String playerHeadUrl
    ) {
    }

    public record Leaderboards(
        int holoRefreshSeconds,
        boolean enableCategoryArrows,
        int cacheSize
    ) {
    }

    public record Tutorial(
        boolean enabled,
        boolean forceOnFirstJoin,
        boolean hologramEnabled,
        int completionCoins,
        int completionXp,
        boolean usePracticeCourt
    ) {
    }

    public record Guilds(
        boolean enabled,
        int createCostCoins,
        int maxMembers,
        int tagMaxLength,
        int nameMinLength,
        int nameMaxLength,
        int startingElo
    ) {
    }

    public record I18n(
        boolean enabled,
        String defaultLocale,
        boolean translateChatBroadcasts
    ) {
    }

    public static BosketConfig defaults() {
        return fromYaml(loadBundledDefaultYaml());
    }

    public static BosketConfig loadAndUpgrade(File configFile, Logger logger) {
        YamlConfiguration bundled = loadBundledDefaultYaml();
        if (configFile == null || !configFile.exists()) {
            return fromYaml(bundled);
        }

        YamlConfiguration live = AtomicYamlWriter.loadSafe(configFile, logger);
        boolean upgradedLegacyDefaults = false;
        int prevVersion = live.getInt("config-version", 1);
        if (live.contains("general.welcome-card-enabled") && !live.contains("welcome.enabled")) {
            live.set("welcome.enabled", live.getBoolean("general.welcome-card-enabled", true));
            upgradedLegacyDefaults = true;
        }

        if (prevVersion < 4) {
            if ("https://bosketball.net".equalsIgnoreCase(live.getString("links.website", "").trim())) {
                live.set("links.website", "");
            }
            if ("https://store.bosketball.net".equalsIgnoreCase(live.getString("links.shop", "").trim())) {
                live.set("links.shop", "");
            }
            String liveExt = live.getString("resource-pack.external-url", "").trim();
            if (liveExt.isEmpty()) {
                live.set("resource-pack.embedded-http-server.enabled", true);
                String curHost = live.getString("resource-pack.embedded-http-server.public-host", "").trim();
                if (curHost.isEmpty() || "127.0.0.1".equals(curHost)) {
                    live.set("resource-pack.embedded-http-server.public-host", "auto");
                }
            }
            live.set("config-version", 4);
            upgradedLegacyDefaults = true;
        }

        String extUrl = live.getString("resource-pack.external-url", "");
        String bundledSha1 = bundled.getString("resource-pack.external-sha1", "");
        if ((extUrl == null || extUrl.isBlank()) && !bundledSha1.isBlank()
            && !bundledSha1.equalsIgnoreCase(live.getString("resource-pack.external-sha1", ""))) {
            live.set("resource-pack.external-sha1", bundledSha1);
            upgradedLegacyDefaults = true;
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

        if (addedMissingKeys || upgradedLegacyDefaults) {
            live.set("config-version", bundled.getInt("config-version", 4));
            String header = "# ===========================================================================\n"
                + "#   BOSKETBALL — MAIN SERVER CONFIGURATION\n"
                + "#   Reload changes live at any time with: /bb reload\n"
                + "#   Shop item prices (Tags, Basketballs, Dunk Styles) live in: shop.yml\n"
                + "#   Permissions & staff groups live in: permissions.yml\n"
                + "#   Arenas & custom maps live in: arenas.yml\n"
                + "#   Player profiles live in: players/<uuid>.yml (indexed in _name_index.yml)\n"
                + "# ===========================================================================\n";
            AtomicYamlWriter.writeYamlAtomically(configFile, header, live, logger);
            if (logger != null && addedMissingKeys) {
                logger.info("Updated config.yml with newly added configuration keys.");
            }
        }

        return fromYaml(live);
    }

    public static BosketConfig fromYaml(YamlConfiguration cfg) {
        int version = cfg.getInt("config-version", 3);

        General general = new General(
            nonBlank(cfg.getString("general.server-domain"), "bosketball.mc-hosting.xyz"),
            nonBlank(cfg.getString("general.season-name"), "ꜱᴇᴀꜱᴏɴ ɪ"),
            cfg.getBoolean("general.auto-equip-lobby-playbook", true),
            Math.clamp(cfg.getInt("general.starting-coins", 100), 0, 1_000_000),
            nonBlank(cfg.getString("general.language"), "en").toLowerCase(java.util.Locale.ROOT)
        );

        Lobby lobby = new Lobby(
            cfg.getBoolean("lobby.spawn.enforce", true),
            lobbyPoint(cfg, "lobby.spawn", "world", 89.5, 63.0, 61.5, -90.0f, 0.0f),
            cfg.getBoolean("lobby.stations.enforce", true),
            lobbyPoint(cfg, "lobby.stations.play", "world", 119.5, 62.0, 56.5, 79.0f, -1.1f),
            lobbyPoint(cfg, "lobby.stations.shop", "world", 119.5, 62.0, 66.5, 103.5f, -0.5f),
            new StationsExtra(
                cfg.getBoolean("lobby.stations-extra.enforce", true),
                stationSpec(cfg, "lobby.stations-extra.crate", "world", 120.5, 62.0, 61.5, "west", 1.0f),
                stationSpec(cfg, "lobby.stations-extra.tutorial", "world", 101.5, 61.0, 61.5, "west", 2.0f),
                stationSpec(cfg, "lobby.stations-extra.hall-of-fame", "world", 0.5, 64.0, 0.5, "south", 2.0f)
            )
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
            nonBlank(cfg.getString("motd.maintenance-subtitle"), "<#F87171>⚠ <bold>SCHEDULED MAINTENANCE IN PROGRESS</bold> ⚠</#F87171>"),
            List.copyOf(cfg.getStringList("motd.maintenance-whitelist")),
            nonBlank(cfg.getString("motd.maintenance-kick-message"),
                "<#F87171><bold>⚠ SCHEDULED MAINTENANCE ⚠</bold></#F87171>\n\n"
                    + "<#CBD5E1>We are upgrading the courts right now.</#CBD5E1>\n"
                    + "<#94A3B8>The server reopens shortly — thanks for your patience!</#94A3B8>")
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
            nonBlank(cfg.getString("welcome.subtitle"), "Queue 1v1–5v5, earn Coins on the court & unlock cosmetics in /shop.")
        );

        Links links = new Links(
            cfg.getString("links.website", "").trim(),
            cfg.getString("links.shop", "").trim(),
            nonBlank(cfg.getString("links.discord"), "https://discord.gg/pnJhKuU2QK")
        );

        ResourcePack rp = new ResourcePack(
            cfg.getBoolean("resource-pack.auto-send-on-join", true),
            cfg.getBoolean("resource-pack.required", false),
            cfg.getBoolean("resource-pack.embedded-http-server.enabled", true),
            nonBlank(cfg.getString("resource-pack.embedded-http-server.bind-address"), "0.0.0.0"),
            Math.clamp(cfg.getInt("resource-pack.embedded-http-server.port", 8765), 1, 65535),
            nonBlank(cfg.getString("resource-pack.embedded-http-server.public-host"), "auto"),
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
            cfg.getBoolean("match.goal-replay-enabled", true),
            Math.clamp(cfg.getInt("match.remake-window-seconds", 60), 0, 600),
            Math.clamp(cfg.getInt("match.remake-min-players", 2), 1, 10)
        );

        Practice practice = new Practice(
            nonBlank(cfg.getString("practice.world-name"), "bosketball_practice"),
            Math.clamp(cfg.getInt("practice.max-instances", 64), 1, 256),
            Math.clamp(cfg.getInt("practice.grid-spacing-blocks", 200), 96, 1024)
        );

        SchematicCourts schematicCourts = new SchematicCourts(
            cfg.getBoolean("schematic-courts.enabled", true),
            nonBlank(cfg.getString("schematic-courts.schematic"), "terrain.schem"),
            nonBlank(cfg.getString("schematic-courts.world-name"), "bosketball_courts"),
            cfg.getInt("schematic-courts.instances", SchematicCourts.DEFAULT_INSTANCES),
            cfg.getInt("schematic-courts.spacing-blocks", 384),
            cfg.getInt("schematic-courts.origin-x", 100_000),
            cfg.getInt("schematic-courts.origin-y", 64),
            cfg.getInt("schematic-courts.origin-z", 100_000),
            cfg.getDouble("schematic-courts.center-offset-x", 0.0),
            cfg.getDouble("schematic-courts.center-offset-z", 0.0),
            nonBlank(cfg.getString("schematic-courts.facing", "west"), "west"),
            cfg.getDouble("schematic-courts.half-width", 9.0),
            cfg.getDouble("schematic-courts.half-length", 15.0),
            nonBlank(cfg.getString("schematic-courts.hoop-facing", "north-south"), "north-south"),
            nonBlank(cfg.getString("schematic-courts.rim-material", "OCHRE_FROGLIGHT"), "OCHRE_FROGLIGHT"),
            cfg.getInt("schematic-courts.floor-relative-y", 0),
            cfg.getInt("schematic-courts.rim-relative-y", 3),
            cfg.getDouble("schematic-courts.rim-inset", 1.25),
            cfg.getBoolean("schematic-courts.flush-floor", true),
            cfg.getBoolean("schematic-courts.skip-empty-air", true),
            cfg.getBoolean("schematic-courts.paste-on-enable", true),
            cfg.getBoolean("schematic-courts.placeholder-until-pasted", true),
            cfg.getInt("schematic-courts.max-blocks-per-tick", 40_000),
            cfg.getInt("schematic-courts.unpaste-delay-seconds", 30),
            cfg.getBoolean("schematic-courts.retire-legacy-practice-world", true)
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
            Math.max(0, cfg.getInt("economy.gg-reward-coins", 5)),
            Math.clamp(cfg.getDouble("economy.casual-reward-multiplier", 0.40), 0.0, 2.0)
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
            List.copyOf(keywords),
            cfg.getBoolean("chat.match-chat-isolated", true)
        );

        Storage storage = new Storage(
            Math.clamp(cfg.getInt("storage.auto-save-interval-seconds", 30), 5, 600),
            Math.clamp(cfg.getInt("storage.max-match-history", 15), 1, 100),
            nonBlank(cfg.getString("storage.backend"), "yaml").toLowerCase(java.util.Locale.ROOT),
            nonBlank(cfg.getString("storage.database.sqlite-file"), "bosketball.db"),
            nonBlank(cfg.getString("storage.database.host"), "127.0.0.1"),
            Math.clamp(cfg.getInt("storage.database.port", 3306), 1, 65535),
            nonBlank(cfg.getString("storage.database.name"), "bosketball"),
            nonBlank(cfg.getString("storage.database.username"), "root"),
            cfg.getString("storage.database.password", ""),
            Math.clamp(cfg.getInt("storage.database.pool-size", 4), 1, 32),
            Math.clamp(cfg.getInt("storage.database.sync-interval-seconds", 60), 10, 3600),
            cfg.getBoolean("storage.backups.enabled", true),
            Math.clamp(cfg.getInt("storage.backups.interval-hours", 12), 1, 168),
            Math.clamp(cfg.getInt("storage.backups.keep", 10), 1, 100)
        );

        Matchmaking matchmaking = new Matchmaking(
            cfg.getBoolean("matchmaking.elo-weighted", true),
            Math.clamp(cfg.getInt("matchmaking.elo-window-start", 150), 0, 5000),
            Math.clamp(cfg.getInt("matchmaking.elo-window-growth", 50), 0, 1000),
            Math.clamp(cfg.getInt("matchmaking.expand-every-seconds", 10), 1, 300),
            Math.clamp(cfg.getInt("matchmaking.elo-window-max", 600), 0, 10_000),
            Math.clamp(cfg.getInt("matchmaking.max-wait-seconds", 180), 5, 3600),
            cfg.getBoolean("matchmaking.accept-prompt.enabled", true),
            Math.clamp(cfg.getInt("matchmaking.accept-prompt.timeout-seconds", 12), 3, 60),
            Math.clamp(cfg.getInt("matchmaking.dodge-penalty-seconds", 180), 0, 86_400),
            Math.clamp(cfg.getInt("matchmaking.dodge-penalty-multiplier", 2), 1, 10),
            Math.clamp(cfg.getInt("matchmaking.dodge-counter-reset-minutes", 60), 1, 1440),
            Math.clamp(cfg.getInt("matchmaking.backfill-bot-after-seconds", 90), 0, 3600),
            cfg.getBoolean("matchmaking.fill-bots-on-launch", true),
            cfg.getString("matchmaking.bot-difficulty", "normal")
        );

        List<Integer> streakRewards = new java.util.ArrayList<>();
        for (int v : cfg.getIntegerList("progression.daily-login.streak-rewards")) {
            streakRewards.add(Math.max(0, v));
        }
        if (streakRewards.isEmpty()) {
            streakRewards = List.of(40, 55, 70, 90, 110, 140, 200);
        }
        Progression progression = new Progression(
            cfg.getBoolean("progression.enabled", true),
            cfg.getBoolean("progression.quests.enabled", true),
            Math.clamp(cfg.getInt("progression.quests.daily-count", 3), 1, 6),
            Math.clamp(cfg.getInt("progression.quests.weekly-count", 3), 1, 6),
            Math.clamp(cfg.getInt("progression.quests.contract-count", 2), 1, 4),
            Math.clamp(cfg.getInt("progression.quests.contract-reroll-cost", 75), 0, 100_000),
            cfg.getBoolean("progression.quests.announce-completions", true),
            cfg.getBoolean("progression.achievements.enabled", true),
            cfg.getBoolean("progression.achievements.broadcast-unlocks", false),
            cfg.getBoolean("progression.daily-login.enabled", true),
            List.copyOf(streakRewards)
        );

        Crates crates = new Crates(
            cfg.getBoolean("crates.enabled", true),
            Math.clamp(cfg.getInt("crates.cost-coins", 250), 0, 1_000_000),
            Math.clamp(cfg.getInt("crates.daily-limit", 5), 1, 100),
            Math.clamp(cfg.getInt("crates.cooldown-seconds", 3), 0, 600),
            Math.clamp(cfg.getInt("crates.coin-min", 60), 0, 1_000_000),
            Math.clamp(cfg.getInt("crates.coin-max", 500), 0, 1_000_000),
            Math.clamp(cfg.getDouble("crates.cosmetic-chance", 0.22), 0.0, 1.0),
            Math.clamp(cfg.getDouble("crates.bonus-chance", 0.15), 0.0, 1.0),
            Math.max(0, cfg.getInt("crates.bonus-coins", 150))
        );

        Moderation moderation = new Moderation(
            cfg.getBoolean("moderation.profanity-filter.enabled", true),
            nonBlank(cfg.getString("moderation.profanity-filter.censor-character"), "█"),
            Math.clamp(cfg.getInt("moderation.profanity-filter.warn-points", 1), 1, 3),
            Math.clamp(cfg.getInt("moderation.profanity-filter.severe-warn-points", 2), 1, 3),
            cfg.getBoolean("moderation.profanity-filter.notify-author", true),
            cfg.getBoolean("moderation.profanity-filter.exempt-staff", true),
            Math.clamp(cfg.getInt("moderation.auto-ban-at-warnings", 3), 2, 10),
            Math.clamp(cfg.getInt("moderation.ban-duration-hours", 24), 1, 8760),
            nonBlank(cfg.getString("moderation.ban-reason"), "Inappropriate language (3/3 chat warnings)"),
            cfg.getBoolean("moderation.anti-spam.enabled", true),
            Math.clamp(cfg.getInt("moderation.anti-spam.min-seconds-between-messages", 1), 0, 60),
            Math.clamp(cfg.getInt("moderation.anti-spam.messages-per-window", 5), 1, 50),
            Math.clamp(cfg.getInt("moderation.anti-spam.window-seconds", 10), 1, 300),
            Math.clamp(cfg.getInt("moderation.anti-spam.mute-seconds", 20), 1, 3600),
            cfg.getBoolean("moderation.anti-spam.block-repeated-messages", true),
            cfg.getBoolean("moderation.block-caps", true),
            Math.clamp(cfg.getInt("moderation.caps-min-length", 8), 3, 64),
            Math.clamp(cfg.getInt("moderation.default-mute-minutes", 30), 1, 10_080)
        );

        Discord discord = new Discord(
            cfg.getBoolean("discord.enabled", true),
            cfg.getString("discord.webhook-url", "").trim(),
            cfg.getBoolean("discord.post-match-start", true),
            cfg.getBoolean("discord.post-score-updates", true),
            cfg.getBoolean("discord.post-match-results", true),
            cfg.getBoolean("discord.post-rank-ups", true),
            cfg.getBoolean("discord.post-crate-openings", true),
            cfg.getBoolean("discord.post-reports", true),
            cfg.getBoolean("discord.post-moderation-actions", true),
            cfg.getString("discord.player-head-url", "").trim()
        );

        Leaderboards leaderboards = new Leaderboards(
            Math.clamp(cfg.getInt("leaderboards.holo-refresh-seconds", 5), 1, 300),
            cfg.getBoolean("leaderboards.enable-category-arrows", true),
            Math.clamp(cfg.getInt("leaderboards.cache-size", 10), 3, 50)
        );

        Tutorial tutorial = new Tutorial(
            cfg.getBoolean("tutorial.enabled", true),
            cfg.getBoolean("tutorial.force-on-first-join", false),
            cfg.getBoolean("tutorial.hologram-enabled", true),
            Math.max(0, cfg.getInt("tutorial.completion-coins", 250)),
            Math.max(0, cfg.getInt("tutorial.completion-xp", 150)),
            cfg.getBoolean("tutorial.use-practice-court", true)
        );

        Guilds guilds = new Guilds(
            cfg.getBoolean("guilds.enabled", true),
            Math.max(0, cfg.getInt("guilds.create-cost-coins", 1000)),
            Math.clamp(cfg.getInt("guilds.max-members", 12), 2, 100),
            Math.clamp(cfg.getInt("guilds.tag-max-length", 6), 2, 12),
            Math.clamp(cfg.getInt("guilds.name-min-length", 3), 2, 32),
            Math.clamp(cfg.getInt("guilds.name-max-length", 16), 4, 32),
            Math.clamp(cfg.getInt("guilds.starting-elo", 1000), 0, 5000)
        );

        I18n i18n = new I18n(
            cfg.getBoolean("i18n.enabled", true),
            cfg.getString("i18n.default-locale", "en"),
            cfg.getBoolean("i18n.translate-chat-broadcasts", true)
        );

        return new BosketConfig(
            version, general, lobby, motd, protection, welcome, links, rp, match, practice, schematicCourts,
            economy, physics, chat, storage, matchmaking, progression, crates, moderation, discord,
            leaderboards, tutorial, guilds, i18n
        );
    }

    private static LobbyPoint lobbyPoint(
        YamlConfiguration cfg, String path, String defaultWorld,
        double defX, double defY, double defZ, float defYaw, float defPitch
    ) {
        return new LobbyPoint(
            nonBlank(cfg.getString(path + ".world"), defaultWorld),
            Math.clamp(cfg.getDouble(path + ".x", defX), -30_000_000.0, 30_000_000.0),
            Math.clamp(cfg.getDouble(path + ".y", defY), -512.0, 1024.0),
            Math.clamp(cfg.getDouble(path + ".z", defZ), -30_000_000.0, 30_000_000.0),
            (float) Math.clamp(cfg.getDouble(path + ".yaw", defYaw), -360.0, 360.0),
            (float) Math.clamp(cfg.getDouble(path + ".pitch", defPitch), -90.0, 90.0)
        );
    }

    private static StationSpec stationSpec(
        YamlConfiguration cfg, String path, String defaultWorld,
        double defX, double defY, double defZ, String defFacing, float defLift
    ) {
        return new StationSpec(
            cfg.getBoolean(path + ".enabled", true),
            nonBlank(cfg.getString(path + ".world"), defaultWorld),
            Math.clamp(cfg.getDouble(path + ".x", defX), -30_000_000.0, 30_000_000.0),
            Math.clamp(cfg.getDouble(path + ".y", defY), -512.0, 1024.0),
            Math.clamp(cfg.getDouble(path + ".z", defZ), -30_000_000.0, 30_000_000.0),
            nonBlank(cfg.getString(path + ".facing"), defFacing).toLowerCase(java.util.Locale.ROOT),
            (float) Math.clamp(cfg.getDouble(path + ".lift", defLift), 0.0, 12.0)
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
