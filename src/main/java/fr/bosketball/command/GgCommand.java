package fr.bosketball.command;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.ui.BosketChatRenderer;
import fr.bosketball.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GgCommand implements CommandExecutor, TabCompleter {

    private static final Map<UUID, Long> LAST_GG_CHAT_MS = new ConcurrentHashMap<>();

    /** Hard ceiling so a server with thousands of unique joins cannot grow this forever. */
    static final int GG_CACHE_LIMIT = 512;

    private final BosketBallPlugin plugin;

    public GgCommand(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "<#FBBF24><bold>GG!</bold></#FBBF24>");
            return true;
        }
        executeGg(plugin, player);
        return true;
    }

    /** Forgets one player (quit). */
    public static void forget(UUID playerId) {
        if (playerId != null) {
            LAST_GG_CHAT_MS.remove(playerId);
        }
    }

    /**
     * Drops entries older than the cooldown, and empties the map if nobody pruned for too long.
     * Pure bookkeeping on a static map: unit-tested without a server.
     */
    public static void prune(long nowMillis, long cooldownMillis) {
        pruneOlderThan(LAST_GG_CHAT_MS, nowMillis, cooldownMillis, GG_CACHE_LIMIT);
    }

    /** Pure bookkeeping: drop stale entries, then hard-empty if the cap is passed anyway. */
    public static int pruneOlderThan(Map<UUID, Long> map, long nowMillis, long keepMillis, int limit) {
        if (map == null || map.isEmpty()) {
            return 0;
        }
        long keep = Math.max(1L, keepMillis);
        map.entrySet().removeIf(e -> e.getValue() == null || nowMillis - e.getValue() > keep);
        if (map.size() > limit) {
            map.clear();
        }
        return map.size();
    }

    public static int cachedPlayers() {
        return LAST_GG_CHAT_MS.size();
    }

    public static void executeGg(BosketBallPlugin plugin, Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (plugin.isGlobalChatMuted() && !player.hasPermission("bosketball.staff") && !player.isOp()) {
            Msg.send(player, Msg.warn("Global chat is currently muted by an administrator."));
            return;
        }
        long now = System.currentTimeMillis();
        UUID pid = player.getUniqueId();

        boolean rewarded = plugin.gameManager().markPostMatchGgClaimed(pid, now);

        if (!rewarded && (now - LAST_GG_CHAT_MS.getOrDefault(pid, 0L)) < 20_000L) {
            Msg.send(player, Msg.warn("You already said GG recently!"));
            return;
        }
        if (LAST_GG_CHAT_MS.size() > GG_CACHE_LIMIT / 2) {
            prune(now, 60_000L);
        }
        LAST_GG_CHAT_MS.put(pid, now);

        int coinReward = rewarded ? Math.max(0, plugin.settings().economy().ggRewardCoins()) : 0;
        if (rewarded) {
            PlayerProfile prof = plugin.statsManager().getOrCreate(player);
            prof.addXp(10);
            if (coinReward > 0) {
                prof.addEarnedCoins(coinReward);
            }
            plugin.statsManager().saveProfileAsync(prof);
            if (plugin.sidebarManager() != null) {
                plugin.sidebarManager().clearPlayerCache(pid);
            }
        }

        String rewardHint = coinReward > 0 ? " <dark_gray>(+" + coinReward + " Coins)</dark_gray>" : "";
        Component prefix = BosketChatRenderer.buildPlayerChatPrefix(
            player,
            plugin.statsManager(),
            plugin.gameManager(),
            plugin.guildService(),
            plugin.settings().chat().showTopStatBadges()
        );
        Bukkit.broadcast(Component.text()
            .append(prefix)
            .append(Msg.parse("<#FBBF24><bold>GG!</bold></#FBBF24>" + rewardHint))
            .asComponent());
        Msg.playPersonalPackSound(player, rewarded ? "ui.confirm" : "ui.click", 0.9f, 1.4f);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
