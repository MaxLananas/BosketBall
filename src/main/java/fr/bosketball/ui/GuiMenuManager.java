package fr.bosketball.ui;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.game.GameModeType;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Interactive Custom 3D-Icon GUI Suite for BosketBall 26.2:
 * - Main Hub (/bb menu)
 * - Mode & Stadium Selector (/bb play)
 * - Locker Room Cosmetics: 3D Ball Skins & 3D Dunk Styles (/bb locker)
 * - Hall of Fame Leaderboards & Career Scouting Card (/bb top)
 */
public final class GuiMenuManager {

    public enum MenuType {
        MAIN_HUB,
        PLAY_MODES,
        LOCKER_ROOM,
        LEADERBOARDS
    }

    public static final class BosketGuiHolder implements InventoryHolder {
        private final MenuType type;
        private Inventory inventory;

        public BosketGuiHolder(MenuType type) {
            this.type = type;
        }

        public MenuType type() {
            return type;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final BosketBallPlugin plugin;

    public GuiMenuManager(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMainHub(Player player) {
        BosketGuiHolder holder = new BosketGuiHolder(MenuType.MAIN_HUB);
        Inventory inv = Bukkit.createInventory(
            holder,
            45,
            Msg.parse("<gradient:#FF7A18:#FFD43B><bold>✦ ʙᴏꜱᴋᴇᴛʙᴀʟʟ 26.2 • ᴍᴇɴᴜ ✦</bold></gradient>")
        );
        holder.setInventory(inv);
        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE);

        PlayerProfile prof = plugin.statsManager().getOrCreate(player);

        // Slot 20: Competitive Modes (3D Classic Pro Basketball)
        inv.setItem(20, BallSkin.createCustomModelItem(
            Material.SLIME_BALL,
            "basketball_classic",
            "<gradient:#FF7A18:#FFD43B><bold>🏀 ᴍᴀᴛᴄʜꜱ ᴄᴏᴍᴘᴇᴛɪᴛɪꜰꜱ</bold></gradient>",
            List.of(
                "<gray>ᴀꜰꜰʀᴏɴᴛᴇᴢ ᴅ'ᴀᴜᴛʀᴇꜱ ᴊᴏᴜᴇᴜʀꜱ ᴇɴ 1ᴠ1, 2ᴠ2, 3ᴠ3 ᴏᴜ 5ᴠ5 !</gray>",
                "",
                "<gray>ᴊᴏᴜᴇᴜʀꜱ ᴇɴ ᴍᴀᴛᴄʜ:</gray> <green>" + plugin.gameManager().activePlayerCount() + "</green>",
                "<yellow>▸ ᴄʟɪǫᴜᴇʀ ᴘᴏᴜʀ ᴄʜᴏɪꜱɪʀ ᴜɴ ᴍᴏᴅᴇ</yellow>"
            )
        ));

        // Slot 22: 3-Point Contest (3D Money Ball)
        inv.setItem(22, BallSkin.createCustomModelItem(
            Material.SLIME_BALL,
            "basketball_money",
            "<gradient:#3DB8FF:#FFFFFF:#FF4D4D><bold>🎯 ᴄᴏɴᴄᴏᴜʀꜱ ᴀ 3 ᴘᴏɪɴᴛꜱ</bold></gradient>",
            List.of(
                "<gray>5 ʀᴀᴄᴋꜱ • 25 ʙᴀʟʟᴏɴꜱ • 60 ꜱᴇᴄᴏɴᴅᴇꜱ !</gray>",
                "<gray>ᴠᴏᴛʀᴇ ʀᴇᴄᴏʀᴅ:</gray> <gold>" + prof.threePointContestBest() + "/30 ᴘᴛꜱ</gold>",
                "",
                "<yellow>▸ ᴄʟɪǫᴜᴇʀ ᴘᴏᴜʀ ʟᴀɴᴄᴇʀ ɪᴍᴍᴇᴅɪᴀᴛᴇᴍᴇɴᴛ</yellow>"
            )
        ));

        // Slot 24: Locker Room Cosmetics (3D Cyber Neon Ball)
        inv.setItem(24, BallSkin.createCustomModelItem(
            Material.SLIME_BALL,
            "basketball_neon",
            "<gradient:#00F0FF:#FF2BD6><bold>✨ ᴠᴇꜱᴛɪᴀɪʀᴇ & ᴄᴏꜱᴍᴇᴛɪǫᴜᴇꜱ 3ᴅ</bold></gradient>",
            List.of(
                "<gray>ᴘᴇʀꜱᴏɴɴᴀʟɪꜱᴇᴢ ᴠᴏᴛʀᴇ ʙᴀʟʟᴏɴ 3ᴅ & ᴠᴏᴛʀᴇ ꜱʟᴀᴍ ᴅᴜɴᴋ !</gray>",
                "<gray>ʙᴀʟʟᴏɴ ᴀᴄᴛɪꜰ:</gray> <white>" + prof.selectedBallSkin().name() + "</white>",
                "<gray>ᴅᴜɴᴋ ᴀᴄᴛɪꜰ:</gray> <gold>" + prof.selectedDunkStyle().displayName() + "</gold>",
                "",
                "<yellow>▸ ᴄʟɪǫᴜᴇʀ ᴘᴏᴜʀ ᴏᴜᴠʀɪʀ ʟᴇ ᴠᴇꜱᴛɪᴀɪʀᴇ</yellow>"
            )
        ));

        // Slot 30: Free Practice & Dunk Lab (3D Air Bosket Sneakers)
        inv.setItem(30, BallSkin.createCustomModelItem(
            Material.LEATHER_BOOTS,
            "sneakers_air",
            "<gradient:#48FFB2:#00D2FF><bold>👟 ᴇɴᴛʀᴀɪɴᴇᴍᴇɴᴛ & ᴅᴜɴᴋ ʟᴀʙ</bold></gradient>",
            List.of(
                "<gray>ᴛᴇʀʀᴀɪɴ ʟɪʙʀᴇ ᴘᴏᴜʀ ᴍᴀɪᴛʀɪꜱᴇʀ ʟᴇ ɢʀᴇᴇɴ ʀᴇʟᴇᴀꜱᴇ</gray>",
                "<gray>ᴇᴛ ᴛᴇꜱᴛᴇʀ ᴠᴏꜱ 5 ꜱᴛʏʟᴇꜱ ᴅᴇ ꜱʟᴀᴍ ᴅᴜɴᴋ 3ᴅ !</gray>",
                "",
                "<yellow>▸ ᴄʟɪǫᴜᴇʀ ᴘᴏᴜʀ ᴇɴᴛʀᴇʀ ꜱᴜʀ ʟᴇ ᴘᴀʀǫᴜᴇᴛ</yellow>"
            )
        ));

        // Slot 31: Hall of Fame & Career Card (3D Golden Trophy)
        inv.setItem(31, BallSkin.createCustomModelItem(
            Material.GOLDEN_CARROT,
            "trophy_mvp",
            "<gradient:#FFD43B:#FFF6A3><bold>🏆 ᴄʟᴀꜱꜱᴇᴍᴇɴᴛꜱ & ᴄᴀʀʀɪᴇʀᴇ</bold></gradient>",
            List.of(
                "<gray>ʀᴀɴɢ:</gray> <!shadow><white>" + prof.effectiveRank().pillGlyph() + "</white></!shadow> <gold>(" + prof.elo() + " ᴇʟᴏ)</gold>",
                "<gray>ᴘᴏɪɴᴛꜱ:</gray> <yellow>" + prof.points() + "</yellow> <dark_gray>•</dark_gray> <gray>ᴠɪᴄᴛᴏɪʀᴇꜱ:</gray> <green>" + prof.wins() + "</green>",
                "<gray>ᴅᴜɴᴋꜱ:</gray> <red>" + prof.dunks() + "</red> <dark_gray>•</dark_gray> <gray>ᴍᴠᴘꜱ:</gray> <gold>" + prof.mvps() + "</gold>",
                "",
                "<yellow>▸ ᴄʟɪǫᴜᴇʀ ᴘᴏᴜʀ ᴠᴏɪʀ ʟᴇ ᴛᴏᴘ 10</yellow>"
            )
        ));

        // Slot 32: 3D Resource Pack Push (3D Referee Whistle)
        inv.setItem(32, BallSkin.createCustomModelItem(
            Material.IRON_NUGGET,
            "referee_whistle",
            "<gradient:#7CF4FF:#3DB8FF><bold>📦 ᴘᴀᴄᴋ ᴅᴇ ᴛᴇxᴛᴜʀᴇꜱ 3ᴅ (26.2)</bold></gradient>",
            List.of(
                "<gray>ᴄʜᴀʀɢᴇʀ ʟᴇꜱ ᴍᴏᴅᴇʟᴇꜱ 3ᴅ, ʟᴇ ʜᴜᴅ ʙʀᴏᴀᴅᴄᴀꜱᴛ,</gray>",
                "<gray>ʟᴇꜱ ʙᴀᴅɢᴇꜱ ᴅᴇ ᴄʜᴀᴛ ᴇᴛ ʟᴇꜱ 12 ꜱᴏɴꜱ ꜱᴛᴜᴅɪᴏ !</gray>",
                "",
                "<yellow>▸ ᴄʟɪǫᴜᴇʀ ᴘᴏᴜʀ ʀᴇᴄᴇᴠᴏɪʀ ʟᴇ ᴘᴀᴄᴋ</yellow>"
            )
        ));

        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.8f, 1.15f);
    }

    public void openPlaySelector(Player player) {
        BosketGuiHolder holder = new BosketGuiHolder(MenuType.PLAY_MODES);
        Inventory inv = Bukkit.createInventory(
            holder,
            45,
            Msg.parse("<gradient:#FF7A18:#FFD43B><bold>✦ ᴄʜᴏɪx ᴅᴜ ᴍᴏᴅᴇ & ꜱᴛᴀᴅᴇ ✦</bold></gradient>")
        );
        holder.setInventory(inv);
        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        inv.setItem(10, modeIcon("basketball_classic", "<gold><bold>⚔ ᴅᴜᴇʟ 1ᴠ1 (21 ᴘᴛꜱ)</bold></gold>", GameModeType.DUEL_1V1));
        inv.setItem(12, modeIcon("basketball_neon", "<aqua><bold>⚡ ꜱᴛʀᴇᴇᴛ 2ᴠ2 ɴʙᴀ ᴊᴀᴍ</bold></aqua>", GameModeType.STREET_2V2));
        inv.setItem(14, modeIcon("basketball_money", "<green><bold>🏀 ᴘʀᴏ 3ᴠ3 ᴏꜰꜰɪᴄɪᴇʟ</bold></green>", GameModeType.PRO_3V3));
        inv.setItem(16, modeIcon("basketball_fire", "<red><bold>🔥 ʟɪɢᴜᴇ 5ᴠ5 ᴄᴏᴍᴘʟᴇᴛᴇ</bold></red>", GameModeType.FULL_5V5));

        int slot = 28;
        for (Arena arena : plugin.arenaManager().allArenas()) {
            if (slot > 34) {
                break;
            }
            MatchSession session = plugin.gameManager().getSessionByArena(arena.id());
            String status = session == null
                ? "<green>● ᴛᴇʀʀᴀɪɴ ᴅɪꜱᴘᴏɴɪʙʟᴇ</green>"
                : "<gold>● " + session.mode().smallCapsTitle() + " (" + session.participantCount() + "/" + session.mode().maxPlayers() + ")</gold>";
            inv.setItem(slot++, makeItem(
                Material.BEACON,
                "<yellow><bold>🏟 " + arena.displayName() + "</bold></yellow>",
                List.of(
                    "<gray>ɪᴅ:</gray> <white>" + arena.id() + "</white>",
                    status,
                    "",
                    "<yellow>▸ ᴄʟɪǫᴜᴇʀ ᴘᴏᴜʀ ʀᴇᴊᴏɪɴᴅʀᴇ ᴄᴇ ꜱᴛᴀᴅᴇ</yellow>"
                )
            ));
        }

        inv.setItem(40, makeItem(Material.ARROW, "<gray>« ʀᴇᴛᴏᴜʀ ᴀᴜ ᴍᴇɴᴜ</gray>", List.of()));
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.8f, 1.15f);
    }

    public void openLockerRoom(Player player) {
        BosketGuiHolder holder = new BosketGuiHolder(MenuType.LOCKER_ROOM);
        Inventory inv = Bukkit.createInventory(
            holder,
            45,
            Msg.parse("<gradient:#00F0FF:#FF2BD6><bold>✦ ᴠᴇꜱᴛɪᴀɪʀᴇ ᴄᴏꜱᴍᴇᴛɪǫᴜᴇꜱ 3ᴅ ✦</bold></gradient>")
        );
        holder.setInventory(inv);
        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE);

        PlayerProfile prof = plugin.statsManager().getOrCreate(player);

        // Row 2 (Slots 10, 12, 14, 16): 4 3D PBR Sphere Basketball Skins!
        int[] skinSlots = {10, 12, 14, 16};
        BallSkin[] skins = BallSkin.values();
        for (int i = 0; i < skins.length; i++) {
            BallSkin skin = skins[i];
            boolean unlocked = prof.elo() >= skin.unlockElo() || player.hasPermission("bosketball.admin");
            boolean equipped = prof.selectedBallSkin() == skin;
            inv.setItem(skinSlots[i], BallSkin.createCustomModelItem(
                Material.SLIME_BALL,
                skin.modelId(),
                skin.displayMiniMessage(),
                List.of(
                    "<gray>" + skin.descriptionSmallCaps() + "</gray>",
                    "<gray>ʀᴇǫᴜɪꜱ:</gray> <gold>" + skin.unlockElo() + " ᴇʟᴏ</gold>",
                    "",
                    equipped ? "<green><bold>✓ ᴇǫᴜɪᴘᴇ</bold></green>"
                        : (unlocked ? "<yellow>▸ ᴄʟɪǫᴜᴇʀ ᴘᴏᴜʀ ᴇǫᴜɪᴘᴇʀ</yellow>" : "<red>✖ ᴠᴇʀʀᴏᴜɪʟʟᴇ (" + skin.unlockElo() + " ᴇʟᴏ)</red>")
                )
            ));
        }

        // Row 4 (Slots 28..32): 5 Signature 3D Slam Dunk Styles!
        DunkStyle[] dunks = DunkStyle.values();
        for (int i = 0; i < dunks.length; i++) {
            DunkStyle d = dunks[i];
            boolean unlocked = prof.elo() >= d.unlockElo() || player.hasPermission("bosketball.admin");
            boolean equipped = prof.selectedDunkStyle() == d;
            inv.setItem(28 + i, makeItem(
                equipped ? Material.BLAZE_POWDER : Material.FIREWORK_STAR,
                "<gold><bold>" + d.displayName() + "</bold></gold>",
                List.of(
                    "<gray>" + d.descriptionSmallCaps() + "</gray>",
                    "<gray>ʀᴇǫᴜɪꜱ:</gray> <gold>" + d.unlockElo() + " ᴇʟᴏ</gold>",
                    "",
                    equipped ? "<green><bold>✓ ꜱᴛʏʟᴇ ᴀᴄᴛɪꜰ</bold></green>"
                        : (unlocked ? "<yellow>▸ ᴄʟɪǫᴜᴇʀ ᴘᴏᴜʀ ꜱᴇʟᴇᴄᴛɪᴏɴɴᴇʀ</yellow>" : "<red>✖ ᴠᴇʀʀᴏᴜɪʟʟᴇ (" + d.unlockElo() + " ᴇʟᴏ)</red>")
                )
            ));
        }

        inv.setItem(40, makeItem(Material.ARROW, "<gray>« ʀᴇᴛᴏᴜʀ ᴀᴜ ᴍᴇɴᴜ</gray>", List.of()));
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.8f, 1.15f);
    }

    public void openLeaderboards(Player player) {
        BosketGuiHolder holder = new BosketGuiHolder(MenuType.LEADERBOARDS);
        Inventory inv = Bukkit.createInventory(
            holder,
            45,
            Msg.parse("<gradient:#FFD43B:#FF7A18><bold>✦ ᴘᴀɴᴛʜᴇᴏɴ & ᴄʟᴀꜱꜱᴇᴍᴇɴᴛꜱ ✦</bold></gradient>")
        );
        holder.setInventory(inv);
        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE);

        inv.setItem(19, buildTopColumnItem(StatsManager.LeaderboardCategory.ELO, "trophy_mvp", Material.GOLDEN_CARROT));
        inv.setItem(21, buildTopColumnItem(StatsManager.LeaderboardCategory.WINS, "basketball_classic", Material.SLIME_BALL));
        inv.setItem(23, buildTopColumnItem(StatsManager.LeaderboardCategory.POINTS, "basketball_fire", Material.SLIME_BALL));
        inv.setItem(25, buildTopColumnItem(StatsManager.LeaderboardCategory.THREE_POINTERS, "basketball_money", Material.SLIME_BALL));

        inv.setItem(40, makeItem(Material.ARROW, "<gray>« ʀᴇᴛᴏᴜʀ ᴀᴜ ᴍᴇɴᴜ</gray>", List.of()));
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.8f, 1.15f);
    }

    private ItemStack buildTopColumnItem(StatsManager.LeaderboardCategory cat, String modelId, Material base) {
        List<PlayerProfile> top = plugin.statsManager().topBy(cat, 10);
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>");
        if (top.isEmpty()) {
            lore.add("<gray>ᴀᴜᴄᴜɴ ᴊᴏᴜᴇᴜʀ ᴄʟᴀꜱꜱᴇ ᴘᴏᴜʀ ʟ'ɪɴꜱᴛᴀɴᴛ</gray>");
        } else {
            for (int i = 0; i < top.size(); i++) {
                PlayerProfile p = top.get(i);
                int val = switch (cat) {
                    case ELO -> p.elo();
                    case WINS -> p.wins();
                    case POINTS -> p.points();
                    case DUNKS -> p.dunks();
                    case THREE_POINTERS -> p.threePointersMade();
                };
                String medal = switch (i) {
                    case 0 -> "<#FFD43B><bold>#1</bold></#FFD43B>";
                    case 1 -> "<#DCE4F2><bold>#2</bold></#DCE4F2>";
                    case 2 -> "<#E59866><bold>#3</bold></#E59866>";
                    default -> "<gray>#" + (i + 1) + "</gray>";
                };
                lore.add(medal + " <!shadow><white>" + p.effectiveRank().pillGlyph() + "</white></!shadow> <white>"
                    + p.lastKnownName() + "</white> <dark_gray>—</dark_gray> <gold>" + val + "</gold>");
            }
        }
        return BallSkin.createCustomModelItem(
            base,
            modelId,
            "<gradient:#FFD43B:#FF922B><bold>🏆 " + cat.titleSmallCaps() + "</bold></gradient>",
            lore
        );
    }

    public void handleMenuClick(Player player, BosketGuiHolder holder, InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= holder.getInventory().getSize()) {
            return;
        }

        switch (holder.type()) {
            case MAIN_HUB -> {
                switch (slot) {
                    case 20 -> openPlaySelector(player);
                    case 22 -> {
                        player.closeInventory();
                        plugin.gameManager().quickJoin(player, GameModeType.THREE_POINT_CONTEST, null);
                    }
                    case 24 -> openLockerRoom(player);
                    case 30 -> {
                        player.closeInventory();
                        plugin.gameManager().quickJoin(player, GameModeType.PRACTICE, null);
                    }
                    case 31 -> openLeaderboards(player);
                    case 32 -> {
                        player.closeInventory();
                        plugin.resourcePackHost().sendPackTo(player);
                    }
                    default -> {
                    }
                }
            }
            case PLAY_MODES -> {
                switch (slot) {
                    case 10 -> {
                        player.closeInventory();
                        plugin.gameManager().quickJoin(player, GameModeType.DUEL_1V1, null);
                    }
                    case 12 -> {
                        player.closeInventory();
                        plugin.gameManager().quickJoin(player, GameModeType.STREET_2V2, null);
                    }
                    case 14 -> {
                        player.closeInventory();
                        plugin.gameManager().quickJoin(player, GameModeType.PRO_3V3, null);
                    }
                    case 16 -> {
                        player.closeInventory();
                        plugin.gameManager().quickJoin(player, GameModeType.FULL_5V5, null);
                    }
                    case 40 -> openMainHub(player);
                    default -> {
                        if (slot >= 28 && slot <= 34) {
                            List<Arena> list = new ArrayList<>(plugin.arenaManager().allArenas());
                            int idx = slot - 28;
                            if (idx < list.size()) {
                                player.closeInventory();
                                Arena chosenArena = list.get(idx);
                                MatchSession existing = plugin.gameManager().getSessionByArena(chosenArena.id());
                                GameModeType targetMode = (existing != null && existing.state() == MatchSession.State.WAITING)
                                    ? existing.mode()
                                    : (event.isRightClick() ? GameModeType.PRACTICE : GameModeType.DUEL_1V1);
                                plugin.gameManager().quickJoin(player, targetMode, chosenArena.id());
                            }
                        }
                    }
                }
            }
            case LOCKER_ROOM -> {
                if (slot == 40) {
                    openMainHub(player);
                    return;
                }
                PlayerProfile prof = plugin.statsManager().getOrCreate(player);
                int[] skinSlots = {10, 12, 14, 16};
                for (int i = 0; i < skinSlots.length; i++) {
                    if (slot == skinSlots[i]) {
                        BallSkin chosen = BallSkin.values()[i];
                        if (prof.elo() < chosen.unlockElo() && !player.hasPermission("bosketball.admin")) {
                            Msg.send(player, "<red>ɪʟ ᴠᴏᴜꜱ ꜰᴀᴜᴛ <gold>" + chosen.unlockElo() + " ᴇʟᴏ</gold> ᴘᴏᴜʀ ᴅᴇʙʟᴏǫᴜᴇʀ ᴄᴇ ʙᴀʟʟᴏɴ 3ᴅ !</red>");
                            return;
                        }
                        prof.setSelectedBallSkin(chosen);
                        plugin.statsManager().save();
                        Msg.send(player, "<green>✓ ʙᴀʟʟᴏɴ 3ᴅ ᴇǫᴜɪᴘᴇ:</green> " + chosen.displayMiniMessage());
                        openLockerRoom(player);
                        return;
                    }
                }
                if (slot >= 28 && slot < 28 + DunkStyle.values().length) {
                    DunkStyle chosen = DunkStyle.values()[slot - 28];
                    if (prof.elo() < chosen.unlockElo() && !player.hasPermission("bosketball.admin")) {
                        Msg.send(player, "<red>ɪʟ ᴠᴏᴜꜱ ꜰᴀᴜᴛ <gold>" + chosen.unlockElo() + " ᴇʟᴏ</gold> ᴘᴏᴜʀ ᴄᴇ ꜱʟᴀᴍ ᴅᴜɴᴋ !</red>");
                        return;
                    }
                    prof.setSelectedDunkStyle(chosen);
                    plugin.statsManager().save();
                    Msg.send(player, "<green>✓ ꜱᴛʏʟᴇ ᴅᴇ ᴅᴜɴᴋ ᴇǫᴜɪᴘᴇ:</green> <gold>" + chosen.displayName() + "</gold>");
                    openLockerRoom(player);
                }
            }
            case LEADERBOARDS -> {
                if (slot == 40) {
                    openMainHub(player);
                }
            }
        }
    }

    private ItemStack modeIcon(String modelId, String titleMm, GameModeType mode) {
        return BallSkin.createCustomModelItem(
            Material.SLIME_BALL,
            modelId,
            titleMm,
            List.of(
                "<gray>ꜰᴏʀᴍᴀᴛ:</gray> <white>" + mode.playersPerTeam() + "ᴠ" + mode.playersPerTeam() + "</white>",
                "<gray>ᴊᴏᴜᴇᴜʀꜱ ᴍᴀx:</gray> <gold>" + mode.maxPlayers() + "</gold>",
                "",
                "<yellow>▸ ᴄʟɪǫᴜᴇʀ ᴘᴏᴜʀ ʀᴇᴊᴏɪɴᴅʀᴇ ʟᴀ ꜰɪʟᴇ</yellow>"
            )
        );
    }

    private static ItemStack makeItem(Material mat, String titleMm, List<String> loreMm) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.parse(titleMm).decoration(TextDecoration.ITALIC, false));
            if (!loreMm.isEmpty()) {
                meta.lore(loreMm.stream().map(s -> Msg.parse(s).decoration(TextDecoration.ITALIC, false)).toList());
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static void fillBorder(Inventory inv, Material filler, Material accent) {
        ItemStack dark = makeItem(filler, " ", List.of());
        ItemStack glow = makeItem(accent, " ", List.of());
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == (size / 9) - 1 || col == 0 || col == 8) {
                inv.setItem(i, (col == 0 || col == 8) && (row == 0 || row == (size / 9) - 1) ? glow : dark);
            } else {
                inv.setItem(i, dark);
            }
        }
    }
}
