package fr.bosketball.command;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.ArenaBuilder;
import fr.bosketball.game.GameModeType;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Msg;
import fr.bosketball.util.SmallCaps;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Full Command Suite & Context-Aware TabCompleter for /bosketball (/bb, /basket, /hoops)!
 */
public final class BosketCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
        "menu", "play", "join", "leave", "practice", "3pt", "duel",
        "locker", "stats", "top", "pack", "skin", "dunk",
        "arena", "rank", "giveball", "forcestart", "stop", "reload", "help"
    );

    private final BosketBallPlugin plugin;

    public BosketCommand(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player p)) {
                Msg.send(sender, "<yellow>ᴜᴛɪʟɪꜱᴇᴢ <white>/bb help</white> ᴅᴇᴘᴜɪꜱ ʟᴀ ᴄᴏɴꜱᴏʟᴇ.</yellow>");
                return true;
            }
            plugin.guiMenu().openMainHub(p);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help" -> sendHelp(sender);

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
                    MatchSession s = plugin.gameManager().getSessionOf(p.getUniqueId());
                    if (s == null) {
                        Msg.send(p, "<red>ᴠᴏᴜꜱ ɴ'ᴇᴛᴇꜱ ᴅᴀɴꜱ ᴀᴜᴄᴜɴ ᴍᴀᴛᴄʜ ᴀᴄᴛᴜᴇʟʟᴇᴍᴇɴᴛ.</red>");
                    } else {
                        s.removePlayer(p, false);
                        Msg.send(p, "<green>✓ ᴠᴏᴜꜱ ᴀᴠᴇᴢ ǫᴜɪᴛᴛᴇ ʟᴇ ᴛᴇʀʀᴀɪɴ.</green>");
                    }
                }
            }

            case "practice" -> {
                if (sender instanceof Player p) {
                    plugin.gameManager().quickJoin(p, GameModeType.PRACTICE, args.length >= 2 ? args[1] : null);
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
                    Msg.send(p, "<yellow>ᴜꜱᴀɢᴇ: <white>/bb duel <joueur></white></yellow>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    Msg.send(p, "<red>ᴊᴏᴜᴇᴜʀ ɪɴᴛʀᴏᴜᴠᴀʙʟᴇ ᴇɴ ʟɪɢɴᴇ: <white>" + args[1] + "</white></red>");
                    return true;
                }
                plugin.gameManager().sendOrAcceptDuel(p, target);
            }

            case "locker", "cosmetics" -> {
                if (sender instanceof Player p) {
                    plugin.guiMenu().openLockerRoom(p);
                }
            }

            case "top", "leaderboard" -> {
                if (sender instanceof Player p) {
                    plugin.guiMenu().openLeaderboards(p);
                } else {
                    List<PlayerProfile> top = plugin.statsManager().topBy(StatsManager.LeaderboardCategory.ELO, 10);
                    sender.sendMessage("=== TOP 10 BOSKETBALL ELO ===");
                    for (int i = 0; i < top.size(); i++) {
                        sender.sendMessage("#" + (i + 1) + " " + top.get(i).lastKnownName() + " - " + top.get(i).elo() + " ELO");
                    }
                }
            }

            case "stats", "profile" -> {
                Player target = (args.length >= 2) ? Bukkit.getPlayer(args[1]) : (sender instanceof Player p ? p : null);
                if (target == null) {
                    Msg.send(sender, "<red>ᴊᴏᴜᴇᴜʀ ɪɴᴛʀᴏᴜᴠᴀʙʟᴇ.</red>");
                    return true;
                }
                PlayerProfile prof = plugin.statsManager().getOrCreate(target);
                sender.sendMessage(Msg.parse(
                    "\n<gradient:#FF7A18:#FFD43B><bold>✦ ꜱᴛᴀᴛɪꜱᴛɪǫᴜᴇꜱ ᴅᴇ " + target.getName() + " ✦</bold></gradient>\n"
                        + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                        + " <gray>▸ ʀᴀɴɢ:</gray> <!shadow><white>" + prof.effectiveRank().pillGlyph() + "</white></!shadow> <gold>(" + prof.elo() + " ᴇʟᴏ)</gold>\n"
                        + " <gray>▸ ᴠɪᴄᴛᴏɪʀᴇꜱ / ᴅᴇꜰᴀɪᴛᴇꜱ:</gray> <green>" + prof.wins() + "ᴠ</green> <dark_gray>/</dark_gray> <red>" + prof.losses() + "ᴅ</red> <dark_gray>(🔥" + prof.winStreak() + ")</dark_gray>\n"
                        + " <gray>▸ ᴘᴏɪɴᴛꜱ ᴍᴀʀǫᴜᴇꜱ:</gray> <yellow>" + prof.points() + "</yellow> <dark_gray>•</dark_gray> <gray>ᴍᴠᴘꜱ:</gray> <gold>" + prof.mvps() + "</gold>\n"
                        + " <gray>▸ ᴀᴅʀᴇꜱꜱᴇ ᴛɪʀ:</gray> <aqua>" + String.format("%.1f%%", prof.fieldGoalPercentage()) + " ꜰɢ</aqua> <dark_gray>•</dark_gray> <light_purple>"
                        + String.format("%.1f%%", prof.threePointPercentage()) + " 3ᴘᴛ</light_purple>\n"
                        + " <gray>▸ ᴅᴜɴᴋꜱ:</gray> <red>" + prof.dunks() + "</red> <dark_gray>•</dark_gray> <gray>ᴘᴀꜱꜱᴇꜱ:</gray> <aqua>" + prof.assists() + "</aqua> <dark_gray>•</dark_gray> <gray>ᴄᴏɴᴛʀᴇꜱ:</gray> <green>" + prof.blocks() + "</green>\n"
                        + " <gray>▸ ʀᴇᴄᴏʀᴅ ᴄᴏɴᴄᴏᴜʀꜱ 3-ᴘᴛꜱ:</gray> <gold>" + prof.threePointContestBest() + "/30</gold>\n"
                        + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                ));
            }

            case "pack" -> {
                if (sender instanceof Player p) {
                    plugin.resourcePackHost().sendPackTo(p);
                }
            }

            case "skin" -> {
                if (!(sender instanceof Player p) || args.length < 2) {
                    Msg.send(sender, "<yellow>ᴜꜱᴀɢᴇ: <white>/bb skin <classic|money|neon|fire></white></yellow>");
                    return true;
                }
                BallSkin skin = BallSkin.byNameOrDefault(args[1], null);
                if (skin == null) {
                    Msg.send(p, "<red>ꜱᴋɪɴ ɪɴᴄᴏɴɴᴜ ! ᴄʜᴏɪx: classic, money, neon, fire.</red>");
                    return true;
                }
                PlayerProfile prof = plugin.statsManager().getOrCreate(p);
                if (prof.elo() < skin.unlockElo() && !p.hasPermission("bosketball.admin")) {
                    Msg.send(p, "<red>ɪʟ ᴠᴏᴜꜱ ꜰᴀᴜᴛ <gold>" + skin.unlockElo() + " ᴇʟᴏ</gold> ᴘᴏᴜʀ ᴄᴇ ʙᴀʟʟᴏɴ !</red>");
                    return true;
                }
                prof.setSelectedBallSkin(skin);
                plugin.statsManager().save();
                Msg.send(p, "<green>✓ ʙᴀʟʟᴏɴ 3ᴅ ᴇǫᴜɪᴘᴇ:</green> " + skin.displayMiniMessage());
            }

            case "dunk" -> {
                if (!(sender instanceof Player p) || args.length < 2) {
                    Msg.send(sender, "<yellow>ᴜꜱᴀɢᴇ: <white>/bb dunk <tomahawk|windmill|spin_360|reverse_baseline|meteor_jam></white></yellow>");
                    return true;
                }
                DunkStyle style = DunkStyle.byNameOrDefault(args[1], null);
                if (style == null) {
                    Msg.send(p, "<red>ꜱᴛʏʟᴇ ɪɴᴄᴏɴɴᴜ !</red>");
                    return true;
                }
                PlayerProfile prof = plugin.statsManager().getOrCreate(p);
                if (prof.elo() < style.unlockElo() && !p.hasPermission("bosketball.admin")) {
                    Msg.send(p, "<red>ɪʟ ᴠᴏᴜꜱ ꜰᴀᴜᴛ <gold>" + style.unlockElo() + " ᴇʟᴏ</gold> ᴘᴏᴜʀ ᴄᴇ ꜱʟᴀᴍ ᴅᴜɴᴋ !</red>");
                    return true;
                }
                prof.setSelectedDunkStyle(style);
                plugin.statsManager().save();
                Msg.send(p, "<green>✓ ꜱʟᴀᴍ ᴅᴜɴᴋ ᴇǫᴜɪᴘᴇ:</green> <gold>" + style.displayName() + "</gold>");
            }

            case "arena" -> handleArenaAdmin(sender, args);

            case "rank" -> handleRankAdmin(sender, args);

            case "giveball" -> {
                if (!sender.hasPermission("bosketball.admin") || !(sender instanceof Player p)) {
                    Msg.send(sender, "<red>ᴘᴇʀᴍɪꜱꜱɪᴏɴ ʀᴇǫᴜɪꜱᴇ: bosketball.admin</red>");
                    return true;
                }
                BallSkin skin = args.length >= 2 ? BallSkin.byNameOrDefault(args[1], BallSkin.CLASSIC) : BallSkin.CLASSIC;
                p.getInventory().addItem(skin.createBallItem(false));
                p.getInventory().addItem(BallSkin.createCustomModelItem(Material.GOLDEN_CARROT, "trophy_mvp", "<gold><bold>🏆 ᴛʀᴏᴘʜᴇᴇ ᴍᴠᴘ</bold></gold>", List.of()));
                p.getInventory().addItem(BallSkin.createCustomModelItem(Material.IRON_NUGGET, "referee_whistle", "<aqua><bold>📢 ꜱɪꜰꜰʟᴇᴛ ᴀʀʙɪᴛʀᴇ</bold></aqua>", List.of()));
                p.getInventory().addItem(BallSkin.createCustomModelItem(Material.LEATHER_BOOTS, "sneakers_air", "<red><bold>👟 ᴀɪʀ ʙᴏꜱᴋᴇᴛ 26</bold></red>", List.of()));
                p.getInventory().addItem(BallSkin.createPlaybookItem());
                Msg.send(p, "<green>✓ ɪᴛᴇᴍꜱ 3ᴅ ʙᴏꜱᴋᴇᴛʙᴀʟʟ ᴀᴊᴏᴜᴛᴇꜱ ᴀ ᴠᴏᴛʀᴇ ɪɴᴠᴇɴᴛᴀɪʀᴇ !</green>");
            }

            case "forcestart" -> {
                if (!sender.hasPermission("bosketball.admin") || !(sender instanceof Player p)) {
                    return true;
                }
                MatchSession s = plugin.gameManager().getSessionOf(p.getUniqueId());
                if (s != null) {
                    s.forceStart();
                    Msg.send(p, "<green>✓ ᴅᴇᴍᴀʀʀᴀɢᴇ ꜰᴏʀᴄᴇ ᴅᴜ ᴍᴀᴛᴄʜ !</green>");
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
                        Msg.send(sender, "<green>✓ ᴍᴀᴛᴄʜ ᴀʀʀᴇᴛᴇ ꜱᴜʀ <white>" + args[1] + "</white>.</green>");
                    }
                } else if (sender instanceof Player p) {
                    MatchSession s = plugin.gameManager().getSessionOf(p.getUniqueId());
                    if (s != null) {
                        s.stopAndCleanup();
                        Msg.send(p, "<green>✓ ᴍᴀᴛᴄʜ ᴀʀʀᴇᴛᴇ.</green>");
                    }
                }
            }

            case "reload" -> {
                if (!sender.hasPermission("bosketball.admin")) {
                    Msg.send(sender, "<red>ᴘᴇʀᴍɪꜱꜱɪᴏɴ ʀᴇǫᴜɪꜱᴇ: bosketball.admin</red>");
                    return true;
                }
                plugin.reloadBosketConfig();
                Msg.send(sender, "<green>✓ ᴄᴏɴꜰɪɢᴜʀᴀᴛɪᴏɴ & ꜱᴇʀᴠᴇᴜʀ ᴅᴇ ᴘᴀᴄᴋ 3ᴅ ʀᴇᴄʜᴀʀɢᴇꜱ !</green>");
            }

            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleArenaAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bosketball.admin")) {
            Msg.send(sender, "<red>ᴘᴇʀᴍɪꜱꜱɪᴏɴ ʀᴇǫᴜɪꜱᴇ: bosketball.admin</red>");
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, "<yellow>ᴜꜱᴀɢᴇ: <white>/bb arena <build|list|tp|delete> [nom]</white></yellow>");
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
                Msg.send(p, "<green><bold>✓ ꜱᴛᴀᴅᴇ 3ᴅ ᴄᴏɴꜱᴛʀᴜɪᴛ ᴀᴠᴇᴄ ꜱᴜᴄᴄᴇꜱ !</bold></green> <gray>(</gray><white>"
                    + id + "</white> <dark_gray>•</dark_gray> <gold>ᴘᴀɴɪᴇʀꜱ 3ᴅ + ᴊᴜᴍʙᴏᴛʀᴏɴ + ᴛʀɪʙᴜɴᴇꜱ</gold><gray>)</gray>");
            }
            case "list" -> {
                Msg.send(sender, "<gold><bold>ꜱᴛᴀᴅᴇꜱ ʙᴏꜱᴋᴇᴛʙᴀʟʟ (" + plugin.arenaManager().allArenas().size() + "):</bold></gold>");
                for (Arena a : plugin.arenaManager().allArenas()) {
                    Msg.send(sender, "<gray>• <white>" + a.id() + "</white> — <yellow>" + a.displayName() + "</yellow> (" + a.worldName() + ")</gray>");
                }
            }
            case "tp" -> {
                if (sender instanceof Player p && args.length >= 3) {
                    Arena a = plugin.arenaManager().getArena(args[2]);
                    if (a != null) {
                        p.teleport(a.centerCourtLocation());
                        Msg.send(p, "<green>✓ ᴛᴇʟᴇᴘᴏʀᴛᴇ ᴀᴜ ꜱᴛᴀᴅᴇ <white>" + a.displayName() + "</white>.</green>");
                    }
                }
            }
            case "delete", "remove" -> {
                if (args.length >= 3 && plugin.arenaManager().deleteArena(args[2])) {
                    Msg.send(sender, "<green>✓ ᴀʀᴇɴᴇ <white>" + args[2] + "</white> ꜱᴜᴘᴘʀɪᴍᴇᴇ.</green>");
                } else {
                    Msg.send(sender, "<red>ᴀʀᴇɴᴇ ɪɴᴛʀᴏᴜᴠᴀʙʟᴇ.</red>");
                }
            }
            default -> Msg.send(sender, "<yellow>ᴜꜱᴀɢᴇ: <white>/bb arena <build|list|tp|delete> [nom]</white></yellow>");
        }
    }

    private void handleRankAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bosketball.admin") || args.length < 4) {
            Msg.send(sender, "<yellow>ᴜꜱᴀɢᴇ: <white>/bb rank <set|elo> <joueur> <rang|valeur></white></yellow>");
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            Msg.send(sender, "<red>ᴊᴏᴜᴇᴜʀ ɪɴᴛʀᴏᴜᴠᴀʙʟᴇ.</red>");
            return;
        }
        PlayerProfile prof = plugin.statsManager().getOrCreate(target);
        if (args[1].equalsIgnoreCase("set")) {
            if (args[3].equalsIgnoreCase("auto") || args[3].equalsIgnoreCase("clear")) {
                prof.setStaffRankOverride(null);
            } else {
                Rank rank = Rank.byNameOrNull(args[3]);
                if (rank == null) {
                    Msg.send(sender, "<red>ʀᴀɴɢ ɪɴᴄᴏɴɴᴜ: " + args[3] + "</red>");
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
            plugin.statsManager().save();
            Msg.send(sender, "<green>✓ ʀᴀɴɢ ᴅᴇ <white>" + target.getName() + "</white> ᴍɪꜱ ᴀ ᴊᴏᴜʀ: <!shadow><white>"
                + prof.effectiveRank().pillGlyph() + "</white></!shadow></green>");
        } else if (args[1].equalsIgnoreCase("elo")) {
            try {
                int elo = Integer.parseInt(args[3]);
                prof.setElo(elo);
                plugin.statsManager().refreshLeaderboardBadges();
                plugin.statsManager().save();
                Msg.send(sender, "<green>✓ ᴇʟᴏ ᴅᴇ <white>" + target.getName() + "</white> ᴅᴇꜰɪɴɪ ᴀ <gold>" + prof.elo() + "</gold> !</green>");
            } catch (NumberFormatException e) {
                Msg.send(sender, "<red>ᴠᴀʟᴇᴜʀ ᴇʟᴏ ɪɴᴠᴀʟɪᴅᴇ.</red>");
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Msg.parse(
            "\n<gradient:#FF7A18:#FFD43B><bold>✦ ʙᴏꜱᴋᴇᴛʙᴀʟʟ 26.2 — ᴄᴏᴍᴍᴀɴᴅᴇꜱ ✦</bold></gradient>\n"
                + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                + " <yellow>/bb</yellow> <gray>• ᴏᴜᴠʀɪʀ ʟᴇ ᴍᴇɴᴜ ᴘʀɪɴᴄɪᴘᴀʟ 3ᴅ</gray>\n"
                + " <yellow>/bb play [1v1|2v2|3v3|5v5]</yellow> <gray>• ʀᴇᴊᴏɪɴᴅʀᴇ ᴜɴ ᴍᴀᴛᴄʜ</gray>\n"
                + " <yellow>/bb 3pt</yellow> <gray>• ʟᴀɴᴄᴇʀ ʟᴇ ᴄᴏɴᴄᴏᴜʀꜱ ᴀ 3 ᴘᴏɪɴᴛꜱ (ᴍᴏɴᴇʏ ʙᴀʟʟꜱ)</gray>\n"
                + " <yellow>/bb practice</yellow> <gray>• ᴇɴᴛʀᴀɪɴᴇᴍᴇɴᴛ ʟɪʙʀᴇ & ᴅᴜɴᴋ ʟᴀʙ</gray>\n"
                + " <yellow>/bb duel <joueur></yellow> <gray>• ᴅᴇꜰɪᴇʀ ᴜɴ ᴊᴏᴜᴇᴜʀ ᴇɴ 1ᴠ1</gray>\n"
                + " <yellow>/bb locker</yellow> <gray>• ᴠᴇꜱᴛɪᴀɪʀᴇ ʙᴀʟʟᴏɴꜱ 3ᴅ & ꜱʟᴀᴍ ᴅᴜɴᴋꜱ</gray>\n"
                + " <yellow>/bb stats [joueur]</yellow> <gray>• ᴄᴀʀᴛᴇ ꜱᴄᴏᴜᴛɪɴɢ & ꜱᴛᴀᴛꜱ</gray>\n"
                + " <yellow>/bb top</yellow> <gray>• ᴘᴀɴᴛʜᴇᴏɴ ᴅᴇꜱ ᴄʟᴀꜱꜱᴇᴍᴇɴᴛꜱ</gray>\n"
                + " <yellow>/bb arena build <id></yellow> <gray>• ɢᴇɴᴇʀᴇʀ ᴜɴ ꜱᴛᴀᴅᴇ 3ᴅ ᴄᴏᴍᴘʟᴇᴛ</gray>\n"
                + " <yellow>/bb rank set <joueur> <rang></yellow> <gray>• ɢᴇʀᴇʀ ʟᴇꜱ ʀᴀɴɢꜱ & ʙᴀᴅɢᴇꜱ</gray>\n"
                + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
        ));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterPrefix(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            return switch (sub) {
                case "play", "join" -> filterPrefix(List.of("1v1", "2v2", "3v3", "5v5", "3pt", "practice"), args[1]);
                case "skin", "giveball" -> filterPrefix(Arrays.stream(BallSkin.values()).map(s -> s.name().toLowerCase()).toList(), args[1]);
                case "dunk" -> filterPrefix(Arrays.stream(DunkStyle.values()).map(s -> s.name().toLowerCase()).toList(), args[1]);
                case "arena" -> filterPrefix(List.of("build", "list", "tp", "delete"), args[1]);
                case "rank" -> filterPrefix(List.of("set", "elo"), args[1]);
                case "duel", "stats" -> null; // Online player names
                default -> List.of();
            };
        }
        if (args.length == 3) {
            if (sub.equals("arena") && (args[1].equalsIgnoreCase("tp") || args[1].equalsIgnoreCase("delete"))) {
                return filterPrefix(plugin.arenaManager().allArenas().stream().map(Arena::id).toList(), args[2]);
            }
            if (sub.equals("rank")) {
                return null;
            }
        }
        if (args.length == 4 && sub.equals("rank") && args[1].equalsIgnoreCase("set")) {
            List<String> ranks = new ArrayList<>(Arrays.stream(Rank.values()).map(r -> r.name().toLowerCase()).toList());
            ranks.add("auto");
            return filterPrefix(ranks, args[3]);
        }
        return List.of();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String low = prefix.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(low)).toList();
    }
}
