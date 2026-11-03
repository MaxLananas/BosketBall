package fr.bosketball.ui;

import com.destroystokyo.paper.SkinParts;
import com.destroystokyo.paper.profile.PlayerProfile;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.config.AtomicYamlWriter;
import fr.bosketball.game.GameModeType;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.ChatTag;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.Rank;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Full-Screen Locker Room Menu (resource-pack screen HUD, totally frozen character):
 * <ul>
 *   <li>The viewer is hard-locked onto a static cinema camera (spectating an invisible anchor
 *       Mannequin + {@link PlayerMoveEvent} freeze): <b>zero movement, zero free-look</b>.</li>
 *   <li>All menu graphics are drawn directly on the player's screen with stacked transparent
 *       BossBars + custom font glyphs from the BosketBall resource pack (top tab bar, left menu
 *       list with slanted selection bar, right-hand inspector panel, ActionBar keybind footer).
 *       No world-space hologram panels anymore.</li>
 *   <li>Keyboard only: {@code Forward/Back} navigate, {@code Left/Right} switch tab (or adjust the
 *       highlighted value), {@code Jump} (Space) selects, {@code Sneak} (Shift) exits.</li>
 *   <li>Frames 1..4 online Party players head-to-toe in recessed warm-lit locker bays; sub-menus
 *       (Shop, Locker, Friends, Leaderboards...) open without leaving the frozen stage.</li>
 * </ul>
 */
public final class LockerRoom3dMenuManager implements Listener {

    public enum TopTab {
        PLAY("PLAY"),
        PROFILE("PROFILE"),
        LEADERBOARDS("LEADERBOARDS"),
        HISTORY("MATCH HISTORY"),
        FRIENDS("FRIENDS"),
        TAGS("TAGS"),
        SETTINGS("SETTINGS");

        private final String title;

        TopTab(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    /** Screen-HUD top tabs (screened in the top bar, switched with Left/Right). */
    private enum HudTab {
        PLAY("PLAY"),
        PROFILE("PROFILE"),
        SHOP("SHOP"),
        SOCIAL("SOCIAL"),
        SETTINGS("SETTINGS");

        final String label;

        HudTab(String label) {
            this.label = label;
        }
    }

    /**
     * One selectable screen-HUD row. {@code value} (nullable) is drawn right of the label;
     * {@code adjustable} rows consume Left/Right to change the value instead of switching tabs.
     * {@code info} = [title, line1, line2, status] for the right-hand inspector panel.
     */
    private record HudEntry(
        String id,
        String label,
        String value,
        boolean adjustable,
        boolean danger,
        String[] info
    ) {
    }

    // ── Screen-HUD layout (GUI pixels, virtual 560px-wide canvas centered on screen) ──
    private static final int HUD_VIRTUAL_WIDTH = 560;
    private static final int HUD_LEFT = 14;
    private static final int HUD_RIGHT = HUD_VIRTUAL_WIDTH - 12;
    private static final int HUD_VALUE_X = HUD_LEFT + 132;
    private static final int HUD_PANEL_X = HUD_VIRTUAL_WIDTH - 262;
    private static final int HUD_PANEL_CONTENT_MAX_PX = 228;
    // Vanilla renders stacked BossBars 19px apart and stops once the next row would reach
    // guiHeight / 3 (BossHealthOverlay): 6 bars at a 360px-high GUI, but only 5 at 270px.
    // The HUD therefore uses 6 bars max and anchors the selection in the second menu row,
    // so it stays visible even if the client's GUI height clips the last preview row.
    private static final int HUD_BAR_COUNT = 6;
    private static final int HUD_ROWS_PER_PAGE = 4;

    // ── BosketBall DA palette ──
    private static final TextColor C_ORANGE = TextColor.color(0xFF8C1A);
    private static final TextColor C_GOLD = TextColor.color(0xFFD43B);
    private static final TextColor C_CREAM = TextColor.color(0xFFFDF5);
    private static final TextColor C_SLATE = TextColor.color(0x94A3B8);
    private static final TextColor C_DIM = TextColor.color(0x64748B);
    private static final TextColor C_GREEN = TextColor.color(0x4ADE80);
    private static final TextColor C_SKY = TextColor.color(0x38BDF8);
    private static final TextColor C_RED = TextColor.color(0xF87171);
    private static final TextColor C_ON_SLANT = TextColor.color(0x241203);
    private static final TextColor C_ON_SLANT_SOFT = TextColor.color(0x5C2E05);

    private static final GameModeType[] RANKED_MODES = {
        GameModeType.DUEL_1V1,
        GameModeType.STREET_2V2,
        GameModeType.PRO_3V3,
        GameModeType.FULL_5V5
    };

    public static final class LockerGuiHolder implements InventoryHolder {
        private final UUID ownerId;
        private Inventory inventory;

        public LockerGuiHolder(UUID ownerId) {
            this.ownerId = ownerId;
        }

        public UUID ownerId() {
            return ownerId;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record StageActor(
        UUID uuid,
        String name,
        ResolvableProfile resolvedSkin,
        boolean isViewer,
        boolean isPartyLeader,
        String roleSubtitle
    ) {
    }

    private static final class StageSession {
        UUID viewerId;
        Location returnLocation;
        GameMode previousGameMode;
        boolean previousAllowFlight;
        boolean previousFlying;
        ItemStack[] savedStorageContents;
        ItemStack[] savedArmorContents;
        ItemStack savedOffHand;
        int savedHeldSlot;

        Location cameraEyeLocation;
        Mannequin cameraAnchor;
        Location stageReference;
        long openedAtMillis;
        boolean viewingSubMenu;

        final List<BossBar> hudBars = new ArrayList<>();
        int tabIndex = 0;
        int selectedIndex = 0;
        final int[] tabMemory = new int[HudTab.values().length];
        int rankedModeCursor = 0;
        String lastHudSignature = "";
        boolean prevFwd;
        boolean prevBack;
        boolean prevLeft;
        boolean prevRight;
        boolean prevJump;
        boolean prevSneak;
        long lastNavMillis;
        long lastActionMillis;

        final List<Entity> stageProps = new ArrayList<>();
        final List<Entity> actorProps = new ArrayList<>();
    }

    private final BosketBallPlugin plugin;
    private final Map<UUID, StageSession> sessions = new ConcurrentHashMap<>();
    private Location stageOrigin;
    private boolean stageBuilt = false;
    private int tickCounter = 0;
    private Scoreboard blankLockerScoreboard;

    private Location customCamera;
    private final Location[] customSlots = new Location[4];

    public LockerRoom3dMenuManager(BosketBallPlugin plugin) {
        this.plugin = plugin;
        loadStageConfig();
    }

    public boolean isIn3dMenu(UUID playerId) {
        return playerId != null && sessions.containsKey(playerId);
    }

    public boolean isViewingSubMenu(UUID playerId) {
        StageSession s = playerId != null ? sessions.get(playerId) : null;
        return s != null && s.viewingSubMenu;
    }

    public Location lockedCameraPose(UUID playerId) {
        StageSession s = playerId != null ? sessions.get(playerId) : null;
        return s != null && s.cameraEyeLocation != null ? s.cameraEyeLocation.clone() : null;
    }

    public boolean hasCustomStage() {
        return customCamera != null && customCamera.getWorld() != null;
    }

    public void setCustomCamera(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        this.customCamera = loc.clone();
        saveStageConfig();
    }

    public void setCustomSlot(int oneBasedSlot, Location loc) {
        if (oneBasedSlot < 1 || oneBasedSlot > 4 || loc == null || loc.getWorld() == null) {
            return;
        }
        this.customSlots[oneBasedSlot - 1] = loc.clone();
        saveStageConfig();
    }

    public void resetCustomStage() {
        this.customCamera = null;
        for (int i = 0; i < 4; i++) {
            this.customSlots[i] = null;
        }
        saveStageConfig();
    }

    public void reloadStageConfig() {
        loadStageConfig();
    }

    private Scoreboard getOrCreateBlankScoreboard() {
        if (blankLockerScoreboard == null && Bukkit.getScoreboardManager() != null) {
            blankLockerScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        }
        return blankLockerScoreboard;
    }

    private void loadStageConfig() {
        if (plugin == null || !plugin.getDataFolder().exists()) {
            return;
        }
        File file = new File(plugin.getDataFolder(), "locker-room.yml");
        File legacy = new File(plugin.getDataFolder(), "stage3d.yml");
        if (!file.exists() && legacy.exists()) {
            file = legacy;
        }
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = AtomicYamlWriter.loadSafe(file, plugin.getLogger());
        this.customCamera = readLoc(yml.getConfigurationSection("camera"));
        for (int i = 0; i < 4; i++) {
            this.customSlots[i] = readLoc(yml.getConfigurationSection("slot-" + (i + 1)));
            if (this.customSlots[i] == null) {
                this.customSlots[i] = readLoc(yml.getConfigurationSection("slot" + (i + 1)));
            }
        }
        if (file.equals(legacy)) {
            saveStageConfig();
            legacy.delete();
        }
    }

    private void saveStageConfig() {
        if (plugin == null) {
            return;
        }
        File file = new File(plugin.getDataFolder(), "locker-room.yml");
        YamlConfiguration yml = new YamlConfiguration();
        if (customCamera != null) {
            writeLoc(yml.createSection("camera"), customCamera);
        }
        for (int i = 0; i < 4; i++) {
            if (customSlots[i] != null) {
                writeLoc(yml.createSection("slot-" + (i + 1)), customSlots[i]);
            }
        }
        String header = "# ===========================================================================\n"
            + "#   BOSKETBALL — CUSTOM LOCKER ROOM STAGE POSES (locker-room.yml)\n"
            + "#   Set in-game with: /bb 3d setcamera & /bb 3d setslot <1..4>\n"
            + "# ===========================================================================\n";
        AtomicYamlWriter.writeYamlAtomically(file, header, yml, plugin.getLogger());
    }

    private static Location readLoc(ConfigurationSection sec) {
        if (sec == null) {
            return null;
        }
        String worldName = sec.getString("world");
        if (worldName == null || Bukkit.getServer() == null) {
            return null;
        }
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            return null;
        }
        return new Location(
            w,
            sec.getDouble("x"),
            sec.getDouble("y"),
            sec.getDouble("z"),
            (float) sec.getDouble("yaw"),
            (float) sec.getDouble("pitch")
        );
    }

    private static void writeLoc(ConfigurationSection sec, Location loc) {
        if (sec == null || loc == null || loc.getWorld() == null) {
            return;
        }
        sec.set("world", loc.getWorld().getName());
        sec.set("x", AtomicYamlWriter.round2(loc.getX()));
        sec.set("y", AtomicYamlWriter.round2(loc.getY()));
        sec.set("z", AtomicYamlWriter.round2(loc.getZ()));
        sec.set("yaw", AtomicYamlWriter.round2(loc.getYaw()));
        sec.set("pitch", AtomicYamlWriter.round2(loc.getPitch()));
    }

    /**
     * Builds a spacious pro-esports Locker Room with recessed individual dark-stone &amp; spruce locker alcoves,
     * warm overhead spotlights, and polished hardwood flooring so 1..4 full-body players look crisp &amp; bright!
     */
    private Location ensureStageBuilt(World fallbackWorld) {
        Arena primary = plugin.arenaManager().primaryArena();
        World world = (primary != null && primary.world() != null) ? primary.world() : fallbackWorld;
        if (world == null) {
            return null;
        }
        if (stageBuilt && stageOrigin != null && stageOrigin.getWorld() != null && stageOrigin.getWorld().equals(world)) {
            return stageOrigin;
        }

        int ox = primary != null ? primary.center().getBlockX() + 190 : 190;
        int oy = Math.max(72, primary != null ? primary.center().getBlockY() + 12 : 84);
        int oz = primary != null ? primary.center().getBlockZ() + 190 : 190;

        for (int dx = -10; dx <= 10; dx++) {
            for (int dy = 0; dy <= 7; dy++) {
                for (int dz = -6; dz <= 8; dz++) {
                    Block b = world.getBlockAt(ox + dx, oy + dy, oz + dz);
                    boolean shell = (dx == -10 || dx == 10 || dy == 0 || dy == 7 || dz == -6 || dz == 8);
                    if (!shell) {
                        b.setType(Material.AIR, false);
                        continue;
                    }
                    if (dy == 0) {
                        boolean centerInlay = Math.abs(dx) <= 6 && dz >= 0 && dz <= 4 && (dx + dz) % 2 == 0;
                        b.setType(centerInlay ? Material.STRIPPED_SPRUCE_WOOD : Material.STRIPPED_OAK_WOOD, false);
                    } else if (dy == 7) {
                        boolean ceilingLight = (dz == 2 || dz == 5 || dz == -1) && (dx == -5 || dx == -2 || dx == 2 || dx == 5);
                        b.setType(ceilingLight ? Material.OCHRE_FROGLIGHT : Material.DARK_OAK_PLANKS, false);
                    } else {
                        b.setType(Material.DEEPSLATE_TILES, false);
                    }
                }
            }
        }

        // Back wall recessed locker bays (4 framed pro locker alcoves at x = -5..-3, -2..0, 0..2, 3..5)
        for (int dx = -9; dx <= 9; dx++) {
            for (int dy = 1; dy <= 6; dy++) {
                world.getBlockAt(ox + dx, oy + dy, oz + 7).setType(Material.SPRUCE_PLANKS, false);
            }
        }
        int[] dividerXs = {-9, -6, -3, 0, 3, 6, 9};
        for (int px : dividerXs) {
            for (int dy = 1; dy <= 6; dy++) {
                world.getBlockAt(ox + px, oy + dy, oz + 6).setType(Material.POLISHED_DEEPSLATE, false);
            }
        }
        // Top header soffit + warm recessed bay lights above each locker alcove
        for (int dx = -8; dx <= 8; dx++) {
            world.getBlockAt(ox + dx, oy + 6, oz + 6).setType(Material.DEEPSLATE_BRICKS, false);
            if (dx == -7 || dx == -4 || dx == -1 || dx == 1 || dx == 4 || dx == 7) {
                world.getBlockAt(ox + dx, oy + 5, oz + 6).setType(Material.OCHRE_FROGLIGHT, false);
            }
        }
        // Subtle front floor key-lighting hidden near camera wall so player faces & sneakers are brightly lit!
        for (int lx : new int[]{-4, 0, 4}) {
            world.getBlockAt(ox + lx, oy, oz - 2).setType(Material.OCHRE_FROGLIGHT, false);
        }

        stageOrigin = new Location(world, ox + 0.5, oy + 1.0, oz + 0.5);
        stageBuilt = true;
        return stageOrigin;
    }

    public void open(Player player, TopTab initialTab) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (plugin.gameManager().isInMatch(player.getUniqueId())) {
            Msg.send(player, Msg.warn("Leave your current match (<yellow>/leave</yellow>) before entering the Locker Room."));
            return;
        }

        StageSession prev = sessions.get(player.getUniqueId());
        if (prev != null) {
            // Already inside the Locker Room — just return cleanly from any open sub-menu and focus the requested section!
            applyInitialTab(prev, initialTab);
            returnFromSubMenu(player);
            return;
        }

        Location preservedReturn = player.getLocation().clone();
        GameMode preservedMode = player.getGameMode();
        boolean preservedAllowFlight = player.getAllowFlight();
        boolean preservedFlying = player.isFlying();
        ItemStack[] preservedStorage = deepClone(player.getInventory().getStorageContents());
        ItemStack[] preservedArmor = deepClone(player.getInventory().getArmorContents());
        ItemStack preservedOffHand = cloneItem(player.getInventory().getItemInOffHand());
        int preservedSlot = player.getInventory().getHeldItemSlot();

        plugin.clearLobbyRig(player.getUniqueId(), false);

        boolean usingCustomRoom = hasCustomStage();
        Location stage = usingCustomRoom ? customCamera.clone() : ensureStageBuilt(player.getWorld());
        if (stage == null || stage.getWorld() == null) {
            return;
        }

        StageSession s = new StageSession();
        s.viewerId = player.getUniqueId();
        s.returnLocation = preservedReturn;
        s.previousGameMode = preservedMode;
        s.previousAllowFlight = preservedAllowFlight;
        s.previousFlying = preservedFlying;
        s.savedStorageContents = preservedStorage;
        s.savedArmorContents = preservedArmor;
        s.savedOffHand = preservedOffHand;
        s.savedHeldSlot = preservedSlot;
        s.openedAtMillis = System.currentTimeMillis();
        s.stageReference = stage.clone();
        applyInitialTab(s, initialTab);

        GameModeType curQueued = plugin.gameManager().getQueuedMode(player.getUniqueId());
        if (curQueued != null) {
            for (int i = 0; i < RANKED_MODES.length; i++) {
                if (RANKED_MODES[i] == curQueued) {
                    s.rankedModeCursor = i;
                    break;
                }
            }
        }

        // Wide full-body cinema camera: frames 100% of every character head-to-toe (sneakers + aura ring -> crown nameplate)!
        Location eyeRigLoc;
        if (usingCustomRoom) {
            eyeRigLoc = customCamera.clone().add(0.0, 1.38, 0.0);
            eyeRigLoc.setPitch(Math.clamp(customCamera.getPitch(), -6.0f, 10.0f));
        } else {
            eyeRigLoc = stage.clone().add(0.0, 1.44, -3.95);
            eyeRigLoc.setYaw(0.0f);
            eyeRigLoc.setPitch(3.2f);
        }
        s.cameraEyeLocation = eyeRigLoc.clone();

        eyeRigLoc.getChunk().load();
        stage.getChunk().load();

        // Frozen cinema camera: the viewer spectates an invisible anchor Mannequin whose eyes sit
        // exactly at eyeRigLoc. Spectating a living entity locks the client's view rotation to the
        // anchor, and tickAll + onPlayerMove hard-freeze any residual movement — zero motion allowed.
        Location anchorLoc = eyeRigLoc.clone().add(0.0, -1.62, 0.0);
        s.cameraAnchor = eyeRigLoc.getWorld().spawn(anchorLoc, Mannequin.class, man -> {
            man.setPersistent(false);
            man.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            man.setImmovable(true);
            man.setGravity(false);
            man.setInvulnerable(true);
            man.setSilent(true);
            man.setCollidable(false);
            man.setInvisible(true);
            man.setCustomNameVisible(false);
        });
        hideFromOthers(player, s.cameraAnchor);
        s.stageProps.add(s.cameraAnchor);

        player.closeInventory();
        player.getInventory().clear();
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        player.teleport(anchorLoc);
        player.setGameMode(GameMode.SPECTATOR);
        player.setSpectatorTarget(s.cameraAnchor);

        Scoreboard blank = getOrCreateBlankScoreboard();
        if (blank != null) {
            player.setScoreboard(blank);
        }
        if (plugin.sidebarManager() != null) {
            plugin.sidebarManager().clearPlayerCache(player.getUniqueId());
        }

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(player.getUniqueId())) {
                other.hidePlayer(plugin, player);
                player.hidePlayer(plugin, other);
            }
        }

        spawnPartyStageMannequins(player, stage, s);
        sessions.put(player.getUniqueId(), s);

        // Screen HUD: clear any match/queue boss bars, then show the locker screen stack.
        if (plugin.bossBarHud() != null) {
            plugin.bossBarHud().clearPlayer(player);
        }
        initHudBars(player, s);
        refreshScreenHud(player, s, true);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            StageSession active = sessions.get(player.getUniqueId());
            if (active == s && player.isOnline() && active.cameraAnchor != null && active.cameraAnchor.isValid()) {
                player.setGameMode(GameMode.SPECTATOR);
                player.setSpectatorTarget(active.cameraAnchor);
            }
        }, 1L);

        Msg.playPersonalPackSound(player, "ui.click", 0.85f, 1.18f);
    }

    private static void applyInitialTab(StageSession s, TopTab tab) {
        if (tab == null) {
            s.tabIndex = HudTab.PLAY.ordinal();
            s.selectedIndex = 0;
            return;
        }
        switch (tab) {
            case PLAY -> {
                s.tabIndex = HudTab.PLAY.ordinal();
                s.selectedIndex = 0;
            }
            case TAGS -> {
                s.tabIndex = HudTab.PROFILE.ordinal();
                s.selectedIndex = 2;
            }
            case FRIENDS -> {
                s.tabIndex = HudTab.SOCIAL.ordinal();
                s.selectedIndex = 1;
            }
            case LEADERBOARDS -> {
                s.tabIndex = HudTab.PROFILE.ordinal();
                s.selectedIndex = 4;
            }
            case PROFILE -> {
                s.tabIndex = HudTab.PROFILE.ordinal();
                s.selectedIndex = 3;
            }
            case HISTORY -> {
                s.tabIndex = HudTab.PROFILE.ordinal();
                s.selectedIndex = 5;
            }
            case SETTINGS -> {
                s.tabIndex = HudTab.SETTINGS.ordinal();
                s.selectedIndex = 0;
            }
        }
        s.tabMemory[s.tabIndex] = s.selectedIndex;
    }

    /**
     * Opens a BosketBall chest GUI sub-menu WITHOUT exiting or teleporting out of the Locker Room!
     * The screen HUD hides while the chest GUI is open and returns seamlessly on close.
     */
    public void openSubMenu(Player player, Runnable menuOpener) {
        StageSession s = sessions.get(player.getUniqueId());
        if (s != null) {
            s.viewingSubMenu = true;
            setHudVisible(player, s, false);
        }
        if (menuOpener != null) {
            menuOpener.run();
        }
    }

    /**
     * Called when a player clicks Back or presses ESC from any sub-menu opened inside the Locker Room!
     * Keeps them inside the Locker Room and refreshes their 3D character, basketball, tag &amp; side HUD live!
     */
    public void returnFromSubMenu(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        StageSession s = sessions.get(player.getUniqueId());
        if (s == null) {
            return;
        }
        s.viewingSubMenu = false;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof GuiMenuManager.BosketHolder) {
            player.closeInventory();
        }
        if (s.cameraAnchor != null && s.cameraAnchor.isValid()) {
            if (player.getGameMode() != GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SPECTATOR);
            }
            if (player.getSpectatorTarget() != s.cameraAnchor) {
                player.setSpectatorTarget(s.cameraAnchor);
            }
        }
        Scoreboard blank = getOrCreateBlankScoreboard();
        if (blank != null) {
            player.setScoreboard(blank);
        }
        if (plugin.bossBarHud() != null) {
            plugin.bossBarHud().clearPlayer(player);
        }
        rebuildActors(player, s);
        setHudVisible(player, s, true);
        refreshScreenHud(player, s, true);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.15f);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SCREEN HUD ENGINE — stacked transparent BossBars + pack font glyphs
    // ════════════════════════════════════════════════════════════════════════

    /** Pixel-tracked line builder: every line is padded to the same virtual width so the
     *  client centers every BossBar on the exact same left edge (screen-space anchoring). */
    private static final class HudLine {
        private final TextComponent.Builder out = Component.text();
        private int advance;

        HudLine pad(int px) {
            if (px != 0) {
                out.append(Component.text(Glyphs.shift(px), Glyphs.BITMAP_STYLE));
                advance += px;
            }
            return this;
        }

        HudLine glyph(char glyph) {
            out.append(Component.text(String.valueOf(glyph), Glyphs.BITMAP_STYLE));
            advance += Glyphs.advanceOf(glyph);
            return this;
        }

        HudLine txt(String text, TextColor color) {
            return txt(text, color, false);
        }

        HudLine txt(String text, TextColor color, boolean bold) {
            Style style = bold ? Style.style(color, TextDecoration.BOLD) : Style.style(color);
            out.append(Component.text(text, style));
            advance += Glyphs.textAdvancePx(text, bold);
            return this;
        }

        HudLine txtUnderlined(String text, TextColor color) {
            out.append(Component.text(text, Style.style(color, TextDecoration.BOLD, TextDecoration.UNDERLINED)));
            advance += Glyphs.textAdvancePx(text, true);
            return this;
        }

        int pos() {
            return advance;
        }

        Component build() {
            int missing = HUD_VIRTUAL_WIDTH - advance;
            if (missing > 0) {
                pad(missing);
            }
            return out.build();
        }
    }

    private void initHudBars(Player viewer, StageSession s) {
        for (int i = 0; i < HUD_BAR_COUNT; i++) {
            BossBar bar = BossBar.bossBar(Component.empty(), 1.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            s.hudBars.add(bar);
            viewer.showBossBar(bar);
        }
    }

    private void setHudVisible(Player viewer, StageSession s, boolean visible) {
        for (BossBar bar : s.hudBars) {
            if (visible) {
                viewer.showBossBar(bar);
            } else {
                viewer.hideBossBar(bar);
            }
        }
    }

    private void destroyHudBars(Player viewer, StageSession s) {
        for (BossBar bar : s.hudBars) {
            viewer.hideBossBar(bar);
        }
        s.hudBars.clear();
    }

    /** Hard freeze: while inside the Locker Room the character cannot move or look around at all. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMoveFreeze(PlayerMoveEvent event) {
        StageSession s = sessions.get(event.getPlayer().getUniqueId());
        if (s == null || s.cameraEyeLocation == null || s.cameraAnchor == null) {
            return;
        }
        Location to = event.getTo();
        Location anchor = s.cameraAnchor.getLocation();
        if (to == null || to.getWorld() == null || !to.getWorld().equals(anchor.getWorld())) {
            return;
        }
        if (to.distanceSquared(anchor) > 0.0004
            || Math.abs(to.getYaw() - s.cameraEyeLocation.getYaw()) > 0.01f
            || Math.abs(to.getPitch() - s.cameraEyeLocation.getPitch()) > 0.01f) {
            event.setTo(anchor.clone());
        }
    }

    private HudTab currentTab(StageSession s) {
        return HudTab.values()[Math.clamp(s.tabIndex, 0, HudTab.values().length - 1)];
    }

    /** Builds the dynamic entry list of the current tab (labels, live values, info-panel copy). */
    private List<HudEntry> buildEntries(Player viewer, StageSession s) {
        fr.bosketball.model.PlayerProfile prof = plugin.statsManager().getOrCreate(viewer);
        List<HudEntry> entries = new ArrayList<>();
        GameModeType queued = plugin.gameManager().getQueuedMode(viewer.getUniqueId());
        var party = plugin.socialManager().getParty(viewer.getUniqueId());
        int partySize = plugin.socialManager().partySizeOrOne(viewer.getUniqueId());

        switch (currentTab(s)) {
            case PLAY -> {
                GameModeType mode = RANKED_MODES[Math.clamp(s.rankedModeCursor, 0, RANKED_MODES.length - 1)];
                int modeQueued = plugin.gameManager().queuedCount(mode);
                boolean inThisQueue = queued == mode;
                String queueStatus = inThisQueue
                    ? "⏳ En file — " + modeQueued + "/" + mode.maxPlayers() + " joueurs · " + fmtDuration(plugin.gameManager().getQueueElapsedSeconds(viewer.getUniqueId()))
                    : modeQueued + " joueur" + (modeQueued > 1 ? "s" : "") + " en file sur ce mode";
                entries.add(new HudEntry("ranked", "PLAY RANKED", "◀ " + mode.plainName().toUpperCase(java.util.Locale.ROOT) + " ▶", true, false,
                    new String[]{"MATCH CLASSÉ " + mode.id().toUpperCase(java.util.Locale.ROOT),
                        "Affronte des joueurs de ton niveau,",
                        "gagne de l'ELO, des Coins et de l'XP.", queueStatus}));
                entries.add(new HudEntry("practice", "PRACTICE COURT", null, false, false,
                    new String[]{"PRACTICE COURT", "Terrain solo privé avec bot défenseur.", "Parfait pour roder ton tir et tes dunks.", "Accès instantané, sans file d'attente"}));
                int contestQueued = plugin.gameManager().queuedCount(GameModeType.THREE_POINT_CONTEST);
                entries.add(new HudEntry("contest", "3-POINT CONTEST", null, false, false,
                    new String[]{"3-POINT CONTEST", "25 ballons, 5 racks, Money Balls x2.", "Bats le chrono et le leaderboard.", contestQueued + " joueur" + (contestQueued > 1 ? "s" : "") + " en file"}));
                entries.add(new HudEntry("spectate", "SPECTATE MATCH", null, false, false,
                    new String[]{"MODE SPECTATEUR", "Regarde les matchs en cours", "en caméra libre cinématique.", plugin.gameManager().activePlayerCount() + " joueurs en match"}));
                entries.add(new HudEntry("modes", "ALL GAME MODES", null, false, false,
                    new String[]{"TOUS LES MODES", "Parcours les arènes et modes", "dans le menu détaillé.", Bukkit.getOnlinePlayers().size() + " joueurs en ligne"}));
            }
            case PROFILE -> {
                entries.add(new HudEntry("locker", "MY LOCKER", null, false, false,
                    new String[]{"MY LOCKER", "Équipe tes ballons 3D", "et tes styles de dunk débloqués.", prof.unlockedBalls().size() + " ballons · " + prof.unlockedDunks().size() + " dunks"}));
                entries.add(new HudEntry("ball", "EQUIPPED BALL", "◀ " + truncate(prof.selectedBallSkin().plainName().toUpperCase(java.util.Locale.ROOT), 12) + " ▶", true, false,
                    new String[]{"BALLON ÉQUIPÉ", "Q/D pour changer de ballon en direct —", "regarde ton personnage au centre !", prof.unlockedBalls().size() + " ballons débloqués"}));
                ChatTag tag = prof.selectedTag();
                entries.add(new HudEntry("tags", "CHAT TAGS", null, false, false,
                    new String[]{"CHAT TAGS", "Choisis le badge affiché", "devant ton pseudo dans le chat.", tag != null ? "Actuel : " + tag.displayName() : "Aucun tag équipé"}));
                Rank nextRank = prof.competitiveRank().nextCompetitiveRank();
                entries.add(new HudEntry("ranks", "RANKS & STATS", null, false, false,
                    new String[]{"RANKS & STATS", prof.competitiveRank().plainDisplayName() + " · " + prof.elo() + " ELO · " + prof.wins() + " victoires",
                        nextRank != null ? "Plus que " + Math.max(0, nextRank.minElo() - prof.elo()) + " ELO avant " + nextRank.plainDisplayName() + "." : "Rang maximum atteint. Légende.",
                        prof.points() + " points marqués en carrière"}));
                entries.add(new HudEntry("leaderboards", "LEADERBOARDS", null, false, false,
                    new String[]{"LEADERBOARDS", "Les meilleurs joueurs du serveur :", "ELO, points, coins, victoires...", "Top 10 mis à jour en direct"}));
                entries.add(new HudEntry("history", "MATCH HISTORY", null, false, false,
                    new String[]{"MATCH HISTORY", "Tes derniers matchs, scores", "et adversaires affrontés.", prof.matchHistory().size() + " matchs enregistrés"}));
            }
            case SHOP -> {
                int unowned = plugin.guiMenu().countUnownedShopItems(prof);
                entries.add(new HudEntry("shop_all", "BOUTIQUE SHOP", null, false, false,
                    new String[]{"BOUTIQUE SHOP", "Ballons 3D, dunks signature", "et tags collector en vitrine.", unowned + " articles à débloquer · " + prof.coins() + " coins"}));
                entries.add(new HudEntry("shop_balls", "BASKETBALLS", null, false, false,
                    new String[]{"BASKETBALLS 3D", BallSkin.values().length + " ballons modélisés en 3D,", "du classique au cosmique.", prof.unlockedBalls().size() + "/" + BallSkin.values().length + " débloqués"}));
                entries.add(new HudEntry("shop_dunks", "DUNK STYLES", null, false, false,
                    new String[]{"DUNK STYLES", DunkStyle.values().length + " animations de dunk", "à équiper dans ton locker.", prof.unlockedDunks().size() + "/" + DunkStyle.values().length + " débloqués"}));
                entries.add(new HudEntry("shop_tags", "CHAT TAGS", null, false, false,
                    new String[]{"CHAT TAGS SHOP", "Des dizaines de badges collector", "pour briller dans le chat.", prof.unlockedTags().size() + " tags possédés"}));
            }
            case SOCIAL -> {
                String squadStatus = party != null
                    ? "Party active (" + partySize + "/4)" + (party.hasCustomTeamName() ? " · « " + party.teamName() + " »" : "")
                    : "Tu joues en solo pour l'instant";
                entries.add(new HudEntry("party", "PARTY", party != null ? partySize + "/4" : "SOLO", false, false,
                    new String[]{"PARTY / SQUAD", "Invite tes amis, nomme ton équipe", "et pars en file ensemble (max 4).", squadStatus}));
                int onlineFriends = plugin.socialManager().countOnlineFriends(viewer.getUniqueId());
                int reqs = plugin.socialManager().incomingFriendRequests(viewer.getUniqueId()).size();
                entries.add(new HudEntry("friends", "FRIENDS", reqs > 0 ? reqs + " NEW" : null, false, false,
                    new String[]{"FRIENDS", "Gère tes amis, défis 1v1", "et demandes en attente.", onlineFriends + " amis en ligne" + (reqs > 0 ? " · " + reqs + " demande(s) !" : "")}));
                entries.add(new HudEntry("addfriend", "ADD A FRIEND", null, false, false,
                    new String[]{"AJOUTER UN AMI", "Choisis parmi les joueurs", "actuellement en ligne.", Bukkit.getOnlinePlayers().size() + " joueurs connectés"}));
            }
            case SETTINGS -> {
                var st = prof.settings();
                entries.add(new HudEntry("set_cam", "GOAL REPLAY CAMERA", yn(st.cinematicScoreCam()), true, false,
                    new String[]{"GOAL REPLAY CAMERA", "Caméra cinématique automatique", "après chaque panier marqué.", "Actuellement : " + onOff(st.cinematicScoreCam())}));
                entries.add(new HudEntry("set_aim", "AIM TRAJECTORY", yn(st.aimTrajectory()), true, false,
                    new String[]{"AIM TRAJECTORY", "Affiche la trajectoire de visée 3D", "pendant que tu charges ton tir.", "Actuellement : " + onOff(st.aimTrajectory())}));
                entries.add(new HudEntry("set_sounds", "CUSTOM SOUNDS", yn(st.customSounds()), true, false,
                    new String[]{"CUSTOM SOUNDS", "Sons du pack BosketBall :", "swish, dunks, buzzer, interface...", "Actuellement : " + onOff(st.customSounds())}));
                entries.add(new HudEntry("set_rings", "FLOOR AURA RINGS", yn(st.floorAuraRings()), true, false,
                    new String[]{"FLOOR AURA RINGS", "Anneaux colorés sous les joueurs", "(équipes, lobby, vestiaire).", "Actuellement : " + onOff(st.floorAuraRings())}));
                entries.add(new HudEntry("set_names", "NAMEPLATES", yn(st.overheadNameplates()), true, false,
                    new String[]{"NAMEPLATES", "Badges de rang et tags affichés", "au-dessus des joueurs en match.", "Actuellement : " + onOff(st.overheadNameplates())}));
                entries.add(new HudEntry("settings_gui", "ALL SETTINGS…", null, false, false,
                    new String[]{"TOUS LES RÉGLAGES", "Ouvre le panneau complet", "des préférences BosketBall.", "Audio, visuels, gameplay"}));
            }
        }
        entries.add(new HudEntry("exit", "EXIT TO LOBBY", null, false, true,
            new String[]{"QUITTER LE VESTIAIRE", "Retourner sur la plaza du lobby,", "près des NPCs Play & Shop.", "Astuce : Shift quitte à tout moment"}));
        return entries;
    }

    private static String yn(boolean value) {
        return value ? "◀ ON ▶" : "◀ OFF ▶";
    }

    private static String onOff(boolean value) {
        return value ? "activé" : "désactivé";
    }

    private static String fmtDuration(long totalSeconds) {
        long s = Math.max(0, totalSeconds);
        return (s / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60);
    }

    private static String truncate(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, Math.max(1, maxChars - 1)) + "…";
    }

    /** Cheap change-detection signature so idle seconds don't resend the six BossBar HUD lines. */
    private String hudSignature(Player viewer, StageSession s, List<HudEntry> entries) {
        fr.bosketball.model.PlayerProfile prof = plugin.statsManager().getOrCreate(viewer);
        StringBuilder sig = new StringBuilder(128);
        sig.append(s.tabIndex).append('|').append(s.selectedIndex).append('|').append(s.rankedModeCursor).append('|')
            .append(Bukkit.getOnlinePlayers().size()).append('|')
            .append(prof.coins()).append('|').append(prof.elo()).append('|')
            .append(prof.selectedBallSkin().name()).append('|')
            .append(plugin.socialManager().partySizeOrOne(viewer.getUniqueId())).append('|')
            .append(plugin.gameManager().queuedCount()).append('|')
            .append(plugin.gameManager().activePlayerCount()).append('|')
            .append(plugin.gameManager().getQueueElapsedSeconds(viewer.getUniqueId())).append('|');
        var st = prof.settings();
        sig.append(st.cinematicScoreCam()).append(st.aimTrajectory()).append(st.customSounds())
            .append(st.floorAuraRings()).append(st.overheadNameplates()).append('|');
        for (HudEntry e : entries) {
            sig.append(e.id()).append('=').append(e.value()).append(';');
        }
        return sig.toString();
    }

    private void refreshScreenHud(Player viewer, StageSession s, boolean force) {
        if (viewer == null || !viewer.isOnline() || s == null || s.viewingSubMenu || s.hudBars.isEmpty()) {
            return;
        }
        List<HudEntry> entries = buildEntries(viewer, s);
        s.selectedIndex = Math.clamp(s.selectedIndex, 0, entries.size() - 1);
        String signature = hudSignature(viewer, s, entries);
        if (!force && signature.equals(s.lastHudSignature)) {
            return;
        }
        s.lastHudSignature = signature;

        List<Component> lines = new ArrayList<>(HUD_BAR_COUNT);
        lines.add(buildTopBarLine(s));
        lines.add(buildStatusLine(viewer));
        HudEntry active = entries.get(s.selectedIndex);
        // Sliding 4-row window anchored so the selection normally sits in the second
        // visible row. Each row carries entry + info side by side because only 5–6
        // stacked BossBars render at common GUI scales; anchoring the cursor high in
        // the window guarantees it survives a client that clips the sixth bar.
        int windowStart = Math.clamp(s.selectedIndex - 1, 0,
                Math.max(0, entries.size() - 2));
        boolean moreBelow = windowStart + HUD_ROWS_PER_PAGE < entries.size();
        for (int row = 0; row < HUD_ROWS_PER_PAGE; row++) {
            int idx = windowStart + row;
            String scrollHint = row == 0 && windowStart > 0 ? "▲"
                    : row == HUD_ROWS_PER_PAGE - 1 && moreBelow ? "▼" : "";
            lines.add(buildRowLine(idx < entries.size() ? entries.get(idx) : null,
                    idx == s.selectedIndex, active, row, scrollHint));
        }

        for (int i = 0; i < HUD_BAR_COUNT; i++) {
            Component line = i < lines.size() ? lines.get(i) : new HudLine().build();
            s.hudBars.get(i).name(line);
        }
        sendHudFooter(viewer);
    }

    private Component buildTopBarLine(StageSession s) {
        HudLine line = new HudLine();
        int stripAdvance = Glyphs.advanceOf(Glyphs.LOCKER_TOPBAR);
        int stripX = (HUD_VIRTUAL_WIDTH - stripAdvance) / 2;
        line.pad(stripX);
        line.glyph(Glyphs.LOCKER_TOPBAR);
        line.pad(-stripAdvance);

        HudTab[] tabs = HudTab.values();
        int tabsWidth = 0;
        for (HudTab tab : tabs) {
            tabsWidth += Glyphs.textAdvancePx(tab.label, true);
        }
        tabsWidth += (tabs.length - 1) * 12;
        int tabsX = stripX + (stripAdvance - tabsWidth) / 2;
        line.pad(tabsX - line.pos());
        for (int i = 0; i < tabs.length; i++) {
            if (i > 0) {
                line.pad(12);
            }
            if (i == s.tabIndex) {
                line.txtUnderlined(tabs[i].label, C_GOLD);
            } else {
                line.txt(tabs[i].label, C_SLATE, true);
            }
        }
        return line.build();
    }

    private Component buildStatusLine(Player viewer) {
        fr.bosketball.model.PlayerProfile prof = plugin.statsManager().getOrCreate(viewer);
        HudLine line = new HudLine();
        line.pad(HUD_LEFT);
        int online = Bukkit.getOnlinePlayers().size();
        line.txt("● ", C_GREEN, true).txt(online + " en ligne", C_CREAM);
        GameModeType queued = plugin.gameManager().getQueuedMode(viewer.getUniqueId());
        if (queued != null) {
            int cur = plugin.gameManager().queuedCount(queued);
            line.txt("   ⏳ ", C_DIM).txt("File " + queued.id().toUpperCase(java.util.Locale.ROOT) + " " + cur + "/" + queued.maxPlayers(), C_GREEN);
        }

        // Right-aligned cluster: party · coins · rank/ELO
        var party = plugin.socialManager().getParty(viewer.getUniqueId());
        int partySize = plugin.socialManager().partySizeOrOne(viewer.getUniqueId());
        String partyTxt = party == null
            ? "Solo"
            : (party.hasCustomTeamName() ? truncate(party.teamName(), 14) : "Party") + " " + partySize + "/4";
        String coins = String.valueOf(prof.coins());
        char rankPill = prof.effectiveRank().pillGlyph();
        String elo = prof.elo() + " ELO";
        int rightWidth = Glyphs.textAdvancePx("👥 ", false) + Glyphs.textAdvancePx(partyTxt, false)
            + 8 + Glyphs.advanceOf(Glyphs.CHAT_ICON_COIN) + 2 + Glyphs.textAdvancePx(coins, true)
            + 8 + Glyphs.advanceOf(rankPill) + 2 + Glyphs.textAdvancePx(elo, true);
        line.pad(Math.max(4, HUD_RIGHT - rightWidth - line.pos()));
        line.txt("👥 ", C_SLATE).txt(partyTxt, party == null ? C_SLATE : C_GREEN);
        line.pad(8).glyph(Glyphs.CHAT_ICON_COIN).pad(2).txt(coins, C_GOLD, true);
        line.pad(8).glyph(rankPill).pad(2).txt(elo, C_SKY, true);
        return line.build();
    }

    /**
     * One combined screen row: the menu entry (left) and the matching inspector-panel
     * row (right) share a single stacked BossBar line. Row 0 also paints the tall
     * 250x76 panel background glyph behind the four rows of info text.
     */
    private Component buildRowLine(HudEntry entry, boolean selected, HudEntry active, int infoRow, String scrollHint) {
        HudLine line = new HudLine();
        line.pad(HUD_LEFT);
        if (entry != null && selected) {
            char slant = entry.danger() ? Glyphs.LOCKER_SELECT_SLANT_EXIT : Glyphs.LOCKER_SELECT_SLANT;
            int slantAdvance = Glyphs.advanceOf(slant);
            line.glyph(slant);
            line.pad(-slantAdvance + 12);
            line.txt(entry.label(), C_ON_SLANT, true);
            if (entry.value() != null) {
                line.pad(Math.max(6, HUD_VALUE_X - line.pos()));
                line.txt(entry.value(), entry.adjustable() ? C_ON_SLANT : C_ON_SLANT_SOFT, true);
            }
        } else if (entry != null) {
            line.pad(10);
            line.txt(entry.label(), entry.danger() ? TextColor.color(0xE8A0A0) : C_CREAM);
            if (entry.value() != null) {
                line.pad(Math.max(6, HUD_VALUE_X - line.pos()));
                line.txt(entry.value(), C_DIM);
            }
        }
        if (!scrollHint.isEmpty()) {
            line.pad(10);
            line.txt(scrollHint, C_SLATE);
        }
        // Right: inspector panel describing the currently selected entry.
        line.pad(Math.max(2, HUD_PANEL_X - line.pos()));
        if (infoRow == 0) {
            int panelAdvance = Glyphs.advanceOf(Glyphs.LOCKER_INFO_PANEL);
            line.glyph(Glyphs.LOCKER_INFO_PANEL);
            line.pad(-panelAdvance + 12);
            line.txt(Glyphs.fitTextToWidth(active.info()[0], HUD_PANEL_CONTENT_MAX_PX, true),
                    active.danger() ? C_RED : C_GOLD, true);
        } else {
            line.pad(12);
            line.txt(Glyphs.fitTextToWidth(active.info()[infoRow], HUD_PANEL_CONTENT_MAX_PX, false),
                    infoRow == 3 ? C_GREEN : C_SLATE);
        }
        return line.build();
    }

    private void sendHudFooter(Player viewer) {
        TextComponent.Builder b = Component.text();
        appendKeyHint(b, "key.forward", null);
        b.append(Component.text("/", C_DIM));
        appendKeyHint(b, "key.back", " Naviguer   ");
        appendKeyHint(b, "key.left", null);
        b.append(Component.text("/", C_DIM));
        appendKeyHint(b, "key.right", " Onglet / Valeur   ");
        appendKeyHint(b, "key.jump", " Choisir   ");
        appendKeyHint(b, "key.sneak", " Quitter");
        viewer.sendActionBar(b.build());
    }

    private static void appendKeyHint(TextComponent.Builder b, String keybind, String label) {
        b.append(Component.text("[", C_DIM));
        b.append(Component.keybind(keybind, Style.style(C_GOLD, TextDecoration.BOLD)));
        b.append(Component.text("]", C_DIM));
        if (label != null) {
            b.append(Component.text(label, C_SLATE));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  KEYBOARD NAVIGATION (Forward/Back, Left/Right, Jump = select, Sneak = exit)
    // ════════════════════════════════════════════════════════════════════════

    public void handleInput(Player player, Input input) {
        if (player == null || input == null) {
            return;
        }
        StageSession s = sessions.get(player.getUniqueId());
        if (s == null || s.viewingSubMenu) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean fwd = input.isForward();
        boolean back = input.isBackward();
        boolean left = input.isLeft();
        boolean right = input.isRight();
        boolean jump = input.isJump();
        boolean sneak = input.isSneak();

        boolean stepUp = fwd && (!s.prevFwd || now - s.lastNavMillis >= 210L);
        boolean stepDown = back && (!s.prevBack || now - s.lastNavMillis >= 210L);
        boolean stepLeft = left && (!s.prevLeft || now - s.lastNavMillis >= 210L);
        boolean stepRight = right && (!s.prevRight || now - s.lastNavMillis >= 210L);
        boolean triggerSelect = jump && !s.prevJump;
        boolean triggerBack = sneak && !s.prevSneak;

        s.prevFwd = fwd;
        s.prevBack = back;
        s.prevLeft = left;
        s.prevRight = right;
        s.prevJump = jump;
        s.prevSneak = sneak;

        // Shift (sneak) = Back/Exit — with a short grace period right after opening.
        if (triggerBack && now - s.openedAtMillis >= 500L) {
            Msg.playPersonalPackSound(player, "ui.click", 0.7f, 0.92f);
            close(player, true);
            return;
        }

        if (stepUp) {
            s.lastNavMillis = now;
            moveSelection(player, s, -1);
        } else if (stepDown) {
            s.lastNavMillis = now;
            moveSelection(player, s, +1);
        } else if (stepLeft) {
            s.lastNavMillis = now;
            handleHorizontal(player, s, -1);
        } else if (stepRight) {
            s.lastNavMillis = now;
            handleHorizontal(player, s, +1);
        }

        if (triggerSelect) {
            activateCurrentEntry(player, s);
        }
    }

    private void moveSelection(Player player, StageSession s, int delta) {
        int count = buildEntries(player, s).size();
        s.selectedIndex = (s.selectedIndex + delta % count + count) % count;
        s.tabMemory[s.tabIndex] = s.selectedIndex;
        refreshScreenHud(player, s, false);
        Msg.playPersonalPackSound(player, "ui.click", 0.65f, delta < 0 ? 1.26f : 1.12f);
    }

    /** Left/Right: adjust the highlighted value when the row is adjustable, otherwise switch tabs. */
    private void handleHorizontal(Player player, StageSession s, int dir) {
        List<HudEntry> entries = buildEntries(player, s);
        HudEntry active = entries.get(Math.clamp(s.selectedIndex, 0, entries.size() - 1));
        if (active.adjustable()) {
            adjustValue(player, s, active, dir);
        } else {
            switchTab(player, s, dir);
        }
    }

    private void switchTab(Player player, StageSession s, int dir) {
        int count = HudTab.values().length;
        s.tabMemory[s.tabIndex] = s.selectedIndex;
        s.tabIndex = (s.tabIndex + dir % count + count) % count;
        s.selectedIndex = Math.clamp(s.tabMemory[s.tabIndex], 0, buildEntries(player, s).size() - 1);
        refreshScreenHud(player, s, false);
        Msg.playPersonalPackSound(player, "ui.click", 0.8f, 1.34f);
    }

    private void adjustValue(Player player, StageSession s, HudEntry entry, int dir) {
        fr.bosketball.model.PlayerProfile prof = plugin.statsManager().getOrCreate(player);
        switch (entry.id()) {
            case "ranked" -> {
                int len = RANKED_MODES.length;
                s.rankedModeCursor = (s.rankedModeCursor + dir % len + len) % len;
            }
            case "ball" -> {
                List<BallSkin> owned = new ArrayList<>(prof.unlockedBalls());
                if (!owned.isEmpty()) {
                    int idx = Math.max(0, owned.indexOf(prof.selectedBallSkin()));
                    prof.setSelectedBallSkin(owned.get((idx + dir % owned.size() + owned.size()) % owned.size()));
                    plugin.statsManager().saveProfileAsync(prof);
                    rebuildActors(player, s);
                }
            }
            case "set_cam" -> toggleSetting(prof, prof.settings()::setCinematicScoreCam, prof.settings().cinematicScoreCam());
            case "set_aim" -> toggleSetting(prof, prof.settings()::setAimTrajectory, prof.settings().aimTrajectory());
            case "set_sounds" -> toggleSetting(prof, prof.settings()::setCustomSounds, prof.settings().customSounds());
            case "set_rings" -> {
                toggleSetting(prof, prof.settings()::setFloorAuraRings, prof.settings().floorAuraRings());
                rebuildActors(player, s);
            }
            case "set_names" -> toggleSetting(prof, prof.settings()::setOverheadNameplates, prof.settings().overheadNameplates());
            default -> {
            }
        }
        refreshScreenHud(player, s, true);
        Msg.playPersonalPackSound(player, "ui.click", 0.75f, 1.30f);
    }

    private void toggleSetting(fr.bosketball.model.PlayerProfile prof, java.util.function.Consumer<Boolean> setter, boolean current) {
        setter.accept(!current);
        plugin.statsManager().saveProfileAsync(prof);
    }

    private void activateCurrentEntry(Player player, StageSession s) {
        long now = System.currentTimeMillis();
        if (now - s.lastActionMillis < 160L) {
            return;
        }
        s.lastActionMillis = now;

        List<HudEntry> entries = buildEntries(player, s);
        HudEntry active = entries.get(Math.clamp(s.selectedIndex, 0, entries.size() - 1));
        Msg.playPersonalPackSound(player, "ui.click", 0.85f, 1.22f);

        switch (active.id()) {
            case "ranked" -> {
                GameModeType targetMode = RANKED_MODES[Math.clamp(s.rankedModeCursor, 0, RANKED_MODES.length - 1)];
                if (plugin.gameManager().getQueuedMode(player.getUniqueId()) == targetMode) {
                    plugin.gameManager().leaveQueue(player, true);
                } else {
                    plugin.gameManager().joinQueue(player, targetMode);
                }
                if (sessions.containsKey(player.getUniqueId())) {
                    // joinQueue pushed its own queue BossBars — the locker HUD shows queue status instead.
                    if (plugin.bossBarHud() != null) {
                        plugin.bossBarHud().clearPlayer(player);
                    }
                    rebuildActors(player, s);
                    refreshScreenHud(player, s, true);
                }
            }
            case "practice" -> plugin.gameManager().startPractice(player);
            case "contest" -> plugin.gameManager().quickJoin(player, GameModeType.THREE_POINT_CONTEST, null);
            case "spectate" -> openSubMenu(player, () -> plugin.guiMenu().openSpectateBrowser(player));
            case "modes" -> openSubMenu(player, () -> plugin.guiMenu().openPlayModes(player));
            case "locker" -> openSubMenu(player, () -> plugin.guiMenu().openCosmetics(player));
            case "ball" -> openSubMenu(player, () -> plugin.guiMenu().openCosmetics(player));
            case "tags" -> openSubMenu(player, () -> plugin.guiMenu().openTagsVault(player, 0));
            case "ranks" -> openSubMenu(player, () -> plugin.guiMenu().openRanksGuide(player));
            case "leaderboards" -> openSubMenu(player, () -> plugin.guiMenu().openLeaderboards(player, StatsManager.LeaderboardCategory.ELO));
            case "history" -> openSubMenu(player, () -> plugin.guiMenu().openMatchHistory(player));
            case "shop_all" -> openSubMenu(player, () -> plugin.guiMenu().openShop(player));
            case "shop_balls" -> openSubMenu(player, () -> plugin.guiMenu().openShop(player, GuiMenuManager.ShopCategory.BALLS, 0));
            case "shop_dunks" -> openSubMenu(player, () -> plugin.guiMenu().openShop(player, GuiMenuManager.ShopCategory.DUNKS, 0));
            case "shop_tags" -> openSubMenu(player, () -> plugin.guiMenu().openShop(player, GuiMenuManager.ShopCategory.TAGS, 0));
            case "party" -> openSubMenu(player, () -> plugin.guiMenu().openPartyMenu(player));
            case "friends" -> openSubMenu(player, () -> plugin.guiMenu().openFriendsMenu(player));
            case "addfriend" -> openSubMenu(player, () -> plugin.guiMenu().openAddFriendOnlineMenu(player));
            case "set_cam", "set_aim", "set_sounds", "set_rings", "set_names" -> adjustValue(player, s, active, 1);
            case "settings_gui" -> openSubMenu(player, () -> plugin.guiMenu().openSettings(player));
            case "exit" -> close(player, true);
            default -> {
            }
        }
    }


    public void confirmSelection(Player player) {
        onClickConfirm(player, false);
    }

    public void scrollSelection(Player player, int delta) {
        StageSession s = player != null ? sessions.get(player.getUniqueId()) : null;
        if (s != null && !s.viewingSubMenu) {
            moveSelection(player, s, delta > 0 ? 1 : -1);
        }
    }

    public void onClickConfirm(Player player, boolean rightClick) {
        StageSession s = player != null ? sessions.get(player.getUniqueId()) : null;
        if (s != null && !s.viewingSubMenu) {
            activateCurrentEntry(player, s);
        }
    }

    public void onScrollWheel(Player player, int prevSlot, int newSlot) {
        int diff = newSlot - prevSlot;
        if (diff > 4) diff -= 9;
        if (diff < -4) diff += 9;
        scrollSelection(player, diff > 0 ? 1 : -1);
    }

    public boolean handleSpectateStopAttempt(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void close(Player player, boolean restorePosition) {
        if (player == null) {
            return;
        }
        StageSession s = sessions.remove(player.getUniqueId());
        if (s == null) {
            return;
        }
        s.viewingSubMenu = false;

        if (player.isOnline()) {
            destroyHudBars(player, s);
            player.sendActionBar(Component.empty());
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof LockerGuiHolder
                || player.getOpenInventory().getTopInventory().getHolder() instanceof GuiMenuManager.BosketHolder) {
                player.closeInventory();
            }
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setSpectatorTarget(null);
            }
        }
        despawnSessionEntities(s);

        if (player.isOnline()) {
            plugin.resyncVisibilityFor(player);
            if (Bukkit.getScoreboardManager() != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            if (plugin.sidebarManager() != null) {
                plugin.sidebarManager().clearPlayerCache(player.getUniqueId());
            }

            if (s.savedStorageContents != null) {
                player.getInventory().setStorageContents(deepClone(s.savedStorageContents));
            }
            if (s.savedArmorContents != null) {
                player.getInventory().setArmorContents(deepClone(s.savedArmorContents));
            }
            player.getInventory().setItemInOffHand(cloneItem(s.savedOffHand));
            player.getInventory().setHeldItemSlot(Math.clamp(s.savedHeldSlot, 0, 8));

            if (restorePosition) {
                GameMode targetMode = (s.previousGameMode == GameMode.SPECTATOR) ? GameMode.ADVENTURE : s.previousGameMode;
                player.setGameMode(targetMode != null ? targetMode : GameMode.ADVENTURE);
                if (s.returnLocation != null && s.returnLocation.getWorld() != null) {
                    player.teleport(s.returnLocation);
                }
                player.setAllowFlight(s.previousAllowFlight || targetMode == GameMode.CREATIVE);
                player.setFlying(s.previousFlying && player.getAllowFlight());
                if (plugin.settings().general().autoEquipLobbyPlaybook()) {
                    BallSkin.equipLobbyHotbarIfEmpty(player, false);
                }
            }
        }
    }

    public void closeAll() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                close(p, true);
            } else {
                StageSession s = sessions.remove(id);
                if (s != null) {
                    despawnSessionEntities(s);
                }
            }
        }
    }

    private void despawnSessionEntities(StageSession s) {
        for (Entity e : s.actorProps) {
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
        s.actorProps.clear();
        for (Entity e : s.stageProps) {
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
        s.stageProps.clear();
    }

    private void rebuildActors(Player viewer, StageSession s) {
        for (Entity e : s.actorProps) {
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
        s.actorProps.clear();
        if (s.stageReference != null && s.stageReference.getWorld() != null) {
            spawnPartyStageMannequins(viewer, s.stageReference, s);
        }
    }

    public void refreshPlayerStage(Player viewer, boolean rebuildActorsToo) {
        if (viewer == null) {
            return;
        }
        StageSession s = sessions.get(viewer.getUniqueId());
        if (s != null && viewer.isOnline()) {
            if (rebuildActorsToo) {
                rebuildActors(viewer, s);
            }
            refreshScreenHud(viewer, s, false);
        }
    }

    public void refreshAllOpenStages(boolean rebuildActorsToo) {
        for (Map.Entry<UUID, StageSession> entry : sessions.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            StageSession s = entry.getValue();
            if (p != null && p.isOnline()) {
                if (rebuildActorsToo) {
                    rebuildActors(p, s);
                }
                refreshScreenHud(p, s, false);
            }
        }
    }

    public void hideActivePropsFromJoiner(Player joiner) {
        for (Map.Entry<UUID, StageSession> entry : sessions.entrySet()) {
            if (entry.getKey().equals(joiner.getUniqueId())) {
                continue;
            }
            Player stageOwner = Bukkit.getPlayer(entry.getKey());
            if (stageOwner != null) {
                joiner.hidePlayer(plugin, stageOwner);
                stageOwner.hidePlayer(plugin, joiner);
            }
            StageSession s = entry.getValue();
            for (Entity e : s.stageProps) {
                if (e != null && e.isValid()) {
                    joiner.hideEntity(plugin, e);
                }
            }
            for (Entity e : s.actorProps) {
                if (e != null && e.isValid()) {
                    joiner.hideEntity(plugin, e);
                }
            }
        }
    }

    /**
     * Spawns ONLY the viewer (1 centered full-body pedestal when solo) OR their online Party members (2, 3, or 4 pedestals)!
     */
    private void spawnPartyStageMannequins(Player viewer, Location stage, StageSession s) {
        World w = stage.getWorld();
        if (w == null) {
            return;
        }

        List<Player> partyPlayers = collectOnlinePartyMembersOrdered(viewer);
        List<StageActor> actors = new ArrayList<>(partyPlayers.size());
        var party = plugin.socialManager().getParty(viewer.getUniqueId());

        for (Player member : partyPlayers) {
            boolean isViewer = member.getUniqueId().equals(viewer.getUniqueId());
            boolean isLeader = party == null ? isViewer : party.isLeader(member.getUniqueId());
            GameModeType qm = plugin.gameManager().getQueuedMode(member.getUniqueId());
            String subtitle;
            if (qm != null) {
                subtitle = "<#4ADE80>● Queued " + qm.id().toUpperCase() + "</#4ADE80>";
            } else if (party == null) {
                subtitle = "<#FFD43B>★ Solo Ready</#FFD43B>";
            } else if (isLeader) {
                subtitle = "<#FFD43B>👑 Party Leader</#FFD43B>";
            } else {
                subtitle = "<#38BDF8>● Party Member</#38BDF8>";
            }
            actors.add(new StageActor(
                member.getUniqueId(),
                member.getName(),
                buildPlayerResolvableProfile(member.getPlayerProfile(), member.getName()),
                isViewer,
                isLeader,
                subtitle
            ));
        }

        int count = Math.min(4, actors.size());
        List<Location> poses = computeSymmetricPedestalPoses(s, stage, count);

        for (int i = 0; i < count; i++) {
            StageActor actor = actors.get(i);
            Location loc = poses.get(i);

            fr.bosketball.model.PlayerProfile statsProf = plugin.statsManager().getProfile(actor.uuid());
            BallSkin ballSkin = statsProf != null ? statsProf.selectedBallSkin() : BallSkin.CLASSIC;
            boolean showRing = statsProf == null || statsProf.settings().floorAuraRings();

            Mannequin m = loc.getWorld().spawn(loc, Mannequin.class, man -> {
                man.setPersistent(false);
                man.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
                man.setImmovable(true);
                man.setGravity(false);
                man.setInvulnerable(true);
                man.setSilent(true);
                try {
                    man.setSkinParts(SkinParts.allParts());
                } catch (Throwable ignored) {
                }
                if (actor.resolvedSkin() != null) {
                    man.setProfile(actor.resolvedSkin());
                }
                man.customName(buildMannequinNameplate(actor.uuid(), actor.name(), actor.isPartyLeader()));
                man.setCustomNameVisible(true);
                man.setDescription(Msg.parse(actor.roleSubtitle()));
                if (man.getEquipment() != null && (actor.isViewer() || actor.isPartyLeader())) {
                    man.getEquipment().setItem(EquipmentSlot.HAND, ballSkin.createMannequinHeldBallItem());
                }
            });
            hideFromOthers(viewer, m);
            s.actorProps.add(m);

            if (showRing) {
                String ringKey = actor.isPartyLeader() ? "ring_gold" : (i % 2 == 1 ? "ring_frost" : "ring_ignis");
                ItemStack ringStack = new ItemStack(Material.PAPER);
                ItemMeta rm = ringStack.getItemMeta();
                if (rm != null) {
                    rm.setItemModel(new NamespacedKey("bosketball", ringKey));
                    ringStack.setItemMeta(rm);
                }
                ItemDisplay ring = loc.getWorld().spawn(loc.clone().add(0, 0.03, 0), ItemDisplay.class, d -> {
                    d.setPersistent(false);
                    d.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
                    d.setBillboard(Display.Billboard.FIXED);
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setItemStack(ringStack);
                    d.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new Quaternionf(new AxisAngle4f((float) Math.toRadians(-90.0), 1f, 0f, 0f)),
                        new Vector3f(1.18f, 1.18f, 1.18f),
                        new Quaternionf()
                    ));
                });
                hideFromOthers(viewer, ring);
                s.actorProps.add(ring);
            }
        }
    }

    public static List<Player> collectOnlinePartyMembersOrdered(Player viewer, BosketBallPlugin plugin) {
        List<Player> list = new ArrayList<>(4);
        if (viewer == null) {
            return list;
        }
        list.add(viewer);
        if (plugin != null && plugin.socialManager() != null) {
            var party = plugin.socialManager().getParty(viewer.getUniqueId());
            if (party != null) {
                for (Player mate : plugin.socialManager().onlinePartyMembers(viewer.getUniqueId())) {
                    if (list.size() >= 4) {
                        break;
                    }
                    if (mate != null && mate.isOnline() && !mate.getUniqueId().equals(viewer.getUniqueId())) {
                        list.add(mate);
                    }
                }
            }
        }
        return list;
    }

    private List<Player> collectOnlinePartyMembersOrdered(Player viewer) {
        return collectOnlinePartyMembersOrdered(viewer, plugin);
    }

    /**
     * Full-body head-to-toe camera distances (~4.9m..5.3m) so sneakers, floor aura ring &amp; crown nameplate all fit comfortably!
     */
    private List<Location> computeSymmetricPedestalPoses(StageSession s, Location stage, int count) {
        List<Location> out = new ArrayList<>(count);
        boolean usingCustomRoom = hasCustomStage();

        if (usingCustomRoom && countCustomSlotsConfigured() > 0) {
            if (count == 1) {
                if (customSlots[1] != null && customSlots[2] != null && customSlots[1].getWorld() != null) {
                    out.add(midpointPose(customSlots[1], customSlots[2], s.cameraEyeLocation));
                    return out;
                }
                if (customSlots[0] != null && customSlots[0].getWorld() != null) {
                    out.add(faceTowardCamera(customSlots[0].clone(), s.cameraEyeLocation));
                    return out;
                }
            } else if (count == 2 && customSlots[1] != null && customSlots[2] != null) {
                out.add(faceTowardCamera(customSlots[1].clone(), s.cameraEyeLocation));
                out.add(faceTowardCamera(customSlots[2].clone(), s.cameraEyeLocation));
                return out;
            } else {
                for (int i = 0; i < count; i++) {
                    if (customSlots[i] != null && customSlots[i].getWorld() != null) {
                        out.add(faceTowardCamera(customSlots[i].clone(), s.cameraEyeLocation));
                    }
                }
                if (out.size() == count) {
                    return out;
                }
                out.clear();
            }
        }

        // Balanced full-body stage framing inside the open center between Left & Right side panels:
        double[][] layout = switch (count) {
            case 1 -> new double[][]{{0.0, 4.90}};
            case 2 -> new double[][]{{-1.10, 5.05}, {1.10, 5.05}};
            case 3 -> new double[][]{{0.0, 4.75}, {-1.85, 5.20}, {1.85, 5.20}};
            default -> new double[][]{{-2.35, 5.25}, {-0.80, 5.05}, {0.80, 5.05}, {2.35, 5.25}};
        };

        World w = s.cameraEyeLocation.getWorld();
        double camYawRad = Math.toRadians(s.cameraEyeLocation.getYaw());
        double fwdX = -Math.sin(camYawRad);
        double fwdZ = Math.cos(camYawRad);
        double rightX = -Math.cos(camYawRad);
        double rightZ = -Math.sin(camYawRad);

        double baseFloorY = usingCustomRoom ? customCamera.getY() : stage.getY();

        for (int i = 0; i < count; i++) {
            double rOff = layout[i][0];
            double fDist = layout[i][1];
            double px = s.cameraEyeLocation.getX() + rightX * rOff + fwdX * fDist;
            double pz = s.cameraEyeLocation.getZ() + rightZ * rOff + fwdZ * fDist;
            double py = snapToSolidFloor(w, px, baseFloorY, pz);
            Location pLoc = new Location(w, px, py, pz, 0f, 0f);
            out.add(faceTowardCamera(pLoc, s.cameraEyeLocation));
        }
        return out;
    }

    private int countCustomSlotsConfigured() {
        int n = 0;
        for (Location l : customSlots) {
            if (l != null && l.getWorld() != null) {
                n++;
            }
        }
        return n;
    }

    private static Location midpointPose(Location a, Location b, Location cam) {
        Location mid = new Location(
            a.getWorld(),
            (a.getX() + b.getX()) * 0.5,
            (a.getY() + b.getY()) * 0.5,
            (a.getZ() + b.getZ()) * 0.5
        );
        return faceTowardCamera(mid, cam);
    }

    private static Location faceTowardCamera(Location actorLoc, Location camLoc) {
        double dx = camLoc.getX() - actorLoc.getX();
        double dz = camLoc.getZ() - actorLoc.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        actorLoc.setYaw(yaw);
        actorLoc.setPitch(0.0f);
        return actorLoc;
    }

    private static double snapToSolidFloor(World w, double x, double hintY, double z) {
        if (w == null) {
            return hintY;
        }
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int startY = (int) Math.round(hintY) + 1;
        for (int y = startY; y >= startY - 4; y--) {
            Block below = w.getBlockAt(bx, y - 1, bz);
            Block feet = w.getBlockAt(bx, y, bz);
            if (!below.isPassable() && feet.isPassable()) {
                return below.getBoundingBox().getMaxY();
            }
        }
        return hintY;
    }

    private Component buildMannequinNameplate(UUID uuid, String name, boolean leaderCrown) {
        fr.bosketball.model.PlayerProfile prof = plugin.statsManager().getProfile(uuid);
        Rank rank = prof != null ? prof.effectiveRank() : Rank.ROOKIE;
        ChatTag tag = prof != null ? prof.selectedTag() : null;
        boolean tagOk = tag != null && tag.canBeUsedBy(prof, name, false);
        StatsManager.LeaderboardBadges badges = plugin.statsManager().getBadges(uuid);

        StringBuilder mm = new StringBuilder();
        if (leaderCrown) {
            mm.append("<!shadow><gold>👑 </gold></!shadow>");
        }
        mm.append("<!shadow><white>").append(rank.pillGlyph()).append("</white></!shadow>");
        if (badges.eloPlace() >= 1 && badges.eloPlace() <= 3) {
            mm.append(" <!shadow><white>").append(Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, badges.eloPlace())).append("</white></!shadow>");
        }
        if (tagOk) {
            mm.append(" <!shadow><white>").append(tag.glyph()).append("</white></!shadow>");
        }
        mm.append(" <#FFFDF5><bold>").append(name).append("</bold></#FFFDF5>");
        return Msg.parse(mm.toString());
    }

    public static ResolvableProfile buildCustomPatchedSkin(String fallbackName, String skinTextureId) {
        String safeName = fallbackName == null || fallbackName.isBlank()
            ? "BB_NPC"
            : (fallbackName.length() > 16 ? fallbackName.substring(0, 16) : fallbackName);
        UUID staticUuid = UUID.nameUUIDFromBytes(("bosketball_npc:" + safeName + ":" + skinTextureId).getBytes(StandardCharsets.UTF_8));
        try {
            return ResolvableProfile.resolvableProfile()
                .uuid(staticUuid)
                .name(safeName)
                .skinPatch(sp -> sp
                    .body(Key.key("bosketball", "entity/player/wide/" + skinTextureId))
                    .model(org.bukkit.profile.PlayerTextures.SkinModel.CLASSIC))
                .build();
        } catch (LinkageError | RuntimeException ignored) {
            return ResolvableProfile.resolvableProfile().uuid(staticUuid).name(safeName).build();
        }
    }

    public void tickAll() {
        tickCounter++;
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            StageSession s = sessions.get(id);
            if (p == null || !p.isOnline() || s == null) {
                if (s != null) {
                    despawnSessionEntities(s);
                }
                sessions.remove(id);
                continue;
            }
            if (s.cameraAnchor != null && s.cameraAnchor.isValid()) {
                if (p.getGameMode() != GameMode.SPECTATOR) {
                    p.setGameMode(GameMode.SPECTATOR);
                }
                if (p.getSpectatorTarget() != s.cameraAnchor) {
                    p.setSpectatorTarget(s.cameraAnchor);
                }
                // Hard position lock: if anything moved the viewer off the camera anchor, snap back.
                Location anchor = s.cameraAnchor.getLocation();
                if (p.getLocation().getWorld() != null
                    && anchor.getWorld() != null
                    && p.getLocation().getWorld().equals(anchor.getWorld())
                    && p.getLocation().distanceSquared(anchor) > 0.25) {
                    p.teleport(anchor);
                    p.setSpectatorTarget(s.cameraAnchor);
                }
                p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            }
            // Refresh live queue timers & stats once per second; keep the keybind footer alive.
            if (tickCounter % 20 == 0) {
                refreshScreenHud(p, s, false);
            }
            if (tickCounter % 40 == 10 && !s.viewingSubMenu) {
                sendHudFooter(p);
            }
        }
    }

    private void hideFromOthers(Player owner, Entity entity) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(owner.getUniqueId())) {
                other.hideEntity(plugin, entity);
            }
        }
    }

    private static ResolvableProfile buildPlayerResolvableProfile(PlayerProfile livePaperProfile, String fallbackName) {
        if (livePaperProfile != null && !livePaperProfile.getProperties().isEmpty()) {
            return ResolvableProfile.resolvableProfile(livePaperProfile);
        }
        return ResolvableProfile.resolvableProfile().name(fallbackName).build();
    }

    private static ItemStack[] deepClone(ItemStack[] src) {
        if (src == null) {
            return new ItemStack[0];
        }
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = src[i] != null ? src[i].clone() : null;
        }
        return out;
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item != null ? item.clone() : null;
    }
}
