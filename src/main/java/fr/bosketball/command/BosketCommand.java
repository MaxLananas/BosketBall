package fr.bosketball.command;

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
import fr.bosketball.ui.LockerRoom3dMenuManager;
import fr.bosketball.util.Msg;
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

/**
 * Full Command Suite & Context-Aware TabCompleter for /bosketball (/bb, /basket, /hoops).
 */
public final class BosketCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
        "menu", "play", "join", "leave", "practice", "bot", "3pt", "duel",
        "shop", "tags", "ranks", "locker", "coins", "history", "friends", "party", "settings",
        "discord", "mail", "welcome", "hotbar", "stats", "top", "skin", "dunk", "3d",
        "admin", "maintenance", "mutechat", "map", "arena", "rank", "giveball", "forcestart", "stop", "save", "reload", "help"
    );

    private static final List<String> MAP_SUBS = List.of(
        "gui", "auto", "create", "edit", "wand", "pos1", "pos2", "barrier1", "barrier2",
        "ceiling", "hoop", "lines", "setspawn", "jumbotron", "preview", "info", "save", "help"
    );

    public static boolean isArenaArchitectAuthorized(CommandSender sender) {
        return sender instanceof org.bukkit.command.ConsoleCommandSender || (sender != null && sender.isOp());
    }

    private static String[] normalizeShortcutArgs(Command command, String label, String[] args) {
        String cmdName = command != null ? command.getName().toLowerCase() : label.toLowerCase();
        String mapped = switch (cmdName) {
            case "play" -> "play";
            case "practice" -> "practice";
            case "leave" -> "leave";
            case "shop", "store" -> "shop";
            case "ranks", "elo", "guide" -> "ranks";
            case "friend", "friends", "f" -> "friends";
            case "party", "p" -> "party";
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
        String[] args = normalizeShortcutArgs(command, label, rawArgs);
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player p)) {
                Msg.send(sender, Msg.info("Use <white>/bb help</white> from the console."));
                return true;
            }
            plugin.guiMenu().openMainHub(p);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help" -> sendHelp(sender);

            case "discord" -> {
                Msg.sendDiscordCard(sender, plugin.settings().links().discord());
            }

            case "3d", "stage" -> {
                if (!(sender instanceof Player p)) {
                    return true;
                }
                if (args.length >= 2 && p.hasPermission("bosketball.admin")) {
                    String act = args[1].toLowerCase();
                    switch (act) {
                        case "setcamera", "camera", "cam" -> {
                            plugin.lockerRoom3dManager().setCustomCamera(p.getLocation());
                            Msg.send(p, Msg.success("Locker Room camera saved at your current view. (Use /bb 3d setslot 1..4 to place player pedestals)"));
                            return true;
                        }
                        case "setslot", "slot" -> {
                            int slotNum = 1;
                            if (args.length >= 3) {
                                try {
                                    slotNum = Math.clamp(Integer.parseInt(args[2]), 1, 4);
                                } catch (NumberFormatException ignored) {
                                }
                            }
                            plugin.lockerRoom3dManager().setCustomSlot(slotNum, p.getLocation());
                            Msg.send(p, Msg.success("Locker Room player pedestal #" + slotNum + "/4 saved."));
                            return true;
                        }
                        case "resetstage", "reset" -> {
                            plugin.lockerRoom3dManager().resetCustomStage();
                            Msg.send(p, Msg.info("Locker Room reset to the default built-in room."));
                            return true;
                        }
                        default -> {
                        }
                    }
                }
                plugin.lockerRoom3dManager().open(p, LockerRoom3dMenuManager.TopTab.PLAY);
            }

            case "shop", "store" -> {
                if (sender instanceof Player p) {
                    if (args.length >= 2) {
                        switch (args[1].toLowerCase()) {
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
                if (sender instanceof Player p) {
                    plugin.guiMenu().openRanksGuide(p);
                }
            }

            case "tags", "tag" -> handleTagCommand(sender, args);

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
                    plugin.guiMenu().openFriendsMenu(p);
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
                String act = args[1].toLowerCase();
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
                plugin.lockerRoom3dManager().refreshAllOpenStages(true);
            }

            case "mail", "inbox" -> {
                if (sender instanceof Player p) {
                    PlayerProfile prof = plugin.statsManager().getOrCreate(p);
                    prof.setUnreadMailCount(0);
                    plugin.statsManager().saveProfileAsync(prof);
                    Msg.sendFramedCard(p, "BosketBall • Inbox", List.of(
                        "<gold><bold>Welcome to BosketBall!</bold></gold>",
                        "<gray>• <aqua>/bb play</aqua> : Queue 1v1, 2v2, 3v3, 5v5 or Solo Practice</gray>",
                        "<gray>• <aqua>/bb shop</aqua> : Spend match Coins on Tags, Basketballs & Dunk Styles</gray>",
                        "<gray>• <aqua>/discord</aqua> : Join our community Discord server</gray>"
                    ));
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
                if (args.length == 1) {
                    plugin.guiMenu().openPlaySelector(p);
                    return true;
                }
                GameModeType mode = GameModeType.byIdOrNull(args[1]);
                if (mode != null) {
                    String arenaId = args.length >= 3 ? args[2] : null;
                    plugin.gameManager().quickJoin(p, mode, arenaId);
                } else {
                    plugin.gameManager().quickJoin(p, GameModeType.STREET_2V2, args[1]);
                }
            }

            case "leave", "quit" -> {
                if (sender instanceof Player p) {
                    boolean wasQueued = plugin.gameManager().getQueuedMode(p.getUniqueId()) != null;
                    if (wasQueued) {
                        plugin.gameManager().leaveQueue(p, true);
                        return true;
                    }
                    MatchSession s = plugin.gameManager().getSessionOf(p.getUniqueId());
                    if (s == null) {
                        Msg.send(p, Msg.error("You are not currently in a queue or match."));
                    } else {
                        s.removePlayer(p, false);
                    }
                }
            }

            case "practice" -> {
                if (sender instanceof Player p) {
                    plugin.gameManager().quickJoin(p, GameModeType.PRACTICE, args.length >= 2 ? args[1] : null);
                }
            }

            case "bot", "defender" -> {
                if (sender instanceof Player p) {
                    MatchSession s = plugin.gameManager().getSessionOf(p.getUniqueId());
                    if (s == null) {
                        Msg.send(p, Msg.info("Enter Practice first with <white>/bb practice</white>."));
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
                    Msg.send(p, Msg.info("Usage: <white>/bb duel <player></white>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    Msg.send(p, Msg.error("Player not online: <white>" + args[1] + "</white>"));
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
                Msg.sendFramedCard(sender, "Profile • " + target.getName(), List.of(
                    " <gray>▸ Rank:</gray> <!shadow><white>" + prof.effectiveRank().pillGlyph() + "</white></!shadow> <gold>(" + prof.elo() + " ELO)</gold> <dark_gray>•</dark_gray> <gray>Coins:</gray> " + Msg.coinsBadge(prof.coins()),
                    " <gray>▸ Record:</gray> <green>" + prof.wins() + "W</green> <dark_gray>/</dark_gray> <red>" + prof.losses() + "L</red> <dark_gray>(🔥" + prof.winStreak() + ")</dark_gray> <gray>• Playtime:</gray> <white>" + prof.formattedPlayTimeEn() + "</white>",
                    " <gray>▸ Points:</gray> <yellow>" + prof.points() + "</yellow> <dark_gray>•</dark_gray> <gray>MVPs:</gray> <gold>" + prof.mvps() + "</gold> <dark_gray>•</dark_gray> <gray>Assists:</gray> <aqua>" + prof.assists() + "</aqua>",
                    " <gray>▸ Shooting:</gray> <aqua>" + String.format("%.1f%%", prof.fieldGoalPercentage()) + " FG</aqua> <dark_gray>•</dark_gray> <light_purple>"
                        + String.format("%.1f%%", prof.threePointPercentage()) + " 3PT</light_purple> <dark_gray>•</dark_gray> <gray>Dunks:</gray> <red>" + prof.dunks() + "</red>"
                ));
            }

            case "pack" -> {
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
                if (!prof.hasUnlockedBall(skin) && !p.hasPermission("bosketball.admin")) {
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
                if (!prof.hasUnlockedDunk(style) && !p.hasPermission("bosketball.admin")) {
                    Msg.send(p, Msg.error("Unlock <white>" + style.displayName() + "</white> (<gold>" + style.coinPrice() + " Coins</gold>) in <yellow>/bb shop cosmetics</yellow>!"));
                    return true;
                }
                prof.setSelectedDunkStyle(style);
                plugin.statsManager().saveProfileAsync(prof);
                Msg.send(p, Msg.success("Dunk Style equipped: <gold>" + style.displayName() + "</gold>"));
            }

            case "admin", "panel" -> {
                if (sender instanceof Player p) {
                    plugin.guiMenu().openServerAdminPanel(p);
                } else {
                    Msg.send(sender, Msg.info("Online: " + Bukkit.getOnlinePlayers().size()
                        + " | Active Matches: " + plugin.gameManager().activePlayerCount()
                        + " | Maintenance: " + plugin.settings().motd().maintenanceMode()));
                }
            }

            case "maintenance", "maint" -> {
                if (!sender.hasPermission("bosketball.admin") && !sender.isOp()) {
                    Msg.send(sender, Msg.error("Admin permission required."));
                    return true;
                }
                boolean targetState = args.length >= 2
                    ? (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true") || args[1].equalsIgnoreCase("enable"))
                    : !plugin.settings().motd().maintenanceMode();
                plugin.setMaintenanceModeLive(targetState);
                Msg.send(sender, Msg.success("Maintenance Mode: " + (targetState ? "<#F87171><bold>ON</bold></#F87171>" : "<#4ADE80><bold>OFF</bold></#4ADE80>")));
            }

            case "mutechat" -> {
                if (!sender.hasPermission("bosketball.staff") && !sender.hasPermission("bosketball.admin") && !sender.isOp()) {
                    Msg.send(sender, Msg.error("Staff permission required."));
                    return true;
                }
                boolean muted = !plugin.isGlobalChatMuted();
                plugin.setGlobalChatMuted(muted);
                Bukkit.broadcast(Msg.prefixed(muted
                    ? "<#F87171><bold>Global chat has been muted by staff.</bold></#F87171>"
                    : "<#4ADE80><bold>Global chat has been unmuted!</bold></#4ADE80>"));
            }

            case "map" -> handleCustomMapArchitect(sender, args);

            case "arena" -> handleArenaAdmin(sender, args);

            case "rank" -> handleRankAdmin(sender, args);

            case "giveball" -> {
                if (!sender.hasPermission("bosketball.admin") || !(sender instanceof Player p)) {
                    Msg.send(sender, Msg.error("Permission required: bosketball.admin"));
                    return true;
                }
                BallSkin skin = args.length >= 2 ? BallSkin.byNameOrDefault(args[1], BallSkin.CLASSIC) : BallSkin.CLASSIC;
                p.getInventory().addItem(skin.createBallItem(false));
                p.getInventory().addItem(BallSkin.createArchitectWandItem("custom"));
                p.getInventory().addItem(BallSkin.createCustomModelItem(Material.GOLDEN_CARROT, "trophy_mvp", "<gold><bold>🏆 MVP Trophy</bold></gold>", List.of()));
                p.getInventory().addItem(BallSkin.createCustomModelItem(Material.IRON_NUGGET, "referee_whistle", "<aqua><bold>📢 Referee Whistle</bold></aqua>", List.of()));
                p.getInventory().addItem(BallSkin.createCustomModelItem(Material.LEATHER_BOOTS, "sneakers_air", "<red><bold>👟 Air Bosket</bold></red>", List.of()));
                p.getInventory().addItem(BallSkin.createPlaybookItem());
                Msg.send(p, Msg.success("BosketBall admin items & Architect Wand added to your inventory."));
            }

            case "forcestart" -> {
                if (!(sender instanceof Player p)) {
                    return true;
                }
                boolean isAdmin = sender.hasPermission("bosketball.admin");
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
                if (!sender.hasPermission("bosketball.admin")) {
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
                if (!sender.hasPermission("bosketball.admin")) {
                    Msg.send(sender, Msg.error("Permission required: bosketball.admin"));
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

            case "reload" -> {
                if (!sender.hasPermission("bosketball.admin")) {
                    Msg.send(sender, Msg.error("Permission required: bosketball.admin"));
                    return true;
                }
                long t0 = System.nanoTime();
                plugin.reloadBosketConfig();
                long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
                Msg.send(sender, Msg.success("Reloaded <white>config.yml</white>, <white>permissions.yml</white>, <white>shop.yml</white>, <white>spawn.yml</white> & <white>locker-room.yml</white> in <aqua>" + elapsedMs + "ms</aqua>."));
            }

            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleTagCommand(CommandSender sender, String[] args) {
        // /bb tag grant|revoke <player> <tag> — strictly MaxLananas, OP, or Console!
        if (args.length >= 4 && (args[1].equalsIgnoreCase("grant") || args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("revoke") || args[1].equalsIgnoreCase("take"))) {
            boolean canGrant = !(sender instanceof Player)
                || sender.isOp()
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
            if (!tag.canBeUsedBy(prof, p.getName(), staffBypass)) {
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
            if (!sender.hasPermission("bosketball.admin")) {
                Msg.send(sender, Msg.error("Permission required: bosketball.admin"));
                return;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                Msg.send(sender, Msg.error("Player not online: " + args[2]));
                return;
            }
            int amount;
            try {
                amount = Math.max(0, Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                Msg.send(sender, Msg.error("Invalid coin amount: " + args[3]));
                return;
            }
            PlayerProfile prof = plugin.statsManager().getOrCreate(target);
            String op = args[1].toLowerCase();
            if (op.equals("give") || op.equals("add")) {
                prof.addCoins(amount);
            } else if (op.equals("take") || op.equals("remove")) {
                prof.removeCoins(amount);
            } else {
                prof.setCoins(amount);
            }
            plugin.statsManager().refreshLeaderboardBadges();
            plugin.statsManager().saveProfileAsync(prof);
            if (plugin.sidebarManager() != null) {
                plugin.sidebarManager().updatePlayerSidebar(target, plugin.gameManager());
            }
            Msg.send(sender, Msg.success("Updated <white>" + target.getName() + "</white>'s balance: " + Msg.coinsBadge(prof.coins())));
            return;
        }

        Player target = args.length >= 2 ? Bukkit.getPlayer(args[1]) : (sender instanceof Player p ? p : null);
        if (target == null) {
            Msg.send(sender, Msg.info("Usage: <white>/bb coins [player]</white> or <white>/bb coins <give|take|set> <player> <amount></white>"));
            return;
        }
        PlayerProfile prof = plugin.statsManager().getOrCreate(target);
        Msg.send(sender, Msg.info("<white>" + target.getName() + "</white>'s balance: " + Msg.coinsBadge(prof.coins())
            + " <dark_gray>•</dark_gray> <click:run_command:'/bb shop'><#FBBF24>[Open Shop]</#FBBF24></click>"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //   CUSTOM BASKETBALL MAP ARCHITECT SUITE (/bb map ... — Strictly OP Only!)
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleCustomMapArchitect(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p) || !isArenaArchitectAuthorized(p)) {
            Msg.send(sender, Msg.error("Arena creation (<white>/bb map</white>) is strictly reserved for Server Operators (OP)."));
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

        String action = args[1].toLowerCase();
        switch (action) {
            case "auto", "autosetup", "quick" -> {
                Arena built = plugin.mapEditor().autoSetupFullCourt(p);
                Msg.send(p, Msg.success("Auto-configured full court <white><bold>" + built.id() + "</bold></white> (Hoops + Backboards + White Lines + Scoreboard + Barriers)! "
                    + "<click:run_command:'/bb practice " + built.id() + "'><#4ADE80><bold>[▶ Test Court Now]</bold></#4ADE80></click>"));
                Msg.playPersonalPackSound(p, "game.green_release", 0.9f, 1.15f);
            }
            case "create", "edit" -> {
                String id = args.length >= 3 ? args[2] : "map" + (plugin.arenaManager().allArenas().size() + 1);
                String display = SmallCaps.toSmallCaps(
                    args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : id
                );
                Arena arena = plugin.mapEditor().createOrSelectCustomMap(p, id, display);
                p.sendMessage(Msg.parse(
                    "\n<gradient:#00F0FF:#FFD43B><bold>✦ Map Architect: " + arena.displayName() + " (" + arena.id() + ") ✦</bold></gradient>\n"
                        + "<gray>Zero blocks of your build are modified! Use the <aqua><bold>Architect Wand</bold></aqua> or commands:</gray>\n"
                        + " <yellow>1.</yellow> <white>Left / Right-Click Block</white> <gray>(or <yellow>/bb map pos1</yellow> & <yellow>pos2</yellow>) ➔ Court Out-of-Bounds</gray>\n"
                        + " <aqua>2.</aqua> <white>Sneak + Left / Right-Click</white> <gray>(or <aqua>/bb map barrier1</aqua> & <aqua>barrier2</aqua>) ➔ Arena Barrier Cage</gray>\n"
                        + " <green>3.</green> <click:suggest_command:'/bb map hoop ignis'><yellow>/bb map hoop ignis [3d|custom]</yellow></click> & <click:suggest_command:'/bb map hoop frost'><yellow>/bb map hoop frost [3d|custom]</yellow></click> <gray>➔ Place Both Hoops</gray>\n"
                        + " <white>4.</white> <click:run_command:'/bb map lines on'><white>/bb map lines <on|off|preset|color|width></white></click> <gray>➔ Flush White Court Lines</gray>\n"
                        + " <gold>5.</gold> <click:suggest_command:'/bb map setspawn ignis'><gold>/bb map setspawn <ignis|frost|spectator|lobby> [1..5]</gold></click> <gray>➔ Spawn Points</gray>\n"
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
                    Msg.send(p, Msg.success("Ball ceiling height set to <white>+" + String.format("%.1f", h)
                        + " blocks</white> (Y = " + String.format("%.1f", g.ceilingY()) + ")."));
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
                String sideArg = args[2].toLowerCase();
                boolean isIgnisTarget = sideArg.startsWith("ignis") || sideArg.equals("south") || sideArg.equals("1");
                boolean spawn3d = !(args.length >= 4 && (args[3].equalsIgnoreCase("custom") || args[3].equalsIgnoreCase("blocks") || args[3].equalsIgnoreCase("none")));

                String dirArg = args.length >= 5 ? args[4].toLowerCase() : (args.length >= 4 ? args[3].toLowerCase() : "");
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

                String hoopLabel = isIgnisTarget ? "<#FF4D3D><bold>Ignis Target Hoop</bold></#FF4D3D>" : "<#3DB8FF><bold>Frost Target Hoop</bold></#3DB8FF>";
                Msg.send(p, Msg.success(hoopLabel + " placed at <white>("
                    + String.format("%.1f, %.2f, %.1f", rimCenter.getX(), rimCenter.getY(), rimCenter.getZ())
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
                String target = args[2].toLowerCase();
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
                String subLine = args.length >= 3 ? args[2].toLowerCase() : "on";
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
                                + String.format("%.2f", arena.courtLineWidth()) + " blocks</white>."));
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
                    "\n<gradient:#00F0FF:#FFD43B><bold>✦ Map Info: " + arena.displayName() + " (" + arena.id() + ") ✦</bold></gradient>\n"
                        + " <gray>▸ World:</gray> <white>" + arena.worldName() + "</white> <dark_gray>•</dark_gray> <gray>Custom Map:</gray> <aqua>" + (arena.isCustomMap() ? "Yes" : "Generated Stadium") + "</aqua>\n"
                        + " <gray>▸ Court Bounds:</gray> <yellow>X[" + String.format("%.1f..%.1f", g.minX(), g.maxX()) + "] Z[" + String.format("%.1f..%.1f", g.minZ(), g.maxZ()) + "] • Floor Y=" + String.format("%.1f", g.floorY()) + "</yellow>\n"
                        + " <gray>▸ Barrier Cage:</gray> <aqua>X[" + String.format("%.1f..%.1f", g.barrierMinX(), g.barrierMaxX()) + "] Z[" + String.format("%.1f..%.1f", g.barrierMinZ(), g.barrierMaxZ()) + "] • Ceiling Y=" + String.format("%.1f", g.ceilingY()) + "</aqua>\n"
                        + " <gray>▸ Ignis Hoop:</gray> <#FF6B4A>(" + String.format("%.1f, %.2f, %.1f", g.southHoopRim().getX(), g.southHoopRim().getY(), g.southHoopRim().getZ()) + ") ➔ " + CourtGeometry.cardinalLabel(g.southHoopForward()) + "</#FF6B4A>\n"
                        + " <gray>▸ Frost Hoop:</gray> <#3DB8FF>(" + String.format("%.1f, %.2f, %.1f", g.northHoopRim().getX(), g.northHoopRim().getY(), g.northHoopRim().getZ()) + ") ➔ " + CourtGeometry.cardinalLabel(g.northHoopForward()) + "</#3DB8FF>\n"
                        + " <gray>▸ Court Lines:</gray> <white>" + (arena.isCourtLinesEnabled()
                            ? arena.courtLinesPreset().name() + " (" + arena.courtLinePrimaryMaterial().name() + ", w=" + String.format("%.2f", arena.courtLineWidth()) + ")"
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
            "\n<gradient:#00F0FF:#FFD43B><bold>✦ Custom Basketball Map Architect (/bb map) ✦</bold></gradient>\n"
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
        if (!isArenaArchitectAuthorized(sender)) {
            Msg.send(sender, Msg.error("Arena management (<white>/bb arena</white>) is strictly reserved for Server Operators (OP)."));
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
        switch (args[1].toLowerCase()) {
            case "build", "create" -> {
                if (!(sender instanceof Player p)) {
                    return;
                }
                String id = args.length >= 3 ? args[2].toLowerCase() : "stadium" + (plugin.arenaManager().allArenas().size() + 1);
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

    private void handleRankAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bosketball.admin") || args.length < 4) {
            Msg.send(sender, Msg.info("Usage: <white>/bb rank <set|elo> <player> <rank|value></white>"));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            Msg.send(sender, Msg.error("Player not found."));
            return;
        }
        PlayerProfile prof = plugin.statsManager().getOrCreate(target);
        if (args[1].equalsIgnoreCase("set")) {
            if (args[3].equalsIgnoreCase("auto") || args[3].equalsIgnoreCase("clear")) {
                prof.setStaffRankOverride(null);
            } else {
                Rank rank = Rank.byNameOrNull(args[3]);
                if (rank == null) {
                    Msg.send(sender, Msg.error("Unknown rank: " + args[3]));
                    return;
                }
                if (rank.staffOnly()) {
                    prof.setStaffRankOverride(rank);
                } else {
                    prof.setStaffRankOverride(null);
                    prof.setElo(Math.max(1000, rank.minElo()));
                }
            }
            plugin.statsManager().refreshLeaderboardBadges();
            plugin.statsManager().saveProfileAsync(prof);
            Msg.send(sender, Msg.success("Updated <white>" + target.getName() + "</white>'s rank: <!shadow><white>"
                + prof.effectiveRank().pillGlyph() + "</white></!shadow>"));
        } else if (args[1].equalsIgnoreCase("elo")) {
            try {
                int elo = Integer.parseInt(args[3]);
                prof.setElo(elo);
                plugin.statsManager().refreshLeaderboardBadges();
                plugin.statsManager().saveProfileAsync(prof);
                Msg.send(sender, Msg.success("Set <white>" + target.getName() + "</white>'s ELO to <gold>" + prof.elo() + "</gold>."));
            } catch (NumberFormatException e) {
                Msg.send(sender, Msg.error("Invalid ELO value."));
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        List<String> lines = new ArrayList<>(List.of(
            " <yellow>/play</yellow> <gray>• Browse Ranked 1v1–5v5, 3PT Contest & Practice</gray>",
            " <yellow>/practice</yellow> <gray>• Step onto a Solo Court (+ AI Defender Bot)</gray>",
            " <yellow>/ranks</yellow> <gray>• ELO Ladder (Rookie 1000 → MVP 2200+), #1/#2/#3 Badges & Controls</gray>",
            " <yellow>/friend (/f)</yellow> <gray>• Friends List GUI, online status & requests</gray>",
            " <yellow>/party (/p)</yellow> <gray>• Party Menu &</gray> <#FBBF24>/party name <TeamName></#FBBF24>",
            " <yellow>/shop</yellow> <gray>• Spend Coins on Basketballs, Dunk Styles & Tags</gray>",
            " <yellow>/gg</yellow> <gray>• Good Game (+Coins & XP)</gray>  <dark_gray>|</dark_gray>  <yellow>/discord</yellow> <gray>• Community</gray>"
        ));
        if (isArenaArchitectAuthorized(sender)) {
            lines.add(" <aqua>/bb map</aqua> <gray>• OP 1-Click Visual Arena Studio & Auto-Court Builder</gray>");
        }
        Msg.sendFramedCard(sender, "BosketBall • Quick Guide", lines);
    }

    private static final java.util.Set<String> ADMIN_ONLY_SUBS = java.util.Set.of(
        "admin", "maintenance", "rank", "giveball", "stop", "save", "reload"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] rawArgs) {
        String[] args = normalizeShortcutArgs(command, alias, rawArgs);
        boolean isAdmin = !(sender instanceof Player) || sender.isOp() || sender.hasPermission("bosketball.admin");
        boolean isStaff = isAdmin || sender.hasPermission("bosketball.staff");
        if (args.length == 1) {
            List<String> visible = SUBCOMMANDS.stream()
                .filter(s -> (!s.equals("map") && !s.equals("arena")) || isArenaArchitectAuthorized(sender))
                .filter(s -> !ADMIN_ONLY_SUBS.contains(s) || isAdmin)
                .filter(s -> !s.equals("mutechat") || isStaff)
                .toList();
            return filterPrefix(visible, args[0]);
        }
        String sub = args[0].toLowerCase();
        if ((sub.equals("map") || sub.equals("arena")) && !isArenaArchitectAuthorized(sender)) {
            return List.of();
        }
        if (ADMIN_ONLY_SUBS.contains(sub) && !isAdmin) {
            return List.of();
        }
        List<String> arenaIds = plugin.arenaManager().allArenas().stream().map(Arena::id).toList();

        if (args.length == 2) {
            return switch (sub) {
                case "play", "join" -> filterPrefix(List.of("1v1", "2v2", "3v3", "5v5", "3pt", "practice"), args[1]);
                case "practice", "3pt", "contest" -> filterPrefix(arenaIds, args[1]);
                case "stop" -> isAdmin ? filterPrefix(arenaIds, args[1]) : List.of();
                case "maintenance", "maint" -> isAdmin ? filterPrefix(List.of("on", "off"), args[1]) : List.of();
                case "shop", "store" -> filterPrefix(List.of("all", "balls", "dunks", "tags", "locker"), args[1]);
                case "coins", "coin" -> isAdmin ? filterPrefix(List.of("give", "take", "set"), args[1]) : List.of();
                case "top", "leaderboard" -> filterPrefix(List.of("elo", "coins"), args[1]);
                case "skin" -> filterPrefix(Arrays.stream(BallSkin.values()).map(s -> s.name().toLowerCase()).toList(), args[1]);
                case "giveball" -> isAdmin ? filterPrefix(Arrays.stream(BallSkin.values()).map(s -> s.name().toLowerCase()).toList(), args[1]) : List.of();
                case "tags", "tag" -> {
                    boolean canGrant = !(sender instanceof Player) || sender.isOp() || ChatTag.FOUNDER_USERNAME.equalsIgnoreCase(sender.getName());
                    List<String> opts = new ArrayList<>();
                    for (ChatTag t : ChatTag.values()) {
                        if (!t.isSpecialUnpurchasable() || canGrant) {
                            opts.add(t.id());
                        }
                    }
                    opts.add("off");
                    if (canGrant) {
                        opts.add("grant");
                        opts.add("revoke");
                    }
                    yield filterPrefix(opts, args[1]);
                }
                case "3d", "stage" -> isAdmin ? filterPrefix(List.of("setcamera", "setslot", "resetstage"), args[1]) : List.of();
                case "party", "group", "p" -> filterPrefix(List.of("name", "invite", "join", "leave", "kick", "promote"), args[1]);
                case "friends", "friend", "f" -> filterPrefix(List.of("add", "remove", "decline"), args[1]);
                case "dunk" -> filterPrefix(Arrays.stream(DunkStyle.values()).map(s -> s.name().toLowerCase()).toList(), args[1]);
                case "map" -> filterPrefix(MAP_SUBS, args[1]);
                case "arena" -> filterPrefix(List.of("build", "list", "tp", "delete"), args[1]);
                case "rank" -> filterPrefix(List.of("set", "elo"), args[1]);
                case "duel", "stats" -> null;
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
            if (sub.equals("map")) {
                return switch (args[1].toLowerCase()) {
                    case "edit", "wand", "info" -> filterPrefix(arenaIds, args[2]);
                    case "hoop", "sethoop" -> filterPrefix(List.of("ignis", "frost"), args[2]);
                    case "lines", "courtlines" -> filterPrefix(List.of("on", "off", "preset", "color", "width"), args[2]);
                    case "setspawn", "spawn" -> filterPrefix(List.of("ignis", "frost", "spectator", "lobby"), args[2]);
                    case "jumbotron" -> filterPrefix(List.of("here", "off"), args[2]);
                    case "ceiling" -> filterPrefix(List.of("12", "16", "20", "24"), args[2]);
                    default -> List.of();
                };
            }
            if ((sub.equals("3d") || sub.equals("stage"))
                && (args[1].equalsIgnoreCase("setslot") || args[1].equalsIgnoreCase("slot"))) {
                return filterPrefix(List.of("1", "2", "3", "4"), args[2]);
            }
            if (sub.equals("rank") || sub.equals("coins") || sub.equals("coin")
                || ((sub.equals("tags") || sub.equals("tag")) && (args[1].equalsIgnoreCase("grant") || args[1].equalsIgnoreCase("revoke")))) {
                return null;
            }
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
                List<String> ranks = new ArrayList<>(Arrays.stream(Rank.values()).map(r -> r.name().toLowerCase()).toList());
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
                return switch (args[2].toLowerCase()) {
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
        return switch (raw.trim().toLowerCase()) {
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
        String low = prefix.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(low)).toList();
    }
}
