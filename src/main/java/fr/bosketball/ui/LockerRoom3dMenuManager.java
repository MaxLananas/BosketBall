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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Interactive 3D Locker Room Showcase with Mouse-Clickable On-Screen GUI Overlay:
 *   - Displays ONLY the player alone (centered pedestal when solo) or their online Party teammates (1..4).
 *   - Lowered eye-level camera framing the full player skins cleanly in the open transparent viewport.
 *   - 100% locked movement &amp; rotation while inside the Locker Room.
 *   - Direct mouse-clickable GUI buttons on the left rail, right rail, and bottom Esports Action Dock
 *     rendered via the custom transparent {@code shulker_box.png} resource pack container overlay!
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
        Location stageReference;
        long openedAtMillis;
        boolean guiOpened;
        boolean suppressAutoCloseOnInventorySwap;

        final List<Entity> stageProps = new ArrayList<>();
        final List<Entity> actorProps = new ArrayList<>();
    }

    private final BosketBallPlugin plugin;
    private final Map<UUID, StageSession> sessions = new ConcurrentHashMap<>();
    private Location stageOrigin;
    private boolean stageBuilt = false;
    private int tickCounter = 0;

    private Location customCamera;
    private final Location[] customSlots = new Location[4];

    public LockerRoom3dMenuManager(BosketBallPlugin plugin) {
        this.plugin = plugin;
        loadStageConfig();
    }

    public boolean isIn3dMenu(UUID playerId) {
        return playerId != null && sessions.containsKey(playerId);
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

        double baseX = primary != null ? primary.center().getBlockX() + 190 : 190;
        double baseY = Math.max(72, primary != null ? primary.center().getBlockY() + 12 : 84);
        double baseZ = primary != null ? primary.center().getBlockZ() + 190 : 190;
        int ox = (int) baseX;
        int oy = (int) baseY;
        int oz = (int) baseZ;

        for (int dx = -9; dx <= 9; dx++) {
            for (int dy = 0; dy <= 7; dy++) {
                for (int dz = -6; dz <= 7; dz++) {
                    Block b = world.getBlockAt(ox + dx, oy + dy, oz + dz);
                    boolean shell = (dx == -9 || dx == 9 || dy == 0 || dy == 7 || dz == -6 || dz == 7);
                    if (!shell) {
                        b.setType(Material.AIR, false);
                        continue;
                    }
                    if (dy == 0) {
                        b.setType(Material.STRIPPED_OAK_WOOD, false);
                    } else if (dy == 7) {
                        boolean light = (dz == 2 || dz == -1) && (dx == -4 || dx == 0 || dx == 4);
                        b.setType(light ? Material.OCHRE_FROGLIGHT : Material.SPRUCE_PLANKS, false);
                    } else {
                        b.setType(Material.DEEPSLATE_TILES, false);
                    }
                }
            }
        }

        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = 1; dy <= 6; dy++) {
                world.getBlockAt(ox + dx, oy + dy, oz + 6).setType(Material.POLISHED_DEEPSLATE, false);
            }
        }
        int[] pillarXs = {-8, -4, 4, 8};
        for (int px : pillarXs) {
            for (int dy = 1; dy <= 6; dy++) {
                world.getBlockAt(ox + px, oy + dy, oz + 5).setType(Material.DEEPSLATE_TILES, false);
            }
        }
        int[] lightXs = {-5, -2, 0, 2, 5};
        for (int cx : lightXs) {
            world.getBlockAt(ox + cx, oy + 5, oz + 5).setType(Material.OCHRE_FROGLIGHT, false);
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
            Msg.send(player, Msg.warn("Leave your current match (<yellow>/bb leave</yellow>) before entering the Locker Room."));
            return;
        }

        StageSession prev = sessions.get(player.getUniqueId());
        Location preservedReturn = (prev != null && prev.returnLocation != null)
            ? prev.returnLocation.clone()
            : player.getLocation().clone();
        GameMode preservedMode = (prev != null && prev.previousGameMode != null) ? prev.previousGameMode : player.getGameMode();
        boolean preservedAllowFlight = prev != null ? prev.previousAllowFlight : player.getAllowFlight();
        boolean preservedFlying = prev != null ? prev.previousFlying : player.isFlying();
        ItemStack[] preservedStorage = prev != null ? prev.savedStorageContents : deepClone(player.getInventory().getStorageContents());
        ItemStack[] preservedArmor = prev != null ? prev.savedArmorContents : deepClone(player.getInventory().getArmorContents());
        ItemStack preservedOffHand = prev != null ? prev.savedOffHand : cloneItem(player.getInventory().getItemInOffHand());
        int preservedSlot = prev != null ? prev.savedHeldSlot : player.getInventory().getHeldItemSlot();

        if (prev != null) {
            prev.suppressAutoCloseOnInventorySwap = true;
            close(player, false);
        }
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

        // Lowered natural eye/chest-level camera so player pedestals stand tall inside the open center viewport!
        Location eyeRigLoc;
        if (usingCustomRoom) {
            eyeRigLoc = customCamera.clone().add(0.0, 1.02, 0.0);
            eyeRigLoc.setPitch(Math.clamp(customCamera.getPitch(), -8.0f, 10.0f));
        } else {
            eyeRigLoc = stage.clone().add(0.0, 1.24, -3.55);
            eyeRigLoc.setYaw(0.0f);
            eyeRigLoc.setPitch(-1.5f);
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

        player.setFlying(false);
        player.setAllowFlight(false);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        player.teleport(eyeRigLoc);
        player.setGameMode(GameMode.SPECTATOR);
        player.setSpectatorTarget(s.cameraRig);

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(player.getUniqueId())) {
                other.hidePlayer(plugin, player);
                player.hidePlayer(plugin, other);
            }
        }

        spawnPartyStageMannequins(player, stage, s);
        sessions.put(player.getUniqueId(), s);

        // Open the transparent-viewport On-Screen Button GUI 1 tick after teleport so camera lock + mouse cursor engage 100% cleanly!
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            StageSession active = sessions.get(player.getUniqueId());
            if (active == s && player.isOnline()) {
                if (active.cameraRig != null && active.cameraRig.isValid()) {
                    player.setGameMode(GameMode.SPECTATOR);
                    player.setSpectatorTarget(active.cameraRig);
                }
                openInteractiveOverlayGui(player, active);
            }
        }, 1L);

        Msg.playPersonalPackSound(player, "ui.click", 0.85f, 1.18f);
    }

    /**
     * Populates &amp; opens the transparent {@code shulker_box.png} HUD overlay with direct mouse-clickable buttons!
     */
    private void openInteractiveOverlayGui(Player player, StageSession s) {
        s.suppressAutoCloseOnInventorySwap = true;
        try {
            LockerGuiHolder holder = new LockerGuiHolder(player.getUniqueId());
            Inventory top = Bukkit.createInventory(holder, InventoryType.SHULKER_BOX, Component.empty());
            holder.setInventory(top);

            populateOverlayButtons(player, top);
            player.openInventory(top);
            s.guiOpened = true;
        } finally {
            s.suppressAutoCloseOnInventorySwap = false;
        }
    }

    private void populateOverlayButtons(Player player, Inventory top) {
        top.clear();
        player.getInventory().clear();

        fr.bosketball.model.PlayerProfile prof = plugin.statsManager().getOrCreate(player);
        int partySize = plugin.socialManager().partySizeOrOne(player.getUniqueId());
        GameModeType queuedMode = plugin.gameManager().getQueuedMode(player.getUniqueId());

        // ── LEFT FLOATING QUICK-RAIL (Slots 0, 9, 18) ──
        top.setItem(0, GuiMenuManager.customIcon(
            "icon_friends",
            "<#4ADE80><bold>Party & Squad (" + partySize + "/4)</bold></#4ADE80>",
            List.of(
                "<gray>Only your online Party stands</gray>",
                "<gray>beside you in the Locker Room!</gray>",
                "",
                "<yellow>▸ Click to manage Party & Friends</yellow>"
            )
        ));
        top.setItem(9, GuiMenuManager.customIcon(
            "icon_podium",
            "<#FBBF24><bold>Leaderboards & Stats</bold></#FBBF24>",
            List.of(
                "<gray>ELO:</gray> <#FBBF24>" + prof.elo() + "</#FBBF24> <dark_gray>•</dark_gray> <gray>Wins:</gray> <#4ADE80>" + prof.wins() + "</#4ADE80>",
                "<gray>Points:</gray> <white>" + prof.points() + "</white> <dark_gray>•</dark_gray> <gray>Dunks:</gray> <#FB923C>" + prof.dunks() + "</#FB923C>",
                "",
                "<yellow>▸ Click to view server rankings</yellow>"
            )
        ));
        top.setItem(18, GuiMenuManager.customIcon(
            "icon_settings",
            "<#94A3B8><bold>Match Settings</bold></#94A3B8>",
            List.of(
                "<gray>Goal Replay Cam, trajectory HUD</gray>",
                "<gray>and personal audio preferences.</gray>",
                "",
                "<yellow>▸ Click to configure</yellow>"
            )
        ));

        // ── RIGHT FLOATING QUICK-RAIL (Slots 8, 17, 26) ──
        int unownedCount = plugin.guiMenu().countUnownedShopItems(prof);
        top.setItem(8, GuiMenuManager.customIcon(
            "icon_chest",
            "<#FBBF24><bold>Shop</bold></#FBBF24>",
            List.of(
                "<gray>Balance:</gray> <!shadow><white>" + Glyphs.CHAT_ICON_COIN + "</white></!shadow> <#FDE047><bold>" + prof.coins() + " Coins</bold></#FDE047>",
                "<gray>Available to unlock:</gray> <aqua>" + unownedCount + " items</aqua>",
                "",
                "<yellow>▸ Click to browse unowned items</yellow>"
            )
        ));

        ChatTag eqTag = prof.selectedTag();
        top.setItem(17, GuiMenuManager.customIcon(
            "icon_tags",
            "<#E879F9><bold>My Locker & Tags</bold></#E879F9>",
            List.of(
                "<gray>Ball:</gray> <gold>" + prof.selectedBallSkin().plainName() + "</gold>",
                "<gray>Dunk:</gray> <#FB923C>" + prof.selectedDunkStyle().displayName() + "</#FB923C>",
                "<gray>Tag:</gray> " + (eqTag != null ? "<white>" + eqTag.displayName() + "</white>" : "<dark_gray>None</dark_gray>"),
                "",
                "<yellow>▸ Left-Click: Balls & Dunks</yellow>",
                "<aqua>▸ Right-Click: Chat Tags</aqua>"
            )
        ));

        top.setItem(26, GuiMenuManager.customIcon(
            "icon_book",
            "<#818CF8><bold>Official Discord</bold></#818CF8>",
            List.of(
                "<gray>Join tournaments, patch notes</gray>",
                "<gray>and find squad teammates!</gray>",
                "",
                "<aqua>▸ Click for invite link</aqua>"
            )
        ));

        // ── BOTTOM 9-SLOT ESPORTS ACTION DOCK (Player Hotbar 0..8) ──
        GameModeType[] modes = {
            GameModeType.DUEL_1V1,
            GameModeType.STREET_2V2,
            GameModeType.PRO_3V3,
            GameModeType.FULL_5V5
        };
        for (int i = 0; i < modes.length; i++) {
            GameModeType m = modes[i];
            boolean isQueued = queuedMode == m;
            int qCount = plugin.gameManager().queuedCount(m);
            player.getInventory().setItem(i, GuiMenuManager.customIcon(
                isQueued ? "icon_bot_on" : "icon_arena",
                (isQueued ? "<#4ADE80><bold>✔ Queued: " : "<#FF8C1A><bold>Play ") + m.smallCapsTitle() + "</bold>" + (isQueued ? "</#4ADE80>" : "</#FF8C1A>"),
                List.of(
                    "<gray>In Queue:</gray> <#4ADE80>" + qCount + "/" + m.maxPlayers() + "</#4ADE80>",
                    "",
                    isQueued ? "<#F87171>▸ Click to leave queue</#F87171>" : "<yellow>▸ Click to queue " + m.id().toUpperCase() + "</yellow>"
                )
            ));
        }

        player.getInventory().setItem(4, GuiMenuManager.customIcon(
            "icon_bot_on",
            "<#38BDF8><bold>Practice Court</bold></#38BDF8>",
            List.of(
                "<gray>Instant private practice arena</gray>",
                "<gray>with 24s clock & AI Defender.</gray>",
                "",
                "<aqua>▸ Click to step onto court</aqua>"
            )
        ));

        player.getInventory().setItem(5, GuiMenuManager.customIcon(
            "icon_star_shield",
            "<#FBBF24><bold>3-Point Contest</bold></#FBBF24>",
            List.of(
                "<gray>25-ball All-Star rack challenge</gray>",
                "<gray>with Money Balls & Coin prizes!</gray>",
                "",
                "<yellow>▸ Click to start</yellow>"
            )
        ));

        ItemStack ballPreview = prof.selectedBallSkin().createItemStack();
        ItemMeta bm = ballPreview.getItemMeta();
        if (bm != null) {
            bm.displayName(Msg.parse("<#FBBF24><bold>Ball: " + prof.selectedBallSkin().plainName() + "</bold></#FBBF24>"));
            bm.lore(List.of(
                Msg.parse("<gray>Owned Skins:</gray> <white>" + prof.unlockedBalls().size() + "/" + BallSkin.values().length + "</white>"),
                Msg.parse(""),
                Msg.parse("<yellow>▸ Click to cycle owned Ball Skins live!</yellow>")
            ));
            ballPreview.setItemMeta(bm);
        }
        player.getInventory().setItem(6, ballPreview);

        player.getInventory().setItem(7, GuiMenuManager.customIcon(
            "icon_dunk",
            "<#FB923C><bold>Dunk: " + prof.selectedDunkStyle().displayName() + "</bold></#FB923C>",
            List.of(
                "<gray>Owned Dunks:</gray> <white>" + prof.unlockedDunks().size() + "/" + DunkStyle.values().length + "</white>",
                "",
                "<yellow>▸ Click to cycle owned Dunk Styles!</yellow>"
            )
        ));

        player.getInventory().setItem(8, GuiMenuManager.customIcon(
            "icon_leave",
            "<#F87171><bold>Exit Locker Room</bold></#F87171>",
            List.of(
                "<gray>Return to the BosketBall Lobby.</gray>",
                "",
                "<red>▸ Click or press ESC</red>"
            )
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLockerGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof LockerGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);

        StageSession s = sessions.get(player.getUniqueId());
        if (s == null || !player.getUniqueId().equals(holder.ownerId())) {
            player.closeInventory();
            return;
        }

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) {
            return;
        }

        int slot = event.getSlot();
        fr.bosketball.model.PlayerProfile prof = plugin.statsManager().getOrCreate(player);

        if (clickedInv.equals(event.getView().getTopInventory())) {
            switch (slot) {
                case 0 -> exitAndRun(player, () -> plugin.guiMenu().openFriendsAndParty(player));
                case 9 -> exitAndRun(player, () -> plugin.guiMenu().openLeaderboards(player, StatsManager.LeaderboardCategory.ELO));
                case 18 -> exitAndRun(player, () -> plugin.guiMenu().openSettings(player));
                case 8 -> exitAndRun(player, () -> plugin.guiMenu().openShop(player));
                case 17 -> {
                    if (event.isRightClick()) {
                        exitAndRun(player, () -> plugin.guiMenu().openTagsVault(player, 0));
                    } else {
                        exitAndRun(player, () -> plugin.guiMenu().openCosmetics(player));
                    }
                }
                case 26 -> {
                    Msg.sendDiscordCard(player, plugin.settings().links().discord());
                    Msg.playPersonalPackSound(player, "ui.click", 0.9f, 1.2f);
                }
                default -> {
                }
            }
            return;
        }

        // Bottom player hotbar row (slots 0..8)
        if (slot >= 0 && slot <= 3) {
            GameModeType[] modes = {
                GameModeType.DUEL_1V1,
                GameModeType.STREET_2V2,
                GameModeType.PRO_3V3,
                GameModeType.FULL_5V5
            };
            GameModeType targetMode = modes[slot];
            plugin.gameManager().joinQueue(player, targetMode);
            if (sessions.containsKey(player.getUniqueId())) {
                populateOverlayButtons(player, event.getView().getTopInventory());
            }
        } else if (slot == 4) {
            exitAndRun(player, () -> plugin.gameManager().startPractice(player));
        } else if (slot == 5) {
            exitAndRun(player, () -> plugin.gameManager().quickJoin(player, GameModeType.THREE_POINT_CONTEST, null));
        } else if (slot == 6) {
            List<BallSkin> owned = new ArrayList<>(prof.unlockedBalls());
            if (!owned.isEmpty()) {
                int idx = owned.indexOf(prof.selectedBallSkin());
                BallSkin next = owned.get((idx + 1) % owned.size());
                prof.setSelectedBallSkin(next);
                plugin.statsManager().saveProfileAsync(prof);
                rebuildActors(player, s);
                populateOverlayButtons(player, event.getView().getTopInventory());
                Msg.playPersonalPackSound(player, "ui.click", 0.85f, 1.22f);
            }
        } else if (slot == 7) {
            List<DunkStyle> owned = new ArrayList<>(prof.unlockedDunks());
            if (!owned.isEmpty()) {
                int idx = owned.indexOf(prof.selectedDunkStyle());
                DunkStyle next = owned.get((idx + 1) % owned.size());
                prof.setSelectedDunkStyle(next);
                plugin.statsManager().saveProfileAsync(prof);
                populateOverlayButtons(player, event.getView().getTopInventory());
                Msg.playPersonalPackSound(player, "ui.click", 0.85f, 1.22f);
            }
        } else if (slot == 8) {
            close(player, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLockerGuiDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof LockerGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onLockerGuiClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof LockerGuiHolder)) {
            return;
        }
        StageSession s = sessions.get(player.getUniqueId());
        if (s == null || s.suppressAutoCloseOnInventorySwap) {
            return;
        }
        // Pressing ESC cleanly exits the Locker Room back to the Lobby on the next tick!
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (sessions.get(player.getUniqueId()) == s) {
                close(player, true);
            }
        });
    }

    private void exitAndRun(Player player, Runnable nextAction) {
        close(player, true);
        if (player.isOnline() && nextAction != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    nextAction.run();
                }
            });
        }
    }

    public void close(Player player, boolean restorePosition) {
        if (player == null) {
            return;
        }
        StageSession s = sessions.remove(player.getUniqueId());
        if (s == null) {
            return;
        }
        s.suppressAutoCloseOnInventorySwap = true;

        if (player.isOnline()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof LockerGuiHolder) {
                player.closeInventory();
            }
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setSpectatorTarget(null);
            }
        }
        despawnSessionEntities(s);

        if (player.isOnline()) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(player.getUniqueId()) && !sessions.containsKey(other.getUniqueId())) {
                    other.showPlayer(plugin, player);
                    player.showPlayer(plugin, other);
                }
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

    public void refreshAllOpenStages(boolean rebuildActorsToo) {
        for (Map.Entry<UUID, StageSession> entry : sessions.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            StageSession s = entry.getValue();
            if (p != null && p.isOnline()) {
                if (rebuildActorsToo) {
                    rebuildActors(p, s);
                }
                if (p.getOpenInventory().getTopInventory().getHolder() instanceof LockerGuiHolder) {
                    populateOverlayButtons(p, p.getOpenInventory().getTopInventory());
                }
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
     * Spawns ONLY the viewer (1 centered pedestal when solo) OR their online Party members (2, 3, or 4 symmetrical pedestals)!
     * Zero fake NPCs, zero offline Top-ELO statues, zero queued strangers!
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
                    new Vector3f(1.22f, 1.22f, 1.22f),
                    new Quaternionf()
                ));
            });
            hideFromOthers(viewer, ring);
            s.actorProps.add(ring);
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
     * Computes 1, 2, 3, or 4 symmetrically balanced stage poses facing the camera!
     */
    private List<Location> computeSymmetricPedestalPoses(StageSession s, Location stage, int count) {
        List<Location> out = new ArrayList<>(count);
        boolean usingCustomRoom = hasCustomStage();

        // Check if custom slots are configured
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

        // Camera-relative symmetric stage layout (screenRightOffset, forwardDistance):
        double[][] layout = switch (count) {
            case 1 -> new double[][]{{0.0, 3.25}};
            case 2 -> new double[][]{{-1.05, 3.45}, {1.05, 3.45}};
            case 3 -> new double[][]{{0.0, 3.15}, {-1.75, 3.55}, {1.75, 3.55}};
            default -> new double[][]{{-2.35, 3.55}, {-0.82, 3.85}, {0.82, 3.85}, {2.35, 3.55}};
        };

        World w = s.cameraEyeLocation.getWorld();
        double camYawRad = Math.toRadians(s.cameraEyeLocation.getYaw());
        double fwdX = -Math.sin(camYawRad);
        double fwdZ = Math.cos(camYawRad);
        // Right vector on screen (when yaw=0 looking +Z, screen-right is -X!)
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

        StringBuilder mm = new StringBuilder();
        if (leaderCrown) {
            mm.append("<!shadow><gold>👑 </gold></!shadow>");
        }
        mm.append("<!shadow><white>").append(rank.pillGlyph()).append("</white></!shadow>");
        if (tag != null) {
            mm.append(" <!shadow><white>").append(tag.glyph()).append("</white></!shadow>");
        }
        mm.append(" <#FFFDF5><bold>").append(name).append("</bold></#FFFDF5>");
        return Msg.parse(mm.toString());
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

    // Legacy input/confirm hooks kept as safe no-ops / camera lock guards
    public void handleInput(Player player, Input input) {
    }

    public void confirmSelection(Player player) {
    }

    public void scrollSelection(Player player, int delta) {
    }

    public void onClickConfirm(Player player, boolean rightClick) {
    }

    public void onScrollWheel(Player player, int prevSlot, int newSlot) {
    }

    public boolean handleSpectateStopAttempt(Player player) {
        StageSession s = sessions.get(player.getUniqueId());
        if (s == null) {
            return false;
        }
        // Keep camera 100% locked while inside the Locker Room; ESC closes the GUI to exit cleanly!
        return true;
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
