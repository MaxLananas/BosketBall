package fr.bosketball;

import fr.bosketball.arena.ArenaManager;
import fr.bosketball.arena.MapEditorManager;
import fr.bosketball.command.BosketCommand;
import fr.bosketball.command.GgCommand;
import fr.bosketball.game.GameManager;
import fr.bosketball.listener.GameplayListener;
import fr.bosketball.pack.ResourcePackHostService;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.ui.BosketChatRenderer;
import fr.bosketball.ui.BossBarHudManager;
import fr.bosketball.ui.GuiMenuManager;
import fr.bosketball.ui.ScoreboardSidebarManager;
import fr.bosketball.ui.TabListManager;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * BosketBall 26.2 — Complete 3D Basketball Engine, Broadcast BossBar HUD, Custom Ranks & Stadium Plugin!
 */
public final class BosketBallPlugin extends JavaPlugin {

    private StatsManager statsManager;
    private ArenaManager arenaManager;
    private MapEditorManager mapEditor;
    private BossBarHudManager bossBarHud;
    private ScoreboardSidebarManager sidebarManager;
    private TabListManager tabListManager;
    private GuiMenuManager guiMenu;
    private ResourcePackHostService resourcePackHost;
    private GameManager gameManager;

    private int uiTickCounter = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.statsManager = new StatsManager(getDataFolder(), getLogger());
        this.statsManager.load();

        this.arenaManager = new ArenaManager(getDataFolder(), getLogger());
        this.arenaManager.load();
        this.mapEditor = new MapEditorManager(this);

        this.bossBarHud = new BossBarHudManager();
        this.gameManager = new GameManager(this);
        this.guiMenu = new GuiMenuManager(this);
        this.resourcePackHost = new ResourcePackHostService(getDataFolder(), getLogger());

        reloadBosketConfig();

        List<String> keywords = getConfig().getStringList("chat.highlight-keywords");
        boolean showBadges = getConfig().getBoolean("chat.show-top-stat-badges", true);
        BosketChatRenderer chatRenderer = new BosketChatRenderer(statsManager, gameManager, keywords, showBadges);

        Bukkit.getPluginManager().registerEvents(new GameplayListener(this, chatRenderer), this);

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

        // Main 20Hz Physics & Match Tick + Periodic Sidebar & TabList Refresh!
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            gameManager.tickAll();
            uiTickCounter++;
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

        getLogger().info("✦ BosketBall 26.2-ULTRA enabled! (" + arenaManager.allArenas().size() + " stadium arenas loaded)");
    }

    public void reloadBosketConfig() {
        reloadConfig();
        String domain = getConfig().getString("general.server-domain", "ᴘʟᴀʏ.ʙᴏꜱᴋᴇᴛʙᴀʟʟ.ꜰʀ");
        String season = getConfig().getString("general.season-name", "ꜱᴀɪꜱᴏɴ ɪ • ᴄʜᴀᴏꜱ ᴄᴜʙᴇᴅ");
        this.sidebarManager = new ScoreboardSidebarManager(statsManager, domain);
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
            statsManager.save();
        }
        if (resourcePackHost != null) {
            resourcePackHost.shutdown();
        }
    }

    public StatsManager statsManager() {
        return statsManager;
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
