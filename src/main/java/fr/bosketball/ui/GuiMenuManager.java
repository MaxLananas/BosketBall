package fr.bosketball.ui;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.game.GameModeType;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.ChatTag;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.MatchHistoryEntry;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.PlayerSettings;
import fr.bosketball.social.SocialPartyManager;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.stats.StatsManager.LeaderboardCategory;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Clean, Custom-Pixel-Art /bb Menu Suite (Requirements #4, #5, #10):
 *   - Resource Pack button completely removed!
 *   - 100% custom bosketball:icon_* sprites (image-10.png craft) + 0.52-scale square basketballs!
 *   - Includes: Main Hub (with 3D Locker-Room portal!), Play Modes, Ball Cosmetics,
 *     20-Tag Glossy Vault, 7-Category Leaderboards, Match History, Friends & Party, and Settings!
 */
public final class GuiMenuManager implements Listener {

    public enum MenuType {
        MAIN_HUB,
        PLAY_MODES,
        COSMETICS,
        TAGS_VAULT,
        LEADERBOARDS,
        MATCH_HISTORY,
        FRIENDS_PARTY,
        SETTINGS
    }

    public static final class BosketHolder implements InventoryHolder {
        private final MenuType type;
        private final LeaderboardCategory leaderboardCategory;
        private final int page;
        private Inventory inventory;

        public BosketHolder(MenuType type) {
            this(type, LeaderboardCategory.ELO, 0);
        }

        public BosketHolder(MenuType type, LeaderboardCategory leaderboardCategory) {
            this(type, leaderboardCategory, 0);
        }

        public BosketHolder(MenuType type, int page) {
            this(type, LeaderboardCategory.ELO, page);
        }

        public BosketHolder(MenuType type, LeaderboardCategory leaderboardCategory, int page) {
            this.type = type;
            this.leaderboardCategory = leaderboardCategory;
            this.page = page;
        }

        public MenuType type() {
            return type;
        }

        public LeaderboardCategory leaderboardCategory() {
            return leaderboardCategory;
        }

        public int page() {
            return page;
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

    public void openPlaySelector(Player player) {
        openPlayModes(player);
    }

    public void openLockerRoom(Player player) {
        openCosmetics(player);
    }

    public void openLeaderboards(Player player) {
        openLeaderboards(player, LeaderboardCategory.ELO);
    }

    private static net.kyori.adventure.text.Component guiTitle(char iconGlyph, String gradientTitleMm) {
        return Msg.parse("<!shadow><white>" + iconGlyph + "</white></!shadow> " + gradientTitleMm);
    }

    // -------------------------------------------------------------------------
    // 1. MAIN HUB (/bb) — Clean, cohesive, 100% custom icons, NO resource-pack filler!
    // -------------------------------------------------------------------------
    public void openMainHub(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.MAIN_HUB);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            guiTitle(Glyphs.ICON_BALL, "<gradient:#FF8A28:#FFE259><bold>ʙᴏꜱᴋᴇᴛʙᴀʟʟ 26.2 • ʜᴜʙ</bold></gradient>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        int groupSize = plugin.socialManager().partySizeOrOne(player.getUniqueId());
        int onlineFriends = plugin.socialManager().countOnlineFriends(player.getUniqueId());

        // Top Center (Slot 13): Interactive 3D Locker-Room Stage Portal!
        inv.setItem(13, customIcon(
            "icon_portal",
            "<gradient:#52FFB8:#38BDF8><bold>✦ ᴠᴇꜱᴛɪᴀɪʀᴇ 3ᴅ ɪɴᴛᴇʀᴀᴄᴛɪꜰ</bold></gradient>",
            List.of(
                "<gray>ᴇɴᴛʀᴇᴢ ᴅᴀɴꜱ ʟᴀ ꜱᴄᴇɴᴇ 3ᴅ ᴘʟᴇɪɴ ᴇᴄʀᴀɴ</gray>",
                "<gray>ᴀᴠᴇᴄ ᴠᴏᴛʀᴇ ɢʀᴏᴜᴘᴇ ᴇᴛ ᴠᴏꜱ ꜱᴛᴀᴛᴜᴇꜱ 3ᴅ !</gray>",
                "",
                "<aqua>• ɴᴀᴠɪɢᴀᴛɪᴏɴ [Z/S/Q/D] + [ᴇꜱᴘᴀᴄᴇ]</aqua>",
                "<yellow>▸ ᴄʟɪǫᴜᴇʀ ᴘᴏᴜʀ ᴏᴜᴠʀɪʀ (/bb 3d)</yellow>"
            )
        ));

        // Row 3: Core Gameplay & Customization
        inv.setItem(20, customIcon(
            "icon_arena",
            "<gradient:#FF6B2B:#FFD43B><bold>ᴊᴏᴜᴇʀ ᴇɴ ᴍᴀᴛᴄʜ</bold></gradient>",
            List.of(
                "<gray>ꜰɪʟᴇꜱ ᴅ'ᴀᴛᴛᴇɴᴛᴇ 1ᴠ1, 2ᴠ2, 3ᴠ3 & 5ᴠ5</gray>",
                "<gray>ᴇɴ ꜰɪʟᴇ:</gray> <green>" + plugin.gameManager().queuedCount() + "</green> <dark_gray>•</dark_gray> <gray>ᴇɴ ᴍᴀᴛᴄʜ:</gray> <gold>" + plugin.gameManager().activePlayerCount() + "</gold>",
                "",
                "<yellow>▸ ᴄʜᴏɪꜱɪʀ ᴜɴ ᴍᴏᴅᴇ</yellow>"
            )
        ));

        ItemStack practiceBall = profile.selectedBallSkin().createItemStack();
        ItemMeta pm = practiceBall.getItemMeta();
        if (pm != null) {
            pm.displayName(Msg.parse("<gradient:#56CCF2:#2F80ED><bold>ᴇɴᴛʀᴀɪɴᴇᴍᴇɴᴛ 24ꜱ + ʙᴏᴛ</bold></gradient>"));
            pm.lore(List.of(
                Msg.parse("<gray>ᴛɪʀᴇᴢ ʟɪʙʀᴇᴍᴇɴᴛ ᴀᴠᴇᴄ ʟ'ʜᴏʀʟᴏɢᴇ 24ꜱ</gray>"),
                Msg.parse("<gray>ᴇᴛ ᴀꜰꜰʀᴏɴᴛᴇᴢ ʟᴇ ᴅᴇꜰᴇɴꜱᴇᴜʀ ɪᴀ !</gray>"),
                Msg.parse(""),
                Msg.parse("<aqua>▸ ᴇɴᴛʀᴇʀ ꜱᴜʀ ʟᴇ ᴘᴀʀǫᴜᴇᴛ</aqua>")
            ));
            practiceBall.setItemMeta(pm);
        }
        inv.setItem(21, practiceBall);

        inv.setItem(22, customIcon(
            "icon_chest",
            "<gradient:#B24592:#F15F79><bold>ʙᴀʟʟᴏɴꜱ ᴄᴀʀʀᴇꜱ 3ᴅ</bold></gradient>",
            List.of(
                "<gray>8 ꜱᴋɪɴꜱ ᴅᴇ ʙᴀʟʟᴏɴꜱ ᴄᴜʙɪǫᴜᴇꜱ 3ᴅ</gray>",
                "<gray>ᴇǫᴜɪᴘᴇ:</gray> " + profile.selectedBallSkin().displayNameMm(),
                "",
                "<light_purple>▸ ᴏᴜᴠʀɪʀ ʟᴇ ᴠᴇꜱᴛɪᴀɪʀᴇ ʙᴀʟʟᴏɴꜱ</light_purple>"
            )
        ));

        StringBuilder eqTagsSb = new StringBuilder();
        for (ChatTag ct : profile.equippedTags()) {
            if (!eqTagsSb.isEmpty()) {
                eqTagsSb.append(" ");
            }
            eqTagsSb.append("<!shadow><white>").append(ct.glyph()).append("</white></!shadow>");
        }
        String equippedTag = eqTagsSb.isEmpty() ? "<gray>ᴀᴜᴄᴜɴ</gray>" : eqTagsSb.toString();
        inv.setItem(23, customIcon(
            "icon_tags",
            "<gradient:#FFD43B:#FF8A28><bold>ᴛᴀɢꜱ & ʙᴀᴅɢᴇꜱ ɢʟᴏꜱꜱʏ (54)</bold></gradient>",
            List.of(
                "<gray>54 ᴛᴀɢꜱ 3ᴅ ʙɪꜱᴇᴀᴜᴛᴇꜱ • ᴊᴜꜱǫᴜ'ᴀ 4 ᴀᴄᴛɪꜰꜱ !</gray>",
                "<gray>ᴇǫᴜɪᴘᴇꜱ:</gray> " + equippedTag,
                "",
                "<gold>▸ ɢᴇʀᴇʀ ᴍᴇꜱ ᴛᴀɢꜱ (54)</gold>"
            )
        ));

        inv.setItem(24, customIcon(
            "icon_podium",
            "<gradient:#F7971E:#FFD200><bold>ᴄʟᴀꜱꜱᴇᴍᴇɴᴛꜱ ᴛᴏᴘ 10</bold></gradient>",
            List.of(
                "<gray>7 ᴄᴀᴛᴇɢᴏʀɪᴇꜱ ᴅᴏɴᴛ ᴘᴀꜱꜱᴇꜱ, ᴇʟᴏ & ᴍᴠᴘ</gray>",
                "<gray>ʟᴇꜱ #1-#3 ɢᴀɢɴᴇɴᴛ ᴅᴇꜱ ʙᴀᴅɢᴇꜱ ᴄʜᴀᴛ !</gray>",
                "",
                "<gold>▸ ᴠᴏɪʀ ʟᴇ ᴘᴀɴᴛʜᴇᴏɴ</gold>"
            )
        ));

        // Row 4: Match History, Career Profile Skull, Friends & Party
        inv.setItem(29, customIcon(
            "icon_history",
            "<gradient:#38BDF8:#818CF8><bold>ʜɪꜱᴛᴏʀɪǫᴜᴇ ᴅᴇꜱ ᴍᴀᴛᴄʜꜱ</bold></gradient>",
            List.of(
                "<gray>ᴄᴏɴꜱᴜʟᴛᴇᴢ ᴠᴏꜱ 15 ᴅᴇʀɴɪᴇʀꜱ ᴍᴀᴛᴄʜꜱ,</gray>",
                "<gray>ꜱᴄᴏʀᴇꜱ, ꜱᴛᴀᴛꜱ ᴇᴛ ɢᴀɪɴꜱ ᴅ'ᴇʟᴏ !</gray>",
                "<gray>ᴍᴀᴛᴄʜꜱ ᴇɴʀᴇɢɪꜱᴛʀᴇꜱ:</gray> <white>" + profile.matchHistory().size() + "</white>",
                "",
                "<aqua>▸ ᴏᴜᴠʀɪʀ ʟ'ʜɪꜱᴛᴏʀɪǫᴜᴇ</aqua>"
            )
        ));

        inv.setItem(31, buildPlayerSkullCard(player, profile));

        inv.setItem(33, customIcon(
            "icon_friends",
            "<gradient:#34D399:#10B981><bold>ᴀᴍɪꜱ & ɢʀᴏᴜᴘᴇ (" + groupSize + "/4)</bold></gradient>",
            List.of(
                "<gray>ɢʀᴏᴜᴘᴇ ᴀᴄᴛᴜᴇʟ:</gray> <green>" + groupSize + "/4 ᴊᴏᴜᴇᴜʀꜱ</green>",
                "<gray>ᴀᴍɪꜱ ᴇɴ ʟɪɢɴᴇ:</gray> <aqua>" + onlineFriends + "</aqua><gray>/" + profile.friends().size() + "</gray>",
                "",
                "<green>▸ ɢᴇʀᴇʀ ᴍᴏɴ ᴇꜱᴄᴏᴜᴀᴅᴇ</green>"
            )
        ));

        // Row 5: Center Tab Toggle + Player Settings + Leave Match (if active)
        inv.setItem(39, customIcon(
            "icon_tab",
            "<gradient:#60A5FA:#38BDF8><bold>ᴛᴀʙ ᴄᴇɴᴛʀᴀʟ 3ᴅ ([F])</bold></gradient>",
            List.of(
                "<gray>ᴀꜰꜰɪᴄʜᴇ ʟᴇ ᴛᴀʙʟᴇᴀᴜ ᴄᴇɴᴛʀᴀʟ ᴀᴠᴇᴄ</gray>",
                "<gray>ᴛᴇᴛᴇꜱ ᴅᴇ ᴊᴏᴜᴇᴜʀꜱ, ᴇǫᴜɪᴘᴇꜱ & ᴘᴏɪɴᴛꜱ !</gray>",
                "",
                "<aqua>▸ ᴀꜰꜰɪᴄʜᴇʀ ᴍᴀɪɴᴛᴇɴᴀɴᴛ</aqua>"
            )
        ));

        inv.setItem(40, customIcon(
            "icon_settings",
            "<gradient:#CBD5E1:#94A3B8><bold>ᴘᴀʀᴀᴍᴇᴛʀᴇꜱ ᴊᴏᴜᴇᴜʀ</bold></gradient>",
            List.of(
                "<gray>ᴀɴɪᴍᴀᴛɪᴏɴꜱ 3ᴅ, ᴄᴇʀᴄʟᴇꜱ ᴀᴜ ꜱᴏʟ,</gray>",
                "<gray>ꜱᴏɴꜱ ᴄᴜꜱᴛᴏᴍ & ᴄᴀᴍᴇʀᴀ ᴘᴀɴɪᴇʀ</gray>",
                "",
                "<white>▸ ᴄᴏɴꜰɪɢᴜʀᴇʀ</white>"
            )
        ));

        if (plugin.gameManager().isInMatch(player.getUniqueId())) {
            inv.setItem(41, customIcon(
                "icon_leave",
                "<red><bold>ǫᴜɪᴛᴛᴇʀ ʟᴇ ᴛᴇʀʀᴀɪɴ</bold></red>",
                List.of("<gray>ʀᴇᴛᴏᴜʀɴᴇʀ ᴀᴜ ʟᴏʙʙʏ ʙᴏꜱᴋᴇᴛʙᴀʟʟ</gray>")
            ));
        } else {
            inv.setItem(41, customIcon(
                "icon_book",
                "<gradient:#FDE047:#F59E0B><bold>ɢᴜɪᴅᴇ ᴅᴇꜱ ᴄᴏɴᴛʀᴏʟᴇꜱ</bold></gradient>",
                List.of(
                    "<yellow>ᴄʟɪᴄ ᴅʀᴏɪᴛ (ᴍᴀɪɴᴛᴇɴɪʀ)</yellow> <gray>— ᴊᴀᴜɢᴇ ᴅᴇ ᴛɪʀ 3ᴅ</gray>",
                    "<yellow>ᴄʟɪᴄ ɢᴀᴜᴄʜᴇ (ᴇɴ ʟ'ᴀɪʀ)</yellow> <gray>— ꜱʟᴀᴍ ᴅᴜɴᴋ 360°</gray>",
                    "<yellow>ꜱʟᴏᴛ 2 / 3 / 4</yellow> <gray>— ᴘᴀꜱꜱᴇ / ᴄʀᴏꜱꜱ / ᴅᴜɴᴋ</gray>",
                    "<yellow>ᴛᴏᴜᴄʜᴇ [F]</yellow> <gray>— ᴛᴀʙ ᴄᴇɴᴛʀᴀʟ 3ᴅ</gray>"
                )
            ));
        }

        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
    }

    // -------------------------------------------------------------------------
    // 2. PLAY MODES
    // -------------------------------------------------------------------------
    public void openPlayModes(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.PLAY_MODES);
        Inventory inv = Bukkit.createInventory(
            holder,
            45,
            guiTitle(Glyphs.ICON_FIRE, "<gradient:#FF6B2B:#FFD43B><bold>ᴍᴏᴅᴇꜱ ᴅᴇ ᴊᴇᴜ</bold></gradient>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        String[] modeIcons = {"icon_dunk", "icon_crossover", "icon_arena", "icon_podium"};
        GameModeType[] modes = {
            GameModeType.DUEL_1V1,
            GameModeType.STREET_2V2,
            GameModeType.PRO_3V3,
            GameModeType.FULL_5V5
        };
        int[] slots = {19, 21, 23, 25};
        for (int i = 0; i < modes.length; i++) {
            GameModeType mode = modes[i];
            int q = plugin.gameManager().queuedCount(mode);
            inv.setItem(slots[i], customIcon(
                modeIcons[i],
                "<gradient:#FF8C2B:#FFE259><bold>" + mode.smallCapsTitle() + "</bold></gradient>",
                List.of(
                    "<gray>ꜰᴏʀᴍᴀᴛ ᴄᴏᴍᴘᴇᴛɪᴛɪꜰ:</gray> <aqua>" + mode.playersPerTeam() + " ᴄᴏɴᴛʀᴇ " + mode.playersPerTeam() + "</aqua>",
                    "<gray>ᴇɴ ᴀᴛᴛᴇɴᴛᴇ:</gray> <green>" + q + "/" + mode.maxPlayers() + "</green>",
                    "",
                    "<yellow>▸ ʀᴇᴊᴏɪɴᴅʀᴇ ʟᴀ ꜰɪʟᴇ</yellow>"
                )
            ));
        }

        inv.setItem(29, customIcon(
            "icon_tags",
            "<gradient:#00F0FF:#FFD43B><bold>🎯 ᴄᴏɴᴄᴏᴜʀꜱ 3-ᴘᴏɪɴᴛꜱ</bold></gradient>",
            List.of("<gray>5 ʀᴀᴄᴋꜱ • 25 ʙᴀʟʟᴏɴꜱ • 60 ꜱᴇᴄᴏɴᴅᴇꜱ</gray>", "", "<yellow>▸ ʟᴀɴᴄᴇʀ ʟᴇ ᴄᴏɴᴄᴏᴜʀꜱ</yellow>")
        ));

        inv.setItem(30, customIcon(
            "icon_star_shield",
            "<aqua><bold>ᴍᴏᴅᴇ ꜱᴘᴇᴄᴛᴀᴛᴇᴜʀ</bold></aqua>",
            List.of("<gray>ʀᴇɢᴀʀᴅᴇʀ ᴜɴ ᴍᴀᴛᴄʜ ᴇɴ ᴅɪʀᴇᴄᴛ</gray>", "", "<aqua>▸ ᴏʙꜱᴇʀᴠᴇʀ</aqua>")
        ));
        inv.setItem(32, customIcon(
            "icon_portal",
            "<#52FFB8><bold>ᴏᴜᴠʀɪʀ ʟᴇ ᴠᴇꜱᴛɪᴀɪʀᴇ 3ᴅ</bold></#52FFB8>",
            List.of("<gray>ʙᴀꜱᴄᴜʟᴇʀ ᴠᴇʀꜱ ʟ'ɪɴᴛᴇʀꜰᴀᴄᴇ 3ᴅ</gray>")
        ));
        inv.setItem(40, backButton());

        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.25f);
    }

    // -------------------------------------------------------------------------
    // 3. SQUARE BASKETBALL COSMETICS (0.52 inventory scale!) & 3D SLAM DUNK STYLES
    // -------------------------------------------------------------------------
    public void openCosmetics(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.COSMETICS);
        Inventory inv = Bukkit.createInventory(
            holder,
            45,
            guiTitle(Glyphs.ICON_BALL, "<gradient:#C471ED:#F64F59><bold>ʙᴀʟʟᴏɴꜱ ᴄᴀʀʀᴇꜱ & ᴅᴜɴᴋꜱ</bold></gradient>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        BallSkin[] skins = BallSkin.values();
        int[] skinSlots = {9, 10, 11, 12, 14, 15, 16, 17};

        for (int i = 0; i < skins.length && i < skinSlots.length; i++) {
            BallSkin skin = skins[i];
            boolean selected = profile.selectedBallSkin() == skin;
            boolean unlocked = profile.elo() >= skin.unlockElo() || player.hasPermission("bosketball.admin");

            ItemStack stack = skin.createItemStack();
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Msg.parse("<gray>" + skin.descriptionSmallCaps() + "</gray>"));
                lore.add(Msg.parse("<gray>ᴇʟᴏ ʀᴇǫᴜɪꜱ:</gray> <gold>" + skin.unlockElo() + "</gold>"));
                lore.add(Msg.parse("<gray>ᴍᴏᴅᴇʟᴇ:</gray> <white>ᴄᴜʙᴇ 3ᴅ ᴍɪɴɪ (0.40x)</white>"));
                lore.add(Msg.parse(""));
                if (selected) {
                    lore.add(Msg.parse("<green><bold>✔ ᴇǫᴜɪᴘᴇ ᴀᴄᴛᴜᴇʟʟᴇᴍᴇɴᴛ</bold></green>"));
                } else if (unlocked) {
                    lore.add(Msg.parse("<yellow>▸ ᴄʟɪǫᴜᴇʀ ᴘᴏᴜʀ ᴇǫᴜɪᴘᴇʀ</yellow>"));
                } else {
                    lore.add(Msg.parse("<red>✖ ᴍᴏɴᴛᴇᴢ ᴇɴ ᴇʟᴏ ᴘᴏᴜʀ ᴅᴇʙʟᴏǫᴜᴇʀ</red>"));
                }
                meta.lore(lore);
                stack.setItemMeta(meta);
            }
            inv.setItem(skinSlots[i], stack);
        }

        // Row 4: 5 Articulated 3D Slam Dunk Styles!
        DunkStyle[] dunks = DunkStyle.values();
        int[] dunkSlots = {28, 29, 31, 33, 34};
        for (int i = 0; i < dunks.length && i < dunkSlots.length; i++) {
            DunkStyle d = dunks[i];
            boolean selected = profile.selectedDunkStyle() == d;
            boolean unlocked = profile.elo() >= d.unlockElo() || player.hasPermission("bosketball.admin");
            inv.setItem(dunkSlots[i], customIcon(
                selected ? "icon_star_shield" : "icon_dunk",
                "<gold><bold>🔥 " + d.displayName() + "</bold></gold>",
                List.of(
                    "<gray>ᴇʟᴏ ʀᴇǫᴜɪꜱ:</gray> <yellow>" + d.unlockElo() + "</yellow>",
                    "",
                    selected ? "<green><bold>✔ ᴅᴜɴᴋ ᴇǫᴜɪᴘᴇ</bold></green>"
                        : (unlocked ? "<yellow>▸ ᴄʟɪǫᴜᴇʀ ᴘᴏᴜʀ ᴇǫᴜɪᴘᴇʀ</yellow>" : "<red>✖ ᴠᴇʀʀᴏᴜɪʟʟᴇ</red>")
                )
            ));
        }

        inv.setItem(40, backButton());
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.3f);
    }

    // -------------------------------------------------------------------------
    // 4. 54-TAG GLOSSY VAULT WITH 2-PAGE NAVIGATION & MULTI-STACKING UP TO 4 TAGS (image-5.png!)
    // -------------------------------------------------------------------------
    public void openTagsVault(Player player) {
        openTagsVault(player, 0);
    }

    public void openTagsVault(Player player, int requestedPage) {
        ChatTag[] tags = ChatTag.values();
        int perPage = 36;
        int totalPages = Math.max(1, (tags.length + perPage - 1) / perPage);
        int page = Math.max(0, Math.min(totalPages - 1, requestedPage));

        BosketHolder holder = new BosketHolder(MenuType.TAGS_VAULT, page);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            guiTitle(Glyphs.ICON_CROWN, "<gradient:#FFD43B:#FF8A28><bold>54 ᴛᴀɢꜱ ɢʟᴏꜱꜱʏ • ᴘ." + (page + 1) + "/" + totalPages + "</bold></gradient>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        int equippedCount = profile.equippedTags().size();
        int startIdx = page * perPage;

        // Rows 0..3 (Slots 0..35 = 36 Glossy Beveled Tags per page!)
        for (int slot = 0; slot < perPage && (startIdx + slot) < tags.length; slot++) {
            ChatTag tag = tags[startIdx + slot];
            boolean equipped = profile.isTagEquipped(tag);
            int orderIdx = profile.equippedTags().indexOf(tag) + 1;
            boolean unlocked = tag.isUnlocked(profile) || player.hasPermission("bosketball.tags.all");

            String status = equipped
                ? "<green><bold>✔ ᴇǫᴜɪᴘᴇ [" + orderIdx + "/" + PlayerProfile.MAX_EQUIPPED_TAGS + "] (ᴄʟɪᴄ = ʀᴇᴛɪʀᴇʀ)</bold></green>"
                : (unlocked
                    ? "<yellow>▸ ᴄʟɪᴄ-ɢᴀᴜᴄʜᴇ: ᴀᴊᴏᴜᴛᴇʀ (" + equippedCount + "/4) • ᴅʀᴏɪᴛ: ᴜɴɪǫᴜᴇ</yellow>"
                    : "<red>✖ ʀᴇǫᴜɪꜱ: " + tag.requiredRank().name() + "</red>");

            inv.setItem(slot, customIcon(
                equipped ? "icon_star_shield" : "icon_tags",
                "<!shadow><white>" + tag.glyph() + "</white></!shadow> <gold><bold>" + tag.displayName() + "</bold></gold>",
                List.of(
                    "<gray>" + tag.loreHint() + "</gray>",
                    "<gray>ᴀᴘᴇʀᴄᴜ ᴄʜᴀᴛ:</gray> <!shadow><white>" + tag.glyph() + "</white></!shadow> <white>" + player.getName() + "</white>",
                    "",
                    status
                )
            ));
        }

        if (page > 0) {
            inv.setItem(45, customIcon(
                "icon_prev",
                "<yellow><bold>◂ ᴘᴀɢᴇ ᴘʀᴇᴄᴇᴅᴇɴᴛᴇ (" + page + "/" + totalPages + ")</bold></yellow>",
                List.of("<gray>ᴠᴏɪʀ ʟᴇꜱ ᴛᴀɢꜱ 1 ᴀ 36</gray>")
            ));
        }
        inv.setItem(48, customIcon(
            "icon_leave",
            "<red><bold>ʀᴇᴛɪʀᴇʀ ᴛᴏᴜꜱ ᴍᴇꜱ ᴛᴀɢꜱ</bold></red>",
            List.of("<gray>ɴ'ᴀꜰꜰɪᴄʜᴇ ǫᴜᴇ ᴠᴏꜱ ʙᴀᴅɢᴇꜱ #1-#3</gray>")
        ));
        inv.setItem(49, backButton());
        if (page + 1 < totalPages) {
            inv.setItem(53, customIcon(
                "icon_next",
                "<yellow><bold>ᴘᴀɢᴇ ꜱᴜɪᴠᴀɴᴛᴇ (" + (page + 2) + "/" + totalPages + ") ▸</bold></yellow>",
                List.of("<gray>ᴠᴏɪʀ ʟᴇꜱ ᴛᴀɢꜱ 37 ᴀ " + tags.length + "</gray>")
            ));
        }

        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.28f);
    }

    // -------------------------------------------------------------------------
    // 5. 7-CATEGORY LEADERBOARDS
    // -------------------------------------------------------------------------
    public void openLeaderboards(Player player, LeaderboardCategory category) {
        BosketHolder holder = new BosketHolder(MenuType.LEADERBOARDS, category);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            guiTitle(Glyphs.ICON_TROPHY, "<gradient:#FFD200:#F7971E><bold>ᴛᴏᴘ 10 • " + category.label() + "</bold></gradient>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        LeaderboardCategory[] cats = LeaderboardCategory.values();
        String[] catIcons = {"icon_podium", "icon_arena", "icon_star_shield", "icon_pass", "icon_dunk", "icon_tags", "icon_defense"};
        int[] catSlots = {1, 2, 3, 4, 5, 6, 7};
        for (int i = 0; i < cats.length && i < catSlots.length; i++) {
            LeaderboardCategory c = cats[i];
            boolean active = c == category;
            inv.setItem(catSlots[i], customIcon(
                catIcons[i],
                (active ? "<gold><bold>✦ " : "<gray>") + c.label() + (active ? " ✦</bold></gold>" : "</gray>"),
                List.of(active ? "<green>✔ ᴄᴀᴛᴇɢᴏʀɪᴇ ᴀᴄᴛɪᴠᴇ</green>" : "<yellow>▸ ᴀꜰꜰɪᴄʜᴇʀ</yellow>")
            ));
        }

        List<PlayerProfile> top = plugin.statsManager().topBy(category, 10);
        int[] podiumSlots = {13, 21, 23, 29, 30, 31, 32, 33, 39, 41};
        for (int i = 0; i < top.size() && i < podiumSlots.length; i++) {
            inv.setItem(podiumSlots[i], buildLeaderboardSkull(i + 1, top.get(i), category));
        }

        inv.setItem(49, backButton());
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.15f);
    }

    // -------------------------------------------------------------------------
    // 6. MATCH HISTORY
    // -------------------------------------------------------------------------
    public void openMatchHistory(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.MATCH_HISTORY);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            guiTitle(Glyphs.ICON_STAR, "<gradient:#38BDF8:#818CF8><bold>ʜɪꜱᴛᴏʀɪǫᴜᴇ ᴍᴀᴛᴄʜꜱ</bold></gradient>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        List<MatchHistoryEntry> history = profile.matchHistory();
        if (history.isEmpty()) {
            inv.setItem(22, customIcon(
                "icon_history",
                "<yellow><bold>ᴀᴜᴄᴜɴ ᴍᴀᴛᴄʜ ᴇɴʀᴇɢɪꜱᴛʀᴇ</bold></yellow>",
                List.of("<gray>ᴛᴇʀᴍɪɴᴇᴢ ᴜɴ ᴍᴀᴛᴄʜ 1ᴠ1..5ᴠ5 ᴘᴏᴜʀ</gray>", "<gray>ᴠᴏɪʀ ᴠᴏꜱ ꜱᴛᴀᴛꜱ ᴅᴇᴛᴀɪʟʟᴇᴇꜱ ɪᴄɪ !</gray>")
            ));
        } else {
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 31};
            for (int i = 0; i < history.size() && i < slots.length; i++) {
                MatchHistoryEntry h = history.get(i);
                String delta = h.eloDelta() >= 0 ? "<green>+" + h.eloDelta() + " ELO</green>" : "<red>" + h.eloDelta() + " ELO</red>";
                inv.setItem(slots[i], customIcon(
                    h.won() ? "icon_podium" : "icon_history",
                    (h.won() ? "<#52FFB8><bold>✔ ᴠɪᴄᴛᴏɪʀᴇ" : "<#FF6B6B><bold>✖ ᴅᴇꜰᴀɪᴛᴇ") + " • " + h.modeKey() + "</bold>",
                    List.of(
                        "<gray>ᴅᴀᴛᴇ:</gray> <white>" + h.formattedDate() + "</white>",
                        "<#FF5C4D>ɪɢɴɪꜱ " + h.ignisScore() + "</#FF5C4D> <gray>—</gray> <#3DB8FF>" + h.frostScore() + " ꜰʀᴏꜱᴛ</#3DB8FF>",
                        "<gray>ᴛᴇꜱ ꜱᴛᴀᴛꜱ:</gray> <yellow>" + h.points() + " PTS</yellow> <dark_gray>•</dark_gray> <aqua>" + h.assists() + " AST</aqua> <dark_gray>•</dark_gray> <green>" + h.rebounds() + " REB</green>",
                        "<gray>ᴄʟᴀꜱꜱᴇᴍᴇɴᴛ:</gray> " + delta
                    )
                ));
            }
        }

        inv.setItem(49, backButton());
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
    }

    // -------------------------------------------------------------------------
    // 7. FRIENDS & PARTY (4-Player Squad)
    // -------------------------------------------------------------------------
    public void openFriendsAndParty(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.FRIENDS_PARTY);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            guiTitle(Glyphs.ICON_SHIELD, "<gradient:#34D399:#10B981><bold>ᴀᴍɪꜱ & ɢʀᴏᴜᴘᴇ</bold></gradient>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        SocialPartyManager.Party party = plugin.socialManager().getParty(player.getUniqueId());
        int partySize = plugin.socialManager().partySizeOrOne(player.getUniqueId());
        inv.setItem(4, customIcon(
            "icon_friends",
            "<#52FFB8><bold>ɢʀᴏᴜᴘᴇ ᴀᴄᴛᴜᴇʟ (" + partySize + "/4)</bold></#52FFB8>",
            List.of(
                "<gray>ᴄʟɪǫᴜᴇᴢ ꜱᴜʀ ᴜɴ ᴊᴏᴜᴇᴜʀ ᴇɴ ʟɪɢɴᴇ</gray>",
                "<gray>ᴄɪ-ᴅᴇꜱꜱᴏᴜꜱ ᴘᴏᴜʀ ʟ'ɪɴᴠɪᴛᴇʀ !</gray>"
            )
        ));

        List<Player> others = Bukkit.getOnlinePlayers().stream()
            .<Player>map(p -> p)
            .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
            .limit(14)
            .toList();
        int[] playerSlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        PlayerProfile myProf = plugin.statsManager().getOrCreate(player);

        for (int i = 0; i < others.size() && i < playerSlots.length; i++) {
            Player other = others.get(i);
            boolean isFriend = myProf.isFriend(other.getUniqueId());
            boolean inMyParty = party != null && party.members().contains(other.getUniqueId());
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(other);
                meta.displayName(Msg.parse("<aqua><bold>" + other.getName() + "</bold></aqua>"));
                meta.lore(List.of(
                    Msg.parse("<gray>ᴀᴍɪ:</gray> " + (isFriend ? "<green>✔ ᴏᴜɪ</green>" : "<gray>ɴᴏɴ</gray>")),
                    Msg.parse("<gray>ɢʀᴏᴜᴘᴇ:</gray> " + (inMyParty ? "<#52FFB8>✔ ᴅᴀɴꜱ ᴛᴏɴ ɢʀᴏᴜᴘᴇ</#52FFB8>" : "<gray>ʜᴏʀꜱ ɢʀᴏᴜᴘᴇ</gray>")),
                    Msg.parse(""),
                    Msg.parse("<yellow>▸ ᴄʟɪᴄ ɢᴀᴜᴄʜᴇ: ɪɴᴠɪᴛᴇʀ ɢʀᴏᴜᴘᴇ</yellow>"),
                    Msg.parse("<aqua>▸ ᴄʟɪᴄ ᴅʀᴏɪᴛ: ᴀᴊᴏᴜᴛᴇʀ ᴀᴍɪ</aqua>")
                ));
                skull.setItemMeta(meta);
            }
            inv.setItem(playerSlots[i], skull);
        }

        if (party != null) {
            inv.setItem(48, customIcon(
                "icon_leave",
                "<red><bold>ǫᴜɪᴛᴛᴇʀ ʟᴇ ɢʀᴏᴜᴘᴇ</bold></red>",
                List.of("<gray>ᴅɪꜱꜱᴏᴜᴅʀᴇ ᴏᴜ ǫᴜɪᴛᴛᴇʀ ʟ'ᴇꜱᴄᴏᴜᴀᴅᴇ</gray>")
            ));
        }
        inv.setItem(49, backButton());
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
    }

    // -------------------------------------------------------------------------
    // 8. PLAYER SETTINGS
    // -------------------------------------------------------------------------
    public void openSettings(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.SETTINGS);
        Inventory inv = Bukkit.createInventory(
            holder,
            45,
            guiTitle(Glyphs.ICON_BOLT, "<gradient:#CBD5E1:#94A3B8><bold>ᴘᴀʀᴀᴍᴇᴛʀᴇꜱ</bold></gradient>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        PlayerSettings st = profile.settings();

        inv.setItem(19, settingToggle("icon_dunk", "ᴀɴɪᴍᴀᴛɪᴏɴꜱ ᴊᴏᴜᴇᴜʀ 3ᴅ", "ᴍᴀʀᴄʜᴇ, ꜱᴀᴜᴛ, ᴅʀɪʙʙʟᴇ & ᴛɪʀ 6-ᴏꜱ", st.customAnimations()));
        inv.setItem(20, settingToggle("icon_arena", "ᴄᴇʀᴄʟᴇꜱ & ɢʟᴏᴡ ᴅ'ᴇǫᴜɪᴘᴇ", "ᴀᴜʀᴀ ᴀᴜ ꜱᴏʟ + ᴄᴏɴᴛᴏᴜʀ ʟᴜᴍɪɴᴇᴜx ɪɢɴɪꜱ/ꜰʀᴏꜱᴛ", st.floorAuraRings()));
        inv.setItem(21, settingToggle("icon_star_shield", "ꜱᴏɴꜱ ᴄᴜꜱᴛᴏᴍ ᴅᴜ ᴘᴀᴄᴋ", "ꜱᴡɪꜱʜ, ʙᴜᴢᴢᴇʀ, ꜱɪꜰꜰʟᴇᴛ & ᴅʀɪʙʙʟᴇ", st.customSounds()));
        inv.setItem(22, settingToggle("icon_defense", "ᴄᴏʀᴘꜱ 3ᴅ ᴇɴ ᴠᴜᴇ ꜰ5", "ᴀꜰꜰɪᴄʜᴇʀ ᴠᴏᴛʀᴇ ᴘʀᴏᴘʀᴇ ᴍᴀɪʟʟᴏᴛ ᴇᴛ ᴍᴇᴍʙʀᴇꜱ ᴇɴ ꜰ5", st.selfBodyRigInF5()));
        inv.setItem(23, settingToggle("icon_portal", "ᴄᴀᴍᴇʀᴀ ᴄɪɴᴇᴍᴀᴛɪǫᴜᴇ", "ᴇꜰꜰᴇᴛꜱ ᴠɪꜱᴜᴇʟꜱ ꜱᴜʀ ᴘᴀɴɪᴇʀꜱ ᴄʟᴜᴛᴄʜ", st.cinematicScoreCam()));
        inv.setItem(24, settingToggle("icon_friends", "ɪɴᴠɪᴛᴀᴛɪᴏɴꜱ ᴅᴇ ɢʀᴏᴜᴘᴇ", "ᴀᴜᴛᴏʀɪꜱᴇʀ ʟᴇꜱ ɪɴᴠɪᴛᴀᴛɪᴏɴꜱ /bb party", st.partyInvites()));

        inv.setItem(40, backButton());
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.22f);
    }

    private ItemStack settingToggle(String iconName, String title, String desc, boolean enabled) {
        return customIcon(
            iconName,
            "<white><bold>" + title + "</bold></white>",
            List.of(
                "<gray>" + desc + "</gray>",
                "",
                enabled ? "<#52FFB8><bold>● ᴀᴄᴛɪᴠᴇ</bold></#52FFB8>" : "<#FF6B6B><bold>○ ᴅᴇꜱᴀᴄᴛɪᴠᴇ</bold></#FF6B6B>",
                "<yellow>▸ ᴄʟɪǫᴜᴇʀ ᴘᴏᴜʀ ʙᴀꜱᴄᴜʟᴇʀ</yellow>"
            )
        );
    }

    // -------------------------------------------------------------------------
    // Click Handling
    // -------------------------------------------------------------------------
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof BosketHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != holder.getInventory()) {
            return;
        }
        int slot = event.getSlot();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

        switch (holder.type()) {
            case MAIN_HUB -> {
                switch (slot) {
                    case 13 -> {
                        player.closeInventory();
                        plugin.lockerRoom3dManager().open(player, LockerRoom3dMenuManager.TopTab.PLAY);
                    }
                    case 20 -> openPlayModes(player);
                    case 21 -> {
                        player.closeInventory();
                        plugin.gameManager().startPractice(player);
                    }
                    case 22 -> openCosmetics(player);
                    case 23 -> openTagsVault(player);
                    case 24 -> openLeaderboards(player, LeaderboardCategory.ELO);
                    case 29 -> openMatchHistory(player);
                    case 33 -> openFriendsAndParty(player);
                    case 39 -> {
                        player.closeInventory();
                        plugin.centerTabManager().toggle(player);
                    }
                    case 40 -> openSettings(player);
                    case 41 -> {
                        if (plugin.gameManager().isInMatch(player.getUniqueId())) {
                            player.closeInventory();
                            plugin.gameManager().leaveAll(player, true);
                        }
                    }
                }
            }
            case PLAY_MODES -> {
                switch (slot) {
                    case 19 -> joinModeFromGui(player, GameModeType.DUEL_1V1);
                    case 21 -> joinModeFromGui(player, GameModeType.STREET_2V2);
                    case 23 -> joinModeFromGui(player, GameModeType.PRO_3V3);
                    case 25 -> joinModeFromGui(player, GameModeType.FULL_5V5);
                    case 29 -> joinModeFromGui(player, GameModeType.THREE_POINT_CONTEST);
                    case 30 -> {
                        player.closeInventory();
                        plugin.gameManager().spectateFirstActive(player);
                    }
                    case 32 -> {
                        player.closeInventory();
                        plugin.lockerRoom3dManager().open(player, LockerRoom3dMenuManager.TopTab.PLAY);
                    }
                    case 40 -> openMainHub(player);
                }
            }
            case COSMETICS -> {
                if (slot == 40) {
                    openMainHub(player);
                    return;
                }
                int[] skinSlots = {9, 10, 11, 12, 14, 15, 16, 17};
                BallSkin[] skins = BallSkin.values();
                for (int i = 0; i < skinSlots.length && i < skins.length; i++) {
                    if (slot == skinSlots[i]) {
                        BallSkin chosen = skins[i];
                        boolean unlocked = profile.elo() >= chosen.unlockElo() || player.hasPermission("bosketball.admin");
                        if (!unlocked) {
                            Msg.send(player, Msg.error("ELO requis : " + chosen.unlockElo()));
                            return;
                        }
                        profile.setSelectedBallSkin(chosen);
                        plugin.statsManager().saveProfileAsync(profile);
                        Msg.send(player, Msg.success("Ballon Carré 3D équipé : " + chosen.displayNameMm()));
                        openCosmetics(player);
                        return;
                    }
                }
                int[] dunkSlots = {28, 29, 31, 33, 34};
                DunkStyle[] dunks = DunkStyle.values();
                for (int i = 0; i < dunkSlots.length && i < dunks.length; i++) {
                    if (slot == dunkSlots[i]) {
                        DunkStyle chosen = dunks[i];
                        boolean unlocked = profile.elo() >= chosen.unlockElo() || player.hasPermission("bosketball.admin");
                        if (!unlocked) {
                            Msg.send(player, Msg.error("ELO requis : " + chosen.unlockElo()));
                            return;
                        }
                        profile.setSelectedDunkStyle(chosen);
                        plugin.statsManager().saveProfileAsync(profile);
                        Msg.send(player, Msg.success("Style de Dunk équipé : <gold>" + chosen.displayName() + "</gold>"));
                        openCosmetics(player);
                        return;
                    }
                }
            }
            case TAGS_VAULT -> {
                int curPage = holder.page();
                if (slot == 49) {
                    openMainHub(player);
                    return;
                }
                if (slot == 45 && curPage > 0) {
                    openTagsVault(player, curPage - 1);
                    return;
                }
                if (slot == 53) {
                    openTagsVault(player, curPage + 1);
                    return;
                }
                if (slot == 48) {
                    profile.setSelectedTag(null);
                    plugin.statsManager().saveProfileAsync(profile);
                    Msg.send(player, Msg.info("Tous les tags de chat ont été retirés."));
                    openTagsVault(player, curPage);
                    return;
                }
                ChatTag[] tags = ChatTag.values();
                int tagIndex = curPage * 36 + slot;
                if (slot >= 0 && slot < 36 && tagIndex < tags.length) {
                    ChatTag chosen = tags[tagIndex];
                    if (!chosen.isUnlocked(profile) && !player.hasPermission("bosketball.tags.all")) {
                        Msg.send(player, Msg.error("Rang requis : " + chosen.requiredRank().name()));
                        return;
                    }
                    if (event.isRightClick()) {
                        profile.setSelectedTag(chosen);
                        Msg.send(player, Msg.success("Tag unique équipé : <!shadow><white>" + chosen.glyph() + "</white></!shadow> <gold>" + chosen.displayName() + "</gold>"));
                    } else {
                        boolean added = profile.toggleEquippedTag(chosen);
                        if (added) {
                            Msg.send(player, Msg.success("Tag ajouté (" + profile.equippedTags().size() + "/4) : <!shadow><white>" + chosen.glyph() + "</white></!shadow> <gold>" + chosen.displayName() + "</gold>"));
                        } else {
                            Msg.send(player, Msg.info("Tag retiré : <yellow>" + chosen.displayName() + "</yellow>"));
                        }
                    }
                    plugin.statsManager().saveProfileAsync(profile);
                    openTagsVault(player, curPage);
                }
            }
            case LEADERBOARDS -> {
                if (slot == 49) {
                    openMainHub(player);
                    return;
                }
                LeaderboardCategory[] cats = LeaderboardCategory.values();
                int[] catSlots = {1, 2, 3, 4, 5, 6, 7};
                for (int i = 0; i < catSlots.length && i < cats.length; i++) {
                    if (slot == catSlots[i]) {
                        openLeaderboards(player, cats[i]);
                        return;
                    }
                }
            }
            case MATCH_HISTORY -> {
                if (slot == 49) {
                    openMainHub(player);
                }
            }
            case FRIENDS_PARTY -> {
                if (slot == 49) {
                    openMainHub(player);
                    return;
                }
                if (slot == 48) {
                    plugin.socialManager().leaveParty(player);
                    openFriendsAndParty(player);
                    return;
                }
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() instanceof SkullMeta sm) {
                    OfflinePlayer owner = sm.getOwningPlayer();
                    if (owner != null && owner.getUniqueId() != null) {
                        Player onlineTarget = Bukkit.getPlayer(owner.getUniqueId());
                        if (onlineTarget != null) {
                            if (event.isRightClick()) {
                                plugin.socialManager().sendOrAcceptFriendRequest(player, onlineTarget);
                            } else {
                                plugin.socialManager().inviteToParty(player, onlineTarget);
                            }
                            openFriendsAndParty(player);
                        }
                    }
                }
            }
            case SETTINGS -> {
                if (slot == 40) {
                    openMainHub(player);
                    return;
                }
                PlayerSettings st = profile.settings();
                switch (slot) {
                    case 19 -> st.setCustomAnimations(!st.customAnimations());
                    case 20 -> st.setFloorAuraRings(!st.floorAuraRings());
                    case 21 -> st.setCustomSounds(!st.customSounds());
                    case 22 -> st.setSelfBodyRigInF5(!st.selfBodyRigInF5());
                    case 23 -> st.setCinematicScoreCam(!st.cinematicScoreCam());
                    case 24 -> st.setPartyInvites(!st.partyInvites());
                    default -> {
                        return;
                    }
                }
                plugin.statsManager().saveProfileAsync(profile);
                openSettings(player);
            }
        }
    }

    private void joinModeFromGui(Player player, GameModeType mode) {
        player.closeInventory();
        plugin.gameManager().joinQueue(player, mode);
    }

    private ItemStack backButton() {
        return customIcon("icon_back", "<yellow><bold>◂ ʀᴇᴛᴏᴜʀ ᴀᴜ ᴍᴇɴᴜ</bold></yellow>", List.of("<gray>ʀᴇᴠᴇɴɪʀ ᴀᴜ ʜᴜʙ ʙᴏꜱᴋᴇᴛʙᴀʟʟ</gray>"));
    }

    private void fillBackdrop(Inventory inv) {
        ItemStack invisible = new ItemStack(Material.PAPER);
        ItemMeta meta = invisible.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey("bosketball", "ui_invisible"));
            meta.setHideTooltip(true);
            invisible.setItemMeta(meta);
        }
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            inv.setItem(i, invisible);
        }
    }

    public static ItemStack customIcon(String modelName, String nameMm, List<String> loreMm) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey("bosketball", modelName));
            meta.displayName(Msg.parse(nameMm));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>(loreMm.size());
            for (String line : loreMm) {
                lore.add(Msg.parse(line));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildPlayerSkullCard(Player player, PlayerProfile p) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.displayName(Msg.parse("<gradient:#FF8A28:#FFE259><bold>" + player.getName() + "</bold></gradient> <!shadow><white>" + p.effectiveRank().pillGlyph() + "</white></!shadow>"));
            meta.lore(List.of(
                Msg.parse("<gray>ᴇʟᴏ:</gray> <gold>" + p.elo() + "</gold> <dark_gray>•</dark_gray> <gray>ᴠɪᴄᴛᴏɪʀᴇꜱ:</gray> <green>" + p.wins() + "</green> <gray>(" + String.format("%.1f%%", p.winRatePercent()) + ")</gray>"),
                Msg.parse("<gray>ᴘᴏɪɴᴛꜱ:</gray> <yellow>" + p.points() + "</yellow> <dark_gray>•</dark_gray> <gray>3-ᴘᴛꜱ:</gray> <aqua>" + p.threePointers() + "</aqua> <dark_gray>•</dark_gray> <gray>ᴅᴜɴᴋꜱ:</gray> <red>" + p.dunks() + "</red>"),
                Msg.parse("<gray>ᴘᴀꜱꜱᴇꜱ:</gray> <green>" + p.assists() + "</green> <dark_gray>•</dark_gray> <gray>ɪɴᴛᴇʀᴄᴇᴘᴛɪᴏɴꜱ:</gray> <light_purple>" + p.steals() + "</light_purple>"),
                Msg.parse("<gray>ᴛᴇᴍᴘꜱ ᴅᴇ ᴊᴇᴜ:</gray> <white>" + p.formattedPlayTimeFr() + "</white>")
            ));
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private ItemStack buildLeaderboardSkull(int place, PlayerProfile p, LeaderboardCategory cat) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            OfflinePlayer off = Bukkit.getOfflinePlayer(p.uuid());
            meta.setOwningPlayer(off);
            String medal = switch (place) {
                case 1 -> "<#FFD700><bold>🥇 #1 ";
                case 2 -> "<#E0E8F0><bold>🥈 #2 ";
                case 3 -> "<#CD7F32><bold>🥉 #3 ";
                default -> "<gray><bold>#" + place + " ";
            };
            meta.displayName(Msg.parse(medal + p.lastKnownName() + "</bold> <!shadow><white>" + p.effectiveRank().pillGlyph() + "</white></!shadow>"));
            meta.lore(List.of(
                Msg.parse("<gray>" + cat.label() + ":</gray> <gold><bold>" + cat.extract(p) + "</bold></gold>"),
                Msg.parse("<gray>ᴇʟᴏ:</gray> <yellow>" + p.elo() + "</yellow> <dark_gray>•</dark_gray> <gray>ᴠɪᴄᴛᴏɪʀᴇꜱ:</gray> <green>" + p.wins() + "</green>")
            ));
            skull.setItemMeta(meta);
        }
        return skull;
    }
}
