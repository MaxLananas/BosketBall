package fr.bosketball.listener;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.arena.MapEditorManager;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.ui.BosketChatRenderer;
import fr.bosketball.ui.GuiMenuManager;
import fr.bosketball.ui.LockerRoom3dMenuManager;
import fr.bosketball.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
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
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
 * Paper 26.2 Event Bridge connecting Player Inputs, 3D Locker-Room Stage, Center-Screen Tab ([F]),
 * Illustrated Welcome Cards (image-9.png), Custom Hotbar Icons (image-10.png), Map Architect Wand & Court Protection!
 */
public final class GameplayListener implements Listener {

    private static final NamespacedKey ICON_PORTAL_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_portal");
    private static final NamespacedKey ICON_SHIELD_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_star_shield");
    private static final NamespacedKey ICON_TAB_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_tab");
    private static final NamespacedKey ICON_CHEST_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_chest");
    private static final NamespacedKey ICON_BOOK_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_book");
    private static final NamespacedKey ICON_PODIUM_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_podium");
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
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        plugin.statsManager().refreshLeaderboardBadges();

        // Hide private per-viewer 3D HUD entities (Center Tab, 3D Locker-Room, 1st-person rigs) from newly joined player
        plugin.centerTabManager().hideNewPanelFromLateJoiner(player);
        plugin.lockerRoom3dManager().hideActivePropsFromJoiner(player);
        for (MatchSession s : plugin.gameManager().activeSessions()) {
            s.hideRigEntitiesFromNewJoiner(player);
        }

        // Equip 5-slot custom pixel-art lobby hotbar (image-10.png!) if slots are free
        if (plugin.getConfig().getBoolean("general.auto-equip-lobby-playbook", true)) {
            BallSkin.equipLobbyHotbarIfEmpty(player, false);
        }

        // Send Illustrated BosketBall Welcome & Broadcast Card (image-9.png!)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (plugin.getConfig().getBoolean("general.welcome-card-enabled", true)) {
                Msg.sendIllustratedWelcomeCard(
                    player,
                    plugin.getConfig().getString("links.website", "https://bosketball.fr"),
                    plugin.getConfig().getString("links.shop", "https://shop.bosketball.fr"),
                    plugin.getConfig().getString("links.discord", "https://discord.gg/bosketball"),
                    "Mise à jour 26.2 Chaos Cubed !",
                    "Testez le Vestiaire 3D (/bb 3d), les 54 Tags Glossy (x4) & le Tab Central ([F]) !",
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
        plugin.centerTabManager().close(player);
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

    @EventHandler
    public void onGuiGuard(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof GuiMenuManager.BosketHolder) {
            return; // Handled by GuiMenuManager listener
        }
        if (plugin.gameManager().getSessionOf(player.getUniqueId()) != null
            || plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDragGuard(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (plugin.gameManager().getSessionOf(player.getUniqueId()) != null
            || plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
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

        // 0. Inside 3D Locker-Room Stage: Left-Click = Confirm, Right-Click = Next Tab!
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
            // 1. Custom Map 3D Architect Laser Wand (/bb map wand)!
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

            // 2. 5-Slot Custom Pixel-Art Lobby Hotbar Items (image-10.png!)
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                NamespacedKey modelKey = getItemModelKey(event.getItem());
                if (modelKey != null) {
                    if (ICON_PORTAL_KEY.equals(modelKey)) {
                        event.setCancelled(true);
                        plugin.lockerRoom3dManager().open(player, LockerRoom3dMenuManager.TopTab.PLAY);
                    } else if (ICON_SHIELD_KEY.equals(modelKey) || LEGACY_PLAYBOOK_KEY.equals(modelKey)) {
                        event.setCancelled(true);
                        plugin.guiMenu().openMainHub(player);
                    } else if (ICON_TAB_KEY.equals(modelKey)) {
                        event.setCancelled(true);
                        plugin.centerTabManager().toggle(player);
                    } else if (ICON_CHEST_KEY.equals(modelKey)) {
                        event.setCancelled(true);
                        if (player.isSneaking()) {
                            plugin.guiMenu().openCosmetics(player);
                        } else {
                            plugin.guiMenu().openTagsVault(player);
                        }
                    } else if (ICON_BOOK_KEY.equals(modelKey)) {
                        event.setCancelled(true);
                        if (player.isSneaking()) {
                            plugin.guiMenu().openFriendsAndParty(player);
                        } else {
                            plugin.guiMenu().openMatchHistory(player);
                        }
                    } else if (ICON_PODIUM_KEY.equals(modelKey)) {
                        event.setCancelled(true);
                        plugin.guiMenu().openLeaderboards(player, StatsManager.LeaderboardCategory.ELO);
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
                    session.handleBallHandlerPrimaryUse(player, false);
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
            NamespacedKey handKey = getItemModelKey(player.getInventory().getItemInMainHand());
            if (handKey != null && BallSkin.NAMESPACE.equals(handKey.getNamespace())) {
                event.setCancelled(true); // Never place lobby hotbar icons into Item Frames / Allays / ArmorStands!
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
            || plugin.gameManager().getSessionOf(event.getPlayer().getUniqueId()) != null) {
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
        // Protect Lobby 5-Slot Hotbar icons from accidental [Q] drops (unless sneaking in Creative mode)
        NamespacedKey droppedKey = getItemModelKey(event.getItemDrop().getItemStack());
        if (droppedKey != null && BallSkin.NAMESPACE.equals(droppedKey.getNamespace())
            && !(player.getGameMode() == GameMode.CREATIVE && player.isSneaking())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session == null) {
            // In Lobby -> [F] toggles the 100% Custom Center-Screen Tab Overlay!
            event.setCancelled(true);
            plugin.centerTabManager().toggle(player);
            return;
        }
        event.setCancelled(true);
        // On court: if NOT holding the ball, or if Sneaking, [F] toggles the Center-Screen Tab!
        boolean holdingBall = session.ball() != null && player.getUniqueId().equals(session.ball().holderId());
        if (!holdingBall || player.isSneaking()) {
            plugin.centerTabManager().toggle(player);
        } else {
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
    public void onEntityDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player
            && plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            // Schedule clean 3D Stage exit on next tick so Sneak dismount teleports smoothly back to returnLocation!
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
                    Msg.playPersonalPackSound(player, "ui.click", 0.75f, 0.85f);
                    plugin.lockerRoom3dManager().close(player, true);
                }
            });
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
            || plugin.lockerRoom3dManager().isIn3dMenu(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        // Never litter BosketBall UI icons, court kit or basketballs onto the ground on /kill or death!
        event.getDrops().removeIf(stack -> {
            NamespacedKey key = getItemModelKey(stack);
            return key != null && BallSkin.NAMESPACE.equals(key.getNamespace());
        });
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
