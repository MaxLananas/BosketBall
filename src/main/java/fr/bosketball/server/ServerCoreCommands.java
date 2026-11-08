package fr.bosketball.server;

import fr.bosketball.util.Palette;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.util.Msg;
import fr.bosketball.util.Perms;
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
            case "help" -> {
                Msg.sendInteractiveHelpCard(sender);
                yield true;
            }
            case "pack" -> handlePack(sender);
            case "build" -> handleBuild(sender, args);
            case "spawn" -> handleSpawn(sender);
            case "setspawn" -> handleSetSpawn(sender);
            case "perm" -> handlePerm(sender, args);
            case "msg" -> handlePrivateMessage(sender, args);
            case "reply" -> handleReply(sender, args);
            default -> false;
        };
    }

    public boolean handlePack(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, Msg.error("Only in-game players can load the Resource Pack."));
            return true;
        }
        plugin.resourcePackHost().sendPackTo(player);
        Msg.send(player, Msg.success("Sending the official <white><bold>BosketBall Resource Pack</bold></white> to your client..."));
        return true;
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

        String pmPill = Msg.pillTag(fr.bosketball.util.Glyphs.SYS_PM);
        net.kyori.adventure.text.Component body = net.kyori.adventure.text.Component.text(
            plainText,
            net.kyori.adventure.text.format.TextColor.color(0xF1F5F9)
        );
        sender.sendMessage(
            Msg.parse(pmPill + " <" + Palette.VIOLET + ">You</" + Palette.VIOLET + "> <dark_gray>➔</dark_gray> <white><bold>" + recipient.getName() + "</bold></white> <dark_gray>»</dark_gray> ")
                .append(body)
        );
        net.kyori.adventure.text.Component incomingPrefix = Msg.parse(
            pmPill + " <white><bold>" + sender.getName() + "</bold></white> <dark_gray>➔</dark_gray> <" + Palette.VIOLET + ">You</" + Palette.VIOLET + "> <dark_gray>»</dark_gray> "
        )
            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Msg.parse("<" + Palette.VIOLET + ">Click to reply to <white>" + sender.getName() + "</white></" + Palette.VIOLET + ">")))
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/r "));
        recipient.sendMessage(incomingPrefix.append(body));
        Msg.playPersonalPackSound(recipient, "ui.click", 0.55f, 1.55f);
        spyOnPrivateMessage(sender, recipient, plainText, body);
    }

    /**
     * Social spy (Round 26/27): staff and OPs receive a copy of every private message so they can
     * moderate what players send each other. Sender and recipient never see their own line twice.
     */
    private void spyOnPrivateMessage(
        Player sender,
        Player recipient,
        String plainText,
        net.kyori.adventure.text.Component body
    ) {
        java.util.Set<java.util.UUID> seen = new java.util.HashSet<>();
        seen.add(sender.getUniqueId());
        seen.add(recipient.getUniqueId());
        fr.bosketball.util.ChatSpy.relay(
            "<" + Palette.VIOLET + ">ᴘᴍ</" + Palette.VIOLET + "> <white>" + fr.bosketball.util.Msg.escapeMini(sender.getName()) + "</white>"
                + " <dark_gray>➔</dark_gray> <white>" + fr.bosketball.util.Msg.escapeMini(recipient.getName()) + "</white>"
                + " <dark_gray>»</dark_gray> <" + Palette.TEXT + ">" + fr.bosketball.util.Msg.escapeMini(plainText) + "</" + Palette.TEXT + ">",
            seen
        );
    }

    public boolean handleBuild(CommandSender sender, String[] args) {
        if (sender instanceof Player p && !Perms.build(sender)) {
            Msg.sendAlert(p, "Builder mode is restricted to builders (bosketball.build).");
            return true;
        }

        Player target;
        if (args.length >= 1 && Perms.has(sender, Perms.ADMIN)) {
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
            Msg.send(target, Msg.success("Build Mode <" + Palette.SUCCESS + "><bold>ENABLED</bold></" + Palette.SUCCESS + "> — manual block edits, FAWE & Axiom active."));
        } else {
            Msg.send(target, Msg.info("Build Mode <" + Palette.DANGER + "><bold>DISABLED</bold></" + Palette.DANGER + "> — world protection restored."));
        }
        if (!sender.equals(target)) {
            Msg.send(sender, Msg.success("Build Mode for <white>" + target.getName() + "</white>: " + (enabled ? "<green>ON</green>" : "<red>OFF</red>")));
        }
        return true;
    }

    public void clearPlayerState(UUID playerId) {
        if (playerId == null) {
            return;
        }
        lastPrivateMessagePartner.remove(playerId);
        lastPrivateMessagePartner.values().removeIf(id -> id.equals(playerId));
    }

    public boolean handleSpawn(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            Msg.send(sender, Msg.error("Only in-game players can teleport to spawn."));
            return true;
        }
        if (plugin.cinematicCamera() != null && plugin.cinematicCamera().isWatching(p.getUniqueId())) {
            plugin.cinematicCamera().stopWatching(p, true);
        }
        if (plugin.gameManager() != null) {
            var session = plugin.gameManager().getSessionOf(p.getUniqueId());
            if (session != null) {
                boolean activeCompetitive = session.mode().competitiveTeamMatch()
                    && session.getTeam(p.getUniqueId()) != fr.bosketball.model.TeamSide.SPECTATOR
                    && (session.state() == fr.bosketball.game.MatchSession.State.COUNTDOWN
                        || session.state() == fr.bosketball.game.MatchSession.State.LIVE
                        || session.state() == fr.bosketball.game.MatchSession.State.CELEBRATION);
                if (activeCompetitive) {
                    Msg.sendAlert(p, "You are in a live competitive match! Use <yellow>/leave</yellow> if you want to forfeit and return to the Lobby.");
                    return true;
                }
                session.removePlayer(p, true);
            }
        }
        plugin.protectionManager().teleportToSpawn(p);
        Msg.sendCourt(p, Msg.info("Teleported to the BosketBall Lobby."));
        return true;
    }

    public boolean handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            Msg.send(sender, Msg.error("Stand at your desired spawn location in-game and run <yellow>/setspawn</yellow>."));
            return true;
        }
        if (!Perms.admin(p)) {
            Msg.sendAlert(p, "Setting the Lobby Spawn is restricted to administrators (bosketball.admin).");
            return true;
        }
        plugin.protectionManager().setLobbySpawn(p.getLocation());
        Msg.send(p, Msg.success("Lobby Spawn saved at your exact location & rotation!"));
        return true;
    }

    public boolean handlePerm(CommandSender sender, String[] args) {
        if (!Perms.check(sender, Perms.PERMS, "manage permissions")) {
            return true;
        }
        PermissionManager pm = plugin.permissionManager();
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            Msg.send(sender, "<gold><bold>BosketBall Standalone Permissions</bold></gold>");
            Msg.send(sender, " <yellow>/perm list</yellow> <gray>— Groups, parents and node counts</gray>");
            Msg.send(sender, " <yellow>/perm nodes [filter]</yellow> <gray>— Every BosketBall node and its default</gray>");
            Msg.send(sender, " <yellow>/perm check <player> <node></yellow> <gray>— Why is a node granted or denied?</gray>");
            Msg.send(sender, " <yellow>/perm user <player> info|nodes|group <group>|add|remove <node></yellow>");
            Msg.send(sender, " <yellow>/perm group <group> info|create|add|remove|inherit|users|delete</yellow>");
            Msg.send(sender, " <yellow>/perm reload</yellow> <gray>— Reload permissions.yml and refresh everyone</gray>");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                pm.loadOrCreate();
                Msg.send(sender, Msg.success("Reloaded <white>permissions.yml</white> and updated all online players."));
            }
            case "list", "groups" -> {
                for (String group : pm.groupNames()) {
                    PermissionManager.GroupDefinition def = pm.getGroup(group);
                    int members = pm.usersInGroup(group).size();
                    Msg.send(sender, " <gold>" + group + "</gold> <dark_gray>•</dark_gray> <gray>"
                        + (def == null ? 0 : def.permissions().size()) + " node(s)</gray>"
                        + " <dark_gray>•</dark_gray> <gray>" + members + " member(s)</gray>"
                        + (def == null || def.parents().isEmpty() ? "" : " <dark_gray>• inherits </dark_gray><aqua>"
                            + String.join("</aqua>, <aqua>", def.parents()) + "</aqua>"));
                }
            }
            case "nodes" -> {
                String filter = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
                int shown = 0;
                for (Perms.NodeSpec node : Perms.nodes()) {
                    if (!filter.isEmpty() && !node.id().contains(filter)) {
                        continue;
                    }
                    shown++;
                    Msg.send(sender, " <white>" + node.id() + "</white> <dark_gray>(" + node.defaultMode() + ")</dark_gray>"
                        + " <gray>— " + node.description() + "</gray>");
                }
                Msg.send(sender, Msg.info(shown + " node(s) listed"
                    + (filter.isEmpty() ? "" : " for filter <white>" + filter + "</white>")
                    + " <dark_gray>•</dark_gray> <gray>/perm nodes staff, admin, map…</gray>"));
            }
            case "check", "why" -> {
                if (args.length < 3) {
                    Msg.send(sender, Msg.error("Usage: <yellow>/perm check <player> <node></yellow>"));
                    return true;
                }
                PlayerProfile prof = findProfile(args[1]);
                if (prof == null) {
                    Msg.send(sender, Msg.error("Unknown player <white>" + args[1] + "</white>."));
                    return true;
                }
                String node = Perms.normalize(args[2]);
                boolean allowed = pm.effectiveAllows(prof.uuid(), node);
                Msg.send(sender, Msg.info("<white>" + prof.lastKnownName() + "</white> → <white>" + node + "</white>: "
                    + (allowed ? "<" + Palette.SUCCESS + "><bold>ALLOWED</bold></" + Palette.SUCCESS + ">" : "<" + Palette.DANGER + "><bold>DENIED</bold></" + Palette.DANGER + ">")
                    + " <dark_gray>•</dark_gray> <gray>" + pm.explain(prof.uuid(), node) + "</gray>"));
            }
            case "user" -> {
                if (args.length < 3) {
                    Msg.send(sender, Msg.error("Usage: <yellow>/perm user <player> <info|nodes|group|add|remove> ...</yellow>"));
                    return true;
                }
                PlayerProfile prof = findProfile(args[1]);
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
                            Msg.send(sender, "  <dark_gray>•</dark_gray> " + (e.getValue() ? "<green>+" : "<red>-") + e.getKey());
                        }
                    }
                    case "nodes" -> {
                        Map<String, Boolean> nodes = pm.userNodes(prof.uuid());
                        if (nodes.isEmpty()) {
                            Msg.send(sender, Msg.info("<white>" + prof.lastKnownName() + "</white> has no personal node."));
                            return true;
                        }
                        Msg.send(sender, Msg.info("Personal nodes of <white>" + prof.lastKnownName() + "</white>:"));
                        for (Map.Entry<String, Boolean> e : nodes.entrySet()) {
                            Msg.send(sender, "  <dark_gray>•</dark_gray> " + (e.getValue() ? "<green>+" : "<red>-") + e.getKey());
                        }
                    }
                    case "group", "setgroup" -> {
                        String gName = args.length >= 5 && args[3].equalsIgnoreCase("set") ? args[4] : (args.length >= 4 ? args[3] : null);
                        if (gName == null || !pm.setUserGroup(prof.uuid(), gName)) {
                            Msg.send(sender, Msg.error("Unknown group. Available: <yellow>" + String.join(", ", pm.groupNames()) + "</yellow>"));
                            return true;
                        }
                        Msg.send(sender, Msg.success("Set <white><bold>" + prof.lastKnownName() + "</bold></white>'s permission group to <gold>"
                            + gName.toLowerCase(Locale.ROOT) + "</gold>."));
                    }
                    case "add", "grant" -> {
                        if (args.length < 4) {
                            Msg.send(sender, Msg.error("Usage: <yellow>/perm user " + prof.lastKnownName() + " add <permission.node></yellow>"));
                            return true;
                        }
                        String node = Perms.normalize(args[3]);
                        pm.addUserPermission(prof.uuid(), args[3].startsWith("-") ? "-" + node : node);
                        Msg.send(sender, Msg.success("Granted <green>" + node + "</green> to <white>" + prof.lastKnownName() + "</white>."));
                    }
                    case "remove", "unset" -> {
                        if (args.length < 4) {
                            Msg.send(sender, Msg.error("Usage: <yellow>/perm user " + prof.lastKnownName() + " remove <permission.node></yellow>"));
                            return true;
                        }
                        String node = Perms.normalize(args[3]);
                        if (!pm.removeUserPermission(prof.uuid(), node) && !pm.removeUserPermission(prof.uuid(), "-" + node)) {
                            Msg.send(sender, Msg.warn("That node is not set on <white>" + prof.lastKnownName() + "</white>; it may come from the group."));
                            return true;
                        }
                        Msg.send(sender, Msg.info("Removed <yellow>" + node + "</yellow> from <white>" + prof.lastKnownName() + "</white>."));
                    }
                    default -> Msg.send(sender, Msg.error("Unknown action. Use <yellow>info</yellow>, <yellow>nodes</yellow>, <yellow>group</yellow>, <yellow>add</yellow>, or <yellow>remove</yellow>."));
                }
            }
            case "group" -> {
                if (args.length < 3) {
                    Msg.send(sender, Msg.error("Usage: <yellow>/perm group <group> <create|info|add|remove|inherit|users|delete> [value]</yellow>"));
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
                        Msg.send(sender, Msg.info("Group <gold><bold>" + def.id() + "</bold></gold> inherits: <aqua>"
                            + def.parents() + "</aqua> <dark_gray>•</dark_gray> <gray>" + def.permissions().size() + " node(s)</gray>"));
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
                    case "inherit", "parent" -> {
                        if (args.length < 5 || !(args[3].equalsIgnoreCase("add") || args[3].equalsIgnoreCase("remove"))) {
                            Msg.send(sender, Msg.error("Usage: <yellow>/perm group " + gid + " inherit <add|remove> <group></yellow>"));
                            return true;
                        }
                        boolean add = args[3].equalsIgnoreCase("add");
                        boolean ok = add ? pm.addGroupParent(gid, args[4]) : pm.removeGroupParent(gid, args[4]);
                        if (!ok) {
                            Msg.send(sender, Msg.error("Could not update the inheritance of <gold>" + gid + "</gold>."));
                            return true;
                        }
                        Msg.send(sender, Msg.success("Group <gold>" + gid + "</gold> now "
                            + (add ? "inherits <aqua>" : "no longer inherits <aqua>") + args[4].toLowerCase(Locale.ROOT) + "</aqua>."));
                    }
                    case "users", "members" -> {
                        List<String> members = pm.usersInGroup(gid);
                        Msg.send(sender, Msg.info("Group <gold>" + gid + "</gold> has <white>" + members.size() + "</white> member(s)"
                            + (members.isEmpty() ? "." : ": <white>" + String.join("</white>, <white>", members) + "</white>")));
                    }
                    case "delete" -> {
                        String error = pm.deleteGroup(gid);
                        if (error != null) {
                            Msg.send(sender, Msg.error(error));
                            return true;
                        }
                        Msg.send(sender, Msg.success("Deleted permission group <gold>" + gid + "</gold>."));
                    }
                    default -> Msg.send(sender, Msg.error("Use <yellow>create</yellow>, <yellow>info</yellow>, <yellow>add</yellow>, <yellow>remove</yellow>, <yellow>inherit</yellow>, <yellow>users</yellow> or <yellow>delete</yellow>."));
                }
            }
            default -> Msg.send(sender, Msg.error("Unknown subcommand. Run <yellow>/perm help</yellow>."));
        }
        return true;
    }

    private PlayerProfile findProfile(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return plugin.statsManager().getOrCreate(online);
        }
        return plugin.statsManager().allProfiles().stream()
            .filter(p -> p.lastKnownName() != null && p.lastKnownName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
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
        if ("perm".equals(cmd) && Perms.has(sender, Perms.PERMS)) {
            List<String> known = new ArrayList<>(Perms.nodeIds());
            known.addAll(List.of("axiom.*", "fawe.*", "worldedit.*", "minecraft.command.*", "bukkit.command.*"));
            if (args.length == 1) {
                return prefixFilter(List.of("user", "group", "list", "nodes", "check", "reload", "help"), args[0]);
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("user") || args[0].equalsIgnoreCase("check"))) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("nodes")) {
                return prefixFilter(List.of("bosketball", "staff", "admin", "map", "build", "tags", "cosmetics"), args[1]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("group")) {
                return prefixFilter(new ArrayList<>(plugin.permissionManager().groupNames()), args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("user")) {
                return prefixFilter(List.of("info", "nodes", "group", "add", "remove"), args[2]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("check")) {
                return prefixFilter(known, args[2]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("group")) {
                return prefixFilter(List.of("info", "create", "add", "remove", "inherit", "users", "delete"), args[2]);
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("user") && args[2].equalsIgnoreCase("group")) {
                return prefixFilter(new ArrayList<>(plugin.permissionManager().groupNames()), args[3]);
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("group") && args[2].equalsIgnoreCase("inherit")) {
                return prefixFilter(List.of("add", "remove"), args[3]);
            }
            if (args.length == 5 && args[0].equalsIgnoreCase("group") && args[2].equalsIgnoreCase("inherit")) {
                return prefixFilter(new ArrayList<>(plugin.permissionManager().groupNames()), args[4]);
            }
            if (args.length == 4 && (args[2].equalsIgnoreCase("add") || args[2].equalsIgnoreCase("remove"))) {
                return prefixFilter(known, args[3]);
            }
        }
        return List.of();
    }

    private static List<String> prefixFilter(List<String> items, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return items.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
