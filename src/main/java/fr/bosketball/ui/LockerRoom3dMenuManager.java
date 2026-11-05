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
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
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
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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

    private record HudEntry(
        String id,
        String label,
        String value,
        boolean adjustable,
        boolean danger,
        String[] info
    ) {
    }

    private static final int HUD_VIRTUAL_WIDTH = 560;
    private static final int HUD_LEFT = 14;
    private static final int HUD_RIGHT = HUD_VIRTUAL_WIDTH - 12;
    private static final int HUD_VALUE_X = HUD_LEFT + 132;
    private static final int HUD_PANEL_X = HUD_VIRTUAL_WIDTH - 262;
    private static final int HUD_PANEL_CONTENT_MAX_PX = 228;

    private static final int HUD_BAR_COUNT = 6;
    private static final int HUD_ROWS_PER_PAGE = 4;

    private static final TextColor C_ORANGE = TextColor.color(0xFF8C1A);
    private static final TextColor C_GOLD = TextColor.color(0xFFD43B);
    private static final TextColor C_CREAM = TextColor.color(0xFFFDF5);
    private static final TextColor C_SLATE = TextColor.color(0xB8C7DC);
    private static final TextColor C_DIM = TextColor.color(0x94A8C0);
    private static final TextColor C_PALE = TextColor.color(0xE4EEFA);
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

    private record SpinningRing(ItemDisplay display, float scale, double speed, double phase) {
    }

    private record OrbitingBall(
        ItemDisplay display,
        Location center,
        double radius,
        double height,
        double speed,
        double phase
    ) {
    }

    private static final class StageSession {
        UUID viewerId;
        Location returnLocation;
        GameMode previousGameMode;
        boolean previousAllowFlight;
        boolean previousFlying;
        float previousWalkSpeed = 0.2f;
        float previousFlySpeed = 0.1f;
        ItemStack[] savedStorageContents;
        ItemStack[] savedArmorContents;
        ItemStack savedOffHand;
        int savedHeldSlot;

        Location cameraEyeLocation;
        ArmorStand cameraAnchor;
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
        final List<Location> actorBases = new ArrayList<>();
        final List<SpinningRing> spinningRings = new ArrayList<>();
        final List<OrbitingBall> orbitingBalls = new ArrayList<>();
        TextDisplay showcaseMarquee;
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
                        boolean runway = Math.abs(dx) <= 2 && dz >= -4 && dz <= 6;
                        boolean runwayEdge = (dx == -3 || dx == 3) && dz >= -4 && dz <= 6;
                        boolean championLine = dz == 5 && Math.abs(dx) <= 6;
                        b.setType(championLine
                            ? Material.GOLD_BLOCK
                            : runwayEdge
                                ? Material.ORANGE_CONCRETE
                                : runway || centerInlay ? Material.STRIPPED_SPRUCE_WOOD : Material.STRIPPED_OAK_WOOD, false);
                    } else if (dy == 7) {
                        boolean ceilingLight = (dz == 2 || dz == 5 || dz == -1) && (dx == -5 || dx == -2 || dx == 2 || dx == 5);
                        b.setType(ceilingLight ? Material.OCHRE_FROGLIGHT : Material.DARK_OAK_PLANKS, false);
                    } else {
                        boolean sideLightStrip = (dx == -10 || dx == 10)
                            && (dz == -3 || dz == 1 || dz == 5)
                            && dy >= 2 && dy <= 5;
                        b.setType(sideLightStrip ? Material.OCHRE_FROGLIGHT : Material.DEEPSLATE_TILES, false);
                    }
                }
            }
        }

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

        for (int dx = -8; dx <= 8; dx++) {
            world.getBlockAt(ox + dx, oy + 6, oz + 6).setType(Material.DEEPSLATE_BRICKS, false);
            if (dx == -7 || dx == -4 || dx == -1 || dx == 1 || dx == 4 || dx == 7) {
                world.getBlockAt(ox + dx, oy + 5, oz + 6).setType(Material.OCHRE_FROGLIGHT, false);
            }
        }

        for (int dx = -8; dx <= 8; dx++) {
            world.getBlockAt(ox + dx, oy + 4, oz + 6).setType(Material.DARK_OAK_SLAB, false);
        }
        for (int tx : new int[]{-8, -5, -2, 2, 5, 8}) {
            world.getBlockAt(ox + tx, oy + 5, oz + 6)
                .setType((tx & 1) == 0 ? Material.GOLD_BLOCK : Material.AMETHYST_BLOCK, false);
        }

        for (int lx : new int[]{-4, 0, 4}) {
            world.getBlockAt(ox + lx, oy, oz - 2).setType(Material.OCHRE_FROGLIGHT, false);
        }

        int halfX = 15;
        int halfZ = 15;
        int top = oy + 11;
        for (int dx = -halfX; dx <= halfX; dx++) {
            for (int dz = -halfZ; dz <= halfZ; dz++) {
                boolean edge = (Math.abs(dx) == halfX || Math.abs(dz) == halfZ);

                world.getBlockAt(ox + dx, oy - 1, oz + dz).setType(Material.DEEPSLATE_TILES, false);
                world.getBlockAt(ox + dx, oy - 2, oz + dz).setType(Material.DEEPSLATE, false);
                if (!edge) {
                    continue;
                }
                for (int dy = 0; dy <= top - oy; dy++) {
                    Material mat = (dy % 4 == 2 && (Math.abs(dx) + Math.abs(dz)) % 4 == 0)
                        ? Material.OCHRE_FROGLIGHT
                        : Material.DEEPSLATE_BRICKS;
                    world.getBlockAt(ox + dx, oy + dy, oz + dz).setType(mat, false);
                }

                world.getBlockAt(ox + dx, top, oz + dz).setType(Material.DARK_OAK_PLANKS, false);
            }
        }
        for (int dx = -halfX; dx <= halfX; dx++) {
            for (int dz = -halfZ; dz <= halfZ; dz++) {
                world.getBlockAt(ox + dx, top, oz + dz).setType(Material.DARK_OAK_PLANKS, false);
            }
        }

        int minChunkX = (ox - halfX - 16) >> 4;
        int maxChunkX = (ox + halfX + 16) >> 4;
        int minChunkZ = (oz - halfZ - 16) >> 4;
        int maxChunkZ = (oz + halfZ + 16) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                world.getChunkAt(cx, cz).setForceLoaded(true);
            }
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

            applyInitialTab(prev, initialTab);
            returnFromSubMenu(player);
            return;
        }

        Location preservedReturn = player.getLocation().clone();
        GameMode preservedMode = player.getGameMode();
        boolean preservedAllowFlight = player.getAllowFlight();
        boolean preservedFlying = player.isFlying();
        float preservedWalkSpeed = player.getWalkSpeed();
        float preservedFlySpeed = player.getFlySpeed();
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
        s.previousWalkSpeed = preservedWalkSpeed;
        s.previousFlySpeed = preservedFlySpeed;
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

        Location anchorLoc = eyeRigLoc.clone().add(0.0, -1.0, 0.0);
        s.cameraAnchor = eyeRigLoc.getWorld().spawn(anchorLoc, ArmorStand.class, stand -> {
            stand.setPersistent(false);
            stand.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setCollidable(false);
            stand.setCanPickupItems(false);
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setArms(false);
            stand.setBasePlate(false);
            stand.setCustomNameVisible(false);
            stand.setHeadPose(new EulerAngle(Math.toRadians(eyeRigLoc.getPitch()), 0.0, 0.0));
        });
        double eyeDelta = eyeRigLoc.getY() - s.cameraAnchor.getEyeLocation().getY();
        if (Math.abs(eyeDelta) > 0.001) {
            anchorLoc.add(0.0, eyeDelta, 0.0);
            s.cameraAnchor.teleport(anchorLoc);
        }
        s.cameraAnchor.setRotation(eyeRigLoc.getYaw(), eyeRigLoc.getPitch());
        hideFromOthers(player, s.cameraAnchor);
        s.stageProps.add(s.cameraAnchor);

        player.closeInventory();
        player.getInventory().clear();
        player.setFlying(false);
        player.setAllowFlight(false);
        applyZeroMovementLock(player);
        player.teleport(s.cameraAnchor.getLocation());
        player.setGameMode(GameMode.SPECTATOR);
        player.setSpectatorTarget(s.cameraAnchor);
        applyZeroMovementLock(player);

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
                applyZeroMovementLock(player);
            }
        }, 1L);

        Msg.playPersonalPackSound(player, "ui.click", 0.85f, 1.18f);
    }

    private static void applyZeroMovementLock(Player player) {
        if (player == null) {
            return;
        }
        player.setSprinting(false);
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
    }

    private static void restoreMovementSpeeds(Player player, StageSession s) {
        if (player == null || s == null) {
            return;
        }
        player.setWalkSpeed(Math.clamp(s.previousWalkSpeed, -1.0f, 1.0f));
        player.setFlySpeed(Math.clamp(s.previousFlySpeed, -1.0f, 1.0f));
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
            applyZeroMovementLock(player);
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
                    ? "⏳ In queue — " + modeQueued + "/" + mode.maxPlayers() + " players · " + fmtDuration(plugin.gameManager().getQueueElapsedSeconds(viewer.getUniqueId()))
                    : modeQueued + " player" + (modeQueued == 1 ? "" : "s") + " queued for this mode";
                entries.add(new HudEntry("ranked", "PLAY RANKED", "◀ " + mode.plainName().toUpperCase(java.util.Locale.ROOT) + " ▶", true, false,
                    new String[]{"RANKED MATCH " + mode.id().toUpperCase(java.util.Locale.ROOT),
                        "Face players at your skill level,",
                        "earn ELO, Coins and XP.", queueStatus}));
                entries.add(new HudEntry("practice", "PRACTICE COURT", null, false, false,
                    new String[]{"PRACTICE COURT", "Private solo court with a defender bot.", "Perfect for warming up shots and dunks.", "Instant access, no queue"}));
                int contestQueued = plugin.gameManager().queuedCount(GameModeType.THREE_POINT_CONTEST);
                entries.add(new HudEntry("contest", "3-POINT CONTEST", null, false, false,
                    new String[]{"3-POINT CONTEST", "25 balls, 5 racks, Money Balls x2.", "Beat the clock and the leaderboard.", contestQueued + " player" + (contestQueued == 1 ? "" : "s") + " in queue"}));
                entries.add(new HudEntry("spectate", "SPECTATE MATCH", null, false, false,
                    new String[]{"SPECTATOR MODE", "Watch live matches", "with a free cinematic camera.", plugin.gameManager().activePlayerCount() + " players in matches"}));
                entries.add(new HudEntry("modes", "ALL GAME MODES", null, false, false,
                    new String[]{"ALL GAME MODES", "Browse every arena and mode", "in the detailed menu.", Bukkit.getOnlinePlayers().size() + " players online"}));
            }
            case PROFILE -> {
                entries.add(new HudEntry("locker", "MY LOCKER", null, false, false,
                    new String[]{"MY LOCKER", "Equip your 3D basketballs", "and your unlocked dunk styles.", prof.unlockedBalls().size() + " balls · " + prof.unlockedDunks().size() + " dunks"}));
                entries.add(new HudEntry("ball", "EQUIPPED BALL", "◀ " + truncate(prof.selectedBallSkin().plainName().toUpperCase(java.util.Locale.ROOT), 12) + " ▶", true, false,
                    new String[]{"EQUIPPED BALL", "Left/Right to switch balls live —", "watch your player at center court!", prof.unlockedBalls().size() + " balls unlocked"}));
                ChatTag tag = prof.selectedTag();
                entries.add(new HudEntry("tags", "CHAT TAGS", null, false, false,
                    new String[]{"CHAT TAGS", "Choose the badge shown", "in front of your name in chat.", tag != null ? "Current: " + tag.displayName() : "No tag equipped"}));
                Rank nextRank = prof.competitiveRank().nextCompetitiveRank();
                entries.add(new HudEntry("ranks", "RANKS & STATS", null, false, false,
                    new String[]{"RANKS & STATS", prof.competitiveRank().plainDisplayName() + " · " + prof.elo() + " ELO · " + prof.wins() + " wins",
                        nextRank != null ? Math.max(0, nextRank.minElo() - prof.elo()) + " ELO to " + nextRank.plainDisplayName() + "." : "Maximum rank reached. Legend.",
                        prof.points() + " career points scored"}));
                entries.add(new HudEntry("leaderboards", "LEADERBOARDS", null, false, false,
                    new String[]{"LEADERBOARDS", "The best players on the server:", "ELO, points, coins, wins...", "Top 10 updated live"}));
                entries.add(new HudEntry("history", "MATCH HISTORY", null, false, false,
                    new String[]{"MATCH HISTORY", "Your latest matches, scores", "and opponents faced.", prof.matchHistory().size() + " matches recorded"}));
            }
            case SHOP -> {
                int unowned = plugin.guiMenu().countUnownedShopItems(prof);
                entries.add(new HudEntry("shop_all", "SHOP", null, false, false,
                    new String[]{"SHOP", "3D basketballs, signature dunks", "and collector tags on display.", unowned + " items to unlock · " + prof.coins() + " coins"}));
                entries.add(new HudEntry("shop_balls", "BASKETBALLS", null, false, false,
                    new String[]{"3D BASKETBALLS", BallSkin.values().length + " fully modeled 3D balls,", "from classic to cosmic.", prof.unlockedBalls().size() + "/" + BallSkin.values().length + " unlocked"}));
                entries.add(new HudEntry("shop_dunks", "DUNK STYLES", null, false, false,
                    new String[]{"DUNK STYLES", DunkStyle.values().length + " dunk animations", "to equip in your locker.", prof.unlockedDunks().size() + "/" + DunkStyle.values().length + " unlocked"}));
                entries.add(new HudEntry("shop_tags", "CHAT TAGS", null, false, false,
                    new String[]{"CHAT TAG SHOP", "Dozens of collector badges", "to stand out in chat.", prof.unlockedTags().size() + " tags owned"}));
            }
            case SOCIAL -> {
                String squadStatus = party != null
                    ? "Party active (" + partySize + "/4)" + (party.hasCustomTeamName() ? " · \"" + party.teamName() + "\"" : "")
                    : "You are playing solo right now";
                entries.add(new HudEntry("party", "PARTY", party != null ? partySize + "/4" : "SOLO", false, false,
                    new String[]{"PARTY / SQUAD", "Invite friends, name your team", "and queue together (max 4).", squadStatus}));
                int onlineFriends = plugin.socialManager().countOnlineFriends(viewer.getUniqueId());
                int reqs = plugin.socialManager().incomingFriendRequests(viewer.getUniqueId()).size();
                entries.add(new HudEntry("friends", "FRIENDS", reqs > 0 ? reqs + " NEW" : null, false, false,
                    new String[]{"FRIENDS", "Manage friends, 1v1 challenges", "and pending requests.", onlineFriends + " friends online" + (reqs > 0 ? " · " + reqs + " request(s)!" : "")}));
                entries.add(new HudEntry("addfriend", "ADD A FRIEND", null, false, false,
                    new String[]{"ADD A FRIEND", "Choose from the players", "currently online.", Bukkit.getOnlinePlayers().size() + " players connected"}));
            }
            case SETTINGS -> {
                var st = prof.settings();
                entries.add(new HudEntry("set_cam", "GOAL REPLAY CAMERA", yn(st.cinematicScoreCam()), true, false,
                    new String[]{"GOAL REPLAY CAMERA", "Automatic cinematic camera", "after every basket scored.", "Currently: " + onOff(st.cinematicScoreCam())}));
                entries.add(new HudEntry("set_aim", "AIM TRAJECTORY", yn(st.aimTrajectory()), true, false,
                    new String[]{"AIM TRAJECTORY", "Shows the 3D aiming trajectory", "while you charge your shot.", "Currently: " + onOff(st.aimTrajectory())}));
                entries.add(new HudEntry("set_sounds", "CUSTOM SOUNDS", yn(st.customSounds()), true, false,
                    new String[]{"CUSTOM SOUNDS", "BosketBall pack sounds:", "swishes, dunks, buzzer, UI...", "Currently: " + onOff(st.customSounds())}));
                entries.add(new HudEntry("set_rings", "FLOOR AURA RINGS", yn(st.floorAuraRings()), true, false,
                    new String[]{"FLOOR AURA RINGS", "Colored rings under players", "(teams, lobby, locker room).", "Currently: " + onOff(st.floorAuraRings())}));
                entries.add(new HudEntry("set_names", "NAMEPLATES", yn(st.overheadNameplates()), true, false,
                    new String[]{"NAMEPLATES", "Rank badges and tags shown", "above players in matches.", "Currently: " + onOff(st.overheadNameplates())}));
                entries.add(new HudEntry("settings_gui", "ALL SETTINGS…", null, false, false,
                    new String[]{"ALL SETTINGS", "Open the complete", "BosketBall preferences panel.", "Audio, visuals, gameplay"}));
            }
        }
        entries.add(new HudEntry("exit", "EXIT TO LOBBY", null, false, true,
            new String[]{"EXIT LOCKER ROOM", "Return to the lobby plaza,", "near the Play & Shop NPCs.", "Tip: Sneak exits at any time"}));
        return entries;
    }

    private static String yn(boolean value) {
        return value ? "◀ ON ▶" : "◀ OFF ▶";
    }

    private static String onOff(boolean value) {
        return value ? "enabled" : "disabled";
    }

    private static String fmtDuration(long totalSeconds) {
        long s = Math.max(0, totalSeconds);
        return (s / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60);
    }

    private static String truncate(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, Math.max(1, maxChars - 1)) + "…";
    }

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
        updateShowcaseMarquee(s, active);

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
        line.txt("● ", C_GREEN, true).txt(online + " online", C_CREAM);
        GameModeType queued = plugin.gameManager().getQueuedMode(viewer.getUniqueId());
        if (queued != null) {
            int cur = plugin.gameManager().queuedCount(queued);
            line.txt("   ⏳ ", C_DIM).txt("Queue " + queued.id().toUpperCase(java.util.Locale.ROOT) + " " + cur + "/" + queued.maxPlayers(), C_GREEN);
        }

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

    private Component buildRowLine(HudEntry entry, boolean selected, HudEntry active, int infoRow, String scrollHint) {
        HudLine line = new HudLine();
        line.pad(HUD_LEFT);
        if (entry != null && selected) {
            char slant = entry.danger() ? Glyphs.LOCKER_SELECT_SLANT_EXIT : Glyphs.LOCKER_SELECT_SLANT;
            int slantAdvance = Glyphs.advanceOf(slant);
            line.glyph(slant);
            line.pad(-slantAdvance + 12);
            line.txt(entry.danger() ? "✖" : "✦", TextColor.color(0xFFF7D6), true);
            line.pad(Math.max(2, HUD_LEFT + 30 - line.pos()));
            line.txt(entry.label(), C_ON_SLANT, true);
            if (entry.value() != null) {
                line.pad(Math.max(6, HUD_VALUE_X - line.pos()));
                line.txt(entry.value(), entry.adjustable() ? C_ON_SLANT : C_ON_SLANT_SOFT, true);
            }
        } else if (entry != null) {
            int idleAdvance = Glyphs.advanceOf(Glyphs.LOCKER_ROW_IDLE);
            line.glyph(Glyphs.LOCKER_ROW_IDLE);
            line.pad(-idleAdvance + 12);
            line.txt(entry.danger() ? "✖" : "•", entry.danger() ? C_RED : C_GOLD, true);
            line.pad(Math.max(2, HUD_LEFT + 30 - line.pos()));
            line.txt(entry.label(), entry.danger() ? TextColor.color(0xFFB4B4) : C_CREAM);
            if (entry.value() != null) {
                line.pad(Math.max(6, HUD_VALUE_X - line.pos()));
                line.txt(entry.value(), C_PALE);
            }
        }
        if (!scrollHint.isEmpty()) {
            line.pad(10);
            line.txt(scrollHint, C_SLATE);
        }

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
                    infoRow == 3 ? C_GREEN : C_PALE);
        }
        return line.build();
    }

    private void sendHudFooter(Player viewer) {
        TextComponent.Builder b = Component.text();
        appendKeyHint(b, "key.forward", null);
        b.append(Component.text("/", C_DIM));
        appendKeyHint(b, "key.back", " Navigate   ");
        appendKeyHint(b, "key.left", null);
        b.append(Component.text("/", C_DIM));
        appendKeyHint(b, "key.right", " Tab / Value   ");
        appendKeyHint(b, "key.jump", " Select   ");
        appendKeyHint(b, "key.sneak", " Exit");
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
            restoreMovementSpeeds(player, s);
        }
        despawnSessionEntities(s);

        if (player.isOnline()) {
            plugin.resyncVisibilityFor(player);
            if (Bukkit.getScoreboardManager() != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            if (plugin.sidebarManager() != null) {
                plugin.sidebarManager().clearPlayerCache(player.getUniqueId());
                plugin.sidebarManager().updatePlayerSidebar(player, plugin.gameManager());
            }
            if (plugin.tabListManager() != null) {
                plugin.tabListManager().clearPlayerCache(player.getUniqueId());
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
        s.actorBases.clear();
        s.spinningRings.clear();
        s.orbitingBalls.clear();
        s.showcaseMarquee = null;
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
        s.actorBases.clear();
        s.spinningRings.clear();
        s.orbitingBalls.clear();
        s.showcaseMarquee = null;
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
                subtitle = "<#4ADE80>● Queued " + qm.id().toUpperCase(java.util.Locale.ROOT) + "</#4ADE80>";
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
        s.actorBases.clear();
        s.spinningRings.clear();
        s.orbitingBalls.clear();
        s.showcaseMarquee = null;

        for (int i = 0; i < count; i++) {
            StageActor actor = actors.get(i);
            Location floorBase = poses.get(i).clone();
            Location actorLoc = floorBase.clone().add(0.0, 0.16, 0.0);
            s.actorBases.add(floorBase.clone());

            fr.bosketball.model.PlayerProfile statsProf = plugin.statsManager().getProfile(actor.uuid());
            BallSkin ballSkin = statsProf != null ? statsProf.selectedBallSkin() : BallSkin.CLASSIC;
            boolean showRing = statsProf == null || statsProf.settings().floorAuraRings();
            Material accent = actor.isViewer() || actor.isPartyLeader()
                ? Material.ORANGE_STAINED_GLASS
                : (i % 2 == 1 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.RED_STAINED_GLASS);
            Material accentSolid = actor.isViewer() || actor.isPartyLeader()
                ? Material.ORANGE_CONCRETE
                : (i % 2 == 1 ? Material.LIGHT_BLUE_CONCRETE : Material.RED_CONCRETE);

            spawnShowcasePedestal(viewer, s, floorBase, accentSolid);
            spawnNeonGate(viewer, s, floorBase, accent, count >= 4 ? 0.68 : 0.86);

            Mannequin m = actorLoc.getWorld().spawn(actorLoc, Mannequin.class, man -> {
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
                if (man.getEquipment() != null) {
                    man.getEquipment().setItem(EquipmentSlot.HAND, ballSkin.createMannequinHeldBallItem());
                }
            });
            hideFromOthers(viewer, m);
            s.actorProps.add(m);

            if (showRing) {
                String ringKey = actor.isPartyLeader() ? "ring_gold" : (i % 2 == 1 ? "ring_frost" : "ring_ignis");
                spawnSpinningRing(viewer, s,
                    floorBase.clone().add(0.0, 0.185, 0.0), ringKey, 1.30f, 0.95, i * 0.85);
                spawnSpinningRing(viewer, s,
                    floorBase.clone().add(0.0, 0.175, 0.0), ringKey, 1.74f, -0.55, i * 1.31);
                if (actor.isViewer() || actor.isPartyLeader()) {
                    spawnSpinningRing(viewer, s,
                        floorBase.clone().add(0.0, 2.36, 0.0), "ring_gold", 0.72f, 1.45, 0.35);
                }
            }

            if (actor.isViewer()) {

                for (int orbit = 0; orbit < 3; orbit++) {
                    spawnOrbitingBall(viewer, s, floorBase, ballSkin,
                        1.02,
                        1.02 + orbit * 0.42,
                        orbit % 2 == 0 ? 0.85 : -0.70,
                        orbit * (Math.PI * 2.0 / 3.0));
                }
            }
        }

        spawnShowcaseMarquee(viewer, s);
    }

    private void spawnShowcasePedestal(Player viewer, StageSession s, Location base, Material accentSolid) {
        float yawRad = (float) Math.toRadians(base.getYaw());
        spawnBlockDisplay(viewer, s,
            base.clone().add(0.0, 0.035, 0.0),
            Material.GOLD_BLOCK, new Vector3f(1.52f, 0.07f, 1.52f), yawRad);
        spawnBlockDisplay(viewer, s,
            base.clone().add(0.0, 0.115, 0.0),
            Material.POLISHED_BLACKSTONE, new Vector3f(1.30f, 0.09f, 1.30f), yawRad);

        double yaw = Math.toRadians(base.getYaw());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        spawnBlockDisplay(viewer, s,
            base.clone().add(forwardX * 0.68, 0.145, forwardZ * 0.68),
            accentSolid, new Vector3f(1.04f, 0.035f, 0.06f), yawRad);
    }

    private void spawnNeonGate(Player viewer, StageSession s, Location base, Material accent, double halfWidth) {
        float yawRad = (float) Math.toRadians(base.getYaw());
        double yaw = Math.toRadians(base.getYaw());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        for (int side : new int[]{-1, 1}) {
            Location post = base.clone().add(
                rightX * halfWidth * side - forwardX * 0.22,
                1.34,
                rightZ * halfWidth * side - forwardZ * 0.22
            );
            spawnBlockDisplay(viewer, s, post, accent, new Vector3f(0.07f, 2.38f, 0.07f), yawRad);
        }
        Location beam = base.clone().add(-forwardX * 0.22, 2.56, -forwardZ * 0.22);
        spawnBlockDisplay(viewer, s, beam, accent,
            new Vector3f((float) (halfWidth * 2.0 + 0.07), 0.07f, 0.07f), yawRad);
    }

    private void spawnBlockDisplay(
        Player viewer,
        StageSession s,
        Location center,
        Material material,
        Vector3f scale,
        float yawRad
    ) {
        if (center == null || center.getWorld() == null || material == null || !material.isBlock()) {
            return;
        }
        Quaternionf rotation = new Quaternionf(new AxisAngle4f(yawRad, 0f, 1f, 0f));
        Vector3f localOffset = new Vector3f(-scale.x * 0.5f, -scale.y * 0.5f, -scale.z * 0.5f);
        rotation.transform(localOffset);

        Location spawnLoc = center.clone();
        spawnLoc.setYaw(0.0f);
        spawnLoc.setPitch(0.0f);
        BlockDisplay display = spawnLoc.getWorld().spawn(spawnLoc, BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            d.setBlock(material.createBlockData());
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(1.6f);
            d.setTransformation(new Transformation(localOffset, rotation, scale, new Quaternionf()));
        });
        hideFromOthers(viewer, display);
        s.actorProps.add(display);
    }

    private void spawnSpinningRing(
        Player viewer,
        StageSession s,
        Location center,
        String ringKey,
        float scale,
        double speed,
        double phase
    ) {
        ItemStack ringStack = new ItemStack(Material.PAPER);
        ItemMeta rm = ringStack.getItemMeta();
        if (rm != null) {
            rm.setItemModel(new NamespacedKey("bosketball", ringKey));
            ringStack.setItemMeta(rm);
        }
        ItemDisplay ring = center.getWorld().spawn(center, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(1.6f);
            d.setInterpolationDuration(2);
            d.setInterpolationDelay(0);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setItemStack(ringStack);
            d.setTransformation(flatRingTransformation(scale, phase));
        });
        hideFromOthers(viewer, ring);
        s.actorProps.add(ring);
        s.spinningRings.add(new SpinningRing(ring, scale, speed, phase));
    }

    private static Transformation flatRingTransformation(float scale, double angle) {
        return new Transformation(
            new Vector3f(0f, 0f, 0f),
            new Quaternionf(new AxisAngle4f((float) Math.toRadians(-90.0), 1f, 0f, 0f)),
            new Vector3f(scale, scale, scale),
            new Quaternionf(new AxisAngle4f((float) angle, 0f, 0f, 1f))
        );
    }

    private void spawnOrbitingBall(
        Player viewer,
        StageSession s,
        Location base,
        BallSkin ballSkin,
        double radius,
        double height,
        double speed,
        double phase
    ) {
        Location start = base.clone().add(Math.cos(phase) * radius, height, Math.sin(phase) * radius);
        ItemDisplay display = start.getWorld().spawn(start, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(1.6f);
            d.setTeleportDuration(2);
            d.setInterpolationDuration(2);
            d.setInterpolationDelay(0);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setItemStack(ballSkin.createBallItem(true));
            d.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(new AxisAngle4f((float) phase, 0f, 1f, 0f)),
                new Vector3f(0.46f, 0.46f, 0.46f),
                new Quaternionf()
            ));
        });
        hideFromOthers(viewer, display);
        s.actorProps.add(display);
        s.orbitingBalls.add(new OrbitingBall(display, base.clone(), radius, height, speed, phase));
    }

    private void spawnShowcaseMarquee(Player viewer, StageSession s) {
        if (s.actorBases.isEmpty() || s.cameraEyeLocation == null || s.cameraEyeLocation.getWorld() == null) {
            return;
        }
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (Location base : s.actorBases) {
            x += base.getX();
            y += base.getY();
            z += base.getZ();
        }
        int n = s.actorBases.size();
        Location center = new Location(s.cameraEyeLocation.getWorld(), x / n, y / n, z / n);
        double awayX = center.getX() - s.cameraEyeLocation.getX();
        double awayZ = center.getZ() - s.cameraEyeLocation.getZ();
        double len = Math.hypot(awayX, awayZ);
        if (len < 0.001) {
            double yaw = Math.toRadians(s.cameraEyeLocation.getYaw());
            awayX = -Math.sin(yaw);
            awayZ = Math.cos(yaw);
            len = 1.0;
        }
        Location marqueeLoc = center.clone().add((awayX / len) * 0.78, 3.28, (awayZ / len) * 0.78);
        TextDisplay marquee = marqueeLoc.getWorld().spawn(marqueeLoc, TextDisplay.class, td -> {
            td.setPersistent(false);
            td.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            td.setBillboard(Display.Billboard.VERTICAL);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setBackgroundColor(Color.fromARGB(190, 8, 12, 22));
            td.setShadowed(true);
            td.setLineWidth(420);
            td.setBrightness(new Display.Brightness(15, 15));
            td.setViewRange(1.6f);
            td.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(1.55f, 1.55f, 1.55f),
                new Quaternionf()
            ));
            td.text(Msg.parse("<gradient:#FF8C1A:#FFD43B><bold>✦ BOSKETBALL SHOWCASE ✦</bold></gradient>"));
        });
        hideFromOthers(viewer, marquee);
        s.actorProps.add(marquee);
        s.showcaseMarquee = marquee;
    }

    private void updateShowcaseMarquee(StageSession s, HudEntry active) {
        if (s == null || s.showcaseMarquee == null || !s.showcaseMarquee.isValid() || active == null) {
            return;
        }
        String subtitle = currentTab(s).label + " • " + active.label();
        s.showcaseMarquee.text(Msg.parse(
            "<gradient:#FF8C1A:#FFD43B><bold>✦ BOSKETBALL SHOWCASE ✦</bold></gradient>\n"
                + "<#B8C7DC>" + subtitle + "</#B8C7DC>"
        ));
    }

    private void animateShowcase(Player viewer, StageSession s) {
        double time = tickCounter / 20.0;
        if (tickCounter % 2 == 0) {
            for (SpinningRing ring : s.spinningRings) {
                if (ring.display() != null && ring.display().isValid()) {
                    ring.display().setTransformation(
                        flatRingTransformation(ring.scale(), ring.phase() + time * ring.speed()));
                }
            }
            for (OrbitingBall orbit : s.orbitingBalls) {
                ItemDisplay display = orbit.display();
                if (display == null || !display.isValid()) {
                    continue;
                }
                double angle = orbit.phase() + time * orbit.speed();
                Location next = orbit.center().clone().add(
                    Math.cos(angle) * orbit.radius(),
                    orbit.height() + Math.sin(time * 2.1 + orbit.phase()) * 0.055,
                    Math.sin(angle) * orbit.radius()
                );
                next.setYaw((float) -Math.toDegrees(angle));
                display.teleport(next);
                display.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(new AxisAngle4f((float) angle, 0f, 1f, 0f)),
                    new Vector3f(0.46f, 0.46f, 0.46f),
                    new Quaternionf()
                ));
            }
        }

        if (tickCounter % 4 != 0) {
            return;
        }
        for (int i = 0; i < s.actorBases.size(); i++) {
            Location base = s.actorBases.get(i);
            Color dustColor = i == 0 ? Color.fromRGB(255, 158, 44) : Color.fromRGB(96, 199, 255);
            for (int k = 0; k < 2; k++) {
                double angle = time * 1.85 + i * 1.37 + k * Math.PI;
                Location spark = base.clone().add(Math.cos(angle) * 0.82, 0.24, Math.sin(angle) * 0.82);
                viewer.spawnParticle(Particle.DUST, spark, 1, 0.0, 0.0, 0.0, 0.0,
                    new Particle.DustOptions(dustColor, 0.78f));
            }
            if ((tickCounter + i * 5) % 24 == 0) {
                viewer.spawnParticle(Particle.END_ROD,
                    base.clone().add(0.0, 2.48, 0.0), 2, 0.20, 0.05, 0.20, 0.012);
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
        org.bukkit.entity.Player onlineOwner = org.bukkit.Bukkit.getPlayer(uuid);
        boolean ownerIsStaff = onlineOwner != null
            && fr.bosketball.util.Perms.has(onlineOwner, fr.bosketball.util.Perms.STAFF);
        boolean tagOk = tag != null && tag.canBeUsedBy(prof, name, false, ownerIsStaff);
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

                Location anchor = s.cameraAnchor.getLocation();
                if (p.getLocation().getWorld() != null
                    && anchor.getWorld() != null
                    && p.getLocation().getWorld().equals(anchor.getWorld())
                    && p.getLocation().distanceSquared(anchor) > 0.25) {
                    p.teleport(anchor);
                    p.setSpectatorTarget(s.cameraAnchor);
                }
                p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                if (tickCounter % 20 == 0
                    && (Math.abs(p.getWalkSpeed()) > 0.0001f || Math.abs(p.getFlySpeed()) > 0.0001f)) {
                    applyZeroMovementLock(p);
                }
            }
            if (!s.viewingSubMenu) {
                animateShowcase(p, s);
            }

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
        if (livePaperProfile != null) {
            try {
                PlayerProfile copy = livePaperProfile.clone();
                if (StatsManager.texturesProperty(copy) == null) {
                    try {
                        copy.completeFromCache();
                    } catch (Throwable ignored) {

                    }
                }
                if (StatsManager.texturesProperty(copy) != null) {
                    return ResolvableProfile.resolvableProfile(copy);
                }
            } catch (Throwable ignored) {

            }
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
