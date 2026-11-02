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
        "shop", "tags", "locker", "coins", "history", "friends", "party", "settings",
        "discord", "mail", "welcome", "hotbar", "stats", "top", "skin", "dunk", "3d",
        "map", "arena", "rank", "giveball", "forcestart", "stop", "reload", "help"
    );

    private static final List<String> MAP_SUBS = List.of(
        "create", "edit", "wand", "pos1", "pos2", "barrier1", "barrier2",
        "ceiling", "hoop", "setspawn", "jumbotron", "preview", "info", "save", "help"
    );

    private final BosketBallPlugin plugin;

    public BosketCommand(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
                String url = plugin.getConfig().getString("links.discord", "https://discord.gg/pnJhKuU2QK");
                Msg.sendDiscordCard(sender, url);
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
                    if (args.length >= 2 && (args[1].equalsIgnoreCase("cosmetics") || args[1].equalsIgnoreCase("balls") || args[1].equalsIgnoreCase("dunks"))) {
                        plugin.guiMenu().openCosmetics(p);
                    } else {
                        plugin.guiMenu().openTagsVault(p);
                    }
                }
            }

            case "coins", "coin", "balance", "bal" -> handleCoinsCommand(sender, args);

            case "tags", "tag" -> {
                if (!(sender instanceof Player p)) {
                    return true;
                }
                if (args.length >= 2) {
                    PlayerProfile prof = plugin.statsManager().getOrCreate(p);
                    if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("none") || args[1].equalsIgnoreCase("clear")) {
                        prof.setSelectedTag(null);
                        plugin.statsManager().saveProfileAsync(prof);
                        Msg.send(p, Msg.info("Chat tag unequipped."));
                        return true;
                    }
                    ChatTag tag = ChatTag.fromIdOrNull(args[1]);
                    if (tag == null) {
                        Msg.send(p, Msg.error("Unknown tag. Open <yellow>/bb tags</yellow> to browse all tags."));
                        return true;
                    }
                    boolean staffBypass = p.hasPermission("bosketball.admin") || p.hasPermission("bosketball.tags.all");
                    if (!prof.hasUnlockedTag(tag) && !staffBypass) {
                        Msg.send(p, Msg.error("You haven't unlocked <white>" + tag.displayName()
                            + "</white> (<gold>" + tag.coinPrice() + " Coins</gold>). Open <yellow>/bb shop</yellow> to buy it!"));
                        return true;
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

            case "friends", "friend" -> {
                if (!(sender instanceof Player p)) {
                    return true;
                }
                if (args.length < 2) {
                    plugin.guiMenu().openFriendsAndParty(p);
                    return true;
                }
                if (args[1].equalsIgnoreCase("remove") && args.length >= 3) {
                    plugin.socialManager().removeFriend(p, args[2]);
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

            case "party", "group" -> {
                if (!(sender instanceof Player p)) {
                    return true;
                }
                if (args.length < 2) {
                    plugin.guiMenu().openFriendsAndParty(p);
                    return true;
                }
                String act = args[1].toLowerCase();
                if (act.equals("leave") || act.equals("quit")) {
                    plugin.socialManager().leaveParty(p);
                } else if (act.equals("accept") || act.equals("join")) {
                    plugin.socialManager().acceptPartyInvite(p, args.length >= 3 ? args[2] : null);
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
                    plugin.statsManager().saveAsync();
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
                    Msg.sendIllustratedWelcomeCard(
                        p,
                        plugin.getConfig().getString("links.website", "https://bosketball.net"),
                        plugin.getConfig().getString("links.shop", "https://store.bosketball.net"),
                        plugin.getConfig().getString("links.discord", "https://discord.gg/pnJhKuU2QK"),
                        "Welcome to BosketBall!",
                        "Queue 1v1–5v5, earn Coins on the court & unlock Tags in /bb shop.",
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
                plugin.statsManager().saveAsync();
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
                plugin.statsManager().saveAsync();
                Msg.send(p, Msg.success("Dunk Style equipped: <gold>" + style.displayName() + "</gold>"));
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

            case "reload" -> {
                if (!sender.hasPermission("bosketball.admin")) {
                    Msg.send(sender, Msg.error("Permission required: bosketball.admin"));
                    return true;
                }
                plugin.reloadBosketConfig();
                Msg.send(sender, Msg.success("BosketBall configuration & resource pack service reloaded."));
            }

            default -> sendHelp(sender);
        }
        return true;
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
    //   CUSTOM BASKETBALL MAP ARCHITECT SUITE (/bb map ...)
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleCustomMapArchitect(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bosketball.admin") || !(sender instanceof Player p)) {
            Msg.send(sender, Msg.error("Permission required (in-game): bosketball.admin"));
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("help")) {
            sendMapHelp(p);
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
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
                        + " <gold>4.</gold> <click:suggest_command:'/bb map setspawn ignis'><gold>/bb map setspawn <ignis|frost|spectator|lobby> [1..5]</gold></click> <gray>➔ Spawn Points</gray>\n"
                        + " <light_purple>5.</light_purple> <click:run_command:'/bb map save'><green><bold>[Save & Activate]</bold></green></click> "
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
                + " <gold>/bb map setspawn <ignis|frost|spectator|lobby> [1..5]</gold> <gray>• Team & exit spawns</gray>\n"
                + " <light_purple>/bb map jumbotron <here|off></light_purple> <gray>• Place or hide overhead Jumbotron</gray>\n"
                + " <white>/bb map preview</white> • <white>/bb map info</white> • <green>/bb map save</green>\n"
                + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
        ));
    }

    private void handleArenaAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bosketball.admin")) {
            Msg.send(sender, Msg.error("Permission required: bosketball.admin"));
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, Msg.info("Usage: <white>/bb arena <build|list|tp|delete> [name]</white>"));
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
        Msg.sendFramedCard(sender, "BosketBall • Commands", List.of(
            " <yellow>/bb</yellow> <gray>• Main Hub Menu</gray>  <dark_gray>|</dark_gray>  <aqua>/bb 3d</aqua> <gray>• Locker Room</gray>",
            " <yellow>/bb play [1v1|2v2|3v3|5v5]</yellow> <gray>• Queue for a competitive match</gray>",
            " <yellow>/bb practice</yellow> & <yellow>/bb bot</yellow> <gray>• Solo Court + AI Defender</gray>",
            " <yellow>/bb shop</yellow> <gray>• Buy Tags, Basketballs & Dunk Styles with Coins</gray>",
            " <yellow>/bb coins</yellow> <gray>• Check Coin balance</gray>  <dark_gray>|</dark_gray>  <yellow>/discord</yellow> <gray>• Official Discord</gray>",
            " <yellow>/bb stats</yellow> • <yellow>/bb top</yellow> • <yellow>/bb friends</yellow> • <yellow>/bb party</yellow> • <yellow>/bb settings</yellow>",
            " <aqua>/bb map help</aqua> <gray>• Custom Map Architect</gray>  <dark_gray>|</dark_gray>  <aqua>/bb arena build</aqua>"
        ));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterPrefix(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase();
        List<String> arenaIds = plugin.arenaManager().allArenas().stream().map(Arena::id).toList();

        if (args.length == 2) {
            return switch (sub) {
                case "play", "join" -> filterPrefix(List.of("1v1", "2v2", "3v3", "5v5", "3pt", "practice"), args[1]);
                case "practice", "3pt", "contest", "stop" -> filterPrefix(arenaIds, args[1]);
                case "shop", "store" -> filterPrefix(List.of("tags", "cosmetics"), args[1]);
                case "coins", "coin" -> filterPrefix(List.of("give", "take", "set"), args[1]);
                case "top", "leaderboard" -> filterPrefix(List.of("elo", "coins"), args[1]);
                case "skin", "giveball" -> filterPrefix(Arrays.stream(BallSkin.values()).map(s -> s.name().toLowerCase()).toList(), args[1]);
                case "tags", "tag" -> {
                    List<String> opts = new ArrayList<>(Arrays.stream(ChatTag.values()).map(ChatTag::id).toList());
                    opts.add("off");
                    yield filterPrefix(opts, args[1]);
                }
                case "3d", "stage" -> filterPrefix(List.of("setcamera", "setslot", "resetstage"), args[1]);
                case "party", "group" -> filterPrefix(List.of("invite", "accept", "leave"), args[1]);
                case "friends", "friend" -> filterPrefix(List.of("add", "remove"), args[1]);
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
            if (sub.equals("rank") || sub.equals("coins") || sub.equals("coin")) {
                return null;
            }
        }
        if (args.length == 4) {
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
        }
        if (args.length == 5 && sub.equals("map") && (args[1].equalsIgnoreCase("hoop") || args[1].equalsIgnoreCase("sethoop"))) {
            return filterPrefix(List.of("north", "south", "east", "west"), args[4]);
        }
        return List.of();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String low = prefix.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(low)).toList();
    }
}
