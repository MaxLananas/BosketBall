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
import fr.bosketball.server.PermissionManager;
import fr.bosketball.server.ServerCoreCommands;
import fr.bosketball.server.ServerProtectionManager;
import fr.bosketball.social.SocialPartyManager;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.ui.BosketChatRenderer;
import fr.bosketball.ui.BossBarHudManager;
import fr.bosketball.ui.CinematicCameraManager;
import fr.bosketball.ui.GuiMenuManager;
import fr.bosketball.ui.LockerRoom3dMenuManager;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * BosketBall — Standalone Competitive Multiplayer Basketball Server &amp; Engine.
 */
public final class BosketBallPlugin extends JavaPlugin {

    private static BosketBallPlugin instance;

    private volatile BosketConfig settings = BosketConfig.defaults();
    private PermissionManager permissionManager;
    private ServerProtectionManager protectionManager;
    private MotdManager motdManager;
    private StatsManager statsManager;
    private SocialPartyManager socialManager;
    private ArenaManager arenaManager;
    private PracticeArenaPool practicePool;
    private MapEditorManager mapEditor;
    private BossBarHudManager bossBarHud;
    private ScoreboardSidebarManager sidebarManager;
    private TabListManager tabListManager;
    private BosketChatRenderer chatRenderer;
    private LockerRoom3dMenuManager lockerRoom3dManager;
    private CinematicCameraManager cinematicCamera;
    private GuiMenuManager guiMenu;
    private ResourcePackHostService resourcePackHost;
    private GameManager gameManager;

    private int uiTickCounter = 0;

    public static BosketBallPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        purgeOrphanedPluginEntities();

        this.settings = BosketConfig.loadAndUpgrade(new File(getDataFolder(), "config.yml"), getLogger());
        ShopConfig.loadOrCreate(getDataFolder(), getLogger());

        this.permissionManager = new PermissionManager(this);
        this.protectionManager = new ServerProtectionManager(this);
        this.motdManager = new MotdManager(this);

        this.statsManager = new StatsManager(getDataFolder(), getLogger());
        this.statsManager.load();
        this.socialManager = new SocialPartyManager(statsManager);

        this.arenaManager = new ArenaManager(getDataFolder(), getLogger());
        this.arenaManager.load();
        this.practicePool = new PracticeArenaPool(this);
        this.mapEditor = new MapEditorManager(this);

        this.bossBarHud = new BossBarHudManager();
        this.gameManager = new GameManager(this);
        this.lockerRoom3dManager = new LockerRoom3dMenuManager(this);
        this.cinematicCamera = new CinematicCameraManager(this);
        this.guiMenu = new GuiMenuManager(this);
        this.resourcePackHost = new ResourcePackHostService(getDataFolder(), getLogger());

        this.chatRenderer = new BosketChatRenderer(
            statsManager,
            gameManager,
            settings.chat().highlightKeywords(),
            settings.chat().showTopStatBadges()
        );

        reloadBosketConfig();
        protectionManager.enforceAllWorldRules();
        bootstrapAlreadyOnlinePlayers(settings.general().startingCoins());

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(permissionManager, this);
        pm.registerEvents(new GameplayListener(this, chatRenderer), this);
        pm.registerEvents(guiMenu, this);
        pm.registerEvents(lockerRoom3dManager, this);
        pm.registerEvents(protectionManager, this);
        pm.registerEvents(motdManager, this);

        BosketCommand mainCmd = new BosketCommand(this);
        PluginCommand bb = getCommand("bosketball");
        if (bb != null) {
            bb.setExecutor(mainCmd);
            bb.setTabCompleter(mainCmd);
        }
        PluginCommand gg = getCommand("gg");
        if (gg != null) {
            gg.setExecutor(new GgCommand(this));
        }
        PluginCommand discord = getCommand("discord");
        if (discord != null) {
            discord.setExecutor(new DiscordCommand(this));
        }

        ServerCoreCommands coreCmds = new ServerCoreCommands(this);
        for (String cmdName : new String[]{"build", "spawn", "setspawn", "perm"}) {
            PluginCommand cmd = getCommand(cmdName);
            if (cmd != null) {
                cmd.setExecutor(coreCmds);
                cmd.setTabCompleter(coreCmds);
            }
        }

        // Main 20Hz Physics, Matchmaking, Replay Camera, Locker Room, Sidebar, [TAB] & Debounced Auto-Save Scheduler
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            gameManager.tickAll();
            cinematicCamera.tickAll();
            lockerRoom3dManager.tickAll();

            uiTickCounter++;
            if (uiTickCounter % 20 == 0) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    statsManager.getOrCreate(p).addPlayTimeSeconds(1L);
                    statsManager.markDirty(p.getUniqueId());
                }
            }
            if (uiTickCounter % 10 == 0) {
                mapEditor.tickPreviews();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    sidebarManager.updatePlayerSidebar(p, gameManager);
                }
            }
            if (uiTickCounter % 30 == 0) {
                tabListManager.updateAll(gameManager);
            }
            int autoSaveTicks = Math.max(100, settings.storage().autoSaveIntervalSeconds() * 20);
            if (uiTickCounter % autoSaveTicks == 0) {
                statsManager.flushDirtyAsync(false);
            }
        }, 1L, 1L);

        getLogger().info("BosketBall enabled (" + arenaManager.allArenas().size()
            + " arenas, " + statsManager.allProfiles().size() + " profiles loaded).");
    }

    public void reloadBosketConfig() {
        saveDefaultConfig();
        reloadConfig();
        this.settings = BosketConfig.loadAndUpgrade(new File(getDataFolder(), "config.yml"), getLogger());
        ShopConfig.loadOrCreate(getDataFolder(), getLogger());

        if (this.permissionManager != null) {
            this.permissionManager.loadOrCreate();
        }
        if (this.protectionManager != null) {
            this.protectionManager.loadSpawn();
            this.protectionManager.enforceAllWorldRules();
        }
        if (this.motdManager != null) {
            this.motdManager.reloadIcon();
        }

        this.sidebarManager = new ScoreboardSidebarManager(
            statsManager,
            socialManager,
            settings.general().serverDomain()
        );
        this.tabListManager = new TabListManager(
            statsManager,
            settings.general().seasonName(),
            settings.general().serverDomain()
        );
        if (this.chatRenderer != null) {
            this.chatRenderer.updateConfig(
                settings.chat().highlightKeywords(),
                settings.chat().showTopStatBadges()
            );
        }
        if (this.lockerRoom3dManager != null) {
            this.lockerRoom3dManager.reloadStageConfig();
        }

        BosketConfig.ResourcePack rp = settings.resourcePack();
        this.resourcePackHost.initialize(
            rp.httpEnabled(),
            rp.bindAddress(),
            rp.port(),
            rp.publicHost(),
            rp.externalUrl(),
            rp.externalSha1(),
            rp.required()
        );
    }

    @Override
    public void onDisable() {
        if (cinematicCamera != null) {
            cinematicCamera.stopAll();
        }
        if (lockerRoom3dManager != null) {
            lockerRoom3dManager.closeAll();
        }
        if (gameManager != null) {
            gameManager.shutdownAll();
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
                if (!(entity instanceof Player) && entity.getScoreboardTags().contains(HoopDisplaySet.DISPLAY_TAG)) {
                    entity.remove();
                }
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
    }

    private void bootstrapAlreadyOnlinePlayers(int startingCoins) {
        boolean autoEquip = settings.general().autoEquipLobbyPlaybook();
        for (Player p : Bukkit.getOnlinePlayers()) {
            sanitizeResidualPlayerEffects(p);
            if (permissionManager != null) {
                permissionManager.applyPermissions(p);
            }
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

    public ScoreboardSidebarManager sidebarManager() {
        return sidebarManager;
    }

    public LockerRoom3dMenuManager lockerRoom3dManager() {
        return lockerRoom3dManager;
    }

    public GuiMenuManager guiMenu() {
        return guiMenu;
    }

    public ResourcePackHostService resourcePackHost() {
        return resourcePackHost;
    }

    public GameManager gameManager() {
        return gameManager;
    }
}
