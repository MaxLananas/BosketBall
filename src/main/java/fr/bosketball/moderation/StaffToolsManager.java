package fr.bosketball.moderation;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.util.Msg;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class StaffToolsManager implements Listener {

    private final BosketBallPlugin plugin;

    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    public StaffToolsManager(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(UUID playerId) {
        return playerId != null && vanished.contains(playerId);
    }

    public boolean isFrozen(UUID playerId) {
        return playerId != null && frozen.contains(playerId);
    }

    public boolean toggleVanish(Player staff) {
        if (staff == null) {
            return false;
        }
        UUID id = staff.getUniqueId();
        if (vanished.remove(id)) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                other.showEntity(plugin, staff);
            }
            staff.setInvisible(false);
            Msg.send(staff, Msg.info("Vanish <white><bold>OFF</bold></white> — you are visible again."));
            return false;
        }
        vanished.add(id);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(id) && !fr.bosketball.command.BosketCommand.isStaff(other)) {
                other.hideEntity(plugin, staff);
            }
        }
        staff.setInvisible(true);
        Msg.send(staff, Msg.success("Vanish <white><bold>ON</bold></white> — players cannot see or tab-list you."));
        return true;
    }

    public boolean toggleFreeze(Player target, Player actor) {
        if (target == null) {
            return false;
        }
        UUID id = target.getUniqueId();
        if (frozen.remove(id)) {
            Msg.send(target, Msg.info("You can move again."));
            if (actor != null && !actor.getUniqueId().equals(id)) {
                Msg.send(actor, Msg.info("<white>" + target.getName() + "</white> is no longer frozen."));
            }
            return false;
        }
        frozen.add(id);
        target.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        Msg.send(target, Msg.warn("You have been <white><bold>frozen</bold></white> by staff."));
        if (actor != null && !actor.getUniqueId().equals(id)) {
            Msg.send(actor, Msg.success("<white>" + target.getName() + "</white> frozen in place."));
        }
        return true;
    }

    public int freezeMatch(Player actor, java.util.List<Player> participants) {
        int frozenCount = 0;
        for (Player target : participants) {
            if (target == null || target.getUniqueId().equals(actor == null ? null : actor.getUniqueId())) {
                continue;
            }
            if (frozen.add(target.getUniqueId())) {
                target.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                Msg.send(target, Msg.warn("Play paused by staff — stay where you are."));
                frozenCount++;
            }
        }
        return frozenCount;
    }

    public int unfreezeAll() {
        int released = frozen.size();
        for (UUID id : frozen) {
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                Msg.send(online, Msg.info("Play resumed — you can move again."));
            }
        }
        frozen.clear();
        return released;
    }

    public int frozenCount() {
        return frozen.size();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        if (frozen.isEmpty() || !frozen.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }
        Location pinned = from.clone();
        pinned.setYaw(to.getYaw());
        pinned.setPitch(to.getPitch());
        event.setTo(pinned);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        frozen.remove(id);
        vanished.remove(id);
    }
}
