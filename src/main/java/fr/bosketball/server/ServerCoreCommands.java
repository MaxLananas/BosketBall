package fr.bosketball.server;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Standalone Server Core Commands: {@code /build}, {@code /spawn}, {@code /setspawn}, and {@code /perm}.
 */
public final class ServerCoreCommands implements CommandExecutor, TabCompleter {

    private final BosketBallPlugin plugin;
    private final Map<UUID, UUID> lastPrivateMessagePartner = new ConcurrentHashMap<>();

    public ServerCoreCommands(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "build" -> handleBuild(sender, args);
            case "spawn" -> handleSpawn(sender);
            case "setspawn" -> handleSetSpawn(sender);
            case "perm" -> handlePerm(sender, args);
            case "msg" -> handlePrivateMessage(sender, args);
            case "reply" -> handleReply(sender, args);
            default -> false;
        };
    }

    public boolean handlePrivateMessage(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, Msg.error("Only in-game players can send private messages."));
            return true;
        }
        if (args.length < 2) {
            Msg.send(player, Msg.info("Usage: <yellow>/msg <player> <message></yellow>"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            Msg.send(player, Msg.error("Player <white>" + args[0] + "</white> is not online."));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            Msg.send(player, Msg.error("You cannot send a private message to yourself."));
            return true;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (text.isEmpty()) {
            return true;
        }
        deliverPrivateMessage(player, target, text);
        return true;
    }

    public boolean handleReply(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, Msg.error("Only in-game players can reply to private messages."));
            return true;
        }
        if (args.length < 1) {
            Msg.send(player, Msg.info("Usage: <yellow>/r <message></yellow>"));
            return true;
        }
        UUID partnerId = lastPrivateMessagePartner.get(player.getUniqueId());
        Player target = partnerId != null ? Bukkit.getPlayer(partnerId) : null;
        if (target == null || !target.isOnline()) {
            Msg.send(player, Msg.error("Nobody online to reply to right now."));
            return true;
        }
        String text = String.join(" ", args).trim();
        if (text.isEmpty()) {
            return true;
        }
        deliverPrivateMessage(player, target, text);
        return true;
    }

    private void deliverPrivateMessage(Player sender, Player recipient, String plainText) {
        lastPrivateMessagePartner.put(sender.getUniqueId(), recipient.getUniqueId());
        lastPrivateMessagePartner.put(recipient.getUniqueId(), sender.getUniqueId());

        net.kyori.adventure.text.Component body = net.kyori.adventure.text.Component.text(
            plainText,
            net.kyori.adventure.text.format.TextColor.color(0xF1F5F9)
        );
        sender.sendMessage(
            Msg.parse("<#38BDF8>✉ You</#38BDF8> <dark_gray>➔</dark_gray> <white><bold>" + recipient.getName() + "</bold></white> <dark_gray>»</dark_gray> ")
                .append(body)
        );
        recipient.sendMessage(
            Msg.parse("<#38BDF8>✉ </#38BDF8><white><bold>" + sender.getName() + "</bold></white> <dark_gray>➔</dark_gray> <#38BDF8>You</#38BDF8> <dark_gray>»</dark_gray> ")
                .append(body)
        );
        Msg.playPersonalPackSound(recipient, "ui.click", 0.55f, 1.55f);
    }

    public boolean handleBuild(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bosketball.build") && !sender.hasPermission("bosketball.admin")) {
            Msg.send(sender, Msg.error("You do not have permission (<white>bosketball.build</white>) to toggle Build Mode."));
            return true;
        }

        Player target;
        if (args.length >= 1 && sender.hasPermission("bosketball.admin")) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                Msg.send(sender, Msg.error("Player <white>" + args[0] + "</white> is not online."));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            Msg.send(sender, Msg.info("Usage: <yellow>/build <player></yellow>"));
            return true;
        }

        boolean enabled = plugin.protectionManager().toggleBuildMode(target);
        if (enabled) {
            Msg.send(target, Msg.success("Build Mode <#4ADE80><bold>ENABLED</bold></#4ADE80> — manual block edits, FAWE & Axiom active."));
        } else {
            Msg.send(target, Msg.info("Build Mode <#F87171><bold>DISABLED</bold></#F87171> — world protection restored."));
        }
        if (!sender.equals(target)) {
            Msg.send(sender, Msg.success("Build Mode for <white>" + target.getName() + "</white>: " + (enabled ? "<green>ON</green>" : "<red>OFF</red>")));
        }
        return true;
    }

    public boolean handleSpawn(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            Msg.send(sender, Msg.error("Only in-game players can teleport to spawn."));
            return true;
        }
        plugin.protectionManager().teleportToSpawn(p);
        Msg.send(p, Msg.info("Teleported to the BosketBall Lobby."));
        return true;
    }

    public boolean handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            Msg.send(sender, Msg.error("Stand at your desired spawn location in-game and run <yellow>/setspawn</yellow>."));
            return true;
        }
        if (!p.hasPermission("bosketball.admin")) {
            Msg.send(p, Msg.error("Admin permission required (<white>bosketball.admin</white>)."));
            return true;
        }
        plugin.protectionManager().setLobbySpawn(p.getLocation());
        Msg.send(p, Msg.success("Lobby Spawn saved at your exact location & rotation!"));
        return true;
    }

    public boolean handlePerm(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bosketball.admin")) {
            Msg.send(sender, Msg.error("Admin permission required (<white>bosketball.admin</white>)."));
            return true;
        }
        PermissionManager pm = plugin.permissionManager();
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            Msg.send(sender, "<gold><bold>BosketBall Standalone Permissions</bold></gold>");
            Msg.send(sender, " <yellow>/perm list</yellow> <gray>— List all permission groups</gray>");
            Msg.send(sender, " <yellow>/perm user <player> info</yellow> <gray>— Inspect player's group & permissions</gray>");
            Msg.send(sender, " <yellow>/perm user <player> group <group></yellow> <gray>— Assign group (default, vip, builder, mod, admin)</gray>");
            Msg.send(sender, " <yellow>/perm user <player> add|remove <node></yellow> <gray>— Grant/remove user node</gray>");
            Msg.send(sender, " <yellow>/perm group <group> add|remove <node></yellow> <gray>— Grant/remove group node</gray>");
            Msg.send(sender, " <yellow>/perm reload</yellow> <gray>— Reload permissions.yml</gray>");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                pm.loadOrCreate();
                Msg.send(sender, Msg.success("Reloaded <white>permissions.yml</white> and updated all online players."));
            }
            case "list", "groups" -> {
                Msg.send(sender, Msg.info("Permission Groups: <yellow>" + String.join("</yellow>, <yellow>", pm.groupNames()) + "</yellow>"));
            }
            case "user" -> {
                if (args.length < 3) {
                    Msg.send(sender, Msg.error("Usage: <yellow>/perm user <player> <info|group|add|remove> ...</yellow>"));
                    return true;
                }
                Player online = Bukkit.getPlayerExact(args[1]);
                PlayerProfile prof = online != null
                    ? plugin.statsManager().getOrCreate(online)
                    : plugin.statsManager().allProfiles().stream()
                        .filter(p -> p.lastKnownName() != null && p.lastKnownName().equalsIgnoreCase(args[1]))
                        .findFirst()
                        .orElse(null);
                if (prof == null) {
                    Msg.send(sender, Msg.error("Unknown player <white>" + args[1] + "</white> (must have joined at least once)."));
                    return true;
                }
                String action = args[2].toLowerCase(Locale.ROOT);
                switch (action) {
                    case "info" -> {
                        Map<String, Boolean> eff = pm.resolveEffectivePermissions(prof.uuid());
                        Msg.send(sender, Msg.info("<white><bold>" + prof.lastKnownName() + "</bold></white> — Group: <gold>"
                            + pm.getUserGroup(prof.uuid()) + "</gold> (" + eff.size() + " effective nodes)"));
                        for (Map.Entry<String, Boolean> e : eff.entrySet()) {
                            Msg.send(sender, "  <dark_gray>•</dark_gray> " + (e.getValue() ? "<green>+" : "<red>-") + e.getKey() + "</green>");
                        }
                    }
                    case "group", "setgroup" -> {
                        String gName = args.length >= 5 && args[3].equalsIgnoreCase("set") ? args[4] : (args.length >= 4 ? args[3] : null);
                        if (gName == null || !pm.setUserGroup(prof.uuid(), gName)) {
                            Msg.send(sender, Msg.error("Unknown group. Available: <yellow>" + String.join(", ", pm.groupNames()) + "</yellow>"));
                            return true;
                        }
                        Msg.send(sender, Msg.success("Set <white><bold>" + prof.lastKnownName() + "</bold></white>'s permission group to <gold>" + gName.toLowerCase(Locale.ROOT) + "</gold>."));
                    }
                    case "add", "grant" -> {
                        if (args.length < 4) {
                            Msg.send(sender, Msg.error("Usage: <yellow>/perm user " + prof.lastKnownName() + " add <permission.node></yellow>"));
                            return true;
                        }
                        pm.addUserPermission(prof.uuid(), args[3]);
                        Msg.send(sender, Msg.success("Granted <green>" + args[3] + "</green> to <white>" + prof.lastKnownName() + "</white>."));
                    }
                    case "remove", "unset" -> {
                        if (args.length < 4) {
                            Msg.send(sender, Msg.error("Usage: <yellow>/perm user " + prof.lastKnownName() + " remove <permission.node></yellow>"));
                            return true;
                        }
                        pm.removeUserPermission(prof.uuid(), args[3]);
                        Msg.send(sender, Msg.info("Removed <yellow>" + args[3] + "</yellow> from <white>" + prof.lastKnownName() + "</white>."));
                    }
                    default -> Msg.send(sender, Msg.error("Unknown action. Use <yellow>info</yellow>, <yellow>group</yellow>, <yellow>add</yellow>, or <yellow>remove</yellow>."));
                }
            }
            case "group" -> {
                if (args.length < 3) {
                    Msg.send(sender, Msg.error("Usage: <yellow>/perm group <group> <create|info|add|remove> [node]</yellow>"));
                    return true;
                }
                String gid = args[1].toLowerCase(Locale.ROOT);
                String act = args[2].toLowerCase(Locale.ROOT);
                switch (act) {
                    case "create" -> {
                        if (pm.createGroup(gid)) {
                            Msg.send(sender, Msg.success("Created permission group <gold>" + gid + "</gold>."));
                        } else {
                            Msg.send(sender, Msg.warn("Group <gold>" + gid + "</gold> already exists."));
                        }
                    }
                    case "info" -> {
                        PermissionManager.GroupDefinition def = pm.getGroup(gid);
                        if (def == null) {
                            Msg.send(sender, Msg.error("Unknown group <white>" + gid + "</white>."));
                            return true;
                        }
                        Msg.send(sender, Msg.info("Group <gold><bold>" + def.id() + "</bold></gold> inherits: <aqua>" + def.parents() + "</aqua>"));
                        for (String n : def.permissions()) {
                            Msg.send(sender, "  <dark_gray>•</dark_gray> <white>" + n + "</white>");
                        }
                    }
                    case "add" -> {
                        if (args.length < 4 || !pm.addGroupPermission(gid, args[3])) {
                            Msg.send(sender, Msg.error("Usage: <yellow>/perm group <existingGroup> add <node></yellow>"));
                            return true;
                        }
                        Msg.send(sender, Msg.success("Added <green>" + args[3] + "</green> to group <gold>" + gid + "</gold>."));
                    }
                    case "remove" -> {
                        if (args.length < 4 || !pm.removeGroupPermission(gid, args[3])) {
                            Msg.send(sender, Msg.error("Node not found on group <gold>" + gid + "</gold>."));
                            return true;
                        }
                        Msg.send(sender, Msg.info("Removed <yellow>" + args[3] + "</yellow> from group <gold>" + gid + "</gold>."));
                    }
                    default -> Msg.send(sender, Msg.error("Use <yellow>create</yellow>, <yellow>info</yellow>, <yellow>add</yellow>, or <yellow>remove</yellow>."));
                }
            }
            default -> Msg.send(sender, Msg.error("Unknown subcommand. Run <yellow>/perm help</yellow>."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if ("msg".equals(cmd) && args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> !n.equalsIgnoreCase(sender.getName()))
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                .toList();
        }
        if ("build".equals(cmd) && args.length == 1 && sender.hasPermission("bosketball.admin")) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                .toList();
        }
        if ("perm".equals(cmd) && sender.hasPermission("bosketball.admin")) {
            if (args.length == 1) {
                return prefixFilter(List.of("user", "group", "list", "reload", "help"), args[0]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("user")) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("group")) {
                return prefixFilter(new ArrayList<>(plugin.permissionManager().groupNames()), args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("user")) {
                return prefixFilter(List.of("info", "group", "add", "remove"), args[2]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("group")) {
                return prefixFilter(List.of("info", "create", "add", "remove"), args[2]);
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("user") && args[2].equalsIgnoreCase("group")) {
                return prefixFilter(new ArrayList<>(plugin.permissionManager().groupNames()), args[3]);
            }
            if (args.length == 4 && (args[2].equalsIgnoreCase("add") || args[2].equalsIgnoreCase("remove"))) {
                return prefixFilter(List.of("bosketball.admin", "bosketball.build", "bosketball.staff", "axiom.*", "fawe.*", "worldedit.*"), args[3]);
            }
        }
        return List.of();
    }

    private static List<String> prefixFilter(List<String> items, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return items.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
