package fr.bosketball.command;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class DiscordCommand implements CommandExecutor {

    private final BosketBallPlugin plugin;

    public DiscordCommand(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String url = plugin.settings().links().discord();
        Msg.sendDiscordCard(sender, url);
        if (sender instanceof Player player) {
            Msg.playPersonalPackSound(player, "ui.click", 0.85f, 1.2f);
        }
        return true;
    }
}
