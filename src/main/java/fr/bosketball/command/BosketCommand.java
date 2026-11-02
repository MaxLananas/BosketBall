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
 * Full Command Suite & Context-Aware TabCompleter for /bosketball (/bb, /basket, /hoops)!
 * Includes the complete Custom Map Architect Engine (/bb map ...) & Solo AI Defender Bot (/bb bot)!
 */
public final class BosketCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
        "menu", "3d", "tab", "play", "join", "leave", "practice", "bot", "3pt", "duel",
        "locker", "tags", "history", "friends", "party", "settings", "mail", "welcome", "hotbar",
        "stats", "top", "skin", "dunk",
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
        if (command.getName().equalsIgnoreCase("tab")) {
            if (sender instanceof Player p) {
                plugin.centerTabManager().toggle(p);
            }
            return true;
        }

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

            case "3d", "stage", "vestiaire" -> {
                if (sender instanceof Player p) {
                    plugin.lockerRoom3dManager().open(p, LockerRoom3dMenuManager.TopTab.PLAY);
                }
            }

            case "tab", "scoreboard" -> {
                if (sender instanceof Player p) {
                    plugin.centerTabManager().toggle(p);
                }
            }

            case "tags", "tag" -> {
                if (!(sender instanceof Player p)) {
                    return true;
                }
                if (args.length >= 2) {
                    PlayerProfile prof = plugin.statsManager().getOrCreate(p);
                    if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("none") || args[1].equalsIgnoreCase("clear")) {
                        prof.setSelectedTag(null);
                        plugin.statsManager().save();
                        Msg.send(p, Msg.info("Tag de chat retiré."));
                        return true;
                    }
                    ChatTag tag = ChatTag.fromIdOrNull(args[1]);
                    if (tag == null) {
                        Msg.send(p, Msg.error("Tag inconnu. Ouvrez /bb tags pour voir les 20 tags !"));
                        return true;
                    }
                    if (!tag.isUnlocked(prof) && !p.hasPermission("bosketball.admin")) {
                        Msg.send(p, Msg.error("Rang requis : " + tag.requiredRank().name()));
                        return true;
                    }
                    prof.setSelectedTag(tag);
                    plugin.statsManager().save();
                    Msg.send(p, Msg.success("Tag Glossy équipé : <!shadow><white>" + tag.glyph() + "</white></!shadow> <gold>" + tag.displayName() + "</gold>"));
                } else {
                    plugin.guiMenu().openTagsVault(p);
                }
            }

            case "history", "historique" -> {
                if (sender instanceof Player p) {
                    plugin.guiMenu().openMatchHistory(p);
                }
            }

            case "settings", "options" -> {
                if (sender instanceof Player p) {
                    plugin.guiMenu().openSettings(p);
                }
            }

            case "friends", "friend", "amis", "ami" -> {
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
                    Msg.send(p, Msg.error("Joueur hors ligne : " + targetName));
                    return true;
                }
                plugin.socialManager().sendOrAcceptFriendRequest(p, target);
            }

            case "party", "group", "groupe" -> {
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
                        Msg.send(p, Msg.error("Joueur introuvable : " + tName));
                        return true;
                    }
                    plugin.socialManager().inviteToParty(p, target);
                }
            }

            case "mail", "poste" -> {
                if (sender instanceof Player p) {
                    PlayerProfile prof = plugin.statsManager().getOrCreate(p);
                    prof.setUnreadMailCount(0);
                    plugin.statsManager().save();
                    Msg.sendFramedCard(p, "Boîte aux Lettres • BosketBall 26.2", List.of(
                        "<gold><bold>📬 Bienvenue sur BosketBall Chaos Cubed (26.2) !</bold></gold>",
                        "<gray>• <aqua>/bb 3d</aqua> : Entrez dans le nouveau Vestiaire 3D avec statues & groupe</gray>",
                        "<gray>• <yellow>[F]</yellow> ou <aqua>/tab</aqua> : Affichez le Tab Central 3D avec têtes de joueurs</gray>",
                        "<gray>• <aqua>/bb tags</aqua> : Débloquez et équipez les 20 Tags Glossy biseautés</gray>"
                    ));
                }
            }

            case "welcome" -> {
                if (sender instanceof Player p) {
                    PlayerProfile prof = plugin.statsManager().getOrCreate(p);
                    Msg.sendIllustratedWelcomeCard(
                        p,
                        plugin.getConfig().getString("links.website", "https://bosketball.fr"),
                        plugin.getConfig().getString("links.shop", "https://shop.bosketball.fr"),
                        plugin.getConfig().getString("links.discord", "https://discord.gg/bosketball"),
                        "Mise à jour 26.2 Chaos Cubed !",
                        "Testez le Vestiaire 3D (/bb 3d), les 20 Tags Glossy & le Tab Central ([F]) !",
                        prof.unreadMailCount()
                    );
                }
            }

            case "hotbar" -> {
                if (sender instanceof Player p && !plugin.gameManager().isInMatch(p.getUniqueId())) {
                    BallSkin.equipLobbyHotbarIfEmpty(p, true);
                    Msg.send(p, Msg.success("Barre d'icônes pixel-art BosketBall équipée !"));
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

            case "bot", "defender" -> {
                if (sender instanceof Player p) {
                    MatchSession s = plugin.gameManager().getSessionOf(p.getUniqueId());
                    if (s == null) {
                        Msg.send(p, "<yellow>ʀᴇᴊᴏɪɢɴᴇᴢ ᴅ'ᴀʙᴏʀᴅ ʟ'ᴇɴᴛʀᴀɪɴᴇᴍᴇɴᴛ ᴀᴠᴇᴄ <white>/bb practice</white> !</yellow>");
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
                Msg.sendFramedCard(sender, "Profil • " + target.getName(), List.of(
                    " <gray>▸ Rang:</gray> <!shadow><white>" + prof.effectiveRank().pillGlyph() + "</white></!shadow> <gold>(" + prof.elo() + " ELO)</gold>",
                    " <gray>▸ Bilan:</gray> <green>" + prof.wins() + "V</green> <dark_gray>/</dark_gray> <red>" + prof.losses() + "D</red> <dark_gray>(🔥" + prof.winStreak() + ")</dark_gray> <gray>• Temps:</gray> <white>" + prof.formattedPlayTimeFr() + "</white>",
                    " <gray>▸ Points:</gray> <yellow>" + prof.points() + "</yellow> <dark_gray>•</dark_gray> <gray>MVPs:</gray> <gold>" + prof.mvps() + "</gold> <dark_gray>•</dark_gray> <gray>Passes:</gray> <aqua>" + prof.assists() + "</aqua>",
                    " <gray>▸ Adresse:</gray> <aqua>" + String.format("%.1f%%", prof.fieldGoalPercentage()) + " FG</aqua> <dark_gray>•</dark_gray> <light_purple>"
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

            case "map" -> handleCustomMapArchitect(sender, args);

            case "arena" -> handleArenaAdmin(sender, args);

            case "rank" -> handleRankAdmin(sender, args);

            case "giveball" -> {
                if (!sender.hasPermission("bosketball.admin") || !(sender instanceof Player p)) {
                    Msg.send(sender, "<red>ᴘᴇʀᴍɪꜱꜱɪᴏɴ ʀᴇǫᴜɪꜱᴇ: bosketball.admin</red>");
                    return true;
                }
                BallSkin skin = args.length >= 2 ? BallSkin.byNameOrDefault(args[1], BallSkin.CLASSIC) : BallSkin.CLASSIC;
                p.getInventory().addItem(skin.createBallItem(false));
                p.getInventory().addItem(BallSkin.createArchitectWandItem("custom"));
                p.getInventory().addItem(BallSkin.createCustomModelItem(Material.GOLDEN_CARROT, "trophy_mvp", "<gold><bold>🏆 ᴛʀᴏᴘʜᴇᴇ ᴍᴠᴘ</bold></gold>", List.of()));
                p.getInventory().addItem(BallSkin.createCustomModelItem(Material.IRON_NUGGET, "referee_whistle", "<aqua><bold>📢 ꜱɪꜰꜰʟᴇᴛ ᴀʀʙɪᴛʀᴇ</bold></aqua>", List.of()));
                p.getInventory().addItem(BallSkin.createCustomModelItem(Material.LEATHER_BOOTS, "sneakers_air", "<red><bold>👟 ᴀɪʀ ʙᴏꜱᴋᴇᴛ 26</bold></red>", List.of()));
                p.getInventory().addItem(BallSkin.createPlaybookItem());
                Msg.send(p, "<green>✓ ɪᴛᴇᴍꜱ 3ᴅ ʙᴏꜱᴋᴇᴛʙᴀʟʟ (+ ʙᴀɢᴜᴇᴛᴛᴇ ᴀʀᴄʜɪᴛᴇᴄᴛᴇ) ᴀᴊᴏᴜᴛᴇꜱ ᴀ ᴠᴏᴛʀᴇ ɪɴᴠᴇɴᴛᴀɪʀᴇ !</green>");
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

    // ═══════════════════════════════════════════════════════════════════════════
    //   CUSTOM BASKETBALL MAP ARCHITECT SUITE (/bb map ...)
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleCustomMapArchitect(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bosketball.admin") || !(sender instanceof Player p)) {
            Msg.send(sender, "<red>ᴘᴇʀᴍɪꜱꜱɪᴏɴ ʀᴇǫᴜɪꜱᴇ (ᴇɴ ᴊᴇᴜ): bosketball.admin</red>");
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
                    "\n<gradient:#00F0FF:#FFD43B><bold>✦ ᴀʀᴄʜɪᴛᴇᴄᴛᴇ ᴅᴇ ᴍᴀᴘ : " + arena.displayName() + " (" + arena.id() + ") ✦</bold></gradient>\n"
                        + "<gray>ᴀᴜᴄᴜɴ ʙʟᴏᴄ ᴅᴇ ᴠᴏᴛʀᴇ ᴍᴀᴘ ɴᴇ ꜱᴇʀᴀ ᴍᴏᴅɪꜰɪᴇ ! ᴜᴛɪʟɪꜱᴇᴢ ʟᴀ <aqua><bold>ʙᴀɢᴜᴇᴛᴛᴇ 3ᴅ</bold></aqua> ᴏᴜ ʟᴇꜱ ᴄᴏᴍᴍᴀɴᴅᴇꜱ :</gray>\n"
                        + " <yellow>1.</yellow> <white>ᴄʟɪᴄ-ɢᴀᴜᴄʜᴇ / ᴅʀᴏɪᴛ ʙʟᴏᴄ</white> <gray>(ᴏᴜ <yellow>/bb map pos1</yellow> & <yellow>pos2</yellow>) ➔ ʟɪᴍɪᴛᴇꜱ ᴛᴇʀʀᴀɪɴ (ꜱᴏʀᴛɪᴇꜱ)</gray>\n"
                        + " <aqua>2.</aqua> <white>ꜱʜɪꜰᴛ + ᴄʟɪᴄ-ɢᴀᴜᴄʜᴇ / ᴅʀᴏɪᴛ</white> <gray>(ᴏᴜ <aqua>/bb map barrier1</aqua> & <aqua>barrier2</aqua>) ➔ ʙᴀʀʀɪᴇʀᴇꜱ ᴀɴᴛɪ-ꜱᴏʀᴛɪᴇ ʙᴀʟʟᴏɴ & ᴊᴏᴜᴇᴜʀꜱ</gray>\n"
                        + " <green>3.</green> <click:suggest_command:'/bb map hoop ignis'><yellow>/bb map hoop ignis [3d|custom]</yellow></click> & <click:suggest_command:'/bb map hoop frost'><yellow>/bb map hoop frost [3d|custom]</yellow></click> <gray>➔ ᴘᴏꜱᴇʀ ʟᴇꜱ 2 ᴘᴀɴɪᴇʀꜱ</gray>\n"
                        + " <gold>4.</gold> <click:suggest_command:'/bb map setspawn ignis'><gold>/bb map setspawn <ignis|frost|spectator|lobby> [1..5]</gold></click> <gray>➔ ᴘᴏɪɴᴛꜱ ᴅᴇ ꜱᴘᴀᴡɴ / ᴛᴘ</gray>\n"
                        + " <light_purple>5.</light_purple> <click:run_command:'/bb map save'><green><bold>[💾 ꜱᴀᴜᴠᴇɢᴀʀᴅᴇʀ & ᴀᴄᴛɪᴠᴇʀ]</bold></green></click> "
                        + "<click:run_command:'/bb practice " + arena.id() + "'><aqua><bold>[🏀 ᴛᴇꜱᴛᴇʀ ʟᴀ ᴍᴀᴘ]</bold></aqua></click>\n"
                ));
            }

            case "wand" -> {
                String arenaId = args.length >= 3 ? args[2] : null;
                MapEditorManager.EditorSession s = plugin.mapEditor().getOrBindSession(p, arenaId);
                plugin.mapEditor().giveWand(p, s != null ? s.arenaId : "custom");
                Msg.send(p, "<aqua>✓ <bold>ʙᴀɢᴜᴇᴛᴛᴇ ᴀʀᴄʜɪᴛᴇᴄᴛᴇ 3ᴅ</bold> ᴇǫᴜɪᴘᴇᴇ !</aqua>");
            }

            case "pos1", "pos2" -> {
                // Uses block directly beneath player's feet!
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
                    Msg.send(p, "<yellow>ᴜꜱᴀɢᴇ: <white>/bb map ceiling <hauteur_au_dessus_du_sol></white> (ᴇx: 16)</yellow>");
                    return;
                }
                try {
                    double h = Math.max(5.0, Double.parseDouble(args[2]));
                    CourtGeometry g = arena.geometry();
                    g.setBarrierBounds(g.barrierMinX(), g.barrierMinZ(), g.barrierMaxX(), g.barrierMaxZ(), g.floorY() + h);
                    plugin.arenaManager().save();
                    Msg.send(p, "<green>✓ ᴘʟᴀꜰᴏɴᴅ ᴀɴᴛɪ-ꜱᴏʀᴛɪᴇ ᴅᴜ ʙᴀʟʟᴏɴ ꜰɪxᴇ ᴀ <white>+" + String.format("%.1f", h)
                        + " ʙʟᴏᴄꜱ</white> (Y = " + String.format("%.1f", g.ceilingY()) + ") !</green>");
                } catch (NumberFormatException e) {
                    Msg.send(p, "<red>ʜᴀᴜᴛᴇᴜʀ ɪɴᴠᴀʟɪᴅᴇ.</red>");
                }
            }

            case "hoop", "sethoop" -> {
                Arena arena = requireEditedArena(p, null);
                if (arena == null) {
                    return;
                }
                if (args.length < 3) {
                    Msg.send(p, "<yellow>ᴜꜱᴀɢᴇ: <white>/bb map hoop <ignis|frost> [3d|custom] [north|south|east|west]</white></yellow>");
                    return;
                }
                String sideArg = args[2].toLowerCase();
                boolean isIgnisTarget = sideArg.startsWith("ignis") || sideArg.equals("south") || sideArg.equals("1");
                boolean spawn3d = !(args.length >= 4 && (args[3].equalsIgnoreCase("custom") || args[3].equalsIgnoreCase("blocks") || args[3].equalsIgnoreCase("none")));

                // Determine hoop facing direction: explicit cardinal OR opposite of player's look direction (faces toward player!)
                Vector forward = null;
                String dirArg = args.length >= 5 ? args[4].toLowerCase() : (args.length >= 4 ? args[3].toLowerCase() : "");
                forward = switch (dirArg) {
                    case "north", "nord", "n" -> new Vector(0, 0, -1);
                    case "south", "sud", "s" -> new Vector(0, 0, 1);
                    case "east", "est", "e" -> new Vector(1, 0, 0);
                    case "west", "ouest", "w" -> new Vector(-1, 0, 0);
                    default -> CourtGeometry.snapCardinalHorizontal(p.getLocation().getDirection().multiply(-1.0));
                };

                Location loc = p.getLocation();
                double floorY = arena.geometry().floorY();
                // If standing on the court floor, place rim at regulation +3.05m; if flying/elevated or custom block mode, use player's exact Y!
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

                String hoopLabel = isIgnisTarget ? "<#FF4D3D><bold>ᴘᴀɴɪᴇʀ ᴄɪʙʟᴇ ɪɢɴɪꜱ</bold></#FF4D3D>" : "<#3DB8FF><bold>ᴘᴀɴɪᴇʀ ᴄɪʙʟᴇ ꜰʀᴏꜱᴛ</bold></#3DB8FF>";
                Msg.send(p, "<green>✓ " + hoopLabel + " ᴘᴏꜱᴇ ᴇɴ <white>("
                    + String.format("%.1f, %.2f, %.1f", rimCenter.getX(), rimCenter.getY(), rimCenter.getZ())
                    + ")</white> • ᴏʀɪᴇɴᴛᴇ ᴠᴇʀꜱ <gold>" + CourtGeometry.cardinalLabel(forward) + "</gold> • ᴍᴏᴅᴇʟᴇ 3ᴅ: <aqua>"
                    + (spawn3d ? "ᴏᴜɪ (+ ʜᴏʀʟᴏɢᴇ 24ꜱ)" : "ʙʟᴏᴄꜱ ᴄᴜꜱᴛᴏᴍ (+ ʜᴏʀʟᴏɢᴇ 24ꜱ)") + "</aqua> !</green>");
                Msg.playPersonalPackSound(p, "ball.swish", 0.9f, 1.1f);
            }

            case "setspawn", "spawn" -> {
                Arena arena = requireEditedArena(p, null);
                if (arena == null) {
                    return;
                }
                if (args.length < 3) {
                    Msg.send(p, "<yellow>ᴜꜱᴀɢᴇ: <white>/bb map setspawn <ignis|frost|spectator|lobby> [1..5]</white></yellow>");
                    return;
                }
                String target = args[2].toLowerCase();
                arena.setCustomMap(true);
                arena.setWorldName(p.getWorld().getName());

                if (target.equals("lobby") || target.equals("exit") || target.equals("sortie")) {
                    arena.setLobbyExitSpawn(Arena.SpawnPose.fromLocation(p.getLocation()));
                    plugin.arenaManager().save();
                    Msg.send(p, "<green>✓ <bold>ᴘᴏɪɴᴛ ᴅᴇ ᴛᴘ ꜱᴏʀᴛɪᴇ / ʟᴏʙʙʏ</bold> ᴇɴʀᴇɢɪꜱᴛʀᴇ ᴀ ᴠᴏᴛʀᴇ ᴘᴏꜱɪᴛɪᴏɴ !</green>");
                    return;
                }

                TeamSide side = switch (target) {
                    case "ignis", "red", "rouge", "1" -> TeamSide.IGNIS;
                    case "frost", "blue", "bleu", "2" -> TeamSide.FROST;
                    case "spectator", "spec" -> TeamSide.SPECTATOR;
                    default -> null;
                };
                if (side == null) {
                    Msg.send(p, "<red>ᴄɪʙʟᴇ ɪɴᴄᴏɴɴᴜᴇ ! ᴄʜᴏɪx: <white>ignis, frost, spectator, lobby</white>.</red>");
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
                Msg.send(p, "<green>✓ ꜱᴘᴀᴡɴ <bold>" + side.smallCapsName() + " #" + (slotIdx + 1)
                    + "</bold> ᴇɴʀᴇɢɪꜱᴛʀᴇ ᴀ ᴠᴏᴛʀᴇ ᴘᴏꜱɪᴛɪᴏɴ & ᴏʀɪᴇɴᴛᴀᴛɪᴏɴ !</green>");
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
                    ? "<green>✓ <bold>ᴊᴜᴍʙᴏᴛʀᴏɴ ʟᴇᴅ 4-ꜰᴀᴄᴇꜱ</bold> ᴘʟᴀᴄᴇ ᴀ ᴠᴏᴛʀᴇ ᴘᴏꜱɪᴛɪᴏɴ !</green>"
                    : "<yellow>✓ ᴊᴜᴍʙᴏᴛʀᴏɴ ᴅᴇꜱᴀᴄᴛɪᴠᴇ ꜱᴜʀ ᴄᴇᴛᴛᴇ ᴍᴀᴘ.</yellow>");
            }

            case "preview" -> {
                boolean active = plugin.mapEditor().togglePreview(p);
                Msg.send(p, active
                    ? "<aqua>✓ <bold>ᴀᴘᴇʀᴄᴜ ʜᴏʟᴏɢʀᴀᴘʜɪǫᴜᴇ 3ᴅ</bold> ᴀᴄᴛɪᴠᴇ (ᴏʀ = ꜱᴏʀᴛɪᴇꜱ • ᴄʏᴀɴ = ʙᴀʀʀɪᴇʀᴇꜱ • ʀᴏᴜɢᴇ/ʙʟᴇᴜ = ᴘᴀɴɪᴇʀꜱ & ꜱᴘᴀᴡɴꜱ) !</aqua>"
                    : "<gray>ᴀᴘᴇʀᴄᴜ ʜᴏʟᴏɢʀᴀᴘʜɪǫᴜᴇ 3ᴅ ᴍᴀꜱǫᴜᴇ.</gray>");
            }

            case "info" -> {
                Arena arena = requireEditedArena(p, args.length >= 3 ? args[2] : null);
                if (arena == null) {
                    return;
                }
                CourtGeometry g = arena.geometry();
                p.sendMessage(Msg.parse(
                    "\n<gradient:#00F0FF:#FFD43B><bold>✦ ꜰɪᴄʜᴇ ᴍᴀᴘ : " + arena.displayName() + " (" + arena.id() + ") ✦</bold></gradient>\n"
                        + " <gray>▸ ᴍᴏɴᴅᴇ:</gray> <white>" + arena.worldName() + "</white> <dark_gray>•</dark_gray> <gray>ᴍᴀᴘ ᴄᴜꜱᴛᴏᴍ:</gray> <aqua>" + (arena.isCustomMap() ? "ᴏᴜɪ" : "ꜱᴛᴀᴅᴇ ɢᴇɴᴇʀᴇ") + "</aqua>\n"
                        + " <gray>▸ ᴛᴇʀʀᴀɪɴ (ꜱᴏʀᴛɪᴇꜱ):</gray> <yellow>X[" + String.format("%.1f..%.1f", g.minX(), g.maxX()) + "] Z[" + String.format("%.1f..%.1f", g.minZ(), g.maxZ()) + "] • ꜱᴏʟ Y=" + String.format("%.1f", g.floorY()) + "</yellow>\n"
                        + " <gray>▸ ʙᴀʀʀɪᴇʀᴇꜱ ᴀɴᴛɪ-ꜱᴏʀᴛɪᴇ:</gray> <aqua>X[" + String.format("%.1f..%.1f", g.barrierMinX(), g.barrierMaxX()) + "] Z[" + String.format("%.1f..%.1f", g.barrierMinZ(), g.barrierMaxZ()) + "] • ᴘʟᴀꜰᴏɴᴅ Y=" + String.format("%.1f", g.ceilingY()) + "</aqua>\n"
                        + " <gray>▸ ᴘᴀɴɪᴇʀ ɪɢɴɪꜱ:</gray> <#FF6B4A>(" + String.format("%.1f, %.2f, %.1f", g.southHoopRim().getX(), g.southHoopRim().getY(), g.southHoopRim().getZ()) + ") ➔ " + CourtGeometry.cardinalLabel(g.southHoopForward()) + "</#FF6B4A>\n"
                        + " <gray>▸ ᴘᴀɴɪᴇʀ ꜰʀᴏꜱᴛ:</gray> <#3DB8FF>(" + String.format("%.1f, %.2f, %.1f", g.northHoopRim().getX(), g.northHoopRim().getY(), g.northHoopRim().getZ()) + ") ➔ " + CourtGeometry.cardinalLabel(g.northHoopForward()) + "</#3DB8FF>\n"
                        + " <gray>▸ ꜱᴘᴀᴡɴꜱ ᴄᴜꜱᴛᴏᴍ:</gray> <white>ɪɢɴɪꜱ=" + arena.allCustomSpawns().get(TeamSide.IGNIS).size()
                        + " • ꜰʀᴏꜱᴛ=" + arena.allCustomSpawns().get(TeamSide.FROST).size()
                        + " • ʟᴏʙʙʏ=" + (arena.lobbyExitSpawn() != null ? "ᴏᴜɪ" : "ᴀᴜᴛᴏ") + "</white>\n"
                ));
            }

            case "save" -> {
                Arena arena = requireEditedArena(p, null);
                if (arena == null) {
                    return;
                }
                arena.spawnDisplays();
                plugin.arenaManager().save();
                Msg.send(p, "<green><bold>✓ ᴍᴀᴘ '" + arena.id() + "' ꜱᴀᴜᴠᴇɢᴀʀᴅᴇᴇ & ᴘᴀɴɪᴇʀꜱ 3ᴅ ᴀᴄᴛᴜᴀʟɪꜱᴇꜱ !</bold></green> "
                    + "<click:run_command:'/bb practice " + arena.id() + "'><aqua><bold>[▶ ᴊᴏᴜᴇʀ ᴅᴇꜱꜱᴜꜱ]</bold></aqua></click>");
                Msg.playPersonalPackSound(p, "game.green_release", 0.9f, 1.05f);
            }

            default -> sendMapHelp(p);
        }
    }

    private Arena requireEditedArena(Player p, String explicitId) {
        MapEditorManager.EditorSession s = plugin.mapEditor().getOrBindSession(p, explicitId);
        if (s == null) {
            Msg.send(p, "<red>ᴀᴜᴄᴜɴᴇ ᴍᴀᴘ ꜱᴇʟᴇᴄᴛɪᴏɴɴᴇᴇ ! ꜰᴀɪᴛᴇꜱ <yellow>/bb map create <id> [nom]</yellow>.</red>");
            return null;
        }
        Arena a = plugin.arenaManager().getArena(s.arenaId);
        if (a == null) {
            Msg.send(p, "<red>ᴍᴀᴘ ɪɴᴛʀᴏᴜᴠᴀʙʟᴇ: " + s.arenaId + "</red>");
        }
        return a;
    }

    private void sendMapHelp(Player p) {
        p.sendMessage(Msg.parse(
            "\n<gradient:#00F0FF:#FFD43B><bold>✦ ᴄʀᴇᴀᴛᴇᴜʀ ᴅᴇ ᴍᴀᴘ ʙᴀꜱᴋᴇᴛ ꜱᴜʀ-ᴍᴇꜱᴜʀᴇ (/ʙʙ ᴍᴀᴘ) ✦</bold></gradient>\n"
                + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                + " <aqua>/bb map create <id> [nom]</aqua> <gray>• ᴄʀᴇᴇʀ ᴜɴᴇ ᴍᴀᴘ + ʀᴇᴄᴇᴠᴏɪʀ ʟᴀ ʙᴀɢᴜᴇᴛᴛᴇ 3ᴅ</gray>\n"
                + " <aqua>/bb map wand [id]</aqua> <gray>• ʀᴇᴄᴜᴘᴇʀᴇʀ ʟᴀ ʙᴀɢᴜᴇᴛᴛᴇ ᴀʀᴄʜɪᴛᴇᴄᴛᴇ 3ᴅ</gray>\n"
                + " <yellow>/bb map pos1</yellow> & <yellow>pos2</yellow> <gray>• ᴄᴏɪɴꜱ ᴅᴜ ᴛᴇʀʀᴀɪɴ (ʟɪɢɴᴇꜱ ᴅᴇ ꜱᴏʀᴛɪᴇ + ʜᴀᴜᴛᴇᴜʀ ꜱᴏʟ)</gray>\n"
                + " <yellow>/bb map barrier1</yellow> & <yellow>barrier2</yellow> <gray>• ᴍᴜʀꜱ ɪɴᴠɪꜱɪʙʟᴇꜱ (ʟᴇ ʙᴀʟʟᴏɴ & ʟᴇꜱ ᴊᴏᴜᴇᴜʀꜱ ɴᴇ ꜱᴏʀᴛᴇɴᴛ ᴘᴀꜱ !)</gray>\n"
                + " <yellow>/bb map ceiling <hauteur></yellow> <gray>• ʜᴀᴜᴛᴇᴜʀ ᴅᴜ ᴘʟᴀꜰᴏɴᴅ ᴀɴᴛɪ-ꜱᴏʀᴛɪᴇ ʙᴀʟʟᴏɴ</gray>\n"
                + " <green>/bb map hoop <ignis|frost> [3d|custom]</green> <gray>• ᴘᴏꜱᴇʀ ʟᴇꜱ 2 ᴘᴀɴɪᴇʀꜱ (360° ɴ/ꜱ/ᴇ/ᴏ)</gray>\n"
                + " <gold>/bb map setspawn <ignis|frost|spectator|lobby> [1..5]</gold> <gray>• ꜱᴘᴀᴡɴꜱ & ᴛᴘ ᴇǫᴜɪᴘᴇꜱ</gray>\n"
                + " <light_purple>/bb map jumbotron <here|off></light_purple> <gray>• ᴘʟᴀᴄᴇʀ ᴏᴜ ᴍᴀꜱǫᴜᴇʀ ʟᴇ ᴊᴜᴍʙᴏᴛʀᴏɴ ʟᴇᴅ</gray>\n"
                + " <white>/bb map preview</white> • <white>/bb map info</white> • <green>/bb map save</green>\n"
                + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
        ));
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
                Msg.send(sender, "<gold><bold>ꜱᴛᴀᴅᴇꜱ & ᴍᴀᴘꜱ ʙᴏꜱᴋᴇᴛʙᴀʟʟ (" + plugin.arenaManager().allArenas().size() + "):</bold></gold>");
                for (Arena a : plugin.arenaManager().allArenas()) {
                    String tag = a.isCustomMap() ? "<aqua>[ᴍᴀᴘ ᴄᴜꜱᴛᴏᴍ]</aqua>" : "<gold>[ꜱᴛᴀᴅᴇ 3ᴅ]</gold>";
                    Msg.send(sender, "<gray>• " + tag + " <white>" + a.id() + "</white> — <yellow>" + a.displayName() + "</yellow> (" + a.worldName() + ")</gray>");
                }
            }
            case "tp" -> {
                if (sender instanceof Player p && args.length >= 3) {
                    Arena a = plugin.arenaManager().getArena(args[2]);
                    if (a != null) {
                        p.teleport(a.centerCourtLocation());
                        Msg.send(p, "<green>✓ ᴛᴇʟᴇᴘᴏʀᴛᴇ ᴀᴜ ᴛᴇʀʀᴀɪɴ <white>" + a.displayName() + "</white>.</green>");
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
        Msg.sendFramedCard(sender, "BosketBall 26.2 • Commandes", List.of(
            " <yellow>/bb</yellow> <gray>• Menu Hub Custom Pixel-Art</gray>  <dark_gray>|</dark_gray>  <aqua>/bb 3d</aqua> <gray>• Vestiaire 3D Interactif</gray>",
            " <yellow>/tab</yellow> <gray>(ou touche <white>[F]</white>) • Tab Central 3D avec têtes de joueurs & points</gray>",
            " <yellow>/bb play [1v1|2v2|3v3|5v5]</yellow> <gray>• Rejoindre un match compétitif</gray>",
            " <yellow>/bb practice</yellow> & <yellow>/bb bot</yellow> <gray>• Entraînement 24s + Défenseur IA</gray>",
            " <yellow>/bb tags</yellow> <gray>• 20 Tags Glossy</gray>  <dark_gray>|</dark_gray>  <yellow>/bb locker</yellow> <gray>• Ballons Carrés 3D & Dunks</gray>",
            " <yellow>/bb history</yellow> <gray>• Historique</gray>  <dark_gray>|</dark_gray>  <yellow>/bb friends</yellow> & <yellow>/bb party</yellow> <gray>• Amis & Groupe</gray>",
            " <yellow>/bb settings</yellow> <gray>• Options 3D</gray>  <dark_gray>|</dark_gray>  <aqua>/bb map help</aqua> <gray>• Architecte de Map</gray>"
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
                case "skin", "giveball" -> filterPrefix(Arrays.stream(BallSkin.values()).map(s -> s.name().toLowerCase()).toList(), args[1]);
                case "tags", "tag" -> {
                    List<String> opts = new ArrayList<>(Arrays.stream(ChatTag.values()).map(ChatTag::id).toList());
                    opts.add("off");
                    yield filterPrefix(opts, args[1]);
                }
                case "party", "group", "groupe" -> filterPrefix(List.of("invite", "accept", "leave"), args[1]);
                case "friends", "friend", "amis" -> filterPrefix(List.of("add", "remove"), args[1]);
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
            if (sub.equals("rank")) {
                return null;
            }
        }
        if (args.length == 4) {
            if (sub.equals("rank") && args[1].equalsIgnoreCase("set")) {
                List<String> ranks = new ArrayList<>(Arrays.stream(Rank.values()).map(r -> r.name().toLowerCase()).toList());
                ranks.add("auto");
                return filterPrefix(ranks, args[3]);
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
