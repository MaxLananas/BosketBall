package fr.bosketball.command;

import fr.bosketball.util.Palette;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.ArenaBuilder;
import fr.bosketball.arena.CourtGeometry;
import fr.bosketball.arena.MapEditorManager;
import fr.bosketball.game.GameModeType;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.ChatTag;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import fr.bosketball.model.TeamSide;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Msg;
import fr.bosketball.util.Perms;
import fr.bosketball.util.SmallCaps;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class BosketCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
        "admin", "map", "arena", "holo", "maintenance", "mutechat",
        "rank", "coins", "tags", "giveball", "forcestart", "stop", "save", "reload", "help", "displays",
        "reports", "crate", "status", "backup", "guild", "holofame", "quests", "achievements", "warn", "unwarn", "warnings",

        "play", "practice", "leave", "shop", "ranks", "guide", "friends", "party", "duel", "history",
        "settings", "stats", "top", "accept", "decline", "tuto", "ignore", "pack",

        "elo", "giveelo", "addelo", "profiler", "net", "gchat", "vanish", "freeze", "season", "stress", "audit"
    );

    private static final List<String> MAP_SUBS = List.of(
        "gui", "auto", "create", "edit", "wand", "pos1", "pos2", "barrier1", "barrier2",
        "ceiling", "hoop", "lines", "setspawn", "jumbotron", "preview", "info", "save", "help"
    );

    public static boolean isStaff(CommandSender sender) {
        return Perms.staff(sender);
    }

    public static String[] normalizeShortcutArgs(Command command, String label, String[] args) {
        String cmdName = command != null ? command.getName().toLowerCase(java.util.Locale.ROOT) : label.toLowerCase(java.util.Locale.ROOT);
        String mapped = switch (cmdName) {
            case "play" -> "play";
            case "practice" -> "practice";
            case "locker" -> "cosmetics";
            case "leave" -> "leave";
            case "shop", "store" -> "shop";
            case "ranks", "elo", "guide" -> "ranks";
            // Round 45: the [TAB] footer has been advertising /stats since Round 40, and there was
            // no such command: the only way to read your card was /bb stats, staff only.
            case "stats", "profil", "profile" -> "stats";
            case "friend", "friends", "f" -> "friends";
            case "party", "p" -> "party";
            case "duel" -> "duel";

            case "accept" -> "accept";
            case "decline" -> "decline";
            case "pc", "pchat" -> "pc";
            case "tuto", "tutorial" -> "tuto";
            case "quests", "quest", "objectives" -> "quests";
            case "season", "saison", "rankedseason" -> "season";
            case "achievements", "ach" -> "achievements";
            case "report" -> "report";
            case "crate" -> "crate";
            case "mail", "inbox", "mailbox" -> "mail";
            case "giveelo", "addelo" -> "giveelo";
            case "afk" -> "afk";
            case "guild", "team", "gchat", "gc", "g" -> "gchat";
            case "ignore" -> "ignore";
            case "reports" -> "reports";
            case "warn" -> "warn";
            case "unwarn" -> "unwarn";
            case "warnings", "warns" -> "warnings";
            case "status" -> "status";
            case "backup" -> "backup";
            default -> null;
        };
        if (mapped == null) {
            return args;
        }
        String[] prefixed = new String[args.length + 1];
        prefixed[0] = mapped;
        System.arraycopy(args, 0, prefixed, 1, args.length);
        return prefixed;
    }

    private final BosketBallPlugin plugin;

    public BosketCommand(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] rawArgs) {
        String rootName = command != null ? command.getName().toLowerCase(java.util.Locale.ROOT) : label.toLowerCase(java.util.Locale.ROOT);
        if (rootName.equals("bosketball") && sender instanceof Player p && !isStaff(p)) {
            Msg.sendAlert(p, "Operator command only. Click <click:run_command:'/help'><" + Palette.ICE + "><bold>[ /help]</bold></" + Palette.ICE + "></click> for player commands.");
            return true;
        }

        String[] args = normalizeShortcutArgs(command, label, rawArgs);
        if (args.length == 0) {
            if (!rootName.equals("bosketball")) {
                sendHelp(sender);
                return true;
            }
            if (!(sender instanceof Player p)) {
                Msg.send(sender, Msg.info("Use <white>/bb help</white> from the console."));
                return true;
            }
            plugin.guiMenu().openServerAdminPanel(p);
            return true;
        }
        if (args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player p)) {
                Msg.send(sender, Msg.info("Use <white>/bb help</white> from the console."));
                return true;
            }
            plugin.guiMenu().openServerAdminPanel(p);
            return true;
        }

        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(sender);

            case "discord" -> {
                Msg.sendDiscordCard(sender, plugin.settings().links().discord());
            }

            // Round 27: the Discord webhook used to fail silently — this exposes the real state.
            case "webhook", "discordtest" -> {
                if (!fr.bosketball.util.Perms.admin(sender)) {
                    Msg.send(sender, Msg.error("Staff only."));
                    return false;
                }
                if (plugin.integration() == null) {
                    Msg.send(sender, Msg.error("Discord integration is not ready yet."));
                    return false;
                }
                String url = plugin.settings().discord().webhookUrl();
                String masked = url == null || url.isBlank()
                    ? "<" + Palette.DANGER + ">not configured</" + Palette.DANGER + ">"
                    : "<white>" + fr.bosketball.util.Msg.escapeMini(
                        url.replaceAll("/([^/]{4})[^/]*$", "/$1…")) + "</white>";
                Msg.sendFramedCard(sender, "DISCORD • WEBHOOK", java.util.List.of(
                    "<gray>État :</gray> " + plugin.integration().webhook().statusLine(),
                    "<gray>URL :</gray> " + masked,
                    "<gray>Enabled (config):</gray> <white>" + plugin.settings().discord().enabled() + "</white>",
                    "<dark_gray>/bb webhook test</dark_gray> <gray>envoie un message de test.</gray>"
                ));
                if (args.length > 1 && args[1].equalsIgnoreCase("test")) {
                    plugin.integration().webhook().sendCard(0xFF8C1A,
                        "## ✔ BosketBall — test webhook",
                        java.util.List.of("<gray>Si tu vois ce message, le webhook fonctionne.</gray>"),
                        "BosketBall", null);
                    Msg.send(sender, Msg.info("Test sent — check the Discord channel."));
                }
            }


            case "shop", "store" -> {
                if (sender instanceof Player p) {
                    if (plugin.gameManager().isActivelyPlayingCompetitiveMatch(p.getUniqueId())) {
                        Msg.sendAlert(p, "You cannot open the Shop during a live ranked match.");
                        return true;
                    }
                    if (plugin.cinematicCamera() != null && plugin.cinematicCamera().isWatching(p.getUniqueId())) {
                        plugin.cinematicCamera().skipIfWatching(p);
                    }
                    if (args.length >= 2 && args[1].equalsIgnoreCase("search")) {
                        if (args.length < 3 || args[2].equalsIgnoreCase("clear")) {
                            plugin.guiMenu().setShopSearch(p.getUniqueId(), "");
                            Msg.send(p, Msg.success("Shop search cleared."));
                        } else {
                            String query = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                            plugin.guiMenu().setShopSearch(p.getUniqueId(), query);
                            Msg.send(p, Msg.info("Shop filtered by <white>" + query + "</white> <gray>(name or price)</gray>."));
                        }
                        plugin.guiMenu().openShop(p);
                        return true;
                    }
                    if (args.length >= 3 && args[1].equalsIgnoreCase("sort")) {
                        var sort = fr.bosketball.ui.GuiMenuManager.ShopSort.byName(args[2]);
                        plugin.guiMenu().setShopSort(p.getUniqueId(), sort);
                        Msg.send(p, Msg.success("Shop sort: <white>" + sort.label() + "</white>."));
                        plugin.guiMenu().openShop(p);
                        return true;
                    }
                    if (args.length >= 2) {
                        switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
                            case "balls", "basketballs" -> plugin.guiMenu().openShop(p, fr.bosketball.ui.GuiMenuManager.ShopCategory.BALLS, 0);
                            case "dunks" -> plugin.guiMenu().openShop(p, fr.bosketball.ui.GuiMenuManager.ShopCategory.DUNKS, 0);
                            case "tags" -> plugin.guiMenu().openShop(p, fr.bosketball.ui.GuiMenuManager.ShopCategory.TAGS, 0);
                            case "cosmetics", "locker" -> plugin.guiMenu().openCosmetics(p);
                            default -> plugin.guiMenu().openShop(p);
                        }
                    } else {
                        plugin.guiMenu().openShop(p);
                    }
                }
            }

            case "coins", "coin", "balance", "bal" -> handleCoinsCommand(sender, args);

            case "ranks", "elo", "guide" -> {

                if (args.length >= 2 && (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("add")
                    || args[1].equalsIgnoreCase("take") || args[1].equalsIgnoreCase("remove")
                    || args[1].equalsIgnoreCase("set"))) {
                    handleEloAdminCommand(sender, args);
                    return true;
                }
                if (sender instanceof Player p) {
                    if (plugin.cinematicCamera() != null && plugin.cinematicCamera().isWatching(p.getUniqueId())) {
                        plugin.cinematicCamera().skipIfWatching(p);
                    }
                    plugin.guiMenu().openRanksGuide(p);
                }
            }

            case "tags", "tag" -> handleTagCommand(sender, args);

            case "giveelo", "addelo" -> {
                // /bb giveelo <player> <amount>            -> /bb elo give <player> <amount>
                // /bb giveelo give|take|set <player> <n>   -> kept as typed (no duplicated operation)
                java.util.List<String> rewritten = new java.util.ArrayList<>();
                rewritten.add("elo");
                String head = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
                boolean explicitOp = head.equals("give") || head.equals("add") || head.equals("take")
                    || head.equals("remove") || head.equals("set");
                if (!explicitOp) {
                    rewritten.add(args[0].toLowerCase(java.util.Locale.ROOT).startsWith("add") ? "add" : "give");
                }
                for (int i = 1; i < args.length; i++) {
                    rewritten.add(args[i]);
                }
                handleEloAdminCommand(sender, rewritten.toArray(new String[0]));
            }

            case "history" -> {
                if (sender instanceof Player p) {
                    plugin.guiMenu().openMatchHistory(p);
                }
            }

            case "settings", "options" -> {
                if (sender instanceof Player p) {
                    plugin.guiMenu().openSettings(p);
                }
            }

            case "friends", "friend", "f" -> {
                if (!(sender instanceof Player p)) {
                    return true;
                }
                if (args.length < 2) {
                    plugin.guiMenu().openFriendsRich(p);
                    return true;
                }
                if (args[1].equalsIgnoreCase("remove") && args.length >= 3) {
                    plugin.socialManager().removeFriend(p, args[2]);
                    return true;
                }
                if (args[1].equalsIgnoreCase("decline") && args.length >= 3) {
                    plugin.socialManager().declineFriendRequest(p, args[2]);
                    return true;
                }
                String targetName = args[1].equalsIgnoreCase("add") && args.length >= 3 ? args[2] : args[1];
                Player target = Bukkit.getPlayer(targetName);
                if (target == null) {
                    Msg.send(p, Msg.error("Player offline: " + targetName));
                    return true;
                }
                plugin.socialManager().sendOrAcceptFriendRequest(p, target);
            }

            case "party", "group", "p" -> {
                if (!(sender instanceof Player p)) {
                    return true;
                }
                if (args.length < 2) {
                    plugin.guiMenu().openPartyMenu(p);
                    return true;
                }
                String act = args[1].toLowerCase(java.util.Locale.ROOT);
                if (act.equals("leave") || act.equals("quit")) {
                    plugin.socialManager().leaveParty(p);
                } else if (act.equals("accept") || act.equals("join")) {
                    plugin.socialManager().acceptPartyInvite(p, args.length >= 3 ? args[2] : null);
                } else if (act.equals("name") || act.equals("rename") || act.equals("team")) {
                    if (args.length < 3) {
                        plugin.socialManager().beginChatPrompt(p, fr.bosketball.social.SocialPartyManager.ChatPromptType.PARTY_TEAM_NAME);
                        return true;
                    }
                    String rawTeamName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    plugin.socialManager().setPartyTeamName(p, rawTeamName);
                } else if (act.equals("kick") && args.length >= 3) {
                    Player target = Bukkit.getPlayer(args[2]);
                    if (target != null) {
                        plugin.socialManager().kickFromParty(p, target.getUniqueId());
                    }
                } else if (act.equals("promote") && args.length >= 3) {
                    Player target = Bukkit.getPlayer(args[2]);
                    if (target != null) {
                        plugin.socialManager().promotePartyLeader(p, target.getUniqueId());
                    }
                } else {
                    String tName = act.equals("invite") && args.length >= 3 ? args[2] : args[1];
                    Player target = Bukkit.getPlayer(tName);
                    if (target == null) {
                        Msg.send(p, Msg.error("Player not found: " + tName));
                        return true;
                    }
                    plugin.socialManager().inviteToParty(p, target);
                }
            }

            case "mail", "inbox", "mailbox" -> {
                if (sender instanceof Player p) {
                    PlayerProfile prof = plugin.statsManager().getOrCreate(p);
                    prof.setUnreadMailCount(0);
                    plugin.statsManager().saveProfileAsync(prof);
                    plugin.guiMenu().openMailbox(p);
                } else {
                    Msg.send(sender, Msg.info("The mailbox is an in-game GUI: run <white>/mail</white> from a player account."));
                }
            }

            case "welcome" -> {
                if (sender instanceof Player p) {
                    PlayerProfile prof = plugin.statsManager().getOrCreate(p);
                    var welcome = plugin.settings().welcome();
                    var links = plugin.settings().links();
                    Msg.sendIllustratedWelcomeCard(
                        p,
                        links.website(),
                        links.shop(),
                        links.discord(),
                        welcome.headline(),
                        welcome.subtitle(),
                        prof.unreadMailCount()
                    );
                }
            }

            case "hotbar" -> {
                if (sender instanceof Player p && !plugin.gameManager().isInMatch(p.getUniqueId())) {
                    BallSkin.equipLobbyHotbarIfEmpty(p, true);
                    Msg.send(p, Msg.success("BosketBall lobby hotbar equipped."));
                }
            }

            case "play", "join" -> {
                if (!(sender instanceof Player p)) {
                    return true;
                }
                if (plugin.gameManager().isActivelyPlayingCompetitiveMatch(p.getUniqueId())) {
                    Msg.sendAlert(p, "You are already in a live ranked match! Use <yellow>/leave</yellow> if you want to forfeit.");
                    return true;
                }
                if (plugin.cinematicCamera() != null && plugin.cinematicCamera().isWatching(p.getUniqueId())) {
                    plugin.cinematicCamera().skipIfWatching(p);
                }
                if (args.length == 1) {
                    plugin.guiMenu().openPlaySelector(p);
                    return true;
                }

                boolean casualRequested = "casual".equalsIgnoreCase(args[1])
                    || (args.length >= 3 && "casual".equalsIgnoreCase(args[2]));
                String modeArg = "casual".equalsIgnoreCase(args[1]) && args.length >= 3 ? args[2] : args[1];
                GameModeType mode = GameModeType.byIdOrNull(modeArg);
                if (mode != null && casualRequested) {
                    mode = mode.casualTwin();
                    plugin.gameManager().joinQueue(p, mode);
                    return true;
                }
                if (mode != null) {
                    String arenaId = (isStaff(p) && args.length >= 3) ? args[2] : null;
                    if (arenaId != null) {
                        plugin.gameManager().quickJoin(p, mode, arenaId);
                    } else {
                        plugin.gameManager().joinQueue(p, mode);
                    }
                } else if (isStaff(p) && plugin.arenaManager().getArena(args[1]) != null) {
                    plugin.gameManager().quickJoin(p, GameModeType.STREET_2V2, args[1]);
                } else {
                    Msg.sendAlert(p, "Unknown mode! Choose: <white>1v1</white>, <white>2v2</white>, <white>3v3</white>, <white>5v5</white>, <white>3pt</white>, or <white>practice</white>.");
                }
            }

            case "leave", "quit" -> {
                if (sender instanceof Player p) {
                    if (plugin.cinematicCamera() != null && plugin.cinematicCamera().isWatching(p.getUniqueId())) {
                        plugin.cinematicCamera().stopWatching(p, true);
                    }
                    boolean inMatch = plugin.gameManager().isInMatch(p.getUniqueId());
                    boolean queued = plugin.gameManager().isQueued(p.getUniqueId());
                    if (inMatch || queued) {
                        plugin.gameManager().leaveAll(p, true);
                    } else {
                        Msg.send(p, Msg.info("You are not in a queue or a match."));
                    }
                    if (!inMatch && plugin.protectionManager() != null) {
                        org.bukkit.Location spawn = plugin.protectionManager().spawnLocation();
                        if (spawn != null && (spawn.getWorld() == null || !spawn.getWorld().equals(p.getWorld())
                            || spawn.distanceSquared(p.getLocation()) > 4.0)) {
                            p.teleport(spawn);
                        }
                    }
                }
            }

            case "accept", "ready" -> {
                if (sender instanceof Player p) {
                    plugin.gameManager().matchmaking().accept(p);
                }
            }

            case "decline", "dodge" -> {
                if (sender instanceof Player p) {
                    plugin.gameManager().matchmaking().decline(p);
                }
            }

            case "pc", "pchat", "party chat" -> {
                if (sender instanceof Player p) {
                    boolean on = plugin.gameManager().togglePartyChat(p);
                    Msg.send(p, on
                        ? Msg.success("Party chat <white><bold>ON</bold></white> — only your party sees your messages.")
                        : Msg.info("Party chat <white><bold>OFF</bold></white> — back to global chat."));
                }
            }

            case "tuto", "tutorial" -> {
                if (sender instanceof Player p) {
                    handleTutorial(p, args);
                }
            }

            case "quests", "quest", "objectives" -> {
                if (sender instanceof Player p) {
                    plugin.guiMenu().openQuests(p);
                }
            }

            case "season", "saison" -> handleSeasonCommand(sender, args);

            case "achievements", "ach" -> {
                if (sender instanceof Player p) {
                    plugin.guiMenu().openAchievements(p, null, 0);
                }
            }

            case "report" -> handleReportCommand(sender, args);

            case "ignore" -> handleIgnoreCommand(sender, args);

            case "guild", "team" -> handleGuildCommand(sender, args);

            case "gchat", "gc", "g" -> handleGuildChatCommand(sender, args);

            case "vanish" -> {
                if (!(sender instanceof Player p) || !Perms.check(sender, Perms.STAFF_VANISH, "vanish")) {
                    return true;
                }
                var tools = plugin.staffTools();
                if (tools != null) {
                    tools.toggleVanish(p);
                }
            }

            case "freeze", "unfreeze" -> {
                if (!Perms.check(sender, Perms.STAFF_FREEZE, "freeze players")) {
                    return true;
                }
                var tools = plugin.staffTools();
                if (tools == null) {
                    return true;
                }
                if (args.length < 2 || args[1].equalsIgnoreCase("all") || args[1].equalsIgnoreCase("match")) {
                    int released = tools.unfreezeAll();
                    Msg.send(sender, released > 0
                        ? Msg.success("Released <white>" + released + "</white> frozen player(s).")
                        : Msg.info("Nobody was frozen."));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    Msg.send(sender, Msg.error("Player not online: <white>" + args[1] + "</white>."));
                    return true;
                }
                tools.toggleFreeze(target, sender instanceof Player p ? p : null);
            }

            case "freezematch" -> {
                if (!Perms.check(sender, Perms.STAFF_FREEZE, "freeze a whole match")) {
                    return true;
                }
                var tools = plugin.staffTools();
                if (tools == null) {
                    return true;
                }
                var session = sender instanceof Player p ? plugin.gameManager().getSessionOf(p.getUniqueId()) : null;
                if (session == null) {
                    Msg.send(sender, Msg.warn("Join the match you want to freeze first."));
                    return true;
                }
                int count = tools.freezeMatch(sender instanceof Player p ? p : null, session.onlineParticipants());
                Msg.send(sender, Msg.success("Froze <white>" + count + "</white> player(s) — the score and clock keep running."));
            }

            case "warn" -> handleWarnCommand(sender, args);
            case "unwarn" -> handleUnwarnCommand(sender, args);
            case "warnings", "warns" -> {
                if (args.length >= 2) {
                    handleWarningsCommand(sender, args);
                } else if (sender instanceof Player p && isStaff(p)) {
                    plugin.guiMenu().openWarningsPanel(p, null);
                } else {
                    Msg.sendAlert(sender, "Warnings are restricted to staff.");
                }
            }

            case "reports" -> {
                if (sender instanceof Player p && Perms.has(p, Perms.STAFF_REPORTS)) {
                    plugin.guiMenu().openReportsPanel(p);
                } else {
                    sender.sendMessage(Msg.parse(Msg.error("You must be an operator to open the reports panel.")));
                }
            }

            case "status" -> sendServerStatus(sender);

            case "afk" -> {
                if (!(sender instanceof Player p)) {
                    Msg.send(sender, Msg.info("Only players can go AFK."));
                    return true;
                }
                boolean nowAfk = plugin.socialService() != null && plugin.socialService().toggleAfk(p.getUniqueId());
                if (nowAfk) {
                    Msg.send(p, Msg.info("<" + Palette.DIM + ">◌ You are now <white><bold>AFK</bold></white>. Type <white>/afk</white> or move to come back.</" + Palette.DIM + ">"));
                    Msg.rail(p, "<" + Palette.DIM + "><bold>◌ AFK</bold></" + Palette.DIM + "> <gray>— move or use /afk to return</gray>");
                    Msg.playPersonalPackSound(p, "ui.click", 0.6f, 0.9f);
                } else {
                    Msg.send(p, Msg.success("<" + Palette.SUCCESS + ">● Welcome back — you are no longer AFK.</" + Palette.SUCCESS + ">"));
                    Msg.rail(p, "<" + Palette.SUCCESS + "><bold>● Back</bold></" + Palette.SUCCESS + ">");
                    Msg.playPersonalPackSound(p, "ui.click", 0.6f, 1.3f);
                }
            }

            case "backup" -> {
                if (!Perms.check(sender, Perms.BACKUP, "trigger a backup")) {
                    return true;
                }
                try {
                    var file = plugin.integration().storage().backupNow();
                    sender.sendMessage(Msg.parse(Msg.success("Backup written: <white>" + file.getName() + "</white>")));
                } catch (Exception ex) {
                    sender.sendMessage(Msg.parse(Msg.error("Backup failed: " + ex.getMessage())));
                }
            }

            case "crate" -> handleCrateCommand(sender, args);

            case "bot", "bots", "ai" -> handleBotCommand(sender, args);

            case "practice" -> {
                if (sender instanceof Player p) {
                    String arenaArg = (isStaff(p) && args.length >= 2) ? args[1] : null;
                    plugin.gameManager().quickJoin(p, GameModeType.PRACTICE, arenaArg);
                }
            }

            case "defender", "sparring" -> {
                if (sender instanceof Player p) {
                    MatchSession s = plugin.gameManager().getSessionOf(p.getUniqueId());
                    if (s == null) {
                        Msg.send(p, Msg.info("Enter Practice first with <white>/practice</white>."));
                    } else {
                        s.toggleSparringBot(p);
                    }
                }
            }

            case "3pt", "contest" -> {
                if (sender instanceof Player p) {
                    plugin.gameManager().quickJoin(p, GameModeType.THREE_POINT_CONTEST, args.length >= 2 ? args[1] : null);
                }
            }

            case "duel" -> {
                if (!(sender instanceof Player p)) {
                    return true;
                }
                if (args.length < 2) {
                    Msg.sendSocial(p, "Usage: <yellow>/duel <player></yellow> <dark_gray>(or Shift+Right-Click any player in Lobby!)</dark_gray>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    Msg.sendAlert(p, "Player not online: <white>" + args[1] + "</white>");
                    return true;
                }
                plugin.gameManager().sendOrAcceptDuel(p, target);
            }

            case "locker", "cosmetics" -> {
                if (sender instanceof Player p) {
                    plugin.guiMenu().openCosmetics(p);
                }
            }

            case "top", "leaderboard" -> {
                if (sender instanceof Player p) {
                    StatsManager.LeaderboardCategory cat = StatsManager.LeaderboardCategory.ELO;
                    if (args.length >= 2 && args[1].equalsIgnoreCase("coins")) {
                        cat = StatsManager.LeaderboardCategory.COINS;
                    }
                    plugin.guiMenu().openLeaderboards(p, cat);
                } else {
                    List<PlayerProfile> top = plugin.statsManager().topBy(StatsManager.LeaderboardCategory.ELO, 10);
                    sender.sendMessage("=== TOP 10 BOSKETBALL ELO ===");
                    for (int i = 0; i < top.size(); i++) {
                        sender.sendMessage("#" + (i + 1) + " " + top.get(i).lastKnownName()
                            + " - " + top.get(i).elo() + " ELO (" + top.get(i).coins() + " Coins)");
                    }
                }
            }

            case "stats", "profile" -> {
                Player target = (args.length >= 2) ? Bukkit.getPlayer(args[1]) : (sender instanceof Player p ? p : null);
                if (target == null) {
                    Msg.send(sender, Msg.error("Player not found."));
                    return true;
                }
                PlayerProfile prof = plugin.statsManager().getOrCreate(target);
                // Round 45: one accent (the ELO figure), five labelled rows, no colour salad.
                String lab = "<" + Palette.MUTED + ">";
                String elab = "</" + Palette.MUTED + ">";
                String val = "<" + Palette.TEXT + ">";
                String eval = "</" + Palette.TEXT + ">";
                Msg.sendFramedCard(sender, "CARD \u2022 " + target.getName(), List.of(
                    " " + lab + "Rank" + elab + "     <!shadow><white>" + prof.effectiveRank().pillGlyph()
                        + "</white></!shadow> <" + Palette.TEXT + ">" + prof.effectiveRank().plainDisplayName()
                        + "</" + Palette.TEXT + "> <" + Palette.DIM + ">\u00b7</" + Palette.DIM + "> <"
                        + Palette.GOLD + ">" + prof.elo() + " ELO</" + Palette.GOLD + ">",
                    " " + lab + "Record" + elab + "   " + val + prof.wins() + " W"
                        + eval + " <" + Palette.DIM + ">/</" + Palette.DIM + "> " + val + prof.losses()
                        + " L" + eval + " <" + Palette.DIM + ">\u00b7</" + Palette.DIM + "> " + val
                        + Math.round(prof.winRatePercent()) + "%" + eval,
                    " " + lab + "Career" + elab + "   " + val + prof.points() + " PTS"
                        + eval + " <" + Palette.DIM + ">\u00b7</" + Palette.DIM + "> " + val
                        + prof.dunks() + " DUNKS" + eval + " <" + Palette.DIM + ">\u00b7</" + Palette.DIM
                        + "> " + val + prof.assists() + " ASSISTS" + eval,
                    " " + lab + "Shooting" + elab + " " + val + prof.threePointersMade() + "/"
                        + prof.threePointersAttempted() + " 3PT" + eval + " <" + Palette.DIM
                        + ">\u00b7</" + Palette.DIM + "> " + val + String.format(java.util.Locale.ROOT, "%.0f%%", prof.fieldGoalPercentage())
                        + " FG" + eval,
                    " " + lab + "Playtime" + elab + " " + val + prof.formattedPlayTime() + eval
                ));
            }

            case "profiler", "perf", "ticks" -> {
                if (!Perms.staff(sender)) {
                    Msg.send(sender, Msg.error("Staff only."));
                    return true;
                }
                if (args.length >= 2) {
                    switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
                        case "reset" -> {
                            fr.bosketball.util.TickProfiler.reset();
                            Msg.send(sender, Msg.success("Profiler counters reset."));
                            return true;
                        }
                        case "off" -> {
                            fr.bosketball.util.TickProfiler.setEnabled(false);
                            Msg.send(sender, Msg.warn("Tick profiler disabled — zero overhead from now on."));
                            return true;
                        }
                        case "on" -> {
                            fr.bosketball.util.TickProfiler.setEnabled(true);
                            Msg.send(sender, Msg.success("Tick profiler enabled."));
                            return true;
                        }
                        case "cache" -> {
                            Msg.send(sender, Msg.info("Msg.parse cache: <white>"
                                + Msg.parseCacheSize() + "</white> entries."));
                            return true;
                        }
                        default -> { }
                    }
                }
                java.util.List<String> lines = new java.util.ArrayList<>(
                    fr.bosketball.util.TickProfiler.report(org.bukkit.Bukkit.getTPS()[0]));
                lines.add(" <dark_gray>•</dark_gray> <gray>Msg.parse cache:</gray> <white>"
                    + Msg.parseCacheSize() + "</white> <gray>entries</gray>");
                Msg.sendFramedCard(sender, "Tick profiler", lines);
            }

            case "net", "ping", "latency" -> {
                if (!Perms.staff(sender)) {
                    Msg.send(sender, Msg.error("Staff only."));
                    return true;
                }
                Player target;
                if (args.length >= 2) {
                    target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        Msg.send(sender, Msg.error("Player not online: <white>" + args[1] + "</white>. "
                            + "Use <white>/bb net</white> for yourself."));
                        return true;
                    }
                } else if (sender instanceof Player self) {
                    target = self;
                } else {
                    Msg.send(sender, Msg.info("Usage: <white>/bb net [player]</white>"));
                    return true;
                }
                fr.bosketball.server.PingDiagnostics diagnostics = plugin.pingDiagnostics();
                if (diagnostics == null || !target.isOnline()) {
                    Msg.send(sender, Msg.error("Latency diagnostics are not ready yet — try again in a second."));
                    return true;
                }
                Msg.sendFramedCard(sender, "Network • " + target.getName(), diagnostics.card(target));
            }

            case "pack" -> {
                if (args.length >= 2 && Perms.staff(sender)) {
                    if (args[1].equalsIgnoreCase("test")) {
                        if (!(sender instanceof Player tester)) {
                            Msg.send(sender, Msg.info("Run <white>/bb pack test</white> in game."));
                            return true;
                        }
                        Msg.send(sender, Msg.info("Testing every resource-pack route from the server…"));
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            java.util.List<String> report = plugin.resourcePackHost().testDownload(tester);
                            Bukkit.getScheduler().runTask(plugin,
                                () -> Msg.sendFramedCard(tester, "Resource pack routes", report));
                        });
                        return true;
                    }
                    if (args[1].equalsIgnoreCase("push")) {
                        String scope = args.length >= 3 ? args[2] : "all";
                        if (scope.equalsIgnoreCase("all")) {
                            int sent = 0;
                            for (Player online : Bukkit.getOnlinePlayers()) {
                                plugin.resourcePackHost().sendPackTo(online);
                                sent++;
                            }
                            Msg.send(sender, Msg.success("Resource pack re-pushed to <white>" + sent + "</white> player(s)."));
                        } else {
                            Player target = Bukkit.getPlayerExact(scope);
                            if (target == null) {
                                Msg.send(sender, Msg.error("Player not online: <white>" + scope + "</white>."));
                                return true;
                            }
                            plugin.resourcePackHost().sendPackTo(target);
                            Msg.send(sender, Msg.success("Resource pack re-pushed to <white>" + target.getName() + "</white>."));
                        }
                        return true;
                    }
                }
                if (sender instanceof Player p) {
                    plugin.resourcePackHost().sendPackTo(p);
                }
            }

            case "skin" -> {
                if (!(sender instanceof Player p) || args.length < 2) {
                    Msg.send(sender, Msg.info("Usage: <white>/bb skin <classic|money|neon|fire></white>"));
                    return true;
                }
                BallSkin skin = BallSkin.byNameOrDefault(args[1], null);
                if (skin == null) {
                    Msg.send(p, Msg.error("Unknown basketball skin. Choices: classic, money, neon, fire."));
                    return true;
                }
                PlayerProfile prof = plugin.statsManager().getOrCreate(p);
                if (!prof.hasUnlockedBall(skin) && !Perms.has(p, Perms.ITEMS)) {
                    Msg.send(p, Msg.error("Unlock <white>" + skin.plainName() + "</white> (<gold>" + skin.coinPrice() + " Coins</gold>) in <yellow>/bb shop cosmetics</yellow>!"));
                    return true;
                }
                prof.setSelectedBallSkin(skin);
                plugin.statsManager().saveProfileAsync(prof);
                Msg.send(p, Msg.success("Basketball equipped: " + skin.displayMiniMessage()));
            }

            case "dunk" -> {
                if (!(sender instanceof Player p) || args.length < 2) {
                    Msg.send(sender, Msg.info("Usage: <white>/bb dunk <tomahawk|windmill|spin_360|reverse_baseline|meteor_jam></white>"));
                    return true;
                }
                DunkStyle style = DunkStyle.byNameOrDefault(args[1], null);
                if (style == null) {
                    Msg.send(p, Msg.error("Unknown dunk style."));
                    return true;
                }
                PlayerProfile prof = plugin.statsManager().getOrCreate(p);
                if (!prof.hasUnlockedDunk(style) && !Perms.has(p, Perms.ITEMS)) {
                    Msg.send(p, Msg.error("Unlock <white>" + style.displayName() + "</white> (<gold>" + style.coinPrice() + " Coins</gold>) in <yellow>/bb shop cosmetics</yellow>!"));
                    return true;
                }
                prof.setSelectedDunkStyle(style);
                plugin.statsManager().saveProfileAsync(prof);
                Msg.send(p, Msg.success("Dunk Style equipped: <gold>" + style.displayName() + "</gold>"));
            }

            case "admin", "panel" -> {
                if (!Perms.check(sender, Perms.ADMIN, "open the control center")) {
                    return true;
                }
                if (sender instanceof Player p) {
                    plugin.guiMenu().openServerAdminPanel(p);
                } else {
                    Msg.send(sender, Msg.info("Online: " + Bukkit.getOnlinePlayers().size()
                        + " | Active Matches: " + plugin.gameManager().activePlayerCount()
                        + " | Maintenance: " + plugin.settings().motd().maintenanceMode()));
                }
            }

            case "maintenance", "maint" -> {
                if (!Perms.check(sender, Perms.MAINTENANCE, "toggle maintenance mode")) {
                    return true;
                }
                boolean targetState = args.length >= 2
                    ? (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true") || args[1].equalsIgnoreCase("enable"))
                    : !plugin.settings().motd().maintenanceMode();
                plugin.setMaintenanceModeLive(targetState);
                Msg.send(sender, Msg.success("Maintenance Mode: " + (targetState ? "<" + Palette.DANGER + "><bold>ON</bold></" + Palette.DANGER + ">" : "<" + Palette.SUCCESS + "><bold>OFF</bold></" + Palette.SUCCESS + ">")));
            }

            case "mutechat" -> {
                if (!Perms.check(sender, Perms.MUTECHAT, "mute the global chat")) {
                    return true;
                }
                boolean muted = !plugin.isGlobalChatMuted();
                plugin.setGlobalChatMuted(muted);
                Bukkit.broadcast(Msg.prefixed(muted
                    ? "<" + Palette.DANGER + "><bold>Global chat has been muted by staff.</bold></" + Palette.DANGER + ">"
                    : "<" + Palette.SUCCESS + "><bold>Global chat has been unmuted!</bold></" + Palette.SUCCESS + ">"));
            }

            case "holo", "hologram" -> {
                if (!Perms.check(sender, Perms.HOLO, "place holograms")) {
                    return true;
                }
                if (!(sender instanceof Player p)) {
                    Msg.send(sender, Msg.info("Holograms can only be placed in-game."));
                    return true;
                }
                if (args.length < 2) {
                    Msg.send(p, Msg.info("Usage: <yellow>/bb holo <elo|points|coins|play|shop|list|remove|clear></yellow>"));
                    return true;
                }
                String hAct = args[1].toLowerCase(java.util.Locale.ROOT);
                if ((hAct.equals("create") || hAct.equals("spawn") || hAct.equals("place")) && args.length >= 3) {
                    hAct = args[2].toLowerCase(java.util.Locale.ROOT);
                }
                if (hAct.equals("list")) {
                    Msg.send(p, Msg.info("Active Lobby Hologram Stations & Wall Boards: <white>"
                        + plugin.lobbyHolograms().stationCount() + "</white>."));
                    return true;
                }
                if (hAct.equals("remove") || hAct.equals("delete")) {
                    if (plugin.lobbyHolograms().removeNearest(p.getLocation(), 8.0)) {
                        Msg.send(p, Msg.success("Removed the nearest 3D Hologram Station / Wall Board."));
                    } else {
                        Msg.sendAlert(p, "No 3D Hologram Station found within 8 blocks.");
                    }
                    return true;
                }
                if (hAct.equals("clear")) {
                    int n = plugin.lobbyHolograms().clearAll();
                    Msg.send(p, Msg.success("Cleared <white>" + n + "</white> 3D Hologram Stations."));
                    return true;
                }
                var kind = fr.bosketball.ui.LobbyHologramManager.StationKind.fromString(hAct);
                if (kind == null) {
                    Msg.sendAlert(p, "Unknown hologram kind. Choose: <yellow>elo, points, coins, play, shop, list, remove, clear</yellow>");
                    return true;
                }
                plugin.lobbyHolograms().createStationAt(p.getLocation(), kind);
                Msg.send(p, Msg.success("Placed <white><bold>" + kind.name() + "</bold></white> "
                    + (kind.isLeaderboard() ? "Interactive Fixed Wall Leaderboard" : "Lobby Hologram Station")
                    + " at your location!"));
            }

            case "map" -> handleCustomMapArchitect(sender, args);

            case "arena" -> handleArenaAdmin(sender, args);

            case "rank" -> handleRankAdmin(sender, args);

            case "giveball" -> {
                if (!Perms.check(sender, Perms.ITEMS, "spawn admin items")) {
                    return true;
                }
                if (!(sender instanceof Player p)) {
                    Msg.send(sender, Msg.info("Admin items can only be given in-game."));
                    return true;
                }
                BallSkin skin = args.length >= 2 ? BallSkin.byNameOrDefault(args[1], BallSkin.CLASSIC) : BallSkin.CLASSIC;
                p.getInventory().addItem(skin.createBallItem(false));
                p.getInventory().addItem(BallSkin.createArchitectWandItem("custom"));
                p.getInventory().addItem(BallSkin.createCustomModelItem(Material.GOLDEN_CARROT, "trophy_mvp", "<gold><bold> MVP Trophy</bold></gold>", List.of()));
                p.getInventory().addItem(BallSkin.createCustomModelItem(Material.IRON_NUGGET, "referee_whistle", "<aqua><bold> Referee Whistle</bold></aqua>", List.of()));
                p.getInventory().addItem(BallSkin.createCustomModelItem(Material.LEATHER_BOOTS, "sneakers_air", "<red><bold> Air Bosket</bold></red>", List.of()));
                p.getInventory().addItem(BallSkin.createPlaybookItem());
                Msg.send(p, Msg.success("BosketBall admin items & Architect Wand added to your inventory."));
            }

            case "forcestart" -> {
                if (!(sender instanceof Player p)) {
                    return true;
                }
                boolean isAdmin = Perms.has(sender, Perms.MATCH);
                GameModeType qMode = plugin.gameManager().getQueuedMode(p.getUniqueId());
                if (qMode != null) {
                    int queued = plugin.gameManager().queuedCount(qMode);
                    if (!isAdmin && queued < 2) {
                        Msg.send(p, Msg.info("At least <white>2 players</white> are needed in queue to start early (or try <yellow>/bb practice</yellow> solo!)."));
                        return true;
                    }
                    if (plugin.gameManager().tryLaunchReadyMatch(qMode, true)) {
                        Msg.send(p, Msg.success("Match launched for queue <white>" + qMode.plainName() + "</white>!"));
                    }
                    return true;
                }
                MatchSession s = plugin.gameManager().getSessionOf(p.getUniqueId());
                if (s != null && (isAdmin || s.participantCount() >= 2)) {
                    s.forceStart();
                    Msg.send(p, Msg.success("Match force-started!"));
                } else if (!isAdmin) {
                    Msg.send(p, Msg.error("Join a queue first or gather at least 2 players on the court."));
                }
            }

            case "stop" -> {
                if (!Perms.check(sender, Perms.MATCH, "stop matches")) {
                    return true;
                }
                if (args.length >= 2) {
                    MatchSession s = plugin.gameManager().getSessionByArena(args[1]);
                    if (s != null) {
                        s.stopAndCleanup();
                        Msg.send(sender, Msg.success("Match stopped on arena <white>" + args[1] + "</white>."));
                    }
                } else if (sender instanceof Player p) {
                    MatchSession s = plugin.gameManager().getSessionOf(p.getUniqueId());
                    if (s != null) {
                        s.stopAndCleanup();
                        Msg.send(p, Msg.success("Match stopped."));
                    }
                }
            }

            case "save" -> {
                if (!Perms.check(sender, Perms.BACKUP, "force a data save")) {
                    return true;
                }
                long t0 = System.nanoTime();
                int dirtyBefore = plugin.statsManager().dirtyCount();
                plugin.statsManager().flushDirtyAsync(true);
                plugin.arenaManager().saveAsync();
                long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
                Msg.send(sender, Msg.success("Queued atomic async save (<white>"
                    + plugin.statsManager().allProfiles().size() + " profiles</white>, <yellow>" + dirtyBefore
                    + " dirty</yellow>, <white>" + plugin.arenaManager().allArenas().size()
                    + " arenas</white>) in <aqua>" + elapsedMs + "ms</aqua>."));
            }

            case "stress", "loadtest", "simulate" -> {
                if (!Perms.check(sender, Perms.STRESS, "run a load test")) {
                    return true;
                }
                fr.bosketball.stress.StressTestService stress = plugin.stressTestService();
                if (stress == null) {
                    Msg.send(sender, Msg.error("Stress service unavailable."));
                    return true;
                }
                if (args.length >= 2 && args[1].equalsIgnoreCase("stop")) {
                    stress.abort("stopped by " + sender.getName());
                    Msg.send(sender, Msg.success("Stress test stopped, simulated entities removed."));
                    return true;
                }
                if (stress.isRunning()) {
                    Msg.send(sender, Msg.error("A stress test is already running - <white>/bb stress stop</white>."));
                    return true;
                }
                int players = 50;
                int ticks = 400;
                if (args.length >= 2) {
                    try {
                        players = Integer.parseInt(args[1]);
                    } catch (NumberFormatException bad) {
                        Msg.send(sender, Msg.error("Usage: <white>/bb stress [players] [ticks] | /bb stress stop</white>"));
                        return true;
                    }
                }
                if (args.length >= 3) {
                    try {
                        ticks = Integer.parseInt(args[2]);
                    } catch (NumberFormatException bad) {
                        Msg.send(sender, Msg.error("Usage: <white>/bb stress [players] [ticks] | /bb stress stop</white>"));
                        return true;
                    }
                }
                stress.start(sender, players, ticks);
            }

            case "audit", "diagnose" -> {
                if (!Perms.check(sender, Perms.AUDIT, "open the diagnostic report")) {
                    return true;
                }
                fr.bosketball.ops.OpsAudit.Snapshot snap =
                    fr.bosketball.ops.OpsAudit.snapshotOf(plugin.settings());
                java.util.List<String> found = fr.bosketball.ops.OpsAudit.audit(snap);
                java.util.List<String> lines = new java.util.ArrayList<>(found);
                if (lines.isEmpty()) {
                    lines.add(" <" + Palette.SUCCESS + ">\u2705 Rien a optimiser : configuration et charge sont saines.</" + Palette.SUCCESS + ">");
                }
                lines.add(" " + fr.bosketball.ops.OpsAudit.cacheLine(
                    Msg.parseCacheSize(), Msg.parseCacheLimit(),
                    fr.bosketball.command.GgCommand.cachedPlayers(),
                    fr.bosketball.command.GgCommand.GG_CACHE_LIMIT));
                String thirdParty = fr.bosketball.ops.OpsAudit.thirdPartyNote(snap);
                if (thirdParty != null) {
                    lines.add(" " + thirdParty);
                }
                lines.add(" <dark_gray>view " + snap.viewDistance() + " \u00b7 sim "
                    + snap.simulationDistance() + " \u00b7 Xmx " + snap.maxHeapMb() + " Mo \u00b7 "
                    + snap.processors() + " threads \u00b7 " + snap.loadedChunks() + " chunks \u00b7 "
                    + snap.entities() + " entites \u00b7 " + snap.pasteBlocksPerTick()
                    + " blocs/tranche</dark_gray>");
                Msg.sendFramedCard(sender, "Audit serveur", lines);
            }

            case "displays", "hud", "sidebars" -> handleDisplaysCommand(sender, args);

            case "reload" -> {
                if (!Perms.check(sender, Perms.RELOAD, "reload the configuration")) {
                    return true;
                }
                long t0 = System.nanoTime();
                plugin.reloadBosketConfig();
                long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
                Msg.send(sender, Msg.success("Reloaded <white>config.yml</white>, <white>permissions.yml</white>, <white>shop.yml</white> & <white>spawn.yml</white> in <aqua>" + elapsedMs + "ms</aqua>."));
            }

            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleSeasonCommand(CommandSender sender, String[] args) {
        fr.bosketball.season.SeasonService season = plugin.seasonService();
        if (season == null || !season.enabled()) {
            Msg.send(sender, Msg.warn("Ranked seasons are disabled on this server."));
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
        boolean staff = Perms.has(sender, Perms.SEASON);
        switch (sub) {
            case "end", "close" -> {
                if (!staff) {
                    Msg.send(sender, Msg.error("Only staff can close a season."));
                    return;
                }
                if (!(sender instanceof Player)) {
                    var archive = season.rollover("admin");
                    Msg.send(sender, Msg.success("Season closed. Participants: "
                        + (archive == null ? 0 : archive.participants()) + "."));
                    return;
                }
                Msg.send(sender, Msg.warn("Closing " + season.state().name() + " now — this pays every reward and resets the ladder."));
                Msg.send(sender, "<gray>Confirm with:</gray> <yellow>/season end confirm</yellow>");
                if (args.length > 2 && args[2].equalsIgnoreCase("confirm")) {
                    var archive = season.rollover("admin");
                    Msg.send(sender, Msg.success("Season closed. Participants: "
                        + (archive == null ? 0 : archive.participants()) + ". " + season.state().name() + " is live."));
                }
            }
            case "start", "reset" -> {
                if (!staff) {
                    Msg.send(sender, Msg.error("Only staff can start a season."));
                    return;
                }
                int index = args.length > 2 ? parsePositiveInt(args[2], season.state().index() + 1) : season.state().index() + 1;
                season.restartSeason(index);
                Msg.send(sender, Msg.success("Started " + season.state().name() + " (resets season stats, keeps profiles)."));
            }
            case "days", "set-days" -> {
                if (!staff) {
                    Msg.send(sender, Msg.error("Only staff can change the season timer."));
                    return;
                }
                int days = args.length > 2 ? parsePositiveInt(args[2], 0) : 0;
                if (days <= 0) {
                    Msg.send(sender, Msg.error("Usage: /season days <1-365>"));
                    return;
                }
                season.setEndsAt(System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L);
                Msg.send(sender, Msg.success(season.state().name() + " now ends in " + days + " day(s) ("
                    + season.state().remainingLabel() + ")."));
            }
            case "top", "leaderboard", "standings" -> {
                if (!(sender instanceof Player p)) {
                    Msg.send(sender, Msg.info("Season standings are in-game only."));
                    return;
                }
                plugin.guiMenu().openSeason(p, 1);
            }
            case "claim" -> {
                if (!(sender instanceof Player p)) {
                    Msg.send(sender, Msg.info("Run this in-game."));
                    return;
                }
                int paid = season.claimPending(p);
                if (paid <= 0) {
                    Msg.send(p, Msg.info("No season rewards are waiting for you."));
                }
            }
            default -> {
                if (!(sender instanceof Player p)) {
                    Msg.send(sender, Msg.info(season.state().name() + " — "
                        + fr.bosketball.season.SeasonMath.formattedWindow(
                            season.state().startedAtMillis(), season.state().endsAtMillis())
                        + " · " + season.state().remainingLabel() + " left · "
                        + season.placementsRequired() + " placement matches."));
                    return;
                }
                plugin.guiMenu().openSeason(p);
            }
        }
    }

    private static int parsePositiveInt(String raw, int fallback) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void handleTagCommand(CommandSender sender, String[] args) {

        if (args.length >= 3 && !args[1].equalsIgnoreCase("grant") && !args[1].equalsIgnoreCase("give")
            && !args[1].equalsIgnoreCase("revoke") && !args[1].equalsIgnoreCase("take")) {
            Player onlineTarget = Bukkit.getPlayerExact(args[1]);
            PlayerProfile targetProf = onlineTarget != null
                ? plugin.statsManager().getOrCreate(onlineTarget)
                : plugin.statsManager().findByNameIgnoreCase(args[1]);
            ChatTag targetTag = ChatTag.fromIdOrNull(args[2]);
            String targetArg = args[1];
            String tagArg = args[2];

            if (targetTag == null) {
                ChatTag swapped = ChatTag.fromIdOrNull(args[1]);
                if (swapped != null) {
                    targetTag = swapped;
                    targetArg = args[2];
                    tagArg = args[1];
                    Player onlineTarget2 = Bukkit.getPlayerExact(targetArg);
                    targetProf = onlineTarget2 != null
                        ? plugin.statsManager().getOrCreate(onlineTarget2)
                        : plugin.statsManager().findByNameIgnoreCase(targetArg);
                }
            }
            if (targetProf != null || targetTag != null) {
                handleTagCommand(sender, new String[] { args[0], "grant", targetArg, tagArg });
                return;
            }
        }
        if (args.length >= 4 && (args[1].equalsIgnoreCase("grant") || args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("revoke") || args[1].equalsIgnoreCase("take"))) {
            boolean canGrant = !(sender instanceof Player)
                || Perms.has(sender, Perms.TAGS)
                || ChatTag.FOUNDER_USERNAME.equalsIgnoreCase(sender.getName());
            if (!canGrant) {
                Msg.send(sender, Msg.error("Only Server Operators (OP) or MaxLananas can grant or revoke exclusive tags."));
                return;
            }
            boolean grant = args[1].equalsIgnoreCase("grant") || args[1].equalsIgnoreCase("give");
            String targetName = args[2];
            ChatTag tag = ChatTag.fromIdOrNull(args[3]);
            if (tag == null) {
                Msg.send(sender, Msg.error("Unknown tag: <white>" + args[3] + "</white>."));
                return;
            }
            if (tag == ChatTag.FOUNDER && !ChatTag.FOUNDER_USERNAME.equalsIgnoreCase(targetName)) {
                Msg.send(sender, Msg.error("The <white>FOUNDER</white> tag is strictly exclusive to <gold>MaxLananas</gold>."));
                return;
            }
            Player onlineTarget = Bukkit.getPlayerExact(targetName);
            PlayerProfile targetProf = onlineTarget != null
                ? plugin.statsManager().getOrCreate(onlineTarget)
                : plugin.statsManager().findByNameIgnoreCase(targetName);
            if (targetProf == null) {
                Msg.send(sender, Msg.error("Player profile not found: <white>" + targetName + "</white>."));
                return;
            }
            if (grant) {
                targetProf.unlockTag(tag);
                if (targetProf.selectedTag() == null) {
                    targetProf.setSelectedTag(tag);
                }
                plugin.statsManager().saveProfileAsync(targetProf);
                Msg.send(sender, Msg.success("Granted <!shadow><white>" + tag.glyph() + "</white></!shadow> <white>" + tag.displayName() + "</white> to <gold>" + targetProf.lastKnownName() + "</gold>."));
                if (onlineTarget != null && onlineTarget.isOnline()) {
                    Msg.send(onlineTarget, Msg.success("You received the exclusive <!shadow><white>" + tag.glyph() + "</white></!shadow> <gold><bold>" + tag.displayName() + "</bold></gold> tag!"));
                }
            } else {
                targetProf.revokeTag(tag);
                plugin.statsManager().saveProfileAsync(targetProf);
                Msg.send(sender, Msg.info("Revoked <white>" + tag.displayName() + "</white> from <white>" + targetProf.lastKnownName() + "</white>."));
            }
            return;
        }

        if (!(sender instanceof Player p)) {
            Msg.send(sender, Msg.info("Usage: <white>/bb tag grant|revoke <player> <tag></white>"));
            return;
        }
        if (args.length >= 2) {
            PlayerProfile prof = plugin.statsManager().getOrCreate(p);
            prof.syncExclusiveFounderState();
            if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("none") || args[1].equalsIgnoreCase("clear")) {
                prof.setSelectedTag(null);
                plugin.statsManager().saveProfileAsync(prof);
                Msg.send(p, Msg.info("Chat tag unequipped."));
                return;
            }
            ChatTag tag = ChatTag.fromIdOrNull(args[1]);
            if (tag == null) {
                Msg.send(p, Msg.error("Unknown tag. Open <yellow>/bb tags</yellow> to browse your tags."));
                return;
            }
            boolean staffBypass = p.hasPermission("bosketball.admin") || p.hasPermission("bosketball.tags.all");
            if (!tag.canBeUsedBy(prof, p.getName(), staffBypass, Perms.has(p, Perms.STAFF))) {
                if (tag.isFounderExclusive()) {
                    Msg.send(p, Msg.error("The <white>FOUNDER</white> tag is strictly exclusive to <gold>MaxLananas</gold>."));
                } else if (tag.isTeamExclusive()) {
                    Msg.send(p, Msg.error("The <white>MYPINEAPPLE</white> tag is an exclusive team honor and cannot be bought in the Shop."));
                } else {
                    Msg.send(p, Msg.error("You haven't unlocked <white>" + tag.displayName()
                        + "</white> (<gold>" + tag.coinPrice() + " Coins</gold>). Open <yellow>/shop</yellow> to buy it!"));
                }
                return;
            }
            boolean equipped = prof.toggleEquippedTag(tag);
            plugin.statsManager().saveProfileAsync(prof);
            if (equipped) {
                Msg.send(p, Msg.success("Tag equipped: <!shadow><white>" + tag.glyph() + "</white></!shadow> <gold>" + tag.displayName() + "</gold>"));
            } else {
                Msg.send(p, Msg.info("Tag unequipped: <yellow>" + tag.displayName() + "</yellow>"));
            }
        } else {
            plugin.guiMenu().openTagsVault(p);
        }
    }

    private void handleCoinsCommand(CommandSender sender, String[] args) {
        if (args.length >= 4 && (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("add")
            || args[1].equalsIgnoreCase("take") || args[1].equalsIgnoreCase("remove")
            || args[1].equalsIgnoreCase("set"))) {
            if (!Perms.check(sender, Perms.COINS, "change Coins")) {
                return;
            }
            Player target = Bukkit.getPlayer(args[2]);
            PlayerProfile prof = target != null
                ? plugin.statsManager().getOrCreate(target)
                : plugin.statsManager().findByNameIgnoreCase(args[2]);

            if (prof == null || prof.uuid() == null) {
                Msg.send(sender, Msg.error("Unknown player: " + args[2]));
                return;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                Msg.send(sender, Msg.error("Invalid coin amount: " + args[3]));
                return;
            }

            if (amount < 0) {
                Msg.send(sender, Msg.error("Amount must be zero or positive (got " + amount + ")."));
                return;
            }
            String op = args[1].toLowerCase(java.util.Locale.ROOT);
            if (op.equals("give") || op.equals("add")) {
                prof.addCoins(amount);
            } else if (op.equals("take") || op.equals("remove")) {
                int taken = prof.removeCoins(amount);
                if (taken < amount) {
                    Msg.send(sender, Msg.warn("Balance was lower than requested — removed <white>"
                        + taken + "</white> of " + amount + " Coins."));
                }
            } else {
                prof.setCoins(amount);
            }
            plugin.statsManager().refreshLeaderboardBadges();

            plugin.statsManager().markDirty(prof.uuid());
            plugin.statsManager().saveProfileAsync(prof);
            if (target != null && plugin.sidebarManager() != null) {
                plugin.sidebarManager().updatePlayerSidebar(target, plugin.gameManager());
            }
            String label = prof.lastKnownName() != null ? prof.lastKnownName() : args[2];
            Msg.send(sender, Msg.success("Updated <white>" + label + "</white>" + (target == null ? " <gray>(offline)</gray>" : "")
                + "'s balance: " + Msg.coinsBadge(prof.coins())));
            return;
        }

        Player target = args.length >= 2 ? Bukkit.getPlayer(args[1]) : (sender instanceof Player p ? p : null);
        if (target == null) {
            Msg.send(sender, Msg.info("Usage: <white>/bb coins [player]</white> or <white>/bb coins <give|take|set> <player> <amount></white>"));
            return;
        }
        PlayerProfile prof = plugin.statsManager().getOrCreate(target);
        Msg.send(sender, Msg.info("<white>" + target.getName() + "</white>'s balance: " + Msg.coinsBadge(prof.coins())
            + " <dark_gray>•</dark_gray> <click:run_command:'/shop'><" + Palette.GOLD + ">[Open Shop]</" + Palette.GOLD + "></click>"));
    }

    private void handleCustomMapArchitect(CommandSender sender, String[] args) {
        if (!Perms.check(sender, Perms.MAP, "author arenas")) {
            return;
        }
        if (!(sender instanceof Player p)) {
            Msg.send(sender, Msg.info("The map studio is in-game only."));
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("gui") || args[1].equalsIgnoreCase("studio")) {
            plugin.guiMenu().openMapStudio(p);
            return;
        }
        if (args[1].equalsIgnoreCase("help")) {
            sendMapHelp(p);
            return;
        }

        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "auto", "autosetup", "quick" -> {
                Arena built = plugin.mapEditor().autoSetupFullCourt(p);
                Msg.send(p, Msg.success("Auto-configured full court <white><bold>" + built.id() + "</bold></white> (Hoops + Backboards + White Lines + Scoreboard + Barriers)! "
                    + "<click:run_command:'/bb practice " + built.id() + "'><" + Palette.SUCCESS + "><bold>[▶ Test Court Now]</bold></" + Palette.SUCCESS + "></click>"));
                Msg.playPersonalPackSound(p, "game.green_release", 0.9f, 1.15f);
            }
            case "create", "edit" -> {
                String id = args.length >= 3 ? args[2] : "map" + (plugin.arenaManager().allArenas().size() + 1);
                String display = SmallCaps.toSmallCaps(
                    args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : id
                );
                Arena arena = plugin.mapEditor().createOrSelectCustomMap(p, id, display);
                p.sendMessage(Msg.parse(
                    "\n<gradient:" + Palette.ICE + ":" + Palette.GOLD_SOFT + "><bold>▪ Map Architect: " + arena.displayName() + " (" + arena.id() + ") ▪</bold></gradient>\n"
                        + "<gray>Zero blocks of your build are modified! Use the <aqua><bold>Architect Wand</bold></aqua> or commands:</gray>\n"
                        + " <yellow>1.</yellow> <white>Left / Right-Click Block</white> <gray>(or <yellow>/bb map pos1</yellow> & <yellow>pos2</yellow>) » Court Out-of-Bounds</gray>\n"
                        + " <aqua>2.</aqua> <white>Sneak + Left / Right-Click</white> <gray>(or <aqua>/bb map barrier1</aqua> & <aqua>barrier2</aqua>) » Arena Barrier Cage</gray>\n"
                        + " <green>3.</green> <click:suggest_command:'/bb map hoop ignis'><yellow>/bb map hoop ignis [3d|custom]</yellow></click> & <click:suggest_command:'/bb map hoop frost'><yellow>/bb map hoop frost [3d|custom]</yellow></click> <gray>» Place Both Hoops</gray>\n"
                        + " <white>4.</white> <click:run_command:'/bb map lines on'><white>/bb map lines <on|off|preset|color|width></white></click> <gray>» Flush White Court Lines</gray>\n"
                        + " <gold>5.</gold> <click:suggest_command:'/bb map setspawn ignis'><gold>/bb map setspawn <ignis|frost|spectator|lobby> [1..5]</gold></click> <gray>» Spawn Points</gray>\n"
                        + " <light_purple>6.</light_purple> <click:run_command:'/bb map save'><green><bold>[Save & Activate]</bold></green></click> "
                        + "<click:run_command:'/bb practice " + arena.id() + "'><aqua><bold>[Test Map]</bold></aqua></click>\n"
                ));
            }

            case "wand" -> {
                String arenaId = args.length >= 3 ? args[2] : null;
                MapEditorManager.EditorSession s = plugin.mapEditor().getOrBindSession(p, arenaId);
                plugin.mapEditor().giveWand(p, s != null ? s.arenaId : "custom");
                Msg.send(p, Msg.success("Map Architect Wand equipped."));
            }

            case "pos1", "pos2" -> {
                Location floorBlock = p.getLocation().clone().subtract(0, 1.0, 0);
                plugin.mapEditor().setCourtCorner(p, floorBlock, action.equals("pos1"));
            }

            case "barrier1", "barrier2" -> {
                Location block = p.getLocation().clone().subtract(0, 1.0, 0);
                plugin.mapEditor().setBarrierCorner(p, block, action.equals("barrier1"));
            }

            case "ceiling" -> {
                Arena arena = requireEditedArena(p, null);
                if (arena == null) {
                    return;
                }
                if (args.length < 3) {
                    Msg.send(p, Msg.info("Usage: <white>/bb map ceiling <height_above_floor></white> (e.g. 16)"));
                    return;
                }
                try {
                    double h = Math.max(5.0, Double.parseDouble(args[2]));
                    CourtGeometry g = arena.geometry();
                    g.setBarrierBounds(g.barrierMinX(), g.barrierMinZ(), g.barrierMaxX(), g.barrierMaxZ(), g.floorY() + h);
                    plugin.arenaManager().save();
                    Msg.send(p, Msg.success("Ball ceiling height set to <white>+" + String.format(java.util.Locale.ROOT, "%.1f", h)
                        + " blocks</white> (Y = " + String.format(java.util.Locale.ROOT, "%.1f", g.ceilingY()) + ")."));
                } catch (NumberFormatException e) {
                    Msg.send(p, Msg.error("Invalid height."));
                }
            }

            case "hoop", "sethoop" -> {
                Arena arena = requireEditedArena(p, null);
                if (arena == null) {
                    return;
                }
                if (args.length < 3) {
                    Msg.send(p, Msg.info("Usage: <white>/bb map hoop <ignis|frost> [3d|custom] [north|south|east|west]</white>"));
                    return;
                }
                String sideArg = args[2].toLowerCase(java.util.Locale.ROOT);
                boolean isIgnisTarget = sideArg.startsWith("ignis") || sideArg.equals("south") || sideArg.equals("1");
                boolean spawn3d = !(args.length >= 4 && (args[3].equalsIgnoreCase("custom") || args[3].equalsIgnoreCase("blocks") || args[3].equalsIgnoreCase("none")));

                String dirArg = args.length >= 5 ? args[4].toLowerCase(java.util.Locale.ROOT) : (args.length >= 4 ? args[3].toLowerCase(java.util.Locale.ROOT) : "");
                Vector forward = switch (dirArg) {
                    case "north", "n" -> new Vector(0, 0, -1);
                    case "south", "s" -> new Vector(0, 0, 1);
                    case "east", "e" -> new Vector(1, 0, 0);
                    case "west", "w" -> new Vector(-1, 0, 0);
                    default -> CourtGeometry.snapCardinalHorizontal(p.getLocation().getDirection().multiply(-1.0));
                };

                Location loc = p.getLocation();
                double floorY = arena.geometry().floorY();
                double rimY = (Math.abs(loc.getY() - floorY) <= 1.25 && spawn3d)
                    ? (floorY + CourtGeometry.RIM_HEIGHT_ABOVE_FLOOR)
                    : loc.getY();
                Vector rimCenter = new Vector(
                    Math.floor(loc.getX()) + 0.5,
                    rimY,
                    Math.floor(loc.getZ()) + 0.5
                );

                arena.setCustomMap(true);
                arena.setWorldName(loc.getWorld().getName());
                if (isIgnisTarget) {
                    arena.geometry().setSouthHoop(rimCenter, forward, spawn3d);
                } else {
                    arena.geometry().setNorthHoop(rimCenter, forward, spawn3d);
                }
                arena.spawnDisplays();
                plugin.arenaManager().save();

                String hoopLabel = isIgnisTarget ? "<" + Palette.EMBER + "><bold>Ignis Target Hoop</bold></" + Palette.EMBER + ">" : "<" + Palette.ICE + "><bold>Frost Target Hoop</bold></" + Palette.ICE + ">";
                Msg.send(p, Msg.success(hoopLabel + " placed at <white>("
                    + String.format(java.util.Locale.ROOT, "%.1f, %.2f, %.1f", rimCenter.getX(), rimCenter.getY(), rimCenter.getZ())
                    + ")</white> facing <gold>" + CourtGeometry.cardinalLabel(forward) + "</gold>."));
                Msg.playPersonalPackSound(p, "ball.swish", 0.9f, 1.1f);
            }

            case "setspawn", "spawn" -> {
                Arena arena = requireEditedArena(p, null);
                if (arena == null) {
                    return;
                }
                if (args.length < 3) {
                    Msg.send(p, Msg.info("Usage: <white>/bb map setspawn <ignis|frost|spectator|lobby> [1..5]</white>"));
                    return;
                }
                String target = args[2].toLowerCase(java.util.Locale.ROOT);
                arena.setCustomMap(true);
                arena.setWorldName(p.getWorld().getName());

                if (target.equals("lobby") || target.equals("exit")) {
                    arena.setLobbyExitSpawn(Arena.SpawnPose.fromLocation(p.getLocation()));
                    plugin.arenaManager().save();
                    Msg.send(p, Msg.success("Lobby exit spawn saved at your position."));
                    return;
                }

                TeamSide side = switch (target) {
                    case "ignis", "red", "1" -> TeamSide.IGNIS;
                    case "frost", "blue", "2" -> TeamSide.FROST;
                    case "spectator", "spec" -> TeamSide.SPECTATOR;
                    default -> null;
                };
                if (side == null) {
                    Msg.send(p, Msg.error("Unknown target. Choices: <white>ignis, frost, spectator, lobby</white>."));
                    return;
                }
                int slotIdx = 0;
                if (args.length >= 4) {
                    try {
                        slotIdx = Math.max(1, Math.min(5, Integer.parseInt(args[3]))) - 1;
                    } catch (NumberFormatException ignored) {
                    }
                }
                arena.setCustomSpawn(side, slotIdx, p.getLocation());
                plugin.arenaManager().save();
                Msg.send(p, Msg.success("Saved <white>" + side.plainName() + " Spawn #" + (slotIdx + 1) + "</white> at your position."));
                Msg.playPersonalPackSound(p, "ui.click", 0.9f, 1.22f);
            }

            case "lines", "courtlines" -> {
                Arena arena = requireEditedArena(p, null);
                if (arena == null) {
                    return;
                }
                String subLine = args.length >= 3 ? args[2].toLowerCase(java.util.Locale.ROOT) : "on";
                switch (subLine) {
                    case "on", "enable", "true", "generate" -> {
                        arena.setCourtLinesEnabled(true);
                        arena.refreshCourtLines();
                        plugin.arenaManager().save();
                        Msg.send(p, Msg.success("White court lines enabled & generated (<white>"
                            + arena.courtLinesPreset().name() + "</white> • <white>"
                            + arena.courtLinePrimaryMaterial().name() + "</white>)."));
                    }
                    case "off", "disable", "false", "clear" -> {
                        arena.setCourtLinesEnabled(false);
                        arena.refreshCourtLines();
                        plugin.arenaManager().save();
                        Msg.send(p, Msg.info("Court lines hidden on <white>" + arena.id() + "</white>."));
                    }
                    case "preset", "style" -> {
                        Arena.CourtLinesPreset preset = args.length >= 4
                            ? Arena.CourtLinesPreset.fromString(args[3])
                            : Arena.CourtLinesPreset.FULL;
                        arena.setCourtLinesEnabled(true);
                        arena.setCourtLinesPreset(preset);
                        arena.refreshCourtLines();
                        plugin.arenaManager().save();
                        Msg.send(p, Msg.success("Court lines preset set to <white>" + preset.name() + "</white>."));
                    }
                    case "color", "material" -> {
                        if (args.length < 4) {
                            Msg.send(p, Msg.info("Usage: <white>/bb map lines color <white|gold|cyan|red|black|BLOCK_NAME> [accent]</white>"));
                            return;
                        }
                        Material mainMat = parseLineMaterial(args[3], Material.WHITE_CONCRETE);
                        Material accentMat = args.length >= 5 ? parseLineMaterial(args[4], mainMat) : mainMat;
                        arena.setCourtLinesEnabled(true);
                        arena.setCourtLinePrimaryMaterial(mainMat);
                        arena.setCourtLineAccentMaterial(accentMat);
                        arena.refreshCourtLines();
                        plugin.arenaManager().save();
                        Msg.send(p, Msg.success("Court line materials updated: <white>" + mainMat.name()
                            + "</white> (accent: <white>" + accentMat.name() + "</white>)."));
                    }
                    case "width", "thickness" -> {
                        if (args.length < 4) {
                            Msg.send(p, Msg.info("Usage: <white>/bb map lines width <0.06..0.28></white> (default: 0.13)"));
                            return;
                        }
                        try {
                            float w = Float.parseFloat(args[3]);
                            arena.setCourtLinesEnabled(true);
                            arena.setCourtLineWidth(w);
                            arena.refreshCourtLines();
                            plugin.arenaManager().save();
                            Msg.send(p, Msg.success("Court line width set to <white>"
                                + String.format(java.util.Locale.ROOT, "%.2f", arena.courtLineWidth()) + " blocks</white>."));
                        } catch (NumberFormatException e) {
                            Msg.send(p, Msg.error("Invalid line width."));
                        }
                    }
                    default -> Msg.send(p, Msg.info("Usage: <white>/bb map lines <on|off|preset|color|width></white>"));
                }
                Msg.playPersonalPackSound(p, "ui.click", 0.9f, 1.24f);
            }

            case "jumbotron" -> {
                Arena arena = requireEditedArena(p, null);
                if (arena == null) {
                    return;
                }
                boolean enable = !(args.length >= 3 && (args[2].equalsIgnoreCase("off") || args[2].equalsIgnoreCase("false") || args[2].equalsIgnoreCase("disable")));
                arena.setJumbotronEnabled(enable);
                if (enable) {
                    arena.setCustomJumbotronCenter(p.getLocation().toVector());
                }
                arena.spawnDisplays();
                plugin.arenaManager().save();
                Msg.send(p, enable
                    ? Msg.success("4-Sided Jumbotron placed at your position.")
                    : Msg.info("Jumbotron disabled on this map."));
            }

            case "preview" -> {
                boolean active = plugin.mapEditor().togglePreview(p);
                Msg.send(p, active
                    ? Msg.info("Blueprint particle preview enabled (Gold = Out-of-bounds • Cyan = Barriers • Red/Blue = Hoops & Spawns).")
                    : Msg.info("Blueprint particle preview hidden."));
            }

            case "info" -> {
                Arena arena = requireEditedArena(p, args.length >= 3 ? args[2] : null);
                if (arena == null) {
                    return;
                }
                CourtGeometry g = arena.geometry();
                p.sendMessage(Msg.parse(
                    "\n<gradient:" + Palette.ICE + ":" + Palette.GOLD_SOFT + "><bold>▪ Map Info: " + arena.displayName() + " (" + arena.id() + ") ▪</bold></gradient>\n"
                        + " <gray>▸ World:</gray> <white>" + arena.worldName() + "</white> <dark_gray>•</dark_gray> <gray>Custom Map:</gray> <aqua>" + (arena.isCustomMap() ? "Yes" : "Generated Stadium") + "</aqua>\n"
                        + " <gray>▸ Court Bounds:</gray> <yellow>X[" + String.format(java.util.Locale.ROOT, "%.1f..%.1f", g.minX(), g.maxX()) + "] Z[" + String.format(java.util.Locale.ROOT, "%.1f..%.1f", g.minZ(), g.maxZ()) + "] • Floor Y=" + String.format(java.util.Locale.ROOT, "%.1f", g.floorY()) + "</yellow>\n"
                        + " <gray>▸ Barrier Cage:</gray> <aqua>X[" + String.format(java.util.Locale.ROOT, "%.1f..%.1f", g.barrierMinX(), g.barrierMaxX()) + "] Z[" + String.format(java.util.Locale.ROOT, "%.1f..%.1f", g.barrierMinZ(), g.barrierMaxZ()) + "] • Ceiling Y=" + String.format(java.util.Locale.ROOT, "%.1f", g.ceilingY()) + "</aqua>\n"
                        + " <gray>▸ Ignis Hoop:</gray> <" + Palette.DANGER + ">(" + String.format(java.util.Locale.ROOT, "%.1f, %.2f, %.1f", g.southHoopRim().getX(), g.southHoopRim().getY(), g.southHoopRim().getZ()) + ") » " + CourtGeometry.cardinalLabel(g.southHoopForward()) + "</" + Palette.DANGER + ">\n"
                        + " <gray>▸ Frost Hoop:</gray> <" + Palette.ICE + ">(" + String.format(java.util.Locale.ROOT, "%.1f, %.2f, %.1f", g.northHoopRim().getX(), g.northHoopRim().getY(), g.northHoopRim().getZ()) + ") » " + CourtGeometry.cardinalLabel(g.northHoopForward()) + "</" + Palette.ICE + ">\n"
                        + " <gray>▸ Court Lines:</gray> <white>" + (arena.isCourtLinesEnabled()
                            ? arena.courtLinesPreset().name() + " (" + arena.courtLinePrimaryMaterial().name() + ", w=" + String.format(java.util.Locale.ROOT, "%.2f", arena.courtLineWidth()) + ")"
                            : "Disabled") + "</white>\n"
                        + " <gray>▸ Custom Spawns:</gray> <white>Ignis=" + arena.allCustomSpawns().get(TeamSide.IGNIS).size()
                        + " • Frost=" + arena.allCustomSpawns().get(TeamSide.FROST).size()
                        + " • Lobby=" + (arena.lobbyExitSpawn() != null ? "Set" : "Auto") + "</white>\n"
                ));
            }

            case "save" -> {
                Arena arena = requireEditedArena(p, null);
                if (arena == null) {
                    return;
                }
                arena.spawnDisplays();
                plugin.arenaManager().save();
                Msg.send(p, Msg.success("Map '<white>" + arena.id() + "</white>' saved & hoops refreshed! "
                    + "<click:run_command:'/bb practice " + arena.id() + "'><aqua><bold>[▶ Play Map]</bold></aqua></click>"));
                Msg.playPersonalPackSound(p, "game.green_release", 0.9f, 1.05f);
            }

            default -> sendMapHelp(p);
        }
    }

    private Arena requireEditedArena(Player p, String explicitId) {
        MapEditorManager.EditorSession s = plugin.mapEditor().getOrBindSession(p, explicitId);
        if (s == null) {
            Msg.send(p, Msg.error("No map selected. Run <yellow>/bb map create <id> [name]</yellow>."));
            return null;
        }
        Arena a = plugin.arenaManager().getArena(s.arenaId);
        if (a == null) {
            Msg.send(p, Msg.error("Map not found: " + s.arenaId));
        }
        return a;
    }

    private void sendMapHelp(Player p) {
        p.sendMessage(Msg.parse(
            "\n<gradient:" + Palette.ICE + ":" + Palette.GOLD_SOFT + "><bold>▪ Custom Basketball Map Architect (/bb map) ▪</bold></gradient>\n"
                + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                + " <aqua>/bb map create <id> [name]</aqua> <gray>• Create a map & receive the Architect Wand</gray>\n"
                + " <aqua>/bb map wand [id]</aqua> <gray>• Receive the Map Architect Wand</gray>\n"
                + " <yellow>/bb map pos1</yellow> & <yellow>pos2</yellow> <gray>• Court out-of-bounds corners + floor height</gray>\n"
                + " <yellow>/bb map barrier1</yellow> & <yellow>barrier2</yellow> <gray>• Invisible walls keeping ball & players inside</gray>\n"
                + " <yellow>/bb map ceiling <height></yellow> <gray>• Ceiling height above floor</gray>\n"
                + " <green>/bb map hoop <ignis|frost> [3d|custom]</green> <gray>• Place both hoops (N/S/E/W)</gray>\n"
                + " <white>/bb map lines <on|off|preset|color|width></white> <gray>• Flush white court floor lines</gray>\n"
                + " <gold>/bb map setspawn <ignis|frost|spectator|lobby> [1..5]</gold> <gray>• Team & exit spawns</gray>\n"
                + " <light_purple>/bb map jumbotron <here|off></light_purple> <gray>• Place or hide overhead Jumbotron</gray>\n"
                + " <white>/bb map preview</white> • <white>/bb map info</white> • <green>/bb map save</green>\n"
                + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
        ));
    }

    private void handleArenaAdmin(CommandSender sender, String[] args) {
        if (!Perms.check(sender, Perms.ARENA, "manage arenas")) {
            return;
        }
        if (args.length < 2) {
            if (sender instanceof Player p) {
                plugin.guiMenu().openMapStudio(p);
            } else {
                Msg.send(sender, Msg.info("Usage: <white>/bb arena <build|list|tp|delete> [name]</white>"));
            }
            return;
        }
        if (args[1].equalsIgnoreCase("schematic") || args[1].equalsIgnoreCase("schem") || args[1].equalsIgnoreCase("courts")) {
            handleSchematicCourts(sender, args);
            return;
        }
        switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "build", "create" -> {
                if (!(sender instanceof Player p)) {
                    return;
                }
                String id = args.length >= 3 ? args[2].toLowerCase(java.util.Locale.ROOT) : "stadium" + (plugin.arenaManager().allArenas().size() + 1);
                String display = SmallCaps.toSmallCaps(args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : id);
                Arena built = ArenaBuilder.buildFullStadium(id, display, p.getLocation());
                plugin.arenaManager().registerArena(built);
                p.teleport(built.centerCourtLocation().add(0, 0.2, 0));
                Msg.send(p, Msg.success("Stadium built: <white>" + id + "</white> (Hoops + Jumbotron + Bleachers)."));
            }
            case "list" -> {
                Msg.send(sender, "<gold><bold>BosketBall Arenas (" + plugin.arenaManager().allArenas().size() + "):</bold></gold>");
                for (Arena a : plugin.arenaManager().allArenas()) {
                    String tag = a.isCustomMap() ? "<aqua>[Custom Map]</aqua>" : "<gold>[Stadium]</gold>";
                    Msg.send(sender, "<gray>• " + tag + " <white>" + a.id() + "</white> — <yellow>" + a.displayName() + "</yellow> (" + a.worldName() + ")</gray>");
                }
            }
            case "tp" -> {
                if (sender instanceof Player p && args.length >= 3) {
                    Arena a = plugin.arenaManager().getArena(args[2]);
                    if (a != null) {
                        p.teleport(a.centerCourtLocation());
                        Msg.send(p, Msg.success("Teleported to <white>" + a.displayName() + "</white>."));
                    }
                }
            }
            case "delete", "remove" -> {
                if (args.length >= 3 && plugin.arenaManager().deleteArena(args[2])) {
                    Msg.send(sender, Msg.success("Arena <white>" + args[2] + "</white> deleted."));
                } else {
                    Msg.send(sender, Msg.error("Arena not found."));
                }
            }
            default -> Msg.send(sender, Msg.info("Usage: <white>/bb arena <build|list|tp|delete> [name]</white>"));
        }
    }

    private void handleSchematicCourts(CommandSender sender, String[] args) {
        var pool = plugin.schematicCourtPool();
        if (pool == null) {
            Msg.send(sender, Msg.error("Instanced courts are not initialised."));
            return;
        }
        String action = args.length >= 3 ? args[2].toLowerCase(java.util.Locale.ROOT) : "info";
        switch (action) {
            case "reload", "rescan" -> {
                boolean ok = pool.bootstrap();
                for (String line : pool.statusLines()) {
                    Msg.send(sender, line);
                }
                Msg.send(sender, ok
                    ? Msg.success("Schematic re-read and courts re-allocated. Pasting in the background…")
                    : Msg.error("Schematic unavailable: " + pool.loadError()));
            }
            case "repaste", "refresh" -> {
                int queued = pool.repasteAll(sender instanceof Player p ? p : null);
                Msg.send(sender, Msg.success("Re-paste queued for <white>" + queued + "</white> court(s)."));
            }
            case "goto", "tp" -> {
                if (!(sender instanceof Player p)) {
                    Msg.send(sender, Msg.error("Players only."));
                    return;
                }
                java.util.List<Arena> courts = new java.util.ArrayList<>(pool.allCourts());
                int index = 1;
                if (args.length >= 4) {
                    try {
                        index = Math.clamp(Integer.parseInt(args[3]), 1, Math.max(1, courts.size()));
                    } catch (NumberFormatException e) {
                        Msg.send(sender, Msg.error("Court number must be a number, got " + args[3]));
                        return;
                    }
                }
                if (courts.isEmpty()) {
                    Msg.send(sender, Msg.error("No instanced court is allocated."));
                    return;
                }
                Arena court = courts.get(index - 1);
                p.teleport(court.centerCourtLocation().add(0, 2.0, 0));
                Msg.send(sender, Msg.success("Teleported to court <white>#" + index + "</white> ("
                    + court.id() + ")."));
            }
            case "info", "status" -> {
                for (String line : pool.statusLines()) {
                    Msg.send(sender, line);
                }
                var cfg = pool.config();
                Msg.send(sender, "<gray>File:</gray> <white>" + cfg.schematic() + "</white> <dark_gray>•</dark_gray> <gray>instances:</gray> <white>"
                    + cfg.instances() + "</white> <dark_gray>•</dark_gray> <gray>spacing:</gray> <white>" + cfg.spacingBlocks() + "</white>");
                Msg.send(sender, "<gray>Rim axis:</gray> <white>" + cfg.hoopFacing() + "</white> <dark_gray>•</dark_gray> <gray>half-len/wid:</gray> <white>"
                    + cfg.halfLength() + "/" + cfg.halfWidth() + "</white> <dark_gray>•</dark_gray> <gray>floor offset:</gray> <white>"
                    + cfg.floorRelativeY() + "</white> <dark_gray>•</dark_gray> <gray>rim +Y:</gray> <white>" + cfg.rimRelativeY() + "</white>");
                if (pool.schematic() != null) {
                    Msg.send(sender, "<gray>Auto floor Y:</gray> <white>origin + "
                        + pool.schematic().floorOffsetAtCenter() + "</white> <dark_gray>•</dark_gray> <gray>palette:</gray> <white>"
                        + pool.schematic().paletteSize() + "</white> <dark_gray>•</dark_gray> <gray>chunks/court:</gray> <white>"
                        + pool.schematic().footprintChunks() + "</white>");
                }
            }
            default -> Msg.send(sender, Msg.info("Usage: <white>/bb arena schematic <info|reload|repaste|goto <n>></white>"));
        }
    }

    private void handleRankAdmin(CommandSender sender, String[] args) {
        if (args.length < 4) {
            Msg.send(sender, Msg.info("Usage: <white>/bb rank <set|elo> <player> <rank|value></white>"));
            return;
        }
        if (!Perms.check(sender, Perms.RANK, "assign staff ranks")) {
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        PlayerProfile prof = target != null
            ? plugin.statsManager().getOrCreate(target)
            : plugin.statsManager().findByNameIgnoreCase(args[2]);
        if (prof == null) {
            Msg.send(sender, Msg.error("Player not found: " + args[2]));
            return;
        }
        String targetLabel = prof.lastKnownName() != null ? prof.lastKnownName() : args[2];
        if (args[1].equalsIgnoreCase("set")) {
            if (args[3].equalsIgnoreCase("auto") || args[3].equalsIgnoreCase("clear")) {
                prof.setStaffRankOverride(null);
            } else {
                Rank rank = Rank.byNameOrNull(args[3]);
                if (rank == null) {
                    Msg.send(sender, Msg.error("Unknown rank: " + args[3]));
                    return;
                }

                prof.setStaffRankOverride(rank.staffOnly() ? rank : null);
                if (!rank.staffOnly()) {
                    prof.setElo(Math.max(prof.elo(), rank.minElo()));
                }
            }
            if (target != null) {
                if (plugin.sidebarManager() != null) {
                    plugin.sidebarManager().clearPlayerCache(target.getUniqueId());
                }
                if (plugin.tabListManager() != null) {
                    plugin.tabListManager().clearPlayerCache(target.getUniqueId());
                }
            }
            plugin.statsManager().refreshLeaderboardBadges();
            plugin.statsManager().saveProfileAsync(prof);
            Msg.send(sender, Msg.success("Updated <white>" + targetLabel + "</white>'s rank: <!shadow><white>"
                + prof.effectiveRank().pillGlyph() + "</white></!shadow>"));
        } else if (args[1].equalsIgnoreCase("elo")) {

            String verb = "set";
            String valueArg = args[3];
            if (args.length >= 5 && (args[3].equalsIgnoreCase("give") || args[3].equalsIgnoreCase("take")
                || args[3].equalsIgnoreCase("set") || args[3].equalsIgnoreCase("add")
                || args[3].equalsIgnoreCase("remove"))) {
                verb = args[3].toLowerCase(java.util.Locale.ROOT);
                valueArg = args[4];
            }
            int parsed;
            try {
                parsed = Integer.parseInt(valueArg);
            } catch (NumberFormatException e) {
                Msg.send(sender, Msg.error("Invalid ELO value: " + valueArg));
                return;
            }
            int before = prof.elo();
            int updated = switch (verb) {
                case "give", "add" -> before + parsed;
                case "take", "remove" -> before - parsed;
                default -> parsed;
            };
            prof.setElo(Math.clamp(updated, 100, 99_999));
            plugin.statsManager().markDirty(prof.uuid());
            plugin.statsManager().refreshLeaderboardBadges();
            plugin.statsManager().saveProfileAsync(prof);
            Player online = target != null ? target : Bukkit.getPlayer(prof.uuid());
            if (online != null && plugin.sidebarManager() != null) {
                plugin.sidebarManager().updatePlayerSidebar(online, plugin.gameManager());
            }
            Msg.send(sender, Msg.success("Set <white>" + targetLabel + "</white>'s ELO to <gold>" + prof.elo()
                + "</gold> <dark_gray>(was " + before + ")</dark_gray>."));
        } else {
            Msg.send(sender, Msg.info("Usage: <white>/bb rank <set|elo> <player> <rank|value></white>"));
        }
    }

    private void sendHelp(CommandSender sender) {
        Msg.sendInteractiveHelpCard(sender);
    }

    private static final java.util.Set<String> ADMIN_ONLY_SUBS = java.util.Set.of(
        "admin", "holo", "hologram", "maintenance", "mutechat", "rank", "coins", "tags", "giveball", "forcestart", "stop", "save", "reload"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] rawArgs) {
        String rootName = command != null ? command.getName().toLowerCase(java.util.Locale.ROOT) : alias.toLowerCase(java.util.Locale.ROOT);
        boolean isOp = isStaff(sender);
        if (rootName.equals("bosketball") && !isOp) {
            return List.of();
        }
        String[] args = normalizeShortcutArgs(command, alias, rawArgs);
        if (args.length == 1) {
            if (!isOp) {
                return List.of();
            }
            return filterPrefix(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        if ((sub.equals("map") || sub.equals("arena") || ADMIN_ONLY_SUBS.contains(sub)) && !isOp) {
            return List.of();
        }
        List<String> arenaIds = isOp ? plugin.arenaManager().allArenas().stream().map(Arena::id).toList() : List.of();

        if (args.length == 2) {
            return switch (sub) {
                case "play", "join" -> filterPrefix(List.of("1v1", "2v2", "3v3", "5v5", "3pt", "practice"), args[1]);
                case "practice", "3pt", "contest", "stop" -> isOp ? filterPrefix(arenaIds, args[1]) : List.of();
                case "holo", "hologram" -> isOp ? filterPrefix(List.of("elo", "points", "coins", "play", "shop", "remove", "clear"), args[1]) : List.of();
                case "maintenance", "maint" -> isOp ? filterPrefix(List.of("on", "off"), args[1]) : List.of();
                case "shop", "store" -> args.length <= 2
                    ? filterPrefix(List.of("all", "balls", "dunks", "tags", "search", "sort"), args[1])
                    : List.of();
                case "coins", "coin" -> isOp ? filterPrefix(List.of("give", "take", "set"), args[1]) : List.of();
                case "top", "leaderboard" -> filterPrefix(List.of("elo", "coins"), args[1]);
                case "pack" -> isOp ? filterPrefix(List.of("push", "test"), args[1]) : List.of();
                case "net", "ping", "latency" -> isOp
                    ? filterPrefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1])
                    : List.of();
                case "stress", "loadtest", "simulate" -> isOp
                    ? filterPrefix(List.of("stop", "25", "50", "100", "200"), args[1])
                    : List.of();
                case "skin" -> isOp ? filterPrefix(Arrays.stream(BallSkin.values()).map(s -> s.name().toLowerCase(java.util.Locale.ROOT)).toList(), args[1]) : List.of();
                case "giveball" -> isOp ? filterPrefix(Arrays.stream(BallSkin.values()).map(s -> s.name().toLowerCase(java.util.Locale.ROOT)).toList(), args[1]) : List.of();
                case "tags", "tag" -> {
                    if (!isOp) {
                        yield List.of();
                    }
                    List<String> opts = new ArrayList<>();
                    for (ChatTag t : ChatTag.values()) {
                        opts.add(t.id());
                    }
                    opts.add("off");
                    opts.add("grant");
                    opts.add("revoke");
                    yield filterPrefix(opts, args[1]);
                }
                case "party", "group", "p" -> filterPrefix(List.of("name", "invite", "join", "leave", "kick", "promote"), args[1]);
                case "friends", "friend", "f" -> filterPrefix(List.of("add", "remove", "decline"), args[1]);
                case "duel" -> Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> !n.equalsIgnoreCase(sender.getName()))
                    .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
                case "map" -> isOp ? filterPrefix(MAP_SUBS, args[1]) : List.of();
                case "arena" -> isOp ? filterPrefix(List.of("build", "list", "tp", "delete", "schematic"), args[1]) : List.of();
                case "rank" -> isOp ? filterPrefix(List.of("set", "elo"), args[1]) : List.of();
                case "elo", "giveelo", "addelo" ->
                    isOp ? filterPrefix(List.of("give", "take", "set"), args[1]) : List.of();

                case "give" -> List.of();
                default -> List.of();
            };
        }
        if (args.length == 3) {
            if (sub.equals("play") || sub.equals("join")) {
                return filterPrefix(arenaIds, args[2]);
            }
            if (sub.equals("arena") && (args[1].equalsIgnoreCase("tp") || args[1].equalsIgnoreCase("delete"))) {
                return filterPrefix(arenaIds, args[2]);
            }
            if (sub.equals("arena") && args[1].equalsIgnoreCase("schematic")) {
                return filterPrefix(List.of("info", "reload", "repaste", "goto"), args[2]);
            }
            if (sub.equals("map")) {
                return switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
                    case "edit", "wand", "info" -> filterPrefix(arenaIds, args[2]);
                    case "hoop", "sethoop" -> filterPrefix(List.of("ignis", "frost"), args[2]);
                    case "lines", "courtlines" -> filterPrefix(List.of("on", "off", "preset", "color", "width"), args[2]);
                    case "setspawn", "spawn" -> filterPrefix(List.of("ignis", "frost", "spectator", "lobby"), args[2]);
                    case "jumbotron" -> filterPrefix(List.of("here", "off"), args[2]);
                    case "ceiling" -> filterPrefix(List.of("12", "16", "20", "24"), args[2]);
                    default -> List.of();
                };
            }
            if (sub.equals("rank") || sub.equals("coins") || sub.equals("coin")
                || ((sub.equals("tags") || sub.equals("tag")) && (args[1].equalsIgnoreCase("grant") || args[1].equalsIgnoreCase("revoke")))) {
                return null;
            }
            if (sub.equals("elo") && isOp) {
                return null;
            }
            if (sub.equals("giveelo") || sub.equals("addelo")) {
                return null;
            }
        }
        if (args.length == 4 && sub.equals("elo") && isOp) {
            return filterPrefix(List.of("100", "500", "1000", "2500", "5000"), args[3]);
        }
        if (args.length == 4) {
            if ((sub.equals("tags") || sub.equals("tag")) && (args[1].equalsIgnoreCase("grant") || args[1].equalsIgnoreCase("revoke"))) {
                List<String> grantable = Arrays.stream(ChatTag.values())
                    .filter(t -> t != ChatTag.FOUNDER)
                    .map(ChatTag::id)
                    .toList();
                return filterPrefix(grantable, args[3]);
            }
            if (sub.equals("rank") && args[1].equalsIgnoreCase("set")) {
                List<String> ranks = new ArrayList<>(Arrays.stream(Rank.values()).map(r -> r.name().toLowerCase(java.util.Locale.ROOT)).toList());
                ranks.add("auto");
                return filterPrefix(ranks, args[3]);
            }
            if ((sub.equals("coins") || sub.equals("coin"))
                && (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("take") || args[1].equalsIgnoreCase("set"))) {
                return filterPrefix(List.of("100", "250", "500", "1000", "2500"), args[3]);
            }
            if (sub.equals("map") && (args[1].equalsIgnoreCase("hoop") || args[1].equalsIgnoreCase("sethoop"))) {
                return filterPrefix(List.of("3d", "custom"), args[3]);
            }
            if (sub.equals("map") && (args[1].equalsIgnoreCase("setspawn") || args[1].equalsIgnoreCase("spawn"))) {
                return filterPrefix(List.of("1", "2", "3", "4", "5"), args[3]);
            }
            if (sub.equals("map") && (args[1].equalsIgnoreCase("lines") || args[1].equalsIgnoreCase("courtlines"))) {
                return switch (args[2].toLowerCase(java.util.Locale.ROOT)) {
                    case "preset", "style" -> filterPrefix(List.of("full", "street", "minimal"), args[3]);
                    case "color", "material" -> filterPrefix(List.of("white", "gold", "cyan", "red", "black", "QUARTZ_BLOCK", "WHITE_CONCRETE"), args[3]);
                    case "width", "thickness" -> filterPrefix(List.of("0.09", "0.13", "0.16", "0.20"), args[3]);
                    default -> List.of();
                };
            }
        }
        if (args.length == 5 && sub.equals("map") && (args[1].equalsIgnoreCase("hoop") || args[1].equalsIgnoreCase("sethoop"))) {
            return filterPrefix(List.of("north", "south", "east", "west"), args[4]);
        }
        return List.of();
    }

    private static Material parseLineMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "white", "quartz" -> Material.QUARTZ_BLOCK;
            case "brightwhite", "concrete" -> Material.WHITE_CONCRETE;
            case "gold", "yellow" -> Material.YELLOW_CONCRETE;
            case "cyan", "aqua", "blue" -> Material.LIGHT_BLUE_CONCRETE;
            case "red", "ignis" -> Material.RED_CONCRETE;
            case "black", "dark" -> Material.BLACK_CONCRETE;
            default -> Arena.resolveBlockMaterial(raw.trim(), fallback);
        };
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String low = prefix.toLowerCase(java.util.Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(java.util.Locale.ROOT).startsWith(low)).toList();
    }

    private void handleBotCommand(CommandSender sender, String[] args) {
        Player player = sender instanceof Player p ? p : null;
        if (player == null) {
            sender.sendMessage(Msg.parse(Msg.error("Players only.")));
            return;
        }
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session == null) {
            Msg.send(player, Msg.info("Join a match or Practice first, then use <white>/bb bot</white>."));
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("info")) {
            Msg.sendFramedCard(player, "AI PLAYERS", List.of(
                "<gray>Difficulty:</gray> <white>" + session.botDifficulty().displayName() + "</white>"
                    + " <dark_gray>(/bb bot easy|normal|pro)</dark_gray>",
                "<gray>On court:</gray> <white>" + (session.botSquad().size()) + "</white>",
                "",
                "<yellow>/bb bot <difficulty></yellow> <dark_gray>—</dark_gray> <gray>set the skill of the AI</gray>",
                "<yellow>/bb bot fill</yellow> <dark_gray>—</dark_gray> <gray>fill every empty slot with AI</gray>",
                "<yellow>/bb bot add</yellow> <dark_gray>—</dark_gray> <gray>add one AI to your team</gray>",
                "<yellow>/bb bot off</yellow> <dark_gray>—</dark_gray> <gray>remove every AI</gray>",
                "<yellow>/bb defender</yellow> <dark_gray>—</dark_gray> <gray>classic practice sparring defender</gray>"
            ));
            return;
        }
        String sub = args[1].toLowerCase(java.util.Locale.ROOT);
        if (sub.equals("off") || sub.equals("remove") || sub.equals("clear")) {
            session.clearBots();
            Msg.send(player, Msg.success("All AI players left the court."));
            return;
        }
        var difficulty = fr.bosketball.game.bot.BotDifficulty.byNameOrNull(sub);
        if (difficulty != null) {
            session.setBotDifficulty(difficulty);
            Msg.send(player, Msg.success("AI difficulty set to <white>"
                + difficulty.displayName() + "</white> — they will spawn on the next fill."));
            return;
        }
        if (sub.equals("fill") || sub.equals("add") || sub.equals("spawn")) {
            session.fillWithBots(session.botDifficulty());
            return;
        }
        Msg.send(player, Msg.error("Usage: /bb bot <easy|normal|pro|fill|add|off>"));
    }

    private void handleTutorial(Player player, String[] args) {
        var tuto = plugin.tutorialManager();
        String sub = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
        switch (sub) {
            case "start", "begin", "go" -> tuto.start(player);
            case "restart", "replay", "reset" -> tuto.restart(player);
            case "next", "continue", "ok" -> {
                if (!tuto.isRunning(player)) {
                    Msg.send(player, Msg.warn("No tour is running — <white>/tuto start</white> begins one."));
                } else {
                    tuto.advance(player, false);
                }
            }
            case "skip" -> {
                if (!tuto.isRunning(player)) {
                    Msg.send(player, Msg.warn("No tour is running — <white>/tuto start</white> begins one."));
                } else {
                    tuto.advance(player, true);
                }
            }
            case "show", "again" -> {
                if (tuto.isRunning(player)) {
                    tuto.showCurrentStep(player);
                } else {
                    plugin.guiMenu().openTutorial(player);
                }
            }
            case "menu", "gui", "help" -> plugin.guiMenu().openTutorial(player);
            case "stop", "pause", "quit" -> {
                if (tuto.isRunning(player)) {
                    tuto.stop(player, false);
                } else {
                    Msg.send(player, Msg.info("No tour is running right now."));
                }
            }
            case "finish", "done" -> {
                if (!tuto.isRunning(player)) {
                    Msg.send(player, Msg.warn("The completion bonus is only paid at the end of a tour — "
                        + "<white>/tuto start</white> to run it."));
                } else {
                    tuto.finish(player);
                }
            }
            case "diag", "diagnose", "debug" -> Msg.sendFramedCard(player, "TUTORIAL DIAGNOSTICS",
                tuto.diagnostics(player));
            default -> {
                if (tuto.isRunning(player)) {
                    tuto.showCurrentStep(player);
                } else {
                    plugin.guiMenu().openTutorial(player);
                }
            }
        }
    }

    private void handleReportCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Msg.parse(Msg.error("Players only.")));
            return;
        }
        if (args.length < 2) {
            Msg.send(p, "Usage: <yellow>/report <player> <reason></yellow>");
            return;
        }
        String target = args[1];
        StringBuilder reason = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            reason.append(args[i]).append(' ');
        }
        var report = plugin.reportService().fileReport(p, target, reason.toString().trim());
        if (report != null) {
            Msg.playPersonalPackSound(p, "ui.click", 0.85f, 1.2f);
        }
    }

    private void handleIgnoreCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            return;
        }
        if (args.length < 2) {
            var profile = plugin.statsManager().getOrCreate(p);
            Msg.send(p, "<gray>Ignored players:</gray> <white>" + profile.ignoredPlayers().size()
                + "</white> <dark_gray>•</dark_gray> <yellow>/ignore <player></yellow> to toggle.");
            return;
        }
        PlayerProfile targetProfile = plugin.statsManager().findByNameIgnoreCase(args[1]);
        if (targetProfile == null) {
            Msg.send(p, Msg.error("Unknown player: " + args[1]));
            return;
        }
        boolean nowIgnoring = plugin.socialService().toggleIgnore(
            plugin.statsManager().getOrCreate(p), targetProfile.uuid());
        Msg.send(p, nowIgnoring
            ? Msg.success("You will no longer see messages from <white>" + targetProfile.lastKnownName() + "</white>.")
            : Msg.success("Unignored <white>" + targetProfile.lastKnownName() + "</white>."));
    }

    private void handleWarnCommand(CommandSender sender, String[] args) {
        if (!Perms.staff(sender)) {
            Msg.send(sender, Msg.error("You must be staff to warn players."));
            return;
        }
        if (args.length < 2) {
            if (sender instanceof Player p) {
                plugin.guiMenu().openWarningsPanel(p, null);
            } else {
                Msg.send(sender, Msg.info("Usage: <white>/warn <player> [reason]</white>"));
            }
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Msg.send(sender, Msg.error("Player not online: <white>" + args[1] + "</white>"));
            return;
        }
        List<String> reasons = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            reasons.add(args[i]);
        }
        String reason = String.join(" ", reasons);
        var entry = plugin.warnService().warn(target, sender.getName(), reason);
        if (entry == null) {
            Msg.send(sender, Msg.error("Could not warn that player."));
            return;
        }
        int total = plugin.warnService().count(target.getUniqueId());
        Msg.send(sender, Msg.success("Warned <white>" + target.getName() + "</white>"
            + (reason.isBlank() ? "" : " <gray>— " + Msg.escapeMini(reason) + "</gray>")
            + " <dark_gray>(" + total + " warning" + (total == 1 ? "" : "s") + ")</dark_gray>"));
        if (sender instanceof Player staff) {
            plugin.guiMenu().openWarningsPanel(staff, target.getUniqueId());
        }
    }

    /**
     * /bb displays           — live diagnostics of the display pipeline (sidebar, tab, leaderboards)
     * /bb displays repair    — re-spawns missing lobby holograms and re-applies the sidebar right now
     */
    private void handleDisplaysCommand(CommandSender sender, String[] args) {
        if (!Perms.check(sender, Perms.DISPLAYS, "inspect the display pipeline")) {
            return;
        }
        if (args.length >= 2 && (args[1].equalsIgnoreCase("repair") || args[1].equalsIgnoreCase("fix"))) {
            int respawned = plugin.reconcileDisplays(true);
            Msg.send(sender, Msg.success("Displays repaired: <white>" + respawned
                + "</white> hologram(s) re-spawned, sidebar re-applied to <white>"
                + Bukkit.getOnlinePlayers().size() + "</white> player(s)."));
            return;
        }
        Msg.send(sender, Msg.info("<dark_gray>\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501</dark_gray>"));
        Msg.send(sender, "<gold><bold>\u2b50 Display diagnostics</bold></gold>");
        Msg.send(sender, "<gray>UI task:</gray> " + (plugin.uiTaskAlive()
            ? "<" + Palette.SUCCESS + ">alive</" + Palette.SUCCESS + "> <dark_gray>(sidebar every 10 ticks, tab every 30)</dark_gray>"
            : "<" + Palette.DANGER + ">STOPPED — run /bb displays repair</" + Palette.DANGER + ">"));
        var faults = plugin.uiFaultCounts();
        if (faults.isEmpty()) {
            Msg.send(sender, "<gray>UI faults:</gray> <" + Palette.SUCCESS + ">none</" + Palette.SUCCESS + ">");
        } else {
            faults.forEach((step, count) -> Msg.send(sender, "<gray>Fault </gray><white>" + step
                + "</white><gray>:</gray> <" + Palette.DANGER + ">" + count + "</" + Palette.DANGER + ">"));
        }
        var holo = plugin.lobbyHolograms();
        if (holo != null) {
            int missing = holo.countNeedingRespawn();
            Msg.send(sender, "<gray>Lobby holograms:</gray> <white>" + holo.allHolograms().size()
                + "</white> <dark_gray>\u00b7</dark_gray> <gray>missing:</gray> "
                + (missing > 0 ? "<" + Palette.DANGER + ">" + missing + "</" + Palette.DANGER + ">" : "<" + Palette.SUCCESS + ">0</" + Palette.SUCCESS + ">"));
        }
        int online = Bukkit.getOnlinePlayers().size();
        int withSidebar = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.sidebarManager() != null && plugin.sidebarManager().hasSidebar(p)) {
                withSidebar++;
            }
        }
        Msg.send(sender, "<gray>Sidebar rendered:</gray> <white>" + withSidebar + "/" + online + "</white>"
            + (online > 0 && withSidebar < online ? " <" + Palette.DANGER + ">(some players lost it)</" + Palette.DANGER + ">" : ""));
        var index = plugin.statsManager().leaderboardIndex();
        Msg.send(sender, "<gray>Leaderboard index:</gray> " + (index == null
            ? "<dark_gray>off</dark_gray>"
            : "<white>" + index.cacheSize() + "</white> <gray>entries cached</gray>"));
        Msg.send(sender, "<gray>Repair now:</gray> <white>/bb displays repair</white>");
        Msg.send(sender, Msg.info("<dark_gray>\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501</dark_gray>"));
    }

    /**
     * /warnings <player>            — full chat record (insults, mutes, staff warns, mute timer)
     * /warnings <player> clear      — wipes the insult counter (keeps the mute history)
     * /warnings <player> clear all  — wipes insults + mutes and lifts the mute
     */
    private void handleWarningsCommand(CommandSender sender, String[] args) {
        if (!Perms.staff(sender)) {
            Msg.send(sender, Msg.error("You must be staff to read warnings."));
            return;
        }
        String name = args[1];
        java.util.UUID targetId = plugin.warnService().resolve(name);
        Player online = Bukkit.getPlayerExact(name);
        if (targetId == null && online != null) {
            targetId = online.getUniqueId();
        }
        fr.bosketball.model.PlayerProfile byName = plugin.statsManager().findByNameIgnoreCase(name);
        if (targetId == null && byName != null) {
            targetId = byName.uuid();
        }
        if (targetId == null) {
            Msg.send(sender, Msg.error("Unknown player: <white>" + Msg.escapeMini(name) + "</white>"));
            return;
        }
        var profile = plugin.statsManager().getIfPresent(targetId);
        if (profile == null) {
            profile = byName != null ? byName : (online != null ? plugin.statsManager().getOrCreate(online) : null);
        }
        var chat = plugin.moderationService();
        String displayName = plugin.warnService().nameOf(targetId);
        if (displayName == null || displayName.isBlank()) {
            displayName = profile != null ? profile.lastKnownName() : name;
        }
        boolean clear = args.length >= 3
            && (args[2].equalsIgnoreCase("clear") || args[2].equalsIgnoreCase("reset"));
        boolean clearAll = clear && args.length >= 4
            && (args[3].equalsIgnoreCase("all") || args[3].equalsIgnoreCase("mutes"));

        if (clear) {
            int warnsRemoved = clearAll ? plugin.warnService().clear(targetId) : 0;
            if (profile != null && chat != null) {
                chat.resetLadder(profile, clearAll);
            }
            Msg.send(sender, Msg.success("<white>Chat record of <gold>" + displayName
                + "</gold> cleared</white> <gray>(insults"
                + (clearAll ? ", mutes and mute timer" : "") + ")</gray>"
                + (warnsRemoved > 0 ? " <dark_gray>+" + warnsRemoved + " staff warning"
                + (warnsRemoved == 1 ? "" : "s") + " removed</dark_gray>" : "")));
            if (online != null) {
                Msg.send(online, Msg.success("Your chat record was cleaned up by staff."));
            }
            if (plugin.reportService() != null) {
                plugin.reportService().logSanction(displayName, "UNWARN",
                    "by " + sender.getName() + (clearAll ? " (all)" : ""), 0L);
            }
            return;
        }

        Msg.send(sender, Msg.info("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
        Msg.send(sender, "<gold><bold>\u26a0 Chat record</bold></gold> <white>" + displayName + "</white>");
        if (profile == null) {
            Msg.send(sender, "<gray>No chat data loaded for this player.</gray>");
        } else {
            var rec = chat.recordOf(profile);
            Msg.send(sender, "<gray>Insults:</gray> <white>" + rec.insults() + "/"
                + rec.insultsBeforeMute() + "</white> <dark_gray>(next one = "
                + rec.muteHours() + " h mute)</dark_gray>");
            Msg.send(sender, "<gray>Mutes:</gray> <white>" + rec.muteCount() + "/"
                + rec.mutesBeforeBan() + "</white> <dark_gray>(next one = permanent ban)</dark_gray>");
            long now = System.currentTimeMillis();
            if (rec.muted(now)) {
                long leftMin = Math.max(1L, (rec.mutedUntilMillis() - now) / 60_000L);
                Msg.send(sender, "<gray>Muted:</gray> <red>yes</red> <dark_gray>("
                    + leftMin + " min left)</dark_gray>");
            } else {
                Msg.send(sender, "<gray>Muted:</gray> <white>no</white>");
            }
        }
        int warns = plugin.warnService().count(targetId);
        Msg.send(sender, "<gray>Staff warnings:</gray> <white>" + warns + "</white>");
        var entries = plugin.warnService().of(targetId);
        if (!entries.isEmpty()) {
            long now = System.currentTimeMillis();
            int shown = 0;
            for (var entry : entries) {
                if (shown++ >= 4) {
                    Msg.send(sender, "<dark_gray>… and " + (entries.size() - 4) + " more</dark_gray>");
                    break;
                }
                Msg.send(sender, "<dark_gray>•</dark_gray> <white>"
                    + (entry.reason() == null || entry.reason().isBlank() ? "(no reason)" : Msg.escapeMini(entry.reason()))
                    + "</white> <gray>— " + Msg.escapeMini(entry.staffName() == null ? "staff" : entry.staffName())
                    + "</gray> <dark_gray>("
                    + entry.ageLabel(now) + ")</dark_gray>");
            }
        }
        Msg.send(sender, "<dark_gray>Clear:</dark_gray> <white>/warnings " + name
            + " clear</white> <gray>or</gray> <white>/warnings " + name + " clear all</white>");
        Msg.send(sender, Msg.info("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"));
    }

    private void handleUnwarnCommand(CommandSender sender, String[] args) {
        if (!Perms.staff(sender)) {
            Msg.send(sender, Msg.error("You must be staff to remove warnings."));
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, Msg.info("Usage: <white>/unwarn <player> [all]</white>"));
            return;
        }
        String name = args[1];
        java.util.UUID targetId = plugin.warnService().resolve(name);
        if (targetId == null) {
            Msg.send(sender, Msg.error("No warnings found for <white>" + name + "</white>."));
            return;
        }
        boolean all = args.length >= 3 && args[2].equalsIgnoreCase("all");
        String displayName = plugin.warnService().nameOf(targetId);
        int removed;
        if (all) {
            removed = plugin.warnService().clear(targetId);
        } else {
            removed = plugin.warnService().unwarnLast(targetId) ? 1 : 0;
        }
        if (removed == 0) {
            Msg.send(sender, Msg.error(displayName + " has no warnings to remove."));
            return;
        }
        int left = plugin.warnService().count(targetId);
        Msg.send(sender, Msg.success("Removed <white>" + removed + "</white> warning"
            + (removed == 1 ? "" : "s") + " from <gold>" + displayName + "</gold>"
            + " <dark_gray>(" + left + " left)</dark_gray>"));
        Player online = Bukkit.getPlayer(targetId);
        if (online != null) {
            Msg.send(online, Msg.success("A warning was removed from your record."));
        }
        if (sender instanceof Player staff) {
            plugin.guiMenu().openWarningsPanel(staff, targetId);
        }
    }

    private void handleEloAdminCommand(CommandSender sender, String[] args) {
        if (!Perms.check(sender, Perms.ELO, "change ELO")) {
            return;
        }
        String op = "give";
        int base = 1;   // args[base] = player, args[base + 1] = amount
        if (args.length >= 2) {
            String head = args[1].toLowerCase(java.util.Locale.ROOT);
            if (head.equals("give") || head.equals("add") || head.equals("take")
                || head.equals("remove") || head.equals("set")) {
                op = head;
                base = 2;
            }
        }
        if (args.length < base + 2) {
            Msg.sendFramedCard(sender instanceof Player p ? p : ConsoleCommandSenderShim.INSTANCE, "ELO ADMIN",
                List.of(
                    "<gray>Usage:</gray>",
                    "<white>/bb elo give <player> <amount></white> <dark_gray>—</dark_gray> <gray>add ELO</gray>",
                    "<white>/bb elo take <player> <amount></white> <dark_gray>—</dark_gray> <gray>remove ELO</gray>",
                    "<white>/bb elo set <player> <amount></white> <dark_gray>—</dark_gray> <gray>force a value</gray>",
                    "",
                    "<gray>Shortcuts:</gray> <white>/bb giveelo <player> <amount></white> <dark_gray>•</dark_gray> <white>/bb giveelo give <player> <amount></white>"));
            return;
        }
        String targetName = args[base];
        int amount;
        try {
            amount = Integer.parseInt(args[base + 1]);
        } catch (NumberFormatException ex) {
            Msg.send(sender, Msg.error("Invalid ELO amount: <white>" + args[base + 1] + "</white> (expected a whole number)"));
            return;
        }
        Player online = Bukkit.getPlayerExact(targetName);
        if (online == null) {

            online = Bukkit.getPlayer(targetName);
        }
        PlayerProfile profile = online != null
            ? plugin.statsManager().getOrCreate(online)
            : plugin.statsManager().findByNameIgnoreCase(targetName);
        if (profile == null) {
            Msg.send(sender, Msg.error("Unknown player: <white>" + targetName + "</white>"));
            return;
        }
        int before = profile.elo();

        boolean clamped = false;
        switch (op) {
            case "give", "add" -> {
                profile.adjustElo(Math.max(0, amount));
                clamped = (long) before + amount > 99_999L;
            }
            case "take", "remove" -> {
                profile.adjustElo(-Math.max(0, amount));
                clamped = (long) before - amount < 100L;
            }
            // Round 27: /giveelo set accepted 0 ELO while adjustElo floors at 100.
            default -> {
                int target = Math.clamp(amount, 100, 99_999);
                clamped = target != amount;
                profile.setElo(target);
            }
        }
        plugin.statsManager().refreshLeaderboardBadges();

        plugin.statsManager().markDirty(profile.uuid());
        plugin.statsManager().saveProfileAsync(profile);
        if (online != null) {
            if (plugin.sidebarManager() != null) {
                plugin.sidebarManager().updatePlayerSidebar(online, plugin.gameManager());
            }
            if (plugin.tabListManager() != null) {
                plugin.tabListManager().clearPlayerCache(online.getUniqueId());
            }
            Msg.send(online, Msg.info("Your ELO is now <white>" + profile.elo() + "</white>."));
        }
        Msg.send(sender, Msg.success("<white>" + profile.lastKnownName() + "</white> ELO: <white>"
            + before + "</white> → <" + Palette.GOLD + ">" + profile.elo() + "</" + Palette.GOLD + ">"
            + (clamped ? " <gray>(clamped to the 100–99999 range)</gray>" : "")));
    }

    private void handleGuildChatCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, Msg.error("This command is for players only."));
            return;
        }
        var guilds = plugin.guildService();
        if (guilds == null || guilds.guildOf(player.getUniqueId()) == null) {
            Msg.send(player, Msg.warn("You are not in a guild — create or join one with <white>/guild</white>."));
            return;
        }
        String message = args.length <= 1
            ? ""
            : String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        if (message.isEmpty()) {
            boolean on = guilds.toggleGuildChat(player);
            Msg.send(player, on
                ? Msg.success("Guild chat <white><bold>ON</bold></white> — your messages now go to your guild only. <gray>/g again to turn it off.</gray>")
                : Msg.info("Guild chat <white><bold>OFF</bold></white> — back to global chat."));
            Msg.playPersonalPackSound(player, "ui.click", 0.7f, on ? 1.35f : 1.05f);
            return;
        }
        // args[0] is the sub-command itself ("gchat" / "g" / "gc"): never echo it in the message.
        guilds.sendGuildChat(player, message);
    }

    private void handleGuildCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Msg.parse(Msg.error("Players only — guilds are driven from the GUI.")));
            return;
        }
        var guilds = plugin.guildService();
        var myGuild = guilds.guildOf(p.getUniqueId());
        String sub = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";

        if (sub.isEmpty()) {
            plugin.guiMenu().openGuildMenu(p);
            return;
        }

        switch (sub) {
            case "create", "new" -> {
                if (args.length < 3) {
                    Msg.send(p, "Usage: <yellow>/guild create <name> [tag]</yellow>");
                    return;
                }
                reply(p, guilds.create(p, args[2], args.length >= 4 ? args[3] : null));
            }
            case "invite", "add" -> {
                if (args.length < 3) {
                    Msg.send(p, "Usage: <yellow>/guild invite <player></yellow>");
                    return;
                }
                reply(p, guilds.invite(p, args[2]));
            }
            case "join", "accept" -> reply(p, guilds.acceptInvite(p, args.length >= 3 ? args[2] : null));
            case "decline", "deny" -> reply(p, guilds.declineInvite(p, args.length >= 3 ? args[2] : null));
            case "leave" -> reply(p, guilds.leave(p));
            case "kick" -> {
                if (args.length < 3) {
                    Msg.send(p, "Usage: <yellow>/guild kick <player></yellow>");
                    return;
                }
                reply(p, guilds.kick(p, args[2]));
            }
            case "promote" -> {
                if (args.length < 3) {
                    Msg.send(p, "Usage: <yellow>/guild promote <player></yellow>");
                    return;
                }
                reply(p, guilds.promote(p, args[2]));
            }
            case "demote" -> {
                if (args.length < 3) {
                    Msg.send(p, "Usage: <yellow>/guild demote <player></yellow>");
                    return;
                }
                reply(p, guilds.demote(p, args[2]));
            }
            case "transfer", "leader" -> {
                if (args.length < 3) {
                    Msg.send(p, "Usage: <yellow>/guild transfer <player></yellow>");
                    return;
                }
                reply(p, guilds.transferLeadership(p, args[2]));
            }
            case "disband", "delete" -> reply(p, guilds.disband(p));
            case "open" -> reply(p, guilds.setOpenJoin(p, true));
            case "close" -> reply(p, guilds.setOpenJoin(p, false));
            case "motd" -> {
                if (args.length < 3) {
                    Msg.send(p, "Usage: <yellow>/guild motd <message></yellow>");
                    return;
                }
                reply(p, guilds.setMotd(p, String.join(" ",
                    java.util.Arrays.copyOfRange(args, 2, args.length))));
            }
            case "top", "leaderboard", "lb" -> {
                var top = guilds.topByElo(10);
                java.util.List<String> lines = new java.util.ArrayList<>();
                if (top.isEmpty()) {
                    lines.add("<gray>No guilds yet — open the guild hub to found the first one.</gray>");
                }
                int place = 1;
                for (var g : top) {
                    lines.add("<gray>" + place + ".</gray> " + g.displayTag() + " " + g.displayName()
                        + " <dark_gray>•</dark_gray> <" + Palette.GOLD + ">" + g.elo() + " ELO</" + Palette.GOLD + ">"
                        + " <dark_gray>(" + g.members().size() + " members, "
                        + Math.round(g.winRate() * 100) + "% WR)</dark_gray>");
                    place++;
                }
                Msg.sendFramedCard(p, "GUILD LEADERBOARD", lines);
            }
            default -> {

                if (myGuild == null) {
                    plugin.guiMenu().openGuildMenu(p);
                    return;
                }
                java.util.List<String> lines = new java.util.ArrayList<>();
                lines.add(myGuild.displayNameAndTag());
                lines.add("<gray>ELO:</gray> <" + Palette.GOLD + ">" + myGuild.elo() + "</" + Palette.GOLD + ">"
                    + " <dark_gray>•</dark_gray> <gray>Rank:</gray> <white>#"
                    + guilds.placementOf(myGuild) + "</white>"
                    + " <dark_gray>•</dark_gray> <gray>Record:</gray> <white>"
                    + myGuild.matchesWon() + "W/" + myGuild.losses() + "L</white>");
                if (!myGuild.motd().isBlank()) {
                    lines.add("<gray>MOTD:</gray> <white>" + myGuild.motd() + "</white>");
                }
                lines.add("");
                for (var m : myGuild.sortedMembers()) {
                    boolean online = org.bukkit.Bukkit.getPlayer(m.uuid()) != null;
                    lines.add((online ? "<" + Palette.SUCCESS + ">●</" + Palette.SUCCESS + "> " : "<" + Palette.DIM + ">○</" + Palette.DIM + "> ")
                        + m.role().coloredTitle() + " <white>" + m.name() + "</white>"
                        + " <dark_gray>(" + m.contributedElo() + " ELO)</dark_gray>");
                }
                lines.add("");
                lines.add("<yellow>/guild</yellow> <dark_gray>—</dark_gray> <gray>open the guild hub</gray>");
                Msg.sendFramedCard(p, "GUILD • " + myGuild.name().toUpperCase(java.util.Locale.ROOT), lines);
            }
        }
    }

    private static void reply(Player player, fr.bosketball.social.GuildService.ActionResult result) {
        Msg.send(player, result.success() ? Msg.success(result.message()) : Msg.error(result.message()));
    }

    private void handleCrateCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            return;
        }
        if (args.length >= 2 && "set".equalsIgnoreCase(args[1]) && isStaff(p)) {
            plugin.crateStation().savePose(p.getLocation());
            plugin.crateStation().spawn();
            Msg.send(p, Msg.success("Reward Crate pose saved at your location."));
            return;
        }
        if (args.length >= 2 && "open".equalsIgnoreCase(args[1])) {
            plugin.crateStation().handleClick(p);
            return;
        }
        var crates = plugin.settings().crates();
        Msg.sendFramedCard(p, "REWARD CRATE", java.util.List.of(
            "<gray>Cost:</gray> " + Msg.coinsBadge(crates.costCoins()),
            "<gray>Daily limit:</gray> <white>" + crates.dailyLimit() + "</white> <dark_gray>•</dark_gray> "
                + "<gray>Cosmetic chance:</gray> <white>" + Math.round(crates.cosmeticChance() * 100) + "%</white>",
            "<gray>Walk to the crate in the plaza and right-click it, or</gray> <yellow>/crate open</yellow>"
        ));
    }

    private void sendServerStatus(CommandSender sender) {
        if (!Perms.has(sender, Perms.STAFF) && !Perms.has(sender, Perms.AUDIT)) {
            Msg.send(sender, Msg.error("Staff only."));
            return;
        }
        var index = plugin.statsManager().leaderboardIndex();
        var storage = plugin.integration().storage();
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("<gray>TPS:</gray> <white>" + String.format(java.util.Locale.ROOT, "%.2f", org.bukkit.Bukkit.getTPS()[0]) + "</white>"
            + " <dark_gray>•</dark_gray> <gray>Profiles:</gray> <white>" + plugin.statsManager().allProfiles().size() + "</white>"
            + " <dark_gray>•</dark_gray> <gray>Arenas:</gray> <white>" + plugin.arenaManager().allArenas().size() + "</white>");
        lines.add("<gray>Database:</gray> <white>" + storage.backendName() + "</white>"
            + " <dark_gray>•</dark_gray> <gray>Dirty profiles:</gray> <white>" + plugin.statsManager().dirtyCount() + "</white>");
        lines.add("<gray>Leaderboard cache:</gray> <white>" + (index != null ? index.cacheSize() : 0)
            + " entries / " + (index != null ? index.refreshMillis() / 1000 : 0) + "s</white>"
            + " <dark_gray>•</dark_gray> <gray>Queue:</gray> <white>" + plugin.gameManager().queuedCount() + "</white>"
            + " <dark_gray>•</dark_gray> <gray>In match:</gray> <white>" + plugin.gameManager().activePlayerCount() + "</white>");
        lines.add("<gray>Reports open:</gray> <white>" + plugin.reportService().openReports().size() + "</white>"
            + " <dark_gray>•</dark_gray> <gray>Online:</gray> <white>"
            + org.bukkit.Bukkit.getOnlinePlayers().size() + "</white>");
        Msg.sendFramedCard(sender instanceof Player p ? p : ConsoleCommandSenderShim.INSTANCE, "SERVER STATUS", lines);
    }

    private static final class ConsoleCommandSenderShim {
        static final net.kyori.adventure.audience.Audience INSTANCE = net.kyori.adventure.audience.Audience.empty();
    }
}
