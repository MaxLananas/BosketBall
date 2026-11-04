package fr.bosketball.listener;

import fr.bosketball.BosketBallPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import io.papermc.paper.event.player.AsyncChatEvent;

/**
 * Feeds the presence system with real player input.
 *
 * <p>Before this listener nothing on the server ever called {@code SocialService#touch}: the
 * inactivity clock was only reset by the presence tick itself, so the [TAB] list, the Friends GUI
 * and the queue never showed anybody as AFK, and {@code /afk} did nothing at all.</p>
 *
 * <p>Movement is throttled by a squared-distance check so a 20 Hz event handler never turns into a
 * map write per tick; every other input is inherently rare and recorded as-is.</p>
 */
public final class AfkListener implements Listener {

    /** Ignore sub-block drift (head bobbing, sitting offsets) so it does not count as activity. */
    private static final double MOVE_THRESHOLD_SQUARED = 0.09;

    private final BosketBallPlugin plugin;

    public AfkListener(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    private void touch(Player player) {
        if (player == null || plugin.socialService() == null) {
            return;
        }
        plugin.socialService().touch(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() != to.getWorld()) {
            touch(event.getPlayer());
            return;
        }
        if (from.distanceSquared(to) >= MOVE_THRESHOLD_SQUARED) {
            touch(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        touch(event.getPlayer());
    }
}
