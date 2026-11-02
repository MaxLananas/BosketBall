package fr.bosketball.command;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.util.Msg;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Sportsmanship /gg Command (+5 Coins & +10 XP).
 */
public final class GgCommand implements CommandExecutor {

    private final BosketBallPlugin plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public GgCommand(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - cooldowns.getOrDefault(player.getUniqueId(), 0L) < 15_000L) {
            Msg.send(player, Msg.warn("You already said GG recently."));
            return true;
        }
        cooldowns.put(player.getUniqueId(), now);

        PlayerProfile prof = plugin.statsManager().getOrCreate(player);
        prof.addXp(10);
        prof.addCoins(5);
        plugin.statsManager().saveAsync();

        Bukkit.broadcast(Msg.parse(
            "<!shadow><white>" + prof.effectiveRank().pillGlyph() + "</white></!shadow> <white>"
                + player.getName() + "</white> <dark_gray>»</dark_gray> "
                + "<#FBBF24><bold>GG!</bold></#FBBF24> <dark_gray>(+5 Coins)</dark_gray>"
        ));
        Msg.playPersonalPackSound(player, "ui.click", 0.9f, 1.4f);
        return true;
    }
}
