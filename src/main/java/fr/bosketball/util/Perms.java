package fr.bosketball.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Perms {

    public static final String ADMIN = "bosketball.admin";
    public static final String STAFF = "bosketball.staff";
    public static final String MAP = "bosketball.map";
    public static final String BUILD = "bosketball.build";
    public static final String PLAY = "bosketball.play";

    private Perms() {
    }

    public static boolean has(CommandSender sender, String node) {
        if (sender == null) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            return true;
        }
        return player.isOp() || player.hasPermission(node);
    }

    public static boolean admin(CommandSender sender) {
        return has(sender, ADMIN);
    }

    public static boolean staff(CommandSender sender) {
        return admin(sender) || has(sender, STAFF);
    }

    public static boolean map(CommandSender sender) {
        return admin(sender) || has(sender, MAP);
    }

    public static boolean build(CommandSender sender) {
        return admin(sender) || has(sender, BUILD);
    }
}
