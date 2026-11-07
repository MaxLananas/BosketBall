package fr.bosketball;

import fr.bosketball.arena.ArenaManager;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.arena.MapEditorManager;
import fr.bosketball.arena.PracticeArenaPool;
import fr.bosketball.command.BosketCommand;
import fr.bosketball.command.DiscordCommand;
import fr.bosketball.command.GgCommand;
import fr.bosketball.config.BosketConfig;
import fr.bosketball.config.ShopConfig;
import fr.bosketball.game.GameManager;
import fr.bosketball.listener.GameplayListener;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.pack.ResourcePackHostService;
import fr.bosketball.server.MotdManager;
import fr.bosketball.server.PingDiagnostics;
import fr.bosketball.server.PermissionManager;
import fr.bosketball.server.ServerCoreCommands;
import fr.bosketball.server.ServerProtectionManager;
import fr.bosketball.social.SocialPartyManager;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Msg;
import fr.bosketball.util.TickProfiler;
import fr.bosketball.ui.BosketChatRenderer;
import fr.bosketball.ui.BossBarHudManager;
import fr.bosketball.ui.CinematicCameraManager;
import fr.bosketball.ui.GuiMenuManager;
import fr.bosketball.ui.LobbyHologramManager;
import fr.bosketball.ui.ScoreboardSidebarManager;
import fr.bosketball.ui.TabListManager;
import java.io.File;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class BosketBallPlugin extends JavaPlugin {

    private static BosketBallPlugin instance;

    private volatile BosketConfig settings = BosketConfig.defaults();
    private volatile boolean globalChatMuted = false;
    private PermissionManager permissionManager;
    private ServerProtectionManager protectionManager;
    private MotdManager motdManager;
    private StatsManager statsManager;
    private SocialPartyManager socialManager;
    private ArenaManager arenaManager;
    private PracticeArenaPool practicePool;
    /** Round 29: set as soon as the server starts stopping (see {@link #isShuttingDown()}). */
    private volatile boolean shuttingDown;

    private MapEditorManager mapEditor;
    private BossBarHudManager bossBarHud;
    private ScoreboardSidebarManager sidebarManager;
    private fr.bosketball.stress.StressTestService stressTestService;
    private TabListManager tabListManager;
    private fr.bosketball.ui.NameplateService nameplateService;
    private PingDiagnostics pingDiagnostics;
    private BosketChatRenderer chatRenderer;
    private CinematicCameraManager cinematicCamera;
    private GuiMenuManager guiMenu;
    private LobbyHologramManager lobbyHolograms;
    private ResourcePackHostService resourcePackHost;
    private GameManager gameManager;
    private ServerCoreCommands coreCommands;
    private fr.bosketball.social.SocialService socialService;
    private fr.bosketball.social.GuildService guildService;
    private fr.bosketball.progression.ProgressService progressService;
    private fr.bosketball.season.SeasonService seasonService;
    private fr.bosketball.moderation.ChatModerationService moderationService;
    private fr.bosketball.integration.IntegrationService integration;

    private fr.bosketball.integration.DailyDigestService dailyDigest;
    private fr.bosketball.moderation.ReportService reportService;
    private fr.bosketball.moderation.WarnService warnService;

    private fr.bosketball.moderation.StaffToolsManager staffToolsManager;
    private fr.bosketball.ui.TutorialManager tutorialManager;
    private fr.bosketball.lobby.RewardCrateStation crateStation;
    private fr.bosketball.lobby.HallOfFamePodium hallOfFame;
    private fr.bosketball.i18n.LangService lang;
    private fr.bosketball.arena.schematic.SchematicCourtPool schematicCourtPool;

    private int uiTickCounter = 0;

    private final java.util.Map<java.util.UUID, Boolean> announcedAfk = new java.util.concurrent.ConcurrentHashMap<>();

    public static BosketBallPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        purgeOrphanedPluginEntities();

        this.settings = BosketConfig.loadAndUpgrade(new File(getDataFolder(), "config.yml"), getLogger());
        fr.bosketball.stats.StatsManager.setDefaultLanguage(settings.general().language());
        ShopConfig.loadOrCreate(getDataFolder(), getLogger());

        this.permissionManager = new PermissionManager(this);
        this.protectionManager = new ServerProtectionManager(this);
        this.motdManager = new MotdManager(this);

        this.statsManager = new StatsManager(getDataFolder(), getLogger());
        this.statsManager.load();
        this.socialManager = new SocialPartyManager(statsManager);

        this.socialService = new fr.bosketball.social.SocialService(statsManager, getDataFolder());
        this.guildService = new fr.bosketball.social.GuildService(this);
        this.progressService = new fr.bosketball.progression.ProgressService(statsManager);
        this.moderationService = new fr.bosketball.moderation.ChatModerationService(this);
        this.reportService = new fr.bosketball.moderation.ReportService(this);
        this.warnService = new fr.bosketball.moderation.WarnService(this);

        this.arenaManager = new ArenaManager(getDataFolder(), getLogger());
        this.arenaManager.load();
        this.practicePool = new PracticeArenaPool(this);
        this.mapEditor = new MapEditorManager(this);
        this.schematicCourtPool = new fr.bosketball.arena.schematic.SchematicCourtPool(this);

        this.bossBarHud = new BossBarHudManager();
        this.gameManager = new GameManager(this);
        this.cinematicCamera = new CinematicCameraManager(this);
        this.guiMenu = new GuiMenuManager(this);
        this.lobbyHolograms = new LobbyHologramManager(this);
        this.resourcePackHost = new ResourcePackHostService(getDataFolder(), getLogger());
        this.integration = new fr.bosketball.integration.IntegrationService(this);
        this.tutorialManager = new fr.bosketball.ui.TutorialManager(this);
        this.crateStation = new fr.bosketball.lobby.RewardCrateStation(this);
        this.hallOfFame = new fr.bosketball.lobby.HallOfFamePodium(this);
        this.lang = new fr.bosketball.i18n.LangService(this);

        this.chatRenderer = new BosketChatRenderer(
            this,
            statsManager,
            gameManager,
            guildService,
            settings.chat().highlightKeywords(),
            settings.chat().showTopStatBadges()
        );

        reloadBosketConfig();

        if (!schematicCourtPool.bootstrap()) {
            getLogger().info("[Courts] Instanced schematic courts unavailable — generated stadiums will be used.");
        }
        lobbyHolograms.loadAndSpawnAll();
        protectionManager.applyEnforcedSpawnToWorld();
        protectionManager.enforceAllWorldRules();
        bootstrapAlreadyOnlinePlayers(settings.general().startingCoins());
        statsManager.attachLeaderboardIndex(
            settings.leaderboards().cacheSize(),
            settings.leaderboards().holoRefreshSeconds()
        );
        this.seasonService = new fr.bosketball.season.SeasonService(this);
        this.seasonService.load();
        this.seasonService.start();
        integration.reload();
        this.dailyDigest = new fr.bosketball.integration.DailyDigestService(this);
        this.dailyDigest.start();

        crateStation.spawn();
        tutorialManager.spawnHologram();
        Bukkit.getScheduler().runTaskLater(this, () -> resourcePackHost.ensureNettyMuxHooked(), 1L);

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(permissionManager, this);
        pm.registerEvents(new GameplayListener(this, chatRenderer), this);
        pm.registerEvents(guiMenu, this);
        pm.registerEvents(protectionManager, this);
        pm.registerEvents(motdManager, this);
        pm.registerEvents(moderationService, this);
        pm.registerEvents(tutorialManager, this);
        pm.registerEvents(new fr.bosketball.listener.AfkListener(this), this);
        this.staffToolsManager = new fr.bosketball.moderation.StaffToolsManager(this);
        pm.registerEvents(staffToolsManager, this);

        BosketCommand mainCmd = new BosketCommand(this);

        for (String shortcut : new String[]{
            "bosketball", "play", "practice", "locker", "leave", "shop", "ranks", "friend", "party", "duel",
            "accept", "decline", "pc", "tuto", "quests", "achievements", "report", "crate", "guild", "ignore",
            "reports", "status", "afk", "backup", "warn", "unwarn", "warnings",
            "giveelo", "addelo", "mail", "season"
        }) {
            PluginCommand cmd = getCommand(shortcut);
            if (cmd != null) {
                cmd.setExecutor(mainCmd);
                cmd.setTabCompleter(mainCmd);
            }
        }
        PluginCommand gg = getCommand("gg");
        if (gg != null) {
            gg.setExecutor(new GgCommand(this));
        }
        PluginCommand discord = getCommand("discord");
        if (discord != null) {
            discord.setExecutor(new DiscordCommand(this));
        }

        this.coreCommands = new ServerCoreCommands(this);
        for (String cmdName : new String[]{"help", "pack", "build", "spawn", "setspawn", "perm", "msg", "reply"}) {
            PluginCommand cmd = getCommand(cmdName);
            if (cmd != null) {
                cmd.setExecutor(this.coreCommands);
                cmd.setTabCompleter(this.coreCommands);
            }
        }

        scheduleUiTask();
        // A single exception thrown by any subsystem used to cancel the shared UI task for good
        // (Bukkit never restarts a repeating task), which silently killed the sidebars, the tab
        // list and the lobby leaderboards until the next restart. Every step is now guarded.
        Bukkit.getScheduler().runTaskLater(this, () -> reconcileDisplays(true), 40L);
        Bukkit.getScheduler().runTaskTimer(this, this::ensureDisplayPipeline, 200L, 200L);

        getLogger().info("BosketBall enabled (" + arenaManager.allArenas().size()
            + " arenas, " + statsManager.allProfiles().size() + " profiles loaded).");
    }

    // ======================================================================
    //  Display pipeline (sidebars, tab list, lobby leaderboards, hall of fame)
    // ======================================================================

    private static final java.util.logging.Level UI_FAULT_LEVEL = java.util.logging.Level.WARNING;

    private BukkitTask uiTask;
    private final java.util.Map<String, Integer> uiFaults = new java.util.concurrent.ConcurrentHashMap<>();

    /** Schedules the 1-tick UI pipeline; a no-op while it is already running. */
    private void scheduleUiTask() {
        if (uiTask != null && !uiTask.isCancelled()) {
            return;
        }
        uiTask = Bukkit.getScheduler().runTaskTimer(this, this::uiTick, 1L, 1L);
    }

    /** Watchdog: Bukkit cancels repeating tasks on exception, so make sure ours is still alive. */
    private void ensureDisplayPipeline() {
        if (uiTask == null || uiTask.isCancelled()) {
            getLogger().severe("[Display] The UI task had stopped (cancelled repeating task) — restarting it now.");
            uiTask = null;
            scheduleUiTask();
            reconcileDisplays(true);
            return;
        }
        if (!Bukkit.getOnlinePlayers().isEmpty()
            && lobbyHolograms != null
            && lobbyHolograms.countNeedingRespawn() > 0) {
            int fixed = lobbyHolograms.ensureAllSpawned();
            if (fixed > 0) {
                getLogger().info("[Display] Re-spawned " + fixed + " missing lobby leaderboard/NPC entit"
                    + (fixed == 1 ? "y" : "ies") + ".");
            }
        }
    }

    /**
     * One guarded UI step. Failures are counted per subsystem and logged once, so a broken
     * subsystem can never take the whole display pipeline down with it.
     */
    private void step(String key, Runnable body) {
        long started = TickProfiler.begin(key);
        try {
            body.run();
        } catch (Throwable failure) {
            int count = uiFaults.merge(key, 1, Integer::sum);
            if (count == 1) {
                getLogger().log(UI_FAULT_LEVEL, "[Display] UI step '" + key
                    + "' failed once — the rest of the UI (sidebar, tab, leaderboards) keeps running."
                    + " Paste this stack trace in the bug report.", failure);
            } else if (count % 1200 == 0) {
                getLogger().log(UI_FAULT_LEVEL, "[Display] UI step '" + key + "' still failing ("
                    + count + " times since startup).");
            }
        } finally {
            TickProfiler.end(key, started);
        }
    }

    private void uiTick() {
        TickProfiler.beginTick();
        try {
            step("match", gameManager::tickAll);
            step("schematic", schematicCourtPool::tick);
            step("cinematic", cinematicCamera::tickAll);
            step("tutorial", tutorialManager::tickAll);
            step("crate", crateStation::tick);
            if (hallOfFame != null) {
                step("halloffame", hallOfFame::tick);
            }
            if (uiTickCounter % 2 == 0) {
                step("holo-anim", lobbyHolograms::tickAnimation);
            }

            uiTickCounter++;
            if (uiTickCounter % 20 == 0) {
                step("playtime", () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        var tp = statsManager.getIfPresent(p.getUniqueId());
                        if (tp == null) {
                            tp = statsManager.getOrCreate(p);
                        }
                        tp.addPlayTimeSeconds(1L);
                        if (tp.playTimeSeconds() % 300L == 0L) {
                            statsManager.markDirty(p.getUniqueId());
                        }
                    }
                });
            }
            if (uiTickCounter % 10 == 0) {
                step("map-editor", mapEditor::tickPreviews);
                step("sidebar", () -> sidebarManager.updateAllSidebars(gameManager));
            }
            if (nameplateService != null) {
                // Round 37: always ticked, so switching nameplates off with /bb reload tears the
                // existing displays down instead of leaving stale plates above every head.
                step("nameplate", nameplateService::tickAll);
            }
            if (uiTickCounter % 20 == 0) {
                if (pingDiagnostics != null) {
                    step("net", pingDiagnostics::sample);
                }
                if (resourcePackHost != null) {
                    step("pack-watch", resourcePackHost::tickSlowDownloads);
                }
            }
            if (uiTickCounter % 30 == 0) {
                step("tablist", () -> tabListManager.updateAll(gameManager));
            }
            if (uiTickCounter % 100 == 0 && socialService != null) {
                step("afk", this::tickAfkAnnouncements);
            }
            int autoSaveTicks = Math.max(100, settings.storage().autoSaveIntervalSeconds() * 20);
            if (uiTickCounter % autoSaveTicks == 0) {
                step("autosave", () -> statsManager.flushDirtyAsync(false));
            }
        } finally {
            TickProfiler.endTick();
        }
    }

    private void tickAfkAnnouncements() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean afk = socialService.isAfk(p.getUniqueId());
            Boolean was = announcedAfk.put(p.getUniqueId(), afk);
            if (afk && !Boolean.TRUE.equals(was)) {
                if (gameManager.leaveQueue(p, false)) {
                    Msg.send(p, Msg.warn("You were AFK — removed from the queue. Use <white>/play</white> when you are back."));
                } else {
                    Msg.send(p, Msg.info("<#64748B>\u25cc You are now <white><bold>AFK</bold></white> — move or type to come back.</#64748B>"));
                }
            } else if (!afk && Boolean.TRUE.equals(was)) {
                Msg.send(p, Msg.success("<#4ADE80>\u25cf Welcome back!</#4ADE80>"));
            }
        }
        announcedAfk.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    /**
     * Re-applies every display layer: re-spawns missing lobby hologram entities, rebuilds the
     * sidebar and the tab list for everyone online, and reports what happened. Called 2 s after
     * startup, after /bb reload and by /bb displays repair.
     */
    public int reconcileDisplays(boolean announce) {
        int respawned = 0;
        if (lobbyHolograms != null) {
            respawned = lobbyHolograms.ensureAllSpawned();
        }
        int touched = 0;
        if (gameManager != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    if (sidebarManager != null) {
                        sidebarManager.clearPlayerCache(p.getUniqueId());
                        sidebarManager.updatePlayerSidebar(p, gameManager);
                    }
                    if (tabListManager != null) {
                        tabListManager.clearPlayerCache(p.getUniqueId());
                    }
                    touched++;
                } catch (RuntimeException failure) {
                    getLogger().log(UI_FAULT_LEVEL, "[Display] Could not refresh the displays of " + p.getName(), failure);
                }
            }
            if (tabListManager != null) {
                tabListManager.updateAll(gameManager);
            }
        }
        boolean alive = uiTask != null && !uiTask.isCancelled();
        int instances = lobbyHolograms == null ? 0 : lobbyHolograms.allHolograms().size();
        if (announce || respawned > 0) {
            getLogger().info("[Display] UI task " + (alive ? "alive" : "stopped") + " \u00b7 "
                + instances + " lobby hologram(s) \u00b7 " + respawned + " re-spawned \u00b7 sidebar re-applied to "
                + touched + " player(s)");
        }
        return respawned;
    }

    public boolean uiTaskAlive() {
        return uiTask != null && !uiTask.isCancelled();
    }

    public java.util.Map<String, Integer> uiFaultCounts() {
        return java.util.Map.copyOf(uiFaults);
    }

    public void reloadBosketConfig() {
        saveDefaultConfig();
        reloadConfig();
        this.settings = BosketConfig.loadAndUpgrade(new File(getDataFolder(), "config.yml"), getLogger());
        fr.bosketball.stats.StatsManager.setDefaultLanguage(settings.general().language());
        ShopConfig.loadOrCreate(getDataFolder(), getLogger());
        if (this.seasonService != null) {
            this.seasonService.reload();
        }

        if (this.permissionManager != null) {
            this.permissionManager.loadOrCreate();
        }
        if (this.protectionManager != null) {
            this.protectionManager.loadSpawn();
            this.protectionManager.applyEnforcedSpawnToWorld();
            this.protectionManager.enforceAllWorldRules();
        }
        if (this.lobbyHolograms != null) {
            this.lobbyHolograms.applyConfiguredStations();
        }
        if (this.motdManager != null) {
            this.motdManager.reloadIcon();
        }

        this.sidebarManager = new ScoreboardSidebarManager(statsManager, socialManager);
        this.stressTestService = new fr.bosketball.stress.StressTestService(this);
        try {
            fr.bosketball.ops.OpsAudit.Snapshot auditSnap = fr.bosketball.ops.OpsAudit.snapshotOf(settings);
            java.util.List<String> auditFindings = fr.bosketball.ops.OpsAudit.audit(auditSnap);
            if (auditFindings.isEmpty()) {
                getLogger().info("[Audit] RAS - configuration and load are healthy.");
            } else {
                getLogger().info("[Audit] " + fr.bosketball.ops.OpsAudit.summary(auditSnap)
                    + " - details with /bb audit (AxiomPaper/FAWE are kept, not flagged):");
                for (String finding : auditFindings) {
                    getLogger().info("[Audit] " + finding.replaceAll("<[^>]+>", ""));
                }
            }
        } catch (RuntimeException ignored) {
            // never block startup on a diagnostic
        }
        fr.bosketball.stress.StressTestService.purgeStressEntities();
        this.tabListManager = new TabListManager(
            statsManager,
            settings.general().seasonName(),
            settings.general().serverDomain()
        );
        this.tabListManager.setPanel(
            settings.tabList().frame(),
            settings.tabList().railLines()
        );
        this.sidebarManager.attachSeasonProvider(profile -> {
            fr.bosketball.season.SeasonService season = seasonService;
            if (season == null || !season.enabled() || season.state() == null) {
                return "<#94A3B8>Season off</#94A3B8>";
            }
            String remaining = "<gray>" + season.state().remainingLabel() + " left</gray>";
            if (season.isProvisional(profile.uuid())) {
                int done = Math.max(0, season.placementsRequired() - season.placementsRemaining(profile.uuid()));
                return "<#FBBF24>Placements " + done + "/" + season.placementsRequired() + "</#FBBF24> <dark_gray>·</dark_gray> " + remaining;
            }
            int best = season.bestElo(profile.uuid());
            fr.bosketball.model.Rank tier = fr.bosketball.season.SeasonMath.tierFor(best > 0 ? best : profile.elo());
            return "<#4ADE80>" + tier.plainDisplayName() + "</#4ADE80> <dark_gray>·</dark_gray> " + remaining;
        });
        this.tabListManager.attachSeasonProvider(
            () -> seasonService == null ? settings.general().seasonName() : seasonService.footerLabel(),
            id -> {
                fr.bosketball.season.SeasonService season = seasonService;
                if (season == null || !season.enabled() || !season.isProvisional(id)) {
                    return null;
                }
                int done = Math.max(0, season.placementsRequired() - season.placementsRemaining(id));
                return "<#FBBF24><bold>PLACEMENT " + done + "/" + season.placementsRequired() + "</bold></#FBBF24>";
            }
        );
        if (this.socialService != null) {

            this.tabListManager.attachPresenceProvider(this.socialService::statusOf);
            this.tabListManager.attachGuildProvider(id -> guildService == null ? null : guildService.guildOf(id));
        }
        this.nameplateService = new fr.bosketball.ui.NameplateService(this, statsManager);
        this.pingDiagnostics = new PingDiagnostics(getLogger(), resourcePackHost, gameManager);
        if (this.chatRenderer != null) {
            this.chatRenderer.updateConfig(
                settings.chat().highlightKeywords(),
                settings.chat().showTopStatBadges()
            );
        }
        if (statsManager.leaderboardIndex() != null) {
            statsManager.leaderboardIndex().configure(
                settings.leaderboards().cacheSize(), settings.leaderboards().holoRefreshSeconds());
            statsManager.leaderboardIndex().start();
        }
        if (integration != null) {
            integration.reload();
        }
        if (dailyDigest != null) {
            dailyDigest.start();
        }
        if (crateStation != null) {
            crateStation.spawn();
        }
        if (guildService != null) {
            guildService.load();
        }
        if (hallOfFame != null) {
            hallOfFame.load();
        }
        tutorialManager.spawnHologram();
        scheduleUiTask();
        reconcileDisplays(false);

        BosketConfig.ResourcePack rp = settings.resourcePack();
        this.resourcePackHost.initialize(
            rp.httpEnabled(),
            rp.bindAddress(),
            rp.port(),
            rp.publicHost(),
            rp.externalUrl(),
            rp.externalSha1(),
            rp.required(),
            rp.mirrors()
        );
    }

    /**
     * Round 29: true once the server started stopping. The server-list banner reads it to swap to
     * the dedicated "restarting" MOTD, so the last pings before shutdown stay on-brand.
     */
    public boolean isShuttingDown() {
        return shuttingDown;
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        // Round 28: give everyone the branded "server closing" screen before the world goes away.
        // Only on a real stop/restart — a plugin reload must never disconnect anyone.
        kickEveryoneForShutdown();
        if (stressTestService != null) {
            stressTestService.abort("plugin disable");
            stressTestService = null;
        }
        fr.bosketball.stress.StressTestService.purgeStressEntities();
        if (seasonService != null) {
            seasonService.shutdown();
        }
        if (dailyDigest != null) {
            dailyDigest.stop();
        }
        if (cinematicCamera != null) {
            cinematicCamera.stopAll();
        }
        if (lobbyHolograms != null) {
            lobbyHolograms.despawnAllEntities();
        }
        if (nameplateService != null) {
            nameplateService.shutdown();
        }
        if (gameManager != null) {
            gameManager.shutdownAll();
        }
        if (guildService != null) {
            guildService.save();
        }
        if (crateStation != null) {
            crateStation.despawn();
        }
        if (statsManager != null && statsManager.leaderboardIndex() != null) {
            statsManager.leaderboardIndex().stop();
        }
        if (integration != null) {
            integration.shutdown();
        }
        if (bossBarHud != null) {
            bossBarHud.clearAll();
        }
        if (practicePool != null) {
            practicePool.despawnAll();
        }
        if (arenaManager != null) {
            arenaManager.despawnAll();
            arenaManager.shutdownAndSaveSync();
        }
        if (statsManager != null) {
            statsManager.shutdownAndSaveSync();
        }
        if (permissionManager != null) {
            permissionManager.shutdown();
        }
        if (resourcePackHost != null) {
            resourcePackHost.shutdown();
        }
    }

    private void purgeOrphanedPluginEntities() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity entity : w.getEntities()) {
                if (!(entity instanceof Player)) {
                    var tags = entity.getScoreboardTags();
                    if (tags.contains(HoopDisplaySet.DISPLAY_TAG) || tags.contains(LobbyHologramManager.HOLO_ENTITY_TAG)) {
                        entity.remove();
                    }
                }
            }
        }
    }

    public boolean shouldHidePlayerFrom(Player observer, Player subject) {
        if (observer == null || subject == null || observer.getUniqueId().equals(subject.getUniqueId())) {
            return false;
        }
        UUID obsId = observer.getUniqueId();
        UUID subId = subject.getUniqueId();
        if (gameManager != null) {
            var subSession = gameManager.getSessionOf(subId);
            if (subSession != null
                && subSession.getTeam(subId) == fr.bosketball.model.TeamSide.SPECTATOR
                && subSession.hasPlayer(obsId)
                && subSession.getTeam(obsId) != fr.bosketball.model.TeamSide.SPECTATOR) {
                return true;
            }
        }
        return false;
    }

    public void resyncVisibilityFor(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (shouldHidePlayerFrom(other, player)) {
                other.hidePlayer(this, player);
            } else {
                other.showPlayer(this, player);
            }
            if (shouldHidePlayerFrom(player, other)) {
                player.hidePlayer(this, other);
            } else {
                player.showPlayer(this, other);
            }
        }
    }

    public static void sanitizeResidualPlayerEffects(Player player) {
        PotionEffect invis = player.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (invis != null && invis.isAmbient() && !invis.hasParticles()) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && Math.abs(speed.getBaseValue() - 0.118) < 0.001) {
            speed.setBaseValue(0.10);
        }

        if (player.getWalkSpeed() == 0.0f && player.getFlySpeed() == 0.0f) {
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
        }
    }

    public void sanitizeJoinerToCleanLobbyState(Player player) {
        if (player == null) {
            return;
        }
        sanitizeResidualPlayerEffects(player);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        AttributeInstance maxHp = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHp != null ? maxHp.getValue() : 20.0);

        boolean creativeBuilder = player.getGameMode() == org.bukkit.GameMode.CREATIVE
            && fr.bosketball.util.Perms.build(player);
        if (!creativeBuilder) {
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR || player.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
                player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            }
            player.setAllowFlight(false);
            player.setFlying(false);
        }

        var inv = player.getInventory();
        org.bukkit.inventory.ItemStack slot9 = inv.getItem(BallSkin.HIDDEN_BOW_ARROW_SLOT);
        if (slot9 != null && slot9.getType() == org.bukkit.Material.ARROW) {
            inv.setItem(BallSkin.HIDDEN_BOW_ARROW_SLOT, null);
        }
        org.bukkit.inventory.ItemStack boots = inv.getBoots();
        if (boots != null && boots.getItemMeta() != null && boots.getItemMeta().getItemModel() != null
            && BallSkin.NAMESPACE.equals(boots.getItemMeta().getItemModel().getNamespace())) {
            inv.setBoots(null);
        }
        for (int i = 0; i < 9; i++) {
            org.bukkit.inventory.ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir() || it.getItemMeta() == null) {
                continue;
            }
            org.bukkit.NamespacedKey key = it.getItemMeta().getItemModel();
            if (it.getType() == org.bukkit.Material.BOW && key != null && BallSkin.NAMESPACE.equals(key.getNamespace())) {
                inv.setItem(i, null);
                continue;
            }
            if (key != null && BallSkin.NAMESPACE.equals(key.getNamespace())) {
                String k = key.getKey();
                if (k.equals("ref_trophy") || k.equals("icon_crossover") || k.equals("icon_defense")
                    || k.startsWith("dunk_") || k.equals("trophy_mvp") || k.equals("ref_exit")) {
                    inv.setItem(i, null);
                }
            }
        }

        String pracWorld = settings.practice().worldName();
        boolean inPracticeWorld = player.getWorld() != null && player.getWorld().getName().equalsIgnoreCase(pracWorld);
        if (protectionManager != null && (protectionManager.hasSpawnLocation() || inPracticeWorld)) {
            org.bukkit.Location spawn = protectionManager.resolveLobbySpawn(null);
            if (spawn != null && spawn.getWorld() != null) {
                player.teleport(spawn);
            }
        }

        resyncVisibilityFor(player);
    }

    public boolean isGlobalChatMuted() {
        return globalChatMuted;
    }

    public void setGlobalChatMuted(boolean muted) {
        this.globalChatMuted = muted;
    }

    /**
     * Kicks every online player with the branded shutdown screen, but only when the server is
     * actually stopping (never on a plugin reload).
     */
    private void kickEveryoneForShutdown() {
        if (!isServerStopping()) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            try {
                online.kick(fr.bosketball.server.ServerScreens.shutdown(this, online));
            } catch (Throwable ignored) {
                // the connection may already be gone
            }
        }
    }

    /** Paper's {@code Server#isStopping()} — true only during a real stop/restart. */
    private static boolean isServerStopping() {
        try {
            return Bukkit.getServer().isStopping();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean setMaintenanceModeLive(boolean enabled) {
        File cfgFile = new File(getDataFolder(), "config.yml");
        org.bukkit.configuration.file.YamlConfiguration yml = fr.bosketball.config.AtomicYamlWriter.loadSafe(cfgFile, getLogger());
        yml.set("motd.maintenance-mode", enabled);
        fr.bosketball.config.AtomicYamlWriter.writeYamlAtomically(
            cfgFile,
            "# BosketBall — Main Server Configuration (/bb reload)\n",
            yml,
            getLogger()
        );
        this.settings = BosketConfig.loadAndUpgrade(cfgFile, getLogger());
        if (enabled) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!MotdManager.canBypassMaintenance(online)) {
                    online.kick(fr.bosketball.server.ServerScreens.maintenanceStarted(this, online));
                }
            }
        }
        return enabled;
    }

    private void bootstrapAlreadyOnlinePlayers(int startingCoins) {
        boolean autoEquip = settings.general().autoEquipLobbyPlaybook();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (permissionManager != null) {
                permissionManager.applyPermissions(p);
            }
            sanitizeJoinerToCleanLobbyState(p);
            if (statsManager.getIfPresent(p.getUniqueId()) == null) {
                PlayerProfile created = statsManager.getOrCreate(p);
                created.setCoins(startingCoins);
                statsManager.saveProfileAsync(created);
            } else {
                statsManager.getOrCreate(p);
            }
            if (autoEquip) {
                BallSkin.equipLobbyHotbarIfEmpty(p, false);
            }
        }
        statsManager.refreshLeaderboardBadges();
    }

    public BosketConfig settings() {
        return settings;
    }

    public PermissionManager permissionManager() {
        return permissionManager;
    }

    public ServerProtectionManager protectionManager() {
        return protectionManager;
    }

    public MotdManager motdManager() {
        return motdManager;
    }

    public StatsManager statsManager() {
        return statsManager;
    }

    public void clearLobbyRig(UUID playerId, boolean displaysOnly) {
    }

    public CinematicCameraManager cinematicCamera() {
        return cinematicCamera;
    }

    public SocialPartyManager socialManager() {
        return socialManager;
    }

    public ArenaManager arenaManager() {
        return arenaManager;
    }

    public PracticeArenaPool practicePool() {
        return practicePool;
    }

    public MapEditorManager mapEditor() {
        return mapEditor;
    }

    public BossBarHudManager bossBarHud() {
        return bossBarHud;
    }

    public fr.bosketball.ui.NameplateService nameplateService() {
        return nameplateService;
    }

    /**
     * Releases every per-player structure the plugin holds. Called when a player disconnects.
     * Each step is isolated: one misbehaving manager must never leak the rest of the state.
     */
    public void cleanupPlayerState(Player player) {
        if (player == null) {
            return;
        }
        java.util.UUID id = player.getUniqueId();
        clean("staff-tools", () -> staffToolsManager.clearPlayer(id));
        clean("tutorial", () -> tutorialManager.clearPlayer(player));
        clean("gui", () -> guiMenu.forgetPlayer(id));
        clean("crate", () -> crateStation.clearPlayer(id));
        clean("nameplate", () -> nameplateService.forget(id));
        clean("purchase-confirm", () -> fr.bosketball.ui.PurchaseConfirmMenu.clear(player));
        clean("social", () -> socialService.clear(id));
        clean("gg", () -> fr.bosketball.command.GgCommand.forget(id));
    }

    private void clean(String step, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            getLogger().warning("[Cleanup] " + step + " failed on quit: " + failure);
        }
    }

    public ScoreboardSidebarManager sidebarManager() {
        return sidebarManager;
    }

    public fr.bosketball.stress.StressTestService stressTestService() {
        return stressTestService;
    }

    public TabListManager tabListManager() {
        return tabListManager;
    }

    public GuiMenuManager guiMenu() {
        return guiMenu;
    }

    public LobbyHologramManager lobbyHolograms() {
        return lobbyHolograms;
    }

    public ResourcePackHostService resourcePackHost() {
        return resourcePackHost;
    }

    public PingDiagnostics pingDiagnostics() {
        return pingDiagnostics;
    }

    public GameManager gameManager() {
        return gameManager;
    }

    public ServerCoreCommands coreCommands() {
        return coreCommands;
    }

    public fr.bosketball.social.SocialService socialService() {
        return socialService;
    }

    public fr.bosketball.moderation.StaffToolsManager staffTools() {
        return staffToolsManager;
    }

    public fr.bosketball.social.GuildService guildService() {
        return guildService;
    }

    public fr.bosketball.progression.ProgressService progressService() {
        return progressService;
    }

    public fr.bosketball.season.SeasonService seasonService() {
        return seasonService;
    }

    public fr.bosketball.moderation.ChatModerationService moderationService() {
        return moderationService;
    }

    public fr.bosketball.moderation.ReportService reportService() {
        return reportService;
    }

    public fr.bosketball.moderation.WarnService warnService() {
        return warnService;
    }

    public fr.bosketball.integration.IntegrationService integration() {
        return integration;
    }

    public fr.bosketball.ui.TutorialManager tutorialManager() {
        return tutorialManager;
    }

    public fr.bosketball.lobby.RewardCrateStation crateStation() {
        return crateStation;
    }

    public fr.bosketball.lobby.HallOfFamePodium hallOfFame() {
        return hallOfFame;
    }

    public fr.bosketball.arena.schematic.SchematicCourtPool schematicCourtPool() {
        return schematicCourtPool;
    }

    public fr.bosketball.i18n.LangService lang() {
        return lang;
    }

}
