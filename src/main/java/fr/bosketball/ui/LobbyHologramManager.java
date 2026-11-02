package fr.bosketball.ui;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.config.AtomicYamlWriter;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
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
 * Live Floating 3D Hologram Leaderboards & Interactive 3D Lobby Stations
 * (MCC Island / Origin Realms / Cytooxien style):
 * - Rotating 3D ItemDisplay prop floating above a glassy dark-slate TextDisplay panel
 * - Invisible Interaction hitbox so Left-Click or Right-Click opens the matching GUI immediately!
 */
public final class LobbyHologramManager {

    public static final String HOLO_ENTITY_TAG = "bosketball_lobby_holo";

    public enum StationKind {
        ELO("elo", "trophy_mvp", "🏆 ᴛᴏᴘ ᴇʟᴏ ʀᴀɴᴋɪɴɢꜱ"),
        POINTS("points", "basketball_gold", "🏀 ᴛᴏᴘ ᴄᴀʀᴇᴇʀ ꜱᴄᴏʀᴇʀꜱ"),
        COINS("coins", "basketball_money", "🪙 ʀɪᴄʜᴇꜱᴛ ʙᴀʟʟᴇʀꜱ"),
        PLAY("play", "prop_play_emblem", "⚡ ᴘʟᴀʏ ʙᴏꜱᴋᴇᴛʙᴀʟʟ"),
        SHOP("shop", "prop_shop_crate", "🛒 ʙᴏᴜᴛɪǫᴜᴇ ꜱʜᴏᴘ");

        private final String id;
        private final String propModelKey;
        private final String titleSmallCaps;

        StationKind(String id, String propModelKey, String titleSmallCaps) {
            this.id = id;
            this.propModelKey = propModelKey;
            this.titleSmallCaps = titleSmallCaps;
        }

        public String id() {
            return id;
        }

        public String propModelKey() {
            return propModelKey;
        }

        public String titleSmallCaps() {
            return titleSmallCaps;
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

    public static final class HologramInstance {
        private final String id;
        private final StationKind kind;
        private final String worldName;
        private final double x;
        private final double y;
        private final double z;

        private ItemDisplay propDisplay;
        private TextDisplay textDisplay;
        private Interaction clickBox;
        private String lastRenderedMarkup = "";

        public HologramInstance(String id, StationKind kind, String worldName, double x, double y, double z) {
            this.id = id;
            this.kind = kind;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String id() {
            return id;
        }

        public StationKind kind() {
            return kind;
        }

        public Location baseLocation() {
            World w = Bukkit.getServer() != null ? Bukkit.getWorld(worldName) : null;
            return w != null ? new Location(w, x, y, z) : null;
        }
    }

    private final BosketBallPlugin plugin;
    private final File storageFile;
    private final Map<String, HologramInstance> holograms = new ConcurrentHashMap<>();
    private final Map<UUID, String> interactionToHoloId = new ConcurrentHashMap<>();
    private int animTick = 0;

    public LobbyHologramManager(BosketBallPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = plugin != null ? new File(plugin.getDataFolder(), "holograms.yml") : null;
    }

    public Collection<HologramInstance> allHolograms() {
        return holograms.values();
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
                sec.getDouble("z")
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
        }
        String hdr = "# BosketBall — Floating 3D Lobby Holograms & Stations\n";
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
        HologramInstance inst = new HologramInstance(
            id,
            kind,
            loc.getWorld().getName(),
            loc.getX(),
            loc.getY(),
            loc.getZ()
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
    private static final Vector3f SCALE_STATION = new Vector3f(1.15f, 1.15f, 1.15f);
    private static final Vector3f SCALE_LEADERBOARD = new Vector3f(0.92f, 0.92f, 0.92f);

    public boolean handlePossibleStationClick(Player player, Entity entity) {
        if (player == null || entity == null) {
            return false;
        }
        if (plugin.gameManager().isInMatch(player.getUniqueId())
            || plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())
            || (plugin.cinematicCamera() != null && plugin.cinematicCamera().isWatching(player.getUniqueId()))) {
            return false;
        }
        String holoId = interactionToHoloId.get(entity.getUniqueId());
        if (holoId == null) {
            return false;
        }
        HologramInstance inst = holograms.get(holoId);
        if (inst == null) {
            return false;
        }
        Msg.playPersonalPackSound(player, "ui.click", 0.8f, 1.25f);
        switch (inst.kind) {
            case ELO -> plugin.guiMenu().openLeaderboards(player, StatsManager.LeaderboardCategory.ELO);
            case POINTS -> plugin.guiMenu().openLeaderboards(player, StatsManager.LeaderboardCategory.POINTS);
            case COINS -> plugin.guiMenu().openLeaderboards(player, StatsManager.LeaderboardCategory.COINS);
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
            if (inst.propDisplay == null || !inst.propDisplay.isValid()
                || inst.textDisplay == null || !inst.textDisplay.isValid()
                || inst.clickBox == null || !inst.clickBox.isValid()) {
                spawnEntitiesFor(inst);
                continue;
            }
            Vector3f scaleVec = (inst.kind == StationKind.PLAY || inst.kind == StationKind.SHOP)
                ? SCALE_STATION : SCALE_LEADERBOARD;
            inst.propDisplay.setInterpolationDelay(0);
            inst.propDisplay.setInterpolationDuration(3);
            inst.propDisplay.setTransformation(new Transformation(
                bobOffset,
                spinAxis,
                scaleVec,
                IDENTITY_AXIS
            ));
            if (refreshText) {
                String markup = buildHologramMarkup(inst.kind);
                if (!markup.equals(inst.lastRenderedMarkup)) {
                    inst.lastRenderedMarkup = markup;
                    inst.textDisplay.text(Msg.parse(markup));
                }
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
        boolean isLeaderboard = (inst.kind == StationKind.ELO || inst.kind == StationKind.POINTS || inst.kind == StationKind.COINS);
        double textY = base.getY() + (isLeaderboard ? 1.05 : 1.15);
        double propY = base.getY() + (isLeaderboard ? 2.85 : 2.25);

        ItemStack propStack = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = propStack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey("bosketball", inst.kind.propModelKey()));
            propStack.setItemMeta(meta);
        }

        inst.propDisplay = w.spawn(new Location(w, base.getX(), propY, base.getZ()), ItemDisplay.class, id -> {
            id.addScoreboardTag(HOLO_ENTITY_TAG);
            id.setPersistent(false);
            id.setItemStack(propStack);
            id.setBillboard(Display.Billboard.FIXED);
            id.setBrightness(new Display.Brightness(15, 15));
            id.setViewRange(0.75f);
        });

        String initialMarkup = buildHologramMarkup(inst.kind);
        inst.lastRenderedMarkup = initialMarkup;
        inst.textDisplay = w.spawn(new Location(w, base.getX(), textY, base.getZ()), TextDisplay.class, td -> {
            td.addScoreboardTag(HOLO_ENTITY_TAG);
            td.setPersistent(false);
            td.setBillboard(Display.Billboard.VERTICAL);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setBackgroundColor(Color.fromARGB(205, 10, 14, 24));
            td.setShadowed(true);
            td.setLineWidth(240);
            td.setBrightness(new Display.Brightness(15, 15));
            td.setViewRange(0.75f);
            td.text(Msg.parse(initialMarkup));
        });

        inst.clickBox = w.spawn(new Location(w, base.getX(), base.getY() + 0.1, base.getZ()), Interaction.class, inter -> {
            inter.addScoreboardTag(HOLO_ENTITY_TAG);
            inter.setPersistent(false);
            inter.setInteractionWidth(1.55f);
            inter.setInteractionHeight(isLeaderboard ? 3.1f : 2.6f);
            inter.setResponsive(true);
        });
        interactionToHoloId.put(inst.clickBox.getUniqueId(), inst.id);
    }

    private String buildHologramMarkup(StationKind kind) {
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
        } else if (kind == StationKind.SHOP) {
            lines.add("<white>3D Basketballs • Signature Dunks</white>");
            lines.add("<#E879F9>54 Collector Chat Tags</#E879F9>");
            lines.add("");
            lines.add("<#FDE047><bold>▸ ᴄʟɪᴄᴋ ᴛᴏ ʙʀᴏᴡꜱᴇ ꜱʜᴏᴘ!</bold></#FDE047>");
        } else {
            StatsManager.LeaderboardCategory cat = switch (kind) {
                case POINTS -> StatsManager.LeaderboardCategory.POINTS;
                case COINS -> StatsManager.LeaderboardCategory.COINS;
                default -> StatsManager.LeaderboardCategory.ELO;
            };
            List<PlayerProfile> top = plugin.statsManager().topBy(cat, 5);
            if (top.isEmpty()) {
                lines.add("<gray>Play Ranked matches to claim #1!</gray>");
            } else {
                for (int i = 0; i < top.size(); i++) {
                    PlayerProfile p = top.get(i);
                    int place = i + 1;
                    String medal = place <= 3
                        ? "<!shadow><white>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, place) + "</white></!shadow>"
                        : "<#94A3B8>#" + place + "</#94A3B8>";
                    String val = switch (cat) {
                        case POINTS -> "<#38BDF8><bold>" + p.points() + "</bold></#38BDF8> <gray>PTS</gray>";
                        case COINS -> "<#FDE047><bold>" + p.coins() + "</bold></#FDE047> <gray>Coins</gray>";
                        default -> "<#FBBF24><bold>" + p.elo() + "</bold></#FBBF24> <gray>ELO</gray>";
                    };
                    lines.add(medal + " <!shadow><white>" + p.effectiveRank().pillGlyph() + "</white></!shadow> <white>" + p.lastKnownName() + "</white> <dark_gray>—</dark_gray> " + val);
                }
            }
            lines.add("");
            lines.add("<#38BDF8><bold>▸ ᴄʟɪᴄᴋ ꜰᴏʀ ꜰᴜʟʟ ʀᴀɴᴋɪɴɢꜱ</bold></#38BDF8>");
        }
        return String.join("\n", lines);
    }

    private void despawnInstance(HologramInstance inst) {
        inst.lastRenderedMarkup = "";
        if (inst.propDisplay != null) {
            inst.propDisplay.remove();
            inst.propDisplay = null;
        }
        if (inst.textDisplay != null) {
            inst.textDisplay.remove();
            inst.textDisplay = null;
        }
        if (inst.clickBox != null) {
            interactionToHoloId.remove(inst.clickBox.getUniqueId());
            inst.clickBox.remove();
            inst.clickBox = null;
        }
    }

    public void despawnAllEntities() {
        for (HologramInstance inst : holograms.values()) {
            despawnInstance(inst);
        }
        interactionToHoloId.clear();
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
