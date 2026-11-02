package fr.bosketball;

import fr.bosketball.arena.ArenaManager;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.arena.MapEditorManager;
import fr.bosketball.command.BosketCommand;
import fr.bosketball.command.DiscordCommand;
import fr.bosketball.command.GgCommand;
import fr.bosketball.game.GameManager;
import fr.bosketball.listener.GameplayListener;
import fr.bosketball.model.BallSkin;
import fr.bosketball.pack.ResourcePackHostService;
import fr.bosketball.social.SocialPartyManager;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.ui.BosketChatRenderer;
import fr.bosketball.ui.BossBarHudManager;
import fr.bosketball.ui.CinematicCameraManager;
import fr.bosketball.ui.GuiMenuManager;
import fr.bosketball.ui.LockerRoom3dMenuManager;
import fr.bosketball.ui.ScoreboardSidebarManager;
import fr.bosketball.ui.TabListManager;
import java.util.List;
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
 * BosketBall — Competitive Multiplayer Basketball Plugin.
 */
public final class BosketBallPlugin extends JavaPlugin {

    private static BosketBallPlugin instance;

    private StatsManager statsManager;
    private SocialPartyManager socialManager;
    private ArenaManager arenaManager;
    private MapEditorManager mapEditor;
    private BossBarHudManager bossBarHud;
    private ScoreboardSidebarManager sidebarManager;
    private TabListManager tabListManager;
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

        int startingCoins = Math.max(0, getConfig().getInt("general.starting-coins", 100));
        this.statsManager = new StatsManager(getDataFolder(), getLogger());
        this.statsManager.load();
        this.socialManager = new SocialPartyManager(statsManager);

        this.arenaManager = new ArenaManager(getDataFolder(), getLogger());
        this.arenaManager.load();
        this.mapEditor = new MapEditorManager(this);

        this.bossBarHud = new BossBarHudManager();
        this.gameManager = new GameManager(this);
        this.lockerRoom3dManager = new LockerRoom3dMenuManager(this);
        this.cinematicCamera = new CinematicCameraManager(this);
        this.guiMenu = new GuiMenuManager(this);
        this.resourcePackHost = new ResourcePackHostService(getDataFolder(), getLogger());

        reloadBosketConfig();
        bootstrapAlreadyOnlinePlayers(startingCoins);

        List<String> keywords = getConfig().getStringList("chat.highlight-keywords");
        boolean showBadges = getConfig().getBoolean("chat.show-top-stat-badges", true);
        BosketChatRenderer chatRenderer = new BosketChatRenderer(statsManager, gameManager, keywords, showBadges);

        Bukkit.getPluginManager().registerEvents(new GameplayListener(this, chatRenderer), this);
        Bukkit.getPluginManager().registerEvents(guiMenu, this);

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

        // Main 20Hz Physics, Matchmaking, Replay Camera, Locker Room, Sidebar & [TAB] Scheduler
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            gameManager.tickAll();
            cinematicCamera.tickAll();
            lockerRoom3dManager.tickAll();

            uiTickCounter++;
            if (uiTickCounter % 20 == 0) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    statsManager.getOrCreate(p).addPlayTimeSeconds(1L);
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
        }, 1L, 1L);

        getLogger().info("BosketBall enabled (" + arenaManager.allArenas().size() + " arenas loaded).");
    }

    public void reloadBosketConfig() {
        reloadConfig();
        String domain = getConfig().getString("general.server-domain", "play.bosketball.net");
        String season = getConfig().getString("general.season-name", "Season 1");
        this.sidebarManager = new ScoreboardSidebarManager(statsManager, socialManager, domain);
        this.tabListManager = new TabListManager(statsManager, season, domain);

        this.resourcePackHost.initialize(
            getConfig().getBoolean("resource-pack.embedded-http-server.enabled", false),
            getConfig().getString("resource-pack.embedded-http-server.bind-address", "0.0.0.0"),
            getConfig().getInt("resource-pack.embedded-http-server.port", 8765),
            getConfig().getString("resource-pack.embedded-http-server.public-host", "127.0.0.1"),
            getConfig().getString("resource-pack.external-url", ""),
            getConfig().getString("resource-pack.external-sha1", ""),
            getConfig().getBoolean("resource-pack.required", false)
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
        if (arenaManager != null) {
            arenaManager.despawnAll();
            arenaManager.save();
        }
        if (statsManager != null) {
            statsManager.shutdownAndSaveSync();
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
        boolean autoEquip = getConfig().getBoolean("general.auto-equip-lobby-playbook", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            sanitizeResidualPlayerEffects(p);
            if (statsManager.getIfPresent(p.getUniqueId()) == null) {
                statsManager.getOrCreate(p).setCoins(startingCoins);
            } else {
                statsManager.getOrCreate(p);
            }
            if (autoEquip) {
                BallSkin.equipLobbyHotbarIfEmpty(p, false);
            }
        }
        statsManager.refreshLeaderboardBadges();
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
