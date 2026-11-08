package fr.bosketball.ui;

import fr.bosketball.util.Palette;
import com.destroystokyo.paper.SkinParts;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.config.AtomicYamlWriter;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Color;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class LobbyHologramManager {

    public static final String HOLO_ENTITY_TAG = "bosketball_lobby_holo";
    public static final String NPC_PLAY_PROFILE_NAME = "BB_PlayNPC";
    public static final String NPC_SHOP_PROFILE_NAME = "BB_ShopNPC";
    public static final String NPC_PLAY_SKIN_ID = "npc_play_superstar";
    public static final String NPC_SHOP_SKIN_ID = "npc_shop_merchant";

    public static final double LEADERBOARD_ARROW_SIDE_OFFSET = 3.12;

    private static final int BOARD_ROWS = 7;

    /**
     * Leaderboard rows are a fixed pixel grid: every row is exactly
     * 18 + 62 + 90 + 28 + 10 = 208 px wide, so all the plates are the same size and the columns
     * line up. The previous version padded rows with negative-space glyphs and relied on
     * the measured width of small-capped names, which nobody can measure exactly: the
     * plates ended up different sizes and each row drifted sideways (see the "ROOKIE"
     * screenshot with 7 rows at 5 different offsets).
     */
    private static final int CELL_PLACE_PX = 18;
    /** Widest rank artwork is SUPERSTAR (58 px), so the pill column is 62 px. */
    private static final int CELL_PILL_PX = 62;
    private static final int CELL_NAME_PX = 96;
    private static final int CELL_VALUE_PX = 40;
    /** Kept for {@link #statCell(char)}; a row no longer repeats the category icon. */
    private static final int CELL_STAT_PX = 10;
    private static final int ROW_GAP_PX = 3;
    /** Every plate is exactly this wide, empty rows included: the plates are the board's frame. */
    private static final int BOARD_ROW_PX = CELL_PLACE_PX + CELL_PILL_PX + CELL_NAME_PX + CELL_VALUE_PX;
    /** The plate behind one row, and the header / footer panels. Neutral, one family. */
    private static final Color PLATE_BG = Color.fromARGB(196, 11, 15, 24);
    private static final Color PANEL_BG = Color.fromARGB(216, 8, 11, 18);
    private static final Set<String> ACTIVE_NPC_SCOREBOARD_ENTRIES = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final StatsManager.LeaderboardCategory[] BOARD_CYCLE = {
        StatsManager.LeaderboardCategory.ELO,
        StatsManager.LeaderboardCategory.POINTS,
        StatsManager.LeaderboardCategory.COINS,
        StatsManager.LeaderboardCategory.WINS,
        StatsManager.LeaderboardCategory.DUNKS
    };

    public enum StationKind {
        ELO("elo", "trophy_mvp", " ᴛᴏᴘ ᴇʟᴏ ʀᴀɴᴋɪɴɢꜱ", true),
        POINTS("points", "basketball_gold", " ᴛᴏᴘ ᴄᴀʀᴇᴇʀ ꜱᴄᴏʀᴇʀꜱ", true),
        COINS("coins", "basketball_money", " ʀɪᴄʜᴇꜱᴛ ʙᴀʟʟᴇʀꜱ", true),
        PLAY("play", null, " ᴘʟᴀʏ ʙᴏꜱᴋᴇᴛʙᴀʟʟ", false),
        SHOP("shop", null, " ꜱʜᴏᴘ", false);

        private final String id;
        private final String propModelKey;
        private final String titleSmallCaps;
        private final boolean leaderboard;

        StationKind(String id, String propModelKey, String titleSmallCaps, boolean leaderboard) {
            this.id = id;
            this.propModelKey = propModelKey;
            this.titleSmallCaps = titleSmallCaps;
            this.leaderboard = leaderboard;
        }

        public String id() {
            return id;
        }

        public String propModelKey() {
            return propModelKey;
        }

        public boolean has3dProp() {
            return propModelKey != null;
        }

        public boolean isLeaderboard() {
            return leaderboard;
        }

        public boolean hasFloorNpc() {
            return !leaderboard;
        }

        public String titleSmallCaps() {
            return titleSmallCaps;
        }

        public StatsManager.LeaderboardCategory defaultCategory() {
            return switch (this) {
                case POINTS -> StatsManager.LeaderboardCategory.POINTS;
                case COINS -> StatsManager.LeaderboardCategory.COINS;
                default -> StatsManager.LeaderboardCategory.ELO;
            };
        }

        public static StationKind fromString(String raw) {
            if (raw == null) {
                return null;
            }
            String low = raw.trim().toLowerCase(Locale.ROOT);
            for (StationKind k : values()) {
                if (k.id.equals(low) || k.name().toLowerCase(Locale.ROOT).equals(low)) {
                    return k;
                }
            }
            return null;
        }
    }

    private enum ClickAction {
        MAIN_OPEN,
        PREV_CATEGORY,
        NEXT_CATEGORY
    }

    private record HoloClickBinding(String holoId, ClickAction action) {
    }

    public static final class HologramInstance {
        private final String id;
        private final StationKind kind;
        private final String worldName;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;

        private final boolean poseLocked;

        private StatsManager.LeaderboardCategory activeCategory;
        private ItemDisplay propDisplay;
        private Mannequin npcMannequin;
        private ItemDisplay npcFloorRing;
        private TextDisplay headerDisplay;
        private final List<TextDisplay> rowDisplays = new ArrayList<>();
        private TextDisplay footerDisplay;
        private TextDisplay leftArrowDisplay;
        private TextDisplay rightArrowDisplay;
        private Interaction mainClickBox;
        private Interaction leftClickBox;
        private Interaction rightClickBox;
        private String lastRenderedSignature = "";

        public HologramInstance(String id, StationKind kind, String worldName, double x, double y, double z, float yaw) {
            this(id, kind, worldName, x, y, z, yaw, 0.0f, false);
        }

        public HologramInstance(String id, StationKind kind, String worldName, double x, double y, double z,
                                 float yaw, float pitch, boolean poseLocked) {
            this.id = id;
            this.kind = kind;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.poseLocked = poseLocked;
            this.activeCategory = kind.defaultCategory();
        }

        public String id() {
            return id;
        }

        public StationKind kind() {
            return kind;
        }

        public float yaw() {
            return yaw;
        }

        public float pitch() {
            return pitch;
        }

        public boolean poseLocked() {
            return poseLocked;
        }

        public StatsManager.LeaderboardCategory activeCategory() {
            return activeCategory;
        }

        public Location baseLocation() {
            World w = Bukkit.getServer() != null ? Bukkit.getWorld(worldName) : null;
            return w != null ? new Location(w, x, y, z, yaw, pitch) : null;
        }
    }

    private final BosketBallPlugin plugin;
    private final File storageFile;
    private final Map<String, HologramInstance> holograms = new ConcurrentHashMap<>();
    private final Map<UUID, HoloClickBinding> interactionBindings = new ConcurrentHashMap<>();
    private int animTick = 0;

    static {
        ACTIVE_NPC_SCOREBOARD_ENTRIES.add(NPC_PLAY_PROFILE_NAME);
        ACTIVE_NPC_SCOREBOARD_ENTRIES.add(NPC_SHOP_PROFILE_NAME);
    }

    public LobbyHologramManager(BosketBallPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = plugin != null ? new File(plugin.getDataFolder(), "holograms.yml") : null;
    }

    public static Set<String> activeNpcScoreboardEntries() {
        return new HashSet<>(ACTIVE_NPC_SCOREBOARD_ENTRIES);
    }

    public Collection<HologramInstance> allHolograms() {
        return holograms.values();
    }

    public int stationCount() {
        return holograms.size();
    }

    public static float snapFacingYaw(float playerLookYaw) {
        float facing = playerLookYaw + 180.0f;
        while (facing <= -180.0f) facing += 360.0f;
        while (facing > 180.0f) facing -= 360.0f;
        float nearest45 = Math.round(facing / 45.0f) * 45.0f;
        if (Math.abs(facing - nearest45) <= 12.0f) {
            facing = nearest45;
        }
        if (facing <= -180.0f) facing += 360.0f;
        return facing;
    }

    public static double resolveNpcFloorY(Location base) {
        if (base == null || base.getWorld() == null) {
            return base != null ? base.getY() : 0.0;
        }
        World w = base.getWorld();
        int bx = Location.locToBlock(base.getX());
        int bz = Location.locToBlock(base.getZ());
        int startBy = Location.locToBlock(base.getY() + 0.30);
        for (int dy = 0; dy <= 6; dy++) {
            int checkY = startBy - dy;
            if (checkY < w.getMinHeight()) {
                break;
            }
            Block block = w.getBlockAt(bx, checkY, bz);
            if (block.getType().isSolid() && !block.isPassable()) {
                return checkY + 1.0;
            }
        }
        return base.getY();
    }

    public static final String PINNED_PLAY_ID = "lobby_play";

    public static final String PINNED_SHOP_ID = "lobby_shop";

    public void loadAndSpawnAll() {
        despawnAllEntities();
        sweepOrphanHoloEntities();
        holograms.clear();
        if (storageFile != null && storageFile.exists()) {
            YamlConfiguration yml = AtomicYamlWriter.loadSafe(storageFile, plugin.getLogger());
            ConfigurationSection root = yml.getConfigurationSection("holograms");
            if (root != null) {
                for (String id : root.getKeys(false)) {
                    ConfigurationSection sec = root.getConfigurationSection(id);
                    if (sec == null) {
                        continue;
                    }
                    StationKind kind = StationKind.fromString(sec.getString("kind", "elo"));
                    String worldName = sec.getString("world");
                    if (kind == null || worldName == null) {
                        continue;
                    }
                    HologramInstance inst = new HologramInstance(
                        id,
                        kind,
                        worldName,
                        sec.getDouble("x"),
                        sec.getDouble("y"),
                        sec.getDouble("z"),
                        (float) sec.getDouble("yaw", 0.0),
                        (float) sec.getDouble("pitch", 0.0),
                        sec.getBoolean("pose-locked", false)
                    );
                    holograms.put(id, inst);
                    spawnEntitiesFor(inst);
                }
            }
        }

        applyConfiguredStations();
    }

    public void save() {
        if (storageFile == null) {
            return;
        }
        YamlConfiguration yml = new YamlConfiguration();
        for (HologramInstance inst : holograms.values()) {
            String p = "holograms." + inst.id + ".";
            yml.set(p + "kind", inst.kind.id());
            yml.set(p + "world", inst.worldName);
            yml.set(p + "x", Math.round(inst.x * 100.0) / 100.0);
            yml.set(p + "y", Math.round(inst.y * 100.0) / 100.0);
            yml.set(p + "z", Math.round(inst.z * 100.0) / 100.0);
            yml.set(p + "yaw", Math.round(inst.yaw * 10.0) / 10.0);
            yml.set(p + "pitch", Math.round(inst.pitch * 10.0) / 10.0);
            yml.set(p + "pose-locked", inst.poseLocked);
        }
        String hdr = "# BosketBall — Fixed Wall Leaderboards & 3D NPC Lobby Stations\n"
            + "# NOTE: while lobby.stations.enforce is true in config.yml, the pinned lobby_play /\n"
            + "# lobby_shop NPC poses below are re-applied from config.yml on every start & /bb reload.\n";
        AtomicYamlWriter.writeYamlAtomically(storageFile, hdr, yml, plugin.getLogger());
    }

    public void applyConfiguredStations() {
        if (plugin == null || plugin.settings() == null) {
            return;
        }
        fr.bosketball.config.BosketConfig.Lobby lobby = plugin.settings().lobby();
        if (lobby == null || !lobby.enforceStations()) {
            return;
        }
        boolean changed = false;
        for (HologramInstance inst : new ArrayList<>(holograms.values())) {
            boolean npcKind = inst.kind == StationKind.PLAY || inst.kind == StationKind.SHOP;
            if (npcKind && !inst.id.equals(PINNED_PLAY_ID) && !inst.id.equals(PINNED_SHOP_ID)) {
                despawnInstance(inst);
                holograms.remove(inst.id);
                changed = true;
            }
        }
        changed |= upsertPinnedStation(PINNED_PLAY_ID, StationKind.PLAY, lobby.playStation());
        changed |= upsertPinnedStation(PINNED_SHOP_ID, StationKind.SHOP, lobby.shopStation());
        if (changed) {
            save();
        }
    }

    private boolean upsertPinnedStation(String id, StationKind kind, fr.bosketball.config.BosketConfig.LobbyPoint point) {
        if (point == null) {
            return false;
        }
        HologramInstance existing = holograms.get(id);
        if (existing != null
            && existing.kind == kind
            && existing.poseLocked
            && existing.worldName.equalsIgnoreCase(point.world())
            && Math.abs(existing.x - point.x()) < 0.001
            && Math.abs(existing.y - point.y()) < 0.001
            && Math.abs(existing.z - point.z()) < 0.001
            && Math.abs(existing.yaw - point.yaw()) < 0.01f
            && Math.abs(existing.pitch - point.pitch()) < 0.01f) {

            if (existing.npcMannequin == null || !existing.npcMannequin.isValid()) {
                spawnEntitiesFor(existing);
            }
            return false;
        }
        if (existing != null) {
            despawnInstance(existing);
        }
        HologramInstance inst = new HologramInstance(
            id, kind, point.world(), point.x(), point.y(), point.z(), point.yaw(), point.pitch(), true
        );
        holograms.put(id, inst);
        spawnEntitiesFor(inst);
        return true;
    }

    public HologramInstance createStationAt(Location loc, StationKind kind) {
        if (loc == null || loc.getWorld() == null || kind == null) {
            return null;
        }
        int n = 1;
        while (holograms.containsKey(kind.id() + "_" + n)) {
            n++;
        }
        String id = kind.id() + "_" + n;
        float facingYaw = snapFacingYaw(loc.getYaw());
        HologramInstance inst = new HologramInstance(
            id,
            kind,
            loc.getWorld().getName(),
            loc.getX(),
            loc.getY(),
            loc.getZ(),
            facingYaw
        );
        holograms.put(id, inst);
        spawnEntitiesFor(inst);
        save();
        return inst;
    }

    public boolean removeNearest(Location near, double maxRadius) {
        if (near == null || near.getWorld() == null) {
            return false;
        }
        HologramInstance best = null;
        double bestDistSq = maxRadius * maxRadius;
        for (HologramInstance inst : holograms.values()) {
            Location loc = inst.baseLocation();
            if (loc != null && near.getWorld().equals(loc.getWorld())) {
                double d2 = loc.distanceSquared(near);
                if (d2 <= bestDistSq) {
                    bestDistSq = d2;
                    best = inst;
                }
            }
        }
        if (best == null) {
            return false;
        }
        despawnInstance(best);
        holograms.remove(best.id);
        save();
        return true;
    }

    public int clearAll() {
        int count = holograms.size();
        despawnAllEntities();
        holograms.clear();
        save();
        return count;
    }

    private static final AxisAngle4f IDENTITY_AXIS = new AxisAngle4f(0f, 0f, 1f, 0f);
    private static final Vector3f SCALE_LEADERBOARD = new Vector3f(0.95f, 0.95f, 0.95f);
    private static final Vector3f SCALE_ARROW_PILL = new Vector3f(1.22f, 1.22f, 1.22f);
    private static final Vector3f SCALE_NPC_RING = new Vector3f(0.85f, 0.85f, 0.85f);

    /**
     * True when one of the entities of this hologram is gone (unloaded chunk, non-persistent
     * display wiped by a restart, {@code /kill @e}, another plugin's cleanup...).
     * Lobby leaderboards and NPCs are spawned with {@code setPersistent(false)}, so they must be
     * re-created on every boot and after any chunk unload.
     */
    public boolean needsRespawn(HologramInstance inst) {
        if (inst == null) {
            return false;
        }
        return inst.headerDisplay == null || !inst.headerDisplay.isValid()
            || inst.mainClickBox == null || !inst.mainClickBox.isValid()
            || (inst.kind.has3dProp() && (inst.propDisplay == null || !inst.propDisplay.isValid()))
            || (inst.kind.hasFloorNpc() && (inst.npcMannequin == null || !inst.npcMannequin.isValid()));
    }

    /** Number of configured holograms whose entities are currently missing. */
    public int countNeedingRespawn() {
        int missing = 0;
        for (HologramInstance inst : holograms.values()) {
            if (needsRespawn(inst)) {
                missing++;
            }
        }
        return missing;
    }

    /**
     * Re-spawns every hologram whose entities vanished, loading its chunk first so a station that
     * sits outside the view distance is rebuilt too. Returns how many were repaired.
     */
    public int ensureAllSpawned() {
        if (Bukkit.getServer() == null) {
            return 0;
        }
        int repaired = 0;
        for (HologramInstance inst : holograms.values()) {
            Location base = inst.baseLocation();
            if (base == null || base.getWorld() == null) {
                continue;
            }
            if (!needsRespawn(inst)) {
                continue;
            }
            if (!base.isChunkLoaded()) {
                base.getWorld().getChunkAt(base).load();
            }
            spawnEntitiesFor(inst);
            repaired++;
        }
        return repaired;
    }

    public boolean handlePossibleStationClick(Player player, Entity entity) {
        if (player == null || entity == null) {
            return false;
        }
        if (plugin.gameManager().isInMatch(player.getUniqueId())
            || (plugin.cinematicCamera() != null && plugin.cinematicCamera().isWatching(player.getUniqueId()))) {
            return false;
        }
        HoloClickBinding binding = interactionBindings.get(entity.getUniqueId());
        if (binding == null) {
            return false;
        }
        HologramInstance inst = holograms.get(binding.holoId());
        if (inst == null) {
            return false;
        }

        if (inst.kind.isLeaderboard() && (binding.action() == ClickAction.PREV_CATEGORY || binding.action() == ClickAction.NEXT_CATEGORY)) {
            int curIdx = 0;
            for (int i = 0; i < BOARD_CYCLE.length; i++) {
                if (BOARD_CYCLE[i] == inst.activeCategory) {
                    curIdx = i;
                    break;
                }
            }
            int step = binding.action() == ClickAction.NEXT_CATEGORY ? 1 : -1;
            inst.activeCategory = BOARD_CYCLE[Math.floorMod(curIdx + step, BOARD_CYCLE.length)];
            inst.lastRenderedSignature = "";
            refreshBoardTexts(inst);
            Msg.playPersonalPackSound(player, "ui.click", 0.85f, binding.action() == ClickAction.NEXT_CATEGORY ? 1.32f : 1.12f);
            return true;
        }

        Msg.playPersonalPackSound(player, "ui.click", 0.8f, 1.25f);
        switch (inst.kind) {
            case ELO, POINTS, COINS -> plugin.guiMenu().openLeaderboards(player, inst.activeCategory);
            case PLAY -> plugin.guiMenu().openPlayModes(player);
            case SHOP -> plugin.guiMenu().openShop(player);
        }
        return true;
    }

    /** True when a player stands close enough to at least one hologram to see it. */
    private boolean anyHologramWatched() {
        for (HologramInstance inst : holograms.values()) {
            Location base = inst.baseLocation();
            if (base != null && fr.bosketball.util.Viewers.anyWithin(base, 96.0)) {
                return true;
            }
        }
        return false;
    }

    public void tickAnimation() {
        if (holograms.isEmpty() || Bukkit.getServer() == null) {
            return;
        }
        // Transformations, leaderboard text and packets are only worth building for a real viewer.
        if (!anyHologramWatched()) {
            animTick++;
            return;
        }
        animTick++;
        float angleRad = (animTick * 0.09f) % ((float) (Math.PI * 2.0));
        float ringAngleRad = (animTick * 0.045f) % ((float) (Math.PI * 2.0));
        float bobY = (float) (Math.sin(animTick * 0.10) * 0.08);

        int refreshSeconds = Math.max(1, plugin.settings().leaderboards().holoRefreshSeconds());
        boolean refreshText = (animTick % (refreshSeconds * 10) == 0);
        Vector3f bobOffset = new Vector3f(0f, bobY, 0f);
        AxisAngle4f spinAxis = new AxisAngle4f(angleRad, 0f, 1f, 0f);
        AxisAngle4f ringSpinAxis = new AxisAngle4f(ringAngleRad, 0f, 1f, 0f);

        for (HologramInstance inst : holograms.values()) {
            Location base = inst.baseLocation();
            if (base == null || !base.isChunkLoaded()) {
                continue;
            }
            if (needsRespawn(inst)) {
                spawnEntitiesFor(inst);
                continue;
            }
            if (inst.propDisplay != null) {
                inst.propDisplay.setInterpolationDelay(0);
                inst.propDisplay.setInterpolationDuration(3);
                inst.propDisplay.setTransformation(new Transformation(
                    bobOffset,
                    spinAxis,
                    SCALE_LEADERBOARD,
                    IDENTITY_AXIS
                ));
            }
            if (inst.npcFloorRing != null && inst.npcFloorRing.isValid()) {
                inst.npcFloorRing.setInterpolationDelay(0);
                inst.npcFloorRing.setInterpolationDuration(3);
                inst.npcFloorRing.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    ringSpinAxis,
                    SCALE_NPC_RING,
                    IDENTITY_AXIS
                ));
            }
            if (inst.npcMannequin != null && inst.npcMannequin.isValid()) {
                updateNpcGazeTracking(inst);
            }
            if (refreshText) {
                refreshBoardTexts(inst);
            }
        }
    }

    private void updateNpcGazeTracking(HologramInstance inst) {
        Mannequin npc = inst.npcMannequin;
        if (npc == null) {
            return;
        }
        Location npcLoc = npc.getLocation();
        if (inst.poseLocked) {

            if (Math.abs(npcLoc.getYaw() - inst.yaw) > 0.01f || Math.abs(npcLoc.getPitch() - inst.pitch) > 0.01f) {
                npc.setRotation(inst.yaw, inst.pitch);
            }
            return;
        }
        World w = npcLoc.getWorld();
        if (w == null) {
            return;
        }
        Player closest = null;
        double bestDistSq = 7.5 * 7.5;
        for (Player p : w.getPlayers()) {
            if (plugin.gameManager().isInMatch(p.getUniqueId())) {
                continue;
            }
            double d2 = p.getLocation().distanceSquared(npcLoc);
            if (d2 <= bestDistSq) {
                bestDistSq = d2;
                closest = p;
            }
        }

        float desiredYaw = inst.yaw;
        float desiredPitch = inst.pitch;
        if (closest != null && bestDistSq > 0.04) {
            Location pEye = closest.getEyeLocation();
            double dx = pEye.getX() - npcLoc.getX();
            double dy = pEye.getY() - (npcLoc.getY() + 1.62);
            double dz = pEye.getZ() - npcLoc.getZ();
            double horiz = Math.sqrt(dx * dx + dz * dz);
            desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            desiredPitch = (float) Math.clamp(-Math.toDegrees(Math.atan2(dy, Math.max(0.25, horiz))), -26.0, 26.0);
        }

        float curYaw = npcLoc.getYaw();
        float curPitch = npcLoc.getPitch();
        float diffYaw = desiredYaw - curYaw;
        while (diffYaw <= -180.0f) diffYaw += 360.0f;
        while (diffYaw > 180.0f) diffYaw -= 360.0f;
        float nextYaw = curYaw + diffYaw * 0.28f;
        float nextPitch = curPitch + (desiredPitch - curPitch) * 0.28f;
        if (Math.abs(diffYaw) > 0.35f || Math.abs(desiredPitch - curPitch) > 0.35f) {
            npc.setRotation(nextYaw, nextPitch);
        }
    }

    private void spawnEntitiesFor(HologramInstance inst) {
        despawnInstance(inst);
        Location base = inst.baseLocation();
        if (base == null || base.getWorld() == null) {
            return;
        }
        World w = base.getWorld();

        if (inst.kind.has3dProp()) {
            double propY = base.getY() + 3.95;
            ItemStack propStack = new ItemStack(Material.AMETHYST_SHARD);
            ItemMeta meta = propStack.getItemMeta();
            if (meta != null) {
                meta.setItemModel(new NamespacedKey("bosketball", inst.kind.propModelKey()));
                propStack.setItemMeta(meta);
            }
            inst.propDisplay = w.spawn(new Location(w, base.getX(), propY, base.getZ(), inst.yaw, 0f), ItemDisplay.class, id -> {
                id.addScoreboardTag(HOLO_ENTITY_TAG);
                id.setPersistent(false);
                id.setItemStack(propStack);
                id.setBillboard(Display.Billboard.FIXED);
                id.setBrightness(new Display.Brightness(15, 15));
                id.setViewRange(0.85f);
            });
        }

        if (!inst.kind.isLeaderboard()) {
            double floorY = resolveNpcFloorY(base);
            double textY = Math.max(base.getY() + 1.20, floorY + 2.15);

            String profileName = inst.kind == StationKind.PLAY ? NPC_PLAY_PROFILE_NAME : NPC_SHOP_PROFILE_NAME;
            String skinId = inst.kind == StationKind.PLAY ? NPC_PLAY_SKIN_ID : NPC_SHOP_SKIN_ID;
            BallSkin heldBall = inst.kind == StationKind.PLAY ? BallSkin.CLASSIC : BallSkin.GOLD;
            String ringModel = inst.kind == StationKind.PLAY ? "ring_ignis" : "ring_gold";

            Location npcLoc = new Location(w, base.getX(), floorY, base.getZ(), inst.yaw, 0f);
            inst.npcMannequin = w.spawn(npcLoc, Mannequin.class, man -> {
                man.addScoreboardTag(HOLO_ENTITY_TAG);
                man.setPersistent(false);
                man.setImmovable(true);
                man.setGravity(false);
                man.setInvulnerable(true);
                man.setSilent(true);
                man.setCollidable(false);
                man.setCustomNameVisible(false);
                man.setDescription(null);
                try {
                    man.setSkinParts(SkinParts.allParts());
                } catch (Throwable ignored) {
                }
                man.setProfile(fr.bosketball.util.Skins.buildCustomPatchedSkin(profileName, skinId));
                if (man.getEquipment() != null) {
                    man.getEquipment().setItem(EquipmentSlot.HAND, heldBall.createMannequinHeldBallItem());
                }
            });
            ACTIVE_NPC_SCOREBOARD_ENTRIES.add(inst.npcMannequin.getUniqueId().toString());
            interactionBindings.put(inst.npcMannequin.getUniqueId(), new HoloClickBinding(inst.id, ClickAction.MAIN_OPEN));

            ItemStack ringStack = new ItemStack(Material.PAPER);
            ItemMeta ringMeta = ringStack.getItemMeta();
            if (ringMeta != null) {
                ringMeta.setItemModel(new NamespacedKey("bosketball", ringModel));
                ringStack.setItemMeta(ringMeta);
            }
            inst.npcFloorRing = w.spawn(new Location(w, base.getX(), floorY + 0.02, base.getZ(), inst.yaw, 0f), ItemDisplay.class, d -> {
                d.addScoreboardTag(HOLO_ENTITY_TAG);
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.FIXED);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setItemStack(ringStack);
                d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    IDENTITY_AXIS,
                    SCALE_NPC_RING,
                    IDENTITY_AXIS
                ));
            });

            String stationMarkup = buildStationMarkup(inst.kind);
            inst.lastRenderedSignature = stationMarkup;
            inst.headerDisplay = w.spawn(new Location(w, base.getX(), textY, base.getZ(), inst.yaw, 0f), TextDisplay.class, td -> {
                td.addScoreboardTag(HOLO_ENTITY_TAG);
                td.setPersistent(false);
                td.setBillboard(Display.Billboard.VERTICAL);
                td.setAlignment(TextDisplay.TextAlignment.CENTER);
                td.setBackgroundColor(Color.fromARGB(212, 12, 16, 28));
                td.setShadowed(true);
                td.setLineWidth(240);
                td.setBrightness(new Display.Brightness(15, 15));
                td.setViewRange(0.85f);
                td.text(Msg.parse(fr.bosketball.util.Glyphs.smallCapsLabels(stationMarkup)));
            });

            float totalClickHeight = (float) Math.max(3.35, (textY - floorY) + 1.40);
            inst.mainClickBox = w.spawn(new Location(w, base.getX(), floorY, base.getZ()), Interaction.class, inter -> {
                inter.addScoreboardTag(HOLO_ENTITY_TAG);
                inter.setPersistent(false);
                inter.setInteractionWidth(1.40f);
                inter.setInteractionHeight(totalClickHeight);
                inter.setResponsive(true);
            });
            interactionBindings.put(inst.mainClickBox.getUniqueId(), new HoloClickBinding(inst.id, ClickAction.MAIN_OPEN));
            return;
        }

        float boardYaw = inst.yaw;
        double yawRad = Math.toRadians(boardYaw);
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        inst.headerDisplay = w.spawn(new Location(w, base.getX(), base.getY() + 3.10, base.getZ(), boardYaw, 0f), TextDisplay.class, td -> {
            td.addScoreboardTag(HOLO_ENTITY_TAG);
            td.setPersistent(false);
            td.setBillboard(Display.Billboard.FIXED);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setBackgroundColor(PANEL_BG);
            td.setShadowed(true);
            td.setLineWidth(300);
            td.setBrightness(new Display.Brightness(15, 15));
            td.setViewRange(0.90f);
        });

        for (int r = 0; r < BOARD_ROWS; r++) {
            final boolean topRow = (r == 0);
            double rowY = base.getY() + 2.62 - (r * 0.31);
            TextDisplay rowTd = w.spawn(new Location(w, base.getX(), rowY, base.getZ(), boardYaw, 0f), TextDisplay.class, td -> {
                td.addScoreboardTag(HOLO_ENTITY_TAG);
                td.setPersistent(false);
                td.setBillboard(Display.Billboard.FIXED);
                td.setAlignment(TextDisplay.TextAlignment.CENTER);
                // One plate colour for the whole board. The leader is told apart by the gold of
                // its text, not by a second background: two backgrounds were two accents.
                td.setBackgroundColor(PLATE_BG);
                td.setShadowed(true);
                td.setLineWidth(340);
                td.setBrightness(new Display.Brightness(15, 15));
                td.setViewRange(0.90f);
            });
            inst.rowDisplays.add(rowTd);
        }

        inst.footerDisplay = w.spawn(new Location(w, base.getX(), base.getY() + 0.46, base.getZ(), boardYaw, 0f), TextDisplay.class, td -> {
            td.addScoreboardTag(HOLO_ENTITY_TAG);
            td.setPersistent(false);
            td.setBillboard(Display.Billboard.FIXED);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setBackgroundColor(PANEL_BG);
            td.setShadowed(true);
            td.setLineWidth(380);
            td.setBrightness(new Display.Brightness(15, 15));
            td.setViewRange(0.90f);
        });

        double arrowOffset = LEADERBOARD_ARROW_SIDE_OFFSET;
        // The arrows used to float between two plates, halfway up the board. They now sit on the
        // footer line, where they read as its previous / next buttons instead of leftovers.
        double arrowY = base.getY() + 0.46;
        Location leftLoc = new Location(w, base.getX() - rightX * arrowOffset, arrowY, base.getZ() - rightZ * arrowOffset, boardYaw, 0f);
        Location rightLoc = new Location(w, base.getX() + rightX * arrowOffset, arrowY, base.getZ() + rightZ * arrowOffset, boardYaw, 0f);

        inst.leftArrowDisplay = w.spawn(leftLoc, TextDisplay.class, td -> {
            td.addScoreboardTag(HOLO_ENTITY_TAG);
            td.setPersistent(false);
            td.setBillboard(Display.Billboard.FIXED);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setBackgroundColor(PLATE_BG);
            td.setShadowed(true);
            td.setBrightness(new Display.Brightness(15, 15));
            td.setViewRange(0.85f);
            td.setTransformation(new Transformation(new Vector3f(0f, 0f, 0f), IDENTITY_AXIS, SCALE_ARROW_PILL, IDENTITY_AXIS));
            td.text(Msg.parse(" <" + Palette.MUTED + ">◀</" + Palette.MUTED + "> "));
        });
        inst.rightArrowDisplay = w.spawn(rightLoc, TextDisplay.class, td -> {
            td.addScoreboardTag(HOLO_ENTITY_TAG);
            td.setPersistent(false);
            td.setBillboard(Display.Billboard.FIXED);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setBackgroundColor(Color.fromARGB(224, 18, 24, 38));
            td.setShadowed(true);
            td.setBrightness(new Display.Brightness(15, 15));
            td.setViewRange(0.85f);
            td.setTransformation(new Transformation(new Vector3f(0f, 0f, 0f), IDENTITY_AXIS, SCALE_ARROW_PILL, IDENTITY_AXIS));
            td.text(Msg.parse(" <" + Palette.MUTED + ">▶</" + Palette.MUTED + "> "));
        });

        inst.mainClickBox = w.spawn(new Location(w, base.getX(), base.getY() + 0.35, base.getZ()), Interaction.class, inter -> {
            inter.addScoreboardTag(HOLO_ENTITY_TAG);
            inter.setPersistent(false);
            inter.setInteractionWidth(2.45f);
            inter.setInteractionHeight(3.05f);
            inter.setResponsive(true);
        });
        interactionBindings.put(inst.mainClickBox.getUniqueId(), new HoloClickBinding(inst.id, ClickAction.MAIN_OPEN));

        inst.leftClickBox = w.spawn(leftLoc.clone().subtract(0, 0.32, 0), Interaction.class, inter -> {
            inter.addScoreboardTag(HOLO_ENTITY_TAG);
            inter.setPersistent(false);
            inter.setInteractionWidth(0.82f);
            inter.setInteractionHeight(0.90f);
            inter.setResponsive(true);
        });
        interactionBindings.put(inst.leftClickBox.getUniqueId(), new HoloClickBinding(inst.id, ClickAction.PREV_CATEGORY));

        inst.rightClickBox = w.spawn(rightLoc.clone().subtract(0, 0.32, 0), Interaction.class, inter -> {
            inter.addScoreboardTag(HOLO_ENTITY_TAG);
            inter.setPersistent(false);
            inter.setInteractionWidth(0.82f);
            inter.setInteractionHeight(0.90f);
            inter.setResponsive(true);
        });
        interactionBindings.put(inst.rightClickBox.getUniqueId(), new HoloClickBinding(inst.id, ClickAction.NEXT_CATEGORY));

        inst.lastRenderedSignature = "";
        refreshBoardTexts(inst);
    }

    // ---------------------------------------------------------------- row layout

    /** Pads a plate to exactly {@code targetPx}, so every plate on the board is the same width. */
    private static String padTo(String markup, int targetPx) {
        int width = 0;
        String plain = fr.bosketball.util.MiniSyntax.stripTags(markup);
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            int shift = Glyphs.shiftAdvancePx(c);
            width += shift != 0 ? shift : Glyphs.textAdvancePx(String.valueOf(c), false);
        }
        return width >= targetPx ? markup : markup + pad(targetPx - width);
    }

    /** Exact pixel padding; positive space glyphs never break the line box. */
    private static String pad(int pixels) {
        return pixels <= 0 ? "" : Glyphs.shift(pixels);
    }

    /** Truncates plain text so it never exceeds {@code maxPx}, appending an ellipsis. */
    public static String fitToPx(String plain, int maxPx) {
        return fitToPx(plain, maxPx, false);
    }

    /** Same, for bold text (bold is one pixel wider per glyph). */
    public static String fitToPx(String plain, int maxPx, boolean bold) {
        if (plain == null || plain.isEmpty() || Glyphs.textAdvancePx(plain, bold) <= maxPx) {
            return plain == null ? "" : plain;
        }
        int budget = Math.max(0, maxPx - Glyphs.textAdvancePx("…", bold));
        StringBuilder out = new StringBuilder(plain.length());
        int used = 0;
        for (int i = 0; i < plain.length(); i++) {
            int advance = Glyphs.textAdvancePx(String.valueOf(plain.charAt(i)), bold);
            if (used + advance > budget) {
                break;
            }
            out.append(plain.charAt(i));
            used += advance;
        }
        return out + "…";
    }

    /** Column 1: the place number, right aligned in a fixed cell. */
    public static String placeCell(int place, String placeHex) {
        String digits = place + ".";
        int width = Glyphs.textAdvancePx(digits, true);
        int lead = Math.max(0, CELL_PLACE_PX - width - ROW_GAP_PX);
        return pad(lead)
            + "<" + placeHex + "><bold>" + digits + "</bold></" + placeHex + ">"
            + pad(Math.max(0, CELL_PLACE_PX - lead - width));
    }

    /** Column 2: the rank artwork pill, padded so every name starts on the same pixel. */
    public static String pillCell(char pill) {
        int width = pill == ' ' ? 0 : Glyphs.textAdvancePx(String.valueOf(pill), false);
        String body = pill == ' ' ? "" : "<!shadow><#F8FAFC>" + pill + "</#F8FAFC></!shadow>";
        return pad(ROW_GAP_PX) + body
            + pad(Math.max(0, CELL_PILL_PX - ROW_GAP_PX - width));
    }

    /** Column 3: the player name (never small-capped, so its width is exact). */
    public static String nameCell(String name, boolean bold) {
        String plain = name == null ? "" : name;
        // Bold adds one pixel per glyph in Minecraft's width computation, so measure it as bold.
        String fitted = fitToPx(plain, CELL_NAME_PX - ROW_GAP_PX, bold);
        int width = Glyphs.textAdvancePx(fitted, bold);
        String body = bold
            ? "<" + Palette.TEXT + "><bold>" + fitted + "</bold></" + Palette.TEXT + ">"
            : "<#F8FAFC>" + fitted + "</#F8FAFC>";
        return body + pad(Math.max(0, CELL_NAME_PX - width));
    }

    /** Same as {@link #nameCell(String, boolean)} but the label already carries its colours. */
    public static String nameCell(String markup, boolean bold, String plain) {
        String fittedPlain = fitToPx(plain, CELL_NAME_PX - ROW_GAP_PX);
        int width = Glyphs.textAdvancePx(fittedPlain, false);
        String trimmed = fittedPlain.equals(plain) ? markup : markup.replace(plain, fittedPlain);
        return trimmed + pad(Math.max(0, CELL_NAME_PX - width));
    }

    /** Column 4: the stat value, right aligned in a fixed cell. */
    public static String valueCell(String value, String colour, String hex) {
        int width = Glyphs.textAdvancePx(value, true);
        int lead = Math.max(0, CELL_VALUE_PX - width);
        return pad(lead) + colour + "<bold>" + value + "</bold></" + hex + ">";
    }

    /** Column 5: the stat artwork, padded to the same width whatever the icon is. */
    public static String statCell(char glyph) {
        int width = Glyphs.textAdvancePx(String.valueOf(glyph), false);
        return pad(ROW_GAP_PX)
            + "<!shadow><#F8FAFC>" + glyph + "</#F8FAFC></!shadow>"
            + pad(Math.max(0, CELL_STAT_PX - ROW_GAP_PX - width));
    }

    private static String blankCell(int pixels) {
        return pad(pixels);
    }

    private void refreshBoardTexts(HologramInstance inst) {
        if (!inst.kind.isLeaderboard()) {
            String markup = buildStationMarkup(inst.kind);
            if (!markup.equals(inst.lastRenderedSignature) && inst.headerDisplay != null && inst.headerDisplay.isValid()) {
                inst.lastRenderedSignature = markup;
                inst.headerDisplay.text(Msg.parse(fr.bosketball.util.Glyphs.smallCapsLabels(markup)));
            }
            return;
        }

        StatsManager.LeaderboardCategory cat = inst.activeCategory;
        List<PlayerProfile> top = plugin.statsManager().topBy(cat, BOARD_ROWS);
        StringBuilder sig = new StringBuilder(cat.name()).append('|');
        for (PlayerProfile p : top) {
            sig.append(p.lastKnownName()).append(':').append(cat.extract(p)).append(';');
        }
        String sigStr = sig.toString();
        if (sigStr.equals(inst.lastRenderedSignature)) {
            return;
        }
        inst.lastRenderedSignature = sigStr;

        if (inst.headerDisplay != null && inst.headerDisplay.isValid()) {
            // One title, one accent. The server is English everywhere, holograms included.
            String titleText = "<" + Palette.GOLD + ">ʟᴇᴀᴅᴇʀʙᴏᴀʀᴅ • "
                + fr.bosketball.util.SmallCaps.toSmallCaps(cat.label()) + "</" + Palette.GOLD + ">";
            inst.headerDisplay.text(Msg.parse(
                Glyphs.shift(24) + titleText + Glyphs.shift(24)));
        }

        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        // One accent for the whole board. Before, the value changed colour with the category
        // (blue for points, green for wins, orange for dunks): switching tabs repainted the board
        // instead of moving it, and each category competed with the gold of the title.
        String valColor = "<" + Palette.GOLD_SOFT + ">";
        String valHex = Palette.GOLD_SOFT;

        for (int i = 0; i < inst.rowDisplays.size(); i++) {
            TextDisplay rowTd = inst.rowDisplays.get(i);
            if (rowTd == null || !rowTd.isValid()) {
                continue;
            }
            int place = i + 1;
            // Places 1-3 keep a warm tint, the rest is quiet: a hierarchy of three, not of four.
            String placeHex = place == 1 ? Palette.GOLD : (place <= 3 ? Palette.GOLD_SOFT : Palette.MUTED);
            StringBuilder row = new StringBuilder(160);
            row.append(placeCell(place, placeHex));

            if (i < top.size()) {
                PlayerProfile p = top.get(i);
                row.append(pillCell(p.effectiveRank().pillGlyph()));
                row.append(nameCell(p.lastKnownName(), place == 1));
                row.append(valueCell(nf.format(cat.extract(p)), valColor, valHex));
            } else {
                // An empty slot used to print "— Unclaimed —" and then stop, so its plate was
                // shorter than the others and the board looked torn. It is a dash in the name
                // column and a plate of exactly the same width.
                row.append(pillCell(' '));
                row.append(nameCell("<" + Palette.DIM + ">—</" + Palette.DIM + ">", false, "—"));
                row.append(valueCell("", "<" + Palette.DIM + ">", Palette.DIM));
            }

            rowTd.text(Msg.parse(padTo(row.toString(), BOARD_ROW_PX)));
        }

        if (inst.footerDisplay != null && inst.footerDisplay.isValid()) {
            StringBuilder fb = new StringBuilder(64);
            StatsManager.LeaderboardCategory[] pills = {
                StatsManager.LeaderboardCategory.ELO,
                StatsManager.LeaderboardCategory.POINTS,
                StatsManager.LeaderboardCategory.COINS,
                StatsManager.LeaderboardCategory.WINS
            };
            String[] shortLabels = {
                fr.bosketball.util.SmallCaps.toSmallCaps("Elo"),
                fr.bosketball.util.SmallCaps.toSmallCaps("Points"),
                fr.bosketball.util.SmallCaps.toSmallCaps("Coins"),
                fr.bosketball.util.SmallCaps.toSmallCaps("Wins")
            };
            for (int i = 0; i < pills.length; i++) {
                StatsManager.LeaderboardCategory c = pills[i];
                boolean active = (c == cat);
                if (i > 0) {
                    fb.append(" <" + Palette.DIM + ">·</" + Palette.DIM + "> ");
                }
                if (active) {
                    fb.append("<" + Palette.GOLD + ">[ ").append(shortLabels[i]).append(" ]</" + Palette.GOLD + ">");
                } else {
                    fb.append("<" + Palette.MUTED + ">").append(shortLabels[i]).append("</" + Palette.MUTED + ">");
                }
            }
            fb.append(" <" + Palette.DIM + ">│</" + Palette.DIM + "> <" + Palette.MUTED
                + ">Top 10</" + Palette.MUTED + ">");
            inst.footerDisplay.text(Msg.parse(Glyphs.shift(20) + fb + Glyphs.shift(20)));
        }
    }

    private String buildStationMarkup(StationKind kind) {
        List<String> lines = new ArrayList<>();
        lines.add("<" + Palette.GOLD + "><bold>" + kind.titleSmallCaps() + "</bold></" + Palette.GOLD + ">");
        lines.add("<#64748B>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</#64748B>");

        if (kind == StationKind.PLAY) {
            int inMatch = plugin.gameManager().activePlayerCount();
            int inQueue = plugin.gameManager().queuedCount();
            lines.add("<#F8FAFC>Ranked 1v1 • 2v2 • 3v3 • 5v5</#F8FAFC>");
            lines.add("<" + Palette.ICE + ">3-Point Contest & Solo Practice</" + Palette.ICE + ">");
            lines.add("");
            lines.add("<#94A3B8>Playing:</#94A3B8> <" + Palette.SUCCESS + "><bold>" + inMatch + "</bold></" + Palette.SUCCESS + "> <#64748B>·</#64748B> <#94A3B8>Queued:</#94A3B8> <" + Palette.ICE + "><bold>" + inQueue + "</bold></" + Palette.ICE + ">");
            lines.add("");
            lines.add("<" + Palette.SUCCESS + "><bold>▸ ᴄʟɪᴄᴋ ɴᴘᴄ ᴛᴏ ᴘʟᴀʏ!</bold></" + Palette.SUCCESS + ">");
        } else {
            lines.add("<#F8FAFC>3D Basketballs • Signature Dunks</#F8FAFC>");
            lines.add("<" + Palette.VIOLET + ">54 Collector Chat Tags</" + Palette.VIOLET + ">");
            lines.add("");
            lines.add("<" + Palette.GOLD_SOFT + "><bold>▸ ᴄʟɪᴄᴋ ɴᴘᴄ ᴛᴏ ꜱʜᴏᴘ!</bold></" + Palette.GOLD_SOFT + ">");
        }
        return String.join("\n", lines);
    }

    private void despawnInstance(HologramInstance inst) {
        inst.lastRenderedSignature = "";
        if (inst.propDisplay != null) {
            inst.propDisplay.remove();
            inst.propDisplay = null;
        }
        if (inst.npcMannequin != null) {
            interactionBindings.remove(inst.npcMannequin.getUniqueId());
            ACTIVE_NPC_SCOREBOARD_ENTRIES.remove(inst.npcMannequin.getUniqueId().toString());
            inst.npcMannequin.remove();
            inst.npcMannequin = null;
        }
        if (inst.npcFloorRing != null) {
            inst.npcFloorRing.remove();
            inst.npcFloorRing = null;
        }
        if (inst.headerDisplay != null) {
            inst.headerDisplay.remove();
            inst.headerDisplay = null;
        }
        for (TextDisplay td : inst.rowDisplays) {
            if (td != null) {
                td.remove();
            }
        }
        inst.rowDisplays.clear();
        if (inst.footerDisplay != null) {
            inst.footerDisplay.remove();
            inst.footerDisplay = null;
        }
        if (inst.leftArrowDisplay != null) {
            inst.leftArrowDisplay.remove();
            inst.leftArrowDisplay = null;
        }
        if (inst.rightArrowDisplay != null) {
            inst.rightArrowDisplay.remove();
            inst.rightArrowDisplay = null;
        }
        for (Interaction inter : new Interaction[]{inst.mainClickBox, inst.leftClickBox, inst.rightClickBox}) {
            if (inter != null) {
                interactionBindings.remove(inter.getUniqueId());
                inter.remove();
            }
        }
        inst.mainClickBox = null;
        inst.leftClickBox = null;
        inst.rightClickBox = null;
    }

    public void despawnAllEntities() {
        for (HologramInstance inst : holograms.values()) {
            despawnInstance(inst);
        }
        interactionBindings.clear();
    }

    private void sweepOrphanHoloEntities() {
        if (Bukkit.getServer() == null) {
            return;
        }
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getScoreboardTags().contains(HOLO_ENTITY_TAG)) {
                    e.remove();
                }
            }
        }
    }
}
