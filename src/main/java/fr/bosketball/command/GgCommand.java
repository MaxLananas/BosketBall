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

/**
 * Sportsmanlike {@code /gg} command & 1-click Post-Match Ceremony button handler:
 * - Shares {@link BosketChatRenderer#buildPlayerChatPrefix} so rank, #1/#2/#3 ELO badges & tags render identically.
 * - Grants the GG Coin bonus strictly ONCE per finished match within the 45-second post-match window.
 */
public final class GgCommand implements CommandExecutor, TabCompleter {

    private static final Map<UUID, Long> LAST_GG_CHAT_MS = new ConcurrentHashMap<>();

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
        LAST_GG_CHAT_MS.put(pid, now);

        int coinReward = rewarded ? Math.max(0, plugin.settings().economy().ggRewardCoins()) : 0;
        if (rewarded) {
            PlayerProfile prof = plugin.statsManager().getOrCreate(player);
            prof.addXp(10);
            if (coinReward > 0) {
                prof.addCoins(coinReward);
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
