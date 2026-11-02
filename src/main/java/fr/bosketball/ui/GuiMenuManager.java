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
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
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
        SETTINGS
    }

    public enum ShopCategory {
        ALL("All Unowned"),
        BALLS("Basketballs"),
        DUNKS("Dunk Styles"),
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

        inv.setItem(33, customIcon(
            "icon_friends",
            "<#34D399><bold>Party & Friends (" + groupSize + "/4)</bold></#34D399>",
            List.of(
                "<gray>Party Size:</gray> <#4ADE80>" + groupSize + "/4 Players</#4ADE80>",
                "<gray>Friends Online:</gray> <aqua>" + onlineFriends + "</aqua><gray>/" + profile.friends().size() + "</gray>",
                "",
                "<green>▸ Manage Party</green>"
            )
        ));

        // Row 5: Discord, Settings, Controls / Leave Match
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
                "<#FBBF24><bold>Controls</bold></#FBBF24>",
                List.of(
                    "<yellow>Hold Right-Click</yellow> <gray>— Aim & Release Jump Shot</gray>",
                    "<yellow>Left-Click (In Paint)</yellow> <gray>— Aerial Slam Dunk</gray>",
                    "<yellow>Slot 2 / Q</yellow> <gray>— Pass to Teammate</gray>",
                    "<yellow>Slot 3 / 4</yellow> <gray>— Crossover / Dunk Style</gray>"
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

        inv.setItem(40, backButton());

        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.25f);
    }

    // -------------------------------------------------------------------------
    // 2B. DEDICATED UNOWNED-ONLY SHOP (Basketballs, Dunk Styles & Chat Tags)
    // -------------------------------------------------------------------------
    public static List<ShopItemEntry> collectUnownedShopItems(PlayerProfile profile, ShopCategory filter) {
        List<ShopItemEntry> list = new ArrayList<>();
        if (profile == null) {
            return list;
        }
        ShopCategory cat = filter != null ? filter : ShopCategory.ALL;

        if (cat == ShopCategory.ALL || cat == ShopCategory.BALLS) {
            for (BallSkin skin : BallSkin.values()) {
                if (!profile.hasUnlockedBall(skin) && skin.priceCoins() > 0) {
                    list.add(new ShopItemEntry(ShopCategory.BALLS, skin, null, null, skin.priceCoins()));
                }
            }
        }
        if (cat == ShopCategory.ALL || cat == ShopCategory.DUNKS) {
            for (DunkStyle dunk : DunkStyle.values()) {
                if (!profile.hasUnlockedDunk(dunk) && dunk.priceCoins() > 0) {
                    list.add(new ShopItemEntry(ShopCategory.DUNKS, null, dunk, null, dunk.priceCoins()));
                }
            }
        }
        if (cat == ShopCategory.ALL || cat == ShopCategory.TAGS) {
            for (ChatTag tag : ChatTag.values()) {
                if (!tag.isUnlocked(profile) && tag.priceCoins() > 0) {
                    list.add(new ShopItemEntry(ShopCategory.TAGS, null, null, tag, tag.priceCoins()));
                }
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

    public void openShop(Player player) {
        openShop(player, ShopCategory.ALL, 0);
    }

    public void openShop(Player player, ShopCategory category, int requestedPage) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        ShopCategory cat = category != null ? category : ShopCategory.ALL;
        List<ShopItemEntry> unowned = collectUnownedShopItems(profile, cat);

        int perPage = SHOP_GRID_SLOTS.length;
        int totalPages = Math.max(1, (unowned.size() + perPage - 1) / perPage);
        int page = Math.max(0, Math.min(totalPages - 1, requestedPage));

        BosketHolder holder = new BosketHolder(MenuType.SHOP, cat, page);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            guiTitle(Glyphs.ICON_CROWN, "<#FBBF24><bold>Shop • " + cat.label() + " (" + unowned.size() + ")</bold></#FBBF24>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        // Top Filter Bar (Slots 1, 2, 3, 4) + Balance Card (Slot 7)
        ShopCategory[] filters = ShopCategory.values();
        String[] filterIcons = {"icon_chest", "icon_arena", "icon_dunk", "icon_tags"};
        for (int i = 0; i < filters.length; i++) {
            ShopCategory f = filters[i];
            boolean active = (f == cat);
            int countInFilter = collectUnownedShopItems(profile, f).size();
            inv.setItem(1 + i, customIcon(
                active ? "icon_star_shield" : filterIcons[i],
                (active ? "<#4ADE80><bold>● " : "<#CBD5E1><bold>") + f.label() + " (" + countInFilter + ")</bold>" + (active ? "</#4ADE80>" : "</#CBD5E1>"),
                List.of(
                    "<gray>Shows ONLY items you do not own yet.</gray>",
                    "",
                    active ? "<#4ADE80>✔ Selected Filter</#4ADE80>" : "<yellow>▸ Click to filter</yellow>"
                )
            ));
        }

        inv.setItem(7, customIcon(
            "icon_chest",
            "<#FDE047><bold>Your Balance</bold></#FDE047>",
            List.of(
                coinStr(profile.coins()),
                "<gray>Score points & win matches to earn Coins!</gray>"
            )
        ));

        if (unowned.isEmpty()) {
            inv.setItem(22, customIcon(
                "icon_podium",
                "<#4ADE80><bold>Everything Unlocked!</bold></#4ADE80>",
                List.of(
                    "<gray>You already own every item in</gray>",
                    "<white>" + cat.label() + "</white><gray>!</gray>",
                    "",
                    "<aqua>Use the buttons below to equip your items.</aqua>"
                )
            ));
        } else {
            int startIdx = page * perPage;
            for (int i = 0; i < perPage && (startIdx + i) < unowned.size(); i++) {
                ShopItemEntry entry = unowned.get(startIdx + i);
                boolean canAfford = profile.coins() >= entry.priceCoins();
                String buyAction = canAfford
                    ? "<#4ADE80><bold>▸ Click to Buy (" + entry.priceCoins() + " Coins)</bold></#4ADE80>"
                    : "<#F87171>▸ Need " + (entry.priceCoins() - profile.coins()) + " more Coins</#F87171>";

                ItemStack card;
                if (entry.ballSkin() != null) {
                    BallSkin skin = entry.ballSkin();
                    card = skin.createItemStack();
                    ItemMeta meta = card.getItemMeta();
                    if (meta != null) {
                        meta.displayName(Msg.parse(skin.displayNameMm()));
                        meta.lore(List.of(
                            Msg.parse("<dark_gray>Basketball Skin</dark_gray>"),
                            Msg.parse("<gray>" + skin.loreLine() + "</gray>"),
                            Msg.parse("<gray>Price:</gray> " + coinStr(entry.priceCoins())),
                            Msg.parse(""),
                            Msg.parse(buyAction)
                        ));
                        card.setItemMeta(meta);
                    }
                } else if (entry.dunkStyle() != null) {
                    DunkStyle dunk = entry.dunkStyle();
                    card = customIcon(
                        "icon_dunk",
                        dunk.displayNameMm(),
                        List.of(
                            "<dark_gray>Aerial Dunk Style</dark_gray>",
                            "<gray>Price:</gray> " + coinStr(entry.priceCoins()),
                            "",
                            buyAction
                        )
                    );
                } else {
                    ChatTag tag = entry.chatTag();
                    card = customIcon(
                        "icon_tags",
                        "<!shadow><white>" + tag.glyph() + "</white></!shadow> <#F8FAFC><bold>" + tag.displayName() + "</bold></#F8FAFC>",
                        List.of(
                            "<dark_gray>Chat & [TAB] Tag • " + tag.tierLabel() + "</dark_gray>",
                            "<gray>Preview:</gray> <!shadow><white>" + profile.effectiveRank().pillGlyph() + "</white></!shadow> <!shadow><white>" + tag.glyph() + "</white></!shadow> <white>" + player.getName() + "</white>",
                            "<gray>Price:</gray> " + coinStr(entry.priceCoins()),
                            "",
                            buyAction
                        )
                    );
                }
                inv.setItem(SHOP_GRID_SLOTS[i], card);
            }
        }

        if (page > 0) {
            inv.setItem(45, customIcon(
                "icon_prev",
                "<yellow><bold>◂ Previous Page</bold></yellow>",
                List.of("<gray>Page " + page + " of " + totalPages + "</gray>")
            ));
        }
        inv.setItem(48, customIcon(
            "icon_dunk",
            "<#F472B6><bold>My Basketballs & Dunks</bold></#F472B6>",
            List.of("<gray>Equip your owned Basketballs & Dunks</gray>", "", "<yellow>▸ Open Locker</yellow>")
        ));
        inv.setItem(49, backButton());
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

            inv.setItem(dunkSlots[i], customIcon(
                selected ? "icon_star_shield" : "icon_dunk",
                dunk.displayNameMm(),
                List.of(
                    "<gray>Aerial Dunk Animation</gray>",
                    unlocked ? "<#4ADE80>Unlocked</#4ADE80>" : "<gray>Price:</gray> " + coinStr(dunk.priceCoins()),
                    "",
                    actionLine
                )
            ));
        }

        inv.setItem(39, customIcon(
            "icon_tags",
            "<#FBBF24><bold>Tag Shop</bold></#FBBF24>",
            List.of("<gray>Browse purchasable chat tags</gray>", "", "<yellow>▸ Open Tag Shop</yellow>")
        ));
        inv.setItem(40, backButton());
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
        ChatTag[] tags = ChatTag.values();
        int perPage = 36;
        int totalPages = Math.max(1, (tags.length + perPage - 1) / perPage);
        int page = Math.max(0, Math.min(totalPages - 1, requestedPage));

        BosketHolder holder = new BosketHolder(MenuType.TAGS_VAULT, page);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            guiTitle(Glyphs.ICON_CROWN, "<#FBBF24><bold>Tag Shop • Page " + (page + 1) + "/" + totalPages + "</bold></#FBBF24>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        int startIdx = page * perPage;

        for (int slot = 0; slot < perPage && (startIdx + slot) < tags.length; slot++) {
            ChatTag tag = tags[startIdx + slot];
            boolean equipped = profile.selectedTag() == tag;
            boolean unlocked = tag.isUnlocked(profile) || player.hasPermission("bosketball.tags.all");

            String status = equipped
                ? "<#4ADE80><bold>✔ Equipped (Click to remove)</bold></#4ADE80>"
                : (unlocked
                    ? "<yellow>▸ Click to equip</yellow>"
                    : "<#FDE047>▸ Buy for " + tag.priceCoins() + " Coins</#FDE047>");

            inv.setItem(slot, customIcon(
                equipped ? "icon_star_shield" : "icon_tags",
                "<!shadow><white>" + tag.glyph() + "</white></!shadow> <#F8FAFC><bold>" + tag.displayName() + "</bold></#F8FAFC>",
                List.of(
                    "<gray>Tier:</gray> <white>" + tag.tierLabel() + "</white>",
                    unlocked ? "<#4ADE80>Owned</#4ADE80>" : "<gray>Price:</gray> " + coinStr(tag.priceCoins()),
                    "<gray>Preview:</gray> <!shadow><white>" + profile.effectiveRank().pillGlyph() + "</white></!shadow> <!shadow><white>" + tag.glyph() + "</white></!shadow> <white>" + player.getName() + "</white>",
                    "",
                    status
                )
            ));
        }

        if (page > 0) {
            inv.setItem(45, customIcon(
                "icon_prev",
                "<yellow><bold>◂ Previous Page</bold></yellow>",
                List.of("<gray>Page " + page + " of " + totalPages + "</gray>")
            ));
        }
        inv.setItem(47, customIcon(
            "icon_chest",
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
        inv.setItem(49, backButton());
        inv.setItem(50, customIcon(
            "icon_dunk",
            "<#F472B6><bold>Basketballs & Dunks</bold></#F472B6>",
            List.of("<gray>Switch to Basketball & Dunk Shop</gray>")
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

        inv.setItem(49, backButton());
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
    }

    // -------------------------------------------------------------------------
    // 7. PARTY & FRIENDS
    // -------------------------------------------------------------------------
    public void openFriendsAndParty(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.FRIENDS_PARTY);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            guiTitle(Glyphs.ICON_SHIELD, "<#34D399><bold>Party & Friends</bold></#34D399>")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        SocialPartyManager.Party party = plugin.socialManager().getParty(player.getUniqueId());
        int partySize = plugin.socialManager().partySizeOrOne(player.getUniqueId());
        inv.setItem(4, customIcon(
            "icon_friends",
            "<#4ADE80><bold>Current Party (" + partySize + "/4)</bold></#4ADE80>",
            List.of(
                "<gray>Click an online player below</gray>",
                "<gray>to invite them to your party.</gray>"
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
                meta.displayName(Msg.parse("<#38BDF8><bold>" + other.getName() + "</bold></#38BDF8>"));
                meta.lore(List.of(
                    Msg.parse("<gray>Friend:</gray> " + (isFriend ? "<#4ADE80>Yes</#4ADE80>" : "<gray>No</gray>")),
                    Msg.parse("<gray>Party:</gray> " + (inMyParty ? "<#4ADE80>In your party</#4ADE80>" : "<gray>Not in party</gray>")),
                    Msg.parse(""),
                    Msg.parse("<yellow>▸ Left-Click: Invite to Party</yellow>"),
                    Msg.parse("<aqua>▸ Right-Click: Toggle Friend</aqua>")
                ));
                skull.setItemMeta(meta);
            }
            inv.setItem(playerSlots[i], skull);
        }

        if (party != null) {
            inv.setItem(48, customIcon(
                "icon_leave",
                "<#F87171><bold>Leave Party</bold></#F87171>",
                List.of("<gray>Leave or disband your current party</gray>")
            ));
        }
        inv.setItem(49, backButton());
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
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
        inv.setItem(24, settingToggle("icon_friends", "Party Invites", "Allow incoming /bb party invitations", st.partyInvites()));

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
                    case 33 -> openFriendsAndParty(player);
                    case 39 -> {
                        player.closeInventory();
                        Msg.sendDiscordCard(player, plugin.settings().links().discord());
                    }
                    case 40 -> openSettings(player);
                    case 41 -> {
                        if (plugin.gameManager().isInMatch(player.getUniqueId()) || plugin.gameManager().isQueued(player.getUniqueId())) {
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
                    case 31 -> {
                        player.closeInventory();
                        plugin.gameManager().spectateFirstActiveMatch(player);
                    }
                    case 33 -> {
                        player.closeInventory();
                        plugin.gameManager().startPractice(player);
                    }
                    case 40 -> openMainHub(player);
                }
            }
            case SHOP -> {
                ShopCategory cat = holder.shopCategory();
                int page = holder.page();
                if (slot >= 1 && slot <= 4) {
                    ShopCategory[] filters = ShopCategory.values();
                    openShop(player, filters[slot - 1], 0);
                    return;
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
                    openMainHub(player);
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
                for (int i = 0; i < SHOP_GRID_SLOTS.length; i++) {
                    if (slot == SHOP_GRID_SLOTS[i]) {
                        List<ShopItemEntry> unowned = collectUnownedShopItems(profile, cat);
                        int idx = page * SHOP_GRID_SLOTS.length + i;
                        if (idx >= 0 && idx < unowned.size()) {
                            handleShopEntryPurchase(player, profile, unowned.get(idx), cat, page);
                        }
                        return;
                    }
                }
            }
            case COSMETICS -> {
                if (slot == 40) {
                    openMainHub(player);
                    return;
                }
                if (slot == 39) {
                    openTagsVault(player, 0);
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
                    Msg.send(player, Msg.info("Unequipped your active chat tag."));
                    openTagsVault(player, page);
                    return;
                }
                if (slot == 49) {
                    openMainHub(player);
                    return;
                }
                if (slot == 50) {
                    openCosmetics(player);
                    return;
                }
                ChatTag[] tags = ChatTag.values();
                int idx = page * 36 + slot;
                if (slot >= 0 && slot < 36 && idx < tags.length) {
                    handleTagClick(player, profile, tags[idx], page);
                }
            }
            case LEADERBOARDS -> {
                if (slot == 49) {
                    openMainHub(player);
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
                    OfflinePlayer targetOff = sm.getOwningPlayer();
                    if (targetOff != null && targetOff.getName() != null) {
                        if (event.isRightClick()) {
                            if (profile.isFriend(targetOff.getUniqueId())) {
                                profile.removeFriend(targetOff.getUniqueId());
                                Msg.send(player, Msg.info("Removed <white>" + targetOff.getName() + "</white> from friends."));
                            } else {
                                profile.addFriend(targetOff.getUniqueId());
                                Msg.send(player, Msg.success("Added <white>" + targetOff.getName() + "</white> to friends."));
                            }
                            plugin.statsManager().saveProfileAsync(profile);
                        } else {
                            Player onlineTarget = Bukkit.getPlayer(targetOff.getUniqueId());
                            if (onlineTarget != null) {
                                plugin.socialManager().inviteToParty(player, onlineTarget);
                            }
                        }
                        openFriendsAndParty(player);
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
        plugin.sidebarManager().clearPlayerCache(player.getUniqueId());
        Msg.playPersonalPackSound(player, "crowd.cheer", 0.75f, 1.25f);
        openShop(player, cat, page);
    }

    private void handleTagClick(Player player, PlayerProfile profile, ChatTag tag, int page) {
        boolean unlocked = tag.isUnlocked(profile) || player.hasPermission("bosketball.tags.all");
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
            plugin.sidebarManager().clearPlayerCache(player.getUniqueId());
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
            plugin.sidebarManager().clearPlayerCache(player.getUniqueId());
            Msg.send(player, Msg.success("Purchased & equipped " + skin.displayNameMm() + " for <#FDE047>" + price + " Coins</#FDE047>!"));
            Msg.playPersonalPackSound(player, "crowd.cheer", 0.7f, 1.25f);
            openCosmetics(player);
            return;
        }

        profile.setSelectedBallSkin(skin);
        plugin.statsManager().saveProfileAsync(profile);
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
            plugin.sidebarManager().clearPlayerCache(player.getUniqueId());
            Msg.send(player, Msg.success("Purchased & equipped " + dunk.displayNameMm() + " for <#FDE047>" + price + " Coins</#FDE047>!"));
            Msg.playPersonalPackSound(player, "crowd.cheer", 0.7f, 1.25f);
            openCosmetics(player);
            return;
        }

        profile.setSelectedDunkStyle(dunk);
        plugin.statsManager().saveProfileAsync(profile);
        Msg.send(player, Msg.success("Equipped " + dunk.displayNameMm() + "."));
        openCosmetics(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BosketHolder) {
            event.setCancelled(true);
        }
    }

    private void joinModeFromGui(Player player, GameModeType mode) {
        player.closeInventory();
        if (plugin.gameManager().getQueuedMode(player.getUniqueId()) == mode) {
            plugin.gameManager().leaveQueue(player, true);
        } else {
            plugin.gameManager().joinQueue(player, mode);
        }
    }

    private ItemStack backButton() {
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
            meta.displayName(Msg.parse("<#FF8C1A><bold>" + player.getName() + "</bold></#FF8C1A> <!shadow><white>" + p.effectiveRank().pillGlyph() + "</white></!shadow>"));
            meta.lore(List.of(
                Msg.parse("<gray>Coins:</gray> " + coinStr(p.coins())),
                Msg.parse("<gray>ELO:</gray> <#FBBF24>" + p.elo() + "</#FBBF24> <dark_gray>•</dark_gray> <gray>Wins:</gray> <#4ADE80>" + p.wins() + "</#4ADE80> <gray>(" + String.format("%.1f%%", p.winRatePercent()) + ")</gray>"),
                Msg.parse("<gray>Points:</gray> <white>" + p.points() + "</white> <dark_gray>•</dark_gray> <gray>3PT:</gray> <aqua>" + p.threePointers() + "</aqua> <dark_gray>•</dark_gray> <gray>Dunks:</gray> <red>" + p.dunks() + "</red>"),
                Msg.parse("<gray>Assists:</gray> <#4ADE80>" + p.assists() + "</#4ADE80> <dark_gray>•</dark_gray> <gray>Steals:</gray> <light_purple>" + p.steals() + "</light_purple>"),
                Msg.parse("<gray>Playtime:</gray> <white>" + p.formattedPlayTime() + "</white>")
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
                Msg.parse("<gray>" + cat.label() + ":</gray> <#FDE047><bold>" + cat.extract(p) + "</bold></#FDE047>"),
                Msg.parse("<gray>ELO:</gray> <#FBBF24>" + p.elo() + "</#FBBF24> <dark_gray>•</dark_gray> <gray>Wins:</gray> <#4ADE80>" + p.wins() + "</#4ADE80>")
            ));
            skull.setItemMeta(meta);
        }
        return skull;
    }
}
