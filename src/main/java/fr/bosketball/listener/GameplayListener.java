package fr.bosketball.listener;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.BallSkin;
import fr.bosketball.ui.BosketChatRenderer;
import fr.bosketball.ui.GuiMenuManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Paper 26.2 Event Bridge connecting Player Inputs, Custom ChatRenderer, ResourcePack Auto-Push & Court Protection!
 */
public final class GameplayListener implements Listener {

    private static final NamespacedKey PLAYBOOK_MODEL_KEY = new NamespacedKey(BallSkin.NAMESPACE, "tactics_playbook");

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
        plugin.statsManager().getOrCreate(player);
        plugin.statsManager().refreshLeaderboardBadges();

        if (plugin.getConfig().getBoolean("general.auto-equip-lobby-playbook", true)
            && !hasPlaybookItem(player)) {
            ItemStack slot8 = player.getInventory().getItem(8);
            if (slot8 == null || slot8.getType().isAir()) {
                player.getInventory().setItem(8, BallSkin.createPlaybookItem());
            }
        }

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
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session != null) {
            session.removePlayer(player, false);
        }
        plugin.bossBarHud().clearPlayer(player);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof GuiMenuManager.BosketGuiHolder holder) {
            plugin.guiMenu().handleMenuClick(player, holder, event);
            return;
        }
        // Lock hotbar/armor slots while inside an active match!
        if (plugin.gameManager().getSessionOf(player.getUniqueId()) != null) {
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
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());

        if (session == null) {
            if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)
                && isPlaybookItem(event.getItem())) {
                event.setCancelled(true);
                plugin.guiMenu().openMainHub(player);
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
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session != null && event.getRightClicked() instanceof Interaction) {
            event.setCancelled(true);
            boolean fromSlot0 = player.getInventory().getHeldItemSlot() == 0;
            session.handleBallHandlerPrimaryUse(player, fromSlot0);
        }
    }

    private static boolean isPlaybookItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && PLAYBOOK_MODEL_KEY.equals(meta.getItemModel());
    }

    private static boolean hasPlaybookItem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isPlaybookItem(item)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
            if (session != null) {
                event.setCancelled(true);
                session.handleDefensiveAction(player);
            }
        }
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session != null) {
            event.setCancelled(true);
            session.handlePassOrAlleyOop(player);
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session != null) {
            event.setCancelled(true);
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
            && plugin.gameManager().getSessionOf(player.getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
            && plugin.gameManager().getSessionOf(player.getUniqueId()) != null) {
            event.setCancelled(true);
            event.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.gameManager().getSessionOf(event.getPlayer().getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.gameManager().getSessionOf(event.getPlayer().getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }
}
