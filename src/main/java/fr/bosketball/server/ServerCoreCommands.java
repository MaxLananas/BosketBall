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
        Msg.send(player, Msg.success("Sending the official <#F8FAFC><bold>BosketBall Resource Pack</bold></#F8FAFC> to your client..."));
        return true;
    }

    public boolean handlePrivateMessage(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, Msg.error("Only in-game players can send private messages."));
            return true;
        }
        if (args.length < 2) {
            Msg.send(player, Msg.info("Usage: <#FDE047>/msg <player> <message></#FDE047>"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            Msg.send(player, Msg.error("Player <#F8FAFC>" + args[0] + "</#F8FAFC> is not online."));
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
            Msg.send(player, Msg.info("Usage: <#FDE047>/r <message></#FDE047>"));
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
            Msg.parse(pmPill + " <" + Palette.VIOLET + ">You</" + Palette.VIOLET + "> <#64748B>»</#64748B> <#F8FAFC><bold>" + recipient.getName() + "</bold></#F8FAFC> <#64748B>»</#64748B> ")
                .append(body)
        );
        net.kyori.adventure.text.Component incomingPrefix = Msg.parse(
            pmPill + " <#F8FAFC><bold>" + sender.getName() + "</bold></#F8FAFC> <#64748B>»</#64748B> <" + Palette.VIOLET + ">You</" + Palette.VIOLET + "> <#64748B>»</#64748B> "
        )
            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Msg.parse("<" + Palette.VIOLET + ">Click to reply to <#F8FAFC>" + sender.getName() + "</#F8FAFC></" + Palette.VIOLET + ">")))
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
            "<" + Palette.VIOLET + ">ᴘᴍ</" + Palette.VIOLET + "> <#F8FAFC>" + fr.bosketball.util.Msg.escapeMini(sender.getName()) + "</#F8FAFC>"
                + " <#64748B>»</#64748B> <#F8FAFC>" + fr.bosketball.util.Msg.escapeMini(recipient.getName()) + "</#F8FAFC>"
                + " <#64748B>»</#64748B> <" + Palette.TEXT + ">" + fr.bosketball.util.Msg.escapeMini(plainText) + "</" + Palette.TEXT + ">",
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
                Msg.send(sender, Msg.error("Player <#F8FAFC>" + args[0] + "</#F8FAFC> is not online."));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            Msg.send(sender, Msg.info("Usage: <#FDE047>/build <player></#FDE047>"));
            return true;
        }

        boolean enabled = plugin.protectionManager().toggleBuildMode(target);
        if (enabled) {
            Msg.send(target, Msg.success("Build Mode <" + Palette.SUCCESS + "><bold>ENABLED</bold></" + Palette.SUCCESS + "> — manual block edits, FAWE & Axiom active."));
        } else {
            Msg.send(target, Msg.info("Build Mode <" + Palette.DANGER + "><bold>DISABLED</bold></" + Palette.DANGER + "> — world protection restored."));
        }
        if (!sender.equals(target)) {
            Msg.send(sender, Msg.success("Build Mode for <#F8FAFC>" + target.getName() + "</#F8FAFC>: " + (enabled ? "<#4ADE80>ON</#4ADE80>" : "<#F87171>OFF</#F87171>")));
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
                    Msg.sendAlert(p, "You are in a live competitive match! Use <#FDE047>/leave</#FDE047> if you want to forfeit and return to the Lobby.");
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
            Msg.send(sender, Msg.error("Stand at your desired spawn location in-game and run <#FDE047>/setspawn</#FDE047>."));
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
            Msg.send(sender, "<#FBBF24><bold>BosketBall Standalone Permissions</bold></#FBBF24>");
            Msg.send(sender, " <#FDE047>/perm list</#FDE047> <#94A3B8>— Groups, parents and node counts</#94A3B8>");
            Msg.send(sender, " <#FDE047>/perm nodes [filter]</#FDE047> <#94A3B8>— Every BosketBall node and its default</#94A3B8>");
            Msg.send(sender, " <#FDE047>/perm check <player> <node></#FDE047> <#94A3B8>— Why is a node granted or denied?</#94A3B8>");
            Msg.send(sender, " <#FDE047>/perm user <player> info|nodes|group <group>|add|remove <node></#FDE047>");
            Msg.send(sender, " <#FDE047>/perm group <group> info|create|add|remove|inherit|users|delete</#FDE047>");
            Msg.send(sender, " <#FDE047>/perm reload</#FDE047> <#94A3B8>— Reload permissions.yml and refresh everyone</#94A3B8>");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                pm.loadOrCreate();
                Msg.send(sender, Msg.success("Reloaded <#F8FAFC>permissions.yml</#F8FAFC> and updated all online players."));
            }
            case "list", "groups" -> {
                for (String group : pm.groupNames()) {
                    PermissionManager.GroupDefinition def = pm.getGroup(group);
                    int members = pm.usersInGroup(group).size();
                    Msg.send(sender, " <#FBBF24>" + group + "</#FBBF24> <#64748B>·</#64748B> <#94A3B8>"
                        + (def == null ? 0 : def.permissions().size()) + " node(s)</#94A3B8>"
                        + " <#64748B>•</#64748B> <#94A3B8>" + members + " member(s)</#94A3B8>"
                        + (def == null || def.parents().isEmpty() ? "" : " <#64748B>• inherits </#64748B><#38BDF8>"
                            + String.join("</#38BDF8>, <#38BDF8>", def.parents()) + "</#38BDF8>"));
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
                    Msg.send(sender, " <#F8FAFC>" + node.id() + "</#F8FAFC> <#64748B>(" + node.defaultMode() + ")</#64748B>"
                        + " <#94A3B8>— " + node.description() + "</#94A3B8>");
                }
                Msg.send(sender, Msg.info(shown + " node(s) listed"
                    + (filter.isEmpty() ? "" : " for filter <#F8FAFC>" + filter + "</#F8FAFC>")
                    + " <#64748B>•</#64748B> <#94A3B8>/perm nodes staff, admin, map…</#94A3B8>"));
            }
            case "check", "why" -> {
                if (args.length < 3) {
                    Msg.send(sender, Msg.error("Usage: <#FDE047>/perm check <player> <node></#FDE047>"));
                    return true;
                }
                PlayerProfile prof = findProfile(args[1]);
                if (prof == null) {
                    Msg.send(sender, Msg.error("Unknown player <#F8FAFC>" + args[1] + "</#F8FAFC>."));
                    return true;
                }
                String node = Perms.normalize(args[2]);
                boolean allowed = pm.effectiveAllows(prof.uuid(), node);
                Msg.send(sender, Msg.info("<#F8FAFC>" + prof.lastKnownName() + "</#F8FAFC> → <#F8FAFC>" + node + "</#F8FAFC>: "
                    + (allowed ? "<" + Palette.SUCCESS + "><bold>ALLOWED</bold></" + Palette.SUCCESS + ">" : "<" + Palette.DANGER + "><bold>DENIED</bold></" + Palette.DANGER + ">")
                    + " <#64748B>•</#64748B> <#94A3B8>" + pm.explain(prof.uuid(), node) + "</#94A3B8>"));
            }
            case "user" -> {
                if (args.length < 3) {
                    Msg.send(sender, Msg.error("Usage: <#FDE047>/perm user <player> <info|nodes|group|add|remove> ...</#FDE047>"));
                    return true;
                }
                PlayerProfile prof = findProfile(args[1]);
                if (prof == null) {
                    Msg.send(sender, Msg.error("Unknown player <#F8FAFC>" + args[1] + "</#F8FAFC> (must have joined at least once)."));
                    return true;
                }
                String action = args[2].toLowerCase(Locale.ROOT);
                switch (action) {
                    case "info" -> {
                        Map<String, Boolean> eff = pm.resolveEffectivePermissions(prof.uuid());
                        Msg.send(sender, Msg.info("<#F8FAFC><bold>" + prof.lastKnownName() + "</bold></#F8FAFC> — Group: <#FBBF24>"
                            + pm.getUserGroup(prof.uuid()) + "</#FBBF24> (" + eff.size() + " effective nodes)"));
                        for (Map.Entry<String, Boolean> e : eff.entrySet()) {
                            Msg.send(sender, "  <#64748B>•</#64748B> " + (e.getValue() ? "<#4ADE80>+" : "<#F87171>-") + e.getKey());
                        }
                    }
                    case "nodes" -> {
                        Map<String, Boolean> nodes = pm.userNodes(prof.uuid());
                        if (nodes.isEmpty()) {
                            Msg.send(sender, Msg.info("<#F8FAFC>" + prof.lastKnownName() + "</#F8FAFC> has no personal node."));
                            return true;
                        }
                        Msg.send(sender, Msg.info("Personal nodes of <#F8FAFC>" + prof.lastKnownName() + "</#F8FAFC>:"));
                        for (Map.Entry<String, Boolean> e : nodes.entrySet()) {
                            Msg.send(sender, "  <#64748B>•</#64748B> " + (e.getValue() ? "<#4ADE80>+" : "<#F87171>-") + e.getKey());
                        }
                    }
                    case "group", "setgroup" -> {
                        String gName = args.length >= 5 && args[3].equalsIgnoreCase("set") ? args[4] : (args.length >= 4 ? args[3] : null);
                        if (gName == null || !pm.setUserGroup(prof.uuid(), gName)) {
                            Msg.send(sender, Msg.error("Unknown group. Available: <#FDE047>" + String.join(", ", pm.groupNames()) + "</#FDE047>"));
                            return true;
                        }
                        Msg.send(sender, Msg.success("Set <#F8FAFC><bold>" + prof.lastKnownName() + "</bold></#F8FAFC>'s permission group to <#FBBF24>"
                            + gName.toLowerCase(Locale.ROOT) + "</#FBBF24>."));
                    }
                    case "add", "grant" -> {
                        if (args.length < 4) {
                            Msg.send(sender, Msg.error("Usage: <#FDE047>/perm user " + prof.lastKnownName() + " add <permission.node></#FDE047>"));
                            return true;
                        }
                        String node = Perms.normalize(args[3]);
                        pm.addUserPermission(prof.uuid(), args[3].startsWith("-") ? "-" + node : node);
                        Msg.send(sender, Msg.success("Granted <#4ADE80>" + node + "</#4ADE80> to <#F8FAFC>" + prof.lastKnownName() + "</#F8FAFC>."));
                    }
                    case "remove", "unset" -> {
                        if (args.length < 4) {
                            Msg.send(sender, Msg.error("Usage: <#FDE047>/perm user " + prof.lastKnownName() + " remove <permission.node></#FDE047>"));
                            return true;
                        }
                        String node = Perms.normalize(args[3]);
                        if (!pm.removeUserPermission(prof.uuid(), node) && !pm.removeUserPermission(prof.uuid(), "-" + node)) {
                            Msg.send(sender, Msg.warn("That node is not set on <#F8FAFC>" + prof.lastKnownName() + "</#F8FAFC>; it may come from the group."));
                            return true;
                        }
                        Msg.send(sender, Msg.info("Removed <#FDE047>" + node + "</#FDE047> from <#F8FAFC>" + prof.lastKnownName() + "</#F8FAFC>."));
                    }
                    default -> Msg.send(sender, Msg.error("Unknown action. Use <#FDE047>info</#FDE047>, <#FDE047>nodes</#FDE047>, <#FDE047>group</#FDE047>, <#FDE047>add</#FDE047>, or <#FDE047>remove</#FDE047>."));
                }
            }
            case "group" -> {
                if (args.length < 3) {
                    Msg.send(sender, Msg.error("Usage: <#FDE047>/perm group <group> <create|info|add|remove|inherit|users|delete> [value]</#FDE047>"));
                    return true;
                }
                String gid = args[1].toLowerCase(Locale.ROOT);
                String act = args[2].toLowerCase(Locale.ROOT);
                switch (act) {
                    case "create" -> {
                        if (pm.createGroup(gid)) {
                            Msg.send(sender, Msg.success("Created permission group <#FBBF24>" + gid + "</#FBBF24>."));
                        } else {
                            Msg.send(sender, Msg.warn("Group <#FBBF24>" + gid + "</#FBBF24> already exists."));
                        }
                    }
                    case "info" -> {
                        PermissionManager.GroupDefinition def = pm.getGroup(gid);
                        if (def == null) {
                            Msg.send(sender, Msg.error("Unknown group <#F8FAFC>" + gid + "</#F8FAFC>."));
                            return true;
                        }
                        Msg.send(sender, Msg.info("Group <#FBBF24><bold>" + def.id() + "</bold></#FBBF24> inherits: <#38BDF8>"
                            + def.parents() + "</#38BDF8> <#64748B>·</#64748B> <#94A3B8>" + def.permissions().size() + " node(s)</#94A3B8>"));
                        for (String n : def.permissions()) {
                            Msg.send(sender, "  <#64748B>•</#64748B> <#F8FAFC>" + n + "</#F8FAFC>");
                        }
                    }
                    case "add" -> {
                        if (args.length < 4 || !pm.addGroupPermission(gid, args[3])) {
                            Msg.send(sender, Msg.error("Usage: <#FDE047>/perm group <existingGroup> add <node></#FDE047>"));
                            return true;
                        }
                        Msg.send(sender, Msg.success("Added <#4ADE80>" + args[3] + "</#4ADE80> to group <#FBBF24>" + gid + "</#FBBF24>."));
                    }
                    case "remove" -> {
                        if (args.length < 4 || !pm.removeGroupPermission(gid, args[3])) {
                            Msg.send(sender, Msg.error("Node not found on group <#FBBF24>" + gid + "</#FBBF24>."));
                            return true;
                        }
                        Msg.send(sender, Msg.info("Removed <#FDE047>" + args[3] + "</#FDE047> from group <#FBBF24>" + gid + "</#FBBF24>."));
                    }
                    case "inherit", "parent" -> {
                        if (args.length < 5 || !(args[3].equalsIgnoreCase("add") || args[3].equalsIgnoreCase("remove"))) {
                            Msg.send(sender, Msg.error("Usage: <#FDE047>/perm group " + gid + " inherit <add|remove> <group></#FDE047>"));
                            return true;
                        }
                        boolean add = args[3].equalsIgnoreCase("add");
                        boolean ok = add ? pm.addGroupParent(gid, args[4]) : pm.removeGroupParent(gid, args[4]);
                        if (!ok) {
                            Msg.send(sender, Msg.error("Could not update the inheritance of <#FBBF24>" + gid + "</#FBBF24>."));
                            return true;
                        }
                        Msg.send(sender, Msg.success("Group <#FBBF24>" + gid + "</#FBBF24> now "
                            + (add ? "inherits <#38BDF8>" : "no longer inherits <#38BDF8>") + args[4].toLowerCase(Locale.ROOT) + "</#38BDF8>."));
                    }
                    case "users", "members" -> {
                        List<String> members = pm.usersInGroup(gid);
                        Msg.send(sender, Msg.info("Group <#FBBF24>" + gid + "</#FBBF24> has <#F8FAFC>" + members.size() + "</#F8FAFC> member(s)"
                            + (members.isEmpty() ? "." : ": <#F8FAFC>" + String.join("</#F8FAFC>, <#F8FAFC>", members) + "</#F8FAFC>")));
                    }
                    case "delete" -> {
                        String error = pm.deleteGroup(gid);
                        if (error != null) {
                            Msg.send(sender, Msg.error(error));
                            return true;
                        }
                        Msg.send(sender, Msg.success("Deleted permission group <#FBBF24>" + gid + "</#FBBF24>."));
                    }
                    default -> Msg.send(sender, Msg.error("Use <#FDE047>create</#FDE047>, <#FDE047>info</#FDE047>, <#FDE047>add</#FDE047>, <#FDE047>remove</#FDE047>, <#FDE047>inherit</#FDE047>, <#FDE047>users</#FDE047> or <#FDE047>delete</#FDE047>."));
                }
            }
            default -> Msg.send(sender, Msg.error("Unknown subcommand. Run <#FDE047>/perm help</#FDE047>."));
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
