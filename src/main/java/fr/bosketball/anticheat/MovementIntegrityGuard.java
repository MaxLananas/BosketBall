package fr.bosketball.anticheat;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.util.Msg;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Lightweight, match-scoped anti-cheat.
 *
 * <p>BosketBall is not a full anticheat — it only guards the two things that would ruin a
 * basketball server: <b>movement</b> (fly / speed / teleport bursts during a live match) and
 * <b>perfect-input macros</b> (a scripted dunk/shot release that always lands the green
 * window). Violations never auto-punish by default: they are logged, staff are notified, and
 * the incident appears in the moderation feed.</p>
 */
public final class MovementIntegrityGuard implements Listener {

    /** A detected violation. */
    public record Flag(UUID playerId, String playerName, String kind, String detail, long atMillis) {
    }

    private static final int MAX_FLAGS = 200;

    private final BosketBallPlugin plugin;
    private final Map<UUID, Location> lastLocation = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> perfectReleaseStamps = new ConcurrentHashMap<>();
    private final Deque<Flag> flags = new ArrayDeque<>();

    public MovementIntegrityGuard(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    //  MOVEMENT
    // =========================================================================

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (plugin.settings().antiCheat() == null || !plugin.settings().antiCheat().enabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR
            || player.isFlying() || player.getAllowFlight() || player.isGliding() || player.isInsideVehicle()) {
            return;
        }
        UUID id = player.getUniqueId();
        Location previous = lastLocation.put(id, event.getTo().clone());
        if (previous == null || !event.getTo().getWorld().equals(previous.getWorld())) {
            return;
        }
        // Only police live competitive matches & the lobby, never practice free-roam
        boolean inMatch = plugin.gameManager() != null && plugin.gameManager().isInMatch(id);
        if (!inMatch) {
            return;
        }
        double dy = event.getTo().getY() - previous.getY();
        double horizontal = Math.hypot(event.getTo().getX() - previous.getX(), event.getTo().getZ() - previous.getZ());
        double maxHorizontal = 0.62 * plugin.settings().antiCheat().maxSpeedMultiplier();
        if (dy > 0.55 && horizontal > 0.55) {
            flag(player, "FLY", String.format("dy=%.2f dxz=%.2f", dy, horizontal));
            event.setTo(previous);
        } else if (horizontal > maxHorizontal) {
            flag(player, "SPEED", String.format("dxz=%.2f (max %.2f)", horizontal, maxHorizontal));
            event.setTo(previous);
        }
    }

    private void flag(Player player, String kind, String detail) {
        Flag flag = new Flag(player.getUniqueId(), player.getName(), kind, detail, System.currentTimeMillis());
        synchronized (flags) {
            flags.addFirst(flag);
            while (flags.size() > MAX_FLAGS) {
                flags.removeLast();
            }
        }
        if (plugin.settings().antiCheat().notifyStaff()) {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.isOp() || staff.hasPermission("bosketball.staff")) {
                    Msg.send(staff, "<#F87171><bold>⚑ ANTI-CHEAT</bold></#F87171> <white>" + player.getName()
                        + "</white> <gray>" + kind + " — " + detail + "</gray>");
                }
            }
        }
    }

    // =========================================================================
    //  MACRO DETECTION
    // =========================================================================

    /**
     * Records a shot/dunk release quality. Seven near-perfect timings in a row is statistically
     * impossible for a human and strongly suggests a macro; the streak is reported once.
     */
    public void recordRelease(Player player, boolean greenWindow, boolean perfect) {
        if (plugin.settings().antiCheat() == null || !plugin.settings().antiCheat().macroDetection()) {
            return;
        }
        UUID id = player.getUniqueId();
        Deque<Long> stamps = perfectReleaseStamps.computeIfAbsent(id, k -> new ArrayDeque<>());
        synchronized (stamps) {
            if (perfect || greenWindow) {
                stamps.addLast(System.currentTimeMillis());
            } else {
                stamps.clear();
            }
            int threshold = plugin.settings().antiCheat().macroPerfectStreakThreshold();
            if (stamps.size() >= threshold) {
                stamps.clear();
                flag(player, "MACRO", threshold + " perfect releases in a row");
            }
        }
    }

    public void reset(UUID playerId) {
        lastLocation.remove(playerId);
        perfectReleaseStamps.remove(playerId);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        reset(event.getPlayer().getUniqueId());
    }

    public java.util.List<Flag> recentFlags(int limit) {
        synchronized (flags) {
            return flags.stream().limit(Math.max(0, limit)).toList();
        }
    }
}
