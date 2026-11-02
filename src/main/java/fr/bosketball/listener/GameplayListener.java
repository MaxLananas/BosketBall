package fr.bosketball.listener;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.arena.MapEditorManager;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.ui.BosketChatRenderer;
import fr.bosketball.ui.GuiMenuManager;
import fr.bosketball.ui.LockerRoom3dMenuManager;
import fr.bosketball.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Event Bridge connecting Player Inputs, Lobby Hotbar, Court Controls, Anti-Duplication Guards & Welcome Card.
 */
public final class GameplayListener implements Listener {

    private static final NamespacedKey ICON_ARENA_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_arena");
    private static final NamespacedKey ICON_PORTAL_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_portal");
    private static final NamespacedKey ICON_SHIELD_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_star_shield");
    private static final NamespacedKey ICON_CHEST_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_chest");
    private static final NamespacedKey ICON_BOOK_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_book");
    private static final NamespacedKey ICON_PODIUM_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_podium");
    private static final NamespacedKey ICON_LEAVE_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_leave");
    private static final NamespacedKey LEGACY_PLAYBOOK_KEY = new NamespacedKey(BallSkin.NAMESPACE, "tactics_playbook");

    private final BosketBallPlugin plugin;
    private final BosketChatRenderer chatRenderer;

    public GameplayListener(BosketBallPlugin plugin, BosketChatRenderer chatRenderer) {
        this.plugin = plugin;
        this.chatRenderer = chatRenderer;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAsyncChat(AsyncChatEvent event) {
        if (plugin.getConfig().getBoolean("chat.enabled", true)) {
            event.renderer(chatRenderer);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        BosketBallPlugin.sanitizeResidualPlayerEffects(player);

        boolean firstJoin = plugin.statsManager().getIfPresent(player.getUniqueId()) == null;
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        if (firstJoin) {
            profile.setCoins(Math.max(0, plugin.getConfig().getInt("general.starting-coins", 100)));
            plugin.statsManager().saveAsync();
        }
        plugin.statsManager().refreshLeaderboardBadges();

        // Hide private per-viewer HUD & camera entities
        plugin.lockerRoom3dManager().hideActivePropsFromJoiner(player);
        plugin.cinematicCamera().hideActiveFromLateJoiner(player);
        for (MatchSession s : plugin.gameManager().activeSessions()) {
            s.hideRigEntitiesFromNewJoiner(player);
        }

        if (plugin.getConfig().getBoolean("general.auto-equip-lobby-playbook", true)) {
            BallSkin.equipLobbyHotbarIfEmpty(player, false);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (plugin.getConfig().getBoolean("general.welcome-card-enabled", true)) {
                Msg.sendIllustratedWelcomeCard(
                    player,
                    plugin.getConfig().getString("links.website", "https://bosketball.net"),
                    plugin.getConfig().getString("links.shop", "https://store.bosketball.net"),
                    plugin.getConfig().getString("links.discord", "https://discord.gg/pnJhKuU2QK"),
                    "Welcome to BosketBall!",
                    "Queue 1v1–5v5, earn Coins on the court & unlock Tags in /bb shop.",
                    profile.unreadMailCount()
                );
            }
        }, 12L);

        if (plugin.getConfig().getBoolean("resource-pack.auto-send-on-join", true)
            && plugin.resourcePackHost().isReadyToSend()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.resourcePackHost().sendPackTo(player);
                }
            }, 20L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.clearLobbyRig(player.getUniqueId(), false);
        plugin.mapEditor().removePlayer(player.getUniqueId());
        plugin.cinematicCamera().stopWatching(player, true);
        plugin.gameManager().leaveQueue(player, false);
        plugin.lockerRoom3dManager().close(player, true);
        plugin.socialManager().handleDisconnect(player.getUniqueId());

        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session != null) {
            session.removePlayer(player, false);
        }
        plugin.bossBarHud().clearPlayer(player);
        if (plugin.sidebarManager() != null) {
            plugin.sidebarManager().clearPlayerCache(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            plugin.lockerRoom3dManager().handleInput(player, event.getInput());
        }
    }

    /**
     * Strict anti-duplication guard: blocks moving BosketBall court/lobby UI items into chests, hoppers,
     * crafting grids, offhand slots, or player storage slots.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onGuiGuard(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof GuiMenuManager.BosketHolder) {
            return; // Handled by GuiMenuManager
        }
        if (plugin.gameManager().getSessionOf(player.getUniqueId()) != null
            || plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Prevent stashing/duplicating custom bosketball:* lobby hotbar icons in chests or survival inventory
        boolean creativeBypass = player.getGameMode() == GameMode.CREATIVE
            && player.isSneaking()
            && player.hasPermission("bosketball.admin");
        if (!creativeBypass) {
            ItemStack hotbarSwapped = event.getHotbarButton() >= 0
                ? player.getInventory().getItem(event.getHotbarButton())
                : null;
            if (isBosketItem(event.getCurrentItem()) || isBosketItem(event.getCursor()) || isBosketItem(hotbarSwapped)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDragGuard(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (plugin.gameManager().getSessionOf(player.getUniqueId()) != null
            || plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())
            || isBosketItem(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Action action = event.getAction();

        // Inside Locker Room Stage: Left-Click = Select, Right-Click = Next Tab
        if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            event.setCancelled(true);
            if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                plugin.lockerRoom3dManager().onClickConfirm(player, false);
            } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                plugin.lockerRoom3dManager().onClickConfirm(player, true);
            }
            return;
        }

        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session == null) {
            // 1. Custom Map Architect Wand (/bb map wand)
            if (MapEditorManager.isArchitectWand(event.getItem()) && player.hasPermission("bosketball.admin")) {
                if (event.getClickedBlock() != null && (action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_BLOCK)) {
                    event.setCancelled(true);
                    boolean first = (action == Action.LEFT_CLICK_BLOCK);
                    if (player.isSneaking()) {
                        plugin.mapEditor().setBarrierCorner(player, event.getClickedBlock().getLocation(), first);
                    } else {
                        plugin.mapEditor().setCourtCorner(player, event.getClickedBlock().getLocation(), first);
                    }
                    return;
                }
            }

            // 2. Lobby Hotbar Items
            NamespacedKey modelKey = getItemModelKey(event.getItem());
            if (modelKey != null && BallSkin.NAMESPACE.equals(modelKey.getNamespace())) {
                event.setCancelled(true);
                if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                    if (ICON_ARENA_KEY.equals(modelKey)) {
                        plugin.guiMenu().openPlayModes(player);
                    } else if (ICON_PORTAL_KEY.equals(modelKey)) {
                        plugin.lockerRoom3dManager().open(player, LockerRoom3dMenuManager.TopTab.PLAY);
                    } else if (ICON_SHIELD_KEY.equals(modelKey) || LEGACY_PLAYBOOK_KEY.equals(modelKey)) {
                        plugin.guiMenu().openMainHub(player);
                    } else if (ICON_CHEST_KEY.equals(modelKey)) {
                        if (player.isSneaking()) {
                            plugin.guiMenu().openCosmetics(player);
                        } else {
                            plugin.guiMenu().openTagsVault(player);
                        }
                    } else if (ICON_BOOK_KEY.equals(modelKey)) {
                        if (player.isSneaking()) {
                            plugin.guiMenu().openFriendsAndParty(player);
                        } else {
                            plugin.guiMenu().openMainHub(player);
                        }
                    } else if (ICON_PODIUM_KEY.equals(modelKey)) {
                        plugin.guiMenu().openLeaderboards(player, StatsManager.LeaderboardCategory.ELO);
                    } else if (ICON_LEAVE_KEY.equals(modelKey)) {
                        plugin.gameManager().leaveQueue(player, true);
                    }
                }
            }
            return;
        }

        int slot = player.getInventory().getHeldItemSlot();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            switch (slot) {
                case 0 -> {
                    boolean chargingWithConsumable = session.handleBallHandlerPrimaryUse(player, true);
                    if (chargingWithConsumable) {
                        event.setUseInteractedBlock(Event.Result.DENY);
                        event.setUseItemInHand(Event.Result.ALLOW);
                    } else {
                        event.setCancelled(true);
                    }
                }
                case 1 -> {
                    event.setCancelled(true);
                    session.handlePassOrAlleyOop(player);
                }
                case 2 -> {
                    event.setCancelled(true);
                    session.handleCrossoverMove(player);
                }
                case 3 -> {
                    event.setCancelled(true);
                    if (player.isSneaking()) {
                        cycleOwnedDunkStyle(player);
                    } else {
                        session.handleBallHandlerPrimaryUse(player, false);
                    }
                }
                case 4 -> {
                    event.setCancelled(true);
                    session.toggleSparringBot(player);
                }
                case 8 -> {
                    event.setCancelled(true);
                    session.removePlayer(player, false);
                }
                default -> {
                    event.setCancelled(true);
                    session.handleBallHandlerPrimaryUse(player, false);
                }
            }
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            session.handleDefensiveAction(player);
        } else {
            event.setCancelled(true);
        }
    }

    private void cycleOwnedDunkStyle(Player player) {
        PlayerProfile prof = plugin.statsManager().getOrCreate(player);
        List<DunkStyle> owned = new ArrayList<>(prof.unlockedDunks());
        if (owned.size() <= 1) {
            player.sendActionBar(Msg.parse("<#FBBF24>Unlock more Dunk Styles in /bb shop!</#FBBF24>"));
            return;
        }
        int cur = owned.indexOf(prof.selectedDunkStyle());
        DunkStyle next = owned.get((cur + 1) % owned.size());
        prof.setSelectedDunkStyle(next);
        plugin.statsManager().saveAsync();
        player.getInventory().setItem(3, GuiMenuManager.customIcon("icon_dunk",
            "<#FBBF24><bold>Dunk Style: " + next.displayName() + "</bold></#FBBF24>",
            List.of("<gray>Sprint + Aim at Rim + Right-Click = Slam Dunk</gray>")));
        player.sendActionBar(Msg.parse("<#FBBF24>Dunk Style: <bold>" + next.displayName() + "</bold></#FBBF24>"));
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
    }

    @EventHandler
    public void onStopUsingItem(PlayerStopUsingItemEvent event) {
        Player player = event.getPlayer();
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session != null) {
            session.tryReleaseChargedShotNow(player);
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.lockerRoom3dManager().onClickConfirm(player, true);
            return;
        }
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session == null) {
            if (isBosketItem(player.getInventory().getItemInMainHand()) || isBosketItem(player.getInventory().getItemInOffHand())) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getRightClicked() instanceof Interaction || event.getRightClicked() instanceof ArmorStand) {
            event.setCancelled(true);
            if (session.ball() != null && player.getUniqueId().equals(session.ball().holderId())) {
                boolean fromSlot0 = player.getInventory().getHeldItemSlot() == 0;
                session.handleBallHandlerPrimaryUse(player, fromSlot0);
            } else {
                session.handleDefensiveAction(player);
            }
        }
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (event.getRightClicked().getScoreboardTags().contains(HoopDisplaySet.DISPLAY_TAG)
            || plugin.gameManager().getSessionOf(event.getPlayer().getUniqueId()) != null
            || isBosketItem(event.getPlayerItem())) {
            event.setCancelled(true);
        }
    }

    private static NamespacedKey getItemModelKey(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null ? meta.getItemModel() : null;
    }

    private static boolean isBosketItem(ItemStack item) {
        NamespacedKey key = getItemModelKey(item);
        return key != null && BallSkin.NAMESPACE.equals(key.getNamespace());
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
                event.setCancelled(true);
                plugin.lockerRoom3dManager().onClickConfirm(player, false);
                return;
            }
            MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
            if (session != null) {
                event.setCancelled(true);
                session.handleDefensiveAction(player);
            }
        }
        if (event.getEntity().getScoreboardTags().contains(HoopDisplaySet.DISPLAY_TAG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session != null) {
            event.setCancelled(true);
            session.handlePassOrAlleyOop(player);
            return;
        }
        if (isBosketItem(event.getItemDrop().getItemStack())
            && !(player.getGameMode() == GameMode.CREATIVE && player.isSneaking() && player.hasPermission("bosketball.admin"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session == null) {
            if (isBosketItem(event.getMainHandItem()) || isBosketItem(event.getOffHandItem())) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        boolean holdingBall = session.ball() != null && player.getUniqueId().equals(session.ball().holderId());
        if (holdingBall) {
            session.handleCrossoverMove(player);
        }
    }

    @EventHandler
    public void onSneakToggle(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session != null && session.ball() != null && player.getUniqueId().equals(session.ball().holderId())) {
            session.handleCrossoverMove(player);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
            && (plugin.gameManager().getSessionOf(player.getUniqueId()) != null
                || plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId()))) {
            event.setCancelled(true);
        } else if (event.getEntity().getScoreboardTags().contains(HoopDisplaySet.DISPLAY_TAG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
            && (plugin.gameManager().getSessionOf(player.getUniqueId()) != null
                || plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId()))) {
            event.setCancelled(true);
            event.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        Player player = event.getPlayer();
        if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.lockerRoom3dManager().handleSpectateStopAttempt(player);
            return;
        }
        if (plugin.cinematicCamera().isWatching(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.cinematicCamera().handleSpectateStopAttempt(player);
        }
    }

    @EventHandler
    public void onPlayerSwingIn3dStage(PlayerAnimationEvent event) {
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            Player player = event.getPlayer();
            if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
                event.setCancelled(true);
                plugin.lockerRoom3dManager().confirmSelection(player);
            }
        }
    }

    @EventHandler
    public void onScrollHotbarIn3dStage(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            int prev = event.getPreviousSlot();
            int next = event.getNewSlot();
            int diff = next - prev;
            if (diff > 4) diff -= 9;
            if (diff < -4) diff += 9;
            plugin.lockerRoom3dManager().scrollSelection(player, diff > 0 ? 1 : -1);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (plugin.gameManager().getSessionOf(p.getUniqueId()) != null
            || plugin.lockerRoom3dManager().isIn3dMenu(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        if (plugin.gameManager().getSessionOf(p.getUniqueId()) != null
            || plugin.lockerRoom3dManager().isIn3dMenu(p.getUniqueId())
            || isBosketItem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        event.getDrops().removeIf(GameplayListener::isBosketItem);
        if (plugin.lockerRoom3dManager().isIn3dMenu(p.getUniqueId())) {
            plugin.lockerRoom3dManager().close(p, true);
        }
        MatchSession session = plugin.gameManager().getSessionOf(p.getUniqueId());
        if (session != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            session.removePlayer(p, true);
        }
        plugin.clearLobbyRig(p.getUniqueId(), false);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (plugin.getConfig().getBoolean("general.auto-equip-lobby-playbook", true)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && !plugin.gameManager().isInMatch(p.getUniqueId())) {
                    BallSkin.equipLobbyHotbarIfEmpty(p, false);
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player p = event.getPlayer();
        if (plugin.gameManager().getSessionOf(p.getUniqueId()) != null
            || plugin.lockerRoom3dManager().isIn3dMenu(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        String loadedName = event.getWorld().getName();
        for (Arena arena : plugin.arenaManager().allArenas()) {
            if (loadedName.equalsIgnoreCase(arena.worldName())) {
                arena.spawnDisplays();
            }
        }
    }
}
