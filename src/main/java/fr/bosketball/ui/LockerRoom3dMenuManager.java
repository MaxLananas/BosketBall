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
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Listener;
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
 * Interactive Full-Body 3D Locker Room Studio:
 * <ul>
 *   <li>Frames 1..4 online Party players head-to-toe in recessed warm-lit locker bays (zero cut-off legs, zero center GUI clutter).</li>
 *   <li>Renders sleek obsidian-and-gold 3D Holographic Console Panels on the Left &amp; Right flanks of the screen.</li>
 *   <li>Supports hybrid Mouse (Left-Click / Right-Click via invisible camera-front Interaction hitbox) AND Keyboard
 *       ({@code Z/S} or {@code W/S} move, {@code Q/D} or {@code A/D} live mode/skin switch, {@code Space} select).</li>
 *   <li>Opening any sub-menu (Boutique Shop, Locker, Party &amp; Friends, Leaderboards, Ranks, Settings) keeps the player
 *       INSIDE the Locker Room — closing or clicking Back seamlessly returns to the live 3D Locker Room view!</li>
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

    private enum ConsoleEntry {
        PLAY_RANKED("PLAY RANKED", "1v1 · 2v2 · 3v3 · 5v5"),
        PRACTICE("PRACTICE COURT", "Solo Court + AI Bot"),
        THREE_PT("3-POINT CONTEST", "25-Ball All-Star Rack"),
        SHOP("BOUTIQUE SHOP", "Balls · Dunks · Tags"),
        LOCKER("MY LOCKER & TAGS", "Equip Skins & Badges"),
        SOCIAL("PARTY & FRIENDS", "Squad & Team Name"),
        RANKS("RANKS & STATS", "ELO Ladder & Top #1–#3"),
        SETTINGS("SETTINGS", "Replay Cam & Audio"),
        EXIT_LOBBY("COURT LOBBY", "Return to Outdoor Plaza");

        final String label;
        final String subtitle;

        ConsoleEntry(String label, String subtitle) {
            this.label = label;
            this.subtitle = subtitle;
        }
    }

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
        ItemDisplay cameraRig;
        Interaction clickCatcher;
        TextDisplay leftConsolePanel;
        TextDisplay leftControlsPanel;
        TextDisplay rightContextPanel;
        Location stageReference;
        long openedAtMillis;
        boolean viewingSubMenu;

        int selectedIndex = 0;
        int rankedModeCursor = 0;
        boolean prevFwd;
        boolean prevBack;
        boolean prevLeft;
        boolean prevRight;
        boolean prevJump;
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
            prev.selectedIndex = mapInitialTabToIndex(initialTab);
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
        s.selectedIndex = mapInitialTabToIndex(initialTab);

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

        ItemStack invisibleCamItem = new ItemStack(Material.PAPER);
        ItemMeta camMeta = invisibleCamItem.getItemMeta();
        if (camMeta != null) {
            camMeta.setItemModel(new NamespacedKey("bosketball", "ui_invisible"));
            invisibleCamItem.setItemMeta(camMeta);
        }
        s.cameraRig = eyeRigLoc.getWorld().spawn(eyeRigLoc, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            d.setBillboard(Display.Billboard.FIXED);
            d.setItemStack(invisibleCamItem);
            d.setTeleportDuration(1);
        });
        hideFromOthers(player, s.cameraRig);
        s.stageProps.add(s.cameraRig);

        // Invisible Interaction click-catcher directly in front of cameraRig so Left-Click & Right-Click ALWAYS register in Spectator!
        Location catcherLoc = projectScreenPoint(eyeRigLoc, 0.85, 0.0, -0.75);
        s.clickCatcher = eyeRigLoc.getWorld().spawn(catcherLoc, Interaction.class, inter -> {
            inter.setPersistent(false);
            inter.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            inter.setInteractionWidth(1.85f);
            inter.setInteractionHeight(1.85f);
            inter.setResponsive(true);
        });
        hideFromOthers(player, s.clickCatcher);
        s.stageProps.add(s.clickCatcher);

        spawnSideHolographicPanels(player, s);

        player.closeInventory();
        player.getInventory().clear();
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        player.teleport(eyeRigLoc);
        player.setGameMode(GameMode.SPECTATOR);
        player.setSpectatorTarget(s.cameraRig);

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
        refreshSidePanels(player, s);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            StageSession active = sessions.get(player.getUniqueId());
            if (active == s && player.isOnline() && active.cameraRig != null && active.cameraRig.isValid()) {
                player.setGameMode(GameMode.SPECTATOR);
                player.setSpectatorTarget(active.cameraRig);
            }
        }, 1L);

        Msg.playPersonalPackSound(player, "ui.click", 0.85f, 1.18f);
    }

    private static int mapInitialTabToIndex(TopTab tab) {
        if (tab == null) {
            return 0;
        }
        return switch (tab) {
            case PLAY -> 0;
            case TAGS -> 4;
            case FRIENDS -> 5;
            case LEADERBOARDS, PROFILE, HISTORY -> 6;
            case SETTINGS -> 7;
        };
    }

    /**
     * Opens a BosketBall chest GUI sub-menu WITHOUT exiting or teleporting out of the Locker Room!
     */
    public void openSubMenu(Player player, Runnable menuOpener) {
        StageSession s = sessions.get(player.getUniqueId());
        if (s != null) {
            s.viewingSubMenu = true;
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
        if (s.cameraRig != null && s.cameraRig.isValid()) {
            if (player.getGameMode() != GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SPECTATOR);
            }
            if (player.getSpectatorTarget() != s.cameraRig) {
                player.setSpectatorTarget(s.cameraRig);
            }
        }
        Scoreboard blank = getOrCreateBlankScoreboard();
        if (blank != null) {
            player.setScoreboard(blank);
        }
        rebuildActors(player, s);
        refreshSidePanels(player, s);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.15f);
    }

    /**
     * Projects a 3D point in camera-frustum coordinates (forwardDist, screenRight, screenUp)
     * so Left &amp; Right HUD panels sit cleanly on the sides of the screen for both default &amp; custom cameras!
     */
    private static Location projectScreenPoint(Location cam, double forwardDist, double screenRight, double screenUp) {
        org.bukkit.util.Vector fwd = cam.getDirection().normalize();
        org.bukkit.util.Vector worldUp = new org.bukkit.util.Vector(0.0, 1.0, 0.0);
        org.bukkit.util.Vector right = fwd.clone().crossProduct(worldUp);
        if (right.lengthSquared() < 1e-5) {
            right = new org.bukkit.util.Vector(-1.0, 0.0, 0.0);
        } else {
            right.normalize();
        }
        org.bukkit.util.Vector up = right.clone().crossProduct(fwd).normalize();

        org.bukkit.util.Vector pos = cam.toVector()
            .add(fwd.multiply(forwardDist))
            .add(right.multiply(screenRight))
            .add(up.multiply(screenUp));
        return new Location(cam.getWorld(), pos.getX(), pos.getY(), pos.getZ(), cam.getYaw() + 180.0f, -cam.getPitch());
    }

    private void spawnSideHolographicPanels(Player viewer, StageSession s) {
        Location cam = s.cameraEyeLocation;
        World w = cam.getWorld();
        if (w == null) {
            return;
        }

        // 1. Left Side Navigation Rail (Upper-Left flank, angled 15° inward toward viewer)
        Location leftLoc = projectScreenPoint(cam, 2.22, -1.66, -0.26);
        leftLoc.setYaw(leftLoc.getYaw() - 15.0f);
        s.leftConsolePanel = w.spawn(leftLoc, TextDisplay.class, td -> {
            td.setPersistent(false);
            td.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            td.setBillboard(Display.Billboard.FIXED);
            td.setAlignment(TextDisplay.TextAlignment.LEFT);
            td.setLineWidth(230);
            td.setShadowed(true);
            td.setSeeThrough(false);
            td.setBackgroundColor(Color.fromARGB(186, 10, 14, 24));
            td.setBrightness(new Display.Brightness(15, 15));
            td.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(0.50f, 0.50f, 0.50f),
                new Quaternionf()
            ));
        });
        hideFromOthers(viewer, s.leftConsolePanel);
        s.stageProps.add(s.leftConsolePanel);

        // 2. Bottom-Left Controls & Discord Hint Card
        Location hintLoc = projectScreenPoint(cam, 2.22, -1.66, -1.08);
        hintLoc.setYaw(hintLoc.getYaw() - 15.0f);
        s.leftControlsPanel = w.spawn(hintLoc, TextDisplay.class, td -> {
            td.setPersistent(false);
            td.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            td.setBillboard(Display.Billboard.FIXED);
            td.setAlignment(TextDisplay.TextAlignment.LEFT);
            td.setLineWidth(250);
            td.setShadowed(true);
            td.setBackgroundColor(Color.fromARGB(168, 10, 14, 24));
            td.setBrightness(new Display.Brightness(15, 15));
            td.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(0.41f, 0.41f, 0.41f),
                new Quaternionf()
            ));
        });
        hideFromOthers(viewer, s.leftControlsPanel);
        s.stageProps.add(s.leftControlsPanel);

        // 3. Right Side Live Squad & Context Inspector Card (Lower-Right flank, angled 15° inward)
        Location rightLoc = projectScreenPoint(cam, 2.22, 1.58, -1.04);
        rightLoc.setYaw(rightLoc.getYaw() + 15.0f);
        s.rightContextPanel = w.spawn(rightLoc, TextDisplay.class, td -> {
            td.setPersistent(false);
            td.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            td.setBillboard(Display.Billboard.FIXED);
            td.setAlignment(TextDisplay.TextAlignment.LEFT);
            td.setLineWidth(235);
            td.setShadowed(true);
            td.setBackgroundColor(Color.fromARGB(186, 10, 14, 24));
            td.setBrightness(new Display.Brightness(15, 15));
            td.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(0.46f, 0.46f, 0.46f),
                new Quaternionf()
            ));
        });
        hideFromOthers(viewer, s.rightContextPanel);
        s.stageProps.add(s.rightContextPanel);
    }

    private void refreshSidePanels(Player viewer, StageSession s) {
        if (viewer == null || !viewer.isOnline() || s == null) {
            return;
        }
        fr.bosketball.model.PlayerProfile prof = plugin.statsManager().getOrCreate(viewer);
        ConsoleEntry[] entries = ConsoleEntry.values();
        int sel = Math.clamp(s.selectedIndex, 0, entries.length - 1);

        // ── 1. LEFT CONSOLE RAIL ──
        if (s.leftConsolePanel != null && s.leftConsolePanel.isValid()) {
            StringBuilder mm = new StringBuilder();
            mm.append(" <#FFD43B><bold>👑 BOSKETBALL</bold></#FFD43B> <dark_gray>• LOCKER</dark_gray> \n");
            mm.append(" <#334155>━━━━━━━━━━━━━━━━━━━━</#334155> \n");
            for (int i = 0; i < entries.length; i++) {
                ConsoleEntry e = entries[i];
                if (i == sel) {
                    boolean exitRow = (e == ConsoleEntry.EXIT_LOBBY);
                    String barColor = exitRow ? "#F87171" : "#FF8C1A";
                    String txtColor = exitRow ? "#FCA5A5" : "#FFFDF5";
                    mm.append(" <").append(barColor).append("><bold>▸ ").append(e.label).append("</bold></").append(barColor).append(">")
                      .append(" <").append(txtColor).append(">◀</").append(txtColor).append("> \n");
                } else {
                    String idleColor = (e == ConsoleEntry.EXIT_LOBBY) ? "#94A3B8" : "#CBD5E1";
                    mm.append("   <").append(idleColor).append(">").append(e.label).append("</").append(idleColor).append("> \n");
                }
            }
            s.leftConsolePanel.text(Msg.parse(mm.toString()));
        }

        // ── 2. BOTTOM-LEFT DYNAMIC KEYBIND + MOUSE CONTROLS ──
        if (s.leftControlsPanel != null && s.leftControlsPanel.isValid()) {
            TextComponent.Builder kb = Component.text();
            kb.append(Component.text(" ", NamedTextColor.WHITE))
              .append(Component.keybind("key.forward", Style.style(TextColor.color(0xFFD43B), TextDecoration.BOLD)))
              .append(Component.text("/", NamedTextColor.DARK_GRAY))
              .append(Component.keybind("key.back", Style.style(TextColor.color(0xFFD43B), TextDecoration.BOLD)))
              .append(Component.text(" Move   ", TextColor.color(0xCBD5E1)))
              .append(Component.keybind("key.left", Style.style(TextColor.color(0x38BDF8), TextDecoration.BOLD)))
              .append(Component.text("/", NamedTextColor.DARK_GRAY))
              .append(Component.keybind("key.right", Style.style(TextColor.color(0x38BDF8), TextDecoration.BOLD)))
              .append(Component.text(" Switch \n ", TextColor.color(0xCBD5E1)))
              .append(Component.keybind("key.jump", Style.style(TextColor.color(0x4ADE80), TextDecoration.BOLD)))
              .append(Component.text(" / ", NamedTextColor.DARK_GRAY))
              .append(Component.text("Left-Click", Style.style(TextColor.color(0x4ADE80), TextDecoration.BOLD)))
              .append(Component.text(" Open  •  ", TextColor.color(0xCBD5E1)))
              .append(Component.text("Right-Click", Style.style(TextColor.color(0xFBBF24), TextDecoration.BOLD)))
              .append(Component.text(" Menu ", TextColor.color(0xCBD5E1)));
            s.leftControlsPanel.text(kb.asComponent());
        }

        // ── 3. RIGHT SQUAD DOSSIER & LIVE CONTEXT INSPECTOR ──
        if (s.rightContextPanel != null && s.rightContextPanel.isValid()) {
            GameModeType queued = plugin.gameManager().getQueuedMode(viewer.getUniqueId());
            var party = plugin.socialManager().getParty(viewer.getUniqueId());
            int partySize = plugin.socialManager().partySizeOrOne(viewer.getUniqueId());
            String squadLabel = (party != null && party.hasCustomTeamName())
                ? "<#FBBF24><bold>" + party.teamName() + "</bold></#FBBF24> <gray>(" + partySize + "/4)</gray>"
                : (party != null ? "<#4ADE80>Squad (" + partySize + "/4)</#4ADE80>" : "<#94A3B8>Solo Ready (1/4)</#94A3B8>");

            StringBuilder rc = new StringBuilder();
            rc.append(" <!shadow><white>").append(Glyphs.CHAT_ICON_COIN).append("</white></!shadow> <#FDE047><bold>")
              .append(prof.coins()).append(" Coins</bold></#FDE047>  <dark_gray>•</dark_gray>  <#38BDF8><bold>")
              .append(prof.elo()).append(" ELO</bold></#38BDF8> \n");
            rc.append(" <gray>Squad:</gray> ").append(squadLabel).append(" \n");
            if (queued != null) {
                int qc = plugin.gameManager().queuedCount(queued);
                rc.append(" <#4ADE80><bold>● QUEUED ").append(queued.id().toUpperCase())
                  .append(" (").append(qc).append("/").append(queued.maxPlayers()).append(")</bold></#4ADE80> \n");
            }
            rc.append(" <#334155>━━━━━━━━━━━━━━━━━━━━</#334155> \n");

            ConsoleEntry active = entries[sel];
            switch (active) {
                case PLAY_RANKED -> {
                    GameModeType m = RANKED_MODES[Math.clamp(s.rankedModeCursor, 0, RANKED_MODES.length - 1)];
                    int qCount = plugin.gameManager().queuedCount(m);
                    boolean isThisQueued = (queued == m);
                    rc.append(" <#FF8C1A><bold>").append(active.label).append("</bold></#FF8C1A> \n");
                    rc.append(" <aqua>◀</aqua> <white><bold>").append(m.smallCapsTitle()).append("</bold></white> <aqua>▶</aqua> <dark_gray>(")
                      .append(qCount).append("/").append(m.maxPlayers()).append(")</dark_gray> \n");
                    rc.append(" <gray>Win +ELO & +Coins in Ranked!</gray> \n");
                    rc.append(isThisQueued
                        ? " <#F87171>▸ Click/Space: Leave Queue</#F87171> \n"
                        : " <#4ADE80>▸ Click/Space: Join Queue</#4ADE80> \n");
                    rc.append(" <#FBBF24>▸ Right-Click: All Modes GUI</#FBBF24> ");
                }
                case PRACTICE -> {
                    rc.append(" <#38BDF8><bold>SOLO PRACTICE COURT</bold></#38BDF8> \n");
                    rc.append(" <gray>Private court • 24s Shot Clock</gray> \n");
                    rc.append(" <gray>Bow-Pullback Shooting & AI Bot</gray> \n");
                    rc.append(" <#4ADE80>▸ Click/Space: Enter Court</#4ADE80> ");
                }
                case THREE_PT -> {
                    rc.append(" <#FBBF24><bold>3-POINT SHOOTOUT</bold></#FBBF24> \n");
                    rc.append(" <gray>5 Racks • 25 Balls • 60 Seconds</gray> \n");
                    rc.append(" <gray>Personal Best:</gray> <#FDE047><bold>").append(prof.threePointContestBest()).append(" PTS</bold></#FDE047> \n");
                    rc.append(" <#4ADE80>▸ Click/Space: Start Contest</#4ADE80> ");
                }
                case SHOP -> {
                    int unowned = plugin.guiMenu().countUnownedShopItems(prof);
                    rc.append(" <#FDE047><bold>COSMETICS BOUTIQUE</bold></#FDE047> \n");
                    rc.append(" <gray>Organized Shelves by Category:</gray> \n");
                    rc.append(" <white>Balls • Dunk Styles • Chat Tags</white> \n");
                    rc.append(" <#4ADE80>▸ Click/Space: Browse (").append(unowned).append(" left)</#4ADE80> ");
                }
                case LOCKER -> {
                    ChatTag tag = prof.selectedTag();
                    String tagStr = (tag != null && tag.canBeUsedBy(prof, viewer.getName(), false))
                        ? "<!shadow><white>" + tag.glyph() + "</white></!shadow>"
                        : "<dark_gray>None</dark_gray>";
                    rc.append(" <#E879F9><bold>EQUIPPED LOADOUT</bold></#E879F9> \n");
                    rc.append(" <gray>Ball:</gray> <aqua>◀</aqua> <gold><bold>").append(prof.selectedBallSkin().plainName()).append("</bold></gold> <aqua>▶</aqua> \n");
                    rc.append(" <gray>Dunk:</gray> <#FB923C>").append(prof.selectedDunkStyle().displayName()).append("</#FB923C> <dark_gray>•</dark_gray> ").append(tagStr).append(" \n");
                    rc.append(" <#4ADE80>▸ Click: Balls & Dunks</#4ADE80> <dark_gray>|</dark_gray> <#38BDF8>R-Click: Tags</#38BDF8> ");
                }
                case SOCIAL -> {
                    int onlineFriends = plugin.socialManager().countOnlineFriends(viewer.getUniqueId());
                    int reqs = plugin.socialManager().incomingFriendRequests(viewer.getUniqueId()).size();
                    rc.append(" <#4ADE80><bold>PARTY & FRIENDS</bold></#4ADE80> \n");
                    rc.append(" <gray>Online Friends:</gray> <#4ADE80>").append(onlineFriends).append("</#4ADE80>");
                    if (reqs > 0) {
                        rc.append(" <#FBBF24>(").append(reqs).append(" new!)</#FBBF24>");
                    }
                    rc.append(" \n");
                    rc.append(" <gray>Add friends & set custom Team Name</gray> \n");
                    rc.append(" <#4ADE80>▸ Click: Friends</#4ADE80> <dark_gray>|</dark_gray> <#FBBF24>R-Click: Party</#FBBF24> ");
                }
                case RANKS -> {
                    Rank next = prof.competitiveRank().nextCompetitiveRank();
                    String nextLine = next != null
                        ? "<gray>Next:</gray> <#FBBF24>+" + Math.max(0, next.minElo() - prof.elo()) + " ELO → " + next.plainDisplayName() + "</#FBBF24>"
                        : "<#FDE047>Maximum Competitive Tier (MVP)!</#FDE047>";
                    rc.append(" <#60A5FA><bold>RANKS & LEADERBOARDS</bold></#60A5FA> \n");
                    rc.append(" ").append(nextLine).append(" \n");
                    rc.append(" <gray>Wins:</gray> <#4ADE80>").append(prof.wins()).append("</#4ADE80> <dark_gray>•</dark_gray> <gray>Points:</gray> <white>").append(prof.points()).append("</white> \n");
                    rc.append(" <#4ADE80>▸ Click: Top Players</#4ADE80> <dark_gray>|</dark_gray> <#FBBF24>R-Click: Ranks</#FBBF24> ");
                }
                case SETTINGS -> {
                    rc.append(" <#94A3B8><bold>MATCH PREFERENCES</bold></#94A3B8> \n");
                    rc.append(" <gray>Toggle Goal Replay Camera, 3D</gray> \n");
                    rc.append(" <gray>Aim Ribbon, Aura Rings & Sounds</gray> \n");
                    rc.append(" <#4ADE80>▸ Click/Space: Open Settings</#4ADE80> ");
                }
                case EXIT_LOBBY -> {
                    rc.append(" <#F87171><bold>EXIT LOCKER ROOM</bold></#F87171> \n");
                    rc.append(" <gray>Step back out onto the main</gray> \n");
                    rc.append(" <gray>BosketBall Court Plaza.</gray> \n");
                    rc.append(" <#F87171>▸ Click/Space: Return to Lobby</#F87171> ");
                }
            }
            s.rightContextPanel.text(Msg.parse(rc.toString()));
        }
    }

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

        boolean stepUp = fwd && (!s.prevFwd || now - s.lastNavMillis >= 210L);
        boolean stepDown = back && (!s.prevBack || now - s.lastNavMillis >= 210L);
        boolean stepLeft = left && (!s.prevLeft || now - s.lastNavMillis >= 210L);
        boolean stepRight = right && (!s.prevRight || now - s.lastNavMillis >= 210L);
        boolean triggerSelect = jump && !s.prevJump;

        s.prevFwd = fwd;
        s.prevBack = back;
        s.prevLeft = left;
        s.prevRight = right;
        s.prevJump = jump;

        if (stepUp) {
            s.lastNavMillis = now;
            moveSelection(player, s, -1);
        } else if (stepDown) {
            s.lastNavMillis = now;
            moveSelection(player, s, +1);
        } else if (stepLeft) {
            s.lastNavMillis = now;
            cycleHorizontalOption(player, s, -1);
        } else if (stepRight) {
            s.lastNavMillis = now;
            cycleHorizontalOption(player, s, +1);
        }

        if (triggerSelect) {
            activateCurrentEntry(player, s, false);
        }
    }

    private void moveSelection(Player player, StageSession s, int delta) {
        int count = ConsoleEntry.values().length;
        s.selectedIndex = (s.selectedIndex + delta % count + count) % count;
        refreshSidePanels(player, s);
        Msg.playPersonalPackSound(player, "ui.click", 0.65f, delta < 0 ? 1.26f : 1.12f);
    }

    private void cycleHorizontalOption(Player player, StageSession s, int dir) {
        ConsoleEntry active = ConsoleEntry.values()[Math.clamp(s.selectedIndex, 0, ConsoleEntry.values().length - 1)];
        fr.bosketball.model.PlayerProfile prof = plugin.statsManager().getOrCreate(player);

        if (active == ConsoleEntry.PLAY_RANKED) {
            int len = RANKED_MODES.length;
            s.rankedModeCursor = (s.rankedModeCursor + dir % len + len) % len;
            refreshSidePanels(player, s);
            Msg.playPersonalPackSound(player, "ui.click", 0.75f, 1.30f);
        } else if (active == ConsoleEntry.LOCKER) {
            List<BallSkin> owned = new ArrayList<>(prof.unlockedBalls());
            if (!owned.isEmpty()) {
                int idx = Math.max(0, owned.indexOf(prof.selectedBallSkin()));
                BallSkin next = owned.get((idx + dir % owned.size() + owned.size()) % owned.size());
                prof.setSelectedBallSkin(next);
                plugin.statsManager().saveProfileAsync(prof);
                rebuildActors(player, s);
                refreshSidePanels(player, s);
                Msg.playPersonalPackSound(player, "ui.click", 0.8f, 1.28f);
            }
        } else {
            moveSelection(player, s, dir);
        }
    }

    private void activateCurrentEntry(Player player, StageSession s, boolean rightClick) {
        long now = System.currentTimeMillis();
        if (now - s.lastActionMillis < 160L) {
            return;
        }
        s.lastActionMillis = now;

        ConsoleEntry active = ConsoleEntry.values()[Math.clamp(s.selectedIndex, 0, ConsoleEntry.values().length - 1)];
        Msg.playPersonalPackSound(player, "ui.click", 0.85f, 1.22f);

        switch (active) {
            case PLAY_RANKED -> {
                if (rightClick) {
                    openSubMenu(player, () -> plugin.guiMenu().openPlayModes(player));
                } else {
                    GameModeType targetMode = RANKED_MODES[Math.clamp(s.rankedModeCursor, 0, RANKED_MODES.length - 1)];
                    plugin.gameManager().joinQueue(player, targetMode);
                    if (sessions.containsKey(player.getUniqueId())) {
                        rebuildActors(player, s);
                        refreshSidePanels(player, s);
                    }
                }
            }
            case PRACTICE -> plugin.gameManager().startPractice(player);
            case THREE_PT -> plugin.gameManager().quickJoin(player, GameModeType.THREE_POINT_CONTEST, null);
            case SHOP -> openSubMenu(player, () -> plugin.guiMenu().openShop(player));
            case LOCKER -> {
                if (rightClick) {
                    openSubMenu(player, () -> plugin.guiMenu().openTagsVault(player, 0));
                } else {
                    openSubMenu(player, () -> plugin.guiMenu().openCosmetics(player));
                }
            }
            case SOCIAL -> {
                if (rightClick) {
                    openSubMenu(player, () -> plugin.guiMenu().openPartyMenu(player));
                } else {
                    openSubMenu(player, () -> plugin.guiMenu().openFriendsMenu(player));
                }
            }
            case RANKS -> {
                if (rightClick) {
                    openSubMenu(player, () -> plugin.guiMenu().openRanksGuide(player));
                } else {
                    openSubMenu(player, () -> plugin.guiMenu().openLeaderboards(player, StatsManager.LeaderboardCategory.ELO));
                }
            }
            case SETTINGS -> openSubMenu(player, () -> plugin.guiMenu().openSettings(player));
            case EXIT_LOBBY -> close(player, true);
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
            activateCurrentEntry(player, s, rightClick);
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
            refreshSidePanels(viewer, s);
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
                refreshSidePanels(p, s);
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
            if (s.cameraRig != null && s.cameraRig.isValid()) {
                if (p.getGameMode() != GameMode.SPECTATOR) {
                    p.setGameMode(GameMode.SPECTATOR);
                }
                if (p.getSpectatorTarget() != s.cameraRig) {
                    p.setSpectatorTarget(s.cameraRig);
                }
            }
            // Refresh live queue timers & stats once per second
            if (tickCounter % 20 == 0) {
                refreshSidePanels(p, s);
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
