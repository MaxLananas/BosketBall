package fr.bosketball.ui;

import com.destroystokyo.paper.SkinParts;
import com.destroystokyo.paper.profile.PlayerProfile;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.game.GameModeType;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.ChatTag;
import fr.bosketball.model.MatchHistoryEntry;
import fr.bosketball.model.PlayerSettings;
import fr.bosketball.social.SocialPartyManager;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.io.File;
import java.io.IOException;
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
import net.kyori.adventure.text.object.ObjectContents;
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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Interactive 3D Full-Screen Locker-Room Hub Stage inspired by image-6.png (Requirement #6):
 *   - Builds a warm dark-slate & oak 4-alcove Locker-Room stage
 *   - Spawns 4 live 3D Mannequin player models (Viewer with 👑 crown + Party / Friends / Top Legends)
 *   - Renders 5 screen-framed TextDisplay HUD panels:
 *       1) Top Navigation Bar (PLAY | LEADERBOARDS | HISTORY | FRIENDS | TAGS | SETTINGS + Group X/4)
 *       2) Left Selection List with slanted mint-green highlight bar (\uE130)
 *       3) Right PATCH NOTES card
 *       4) Bottom-Left Keybind Legend ([Z/S] Navigate • [Q/D] Tab • [SPACE/Click] Confirm • [SHIFT] Exit)
 *       5) Bottom-Right Dynamic Detail Card (Stats, History, Friends, Tags, Settings)
 *   - Driven smoothly via Paper 26.2 PlayerInputEvent (WASD + Space + Shift) AND mouse clicks/scroll!
 */
public final class LockerRoom3dMenuManager {

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

    private static final class StageSession {
        UUID viewerId;
        Location returnLocation;
        GameMode previousGameMode;
        boolean previousAllowFlight;
        boolean previousFlying;
        Location cameraAnchor;
        ItemDisplay cameraRig;

        TopTab activeTab = TopTab.PLAY;
        int selectedRow = 0;

        boolean lastForward;
        boolean lastBackward;
        boolean lastLeft;
        boolean lastRight;
        boolean lastJump;
        long openedAtMillis;

        TextDisplay topNavDisplay;
        TextDisplay topStatusDisplay;
        TextDisplay leftMenuDisplay;
        TextDisplay patchNotesDisplay;
        TextDisplay bottomKeysDisplay;
        TextDisplay detailCardDisplay;

        final List<Entity> stageProps = new ArrayList<>();
        final List<Entity> actorProps = new ArrayList<>();
        Location stageReference;
    }

    private static final StatsManager.LeaderboardCategory[] LB_CATEGORIES = {
        StatsManager.LeaderboardCategory.ELO,
        StatsManager.LeaderboardCategory.POINTS,
        StatsManager.LeaderboardCategory.WINS,
        StatsManager.LeaderboardCategory.ASSISTS,
        StatsManager.LeaderboardCategory.DUNKS,
        StatsManager.LeaderboardCategory.MVPS
    };

    private final BosketBallPlugin plugin;
    private final Map<UUID, StageSession> sessions = new ConcurrentHashMap<>();
    private Location stageOrigin;
    private boolean stageBuilt = false;
    private int tickCounter = 0;

    // Optional User-Built Custom Waiting Room ("je builderai le truc d'attente plus tard") via stage3d.yml!
    private Location customCamera;
    private final Location[] customSlots = new Location[4];

    public LockerRoom3dMenuManager(BosketBallPlugin plugin) {
        this.plugin = plugin;
        loadStageConfig();
    }

    public boolean isIn3dMenu(UUID playerId) {
        return sessions.containsKey(playerId);
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

    private void loadStageConfig() {
        if (plugin == null || !plugin.getDataFolder().exists()) {
            return;
        }
        File file = new File(plugin.getDataFolder(), "stage3d.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        this.customCamera = readLoc(yml.getConfigurationSection("camera"));
        for (int i = 0; i < 4; i++) {
            this.customSlots[i] = readLoc(yml.getConfigurationSection("slot" + (i + 1)));
        }
    }

    private void saveStageConfig() {
        if (plugin == null) {
            return;
        }
        plugin.getDataFolder().mkdirs();
        File file = new File(plugin.getDataFolder(), "stage3d.yml");
        YamlConfiguration yml = new YamlConfiguration();
        if (customCamera != null) {
            writeLoc(yml.createSection("camera"), customCamera);
        }
        for (int i = 0; i < 4; i++) {
            if (customSlots[i] != null) {
                writeLoc(yml.createSection("slot" + (i + 1)), customSlots[i]);
            }
        }
        try {
            yml.save(file);
        } catch (IOException ignored) {
        }
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
        sec.set("x", loc.getX());
        sec.set("y", loc.getY());
        sec.set("z", loc.getZ());
        sec.set("yaw", loc.getYaw());
        sec.set("pitch", loc.getPitch());
    }

    /**
     * Builds the sealed architectural 3D Locker-Room chamber in the primary arena world once on demand.
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

        double baseX = primary != null ? primary.center().getBlockX() + 190 : 190;
        double baseY = Math.max(72, primary != null ? primary.center().getBlockY() + 12 : 84);
        double baseZ = primary != null ? primary.center().getBlockZ() + 190 : 190;
        int ox = (int) baseX;
        int oy = (int) baseY;
        int oz = (int) baseZ;

        // Build sealed 19x9x14 Locker-Room chamber (camera at (ox, oy+1, oz-4) facing +Z toward alcoves at oz+5)
        for (int dx = -9; dx <= 9; dx++) {
            for (int dy = 0; dy <= 8; dy++) {
                for (int dz = -6; dz <= 7; dz++) {
                    Block b = world.getBlockAt(ox + dx, oy + dy, oz + dz);
                    boolean shell = (dx == -9 || dx == 9 || dy == 0 || dy == 8 || dz == -6 || dz == 7);
                    if (!shell) {
                        b.setType(Material.AIR, false);
                        continue;
                    }
                    if (dy == 0) {
                        // Warm stripped-oak hardwood stage floor matching image-6.png!
                        b.setType(Material.STRIPPED_OAK_WOOD, false);
                    } else if (dy == 8) {
                        // Coffered spruce & warm recessed froglight ceiling matching image-6.png
                        boolean light = (dz == 2 || dz == -1) && (dx == -4 || dx == 0 || dx == 4);
                        b.setType(light ? Material.OCHRE_FROGLIGHT : Material.SPRUCE_PLANKS, false);
                    } else {
                        b.setType(Material.DEEPSLATE_TILES, false);
                    }
                }
            }
        }

        // Back-wall 4 recessed dark stone Locker Alcoves at dz = 5..6 (dx centers: -5, -2, +2, +5, exact image-6.png!)
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = 1; dy <= 7; dy++) {
                world.getBlockAt(ox + dx, oy + dy, oz + 6).setType(Material.POLISHED_DEEPSLATE, false);
            }
        }
        int[] pillarXs = {-8, -7, -4, 0, 4, 7, 8};
        for (int px : pillarXs) {
            for (int dy = 1; dy <= 7; dy++) {
                world.getBlockAt(ox + px, oy + dy, oz + 5).setType(Material.DEEPSLATE_TILES, false);
                world.getBlockAt(ox + px, oy + dy, oz + 4).setType(
                    dy == 7 ? Material.DARK_OAK_PLANKS : (px == -8 || px == 8 ? Material.DEEPSLATE_TILES : Material.AIR),
                    false
                );
            }
        }
        // Overhead alcove soffit beam + warm alcove sconces + spruce floor grates (exact image-6.png!)
        int[] alcoveCenters = {-5, -2, 2, 5};
        for (int cx : alcoveCenters) {
            world.getBlockAt(ox + cx, oy + 6, oz + 5).setType(Material.OCHRE_FROGLIGHT, false);
            world.getBlockAt(ox + cx, oy + 4, oz + 6).setType(Material.OCHRE_FROGLIGHT, false);
            world.getBlockAt(ox + cx, oy + 7, oz + 4).setType(Material.DARK_OAK_PLANKS, false);
            world.getBlockAt(ox + cx, oy + 1, oz + 3).setType(Material.SPRUCE_TRAPDOOR, false);
        }

        stageOrigin = new Location(world, ox + 0.5, oy + 1.0, oz + 0.5);
        stageBuilt = true;
        return stageOrigin;
    }

    public void open(Player player, TopTab initialTab) {
        if (plugin.gameManager().isInMatch(player.getUniqueId())) {
            Msg.send(player, Msg.warn("Quittez d'abord le terrain (<yellow>/bb leave</yellow>) avant d'entrer dans le Vestiaire 3D."));
            return;
        }
        StageSession prev = sessions.get(player.getUniqueId());
        Location preservedReturn = (prev != null && prev.returnLocation != null)
            ? prev.returnLocation.clone()
            : player.getLocation().clone();
        GameMode preservedMode = (prev != null && prev.previousGameMode != null) ? prev.previousGameMode : player.getGameMode();
        boolean preservedAllowFlight = prev != null ? prev.previousAllowFlight : player.getAllowFlight();
        boolean preservedFlying = prev != null ? prev.previousFlying : player.isFlying();

        close(player, false);
        plugin.clearLobbyRig(player.getUniqueId(), false);
        plugin.centerTabManager().close(player);
        player.closeInventory();

        // If admin built & configured a custom Waiting Room ("je builderai le truc d'attente plus tard"),
        // NEVER place procedural chamber blocks — use their custom camera & slots directly!
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
        s.activeTab = initialTab != null ? initialTab : TopTab.PLAY;
        s.selectedRow = 0;
        s.openedAtMillis = System.currentTimeMillis();
        s.stageReference = stage.clone();

        if (usingCustomRoom) {
            s.cameraAnchor = customCamera.clone();
        } else {
            // Camera position facing +Z (yaw = 0, pitch = 0°) toward the 4 alcoves on a zero-jitter marker mount!
            s.cameraAnchor = stage.clone().add(0.0, 0.35, -3.65);
            s.cameraAnchor.setYaw(0.0f);
            s.cameraAnchor.setPitch(0.0f);
        }

        s.cameraAnchor.getChunk().load();
        stage.getChunk().load();

        // Spawn a rock-solid eye-level ItemDisplay Camera Rig & lock viewer via Spectator Target (zero ArmorStand dismount bugs!)
        Location eyeRigLoc = s.cameraAnchor.clone().add(0.0, 1.62, 0.0);
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
            d.setTeleportDuration(2);
        });
        hideFromOthers(player, s.cameraRig);
        s.stageProps.add(s.cameraRig);

        player.setFlying(false);
        player.setAllowFlight(false);
        player.teleport(eyeRigLoc);
        player.setGameMode(GameMode.SPECTATOR);
        player.setSpectatorTarget(s.cameraRig);

        // Hide viewer from other players & vice-versa inside the chamber so views never collide
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(player.getUniqueId())) {
                other.hidePlayer(plugin, player);
                player.hidePlayer(plugin, other);
            }
        }

        spawnStageMannequins(player, stage, s);
        spawnHudPanels(player, s);

        sessions.put(player.getUniqueId(), s);
        refreshPanels(player, s);
        Msg.playPersonalPackSound(player, "ui.click", 0.85f, 1.18f);
    }

    public void close(Player player, boolean restorePosition) {
        if (player == null) {
            return;
        }
        StageSession s = sessions.remove(player.getUniqueId());
        if (s == null) {
            return;
        }
        if (player.isOnline() && player.getGameMode() == GameMode.SPECTATOR) {
            player.setSpectatorTarget(null);
        }
        despawnSessionEntities(s);

        // If player manually exited the Waiting Room via SHIFT while queued, cancel their queue cleanly!
        if (restorePosition && plugin.gameManager() != null && plugin.gameManager().isQueued(player.getUniqueId())) {
            plugin.gameManager().leaveQueue(player, true);
        }

        // Restore player visibility (except for other players still inside their own 3D stage!)
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(player.getUniqueId()) && !sessions.containsKey(other.getUniqueId())) {
                other.showPlayer(plugin, player);
                player.showPlayer(plugin, other);
            }
        }

        if (player.isOnline()) {
            GameMode targetGm = (s.previousGameMode != null && s.previousGameMode != GameMode.SPECTATOR)
                ? s.previousGameMode
                : GameMode.ADVENTURE;
            player.setGameMode(targetGm);
            player.setAllowFlight(s.previousAllowFlight);
            player.setFlying(s.previousFlying && s.previousAllowFlight);
            if (restorePosition && s.returnLocation != null && s.returnLocation.getWorld() != null) {
                player.teleport(s.returnLocation);
            }
        }
    }

    public void refreshAllOpenStages(boolean includeActors) {
        for (Map.Entry<UUID, StageSession> entry : sessions.entrySet()) {
            Player viewer = Bukkit.getPlayer(entry.getKey());
            StageSession s = entry.getValue();
            if (viewer == null || !viewer.isOnline() || s == null) {
                continue;
            }
            if (includeActors && s.stageReference != null) {
                despawnActorProps(s);
                spawnStageMannequins(viewer, s.stageReference, s);
            }
            refreshPanels(viewer, s);
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

    public void hideActivePropsFromJoiner(Player joiner) {
        for (Map.Entry<UUID, StageSession> entry : sessions.entrySet()) {
            if (entry.getKey().equals(joiner.getUniqueId())) {
                continue;
            }
            StageSession s = entry.getValue();
            for (Entity prop : s.stageProps) {
                if (prop != null && prop.isValid()) {
                    joiner.hideEntity(plugin, prop);
                }
            }
            for (Entity prop : s.actorProps) {
                if (prop != null && prop.isValid()) {
                    joiner.hideEntity(plugin, prop);
                }
            }
            Player stageViewer = Bukkit.getPlayer(entry.getKey());
            if (stageViewer != null) {
                joiner.hidePlayer(plugin, stageViewer);
                stageViewer.hidePlayer(plugin, joiner);
            }
        }
    }

    private void despawnActorProps(StageSession s) {
        for (Entity e : s.actorProps) {
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
        s.actorProps.clear();
    }

    private void despawnSessionEntities(StageSession s) {
        despawnActorProps(s);
        for (Entity e : s.stageProps) {
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
        s.stageProps.clear();
    }

    /**
     * Spawns up to 4 live 3D Mannequin player statues in the Waiting-Room alcoves (or custom /bb 3d setslot 1..4 spots):
     * Slot 0 = Viewer (with 👑 leader crown + equipped square basketball!),
     * Slots 1..3 = Party / Live Queued Players / Online Friends / Top Legends / All-Stars!
     */
    private void spawnStageMannequins(Player viewer, Location stage, StageSession s) {
        World w = stage.getWorld();
        if (w == null) {
            return;
        }

        record StageActor(
            UUID uuid,
            String name,
            ResolvableProfile resolvedSkin,
            boolean isLeader,
            String roleSubtitle
        ) {}
        List<StageActor> actors = new ArrayList<>();
        GameModeType viewerQueueMode = plugin.gameManager().getQueuedMode(viewer.getUniqueId());

        // 1. Viewer is always heroic foreground-left star on stage (with 👑 crown after name, exact 202328.png!)
        actors.add(new StageActor(
            viewer.getUniqueId(),
            viewer.getName(),
            buildPlayerResolvableProfile(viewer.getPlayerProfile(), viewer.getName()),
            true,
            viewerQueueMode != null
                ? "<#52FFB8>● En File " + viewerQueueMode.id().toUpperCase() + "</#52FFB8>"
                : "<gold>👑 Chef de Groupe</gold>"
        ));

        // 2. Online party members
        for (Player mate : plugin.socialManager().onlinePartyMembers(viewer.getUniqueId())) {
            if (!mate.getUniqueId().equals(viewer.getUniqueId()) && actors.size() < 4) {
                actors.add(new StageActor(
                    mate.getUniqueId(),
                    mate.getName(),
                    buildPlayerResolvableProfile(mate.getPlayerProfile(), mate.getName()),
                    false,
                    "<green>● Membre du Groupe</green>"
                ));
            }
        }

        // 3. Live players waiting in the same Matchmaking Queue (exact 202328.png waiting-room stage!)
        for (Player queued : plugin.gameManager().queuedOnlinePlayers(viewerQueueMode)) {
            if (actors.size() >= 4) {
                break;
            }
            if (actors.stream().noneMatch(a -> a.uuid().equals(queued.getUniqueId()))) {
                GameModeType qm = plugin.gameManager().getQueuedMode(queued.getUniqueId());
                actors.add(new StageActor(
                    queued.getUniqueId(),
                    queued.getName(),
                    buildPlayerResolvableProfile(queued.getPlayerProfile(), queued.getName()),
                    false,
                    "<#52FFB8>● En Attente " + (qm != null ? qm.id().toUpperCase() : "") + "</#52FFB8>"
                ));
            }
        }

        // 4. Online friends
        fr.bosketball.model.PlayerProfile viewerProf = plugin.statsManager().getOrCreate(viewer);
        for (UUID fId : viewerProf.friends()) {
            if (actors.size() >= 4) {
                break;
            }
            Player friend = Bukkit.getPlayer(fId);
            if (friend != null && actors.stream().noneMatch(a -> a.uuid().equals(fId))) {
                actors.add(new StageActor(
                    fId,
                    friend.getName(),
                    buildPlayerResolvableProfile(friend.getPlayerProfile(), friend.getName()),
                    false,
                    "<aqua>★ Ami en ligne</aqua>"
                ));
            }
        }

        // 5. Fill remaining alcoves with Top ELO Legends
        for (fr.bosketball.model.PlayerProfile top : plugin.statsManager().topBy(StatsManager.LeaderboardCategory.ELO, 6)) {
            if (actors.size() >= 4) {
                break;
            }
            if (actors.stream().anyMatch(a -> a.uuid().equals(top.uuid()))) {
                continue;
            }
            Player onlineTop = Bukkit.getPlayer(top.uuid());
            PlayerProfile skinProf = onlineTop != null ? onlineTop.getPlayerProfile() : null;
            actors.add(new StageActor(
                top.uuid(),
                top.lastKnownName(),
                buildPlayerResolvableProfile(skinProf, top.lastKnownName()),
                false,
                "<#FFD43B>🏆 Légende #" + (actors.size()) + "</#FFD43B>"
            ));
        }

        // 6. Fill any remaining alcoves with Custom-Skinned All-Star Champions (exact 4-player V-formation of 202328.png!)
        String[][] showcaseStars = {
            {"Refortorr",    "<#FF5C4D>🔥 Champion Ignis #1</#FF5C4D>", "star_ignis"},
            {"A_Koop",       "<#38BDF8>❄ Meneur All-Star</#38BDF8>",    "star_frost"},
            {"DiAM0ndD0g15", "<#FFD43B>⭐ MVP Saison 26.2</#FFD43B>",   "star_mvp"}
        };
        int starIdx = 0;
        while (actors.size() < 4 && starIdx < showcaseStars.length) {
            String sName = showcaseStars[starIdx][0];
            String sRole = showcaseStars[starIdx][1];
            String sTex = showcaseStars[starIdx][2];
            starIdx++;
            if (actors.stream().anyMatch(a -> a.name().equalsIgnoreCase(sName))) {
                continue;
            }
            UUID syntheticId = UUID.nameUUIDFromBytes(("BosketShowcase:" + sName).getBytes());
            ResolvableProfile customChampSkin = buildCustomPatchedSkin(sName, sTex);
            actors.add(new StageActor(
                syntheticId,
                sName,
                customChampSkin,
                false,
                sRole
            ));
        }

        // Exact 4-Alcove Heroic V-Formation matching Capture_decran_2026-11-01_202328.png!
        // In camera-relative coordinates (screenLeft, yDropFromCam, forwardDistFromCam, yawDeltaFromCam):
        double[][] camRelSlots = {
            { 3.35, -0.35, 5.90, -162.0}, // Alcove 1 (Left Foreground: Viewer with 👑 + 3D Square Ball!)
            { 1.20, -0.35, 6.90, -174.0}, // Alcove 2 (Center-Left: Refortorr / Party #2)
            {-1.20, -0.35, 6.90,  174.0}, // Alcove 3 (Center-Right: A_Koop / Party #3)
            {-3.35, -0.35, 5.70,  160.0}  // Alcove 4 (Right Foreground: DiAM0ndD0g15 / Party #4)
        };
        double[][] builtInSlots = {
            {-3.35, 0.0, 2.25, -162.0},
            {-1.20, 0.0, 3.25, -174.0},
            { 1.20, 0.0, 3.25,  174.0},
            { 3.35, 0.0, 2.05,  160.0}
        };

        boolean usingCustomRoom = hasCustomStage();
        double camYawRad = Math.toRadians(s.cameraAnchor.getYaw());
        double fwdX = -Math.sin(camYawRad);
        double fwdZ =  Math.cos(camYawRad);
        double leftX = Math.cos(camYawRad);
        double leftZ = Math.sin(camYawRad);

        for (int i = 0; i < Math.min(actors.size(), 4); i++) {
            StageActor actor = actors.get(i);
            Location loc;
            if (usingCustomRoom && customSlots[i] != null && customSlots[i].getWorld() != null) {
                loc = customSlots[i].clone();
            } else if (usingCustomRoom) {
                double[] rel = camRelSlots[i];
                loc = new Location(
                    w,
                    s.cameraAnchor.getX() + leftX * rel[0] + fwdX * rel[2],
                    s.cameraAnchor.getY() + rel[1],
                    s.cameraAnchor.getZ() + leftZ * rel[0] + fwdZ * rel[2],
                    wrapDeg(s.cameraAnchor.getYaw() + (float) rel[3]),
                    0.0f
                );
            } else {
                double[] pos = builtInSlots[i];
                loc = stage.clone().add(pos[0], pos[1], pos[2]);
                loc.setYaw((float) pos[3]);
                loc.setPitch(0.0f);
            }

            fr.bosketball.model.PlayerProfile statsProf = plugin.statsManager().getProfile(actor.uuid());
            BallSkin ballSkin = statsProf != null ? statsProf.selectedBallSkin() : BallSkin.CLASSIC;

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
                man.customName(buildMannequinNameplate(actor.uuid(), actor.name(), actor.isLeader()));
                man.setCustomNameVisible(true);
                man.setDescription(Msg.parse(actor.roleSubtitle()));
                if (man.getEquipment() != null && actor.isLeader()) {
                    man.getEquipment().setItem(EquipmentSlot.HAND, ballSkin.createMannequinHeldBallItem());
                }
            });
            hideFromOthers(viewer, m);
            s.actorProps.add(m);

            // Glowing Floor Aura Ring flat on the floor under each stage mannequin!
            String ringKey = actor.isLeader() ? "ring_gold" : (i % 2 == 1 ? "ring_frost" : "ring_ignis");
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
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setItemStack(ringStack);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(),
                    new Vector3f(1.42f, 1.0f, 1.42f),
                    new Quaternionf()
                ));
            });
            hideFromOthers(viewer, ring);
            s.actorProps.add(ring);

            // Showcase 3D Square Basketball floating at leader mannequin's palm (rotated along statue yaw!)
            if (actor.isLeader()) {
                double mYawRad = Math.toRadians(loc.getYaw());
                double mFwdX = -Math.sin(mYawRad);
                double mFwdZ =  Math.cos(mYawRad);
                double mRightX = -Math.cos(mYawRad);
                double mRightZ = -Math.sin(mYawRad);
                Location ballLoc = loc.clone().add(mRightX * 0.34 + mFwdX * 0.32, 1.02, mRightZ * 0.34 + mFwdZ * 0.32);
                ItemDisplay propBall = loc.getWorld().spawn(ballLoc, ItemDisplay.class, d -> {
                    d.setPersistent(false);
                    d.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
                    d.setBillboard(Display.Billboard.FIXED);
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    d.setItemStack(ballSkin.createItemStack());
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new Quaternionf(new AxisAngle4f((float) Math.toRadians(22.0), 0.35f, 0.9f, 0.2f)),
                        new Vector3f(0.46f, 0.46f, 0.46f),
                        new Quaternionf()
                    ));
                });
                hideFromOthers(viewer, propBall);
                s.actorProps.add(propBall);
            }
        }
    }

    public static ResolvableProfile buildCustomPatchedSkin(String fallbackName, String skinTextureId) {
        try {
            return ResolvableProfile.resolvableProfile()
                .name(fallbackName)
                .skinPatch(sp -> sp.body(Key.key("bosketball", "entity/player/wide/" + skinTextureId)))
                .build();
        } catch (LinkageError | RuntimeException ignored) {
            return ResolvableProfile.resolvableProfile().name(fallbackName).build();
        }
    }

    private static ResolvableProfile buildPlayerResolvableProfile(PlayerProfile bukkitProfile, String fallbackName) {
        if (bukkitProfile != null && !bukkitProfile.getProperties().isEmpty()) {
            return ResolvableProfile.resolvableProfile(bukkitProfile);
        }
        if (fallbackName != null && !fallbackName.isBlank()) {
            return ResolvableProfile.resolvableProfile().name(fallbackName).build();
        }
        return bukkitProfile != null ? ResolvableProfile.resolvableProfile(bukkitProfile) : Mannequin.defaultProfile();
    }

    private Component buildMannequinNameplate(UUID uuid, String name, boolean leaderCrown) {
        TextComponent.Builder b = Component.text();
        StatsManager.LeaderboardBadges badges = plugin.statsManager().getBadges(uuid);
        if (badges.winsPlace() == 1) {
            b.append(Glyphs.topBadgePill(Glyphs.TopBadgeKind.WINS, 1)).append(Component.text(" "));
        }
        fr.bosketball.model.PlayerProfile prof = plugin.statsManager().getProfile(uuid);
        if (prof != null && prof.selectedTag() != null) {
            b.append(prof.selectedTag().pillComponent()).append(Component.text(" "));
        }
        b.append(Component.text(name, Style.style(TextColor.color(0xFFF4D2), TextDecoration.BOLD)));
        if (leaderCrown) {
            b.append(Component.text(" 👑", TextColor.color(0xFFD43B)));
        }
        return b.asComponent();
    }

    private Location screenOffsetLocation(Location cam, double screenLeftOffset, double screenUpOffset, double zDist) {
        double rad = Math.toRadians(cam.getYaw());
        double fwdX = -Math.sin(rad);
        double fwdZ =  Math.cos(rad);
        double leftX = Math.cos(rad);
        double leftZ = Math.sin(rad);
        double eyeY = cam.getY() + 1.62;
        return new Location(
            cam.getWorld(),
            cam.getX() + leftX * screenLeftOffset + fwdX * zDist,
            eyeY + screenUpOffset,
            cam.getZ() + leftZ * screenLeftOffset + fwdZ * zDist
        );
    }

    private void spawnHudPanels(Player viewer, StageSession s) {
        Location cam = s.cameraAnchor;
        float panelYaw = wrapDeg(cam.getYaw() + 180.0f);
        double zDist = 3.10;

        // Top bar across ceiling edge (dark sleek strip like image-6.png)
        s.topNavDisplay = spawnPanel(viewer, screenOffsetLocation(cam, 0.0, 1.20, zDist),
            panelYaw, 520, 0.43f, Color.fromARGB(210, 10, 14, 22), TextDisplay.TextAlignment.CENTER);

        // Top-Center Live Status Pill right below top bar (exact "• QUEUED 2V2 0:01" pill from image-6.png!)
        s.topStatusDisplay = spawnPanel(viewer, screenOffsetLocation(cam, 0.0, 0.99, zDist),
            panelYaw, 260, 0.36f, Color.fromARGB(216, 12, 18, 28), TextDisplay.TextAlignment.CENTER);

        // Top-Left menu rows with TRANSPARENT background so slanted mint-green bar floats cleanly over stage!
        s.leftMenuDisplay = spawnPanel(viewer, screenOffsetLocation(cam, 1.58, 0.24, zDist),
            panelYaw, 260, 0.49f, Color.fromARGB(0, 0, 0, 0), TextDisplay.TextAlignment.LEFT);

        // Lower-Left Patch Notes + Indigo Discord/Feedback Pills directly below menu (leaves all 4 mannequins 100% open!)
        s.patchNotesDisplay = spawnPanel(viewer, screenOffsetLocation(cam, 1.56, -0.56, zDist),
            panelYaw, 250, 0.36f, Color.fromARGB(204, 12, 16, 28), TextDisplay.TextAlignment.LEFT);

        // Bottom-Left compact 4-line vertical key legend (exact image-6.png bottom-left layout!)
        s.bottomKeysDisplay = spawnPanel(viewer, screenOffsetLocation(cam, 1.62, -1.12, zDist),
            panelYaw, 200, 0.34f, Color.fromARGB(0, 0, 0, 0), TextDisplay.TextAlignment.LEFT);

        // Bottom-Right Selected Mode / Stats card (exact image-6.png bottom-right card!)
        s.detailCardDisplay = spawnPanel(viewer, screenOffsetLocation(cam, -1.52, -0.88, zDist),
            panelYaw, 270, 0.40f, Color.fromARGB(220, 12, 16, 28), TextDisplay.TextAlignment.LEFT);

        s.stageProps.add(s.topNavDisplay);
        s.stageProps.add(s.topStatusDisplay);
        s.stageProps.add(s.leftMenuDisplay);
        s.stageProps.add(s.patchNotesDisplay);
        s.stageProps.add(s.bottomKeysDisplay);
        s.stageProps.add(s.detailCardDisplay);
    }

    private TextDisplay spawnPanel(
        Player viewer,
        Location loc,
        float panelYaw,
        int lineWidth,
        float scale,
        Color bg,
        TextDisplay.TextAlignment alignment
    ) {
        Location flatScreenLoc = loc.clone();
        flatScreenLoc.setYaw(panelYaw);
        flatScreenLoc.setPitch(0.0f);
        TextDisplay td = flatScreenLoc.getWorld().spawn(flatScreenLoc, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            // Orthographic-flat screen orientation (parallel to camera near plane — zero perspective skew!)
            d.setBillboard(Display.Billboard.FIXED);
            d.setAlignment(alignment);
            d.setLineWidth(lineWidth);
            d.setShadowed(true);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setBackgroundColor(bg);
            d.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                new Quaternionf()
            ));
        });
        hideFromOthers(viewer, td);
        return td;
    }

    private void hideFromOthers(Player viewer, Entity entity) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(viewer.getUniqueId())) {
                other.hideEntity(plugin, entity);
            }
        }
    }

    /**
     * Handles Paper 26.2 WASD + Jump + Sneak input events for crisp keyboard navigation!
     */
    public void handleInput(Player player, Input input) {
        StageSession s = sessions.get(player.getUniqueId());
        if (s == null) {
            return;
        }
        // Grace period of 200ms on open so residual jump/sneak doesn't immediately trigger
        if (System.currentTimeMillis() - s.openedAtMillis < 200L) {
            return;
        }

        if (input.isSneak()) {
            Msg.playPersonalPackSound(player, "ui.click", 0.75f, 0.85f);
            close(player, true);
            return;
        }

        boolean changed = false;
        int rowCount = Math.max(1, currentRowLabels(player, s).size());

        if (input.isForward() && !s.lastForward) {
            s.selectedRow = (s.selectedRow - 1 + rowCount) % rowCount;
            changed = true;
        }
        if (input.isBackward() && !s.lastBackward) {
            s.selectedRow = (s.selectedRow + 1) % rowCount;
            changed = true;
        }
        if (input.isLeft() && !s.lastLeft) {
            TopTab[] tabs = TopTab.values();
            s.activeTab = tabs[(s.activeTab.ordinal() - 1 + tabs.length) % tabs.length];
            s.selectedRow = 0;
            changed = true;
        }
        if (input.isRight() && !s.lastRight) {
            TopTab[] tabs = TopTab.values();
            s.activeTab = tabs[(s.activeTab.ordinal() + 1) % tabs.length];
            s.selectedRow = 0;
            changed = true;
        }
        if (input.isJump() && !s.lastJump) {
            activateCurrentSelection(player, s);
            s.lastJump = true;
            return;
        }

        s.lastForward = input.isForward();
        s.lastBackward = input.isBackward();
        s.lastLeft = input.isLeft();
        s.lastRight = input.isRight();
        s.lastJump = input.isJump();

        if (changed) {
            Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.25f);
            refreshPanels(player, s);
        }
    }

    public void onClickConfirm(Player player, boolean rightClickNextTab) {
        StageSession s = sessions.get(player.getUniqueId());
        if (s == null) {
            return;
        }
        if (rightClickNextTab) {
            TopTab[] tabs = TopTab.values();
            s.activeTab = tabs[(s.activeTab.ordinal() + 1) % tabs.length];
            s.selectedRow = 0;
            Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.15f);
            refreshPanels(player, s);
        } else {
            activateCurrentSelection(player, s);
        }
    }

    public boolean handleSpectateStopAttempt(Player player) {
        StageSession s = sessions.get(player.getUniqueId());
        if (s == null) {
            return false;
        }
        // Ignore involuntary packet settle during the first 350ms; afterwards [SHIFT] cleanly exits the 3D stage!
        if (System.currentTimeMillis() - s.openedAtMillis < 350L) {
            return true;
        }
        Bukkit.getScheduler().runTask(plugin, () -> close(player, true));
        return true;
    }

    public void scrollSelection(Player player, int step) {
        StageSession s = sessions.get(player.getUniqueId());
        if (s == null || step == 0) {
            return;
        }
        int rowCount = currentRowLabels(player, s).size();
        if (rowCount <= 0) {
            return;
        }
        s.selectedRow = Math.floorMod(s.selectedRow + step, rowCount);
        Msg.playPersonalPackSound(player, "ui.click", 0.65f, step < 0 ? 1.32f : 1.08f);
        refreshPanels(player, s);
    }

    public void confirmSelection(Player player) {
        StageSession s = sessions.get(player.getUniqueId());
        if (s == null || System.currentTimeMillis() - s.openedAtMillis < 250L) {
            return;
        }
        activateCurrentSelection(player, s);
    }

    public void tickAll() {
        tickCounter++;
        boolean refreshSecond = (tickCounter % 20 == 0);
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
            // Keep viewer smoothly locked onto the eye-level ItemDisplay cameraRig!
            if (s.cameraRig != null && s.cameraRig.isValid() && p.getSpectatorTarget() != s.cameraRig) {
                if (p.getGameMode() != GameMode.SPECTATOR) {
                    p.setGameMode(GameMode.SPECTATOR);
                }
                p.setSpectatorTarget(s.cameraRig);
            }
            // Subtle breathing head-look & periodic palm dribble swing on the 4 Stage Mannequins!
            if (tickCounter % 35 == 0) {
                int idx = 0;
                for (Entity e : s.actorProps) {
                    if (e instanceof Mannequin m && m.isValid()) {
                        float pitchWave = (float) (Math.sin((tickCounter + idx * 17) * 0.14) * 3.5);
                        m.setRotation(m.getLocation().getYaw(), pitchWave);
                        if (idx == 0 && tickCounter % 70 == 0) {
                            m.swingMainHand();
                        }
                        idx++;
                    }
                }
            }
            if (refreshSecond) {
                updateTopStatusPill(p, s);
            }
        }
    }

    private void updateTopStatusPill(Player viewer, StageSession s) {
        if (s.topStatusDisplay == null || !s.topStatusDisplay.isValid()) {
            return;
        }
        GameModeType qMode = plugin.gameManager().getQueuedMode(viewer.getUniqueId());
        if (qMode != null) {
            long qSec = plugin.gameManager().getQueueElapsedSeconds(viewer.getUniqueId());
            String qTimer = String.format("%d:%02d", qSec / 60L, qSec % 60L);
            // Exact top-center "• QUEUED <MODE> 0:01" live badge matching 202328.png!
            s.topStatusDisplay.text(Msg.parse(
                " <#52FFB8><bold>●</bold></#52FFB8> <white><bold>QUEUED " + qMode.id().toUpperCase()
                    + "</bold></white>   <#A8FFB8><bold>" + qTimer + "</bold></#A8FFB8> "
            ));
        } else {
            long secOpen = Math.max(0L, (System.currentTimeMillis() - s.openedAtMillis) / 1000L);
            String timer = String.format("%d:%02d", secOpen / 60L, secOpen % 60L);
            s.topStatusDisplay.text(Msg.parse(
                " <#52FFB8><bold>●</bold></#52FFB8> <white><bold>SALLE D'ATTENTE 3D</bold></white> <gray>•</gray> <#FFD43B>"
                    + Bukkit.getOnlinePlayers().size() + " EN LIGNE</#FFD43B> <gray>" + timer + "</gray> "
            ));
        }
    }

    private static float wrapDeg(float deg) {
        float d = deg % 360.0f;
        if (d >= 180.0f) d -= 360.0f;
        if (d < -180.0f) d += 360.0f;
        return d;
    }

    private List<String> currentRowLabels(Player viewer, StageSession s) {
        fr.bosketball.model.PlayerProfile prof = plugin.statsManager().getOrCreate(viewer);
        GameModeType qMode = plugin.gameManager().getQueuedMode(viewer.getUniqueId());
        return switch (s.activeTab) {
            case PLAY -> {
                List<String> list = new ArrayList<>();
                list.add((qMode == GameModeType.DUEL_1V1 ? "⟳ EN FILE: " : "") + "CLASSÉ 1V1");
                list.add((qMode == GameModeType.STREET_2V2 ? "⟳ EN FILE: " : "") + "MATCH RAPIDE 2V2");
                list.add((qMode == GameModeType.PRO_3V3 ? "⟳ EN FILE: " : "") + "PRO 3V3");
                list.add((qMode == GameModeType.FULL_5V5 ? "⟳ EN FILE: " : "") + "LIGUE 5V5");
                if (qMode != null) {
                    list.add("▶ LANCER MAINTENANT");
                }
                list.add("ENTRAÎNEMENT + BOT");
                list.add("CONCOURS 3-POINTS");
                list.add("MENU COFFRE (/BB)");
                yield list;
            }
            case PROFILE -> List.of(
                "VOIR MES STATS CHAT",
                "COFFRE 54 TAGS (/BB)",
                "VESTIAIRE 8 BALLONS",
                "TAB CENTRAL 3D ([F])"
            );
            case LEADERBOARDS -> List.of(
                "TOP ELO RATING",
                "TOP POINTS",
                "TOP VICTOIRES",
                "TOP PASSES DÉC.",
                "TOP SLAM DUNKS",
                "TOP TROPHÉES MVP"
            );
            case HISTORY -> {
                if (prof.matchHistory().isEmpty()) {
                    yield List.of("AUCUN MATCH JOUÉ", "LANCER ENTRAÎNEMENT");
                }
                List<String> rows = new ArrayList<>();
                int max = Math.min(6, prof.matchHistory().size());
                for (int i = 0; i < max; i++) {
                    MatchHistoryEntry h = prof.matchHistory().get(i);
                    rows.add((h.won() ? "✔ " : "✖ ") + h.modeKey() + "  " + h.ignisScore() + "-" + h.frostScore());
                }
                yield rows;
            }
            case FRIENDS -> {
                List<String> rows = new ArrayList<>();
                rows.add("QUITTER LE GROUPE");
                for (Player on : Bukkit.getOnlinePlayers()) {
                    if (!on.getUniqueId().equals(viewer.getUniqueId()) && rows.size() < 6) {
                        rows.add("INVITER " + on.getName().toUpperCase());
                    }
                }
                if (rows.size() == 1) {
                    rows.add("INVITER VIA /BB PARTY");
                }
                yield rows;
            }
            case TAGS -> List.of(
                "TAG SUIVANT ▸",
                "RETIRER MES TAGS",
                "BALLON SUIVANT ▸",
                "COFFRE 54 TAGS (/BB)"
            );
            case SETTINGS -> List.of(
                "ANIMATIONS JOUEUR 3D",
                "CERCLES D'ÉQUIPE SOL",
                "SONS CUSTOM PACK",
                "CAMÉRA ZOOM PANIER",
                "INVITATIONS GROUPE"
            );
        };
    }

    private void activateCurrentSelection(Player player, StageSession s) {
        fr.bosketball.model.PlayerProfile prof = plugin.statsManager().getOrCreate(player);
        Msg.playPersonalPackSound(player, "ui.click", 0.9f, 1.32f);

        switch (s.activeTab) {
            case PLAY -> {
                int row = s.selectedRow;
                GameModeType qMode = plugin.gameManager().getQueuedMode(player.getUniqueId());
                if (row == 0) {
                    plugin.gameManager().joinQueue(player, GameModeType.DUEL_1V1);
                } else if (row == 1) {
                    plugin.gameManager().joinQueue(player, GameModeType.STREET_2V2);
                } else if (row == 2) {
                    plugin.gameManager().joinQueue(player, GameModeType.PRO_3V3);
                } else if (row == 3) {
                    plugin.gameManager().joinQueue(player, GameModeType.FULL_5V5);
                } else if (qMode != null && row == 4) {
                    // Instant force-launch for solo/partial testing right from the 3D Waiting Room!
                    plugin.gameManager().tryLaunchReadyMatch(qMode, true);
                } else {
                    int shifted = qMode != null ? row - 1 : row;
                    if (shifted == 4) {
                        plugin.gameManager().startPractice(player);
                    } else if (shifted == 5) {
                        plugin.gameManager().joinQueue(player, GameModeType.THREE_POINT_CONTEST);
                    } else {
                        close(player, true);
                        plugin.guiMenu().openMainHub(player);
                    }
                }
            }
            case PROFILE -> {
                int row = s.selectedRow;
                close(player, true);
                switch (row) {
                    case 0 -> player.performCommand("bb stats");
                    case 1 -> plugin.guiMenu().openTagsVault(player);
                    case 2 -> plugin.guiMenu().openCosmetics(player);
                    default -> plugin.centerTabManager().toggle(player);
                }
            }
            case LEADERBOARDS -> {
                StatsManager.LeaderboardCategory cat = LB_CATEGORIES[Math.min(s.selectedRow, LB_CATEGORIES.length - 1)];
                close(player, true);
                plugin.guiMenu().openLeaderboards(player, cat);
            }
            case HISTORY -> {
                if (prof.matchHistory().isEmpty() && s.selectedRow == 1) {
                    close(player, true);
                    plugin.gameManager().startPractice(player);
                } else {
                    close(player, true);
                    plugin.guiMenu().openMatchHistory(player);
                }
            }
            case FRIENDS -> {
                if (s.selectedRow == 0) {
                    plugin.socialManager().leaveParty(player);
                } else {
                    List<Player> candidates = Bukkit.getOnlinePlayers().stream()
                        .<Player>map(p -> p)
                        .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                        .limit(5)
                        .toList();
                    int idx = s.selectedRow - 1;
                    if (idx >= 0 && idx < candidates.size()) {
                        Player target = candidates.get(idx);
                        plugin.socialManager().inviteToParty(player, target);
                        plugin.socialManager().sendOrAcceptFriendRequest(player, target);
                    }
                }
                refreshPanels(player, s);
            }
            case TAGS -> {
                if (s.selectedRow == 0) {
                    ChatTag[] all = ChatTag.values();
                    int next = prof.selectedTag() == null ? 0 : (prof.selectedTag().ordinal() + 1) % all.length;
                    prof.setSelectedTag(all[next]);
                    plugin.statsManager().saveProfileAsync(prof);
                } else if (s.selectedRow == 1) {
                    prof.setSelectedTag(null);
                    plugin.statsManager().saveProfileAsync(prof);
                } else if (s.selectedRow == 2) {
                    BallSkin[] skins = BallSkin.values();
                    int next = (prof.selectedBallSkin().ordinal() + 1) % skins.length;
                    prof.setSelectedBallSkin(skins[next]);
                    plugin.statsManager().saveProfileAsync(prof);
                } else {
                    close(player, true);
                    plugin.guiMenu().openTagsVault(player);
                    return;
                }
                refreshPanels(player, s);
            }
            case SETTINGS -> {
                PlayerSettings st = prof.settings();
                switch (s.selectedRow) {
                    case 0 -> st.setCustomAnimations(!st.customAnimations());
                    case 1 -> st.setFloorAuraRings(!st.floorAuraRings());
                    case 2 -> st.setCustomSounds(!st.customSounds());
                    case 3 -> st.setCinematicScoreCam(!st.cinematicScoreCam());
                    case 4 -> st.setPartyInvites(!st.partyInvites());
                }
                plugin.statsManager().saveProfileAsync(prof);
                refreshPanels(player, s);
            }
        }
    }

    private void refreshPanels(Player viewer, StageSession s) {
        if (s.topNavDisplay == null || !s.topNavDisplay.isValid()) {
            return;
        }
        fr.bosketball.model.PlayerProfile prof = plugin.statsManager().getOrCreate(viewer);

        // 1. Top Navigation Bar + Group X/4 pill (image-6.png!)
        TextComponent.Builder nav = Component.text();
        for (TopTab tab : TopTab.values()) {
            boolean active = tab == s.activeTab;
            if (active) {
                nav.append(Msg.parse("<#52FFB8><bold>[" + tab.title() + "]</bold></#52FFB8>   "));
            } else {
                nav.append(Msg.parse("<gray>" + tab.title() + "</gray>   "));
            }
        }
        int groupSize = plugin.socialManager().partySizeOrOne(viewer.getUniqueId());
        nav.append(Msg.parse("  <#52FFB8><bold>👥 Group " + groupSize + "/" + SocialPartyManager.MAX_PARTY_SIZE + "</bold></#52FFB8>"));
        s.topNavDisplay.text(nav.asComponent());

        updateTopStatusPill(viewer, s);

        // 2. Left Selection Menu with slanted mint-green highlight bar (\uE130, exact image-6.png!)
        List<String> rows = currentRowLabels(viewer, s);
        if (s.selectedRow >= rows.size()) {
            s.selectedRow = 0;
        }
        TextComponent.Builder left = Component.text();
        for (int i = 0; i < rows.size(); i++) {
            left.append(Glyphs.composeMenu3dRow(rows.get(i), i == s.selectedRow)).append(Component.newline());
        }
        s.leftMenuDisplay.text(left.asComponent());

        // 3. Lower-Left Patch Notes Card + TWO Side-by-Side Indigo Pills (exact image-6.png!)
        TextComponent.Builder patch = Component.text();
        patch.append(Msg.parse(
            "<#52FFB8><bold>PATCH NOTES</bold></#52FFB8>     <gray>Espace: lire</gray>\n"
                + "<white>Chaos Cubed Update - 1 Nov 2026</white>\n"
        ));
        patch.append(Glyphs.composeMenu3dIndigoPill("Discord"))
            .append(Component.text(" "))
            .append(Glyphs.composeMenu3dIndigoPill("Suggestions"));
        s.patchNotesDisplay.text(patch.asComponent());

        // 4. Bottom-Left 4-Line Stacked Controls Legend (exact image-6.png!)
        s.bottomKeysDisplay.text(Msg.parse(
            "<white><bold>Z/S</bold></white>  <gray>naviguer</gray>\n"
                + "<white><bold>Q/D</bold></white>  <gray>onglet</gray>\n"
                + "<#FFD43B><bold>Espace</bold></#FFD43B>  <gray>valider</gray>\n"
                + "<#FF6B6B><bold>Shift</bold></#FF6B6B>  <gray>quitter</gray>"
        ));

        // 5. Bottom-Right Dynamic Detail Card
        s.detailCardDisplay.text(buildDetailCard(viewer, prof, s));
    }

    private Component buildDetailCard(Player viewer, fr.bosketball.model.PlayerProfile prof, StageSession s) {
        return switch (s.activeTab) {
            case PLAY -> {
                GameModeType qMode = plugin.gameManager().getQueuedMode(viewer.getUniqueId());
                if (qMode != null && s.selectedRow == 4) {
                    yield Msg.parse(
                        "<#52FFB8><bold>▶ LANCER MAINTENANT (" + qMode.id().toUpperCase() + ")</bold></#52FFB8>\n"
                            + "<white>Démarre immédiatement sur le terrain</white>\n"
                            + "<gray>avec les joueurs présents en file\n(idéal pour tester solo ou en petit comité !)</gray>\n"
                            + "<yellow>Appuyez sur <bold>ESPACE</bold> pour jouer</yellow>"
                    );
                }
                int mappedIdx = (qMode != null && s.selectedRow > 4) ? s.selectedRow - 1 : s.selectedRow;
                String[] modeTitles = {
                    "CLASSÉ 1V1", "STREET 2V2", "PRO 3V3", "LIGUE 5V5",
                    "ENTRAÎNEMENT", "CONCOURS 3-PTS", "MENU /BB"
                };
                String[] modeSub = {
                    "Duel en 21 pts • Gain d'ELO classé\nHorloge des 24s & tirs skill",
                    "2 contre 2 dynamique • Passes & Lobs\nIdéal en groupe (/bb party)",
                    "3 contre 3 demi-terrain ou complet\nJeu collectif, écrans & contres",
                    "5 contre 5 officiel sur grand parquet\n4 quart-temps + cérémonie MVP",
                    "Parquet libre + Horloge 24s active\nActivez le Défenseur IA Pro (Slot 5)",
                    "5 racks × 5 ballons dont Money Balls\n60 secondes pour battre le record !",
                    "Ouvre l'interface coffre Noir & Or\navec les 8 ballons & 54 tags glossy"
                };
                int idx = Math.min(mappedIdx, modeTitles.length - 1);
                String actionHint = (idx <= 3 && qMode != null && idx == qMode.ordinal())
                    ? "<#FF8A7A>Appuyez sur <bold>ESPACE</bold> pour annuler la file</#FF8A7A>"
                    : "<yellow>Appuyez sur <bold>ESPACE</bold> pour " + (idx <= 3 ? "rejoindre la file" : "lancer") + "</yellow>";
                yield Msg.parse(
                    "<white><bold>" + modeTitles[idx] + "</bold></white>\n"
                        + "<#52FFB8>1v1 à 5v5 • Saison Chaos Cubed</#52FFB8>\n"
                        + "<gray>" + modeSub[idx] + "</gray>\n"
                        + actionHint
                );
            }
            case PROFILE -> Msg.parse(
                "<#FFD43B><bold>PROFIL JOUEUR</bold></#FFD43B>\n"
                    + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                    + "<gray>Rang:</gray> <!shadow><white>" + prof.effectiveRank().pillGlyph() + "</white></!shadow>  <gold>" + prof.elo() + " ELO</gold>\n"
                    + "<gray>Bilan:</gray> <green>" + prof.wins() + "W</green> <gray>/</gray> <red>" + prof.losses() + "L</red> <gray>(" + String.format("%.0f%%", prof.winRatePercent()) + ")</gray>\n"
                    + "<gray>Points:</gray> <yellow>" + prof.points() + "</yellow>  <gray>•  Passes:</gray> <aqua>" + prof.assists() + "</aqua>\n"
                    + "<gray>Temps de jeu:</gray> <white>" + prof.formattedPlayTimeFr() + "</white>"
            );
            case LEADERBOARDS -> {
                StatsManager.LeaderboardCategory cat = LB_CATEGORIES[Math.min(s.selectedRow, LB_CATEGORIES.length - 1)];
                TextComponent.Builder b = Component.text();
                b.append(Msg.parse("<#FFD43B><bold>TOP 5 • " + cat.label() + "</bold></#FFD43B>\n"));
                List<fr.bosketball.model.PlayerProfile> top = plugin.statsManager().topBy(cat, 5);
                for (int i = 0; i < top.size(); i++) {
                    fr.bosketball.model.PlayerProfile p = top.get(i);
                    b.append(Component.text("#" + (i + 1) + " ", i == 0 ? TextColor.color(0xFFD43B) : NamedTextColor.GRAY));
                    b.append(Component.object(ObjectContents.playerHead(p.uuid()))).append(Component.text(" "));
                    b.append(Component.text(p.lastKnownName() + " ", NamedTextColor.WHITE));
                    b.append(Component.text(cat.extract(p) + "\n", TextColor.color(0x52FFB8)));
                }
                yield b.asComponent();
            }
            case HISTORY -> {
                if (prof.matchHistory().isEmpty()) {
                    yield Msg.parse("<gray>Jouez un match pour afficher\nvotre historique complet ici !</gray>");
                }
                MatchHistoryEntry h = prof.matchHistory().get(Math.min(s.selectedRow, prof.matchHistory().size() - 1));
                String res = h.won() ? "<#52FFB8><bold>VICTOIRE</bold></#52FFB8>" : "<#FF5C5C><bold>DÉFAITE</bold></#FF5C5C>";
                String delta = h.eloDelta() >= 0 ? "<green>+" + h.eloDelta() + "</green>" : "<red>" + h.eloDelta() + "</red>";
                yield Msg.parse(
                    res + " <gray>• Mode " + h.modeKey() + " (" + h.formattedDate() + ")</gray>\n"
                        + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                        + "<#FF5C4D>Ignis " + h.ignisScore() + "</#FF5C4D> <gray>—</gray> <#3DB8FF>" + h.frostScore() + " Frost</#3DB8FF>\n"
                        + "<white>" + h.points() + " PTS  •  " + h.assists() + " AST  •  " + h.rebounds() + " REB</white>\n"
                        + "<gray>Variation ELO:</gray> " + delta
                );
            }
            case FRIENDS -> {
                int onlineFriends = plugin.socialManager().countOnlineFriends(viewer.getUniqueId());
                int groupSize = plugin.socialManager().partySizeOrOne(viewer.getUniqueId());
                yield Msg.parse(
                    "<#52FFB8><bold>GROUPE & AMIS</bold></#52FFB8>\n"
                        + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                        + "<gray>Groupe actuel:</gray> <white>" + groupSize + "/4 joueurs</white>\n"
                        + "<gray>Amis connectés:</gray> <green>" + onlineFriends + "</green><gray>/" + prof.friends().size() + "</gray>\n"
                        + "<yellow>Appuyez sur ESPACE pour inviter !</yellow>"
                );
            }
            case TAGS -> {
                String tagStr = prof.selectedTag() != null
                    ? "<!shadow><white>" + prof.selectedTag().glyph() + "</white></!shadow> <yellow>" + prof.selectedTag().displayName() + "</yellow>"
                    : "<gray>Aucun</gray>";
                yield Msg.parse(
                    "<#FFD43B><bold>PERSONNALISATION</bold></#FFD43B>\n"
                        + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                        + "<gray>Tag Chat équipé:</gray> " + tagStr + "\n"
                        + "<gray>Ballon Carré 3D:</gray> " + prof.selectedBallSkin().displayNameMm() + "\n"
                        + "<aqua>ESPACE pour changer en direct !</aqua>"
                );
            }
            case SETTINGS -> {
                PlayerSettings st = prof.settings();
                yield Msg.parse(
                    "<#FFD43B><bold>ÉTAT DES OPTIONS</bold></#FFD43B>\n"
                        + stateLine("Animations 3D", st.customAnimations())
                        + stateLine("Cercles au sol", st.floorAuraRings())
                        + stateLine("Sons du pack", st.customSounds())
                        + stateLine("Caméra panier", st.cinematicScoreCam())
                        + stateLine("Invitations", st.partyInvites())
                );
            }
        };
    }

    private static String stateLine(String label, boolean on) {
        return "<gray>• " + label + ":</gray> " + (on ? "<#52FFB8>ACTIVÉ</#52FFB8>\n" : "<#FF6B6B>DÉSACTIVÉ</#FF6B6B>\n");
    }
}
