package fr.bosketball.listener;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.game.MatchSession;
import fr.bosketball.ui.BosketChatRenderer;
import fr.bosketball.ui.GuiMenuManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
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

/**
 * Paper 26.2 Event Bridge connecting Player Inputs, Custom ChatRenderer, ResourcePack Auto-Push & Court Protection!
 */
public final class GameplayListener implements Listener {

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
        Player player = event.getPlayer();
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);

        Action action = event.getAction();
        int slot = player.getInventory().getHeldItemSlot();

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            switch (slot) {
                case 0, 3 -> session.handleBallHandlerPrimaryUse(player);
                case 1 -> session.handlePassOrAlleyOop(player);
                case 2 -> session.handleCrossoverMove(player);
                case 8 -> session.removePlayer(player, false);
                default -> session.handleBallHandlerPrimaryUse(player);
            }
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            session.handleDefensiveAction(player);
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session != null && event.getRightClicked() instanceof Interaction) {
            event.setCancelled(true);
            session.handleBallHandlerPrimaryUse(player);
        }
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
