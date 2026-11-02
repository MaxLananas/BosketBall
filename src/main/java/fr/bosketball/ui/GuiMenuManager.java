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
import fr.bosketball.stats.StatsManager.LeaderboardCategory;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Clean, Idiomatic English Server GUI & Coins Shop Manager:
 *   - Main Menu, Game Modes, Tag Shop, Basketball & Dunk Shop, Leaderboards, Match History, Party & Settings.
 *   - Full Coin Economy integration (purchase & equip Tags, Basketballs & Dunk Styles).
 *   - Strict anti-duplication inventory click/drag protection.
 */
public final class GuiMenuManager implements Listener {

    public enum MenuType {
        MAIN_HUB,
        PLAY_MODES,
        SHOP,
        COSMETICS,
        TAGS_VAULT,
        LEADERBOARDS,
        MATCH_HISTORY,
        FRIENDS_PARTY,
        FRIENDS_MENU,
        ADD_FRIEND_ONLINE,
        PARTY_INVITE_ONLINE,
        PARTY_MENU,
        RANKS_GUIDE,
        MAP_STUDIO,
        SETTINGS
    }

    public enum ShopCategory {
        ALL("Boutique Shelves"),
        BALLS("Basketballs"),
        DUNKS("Signature Dunks"),
        TAGS("Chat Tags");

        private final String label;

        ShopCategory(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record ShopItemEntry(
        ShopCategory itemType,
        BallSkin ballSkin,
        DunkStyle dunkStyle,
        ChatTag chatTag,
        int priceCoins
    ) {
    }

    public static final class BosketHolder implements InventoryHolder {
        private final MenuType type;
        private final LeaderboardCategory leaderboardCategory;
        private final ShopCategory shopCategory;
        private final int page;
        private final Map<Integer, ShopItemEntry> shopSlotMap = new HashMap<>();
        private Inventory inventory;

        public BosketHolder(MenuType type) {
            this(type, LeaderboardCategory.ELO, ShopCategory.ALL, 0);
        }

        public BosketHolder(MenuType type, LeaderboardCategory leaderboardCategory) {
            this(type, leaderboardCategory, ShopCategory.ALL, 0);
        }

        public BosketHolder(MenuType type, int page) {
            this(type, LeaderboardCategory.ELO, ShopCategory.ALL, page);
        }

        public BosketHolder(MenuType type, ShopCategory shopCategory, int page) {
            this(type, LeaderboardCategory.ELO, shopCategory, page);
        }

        public BosketHolder(MenuType type, LeaderboardCategory leaderboardCategory, int page) {
            this(type, leaderboardCategory, ShopCategory.ALL, page);
        }

        public BosketHolder(MenuType type, LeaderboardCategory leaderboardCategory, ShopCategory shopCategory, int page) {
            this.type = type;
            this.leaderboardCategory = leaderboardCategory;
            this.shopCategory = shopCategory;
            this.page = page;
        }

        public MenuType type() {
            return type;
        }

        public LeaderboardCategory leaderboardCategory() {
            return leaderboardCategory;
        }

        public ShopCategory shopCategory() {
            return shopCategory;
        }

        public int page() {
            return page;
        }

        public Map<Integer, ShopItemEntry> shopSlotMap() {
            return shopSlotMap;
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

    private static net.kyori.adventure.text.Component guiTitle(char iconGlyph, String titleMm) {
        return Msg.parse("<!shadow><white>" + iconGlyph + "</white></!shadow> " + titleMm);
    }

    private static String coinStr(int amount) {
        return "<!shadow><white>" + Glyphs.CHAT_ICON_COIN + "</white></!shadow> <#FDE047><bold>" + amount + " Coins</bold></#FDE047>";
    }

    // -------------------------------------------------------------------------
    // 1. MAIN MENU (/bb)
    // -------------------------------------------------------------------------
    public void openMainHub(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.MAIN_HUB);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            guiTitle(Glyphs.ICON_BALL, "<#FF8C1A><bold>BosketBall</bold></#FF8C1A>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        int groupSize = plugin.socialManager().partySizeOrOne(player.getUniqueId());
        int onlineFriends = plugin.socialManager().countOnlineFriends(player.getUniqueId());

        // Top Center (Slot 13): Locker Room
        inv.setItem(13, customIcon(
            "icon_portal",
            "<#38BDF8><bold>Locker Room</bold></#38BDF8>",
            List.of(
                "<gray>Preview your squad, player showcase</gray>",
                "<gray>and equipped cosmetics on stage.</gray>",
                "",
                "<yellow>▸ Click to enter</yellow>"
            )
        ));

        // Row 3: Play, Practice, Basketballs & Dunks, Tag Shop, Leaderboards
        inv.setItem(20, customIcon(
            "icon_arena",
            "<#FF8C1A><bold>Play</bold></#FF8C1A>",
            List.of(
                "<gray>Compete in 1v1, 2v2, 3v3 & 5v5</gray>",
                "<gray>Queue:</gray> <#4ADE80>" + plugin.gameManager().queuedCount() + "</#4ADE80> <dark_gray>•</dark_gray> <gray>In Match:</gray> <#FBBF24>" + plugin.gameManager().activePlayerCount() + "</#FBBF24>",
                "",
                "<yellow>▸ Browse game modes</yellow>"
            )
        ));

        ItemStack practiceBall = profile.selectedBallSkin().createItemStack();
        ItemMeta pm = practiceBall.getItemMeta();
        if (pm != null) {
            pm.displayName(Msg.parse("<#38BDF8><bold>Practice Court</bold></#38BDF8>"));
            pm.lore(List.of(
                Msg.parse("<gray>Warm up with the 24s shot clock</gray>"),
                Msg.parse("<gray>and spar against the AI Defender.</gray>"),
                Msg.parse(""),
                Msg.parse("<aqua>▸ Step onto the court</aqua>")
            ));
            practiceBall.setItemMeta(pm);
        }
        inv.setItem(21, practiceBall);

        int unownedTotal = countUnownedShopItems(profile);
        inv.setItem(22, customIcon(
            "icon_chest",
            "<#FBBF24><bold>Shop</bold></#FBBF24>",
            List.of(
                "<gray>Purchase unowned Basketballs, Dunks & Tags</gray>",
                "<gray>Balance:</gray> " + coinStr(profile.coins()),
                "<gray>Remaining to Unlock:</gray> <aqua>" + unownedTotal + " items</aqua>",
                "",
                "<yellow>▸ Open Shop</yellow>"
            )
        ));

        ChatTag activeTag = profile.selectedTag();
        String equippedTagStr = activeTag == null
            ? "<gray>None</gray>"
            : "<!shadow><white>" + activeTag.glyph() + "</white></!shadow> <white>" + activeTag.displayName() + "</white>";

        inv.setItem(23, customIcon(
            "icon_tags",
            "<#F472B6><bold>My Cosmetics & Tags</bold></#F472B6>",
            List.of(
                "<gray>Equip your unlocked Basketballs, Dunks & Tags</gray>",
                "<gray>Equipped Ball:</gray> " + profile.selectedBallSkin().displayNameMm(),
                "<gray>Active Tag:</gray> " + equippedTagStr,
                "",
                "<yellow>▸ Left-Click: Basketballs & Dunks</yellow>",
                "<aqua>▸ Right-Click: Chat Tags</aqua>"
            )
        ));

        inv.setItem(24, customIcon(
            "icon_podium",
            "<#FDE047><bold>Leaderboards</bold></#FDE047>",
            List.of(
                "<gray>Top 10 rankings across ELO, Coins,</gray>",
                "<gray>Wins, Points, Dunks & Assists.</gray>",
                "",
                "<yellow>▸ View Rankings</yellow>"
            )
        ));

        // Row 4: Match History, Career Profile Skull, Party & Friends
        inv.setItem(29, customIcon(
            "icon_history",
            "<#818CF8><bold>Match History</bold></#818CF8>",
            List.of(
                "<gray>Review your recent match results,</gray>",
                "<gray>box scores & ELO changes.</gray>",
                "<gray>Recorded Games:</gray> <white>" + profile.matchHistory().size() + "</white>",
                "",
                "<aqua>▸ Open History</aqua>"
            )
        ));

        inv.setItem(31, buildPlayerSkullCard(player, profile));

        SocialPartyManager.Party curParty = plugin.socialManager().getParty(player.getUniqueId());
        String teamLabel = (curParty != null && curParty.hasCustomTeamName())
            ? "<#FBBF24><bold>" + curParty.teamName() + "</bold></#FBBF24>"
            : "<gray>Not set (Click to name your team!)</gray>";
        int pendingReqs = plugin.socialManager().incomingFriendRequests(player.getUniqueId()).size();

        inv.setItem(33, customIcon(
            "icon_friends",
            "<#34D399><bold>Friends & Party (" + groupSize + "/4)</bold></#34D399>",
            List.of(
                "<gray>Friends Online:</gray> <#4ADE80>" + onlineFriends + "</#4ADE80><gray>/" + profile.friends().size() + "</gray>"
                    + (pendingReqs > 0 ? " <#FBBF24>(" + pendingReqs + " pending!)</#FBBF24>" : ""),
                "<gray>Party Team Name:</gray> " + teamLabel,
                "",
                "<green>▸ Left-Click: Friends Menu (/f)</green>",
                "<yellow>▸ Right-Click: Party & Team Name (/party)</yellow>"
            )
        ));

        // Row 5: Discord, Settings, Ranks & Controls Guide / Leave Match (+ OP Arena Studio)
        inv.setItem(39, customIcon(
            "icon_star_shield",
            "<#818CF8><bold>Official Discord</bold></#818CF8>",
            List.of(
                "<gray>Join our Discord community to find</gray>",
                "<gray>teammates and follow tournaments.</gray>",
                "",
                "<#818CF8>▸ Click for invite (/discord)</#818CF8>"
            )
        ));

        inv.setItem(40, customIcon(
            "icon_settings",
            "<#CBD5E1><bold>Settings</bold></#CBD5E1>",
            List.of(
                "<gray>Customize court visuals, sound effects,</gray>",
                "<gray>score cameras & party invites.</gray>",
                "",
                "<white>▸ Configure</white>"
            )
        ));

        if (plugin.gameManager().isInMatch(player.getUniqueId()) || plugin.gameManager().isQueued(player.getUniqueId())) {
            inv.setItem(41, customIcon(
                "icon_leave",
                "<#F87171><bold>Leave Match / Queue</bold></#F87171>",
                List.of("<gray>Return to the main lobby</gray>")
            ));
        } else {
            inv.setItem(41, customIcon(
                "icon_book",
                "<#FBBF24><bold>Ranks, Badges & Controls Guide</bold></#FBBF24>",
                List.of(
                    "<gray>Learn what</gray> <white>Rookie (1000 ELO)</white> <gray>→</gray> <#FBBF24>MVP (2200+)</#FBBF24> <gray>mean,</gray>",
                    "<gray>how</gray> <#FFD700>#1/#2/#3 ELO Badges</#FFD700> <gray>& Coins work,</gray>",
                    "<gray>and master</gray> <#4ADE80>Bow-Style Shooting</#4ADE80><gray>!</gray>",
                    "",
                    "<yellow>▸ Click to open full guide (/ranks)</yellow>"
                )
            ));
        }

        if (player.isOp()) {
            inv.setItem(44, customIcon(
                "court_wand",
                "<#38BDF8><bold>OP Arena Studio</bold></#38BDF8>",
                List.of(
                    "<gray>1-Click Arena Builder (Strictly OP only):</gray>",
                    "<gray>Auto-place Hoops, Backboards, Scoreboard</gray>",
                    "<gray>and White Court Lines in seconds!</gray>",
                    "",
                    "<aqua>▸ Open Arena Studio (/bb map)</aqua>"
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
            guiTitle(Glyphs.ICON_FIRE, "<#FF8C1A><bold>Game Modes</bold></#FF8C1A>")
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
        int winReward = plugin.settings().economy().winCoins();
        int lossPenalty = plugin.settings().economy().lossCoins();

        for (int i = 0; i < modes.length; i++) {
            GameModeType mode = modes[i];
            int q = plugin.gameManager().queuedCount(mode);
            boolean myQueue = plugin.gameManager().getQueuedMode(player.getUniqueId()) == mode;
            inv.setItem(slots[i], customIcon(
                modeIcons[i],
                "<#FF9E2C><bold>" + mode.smallCapsTitle() + "</bold></#FF9E2C>",
                List.of(
                    "<gray>Format:</gray> <white>" + mode.playersPerTeam() + "v" + mode.playersPerTeam() + "</white>",
                    "<gray>In Queue:</gray> <#4ADE80>" + q + "/" + mode.maxPlayers() + "</#4ADE80>",
                    "<gray>Stakes:</gray> <#4ADE80>+" + winReward + " Coins</#4ADE80> <gray>/</gray> <#F87171>-" + lossPenalty + " Coins</#F87171>",
                    "",
                    myQueue ? "<#4ADE80>✔ Currently Queued (Click to leave)</#4ADE80>" : "<yellow>▸ Click to join queue</yellow>"
                )
            ));
        }

        inv.setItem(29, customIcon(
            "icon_tags",
            "<#38BDF8><bold>3-Point Contest</bold></#38BDF8>",
            List.of(
                "<gray>5 Racks • 25 Balls • 60 Seconds</gray>",
                "<gray>Earn up to</gray> <#FDE047>+25 Coins</#FDE047>",
                "",
                "<yellow>▸ Start Contest</yellow>"
            )
        ));

        inv.setItem(31, customIcon(
            "icon_star_shield",
            "<#7DD3FC><bold>Spectate Match</bold></#7DD3FC>",
            List.of("<gray>Watch a live match in progress</gray>", "", "<aqua>▸ Spectate</aqua>")
        ));

        inv.setItem(33, customIcon(
            "icon_portal",
            "<#34D399><bold>Practice Court</bold></#34D399>",
            List.of("<gray>Warm up with the AI Defender</gray>", "", "<green>▸ Enter Court</green>")
        ));

        inv.setItem(40, backButton(player));

        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.25f);
    }

    // -------------------------------------------------------------------------
    // 2B. ORGANIZED BOUTIQUE SHOP (Structured Shelves + Category Tabs)
    // -------------------------------------------------------------------------
    public static List<ShopItemEntry> collectUnownedShopItems(PlayerProfile profile, ShopCategory filter) {
        List<ShopItemEntry> list = new ArrayList<>();
        if (profile == null) {
            return list;
        }
        ShopCategory cat = filter != null ? filter : ShopCategory.ALL;

        if (cat == ShopCategory.ALL || cat == ShopCategory.BALLS) {
            List<ShopItemEntry> balls = new ArrayList<>();
            for (BallSkin skin : BallSkin.values()) {
                if (!profile.hasUnlockedBall(skin) && skin.priceCoins() > 0) {
                    balls.add(new ShopItemEntry(ShopCategory.BALLS, skin, null, null, skin.priceCoins()));
                }
            }
            balls.sort(Comparator.comparingInt(ShopItemEntry::priceCoins));
            list.addAll(balls);
        }
        if (cat == ShopCategory.ALL || cat == ShopCategory.DUNKS) {
            List<ShopItemEntry> dunks = new ArrayList<>();
            for (DunkStyle dunk : DunkStyle.values()) {
                if (!profile.hasUnlockedDunk(dunk) && dunk.priceCoins() > 0) {
                    dunks.add(new ShopItemEntry(ShopCategory.DUNKS, null, dunk, null, dunk.priceCoins()));
                }
            }
            dunks.sort(Comparator.comparingInt(ShopItemEntry::priceCoins));
            list.addAll(dunks);
        }
        if (cat == ShopCategory.ALL || cat == ShopCategory.TAGS) {
            List<ShopItemEntry> tags = new ArrayList<>();
            for (ChatTag tag : ChatTag.values()) {
                if (!tag.isSpecialUnpurchasable() && !tag.isUnlocked(profile) && tag.priceCoins() > 0) {
                    tags.add(new ShopItemEntry(ShopCategory.TAGS, null, null, tag, tag.priceCoins()));
                }
            }
            tags.sort(Comparator.comparingInt(ShopItemEntry::priceCoins).thenComparing(e -> e.chatTag().displayName()));
            list.addAll(tags);
        }
        return list;
    }

    public static List<ChatTag> visibleVaultTags(Player player, PlayerProfile profile) {
        List<ChatTag> list = new ArrayList<>();
        String pName = player != null ? player.getName() : (profile != null ? profile.lastKnownName() : "");
        for (ChatTag tag : ChatTag.values()) {
            if (tag.isSpecialUnpurchasable()) {
                if (tag.canBeUsedBy(profile, pName, false)) {
                    list.add(tag);
                }
            } else {
                list.add(tag);
            }
        }
        return list;
    }

    public int countUnownedShopItems(PlayerProfile profile) {
        return collectUnownedShopItems(profile, ShopCategory.ALL).size();
    }

    private static final int[] SHOP_GRID_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private static final int[] SHELF_BALL_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] SHELF_DUNK_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int[] SHELF_TAG_SLOTS = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    public void openShop(Player player) {
        openShop(player, ShopCategory.ALL, 0);
    }

    public void openShop(Player player, ShopCategory category, int requestedPage) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        ShopCategory cat = category != null ? category : ShopCategory.ALL;

        List<ShopItemEntry> unownedBalls = collectUnownedShopItems(profile, ShopCategory.BALLS);
        List<ShopItemEntry> unownedDunks = collectUnownedShopItems(profile, ShopCategory.DUNKS);
        List<ShopItemEntry> unownedTags = collectUnownedShopItems(profile, ShopCategory.TAGS);
        int totalUnowned = unownedBalls.size() + unownedDunks.size() + unownedTags.size();

        int totalPages;
        List<ShopItemEntry> catEntries;
        if (cat == ShopCategory.ALL) {
            catEntries = unownedTags;
            totalPages = Math.max(1, (unownedTags.size() + SHELF_TAG_SLOTS.length - 1) / SHELF_TAG_SLOTS.length);
        } else {
            catEntries = switch (cat) {
                case BALLS -> unownedBalls;
                case DUNKS -> unownedDunks;
                case TAGS -> unownedTags;
                default -> collectUnownedShopItems(profile, cat);
            };
            totalPages = Math.max(1, (catEntries.size() + SHOP_GRID_SLOTS.length - 1) / SHOP_GRID_SLOTS.length);
        }
        int page = Math.max(0, Math.min(totalPages - 1, requestedPage));

        BosketHolder holder = new BosketHolder(MenuType.SHOP, cat, page);
        String headerTitle = cat == ShopCategory.ALL
            ? "<#FBBF24><bold>Boutique • Organized Shelves</bold></#FBBF24>"
            : "<#FBBF24><bold>Boutique • " + cat.label() + " (" + catEntries.size() + ")</bold></#FBBF24>";
        Inventory inv = Bukkit.createInventory(holder, 54, guiTitle(Glyphs.ICON_CROWN, headerTitle));
        holder.setInventory(inv);
        fillBackdrop(inv);

        // ── SYMMETRICAL TOP NAVIGATION BAR (Row 0) ──
        inv.setItem(0, customIcon(
            "icon_coin_stack",
            "<#FDE047><bold>Your Wallet</bold></#FDE047>",
            List.of(
                coinStr(profile.coins()),
                "",
                "<gray>How to earn Coins:</gray>",
                "<dark_gray>• +35 Win • +15 Played • +3/PT</dark_gray>",
                "<dark_gray>• +4/AST • +5 /gg • 3PT Contest</dark_gray>"
            )
        ));

        // Slots 2, 3, 4, 5 = Centered Category Tabs!
        inv.setItem(2, buildShopTabButton(
            cat == ShopCategory.ALL,
            "icon_shelves",
            "Boutique Shelves",
            totalUnowned,
            "Clean shelf-by-shelf overview of Balls, Dunks & Tags"
        ));

        ItemStack ballTabIcon = BallSkin.GOLD.createItemStack();
        ItemMeta btm = ballTabIcon.getItemMeta();
        if (btm != null) {
            boolean active = (cat == ShopCategory.BALLS);
            btm.displayName(Msg.parse((active ? "<#4ADE80><bold>● " : "<#FBBF24><bold>") + "Basketballs (" + unownedBalls.size() + ")</bold>" + (active ? "</#4ADE80>" : "</#FBBF24>")));
            btm.lore(List.of(
                Msg.parse("<gray>3D Square Basketball Skins</gray>"),
                Msg.parse(""),
                Msg.parse(active ? "<#4ADE80>✔ Viewing Basketballs</#4ADE80>" : "<yellow>▸ Click to filter Basketballs</yellow>")
            ));
            if (active) {
                btm.addEnchant(Enchantment.UNBREAKING, 1, true);
                btm.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            ballTabIcon.setItemMeta(btm);
        }
        inv.setItem(3, ballTabIcon);

        inv.setItem(4, buildShopTabButton(
            cat == ShopCategory.DUNKS,
            "dunk_meteor_jam",
            "Signature Dunks",
            unownedDunks.size(),
            "Aerial Slam Dunk animations with custom windups"
        ));
        inv.setItem(5, buildShopTabButton(
            cat == ShopCategory.TAGS,
            "icon_tags",
            "Collector Tags",
            unownedTags.size(),
            "Custom Chat, [TAB] & Locker Room badges sorted by Rarity"
        ));

        inv.setItem(8, customIcon(
            "icon_chest",
            "<#E879F9><bold>My Locker & Vault</bold></#E879F9>",
            List.of(
                "<gray>Equip your already-owned</gray>",
                "<gray>Basketballs, Dunks & Chat Tags!</gray>",
                "",
                "<yellow>▸ Left-Click: Balls & Dunks</yellow>",
                "<aqua>▸ Right-Click: Chat Tags</aqua>"
            )
        ));

        // ── BODY CONTENT ──
        if (cat == ShopCategory.ALL) {
            if (totalUnowned == 0) {
                inv.setItem(22, customIcon(
                    "icon_podium",
                    "<#4ADE80><bold>100% Collection Unlocked!</bold></#4ADE80>",
                    List.of(
                        "<gray>You own every purchasable Basketball,</gray>",
                        "<gray>Signature Dunk & Collector Tag on the server!</gray>",
                        "",
                        "<aqua>Click My Locker (top-right) to customize your loadout.</aqua>"
                    )
                ));
            } else {
                // Shelf 1 (Row 1): Slot 9 Label + Slots 10..16 Unowned Basketballs
                inv.setItem(9, customIcon(
                    "icon_star_shield",
                    "<#FF8C1A><bold>🏀 Basketballs ▸</bold></#FF8C1A>",
                    List.of(
                        "<gray>Remaining:</gray> <white>" + unownedBalls.size() + " skins</white>",
                        "",
                        "<yellow>▸ Click to view Basketballs tab</yellow>"
                    )
                ));
                if (unownedBalls.isEmpty()) {
                    inv.setItem(10, customIcon("icon_podium", "<#4ADE80><bold>✔ All Basketballs Owned</bold></#4ADE80>", List.of("<gray>Equip them in My Locker!</gray>")));
                } else {
                    for (int i = 0; i < SHELF_BALL_SLOTS.length && i < unownedBalls.size(); i++) {
                        int s = SHELF_BALL_SLOTS[i];
                        ShopItemEntry e = unownedBalls.get(i);
                        inv.setItem(s, buildShopEntryCard(player, profile, e));
                        holder.shopSlotMap().put(s, e);
                    }
                }

                // Shelf 2 (Row 2): Slot 18 Label + Slots 19..25 Unowned Signature Dunks
                inv.setItem(18, customIcon(
                    "dunk_meteor_jam",
                    "<#FB923C><bold>🔥 Dunk Styles ▸</bold></#FB923C>",
                    List.of(
                        "<gray>Remaining:</gray> <white>" + unownedDunks.size() + " dunks</white>",
                        "",
                        "<yellow>▸ Click to view Signature Dunks tab</yellow>"
                    )
                ));
                if (unownedDunks.isEmpty()) {
                    inv.setItem(19, customIcon("icon_podium", "<#4ADE80><bold>✔ All Dunk Styles Owned</bold></#4ADE80>", List.of("<gray>Equip them in My Locker!</gray>")));
                } else {
                    for (int i = 0; i < SHELF_DUNK_SLOTS.length && i < unownedDunks.size(); i++) {
                        int s = SHELF_DUNK_SLOTS[i];
                        ShopItemEntry e = unownedDunks.get(i);
                        inv.setItem(s, buildShopEntryCard(player, profile, e));
                        holder.shopSlotMap().put(s, e);
                    }
                }

                // Shelf 3 & 4 (Rows 3–4): Slot 27 Label + 14 Collector Tags per page (Sorted Common -> Legendary!)
                inv.setItem(27, customIcon(
                    "icon_tags",
                    "<#38BDF8><bold>🏷 Chat Tags ▸</bold></#38BDF8>",
                    List.of(
                        "<gray>Remaining:</gray> <white>" + unownedTags.size() + " tags</white>",
                        "<gray>Sorted cleanly from Common → Legendary!</gray>",
                        "",
                        "<aqua>▸ Click for full Collector Tags grid</aqua>"
                    )
                ));
                if (unownedTags.isEmpty()) {
                    inv.setItem(28, customIcon("icon_podium", "<#4ADE80><bold>✔ All Collector Tags Owned</bold></#4ADE80>", List.of("<gray>Equip them in your Tag Vault!</gray>")));
                } else {
                    int startIdx = page * SHELF_TAG_SLOTS.length;
                    for (int i = 0; i < SHELF_TAG_SLOTS.length && (startIdx + i) < unownedTags.size(); i++) {
                        int s = SHELF_TAG_SLOTS[i];
                        ShopItemEntry e = unownedTags.get(startIdx + i);
                        inv.setItem(s, buildShopEntryCard(player, profile, e));
                        holder.shopSlotMap().put(s, e);
                    }
                }
            }
        } else {
            if (catEntries.isEmpty()) {
                inv.setItem(22, customIcon(
                    "icon_podium",
                    "<#4ADE80><bold>All " + cat.label() + " Unlocked!</bold></#4ADE80>",
                    List.of(
                        "<gray>You already own every item in</gray>",
                        "<white>" + cat.label() + "</white><gray>!</gray>",
                        "",
                        "<aqua>Click My Locker (top-right) to equip them.</aqua>"
                    )
                ));
            } else {
                int startIdx = page * SHOP_GRID_SLOTS.length;
                for (int i = 0; i < SHOP_GRID_SLOTS.length && (startIdx + i) < catEntries.size(); i++) {
                    int s = SHOP_GRID_SLOTS[i];
                    ShopItemEntry e = catEntries.get(startIdx + i);
                    inv.setItem(s, buildShopEntryCard(player, profile, e));
                    holder.shopSlotMap().put(s, e);
                }
            }
        }

        // ── BOTTOM FOOTER BAR (Row 5) ──
        if (page > 0) {
            inv.setItem(45, customIcon(
                "icon_prev",
                "<yellow><bold>◂ Previous Page</bold></yellow>",
                List.of("<gray>Page " + page + " of " + totalPages + "</gray>")
            ));
        }
        inv.setItem(48, customIcon(
            "dunk_tomahawk",
            "<#F472B6><bold>My Basketballs & Dunks</bold></#F472B6>",
            List.of("<gray>Equip your owned Basketballs & Dunks</gray>", "", "<yellow>▸ Open Locker</yellow>")
        ));
        inv.setItem(49, backButton(player));
        inv.setItem(50, customIcon(
            "icon_tags",
            "<#38BDF8><bold>My Chat Tags</bold></#38BDF8>",
            List.of("<gray>Equip or remove your unlocked Tags</gray>", "", "<aqua>▸ Open Tag Vault</aqua>")
        ));
        if (page + 1 < totalPages) {
            inv.setItem(53, customIcon(
                "icon_next",
                "<yellow><bold>Next Page ▸</bold></yellow>",
                List.of("<gray>Page " + (page + 2) + " of " + totalPages + "</gray>")
            ));
        }

        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.24f);
    }

    private ItemStack buildShopTabButton(boolean active, String modelId, String title, int count, String desc) {
        ItemStack stack = customIcon(
            modelId,
            (active ? "<#4ADE80><bold>● " : "<#CBD5E1><bold>") + title + " (" + count + ")</bold>" + (active ? "</#4ADE80>" : "</#CBD5E1>"),
            List.of(
                "<gray>" + desc + "</gray>",
                "",
                active ? "<#4ADE80>✔ Active Tab</#4ADE80>" : "<yellow>▸ Click to view</yellow>"
            )
        );
        if (active) {
            ItemMeta m = stack.getItemMeta();
            if (m != null) {
                m.addEnchant(Enchantment.UNBREAKING, 1, true);
                m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                stack.setItemMeta(m);
            }
        }
        return stack;
    }

    private ItemStack buildShopEntryCard(Player player, PlayerProfile profile, ShopItemEntry entry) {
        boolean canAfford = profile.coins() >= entry.priceCoins();
        String buyAction = canAfford
            ? "<#4ADE80><bold>▸ Click to Buy & Equip (" + entry.priceCoins() + " Coins)</bold></#4ADE80>"
            : "<#F87171>▸ Need " + (entry.priceCoins() - profile.coins()) + " more Coins</#F87171>";

        if (entry.ballSkin() != null) {
            BallSkin skin = entry.ballSkin();
            ItemStack card = skin.createItemStack();
            ItemMeta meta = card.getItemMeta();
            if (meta != null) {
                meta.displayName(Msg.parse(skin.displayNameMm()));
                meta.lore(List.of(
                    Msg.parse("<dark_gray>3D Square Basketball Skin</dark_gray>"),
                    Msg.parse("<gray>" + skin.loreLine() + "</gray>"),
                    Msg.parse("<gray>Price:</gray> " + coinStr(entry.priceCoins())),
                    Msg.parse(""),
                    Msg.parse(buyAction)
                ));
                card.setItemMeta(meta);
            }
            return card;
        }
        if (entry.dunkStyle() != null) {
            DunkStyle dunk = entry.dunkStyle();
            return customIcon(
                dunk.iconModelId(),
                dunk.displayNameMm(),
                List.of(
                    "<dark_gray>Signature Aerial Dunk Style</dark_gray>",
                    "<gray>" + dunk.descriptionSmallCaps() + "</gray>",
                    "<gray>Price:</gray> " + coinStr(entry.priceCoins()),
                    "",
                    buyAction
                )
            );
        }
        ChatTag tag = entry.chatTag();
        return customIcon(
            tag.cardModelId(),
            "<!shadow><white>" + tag.glyph() + "</white></!shadow> <#F8FAFC><bold>" + tag.displayName() + "</bold></#F8FAFC>",
            List.of(
                "<dark_gray>Collector Tag • </dark_gray>" + tag.tierLabel(),
                "<gray>" + tag.loreHint() + "</gray>",
                "<gray>In Chat:</gray> <!shadow><white>" + profile.effectiveRank().pillGlyph() + "</white></!shadow> <!shadow><white>" + tag.glyph() + "</white></!shadow> <white>" + player.getName() + "</white>",
                "<gray>Price:</gray> " + coinStr(entry.priceCoins()),
                "",
                buyAction
            )
        );
    }

    // -------------------------------------------------------------------------
    // 3. BASKETBALLS & DUNKS LOCKER
    // -------------------------------------------------------------------------
    public void openCosmetics(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.COSMETICS);
        Inventory inv = Bukkit.createInventory(
            holder,
            45,
            guiTitle(Glyphs.ICON_BALL, "<#F472B6><bold>Basketballs & Dunks</bold></#F472B6>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

        inv.setItem(4, customIcon(
            "icon_chest",
            "<#FDE047><bold>Your Balance</bold></#FDE047>",
            List.of(
                coinStr(profile.coins()),
                "<gray>Win matches & score buckets to earn Coins!</gray>"
            )
        ));

        BallSkin[] balls = BallSkin.values();
        int[] ballSlots = {10, 11, 12, 13, 14, 15, 16, 22};
        for (int i = 0; i < balls.length && i < ballSlots.length; i++) {
            BallSkin skin = balls[i];
            boolean unlocked = profile.hasUnlockedBall(skin) || player.hasPermission("bosketball.cosmetics.all");
            boolean selected = profile.selectedBallSkin() == skin;

            ItemStack ballItem = skin.createItemStack();
            ItemMeta meta = ballItem.getItemMeta();
            if (meta != null) {
                meta.displayName(Msg.parse(skin.displayNameMm()));
                String actionLine = selected
                    ? "<#4ADE80><bold>✔ Equipped</bold></#4ADE80>"
                    : (unlocked
                        ? "<yellow>▸ Click to equip</yellow>"
                        : "<#FDE047>▸ Buy for " + skin.priceCoins() + " Coins</#FDE047>");
                meta.lore(List.of(
                    Msg.parse("<gray>" + skin.loreLine() + "</gray>"),
                    Msg.parse(unlocked ? "<#4ADE80>Unlocked</#4ADE80>" : "<gray>Price:</gray> " + coinStr(skin.priceCoins())),
                    Msg.parse(""),
                    Msg.parse(actionLine)
                ));
                ballItem.setItemMeta(meta);
            }
            inv.setItem(ballSlots[i], ballItem);
        }

        DunkStyle[] dunks = DunkStyle.values();
        int[] dunkSlots = {29, 30, 31, 32, 33};
        for (int i = 0; i < dunks.length && i < dunkSlots.length; i++) {
            DunkStyle dunk = dunks[i];
            boolean unlocked = profile.hasUnlockedDunk(dunk) || player.hasPermission("bosketball.cosmetics.all");
            boolean selected = profile.selectedDunkStyle() == dunk;

            String actionLine = selected
                ? "<#4ADE80><bold>✔ Equipped</bold></#4ADE80>"
                : (unlocked
                    ? "<yellow>▸ Click to equip</yellow>"
                    : "<#FDE047>▸ Buy for " + dunk.priceCoins() + " Coins</#FDE047>");

            ItemStack dunkItem = customIcon(
                dunk.iconModelId(),
                dunk.displayNameMm(),
                List.of(
                    "<gray>" + dunk.descriptionSmallCaps() + "</gray>",
                    unlocked ? "<#4ADE80>Unlocked</#4ADE80>" : "<gray>Price:</gray> " + coinStr(dunk.priceCoins()),
                    "",
                    actionLine
                )
            );
            if (selected) {
                ItemMeta dm = dunkItem.getItemMeta();
                if (dm != null) {
                    dm.addEnchant(Enchantment.UNBREAKING, 1, true);
                    dm.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    dunkItem.setItemMeta(dm);
                }
            }
            inv.setItem(dunkSlots[i], dunkItem);
        }

        inv.setItem(39, customIcon(
            "icon_tags",
            "<#FBBF24><bold>My Chat Tags</bold></#FBBF24>",
            List.of("<gray>Equip or unlock Collector Chat Tags</gray>", "", "<yellow>▸ Open Tag Vault</yellow>")
        ));
        inv.setItem(40, backButton(player));
        inv.setItem(41, customIcon(
            "icon_shelves",
            "<#4ADE80><bold>Boutique Shop</bold></#4ADE80>",
            List.of("<gray>Browse unowned Balls, Dunks & Tags</gray>", "", "<green>▸ Open Boutique</green>")
        ));
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.22f);
    }

    // -------------------------------------------------------------------------
    // 4. TAG SHOP (Paginated, Coin Economy)
    // -------------------------------------------------------------------------
    public void openTagsVault(Player player) {
        openTagsVault(player, 0);
    }

    public void openTagsVault(Player player, int requestedPage) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        profile.syncExclusiveFounderState();
        List<ChatTag> tags = visibleVaultTags(player, profile);
        int perPage = 36;
        int totalPages = Math.max(1, (tags.size() + perPage - 1) / perPage);
        int page = Math.max(0, Math.min(totalPages - 1, requestedPage));

        BosketHolder holder = new BosketHolder(MenuType.TAGS_VAULT, page);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            guiTitle(Glyphs.ICON_CROWN, "<#FBBF24><bold>Chat Tags • Page " + (page + 1) + "/" + totalPages + "</bold></#FBBF24>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        int startIdx = page * perPage;

        for (int slot = 0; slot < perPage && (startIdx + slot) < tags.size(); slot++) {
            ChatTag tag = tags.get(startIdx + slot);
            boolean equipped = profile.selectedTag() == tag;
            boolean unlocked = tag.canBeUsedBy(profile, player.getName(), player.hasPermission("bosketball.tags.all"));

            String status = equipped
                ? "<#4ADE80><bold>✔ Equipped (Click to remove)</bold></#4ADE80>"
                : (unlocked
                    ? "<yellow>▸ Click to equip</yellow>"
                    : "<#FDE047>▸ Buy for " + tag.priceCoins() + " Coins</#FDE047>");

            String ownershipLine = tag.isSpecialUnpurchasable()
                ? "<#F43F5E>★ Exclusive Honor (Not sold in Shop)</#F43F5E>"
                : (unlocked ? "<#4ADE80>Owned</#4ADE80>" : "<gray>Price:</gray> " + coinStr(tag.priceCoins()));

            ItemStack tagCard = customIcon(
                tag.cardModelId(),
                "<!shadow><white>" + tag.glyph() + "</white></!shadow> <#F8FAFC><bold>" + tag.displayName() + "</bold></#F8FAFC>",
                List.of(
                    "<gray>Tier:</gray> <white>" + tag.tierLabel() + "</white>",
                    "<gray>" + tag.loreHint() + "</gray>",
                    ownershipLine,
                    "<gray>Preview:</gray> <!shadow><white>" + profile.effectiveRank().pillGlyph() + "</white></!shadow> <!shadow><white>" + tag.glyph() + "</white></!shadow> <white>" + player.getName() + "</white>",
                    "",
                    status
                )
            );
            if (equipped) {
                ItemMeta tm = tagCard.getItemMeta();
                if (tm != null) {
                    tm.addEnchant(Enchantment.UNBREAKING, 1, true);
                    tm.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    tagCard.setItemMeta(tm);
                }
            }
            inv.setItem(slot, tagCard);
        }

        if (page > 0) {
            inv.setItem(45, customIcon(
                "icon_prev",
                "<yellow><bold>◂ Previous Page</bold></yellow>",
                List.of("<gray>Page " + page + " of " + totalPages + "</gray>")
            ));
        }
        inv.setItem(47, customIcon(
            "icon_coin_stack",
            "<#FDE047><bold>Your Balance</bold></#FDE047>",
            List.of(
                coinStr(profile.coins()),
                "<gray>Play matches to earn Coins!</gray>"
            )
        ));
        inv.setItem(48, customIcon(
            "icon_leave",
            "<#F87171><bold>Unequip Tag</bold></#F87171>",
            List.of("<gray>Clear your active chat tag</gray>")
        ));
        inv.setItem(49, backButton(player));
        inv.setItem(50, customIcon(
            "dunk_tomahawk",
            "<#F472B6><bold>Basketballs & Dunks</bold></#F472B6>",
            List.of("<gray>Switch to Basketball & Dunk Locker</gray>")
        ));
        if (page + 1 < totalPages) {
            inv.setItem(53, customIcon(
                "icon_next",
                "<yellow><bold>Next Page ▸</bold></yellow>",
                List.of("<gray>Page " + (page + 2) + " of " + totalPages + "</gray>")
            ));
        }

        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.28f);
    }

    // -------------------------------------------------------------------------
    // 5. LEADERBOARDS (8 Categories including Coins!)
    // -------------------------------------------------------------------------
    public void openLeaderboards(Player player, LeaderboardCategory category) {
        BosketHolder holder = new BosketHolder(MenuType.LEADERBOARDS, category);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            guiTitle(Glyphs.ICON_TROPHY, "<#FDE047><bold>Top 10 • " + category.label() + "</bold></#FDE047>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        LeaderboardCategory[] cats = LeaderboardCategory.values();
        String[] catIcons = {"icon_podium", "icon_chest", "icon_arena", "icon_star_shield", "icon_dunk", "icon_tags", "icon_defense", "icon_pass"};
        int[] catSlots = {0, 1, 2, 3, 5, 6, 7, 8};
        for (int i = 0; i < cats.length && i < catSlots.length; i++) {
            LeaderboardCategory c = cats[i];
            boolean active = c == category;
            inv.setItem(catSlots[i], customIcon(
                catIcons[i],
                (active ? "<#FDE047><bold>✦ " : "<gray>") + c.label() + (active ? " ✦</bold></#FDE047>" : "</gray>"),
                List.of(active ? "<#4ADE80>✔ Viewing</#4ADE80>" : "<yellow>▸ Click to view</yellow>")
            ));
        }

        inv.setItem(4, customIcon(
            "icon_book",
            "<#FDE047><bold>Top #1 / #2 / #3 Server ELO Badges</bold></#FDE047>",
            List.of(
                "<gray>The Top 3 ELO players on the server earn</gray>",
                "<gray>live badges in Chat, /gg, [TAB] & Scoreboard:</gray>",
                "  <!shadow><white>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 1) + "</white></!shadow> <#FFD700><bold>#1 Gold ELO Champion</bold></#FFD700>",
                "  <!shadow><white>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 2) + "</white></!shadow> <#E2E8F0><bold>#2 Silver ELO Runner-Up</bold></#E2E8F0>",
                "  <!shadow><white>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 3) + "</white></!shadow> <#CD7F32><bold>#3 Bronze ELO Contender</bold></#CD7F32>",
                "",
                "<yellow>▸ Click to open full Ranks & Badges Guide</yellow>"
            )
        ));

        List<PlayerProfile> top = plugin.statsManager().topBy(category, 10);
        int[] podiumSlots = {13, 21, 23, 29, 30, 31, 32, 33, 39, 41};
        for (int i = 0; i < top.size() && i < podiumSlots.length; i++) {
            inv.setItem(podiumSlots[i], buildLeaderboardSkull(i + 1, top.get(i), category));
        }

        inv.setItem(49, backButton(player));
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
            guiTitle(Glyphs.ICON_STAR, "<#818CF8><bold>Match History</bold></#818CF8>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        List<MatchHistoryEntry> history = profile.matchHistory();
        if (history.isEmpty()) {
            inv.setItem(22, customIcon(
                "icon_history",
                "<#FBBF24><bold>No Matches Played Yet</bold></#FBBF24>",
                List.of("<gray>Complete a 1v1, 2v2, 3v3 or 5v5 game</gray>", "<gray>to view your box scores here.</gray>")
            ));
        } else {
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 31};
            for (int i = 0; i < history.size() && i < slots.length; i++) {
                MatchHistoryEntry h = history.get(i);
                String delta = h.eloDelta() >= 0 ? "<#4ADE80>+" + h.eloDelta() + " ELO</#4ADE80>" : "<#F87171>" + h.eloDelta() + " ELO</#F87171>";
                inv.setItem(slots[i], customIcon(
                    h.won() ? "icon_podium" : "icon_history",
                    (h.won() ? "<#4ADE80><bold>✔ Victory" : "<#F87171><bold>✖ Defeat") + " • " + h.modeKey() + "</bold>",
                    List.of(
                        "<gray>Date:</gray> <white>" + h.formattedDate() + "</white>",
                        "<#FF5C4D>Ignis " + h.ignisScore() + "</#FF5C4D> <gray>—</gray> <#3DB8FF>" + h.frostScore() + " Frost</#3DB8FF>",
                        "<gray>Box Score:</gray> <yellow>" + h.points() + " PTS</yellow> <dark_gray>•</dark_gray> <aqua>" + h.assists() + " AST</aqua> <dark_gray>•</dark_gray> <green>" + h.rebounds() + " REB</green>",
                        "<gray>Rating:</gray> " + delta
                    )
                ));
            }
        }

        inv.setItem(49, backButton(player));
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
    }

    // -------------------------------------------------------------------------
    // 7. DEDICATED FRIENDS, PARTY & CUSTOM TEAM NAME MENUS + RANKS GUIDE + OP STUDIO
    // -------------------------------------------------------------------------
    public void openFriendsAndParty(Player player) {
        openFriendsMenu(player);
    }

    public void openFriendsMenu(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.FRIENDS_MENU);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            guiTitle(Glyphs.ICON_SHIELD, "<#4ADE80><bold>Friends List</bold></#4ADE80>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile myProf = plugin.statsManager().getOrCreate(player);
        int groupSize = plugin.socialManager().partySizeOrOne(player.getUniqueId());

        // Top Navigation Tabs
        inv.setItem(3, customIcon(
            "icon_star_shield",
            "<#4ADE80><bold>● Friends (" + myProf.friends().size() + ")</bold></#4ADE80>",
            List.of("<#4ADE80>✔ Viewing Friends List</#4ADE80>")
        ));
        inv.setItem(5, customIcon(
            "icon_friends",
            "<#FBBF24><bold>Party & Team Name (" + groupSize + "/4)</bold></#FBBF24>",
            List.of(
                "<gray>Create a Party, invite up to 4 friends,</gray>",
                "<gray>and set your custom</gray> <#FBBF24>Team Name</#FBBF24><gray>!</gray>",
                "",
                "<yellow>▸ Click to switch to Party Menu</yellow>"
            )
        ));
        inv.setItem(7, customIcon(
            "icon_book",
            "<#38BDF8><bold>How Friends Work</bold></#38BDF8>",
            List.of(
                "<gray>• Add friends via</gray> <white>➕ Add Online Player</white> <gray>or</gray> <white>/f <name></white>",
                "<gray>• Left-Click an online friend to</gray> <#4ADE80>Invite to Party</#4ADE80>",
                "<gray>• Shift + Left-Click to</gray> <#38BDF8>Challenge to a 1v1 Duel</#38BDF8>",
                "<gray>• Right-Click to</gray> <#F87171>Remove Friend</#F87171>"
            )
        ));

        int[] gridSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };
        int cursor = 0;

        // 1. Incoming Friend Requests first!
        for (java.util.UUID reqId : plugin.socialManager().incomingFriendRequests(player.getUniqueId())) {
            if (cursor >= gridSlots.length) break;
            OfflinePlayer off = Bukkit.getOfflinePlayer(reqId);
            PlayerProfile reqProf = plugin.statsManager().getIfPresent(reqId);
            String rName = off.getName() != null ? off.getName() : (reqProf != null ? reqProf.lastKnownName() : "Player");
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) skull.getItemMeta();
            if (sm != null) {
                sm.setOwningPlayer(off);
                sm.displayName(Msg.parse("<#FBBF24><bold>📩 Request: " + rName + "</bold></#FBBF24>"));
                sm.lore(List.of(
                    Msg.parse("<gray>Sent you a friend request!</gray>"),
                    Msg.parse(""),
                    Msg.parse("<#4ADE80><bold>▸ Left-Click: Accept Friend Request</bold></#4ADE80>"),
                    Msg.parse("<#F87171>▸ Right-Click: Decline</#F87171>")
                ));
                skull.setItemMeta(sm);
            }
            inv.setItem(gridSlots[cursor++], skull);
        }

        // 2. Friends sorted Online first, then Offline
        List<java.util.UUID> sortedFriends = new ArrayList<>(myProf.friends());
        sortedFriends.sort((a, b) -> {
            boolean oa = Bukkit.getPlayer(a) != null;
            boolean ob = Bukkit.getPlayer(b) != null;
            return Boolean.compare(ob, oa);
        });

        for (java.util.UUID fid : sortedFriends) {
            if (cursor >= gridSlots.length) break;
            Player onlineFriend = Bukkit.getPlayer(fid);
            OfflinePlayer off = onlineFriend != null ? onlineFriend : Bukkit.getOfflinePlayer(fid);
            PlayerProfile fProf = plugin.statsManager().getIfPresent(fid);
            String fName = onlineFriend != null ? onlineFriend.getName() : (fProf != null ? fProf.lastKnownName() : (off.getName() != null ? off.getName() : "Friend"));
            fr.bosketball.model.Rank fRank = fProf != null ? fProf.effectiveRank() : fr.bosketball.model.Rank.ROOKIE;
            int fElo = fProf != null ? fProf.elo() : 1000;

            String statusLine;
            if (onlineFriend != null && onlineFriend.isOnline()) {
                var sess = plugin.gameManager().getSessionOf(fid);
                statusLine = sess != null
                    ? "<#FBBF24>🏀 In Match (" + sess.mode().id().toUpperCase() + ")</#FBBF24>"
                    : "<#4ADE80>● Online — In Lobby</#4ADE80>";
            } else {
                statusLine = "<#64748B>○ Offline</#64748B>";
            }

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) skull.getItemMeta();
            if (sm != null) {
                sm.setOwningPlayer(off);
                sm.displayName(Msg.parse((onlineFriend != null ? "<#4ADE80><bold>" : "<#94A3B8><bold>") + fName + "</bold> <!shadow><white>" + fRank.pillGlyph() + "</white></!shadow>"));
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Msg.parse("<gray>Status:</gray> " + statusLine));
                lore.add(Msg.parse("<gray>Rank:</gray> <white>" + fRank.plainDisplayName() + "</white> <dark_gray>•</dark_gray> <#FBBF24>" + fElo + " ELO</#FBBF24>"));
                lore.add(Msg.parse(""));
                if (onlineFriend != null && onlineFriend.isOnline()) {
                    lore.add(Msg.parse("<#4ADE80>▸ Left-Click: Invite to Party</#4ADE80>"));
                    lore.add(Msg.parse("<#38BDF8>▸ Shift + Left-Click: 1v1 Duel</#38BDF8>"));
                }
                lore.add(Msg.parse("<#F87171>▸ Right-Click: Remove Friend</#F87171>"));
                sm.lore(lore);
                skull.setItemMeta(sm);
            }
            inv.setItem(gridSlots[cursor++], skull);
        }

        if (cursor == 0) {
            inv.setItem(22, customIcon(
                "icon_friends",
                "<#FBBF24><bold>No Friends Added Yet</bold></#FBBF24>",
                List.of(
                    "<gray>Click</gray> <#4ADE80><bold>➕ Add Online Player</bold></#4ADE80> <gray>below</gray>",
                    "<gray>to send a friend request in 1 click!</gray>"
                )
            ));
        }

        inv.setItem(47, customIcon(
            "icon_friends",
            "<#4ADE80><bold>➕ Add Online Player</bold></#4ADE80>",
            List.of("<gray>Browse online players & click their head</gray>", "<gray>to send a friend request!</gray>", "", "<green>▸ Browse Online Players</green>")
        ));
        inv.setItem(48, customIcon(
            "icon_tags",
            "<#38BDF8><bold>⌨ Type Player Name</bold></#38BDF8>",
            List.of("<gray>Type a player's username in chat</gray>", "<gray>to send them a friend request.</gray>", "", "<aqua>▸ Click to type in chat</aqua>")
        ));
        inv.setItem(49, backButton(player));
        inv.setItem(51, customIcon(
            "icon_arena",
            "<#FBBF24><bold>🏷 Party & Custom Team Name</bold></#FBBF24>",
            List.of("<gray>Manage your Party & rename your Team!</gray>", "", "<yellow>▸ Open Party Menu</yellow>")
        ));

        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
    }

    public void openAddFriendOnlineMenu(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.ADD_FRIEND_ONLINE);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            guiTitle(Glyphs.ICON_SHIELD, "<#4ADE80><bold>Add Online Player</bold></#4ADE80>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile myProf = plugin.statsManager().getOrCreate(player);
        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
            .<Player>map(p -> p)
            .filter(p -> !p.getUniqueId().equals(player.getUniqueId()) && !myProf.isFriend(p.getUniqueId()))
            .limit(28)
            .toList();

        int[] slots = SHOP_GRID_SLOTS;
        if (candidates.isEmpty()) {
            inv.setItem(22, customIcon(
                "icon_friends",
                "<#94A3B8><bold>No Other Unfriended Players Online</bold></#94A3B8>",
                List.of("<gray>Everyone online is already on your friends list!</gray>")
            ));
        } else {
            for (int i = 0; i < candidates.size() && i < slots.length; i++) {
                Player other = candidates.get(i);
                PlayerProfile oProf = plugin.statsManager().getOrCreate(other);
                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta sm = (SkullMeta) skull.getItemMeta();
                if (sm != null) {
                    sm.setOwningPlayer(other);
                    sm.displayName(Msg.parse("<#38BDF8><bold>" + other.getName() + "</bold></#38BDF8> <!shadow><white>" + oProf.effectiveRank().pillGlyph() + "</white></!shadow>"));
                    sm.lore(List.of(
                        Msg.parse("<gray>ELO:</gray> <#FBBF24>" + oProf.elo() + "</#FBBF24> <dark_gray>•</dark_gray> <gray>Rank:</gray> <white>" + oProf.effectiveRank().plainDisplayName() + "</white>"),
                        Msg.parse(""),
                        Msg.parse("<#4ADE80><bold>▸ Left-Click: Send Friend Request</bold></#4ADE80>"),
                        Msg.parse("<#FBBF24>▸ Right-Click: Invite to Party</#FBBF24>")
                    ));
                    skull.setItemMeta(sm);
                }
                inv.setItem(slots[i], skull);
            }
        }

        inv.setItem(49, customIcon("icon_back", "<#FBBF24><bold>◂ Back to Friends</bold></#FBBF24>", List.of("<gray>Return to your Friends List</gray>")));
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.25f);
    }

    public void openPartyMenu(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.PARTY_MENU);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            guiTitle(Glyphs.ICON_CROWN, "<#FBBF24><bold>Party & Custom Team Name</bold></#FBBF24>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile myProf = plugin.statsManager().getOrCreate(player);
        SocialPartyManager.Party party = plugin.socialManager().getParty(player.getUniqueId());
        int partySize = plugin.socialManager().partySizeOrOne(player.getUniqueId());
        boolean isLeader = party == null || party.isLeader(player.getUniqueId());
        String currentTeamName = (party != null && party.hasCustomTeamName()) ? party.teamName() : "Default (Ignis / Frost)";

        // Top Navigation Tabs
        inv.setItem(3, customIcon(
            "icon_friends",
            "<#4ADE80><bold>Friends (" + myProf.friends().size() + ")</bold></#4ADE80>",
            List.of("<gray>View friends, online status & requests</gray>", "", "<green>▸ Switch to Friends List</green>")
        ));

        java.util.UUID incomingPartyLeaderId = plugin.socialManager().pendingPartyInviteLeader(player.getUniqueId());
        if (incomingPartyLeaderId != null) {
            Player inviter = Bukkit.getPlayer(incomingPartyLeaderId);
            if (inviter != null && inviter.isOnline()) {
                ItemStack inviteSkull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta ism = (SkullMeta) inviteSkull.getItemMeta();
                if (ism != null) {
                    ism.setOwningPlayer(inviter);
                    ism.displayName(Msg.parse("<#4ADE80><bold>📩 Party Invite: " + inviter.getName() + "</bold></#4ADE80>"));
                    ism.lore(List.of(
                        Msg.parse("<gray>Invited you to join their party!</gray>"),
                        Msg.parse(""),
                        Msg.parse("<#4ADE80><bold>▸ Left-Click: Accept & Join Party</bold></#4ADE80>"),
                        Msg.parse("<#F87171>▸ Right-Click: Decline Invite</#F87171>")
                    ));
                    inviteSkull.setItemMeta(ism);
                }
                inv.setItem(4, inviteSkull);
            }
        }

        inv.setItem(5, customIcon(
            "icon_star_shield",
            "<#FBBF24><bold>● Party & Team Name (" + partySize + "/4)</bold></#FBBF24>",
            List.of("<#4ADE80>✔ Viewing Party Menu</#4ADE80>")
        ));

        // Centerpiece Slot 13: Set Custom Party Team Name!
        inv.setItem(13, customIcon(
            "icon_tags",
            "<#FBBF24><bold>🏷 Party Team Name: </bold></#FBBF24><white><bold>" + currentTeamName + "</bold></white>",
            List.of(
                "<gray>Create your own Team Name (2–16 chars)!</gray>",
                "<gray>Displayed live on the</gray> <white>Scoreboard, Match BossBar,</white>",
                "<white>Court Jumbotron & Locker Room</white> <gray>when your party plays!</gray>",
                "",
                isLeader ? "<#4ADE80><bold>▸ Left-Click: Type New Team Name in Chat</bold></#4ADE80>" : "<#F87171>Only the Party Leader can rename the team</#F87171>",
                isLeader ? "<#F87171>▸ Right-Click: Reset to Default</#F87171>" : "<dark_gray>Command: /party name <TeamName></dark_gray>"
            )
        ));

        // Party Roster Slots: 20, 21, 23, 24
        List<Player> members = plugin.socialManager().onlinePartyMembers(player);
        int[] memberSlots = {20, 21, 23, 24};
        for (int i = 0; i < memberSlots.length; i++) {
            if (i < members.size()) {
                Player m = members.get(i);
                PlayerProfile mProf = plugin.statsManager().getOrCreate(m);
                boolean memberIsLeader = (party == null && m.getUniqueId().equals(player.getUniqueId()))
                    || (party != null && party.isLeader(m.getUniqueId()));
                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta sm = (SkullMeta) skull.getItemMeta();
                if (sm != null) {
                    sm.setOwningPlayer(m);
                    sm.displayName(Msg.parse((memberIsLeader ? "<#FFD700><bold>👑 " : "<#38BDF8><bold>") + m.getName() + "</bold> <!shadow><white>" + mProf.effectiveRank().pillGlyph() + "</white></!shadow>"));
                    List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                    lore.add(Msg.parse("<gray>Role:</gray> " + (memberIsLeader ? "<#FFD700>Party Leader</#FFD700>" : "<white>Party Member</white>")));
                    lore.add(Msg.parse("<gray>ELO:</gray> <#FBBF24>" + mProf.elo() + "</#FBBF24>"));
                    if (isLeader && !m.getUniqueId().equals(player.getUniqueId())) {
                        lore.add(Msg.parse(""));
                        lore.add(Msg.parse("<#FBBF24>▸ Left-Click: Promote to Party Leader</#FBBF24>"));
                        lore.add(Msg.parse("<#F87171>▸ Right-Click: Remove from Party</#F87171>"));
                    }
                    sm.lore(lore);
                    skull.setItemMeta(sm);
                }
                inv.setItem(memberSlots[i], skull);
            } else {
                inv.setItem(memberSlots[i], customIcon(
                    "icon_friends",
                    "<#64748B><bold>Empty Party Slot #" + (i + 1) + "</bold></#64748B>",
                    List.of("<gray>Click to invite an online player or friend!</gray>", "", "<green>▸ Invite Players</green>")
                ));
            }
        }

        inv.setItem(31, customIcon(
            "icon_friends",
            "<#4ADE80><bold>➕ Invite Players to Party</bold></#4ADE80>",
            List.of("<gray>Invite friends or online players (up to 4)!</gray>", "", "<green>▸ Browse Online Players</green>")
        ));

        inv.setItem(47, customIcon(
            "icon_arena",
            "<#FF8C1A><bold>▶ Play Match Together</bold></#FF8C1A>",
            List.of("<gray>Queue 2v2, 3v3 or 5v5 together on the same team!</gray>", "", "<yellow>▸ Choose Game Mode</yellow>")
        ));
        if (party != null) {
            inv.setItem(48, customIcon(
                "icon_leave",
                "<#F87171><bold>Leave Party</bold></#F87171>",
                List.of("<gray>Leave or disband your current party</gray>")
            ));
        }
        inv.setItem(49, backButton(player));

        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
    }

    public void openPartyInviteMenu(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.PARTY_INVITE_ONLINE);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            guiTitle(Glyphs.ICON_CROWN, "<#4ADE80><bold>Invite Players to Party</bold></#4ADE80>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile myProf = plugin.statsManager().getOrCreate(player);
        SocialPartyManager.Party party = plugin.socialManager().getParty(player.getUniqueId());

        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
            .<Player>map(p -> p)
            .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
            .filter(p -> party == null || !party.members().contains(p.getUniqueId()))
            .sorted((a, b) -> Boolean.compare(myProf.isFriend(b.getUniqueId()), myProf.isFriend(a.getUniqueId())))
            .limit(SHOP_GRID_SLOTS.length)
            .toList();

        if (candidates.isEmpty()) {
            inv.setItem(22, customIcon(
                "icon_friends",
                "<#94A3B8><bold>No Other Players Available</bold></#94A3B8>",
                List.of("<gray>All online players are already in your party!</gray>")
            ));
        } else {
            for (int i = 0; i < candidates.size() && i < SHOP_GRID_SLOTS.length; i++) {
                Player other = candidates.get(i);
                PlayerProfile oProf = plugin.statsManager().getOrCreate(other);
                boolean isFriend = myProf.isFriend(other.getUniqueId());
                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta sm = (SkullMeta) skull.getItemMeta();
                if (sm != null) {
                    sm.setOwningPlayer(other);
                    sm.displayName(Msg.parse((isFriend ? "<#4ADE80><bold>★ " : "<#38BDF8><bold>") + other.getName() + "</bold> <!shadow><white>" + oProf.effectiveRank().pillGlyph() + "</white></!shadow>"));
                    sm.lore(List.of(
                        Msg.parse((isFriend ? "<#4ADE80>Friend</#4ADE80> <dark_gray>•</dark_gray> " : "") + "<gray>ELO:</gray> <#FBBF24>" + oProf.elo() + "</#FBBF24>"),
                        Msg.parse(""),
                        Msg.parse("<#4ADE80><bold>▸ Click to Invite to Party</bold></#4ADE80>")
                    ));
                    skull.setItemMeta(sm);
                }
                inv.setItem(SHOP_GRID_SLOTS[i], skull);
            }
        }

        inv.setItem(49, customIcon("icon_back", "<#FBBF24><bold>◂ Back to Party</bold></#FBBF24>", List.of("<gray>Return to Party Menu</gray>")));
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.25f);
    }

    // -------------------------------------------------------------------------
    // 7B. ELO RANKS, #1/#2/#3 BADGES, ECONOMY & BOW-SHOOTING GUIDE (/ranks)
    // -------------------------------------------------------------------------
    public void openRanksGuide(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.RANKS_GUIDE);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            guiTitle(Glyphs.ICON_TROPHY, "<#FBBF24><bold>Ranks, Badges & Controls Guide</bold></#FBBF24>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        fr.bosketball.model.Rank myCompRank = profile.competitiveRank();
        fr.bosketball.model.Rank nextRank = myCompRank.nextCompetitiveRank();

        String progressText = nextRank != null
            ? "<#FBBF24><bold>" + profile.elo() + "</bold></#FBBF24> <gray>/</gray> <white>" + nextRank.minElo() + " ELO</white> <dark_gray>(+" + Math.max(0, nextRank.minElo() - profile.elo()) + " needed for " + nextRank.plainDisplayName() + ")</dark_gray>"
            : "<#FDE047><bold>" + profile.elo() + " ELO — Maximum Tier Reached (MVP)!</bold></#FDE047>";

        inv.setItem(4, customIcon(
            "icon_podium",
            "<#FF8C1A><bold>Your Competitive Rating</bold></#FF8C1A>",
            List.of(
                "<gray>Current Rank:</gray> <!shadow><white>" + myCompRank.pillGlyph() + "</white></!shadow> <white><bold>" + myCompRank.plainDisplayName() + "</bold></white>",
                "<gray>Progress:</gray> " + progressText,
                "",
                "<gray>• Everyone starts at</gray> <white><bold>1,000 ELO (Rookie)</bold></white><gray>!</gray>",
                "<gray>• Win Ranked 1v1, 2v2, 3v3 or 5v5 matches to gain ELO</gray>",
                "<gray>  and automatically rank up with a celebration!</gray>"
            )
        ));

        fr.bosketball.model.Rank[] ladder = {
            fr.bosketball.model.Rank.ROOKIE,
            fr.bosketball.model.Rank.HOOPER,
            fr.bosketball.model.Rank.PRO,
            fr.bosketball.model.Rank.ALL_STAR,
            fr.bosketball.model.Rank.SUPERSTAR,
            fr.bosketball.model.Rank.LEGEND,
            fr.bosketball.model.Rank.MVP
        };
        int[] rankSlots = {19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < ladder.length; i++) {
            fr.bosketball.model.Rank r = ladder[i];
            boolean current = (r == myCompRank);
            boolean reached = profile.elo() >= r.minElo();
            int missing = Math.max(0, r.minElo() - profile.elo());

            String stateLine = current
                ? "<#4ADE80><bold>✦ YOUR CURRENT RANK ✦</bold></#4ADE80>"
                : (reached
                    ? "<#4ADE80>✔ Unlocked Tier</#4ADE80>"
                    : "<#FBBF24>Requires +" + missing + " more ELO (" + r.minElo() + " ELO)</#FBBF24>");

            inv.setItem(rankSlots[i], customIcon(
                current ? "icon_star_shield" : (reached ? "icon_podium" : "icon_arena"),
                "<!shadow><white>" + r.pillGlyph() + "</white></!shadow> <#F8FAFC><bold>" + r.plainDisplayName() + "</bold></#F8FAFC>",
                List.of(
                    "<gray>Threshold:</gray> <white>" + r.eloRangeDescription() + "</white>",
                    "",
                    stateLine
                )
            ));
        }

        // Bottom Clear Explanations (Slots 38, 40, 42)
        inv.setItem(38, customIcon(
            "icon_podium",
            "<#FFD700><bold>Top #1 / #2 / #3 ELO Badges</bold></#FFD700>",
            List.of(
                "<gray>The 3 highest-ELO players on the entire server</gray>",
                "<gray>automatically wear live Prestige Badges:</gray>",
                "  <!shadow><white>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 1) + "</white></!shadow> <#FFD700>#1 Gold Badge</#FFD700> <gray>(1st ELO)</gray>",
                "  <!shadow><white>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 2) + "</white></!shadow> <#E2E8F0>#2 Silver Badge</#E2E8F0> <gray>(2nd ELO)</gray>",
                "  <!shadow><white>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 3) + "</white></!shadow> <#CD7F32>#3 Bronze Badge</#CD7F32> <gray>(3rd ELO)</gray>",
                "<gray>Shown in Chat, /gg, [TAB], Locker Room & Sidebar!</gray>"
            )
        ));

        inv.setItem(40, customIcon(
            "dunk_meteor_jam",
            "<#4ADE80><bold>Manual Skill Controls</bold></#4ADE80>",
            List.of(
                "<#4ADE80>• Hold Right-Click (Slot 1):</#4ADE80> <white>Draw Bow Power (0% → 100%)</white>",
                "  <gray>Release Right-Click at 85%–100% Full Draw for a Green Swish!</gray>",
                "<#FBBF24>• Manual Slam Dunk:</#FBBF24> <white>Jump inside Paint + Aim at Rim + Click,</white>",
                "  <gray>then Click AGAIN in the 60%–90% Green Zone at peak height!</gray>",
                "<#38BDF8>• Press Q (or Slot 2):</#38BDF8> <white>Quick Pass / Mid-Air Alley-Oop</white>",
                "<#C084FC>• Sneak / Shift (or Slot 5):</#C084FC> <white>Ankle-Breaker Crossover</white>",
                "<#F87171>• Left-Click on Defense:</#F87171> <white>Steal Ball / Jump Block</white>"
            )
        ));

        inv.setItem(42, customIcon(
            "icon_coin_stack",
            "<#FDE047><bold>Coins & Collector Tags</bold></#FDE047>",
            List.of(
                "<gray>• Earn Coins in every match (+35 Win, +15 Played,</gray>",
                "<gray>  +3/Point, +4/Assist, +5 from GG)!</gray>",
                "<gray>• Spend Coins in the</gray> <yellow>Boutique Shop</yellow> <gray>on Balls, Dunks & Tags.</gray>",
                "<gray>• Exclusive Honor Tags:</gray>",
                "  <!shadow><white>" + ChatTag.FOUNDER.glyph() + "</white></!shadow> <#F43F5E>Founder</#F43F5E> <gray>— Exclusive to MaxLananas</gray>",
                "  <!shadow><white>" + ChatTag.MYPINEAPPLE.glyph() + "</white></!shadow> <#FBBF24>MyPineapple</#FBBF24> <gray>— Official Team Exclusive</gray>"
            )
        ));

        inv.setItem(49, backButton(player));
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.22f);
    }

    // -------------------------------------------------------------------------
    // 7C. OP-ONLY 1-CLICK VISUAL ARENA STUDIO (/bb map)
    // -------------------------------------------------------------------------
    public void openMapStudio(Player player) {
        if (!player.isOp()) {
            Msg.send(player, Msg.error("Arena creation is strictly reserved for Server Operators (OP)."));
            return;
        }
        BosketHolder holder = new BosketHolder(MenuType.MAP_STUDIO);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            guiTitle(Glyphs.ICON_BOLT, "<#38BDF8><bold>OP Arena Studio</bold></#38BDF8>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        var session = plugin.mapEditor().getOrBindSession(player, null);
        fr.bosketball.arena.Arena arena = session != null ? plugin.arenaManager().getArena(session.arenaId) : null;
        String arenaLabel = arena != null ? arena.id() : "None yet (Click Auto-Setup or Create!)";

        inv.setItem(4, customIcon(
            "icon_arena",
            "<#38BDF8><bold>Editing Arena: </bold></#38BDF8><white><bold>" + arenaLabel + "</bold></white>",
            List.of(
                "<gray>Super-easy 2-step setup on any custom floor:</gray>",
                "<yellow>1.</yellow> <white>Get the Wand</white> <gray>& Left/Right-Click 2 opposite floor corners</gray>",
                "<yellow>2.</yellow> <white>Click</white> <#4ADE80><bold>⚡ 1-Click Auto-Setup Full Court</bold></#4ADE80><gray>!</gray>",
                "<gray>(Or fine-tune Hoops, Scoreboard & White Lines individually below!)</gray>"
            )
        ));

        inv.setItem(19, customIcon(
            "court_wand",
            "<#38BDF8><bold>1. Get Floor Corner Wand</bold></#38BDF8>",
            List.of(
                "<gray>Gives you the Arena Setup Wand:</gray>",
                "<yellow>• Left-Click Floor Block:</yellow> <white>Corner #1</white>",
                "<yellow>• Right-Click Floor Block:</yellow> <white>Corner #2</white>",
                "<aqua>• Shift + Right-Click Air:</aqua> <white>Re-open this Studio</white>",
                "",
                "<aqua>▸ Click to receive Wand</aqua>"
            )
        ));

        inv.setItem(21, customIcon(
            "icon_star_shield",
            "<#4ADE80><bold>⚡ 2. 1-Click Auto-Setup Full Court!</bold></#4ADE80>",
            List.of(
                "<gray>Automatically places EVERYTHING at once:</gray>",
                "<white>✔ Both Hoops, Backboards & Closed Nets</white>",
                "<white>✔ Flush White Court Lines (3PT, Keys, Half-Court)</white>",
                "<white>✔ Overhead 4-Sided Scoreboard (Jumbotron)</white>",
                "<white>✔ Out-of-Bounds & Invisible Ball Barriers</white>",
                "",
                "<#4ADE80><bold>▸ Click to Auto-Build Court Now!</bold></#4ADE80>"
            )
        ));

        inv.setItem(23, customIcon(
            "icon_dunk",
            "<#FF6B4A><bold>Place Hoops & Backboards Here</bold></#FF6B4A>",
            List.of(
                "<gray>Stand where you want a basket & face the court:</gray>",
                "<#FF6B4A>▸ Left-Click: Place Ignis Target Hoop Here</#FF6B4A>",
                "<#38BDF8>▸ Right-Click: Place Frost Target Hoop Here</#38BDF8>"
            )
        ));

        boolean linesOn = arena != null && arena.isCourtLinesEnabled();
        String lineMat = arena != null ? arena.courtLinePrimaryMaterial().name() : "WHITE_CONCRETE";
        inv.setItem(25, customIcon(
            "icon_tags",
            "<#F8FAFC><bold>White Court Lines: </bold></#F8FAFC>" + (linesOn ? "<#4ADE80><bold>ON</bold></#4ADE80>" : "<#F87171><bold>OFF</bold></#F87171>"),
            List.of(
                "<gray>Material:</gray> <white>" + lineMat + "</white>",
                "<gray>Preset:</gray> <aqua>" + (arena != null ? arena.courtLinesPreset().name() : "FULL") + "</aqua>",
                "",
                "<yellow>▸ Left-Click: Toggle White Lines ON / OFF</yellow>",
                "<aqua>▸ Right-Click: Cycle Preset (FULL / MINIMAL / STREET)</aqua>"
            )
        ));

        boolean jumboOn = arena != null && arena.isJumbotronEnabled();
        inv.setItem(29, customIcon(
            "icon_podium",
            "<#FBBF24><bold>Overhead Scoreboard: </bold></#FBBF24>" + (jumboOn ? "<#4ADE80><bold>ON</bold></#4ADE80>" : "<#94A3B8><bold>OFF</bold></#94A3B8>"),
            List.of(
                "<gray>Places the 4-sided live Scoreboard</gray>",
                "<gray>at your current position (fly up above center court!).</gray>",
                "",
                "<yellow>▸ Left-Click: Place Scoreboard Here</yellow>",
                "<#F87171>▸ Right-Click: Hide Scoreboard</#F87171>"
            )
        ));

        inv.setItem(31, customIcon(
            "icon_friends",
            "<#34D399><bold>Set Team Spawns Here</bold></#34D399>",
            List.of(
                "<gray>Optional custom spawn override at your feet:</gray>",
                "<#FF6B4A>▸ Left-Click: Set Ignis Spawn #1 Here</#FF6B4A>",
                "<#38BDF8>▸ Right-Click: Set Frost Spawn #1 Here</#38BDF8>"
            )
        ));

        inv.setItem(33, customIcon(
            "icon_portal",
            "<#C084FC><bold>Build Full Stadium Structure</bold></#C084FC>",
            List.of(
                "<gray>Generates a complete hardwood stadium</gray>",
                "<gray>(Blocks + Bleachers + Hoops + Scoreboard)!</gray>",
                "",
                "<light_purple>▸ Shift-Click to Generate Full Stadium</light_purple>"
            )
        ));

        inv.setItem(47, customIcon(
            "icon_settings",
            "<#7DD3FC><bold>Toggle 3D Particle Blueprint</bold></#7DD3FC>",
            List.of("<gray>Show/hide live court boundary & hoop particles</gray>", "", "<aqua>▸ Click to toggle</aqua>")
        ));
        inv.setItem(49, backButton(player));
        inv.setItem(51, customIcon(
            "icon_arena",
            "<#4ADE80><bold>💾 Save & Test in Practice!</bold></#4ADE80>",
            List.of("<gray>Saves arena & teleports you onto the court</gray>", "<gray>with a basketball right away!</gray>", "", "<green>▸ Save & Play Now</green>")
        ));

        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.75f, 1.2f);
    }

    // -------------------------------------------------------------------------
    // 8. SETTINGS
    // -------------------------------------------------------------------------
    public void openSettings(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.SETTINGS);
        Inventory inv = Bukkit.createInventory(
            holder,
            45,
            guiTitle(Glyphs.ICON_BOLT, "<#CBD5E1><bold>Settings</bold></#CBD5E1>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        PlayerSettings st = profile.settings();

        inv.setItem(19, settingToggle("icon_dunk", "Court Animations", "Dribble & shot gather animations", st.customAnimations()));
        inv.setItem(20, settingToggle("icon_arena", "Team Floor Rings", "Ignis & Frost floor rings on court", st.floorAuraRings()));
        inv.setItem(21, settingToggle("icon_star_shield", "Custom Sound Effects", "Swish, buzzer, whistle & court audio", st.customSounds()));
        inv.setItem(22, settingToggle("icon_defense", "Third-Person Visuals", "Show extra cosmetic effects in F5", st.selfBodyRigInF5()));
        inv.setItem(23, settingToggle("icon_portal", "Highlight Replay Camera", "Instant replay camera on clutch buckets", st.cinematicScoreCam()));
        inv.setItem(24, settingToggle("icon_friends", "Party Invites", "Allow incoming Party invitations", st.partyInvites()));

        inv.setItem(40, backButton(player));
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
                enabled ? "<#4ADE80><bold>● Enabled</bold></#4ADE80>" : "<#F87171><bold>○ Disabled</bold></#F87171>",
                "<yellow>▸ Click to toggle</yellow>"
            )
        );
    }

    // -------------------------------------------------------------------------
    // Click & Anti-Duplication Handling
    // -------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof BosketHolder holder)) {
            return;
        }
        // Cancel ALL clicks (top, bottom, hotbar swap, shift-click, double-click) to prevent item duplication!
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
                    case 22 -> openShop(player);
                    case 23 -> {
                        if (event.isRightClick()) {
                            openTagsVault(player, 0);
                        } else {
                            openCosmetics(player);
                        }
                    }
                    case 24 -> openLeaderboards(player, LeaderboardCategory.ELO);
                    case 29 -> openMatchHistory(player);
                    case 31 -> openRanksGuide(player);
                    case 33 -> {
                        if (event.isRightClick()) {
                            openPartyMenu(player);
                        } else {
                            openFriendsMenu(player);
                        }
                    }
                    case 39 -> {
                        player.closeInventory();
                        Msg.sendDiscordCard(player, plugin.settings().links().discord());
                    }
                    case 40 -> openSettings(player);
                    case 41 -> {
                        if (plugin.gameManager().isInMatch(player.getUniqueId()) || plugin.gameManager().isQueued(player.getUniqueId())) {
                            player.closeInventory();
                            plugin.gameManager().leaveAll(player, true);
                        } else {
                            openRanksGuide(player);
                        }
                    }
                    case 44 -> {
                        if (player.isOp()) {
                            openMapStudio(player);
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
                    case 31 -> {
                        if (plugin.lockerRoom3dManager() != null && plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
                            plugin.lockerRoom3dManager().close(player, true);
                        } else {
                            player.closeInventory();
                        }
                        plugin.gameManager().spectateFirstActiveMatch(player);
                    }
                    case 33 -> {
                        if (plugin.lockerRoom3dManager() != null && plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
                            plugin.lockerRoom3dManager().close(player, true);
                        } else {
                            player.closeInventory();
                        }
                        plugin.gameManager().startPractice(player);
                    }
                    case 40 -> navigateBack(player);
                }
            }
            case SHOP -> {
                ShopCategory cat = holder.shopCategory();
                int page = holder.page();
                if (slot >= 2 && slot <= 5) {
                    ShopCategory[] filters = ShopCategory.values();
                    openShop(player, filters[slot - 2], 0);
                    return;
                }
                if (slot == 8) {
                    if (event.isRightClick()) {
                        openTagsVault(player, 0);
                    } else {
                        openCosmetics(player);
                    }
                    return;
                }
                if (cat == ShopCategory.ALL) {
                    if (slot == 9) {
                        openShop(player, ShopCategory.BALLS, 0);
                        return;
                    }
                    if (slot == 18) {
                        openShop(player, ShopCategory.DUNKS, 0);
                        return;
                    }
                    if (slot == 27) {
                        openShop(player, ShopCategory.TAGS, 0);
                        return;
                    }
                }
                if (slot == 45 && page > 0) {
                    openShop(player, cat, page - 1);
                    return;
                }
                if (slot == 48) {
                    openCosmetics(player);
                    return;
                }
                if (slot == 49) {
                    navigateBack(player);
                    return;
                }
                if (slot == 50) {
                    openTagsVault(player, 0);
                    return;
                }
                if (slot == 53) {
                    openShop(player, cat, page + 1);
                    return;
                }
                ShopItemEntry slotEntry = holder.shopSlotMap().get(slot);
                if (slotEntry != null) {
                    handleShopEntryPurchase(player, profile, slotEntry, cat, page);
                }
            }
            case COSMETICS -> {
                if (slot == 40) {
                    navigateBack(player);
                    return;
                }
                if (slot == 39) {
                    openTagsVault(player, 0);
                    return;
                }
                if (slot == 41) {
                    openShop(player);
                    return;
                }
                int[] ballSlots = {10, 11, 12, 13, 14, 15, 16, 22};
                BallSkin[] balls = BallSkin.values();
                for (int i = 0; i < ballSlots.length && i < balls.length; i++) {
                    if (slot == ballSlots[i]) {
                        handleBallClick(player, profile, balls[i]);
                        return;
                    }
                }
                int[] dunkSlots = {29, 30, 31, 32, 33};
                DunkStyle[] dunks = DunkStyle.values();
                for (int i = 0; i < dunkSlots.length && i < dunks.length; i++) {
                    if (slot == dunkSlots[i]) {
                        handleDunkClick(player, profile, dunks[i]);
                        return;
                    }
                }
            }
            case TAGS_VAULT -> {
                int page = holder.page();
                if (slot == 45 && page > 0) {
                    openTagsVault(player, page - 1);
                    return;
                }
                if (slot == 53) {
                    openTagsVault(player, page + 1);
                    return;
                }
                if (slot == 48) {
                    profile.clearEquippedTags();
                    plugin.statsManager().saveProfileAsync(profile);
                    notifyLiveUiRefresh(player);
                    Msg.send(player, Msg.info("Unequipped your active chat tag."));
                    openTagsVault(player, page);
                    return;
                }
                if (slot == 49) {
                    navigateBack(player);
                    return;
                }
                if (slot == 50) {
                    openCosmetics(player);
                    return;
                }
                List<ChatTag> tags = visibleVaultTags(player, profile);
                int idx = page * 36 + slot;
                if (slot >= 0 && slot < 36 && idx < tags.size()) {
                    handleTagClick(player, profile, tags.get(idx), page);
                }
            }
            case LEADERBOARDS -> {
                if (slot == 4) {
                    openRanksGuide(player);
                    return;
                }
                if (slot == 49) {
                    navigateBack(player);
                    return;
                }
                int[] catSlots = {0, 1, 2, 3, 5, 6, 7, 8};
                LeaderboardCategory[] cats = LeaderboardCategory.values();
                for (int i = 0; i < catSlots.length && i < cats.length; i++) {
                    if (slot == catSlots[i]) {
                        openLeaderboards(player, cats[i]);
                        return;
                    }
                }
            }
            case MATCH_HISTORY, RANKS_GUIDE -> {
                if (slot == 49) {
                    navigateBack(player);
                }
            }
            case FRIENDS_PARTY, FRIENDS_MENU -> {
                if (slot == 5 || slot == 51) {
                    openPartyMenu(player);
                    return;
                }
                if (slot == 47) {
                    openAddFriendOnlineMenu(player);
                    return;
                }
                if (slot == 48) {
                    plugin.socialManager().beginChatPrompt(player, SocialPartyManager.ChatPromptType.ADD_FRIEND_NAME);
                    return;
                }
                if (slot == 49) {
                    navigateBack(player);
                    return;
                }
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() instanceof SkullMeta sm) {
                    OfflinePlayer targetOff = sm.getOwningPlayer();
                    if (targetOff != null) {
                        java.util.UUID tid = targetOff.getUniqueId();
                        String tName = targetOff.getName() != null ? targetOff.getName() : "Player";
                        if (plugin.socialManager().incomingFriendRequests(player.getUniqueId()).contains(tid)) {
                            if (event.isRightClick()) {
                                plugin.socialManager().declineFriendRequest(player, tid, tName);
                            } else {
                                Player onlineReq = Bukkit.getPlayer(tid);
                                if (onlineReq != null) {
                                    plugin.socialManager().sendOrAcceptFriendRequest(player, onlineReq);
                                } else {
                                    profile.addFriend(tid);
                                    PlayerProfile reqProf = plugin.statsManager().getIfPresent(tid);
                                    if (reqProf != null) {
                                        reqProf.addFriend(player.getUniqueId());
                                        plugin.statsManager().saveProfileAsync(reqProf);
                                    }
                                    plugin.socialManager().declineFriendRequest(player, tid, tName);
                                    plugin.statsManager().saveProfileAsync(profile);
                                    Msg.send(player, Msg.success("Accepted friend request from <white>" + tName + "</white>."));
                                }
                            }
                            openFriendsMenu(player);
                            return;
                        }
                        if (event.isRightClick()) {
                            plugin.socialManager().removeFriend(player, tid, tName);
                            openFriendsMenu(player);
                            return;
                        }
                        Player onlineFriend = Bukkit.getPlayer(tid);
                        if (onlineFriend != null && onlineFriend.isOnline()) {
                            if (event.isShiftClick()) {
                                player.closeInventory();
                                player.performCommand("bb duel " + onlineFriend.getName());
                                return;
                            }
                            plugin.socialManager().inviteToParty(player, onlineFriend);
                        } else {
                            Msg.send(player, Msg.error("<white>" + tName + "</white> is currently offline."));
                        }
                        openFriendsMenu(player);
                    }
                }
            }
            case ADD_FRIEND_ONLINE -> {
                if (slot == 49) {
                    openFriendsMenu(player);
                    return;
                }
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() instanceof SkullMeta sm) {
                    OfflinePlayer targetOff = sm.getOwningPlayer();
                    if (targetOff != null) {
                        Player onlineTarget = Bukkit.getPlayer(targetOff.getUniqueId());
                        if (onlineTarget != null && onlineTarget.isOnline()) {
                            if (event.isRightClick()) {
                                plugin.socialManager().inviteToParty(player, onlineTarget);
                            } else {
                                plugin.socialManager().sendOrAcceptFriendRequest(player, onlineTarget);
                            }
                        }
                        openAddFriendOnlineMenu(player);
                    }
                }
            }
            case PARTY_INVITE_ONLINE -> {
                if (slot == 49) {
                    openPartyMenu(player);
                    return;
                }
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() instanceof SkullMeta sm) {
                    OfflinePlayer targetOff = sm.getOwningPlayer();
                    if (targetOff != null) {
                        Player onlineTarget = Bukkit.getPlayer(targetOff.getUniqueId());
                        if (onlineTarget != null && onlineTarget.isOnline()) {
                            plugin.socialManager().inviteToParty(player, onlineTarget);
                        }
                        openPartyInviteMenu(player);
                    }
                }
            }
            case PARTY_MENU -> {
                if (slot == 3) {
                    openFriendsMenu(player);
                    return;
                }
                if (slot == 4 && plugin.socialManager().pendingPartyInviteLeader(player.getUniqueId()) != null) {
                    if (event.isRightClick()) {
                        plugin.socialManager().declinePartyInvite(player);
                    } else {
                        plugin.socialManager().acceptPartyInvite(player, (String) null);
                    }
                    openPartyMenu(player);
                    return;
                }
                if (slot == 13) {
                    if (event.isRightClick()) {
                        plugin.socialManager().setPartyTeamName(player, "reset");
                        openPartyMenu(player);
                    } else {
                        plugin.socialManager().beginChatPrompt(player, SocialPartyManager.ChatPromptType.PARTY_TEAM_NAME);
                    }
                    return;
                }
                if (slot == 31 || slot == 20 || slot == 21 || slot == 23 || slot == 24) {
                    ItemStack clicked = event.getCurrentItem();
                    if (clicked != null && clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() instanceof SkullMeta sm) {
                        OfflinePlayer targetOff = sm.getOwningPlayer();
                        if (targetOff != null && !targetOff.getUniqueId().equals(player.getUniqueId())) {
                            if (event.isRightClick()) {
                                plugin.socialManager().kickFromParty(player, targetOff.getUniqueId());
                            } else {
                                plugin.socialManager().promotePartyLeader(player, targetOff.getUniqueId());
                            }
                            openPartyMenu(player);
                        }
                        return;
                    }
                    openPartyInviteMenu(player);
                    return;
                }
                if (slot == 47) {
                    openPlayModes(player);
                    return;
                }
                if (slot == 48) {
                    plugin.socialManager().leaveParty(player);
                    openPartyMenu(player);
                    return;
                }
                if (slot == 49) {
                    navigateBack(player);
                }
            }
            case MAP_STUDIO -> {
                if (!player.isOp()) {
                    player.closeInventory();
                    return;
                }
                handleMapStudioClick(player, slot, event.isRightClick(), event.isShiftClick());
            }
            case SETTINGS -> {
                if (slot == 40) {
                    navigateBack(player);
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
                notifyLiveUiRefresh(player);
                openSettings(player);
            }
        }
    }

    private void handleShopEntryPurchase(Player player, PlayerProfile profile, ShopItemEntry entry, ShopCategory cat, int page) {
        int price = entry.priceCoins();
        if (!profile.spendCoins(price)) {
            int missing = price - profile.coins();
            Msg.send(player, Msg.error("Not enough Coins! You need <#FDE047>" + missing + " more Coins</#FDE047>."));
            Msg.playPersonalPackSound(player, "game.buzzer", 0.55f, 1.35f);
            return;
        }

        if (entry.ballSkin() != null) {
            BallSkin skin = entry.ballSkin();
            profile.unlockBall(skin);
            profile.setSelectedBallSkin(skin);
            Msg.send(player, Msg.success("Purchased & equipped " + skin.displayNameMm() + " for <#FDE047>" + price + " Coins</#FDE047>!"));
        } else if (entry.dunkStyle() != null) {
            DunkStyle dunk = entry.dunkStyle();
            profile.unlockDunk(dunk);
            profile.setSelectedDunkStyle(dunk);
            Msg.send(player, Msg.success("Purchased & equipped " + dunk.displayNameMm() + " for <#FDE047>" + price + " Coins</#FDE047>!"));
        } else if (entry.chatTag() != null) {
            ChatTag tag = entry.chatTag();
            profile.unlockTag(tag);
            profile.setSelectedTag(tag);
            Msg.send(player, Msg.success("Purchased & equipped <!shadow><white>" + tag.glyph() + "</white></!shadow> <white>" + tag.displayName() + "</white> for <#FDE047>" + price + " Coins</#FDE047>!"));
        }

        plugin.statsManager().saveProfileAsync(profile);
        notifyLiveUiRefresh(player);
        Msg.playPersonalPackSound(player, "crowd.cheer", 0.75f, 1.25f);
        openShop(player, cat, page);
    }

    private void handleMapStudioClick(Player player, int slot, boolean rightClick, boolean shiftClick) {
        switch (slot) {
            case 19 -> {
                var s = plugin.mapEditor().getOrBindSession(player, null);
                if (s == null) {
                    String nextId = "court" + (plugin.arenaManager().allArenas().size() + 1);
                    plugin.mapEditor().createOrSelectCustomMap(player, nextId, "Court " + (plugin.arenaManager().allArenas().size() + 1));
                } else {
                    plugin.mapEditor().giveWand(player, s.arenaId);
                }
                player.closeInventory();
                Msg.send(player, Msg.success("Equipped Arena Setup Wand! Left-Click Floor Corner #1, Right-Click Floor Corner #2, then Shift+Right-Click Air to re-open Studio."));
            }
            case 21 -> {
                fr.bosketball.arena.Arena built = plugin.mapEditor().autoSetupFullCourt(player);
                Msg.send(player, Msg.success("Auto-built full court <white><bold>" + built.id() + "</bold></white> (Hoops + Backboards + White Lines + Scoreboard + Barriers)!"));
                Msg.playPersonalPackSound(player, "game.green_release", 0.9f, 1.15f);
                openMapStudio(player);
            }
            case 23 -> {
                player.closeInventory();
                player.performCommand("bb map hoop " + (rightClick ? "frost" : "ignis"));
            }
            case 25 -> {
                var s = plugin.mapEditor().getOrBindSession(player, null);
                fr.bosketball.arena.Arena arena = s != null ? plugin.arenaManager().getArena(s.arenaId) : null;
                if (arena == null) {
                    arena = plugin.mapEditor().autoSetupFullCourt(player);
                } else if (rightClick) {
                    fr.bosketball.arena.Arena.CourtLinesPreset[] presets = fr.bosketball.arena.Arena.CourtLinesPreset.values();
                    int next = (arena.courtLinesPreset().ordinal() + 1) % presets.length;
                    arena.setCourtLinesEnabled(true);
                    arena.setCourtLinesPreset(presets[next]);
                    arena.refreshCourtLines();
                    plugin.arenaManager().save();
                } else {
                    arena.setCourtLinesEnabled(!arena.isCourtLinesEnabled());
                    arena.refreshCourtLines();
                    plugin.arenaManager().save();
                }
                openMapStudio(player);
            }
            case 29 -> {
                player.closeInventory();
                player.performCommand("bb map jumbotron " + (rightClick ? "off" : "here"));
            }
            case 31 -> {
                player.closeInventory();
                player.performCommand("bb map setspawn " + (rightClick ? "frost" : "ignis") + " 1");
            }
            case 33 -> {
                if (shiftClick) {
                    player.closeInventory();
                    player.performCommand("bb arena build");
                } else {
                    Msg.send(player, Msg.info("Shift-Click that button to generate a full stadium structure at your position."));
                }
            }
            case 47 -> {
                boolean active = plugin.mapEditor().togglePreview(player);
                Msg.send(player, Msg.info("3D Particle Blueprint: " + (active ? "<#4ADE80>ON</#4ADE80>" : "<#F87171>OFF</#F87171>")));
                openMapStudio(player);
            }
            case 49 -> navigateBack(player);
            case 51 -> {
                var s = plugin.mapEditor().getOrBindSession(player, null);
                fr.bosketball.arena.Arena arena = s != null ? plugin.arenaManager().getArena(s.arenaId) : plugin.mapEditor().autoSetupFullCourt(player);
                if (arena != null) {
                    arena.spawnDisplays();
                    plugin.arenaManager().save();
                    if (plugin.lockerRoom3dManager() != null && plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
                        plugin.lockerRoom3dManager().close(player, true);
                    } else {
                        player.closeInventory();
                    }
                    player.performCommand("bb practice " + arena.id());
                }
            }
            default -> {
            }
        }
    }

    private void handleTagClick(Player player, PlayerProfile profile, ChatTag tag, int page) {
        if (tag.isSpecialUnpurchasable()) {
            if (!tag.canBeUsedBy(profile, player.getName(), false)) {
                Msg.send(player, Msg.error("This exclusive tag is not available for purchase."));
                return;
            }
            if (profile.selectedTag() == tag) {
                profile.clearEquippedTags();
                Msg.send(player, Msg.info("Unequipped <white>" + tag.displayName() + "</white>."));
            } else {
                profile.setSelectedTag(tag);
                Msg.send(player, Msg.success("Equipped exclusive tag <!shadow><white>" + tag.glyph() + "</white></!shadow> <white>" + tag.displayName() + "</white>."));
            }
            plugin.statsManager().saveProfileAsync(profile);
            notifyLiveUiRefresh(player);
            openTagsVault(player, page);
            return;
        }

        boolean unlocked = tag.canBeUsedBy(profile, player.getName(), player.hasPermission("bosketball.tags.all"));
        if (!unlocked) {
            int price = tag.priceCoins();
            if (!profile.spendCoins(price)) {
                int missing = price - profile.coins();
                Msg.send(player, Msg.error("Not enough Coins! You need <#FDE047>" + missing + " more Coins</#FDE047> to unlock <white>" + tag.displayName() + "</white>."));
                Msg.playPersonalPackSound(player, "game.buzzer", 0.55f, 1.35f);
                return;
            }
            profile.unlockTag(tag);
            profile.setSelectedTag(tag);
            plugin.statsManager().saveProfileAsync(profile);
            notifyLiveUiRefresh(player);
            Msg.send(player, Msg.success("Unlocked & equipped <!shadow><white>" + tag.glyph() + "</white></!shadow> <white>" + tag.displayName() + "</white> for <#FDE047>" + price + " Coins</#FDE047>!"));
            Msg.playPersonalPackSound(player, "crowd.cheer", 0.7f, 1.25f);
            openTagsVault(player, page);
            return;
        }

        if (profile.selectedTag() == tag) {
            profile.clearEquippedTags();
            Msg.send(player, Msg.info("Unequipped <white>" + tag.displayName() + "</white>."));
        } else {
            profile.setSelectedTag(tag);
            Msg.send(player, Msg.success("Equipped tag <!shadow><white>" + tag.glyph() + "</white></!shadow> <white>" + tag.displayName() + "</white>."));
        }
        plugin.statsManager().saveProfileAsync(profile);
        notifyLiveUiRefresh(player);
        openTagsVault(player, page);
    }

    private void handleBallClick(Player player, PlayerProfile profile, BallSkin skin) {
        boolean unlocked = profile.hasUnlockedBall(skin) || player.hasPermission("bosketball.cosmetics.all");
        if (!unlocked) {
            int price = skin.priceCoins();
            if (!profile.spendCoins(price)) {
                int missing = price - profile.coins();
                Msg.send(player, Msg.error("Not enough Coins! You need <#FDE047>" + missing + " more Coins</#FDE047> to unlock " + skin.displayNameMm() + "."));
                Msg.playPersonalPackSound(player, "game.buzzer", 0.55f, 1.35f);
                return;
            }
            profile.unlockBall(skin);
            profile.setSelectedBallSkin(skin);
            plugin.statsManager().saveProfileAsync(profile);
            notifyLiveUiRefresh(player);
            Msg.send(player, Msg.success("Purchased & equipped " + skin.displayNameMm() + " for <#FDE047>" + price + " Coins</#FDE047>!"));
            Msg.playPersonalPackSound(player, "crowd.cheer", 0.7f, 1.25f);
            openCosmetics(player);
            return;
        }

        profile.setSelectedBallSkin(skin);
        plugin.statsManager().saveProfileAsync(profile);
        notifyLiveUiRefresh(player);
        Msg.send(player, Msg.success("Equipped " + skin.displayNameMm() + "."));
        openCosmetics(player);
    }

    private void handleDunkClick(Player player, PlayerProfile profile, DunkStyle dunk) {
        boolean unlocked = profile.hasUnlockedDunk(dunk) || player.hasPermission("bosketball.cosmetics.all");
        if (!unlocked) {
            int price = dunk.priceCoins();
            if (!profile.spendCoins(price)) {
                int missing = price - profile.coins();
                Msg.send(player, Msg.error("Not enough Coins! You need <#FDE047>" + missing + " more Coins</#FDE047> to unlock " + dunk.displayNameMm() + "."));
                Msg.playPersonalPackSound(player, "game.buzzer", 0.55f, 1.35f);
                return;
            }
            profile.unlockDunk(dunk);
            profile.setSelectedDunkStyle(dunk);
            plugin.statsManager().saveProfileAsync(profile);
            notifyLiveUiRefresh(player);
            Msg.send(player, Msg.success("Purchased & equipped " + dunk.displayNameMm() + " for <#FDE047>" + price + " Coins</#FDE047>!"));
            Msg.playPersonalPackSound(player, "crowd.cheer", 0.7f, 1.25f);
            openCosmetics(player);
            return;
        }

        profile.setSelectedDunkStyle(dunk);
        plugin.statsManager().saveProfileAsync(profile);
        notifyLiveUiRefresh(player);
        Msg.send(player, Msg.success("Equipped " + dunk.displayNameMm() + "."));
        openCosmetics(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BosketHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof BosketHolder)) {
            return;
        }
        if (plugin.lockerRoom3dManager() == null || !plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (plugin.lockerRoom3dManager() == null || !plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
                return;
            }
            if (plugin.gameManager().isInMatch(player.getUniqueId())) {
                return;
            }
            if (plugin.socialManager() != null && plugin.socialManager().hasPendingChatPrompt(player.getUniqueId())) {
                return;
            }
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BosketHolder) {
                return;
            }
            plugin.lockerRoom3dManager().returnFromSubMenu(player);
        }, 1L);
    }

    private void notifyLiveUiRefresh(Player player) {
        if (plugin.sidebarManager() != null) {
            plugin.sidebarManager().clearPlayerCache(player.getUniqueId());
        }
        if (plugin.tabListManager() != null) {
            plugin.tabListManager().updateAll(plugin.gameManager());
        }
        if (plugin.lockerRoom3dManager() != null && plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            plugin.lockerRoom3dManager().refreshPlayerStage(player, true);
        }
    }

    private void navigateBack(Player player) {
        if (plugin.lockerRoom3dManager() != null && plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            player.closeInventory();
            plugin.lockerRoom3dManager().returnFromSubMenu(player);
            return;
        }
        openMainHub(player);
    }

    private void joinModeFromGui(Player player, GameModeType mode) {
        boolean inLocker = plugin.lockerRoom3dManager() != null && plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId());
        if (mode == GameModeType.THREE_POINT_CONTEST) {
            if (inLocker) {
                plugin.lockerRoom3dManager().close(player, true);
            } else {
                player.closeInventory();
            }
            plugin.gameManager().joinQueue(player, mode);
            return;
        }
        player.closeInventory();
        if (plugin.gameManager().getQueuedMode(player.getUniqueId()) == mode) {
            plugin.gameManager().leaveQueue(player, true);
        } else {
            plugin.gameManager().joinQueue(player, mode);
        }
        if (inLocker) {
            plugin.lockerRoom3dManager().returnFromSubMenu(player);
        }
    }

    private ItemStack backButton(Player player) {
        boolean inLocker = player != null && plugin.lockerRoom3dManager() != null && plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId());
        if (inLocker) {
            return customIcon("icon_back", "<#38BDF8><bold>◂ Back to Locker Room</bold></#38BDF8>", List.of("<gray>Return directly to your 3D Locker Room</gray>"));
        }
        return customIcon("icon_back", "<#FBBF24><bold>◂ Back</bold></#FBBF24>", List.of("<gray>Return to BosketBall menu</gray>"));
    }

    private static ItemStack cachedInvisibleFiller;

    private static ItemStack invisibleFiller() {
        if (cachedInvisibleFiller == null) {
            ItemStack invisible = new ItemStack(Material.PAPER);
            ItemMeta meta = invisible.getItemMeta();
            if (meta != null) {
                meta.setItemModel(new NamespacedKey("bosketball", "ui_invisible"));
                meta.setHideTooltip(true);
                invisible.setItemMeta(meta);
            }
            cachedInvisibleFiller = invisible;
        }
        return cachedInvisibleFiller;
    }

    private void fillBackdrop(Inventory inv) {
        ItemStack invisible = invisibleFiller();
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
            fr.bosketball.model.Rank compRank = p.competitiveRank();
            fr.bosketball.model.Rank nextRank = compRank.nextCompetitiveRank();
            String nextLine = nextRank != null
                ? "<gray>Next Rank:</gray> <#FBBF24>" + p.elo() + "/" + nextRank.minElo() + " ELO</#FBBF24> <dark_gray>(→ " + nextRank.plainDisplayName() + ")</dark_gray>"
                : "<gray>Rank Progress:</gray> <#FDE047>MAX TIER (MVP)</#FDE047>";
            meta.displayName(Msg.parse("<#FF8C1A><bold>" + player.getName() + "</bold></#FF8C1A> <!shadow><white>" + p.effectiveRank().pillGlyph() + "</white></!shadow>"));
            meta.lore(List.of(
                Msg.parse("<gray>Competitive Tier:</gray> <white>" + compRank.plainDisplayName() + "</white>"),
                Msg.parse(nextLine),
                Msg.parse("<gray>Coins:</gray> " + coinStr(p.coins())),
                Msg.parse("<gray>Wins:</gray> <#4ADE80>" + p.wins() + "</#4ADE80> <dark_gray>•</dark_gray> <gray>Win Rate:</gray> <white>" + String.format("%.1f%%", p.winRatePercent()) + "</white>"),
                Msg.parse("<gray>Points:</gray> <white>" + p.points() + "</white> <dark_gray>•</dark_gray> <gray>3PT:</gray> <aqua>" + p.threePointers() + "</aqua> <dark_gray>•</dark_gray> <gray>Dunks:</gray> <red>" + p.dunks() + "</red>"),
                Msg.parse("<gray>Assists:</gray> <#4ADE80>" + p.assists() + "</#4ADE80> <dark_gray>•</dark_gray> <gray>Steals:</gray> <light_purple>" + p.steals() + "</light_purple>"),
                Msg.parse("<gray>Playtime:</gray> <white>" + p.formattedPlayTime() + "</white>"),
                Msg.parse(""),
                Msg.parse("<yellow>▸ Click to view ELO Ranks & Badges Guide</yellow>")
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
            String topEloGlyph = (cat == LeaderboardCategory.ELO && place >= 1 && place <= 3)
                ? " <!shadow><white>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, place) + "</white></!shadow>"
                : "";
            meta.displayName(Msg.parse(medal + p.lastKnownName() + "</bold> <!shadow><white>" + p.effectiveRank().pillGlyph() + "</white></!shadow>" + topEloGlyph));
            meta.lore(List.of(
                Msg.parse("<gray>" + cat.label() + ":</gray> <#FDE047><bold>" + cat.extract(p) + "</bold></#FDE047>"),
                Msg.parse("<gray>ELO:</gray> <#FBBF24>" + p.elo() + "</#FBBF24> <dark_gray>•</dark_gray> <gray>Wins:</gray> <#4ADE80>" + p.wins() + "</#4ADE80>")
            ));
            skull.setItemMeta(meta);
        }
        return skull;
    }
}
