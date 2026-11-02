package fr.bosketball.ui;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.config.AtomicYamlWriter;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Fixed Interactive Multi-Row Dark-Panel Wall Leaderboards &amp; Lobby Stations:
 * - Leaderboards (ELO / POINTS / COINS): Fixed architectural board (image-40 style) with
 *   crowned title pill, 7 pixel-aligned individual dark row bars, clickable Left/Right category
 *   arrows, bottom category filter bar, and a rotating 3D prop +1 block higher above the board.
 * - Play &amp; Boutique Shop Stations: Clean floating dark-slate holograms with zero floating 3D models.
 */
public final class LobbyHologramManager {

    public static final String HOLO_ENTITY_TAG = "bosketball_lobby_holo";
    private static final int BOARD_ROWS = 7;
    private static final int TARGET_ROW_WIDTH_PX = 192;

    private static final StatsManager.LeaderboardCategory[] BOARD_CYCLE = {
        StatsManager.LeaderboardCategory.ELO,
        StatsManager.LeaderboardCategory.POINTS,
        StatsManager.LeaderboardCategory.COINS,
        StatsManager.LeaderboardCategory.WINS,
        StatsManager.LeaderboardCategory.DUNKS
    };

    public enum StationKind {
        ELO("elo", "trophy_mvp", "🏆 ᴛᴏᴘ ᴇʟᴏ ʀᴀɴᴋɪɴɢꜱ", true),
        POINTS("points", "basketball_gold", "🏀 ᴛᴏᴘ ᴄᴀʀᴇᴇʀ ꜱᴄᴏʀᴇʀꜱ", true),
        COINS("coins", "basketball_money", "🪙 ʀɪᴄʜᴇꜱᴛ ʙᴀʟʟᴇʀꜱ", true),
        PLAY("play", null, "⚡ ᴘʟᴀʏ ʙᴏꜱᴋᴇᴛʙᴀʟʟ", false),
        SHOP("shop", null, "🛒 ʙᴏᴜᴛɪǫᴜᴇ ꜱʜᴏᴘ", false);

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

        private StatsManager.LeaderboardCategory activeCategory;
        private ItemDisplay propDisplay;
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
            this.id = id;
            this.kind = kind;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
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

        public StatsManager.LeaderboardCategory activeCategory() {
            return activeCategory;
        }

        public Location baseLocation() {
            World w = Bukkit.getServer() != null ? Bukkit.getWorld(worldName) : null;
            return w != null ? new Location(w, x, y, z, yaw, 0f) : null;
        }
    }

    private final BosketBallPlugin plugin;
    private final File storageFile;
    private final Map<String, HologramInstance> holograms = new ConcurrentHashMap<>();
    private final Map<UUID, HoloClickBinding> interactionBindings = new ConcurrentHashMap<>();
    private int animTick = 0;

    public LobbyHologramManager(BosketBallPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = plugin != null ? new File(plugin.getDataFolder(), "holograms.yml") : null;
    }

    public Collection<HologramInstance> allHolograms() {
        return holograms.values();
    }

    public int stationCount() {
        return holograms.size();
    }

    public static float snapFacingYaw(float playerLookYaw) {
        // Face directly back toward the OP who placed the board, snapping cleanly to 45-deg increments when close
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

    public void loadAndSpawnAll() {
        despawnAllEntities();
        sweepOrphanHoloEntities();
        holograms.clear();
        if (storageFile == null || !storageFile.exists()) {
            return;
        }
        YamlConfiguration yml = AtomicYamlWriter.loadSafe(storageFile, plugin.getLogger());
        ConfigurationSection root = yml.getConfigurationSection("holograms");
        if (root == null) {
            return;
        }
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
                (float) sec.getDouble("yaw", 0.0)
            );
            holograms.put(id, inst);
            spawnEntitiesFor(inst);
        }
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
        }
        String hdr = "# BosketBall — Fixed Wall Leaderboards & Lobby Hologram Stations\n";
        AtomicYamlWriter.writeYamlAtomically(storageFile, hdr, yml, plugin.getLogger());
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

    public boolean handlePossibleStationClick(Player player, Entity entity) {
        if (player == null || entity == null) {
            return false;
        }
        if (plugin.gameManager().isInMatch(player.getUniqueId())
            || plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())
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

    public void tickAnimation() {
        if (holograms.isEmpty() || Bukkit.getServer() == null) {
            return;
        }
        animTick++;
        float angleRad = (animTick * 0.09f) % ((float) (Math.PI * 2.0));
        float bobY = (float) (Math.sin(animTick * 0.10) * 0.08);
        boolean refreshText = (animTick % 25 == 0);
        Vector3f bobOffset = new Vector3f(0f, bobY, 0f);
        AxisAngle4f spinAxis = new AxisAngle4f(angleRad, 0f, 1f, 0f);

        for (HologramInstance inst : holograms.values()) {
            Location base = inst.baseLocation();
            if (base == null || !base.isChunkLoaded()) {
                continue;
            }
            if (inst.headerDisplay == null || !inst.headerDisplay.isValid()
                || inst.mainClickBox == null || !inst.mainClickBox.isValid()
                || (inst.kind.has3dProp() && (inst.propDisplay == null || !inst.propDisplay.isValid()))) {
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
            if (refreshText) {
                refreshBoardTexts(inst);
            }
        }
    }

    private void spawnEntitiesFor(HologramInstance inst) {
        despawnInstance(inst);
        Location base = inst.baseLocation();
        if (base == null || base.getWorld() == null) {
            return;
        }
        World w = base.getWorld();

        // 1. Optional 3D Floating Prop ONLY for Leaderboards (Requirement #10: removed on Play & Shop; Requirement #11: +1 block higher = +3.95!)
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
            // Clean Floating Station Card (PLAY / SHOP — zero 3D floating model!)
            double textY = base.getY() + 1.20;
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
                td.text(Msg.parse(stationMarkup));
            });

            inst.mainClickBox = w.spawn(new Location(w, base.getX(), base.getY() + 0.15, base.getZ()), Interaction.class, inter -> {
                inter.addScoreboardTag(HOLO_ENTITY_TAG);
                inter.setPersistent(false);
                inter.setInteractionWidth(1.55f);
                inter.setInteractionHeight(2.35f);
                inter.setResponsive(true);
            });
            interactionBindings.put(inst.mainClickBox.getUniqueId(), new HoloClickBinding(inst.id, ClickAction.MAIN_OPEN));
            return;
        }

        // 2. FIXED ARCHITECTURAL MULTI-ROW DARK-PANEL LEADERBOARD BOARD (Requirement #17 — image-40.png!)
        float boardYaw = inst.yaw;
        double yawRad = Math.toRadians(boardYaw);
        // Unit right vector across the front of a Display facing boardYaw:
        // Facing yaw=0 (+Z South) -> Viewer looks North (-Z) -> Viewer's left is -X, Viewer's right is +X?
        // Wait: when looking from +Z toward -Z (facing North at a board whose front faces +Z South, yaw=0),
        // viewer's left is West (-X) and viewer's right is East (+X): cos(0)=+1 -> (-cos, -sin) = left, (+cos, +sin) = right?
        // Check: at yaw=0, forward = (0, 0, +1). Viewer stands at (0, 0, +3) looking at (0, 0, 0) (yaw=180, North).
        // Viewer's right hand points East (+X)! Wait: facing North (-Z), East (+X) IS on your right!
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        // Top Floating Title Pill (y = base + 3.04)
        inst.headerDisplay = w.spawn(new Location(w, base.getX(), base.getY() + 3.04, base.getZ(), boardYaw, 0f), TextDisplay.class, td -> {
            td.addScoreboardTag(HOLO_ENTITY_TAG);
            td.setPersistent(false);
            td.setBillboard(Display.Billboard.FIXED);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setBackgroundColor(Color.fromARGB(224, 14, 18, 30));
            td.setShadowed(true);
            td.setLineWidth(300);
            td.setBrightness(new Display.Brightness(15, 15));
            td.setViewRange(0.90f);
        });

        // 7 Individual Aligned Dark-Slate Row Bars (y = base + 2.62 down to base + 0.88, step 0.29)
        for (int r = 0; r < BOARD_ROWS; r++) {
            final boolean topRow = (r == 0);
            double rowY = base.getY() + 2.62 - (r * 0.29);
            TextDisplay rowTd = w.spawn(new Location(w, base.getX(), rowY, base.getZ(), boardYaw, 0f), TextDisplay.class, td -> {
                td.addScoreboardTag(HOLO_ENTITY_TAG);
                td.setPersistent(false);
                td.setBillboard(Display.Billboard.FIXED);
                td.setAlignment(TextDisplay.TextAlignment.CENTER);
                td.setBackgroundColor(topRow ? Color.fromARGB(218, 44, 34, 16) : Color.fromARGB(204, 16, 21, 34));
                td.setShadowed(true);
                td.setLineWidth(340);
                td.setBrightness(new Display.Brightness(15, 15));
                td.setViewRange(0.90f);
            });
            inst.rowDisplays.add(rowTd);
        }

        // Bottom Category Filter Strip Pill (y = base + 0.46)
        inst.footerDisplay = w.spawn(new Location(w, base.getX(), base.getY() + 0.46, base.getZ(), boardYaw, 0f), TextDisplay.class, td -> {
            td.addScoreboardTag(HOLO_ENTITY_TAG);
            td.setPersistent(false);
            td.setBillboard(Display.Billboard.FIXED);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setBackgroundColor(Color.fromARGB(220, 13, 17, 28));
            td.setShadowed(true);
            td.setLineWidth(340);
            td.setBrightness(new Display.Brightness(15, 15));
            td.setViewRange(0.90f);
        });

        // Left & Right Interactive Navigation Arrow Pills (image-40.png flanking < and > buttons!)
        double arrowOffset = 1.48;
        double arrowY = base.getY() + 1.75;
        Location leftLoc = new Location(w, base.getX() - rightX * arrowOffset, arrowY, base.getZ() - rightZ * arrowOffset, boardYaw, 0f);
        Location rightLoc = new Location(w, base.getX() + rightX * arrowOffset, arrowY, base.getZ() + rightZ * arrowOffset, boardYaw, 0f);

        inst.leftArrowDisplay = w.spawn(leftLoc, TextDisplay.class, td -> {
            td.addScoreboardTag(HOLO_ENTITY_TAG);
            td.setPersistent(false);
            td.setBillboard(Display.Billboard.FIXED);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setBackgroundColor(Color.fromARGB(218, 18, 24, 38));
            td.setShadowed(true);
            td.setBrightness(new Display.Brightness(15, 15));
            td.setViewRange(0.85f);
            td.text(Msg.parse(" <#FBBF24><bold>◀</bold></#FBBF24> "));
        });
        inst.rightArrowDisplay = w.spawn(rightLoc, TextDisplay.class, td -> {
            td.addScoreboardTag(HOLO_ENTITY_TAG);
            td.setPersistent(false);
            td.setBillboard(Display.Billboard.FIXED);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setBackgroundColor(Color.fromARGB(218, 18, 24, 38));
            td.setShadowed(true);
            td.setBrightness(new Display.Brightness(15, 15));
            td.setViewRange(0.85f);
            td.text(Msg.parse(" <#FBBF24><bold>▶</bold></#FBBF24> "));
        });

        // Center Board + Left Arrow + Right Arrow Clickboxes
        inst.mainClickBox = w.spawn(new Location(w, base.getX(), base.getY() + 0.35, base.getZ()), Interaction.class, inter -> {
            inter.addScoreboardTag(HOLO_ENTITY_TAG);
            inter.setPersistent(false);
            inter.setInteractionWidth(2.15f);
            inter.setInteractionHeight(3.05f);
            inter.setResponsive(true);
        });
        interactionBindings.put(inst.mainClickBox.getUniqueId(), new HoloClickBinding(inst.id, ClickAction.MAIN_OPEN));

        inst.leftClickBox = w.spawn(leftLoc.clone().subtract(0, 0.28, 0), Interaction.class, inter -> {
            inter.addScoreboardTag(HOLO_ENTITY_TAG);
            inter.setPersistent(false);
            inter.setInteractionWidth(0.62f);
            inter.setInteractionHeight(0.75f);
            inter.setResponsive(true);
        });
        interactionBindings.put(inst.leftClickBox.getUniqueId(), new HoloClickBinding(inst.id, ClickAction.PREV_CATEGORY));

        inst.rightClickBox = w.spawn(rightLoc.clone().subtract(0, 0.28, 0), Interaction.class, inter -> {
            inter.addScoreboardTag(HOLO_ENTITY_TAG);
            inter.setPersistent(false);
            inter.setInteractionWidth(0.62f);
            inter.setInteractionHeight(0.75f);
            inter.setResponsive(true);
        });
        interactionBindings.put(inst.rightClickBox.getUniqueId(), new HoloClickBinding(inst.id, ClickAction.NEXT_CATEGORY));

        inst.lastRenderedSignature = "";
        refreshBoardTexts(inst);
    }

    private void refreshBoardTexts(HologramInstance inst) {
        if (!inst.kind.isLeaderboard()) {
            String markup = buildStationMarkup(inst.kind);
            if (!markup.equals(inst.lastRenderedSignature) && inst.headerDisplay != null && inst.headerDisplay.isValid()) {
                inst.lastRenderedSignature = markup;
                inst.headerDisplay.text(Msg.parse(markup));
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
            String titleIcon = switch (cat) {
                case COINS -> "<!shadow><white>" + Glyphs.CHAT_ICON_COIN + "</white></!shadow>";
                case POINTS, DUNKS -> "<!shadow><white>" + Glyphs.ICON_BALL + "</white></!shadow>";
                default -> "<!shadow><white>" + Glyphs.ICON_TROPHY + "</white></!shadow>";
            };
            inst.headerDisplay.text(Msg.parse(
                "  " + titleIcon + " <#FBBF24><bold>ʟᴇᴀᴅᴇʀʙᴏᴀʀᴅ • " + cat.label().toUpperCase(Locale.ROOT) + "</bold></#FBBF24>  "
            ));
        }

        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        char statGlyph = switch (cat) {
            case COINS -> Glyphs.CHAT_ICON_COIN;
            case POINTS, THREE_POINTERS -> Glyphs.ICON_BALL;
            case DUNKS -> Glyphs.ICON_FIRE;
            case WINS, MVPS -> Glyphs.ICON_CROWN;
            default -> Glyphs.ICON_TROPHY;
        };
        String valColor = switch (cat) {
            case COINS -> "<#FDE047>";
            case POINTS, THREE_POINTERS -> "<#38BDF8>";
            case DUNKS -> "<#FB923C>";
            case WINS -> "<#4ADE80>";
            default -> "<#FBBF24>";
        };
        String valClose = "</" + valColor.substring(1);

        for (int i = 0; i < inst.rowDisplays.size(); i++) {
            TextDisplay rowTd = inst.rowDisplays.get(i);
            if (rowTd == null || !rowTd.isValid()) {
                continue;
            }
            int place = i + 1;
            String placeCol = switch (place) {
                case 1 -> "<#FDE047><bold>";
                case 2 -> "<#E2E8F0><bold>";
                case 3 -> "<#F59E0B><bold>";
                default -> "<#94A3B8><bold>";
            };
            String placePlain = place + ". ";
            int leftPx = 4 + Glyphs.textAdvancePx(placePlain, true);

            String leftMm;
            String rightMm;
            int rightPx;

            if (i < top.size()) {
                PlayerProfile p = top.get(i);
                char rankCh = p.effectiveRank().pillGlyph();
                String name = p.lastKnownName();
                if (name.length() > 13) {
                    name = name.substring(0, 12) + "…";
                }
                leftPx += Glyphs.textAdvancePx(String.valueOf(rankCh), false) + 4 + Glyphs.textAdvancePx(name, place == 1);
                leftMm = " " + placeCol + placePlain + "</bold></" + placeCol.substring(1, 8) + ">"
                    + "<!shadow><white>" + rankCh + "</white></!shadow> "
                    + (place == 1 ? "<#FFFBEB><bold>" + name + "</bold></#FFFBEB>" : "<white>" + name + "</white>");

                String numStr = nf.format(cat.extract(p));
                rightPx = Glyphs.textAdvancePx(numStr, true) + 4 + 9 + 4;
                rightMm = valColor + "<bold>" + numStr + "</bold>" + valClose
                    + " <!shadow><white>" + statGlyph + "</white></!shadow> ";
            } else {
                String emptyLabel = "— Unclaimed —";
                leftPx += Glyphs.textAdvancePx(emptyLabel, false);
                leftMm = " " + placeCol + placePlain + "</bold></" + placeCol.substring(1, 8) + "><dark_gray>" + emptyLabel + "</dark_gray>";
                rightPx = Glyphs.textAdvancePx("—", false) + 4;
                rightMm = "<dark_gray>—</dark_gray> ";
            }

            int padPx = Math.max(8, TARGET_ROW_WIDTH_PX - leftPx - rightPx);
            String spacer = "<!shadow><white>" + Glyphs.shift(padPx) + "</white></!shadow>";
            rowTd.text(Msg.parse(leftMm + spacer + rightMm));
        }

        if (inst.footerDisplay != null && inst.footerDisplay.isValid()) {
            StringBuilder fb = new StringBuilder(" ");
            StatsManager.LeaderboardCategory[] pills = {
                StatsManager.LeaderboardCategory.ELO,
                StatsManager.LeaderboardCategory.POINTS,
                StatsManager.LeaderboardCategory.COINS,
                StatsManager.LeaderboardCategory.WINS
            };
            for (int i = 0; i < pills.length; i++) {
                StatsManager.LeaderboardCategory c = pills[i];
                boolean active = (c == cat);
                if (i > 0) {
                    fb.append(" <dark_gray>•</dark_gray> ");
                }
                if (active) {
                    fb.append("<#4ADE80><bold>[ ").append(c.label()).append(" ]</bold></#4ADE80>");
                } else {
                    fb.append("<gray>").append(c.label()).append("</gray>");
                }
            }
            fb.append("  <dark_gray>│</dark_gray>  <#38BDF8>Click Board for Top 10</#38BDF8> ");
            inst.footerDisplay.text(Msg.parse(fb.toString()));
        }
    }

    private String buildStationMarkup(StationKind kind) {
        List<String> lines = new ArrayList<>();
        lines.add("<#FBBF24><bold>" + kind.titleSmallCaps() + "</bold></#FBBF24>");
        lines.add("<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>");

        if (kind == StationKind.PLAY) {
            int inMatch = plugin.gameManager().activePlayerCount();
            int inQueue = plugin.gameManager().queuedCount();
            lines.add("<white>Ranked 1v1 • 2v2 • 3v3 • 5v5</white>");
            lines.add("<#38BDF8>3-Point Contest & Solo Practice</#38BDF8>");
            lines.add("");
            lines.add("<gray>Playing:</gray> <#4ADE80><bold>" + inMatch + "</bold></#4ADE80> <dark_gray>•</dark_gray> <gray>Queued:</gray> <#38BDF8><bold>" + inQueue + "</bold></#38BDF8>");
            lines.add("");
            lines.add("<#4ADE80><bold>▸ ᴄʟɪᴄᴋ ᴛᴏ ᴘʟᴀʏ!</bold></#4ADE80>");
        } else {
            lines.add("<white>3D Basketballs • Signature Dunks</white>");
            lines.add("<#E879F9>54 Collector Chat Tags</#E879F9>");
            lines.add("");
            lines.add("<#FDE047><bold>▸ ᴄʟɪᴄᴋ ᴛᴏ ʙʀᴏᴡꜱᴇ ꜱʜᴏᴘ!</bold></#FDE047>");
        }
        return String.join("\n", lines);
    }

    private void despawnInstance(HologramInstance inst) {
        inst.lastRenderedSignature = "";
        if (inst.propDisplay != null) {
            inst.propDisplay.remove();
            inst.propDisplay = null;
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
