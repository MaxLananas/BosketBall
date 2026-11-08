package fr.bosketball.ui;

import fr.bosketball.util.Palette;
import com.destroystokyo.paper.profile.ProfileProperty;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.game.GameModeType;
import fr.bosketball.model.Achievement;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.ChatTag;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.MatchHistoryEntry;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.PlayerQuest;
import fr.bosketball.model.PlayerSettings;
import fr.bosketball.moderation.ReportService.Report;
import fr.bosketball.social.OfflineMail;
import fr.bosketball.social.SocialPartyManager;
import fr.bosketball.stats.StatsManager.LeaderboardCategory;
import fr.bosketball.ui.kit.GuiChrome;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import fr.bosketball.util.Perms;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import org.bukkit.persistence.PersistentDataType;

public final class GuiMenuManager implements Listener {

    static String tr(Player player, String key, String fallback) {
        var service = fr.bosketball.i18n.LangService.get();
        if (service == null) {
            return fallback.toUpperCase(java.util.Locale.ROOT);
        }
        return service.caption(player, key, fallback);
    }

    private static final NamespacedKey SKULL_TARGET_UUID_KEY = new NamespacedKey("bosketball", "skull_target_uuid");
    private static final NamespacedKey SKULL_TARGET_NAME_KEY = new NamespacedKey("bosketball", "skull_target_name");
    private static final long SKIN_HEAD_STALE_MILLIS = 7L * 24L * 60L * 60L * 1000L;
    private static final long SKIN_HEAD_FAILURE_COOLDOWN_MILLIS = 15L * 60L * 1000L;

    public enum MenuType {
        MAIN_HUB,
        PLAY_MODES,
        BOT_PANEL,
        SPECTATE_BROWSER,
        SHOP,
        COSMETICS,
        TAGS_VAULT,
        LEADERBOARDS,
        MATCH_HISTORY,
        FRIENDS_PARTY,
        FRIENDS_MENU,

        FRIENDS_RICH,
        ADD_FRIEND_ONLINE,
        PARTY_INVITE_ONLINE,
        PARTY_MENU,
        PLAYER_INTERACT,
        RANKS_GUIDE,
        MAP_STUDIO,
        SERVER_ADMIN,
        SETTINGS,
        QUESTS,
        ACHIEVEMENTS,
        MAILBOX,
        REPORTS,
        WARNINGS,
        GUILDS,
        SEASON,
        TUTORIAL
    }

    public enum ShopCategory {
        ALL("SHOP"),
        BALLS("BASKETBALLS"),
        DUNKS("SIGNATURE DUNKS"),
        TAGS("CHAT TAGS");

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

    public enum ShopSort {
        DEFAULT("Default"),
        PRICE_ASC("Price ↑"),
        PRICE_DESC("Price ↓"),
        NAME("Name A→Z"),
        OWNED("Owned first");

        private final String label;

        ShopSort(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public ShopSort next() {
            ShopSort[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        public static ShopSort byName(String raw) {
            if (raw == null) {
                return DEFAULT;
            }
            for (ShopSort sort : values()) {
                if (sort.name().equalsIgnoreCase(raw.replace('-', '_'))) {
                    return sort;
                }
            }
            return DEFAULT;
        }
    }

    private final java.util.Map<UUID, ShopSort> shopSortModes = new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.Map<UUID, String> shopSearchQueries = new java.util.concurrent.ConcurrentHashMap<>();

    public ShopSort shopSortOf(UUID playerId) {
        return shopSortModes.getOrDefault(playerId, ShopSort.DEFAULT);
    }

    public void setShopSort(UUID playerId, ShopSort sort) {
        if (sort == null || sort == ShopSort.DEFAULT) {
            shopSortModes.remove(playerId);
        } else {
            shopSortModes.put(playerId, sort);
        }
    }

    public String shopSearchOf(UUID playerId) {
        return shopSearchQueries.getOrDefault(playerId, "");
    }

    public void setShopSearch(UUID playerId, String query) {
        if (query == null || query.isBlank()) {
            shopSearchQueries.remove(playerId);
        } else {
            shopSearchQueries.put(playerId, query.trim().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private List<ShopItemEntry> applyShopView(Player player, PlayerProfile profile, List<ShopItemEntry> entries) {
        String query = shopSearchQueries.get(player.getUniqueId());
        List<ShopItemEntry> visible = new ArrayList<>(entries.size());
        for (ShopItemEntry entry : entries) {
            if (query != null) {
                String title = shopEntryTitle(entry).toLowerCase(java.util.Locale.ROOT);
                boolean matchesName = title.contains(query);
                boolean matchesPrice = String.valueOf(entry.priceCoins()).contains(query);
                if (!matchesName && !matchesPrice) {
                    continue;
                }
            }
            visible.add(entry);
        }
        switch (shopSortModes.getOrDefault(player.getUniqueId(), ShopSort.DEFAULT)) {
            case PRICE_ASC -> visible.sort(java.util.Comparator.comparingInt(ShopItemEntry::priceCoins));
            case PRICE_DESC -> visible.sort(java.util.Comparator.comparingInt(ShopItemEntry::priceCoins).reversed());
            case NAME -> visible.sort(java.util.Comparator.comparing(
                e -> shopEntryTitle(e).toLowerCase(java.util.Locale.ROOT)));
            case OWNED -> visible.sort(java.util.Comparator.comparing(
                e -> !isShopEntryOwned(player, profile, e)));
            default -> { }
        }
        return visible;
    }

    public enum TagVaultFilter {
        ALL("All Tags", "ref_bag"),
        COMMON("Common", "icon_star_shield"),
        RARE("Rare", "icon_defense"),
        EPIC("Epic", "icon_gem_ruby"),
        LEGENDARY("Legendary", "ref_cup_gold"),
        OWNED("Unlocked", "ref_check");

        private final String label;
        private final String iconId;

        TagVaultFilter(String label, String iconId) {
            this.label = label;
            this.iconId = iconId;
        }

        public String label() {
            return label;
        }

        public String iconId() {
            return iconId;
        }
    }

    public static final class BosketHolder implements InventoryHolder {
        private final MenuType type;
        private final LeaderboardCategory leaderboardCategory;
        private final ShopCategory shopCategory;
        private final TagVaultFilter tagFilter;
        private final int page;
        private final Map<Integer, ShopItemEntry> shopSlotMap = new HashMap<>();
        private final Map<Integer, ChatTag> tagSlotMap = new HashMap<>();
        private final Map<Integer, String> slotStringMap = new HashMap<>();
        private java.util.UUID targetPlayerId;
        private Inventory inventory;

        public BosketHolder(MenuType type) {
            this(type, LeaderboardCategory.ELO, ShopCategory.ALL, TagVaultFilter.ALL, 0);
        }

        public BosketHolder(MenuType type, LeaderboardCategory leaderboardCategory) {
            this(type, leaderboardCategory, ShopCategory.ALL, TagVaultFilter.ALL, 0);
        }

        public BosketHolder(MenuType type, int page) {
            this(type, LeaderboardCategory.ELO, ShopCategory.ALL, TagVaultFilter.ALL, page);
        }

        public BosketHolder(MenuType type, TagVaultFilter tagFilter, int page) {
            this(type, LeaderboardCategory.ELO, ShopCategory.ALL, tagFilter, page);
        }

        public BosketHolder(MenuType type, ShopCategory shopCategory, int page) {
            this(type, LeaderboardCategory.ELO, shopCategory, TagVaultFilter.ALL, page);
        }

        public BosketHolder(MenuType type, LeaderboardCategory leaderboardCategory, int page) {
            this(type, leaderboardCategory, ShopCategory.ALL, TagVaultFilter.ALL, page);
        }

        public BosketHolder(MenuType type, LeaderboardCategory leaderboardCategory, ShopCategory shopCategory, int page) {
            this(type, leaderboardCategory, shopCategory, TagVaultFilter.ALL, page);
        }

        public BosketHolder(MenuType type, LeaderboardCategory leaderboardCategory, ShopCategory shopCategory, TagVaultFilter tagFilter, int page) {
            this.type = type;
            this.leaderboardCategory = leaderboardCategory;
            this.shopCategory = shopCategory;
            this.tagFilter = tagFilter != null ? tagFilter : TagVaultFilter.ALL;
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

        public TagVaultFilter tagFilter() {
            return tagFilter;
        }

        public int page() {
            return page;
        }

        public Map<Integer, ShopItemEntry> shopSlotMap() {
            return shopSlotMap;
        }

        public Map<Integer, ChatTag> tagSlotMap() {
            return tagSlotMap;
        }

        public Map<Integer, String> slotStringMap() {
            return slotStringMap;
        }

        public java.util.UUID targetPlayerId() {
            return targetPlayerId;
        }

        public void setTargetPlayerId(java.util.UUID targetPlayerId) {
            this.targetPlayerId = targetPlayerId;
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
    private final Set<java.util.UUID> skinHeadLookupsInFlight = ConcurrentHashMap.newKeySet();
    private final Map<java.util.UUID, Long> skinHeadLookupFailures = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, List<Runnable>> skinHeadLookupCallbacks = new ConcurrentHashMap<>();

    public GuiMenuManager(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public void openPlaySelector(Player player) {
        openPlayModes(player);
    }

    public void openQuests(Player player) {
        progressionGuis().openQuests(player);
    }

    public void openAchievements(Player player, String categoryFilter, int page) {
        progressionGuis().openAchievements(player, categoryFilter, page);
    }

    public void openMailbox(Player player) {
        progressionGuis().openMailbox(player);
    }

    public void openReportsPanel(Player staff) {
        progressionGuis().openReportsPanel(staff);
    }

    public void openGuildMenu(Player player) {
        new GuildGuis(plugin).open(player);
    }

    public void openWarningsPanel(Player staff) {
        progressionGuis().openWarningsPanel(staff, null);
    }

    public void openWarningsPanel(Player staff, java.util.UUID focus) {
        progressionGuis().openWarningsPanel(staff, focus);
    }

    public void openFriendsRich(Player player) {
        progressionGuis().openFriends(player);
    }

    private ProgressionGuis progressionGuisInstance;

    private ProgressionGuis progressionGuis() {
        if (progressionGuisInstance == null) {
            progressionGuisInstance = new ProgressionGuis(plugin);
        }
        return progressionGuisInstance;
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

    private void applyPlayerHead(SkullMeta meta, OfflinePlayer target, java.util.UUID fallbackId, String fallbackName) {
        if (meta == null) {
            return;
        }
        java.util.UUID targetId = target != null ? target.getUniqueId() : fallbackId;
        String targetName = fallbackName != null && !fallbackName.isBlank()
            ? fallbackName
            : (target != null ? target.getName() : null);
        if (targetId != null) {
            meta.getPersistentDataContainer().set(SKULL_TARGET_UUID_KEY, PersistentDataType.STRING, targetId.toString());
        }
        if (targetName != null && !targetName.isBlank()) {
            meta.getPersistentDataContainer().set(SKULL_TARGET_NAME_KEY, PersistentDataType.STRING, targetName);
        }
        if (targetId == null) {
            return;
        }

        com.destroystokyo.paper.profile.PlayerProfile paperProfile =
            resolveCachedHeadProfile(target, targetId, targetName);
        boolean appliedRealSkin = false;
        if (paperProfile != null && fr.bosketball.stats.StatsManager.texturesProperty(paperProfile) != null) {
            meta.setPlayerProfile(paperProfile);
            appliedRealSkin = true;
        }
        if (!appliedRealSkin || needsSkinRefresh(targetId)) {
            requestRealHeadAsync(targetId, targetName, null);
        }
    }

    private com.destroystokyo.paper.profile.PlayerProfile resolveCachedHeadProfile(
        OfflinePlayer target,
        java.util.UUID targetId,
        String targetName
    ) {
        if (target instanceof Player online && online.isOnline()) {
            try {
                com.destroystokyo.paper.profile.PlayerProfile liveProfile = online.getPlayerProfile().clone();
                ProfileProperty textures = fr.bosketball.stats.StatsManager.texturesProperty(liveProfile);
                if (textures == null) {
                    try {
                        liveProfile.completeFromCache();
                    } catch (Throwable ignored) {

                    }
                    textures = fr.bosketball.stats.StatsManager.texturesProperty(liveProfile);
                }
                if (textures != null) {
                    plugin.statsManager().storeSkinTexture(targetId, online.getName(), textures);
                    return liveProfile;
                }
            } catch (Throwable ignored) {

            }
        }

        ProfileProperty cached = plugin.statsManager().cachedSkinTexture(targetId);
        if (cached != null) {
            com.destroystokyo.paper.profile.PlayerProfile profile = createPaperHeadProfile(targetId, targetName);
            if (profile != null) {
                profile.setProperty(cached);
                return profile;
            }
        }

        com.destroystokyo.paper.profile.PlayerProfile locallyFilled = createPaperHeadProfile(targetId, targetName);
        if (locallyFilled != null) {
            try {
                locallyFilled.completeFromCache();
                ProfileProperty textures = fr.bosketball.stats.StatsManager.texturesProperty(locallyFilled);
                if (textures != null) {
                    String resolvedName = locallyFilled.getName() != null ? locallyFilled.getName() : targetName;
                    plugin.statsManager().storeSkinTexture(targetId, resolvedName, textures);
                    return locallyFilled;
                }
            } catch (Throwable ignored) {

            }
        }
        return null;
    }

    private boolean needsSkinRefresh(java.util.UUID targetId) {
        PlayerProfile stats = plugin.statsManager().getIfPresent(targetId);
        if (stats == null || !stats.hasSkinTexture()) {
            return true;
        }
        long updatedAt = stats.skinTextureUpdatedAtMillis();
        return updatedAt <= 0L || System.currentTimeMillis() - updatedAt > SKIN_HEAD_STALE_MILLIS;
    }

    private void requestRealHeadAsync(java.util.UUID targetId, String targetName, Runnable onResolved) {
        if (targetId == null || plugin == null || !plugin.isEnabled()) {
            return;
        }
        if (!needsSkinRefresh(targetId)) {
            if (onResolved != null) {
                onResolved.run();
            }
            return;
        }
        if (onResolved != null) {
            skinHeadLookupCallbacks.computeIfAbsent(targetId, id -> new ArrayList<>()).add(onResolved);
        }
        if (skinHeadLookupsInFlight.contains(targetId)) {
            return;
        }
        Long lastFailure = skinHeadLookupFailures.get(targetId);
        if (lastFailure != null && System.currentTimeMillis() - lastFailure < SKIN_HEAD_FAILURE_COOLDOWN_MILLIS) {
            skinHeadLookupCallbacks.remove(targetId);
            return;
        }
        if (!skinHeadLookupsInFlight.add(targetId)) {
            return;
        }

        try {
            com.destroystokyo.paper.profile.PlayerProfile seed = createPaperHeadProfile(targetId, targetName);
            if (seed == null) {
                finishSkinHeadLookup(targetId, false);
                return;
            }
            try {
                seed.completeFromCache();
            } catch (Throwable ignored) {

            }
            ProfileProperty localTextures = fr.bosketball.stats.StatsManager.texturesProperty(seed);
            if (localTextures != null) {
                plugin.statsManager().storeSkinTexture(targetId,
                    seed.getName() != null ? seed.getName() : targetName, localTextures);
                finishSkinHeadLookup(targetId, true);
                return;
            }

            seed.update().whenCompleteAsync((updated, error) -> {
                ProfileProperty textures = error == null
                    ? fr.bosketball.stats.StatsManager.texturesProperty(updated)
                    : null;
                if (textures != null) {
                    String resolvedName = updated != null && updated.getName() != null ? updated.getName() : targetName;
                    plugin.statsManager().storeSkinTexture(targetId, resolvedName, textures);
                    finishSkinHeadLookup(targetId, true);
                } else {
                    finishSkinHeadLookup(targetId, false);
                }
            }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
        } catch (Throwable ignored) {
            finishSkinHeadLookup(targetId, false);
        }
    }

    private void finishSkinHeadLookup(java.util.UUID targetId, boolean success) {
        skinHeadLookupsInFlight.remove(targetId);
        List<Runnable> callbacks = skinHeadLookupCallbacks.remove(targetId);
        if (success) {
            skinHeadLookupFailures.remove(targetId);
            if (callbacks != null) {
                for (Runnable callback : callbacks) {
                    try {
                        callback.run();
                    } catch (Throwable ignored) {

                    }
                }
            }
        } else {
            skinHeadLookupFailures.put(targetId, System.currentTimeMillis());
        }
    }

    private static com.destroystokyo.paper.profile.PlayerProfile createPaperHeadProfile(
        java.util.UUID targetId,
        String targetName
    ) {
        try {
            String safeName = targetName != null && targetName.length() <= 16
                && targetName.chars().allMatch(c -> c == '_' || Character.isLetterOrDigit(c))
                ? targetName
                : null;
            return Bukkit.createProfileExact(targetId, safeName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static java.util.UUID skullTargetId(SkullMeta meta) {
        if (meta == null) {
            return null;
        }
        String stored = meta.getPersistentDataContainer().get(SKULL_TARGET_UUID_KEY, PersistentDataType.STRING);
        if (stored != null && !stored.isBlank()) {
            try {
                return java.util.UUID.fromString(stored);
            } catch (IllegalArgumentException ignored) {

            }
        }
        OfflinePlayer owner = meta.getOwningPlayer();
        return owner != null ? owner.getUniqueId() : null;
    }

    private static String skullTargetName(SkullMeta meta) {
        if (meta == null) {
            return "Player";
        }
        String stored = meta.getPersistentDataContainer().get(SKULL_TARGET_NAME_KEY, PersistentDataType.STRING);
        if (stored != null && !stored.isBlank()) {
            return stored;
        }
        OfflinePlayer owner = meta.getOwningPlayer();
        return owner != null && owner.getName() != null ? owner.getName() : "Player";
    }

    private static String coinStr(int amount) {
        return "<!shadow><white>" + Glyphs.CHAT_ICON_COIN + "</white></!shadow> <" + Palette.GOLD_SOFT + "><bold>" + amount + " Coins</bold></" + Palette.GOLD_SOFT + ">";
    }

    private static List<net.kyori.adventure.text.Component> buildBallHoverLore(BallSkin skin, String ownershipMm, String actionMm) {
        String r0 = "<!shadow><white>" + Glyphs.ballPreviewStrip(skin, 0) + "</white></!shadow>  <dark_gray>3D Square Basketball</dark_gray>";
        String r1 = "<!shadow><white>" + Glyphs.ballPreviewStrip(skin, 1) + "</white></!shadow>  <gray>" + skin.loreLine() + "</gray>";
        String r2 = "<!shadow><white>" + Glyphs.ballPreviewStrip(skin, 2) + "</white></!shadow>  " + ownershipMm;
        String r3 = "<!shadow><white>" + Glyphs.ballPreviewStrip(skin, 3) + "</white></!shadow>  " + actionMm;
        return List.of(Msg.parse(r0), Msg.parse(r1), Msg.parse(r2), Msg.parse(r3));
    }

    public void openMainHub(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.MAIN_HUB);
        char hubOverlay = Perms.admin(player) ? Glyphs.GUI_OVERLAY_HUB_OP : Glyphs.GUI_OVERLAY_HUB;
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            Glyphs.customGuiOverlayTitle(hubOverlay, tr(player, "gui.title.hub", "LOBBY"))
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        UUID id = player.getUniqueId();
        var season = plugin.seasonService();
        int queue = plugin.gameManager().queuedCount();
        int live = plugin.gameManager().activePlayerCount();
        boolean busy = plugin.gameManager().isInMatch(id) || plugin.gameManager().isQueued(id);

        // Layout contract: every row is centred on column 4, never a stray icon.
        //   row 1  13              -> player card
        //   row 2  20 22 24        -> play / shop / locker
        //   row 3  29 31 33        -> social / ranks guide / leaderboards
        //   row 4  37 39 41 43     -> practice / progress / history / settings
        //   row 5  45 46 49 52 53  -> leave, rail (home/back/season) and the corner door
        inv.setItem(13, buildPlayerSkullCard(player, profile));

        String rankLine = season != null && season.enabled() && season.isProvisional(id)
            ? "<" + Palette.GOLD + ">Placements " + Math.max(0, season.placementsRequired() - season.placementsRemaining(id))
                + "/" + season.placementsRequired() + "</" + Palette.GOLD + ">"
            : "<white>" + profile.elo() + " ELO</white>";

        inv.setItem(20, customIcon(
            "ref_play",
            "<" + Palette.WARN + "><bold>ᴘʟᴀʏ</bold></" + Palette.WARN + ">",
            List.of(
                "<gray>Ranked 1v1, 2v2, 3v3 & 5v5,</gray>",
                "<gray>solo drills and AI sparring.</gray>",
                "",
                "<gray>In queue:</gray> <" + Palette.SUCCESS + ">" + queue + "</" + Palette.SUCCESS + "> <dark_gray>•</dark_gray> <gray>On court:</gray> <" + Palette.GOLD + ">" + live + "</" + Palette.GOLD + ">",
                "<gray>Your rank:</gray> " + rankLine,
                "",
                "<yellow>▸ Choose a mode</yellow>"
            )));

        inv.setItem(22, customIcon(
            "ref_shop",
            "<" + Palette.GOLD + "><bold>ꜱʜᴏᴘ</bold></" + Palette.GOLD + ">",
            List.of(
                "<gray>Balls, dunks, tag emblems and bundles.</gray>",
                "",
                "<gray>Still locked:</gray> <aqua>" + countUnownedShopItems(profile) + " items</aqua>",
                "",
                "<yellow>▸ Left-Click: browse the shop</yellow>",
                "<aqua>▸ Right-Click: your collection</aqua>"
            )));

        ChatTag activeTag = profile.selectedTag();
        String equippedTagStr = activeTag == null
            ? "<gray>None</gray>"
            : "<!shadow><white>" + activeTag.glyph() + "</white></!shadow> <white>" + activeTag.displayName() + "</white>";
        inv.setItem(24, customIcon(
            "ref_clothing",
            "<" + Palette.ICE + "><bold>ʟᴏᴄᴋᴇʀ</bold></" + Palette.ICE + ">",
            List.of(
                "<gray>What you wear on and off the court.</gray>",
                "",
                "<gray>Ball:</gray> " + profile.selectedBallSkin().displayNameMm(),
                "<gray>Tag:</gray> " + equippedTagStr,
                "",
                "<aqua>▸ Left-Click: cosmetics & locker</aqua>",
                "<light_purple>▸ Right-Click: chat tags</light_purple>"
            )));

        int groupSize = plugin.socialManager().partySizeOrOne(id);
        int onlineFriends = plugin.socialManager().countOnlineFriends(id);
        var myGuild = plugin.guildService().guildOf(id);
        inv.setItem(29, customIcon(
            "ref_member",
            "<" + Palette.SUCCESS + "><bold>ꜱᴏᴄɪᴀʟ</bold></" + Palette.SUCCESS + ">",
            List.of(
                "<gray>Friends, party and guild - the</gray>",
                "<gray>people you actually play with.</gray>",
                "",
                "<gray>Friends online:</gray> <" + Palette.SUCCESS + ">" + onlineFriends + "</" + Palette.SUCCESS + ">",
                "<gray>Party:</gray> <white>" + groupSize + "/4</white>",
                "<gray>Guild:</gray> " + (myGuild != null ? myGuild.displayTag() + " <gray>(" + myGuild.members().size() + ")</gray>" : "<white>None</white>"),
                "",
                "<yellow>▸ Left-Click: Friends</yellow>",
                "<aqua>▸ Right-Click: Party</aqua>",
                "<gold>▸ Shift-Click: Guild</gold>"
            )));

        inv.setItem(31, customIcon(
            "ref_medal_gold",
            "<" + Palette.VIOLET + "><bold>ʀᴀɴᴋꜱ ɢᴜɪᴅᴇ</bold></" + Palette.VIOLET + ">",
            List.of(
                "<gray>Rookie to MVP: every tier, badge</gray>",
                "<gray>and the full control list.</gray>",
                "",
                "<" + Palette.VIOLET + ">▸ Open the guide</" + Palette.VIOLET + ">"
            )));

        inv.setItem(33, customIcon(
            "ref_cup_gold",
            "<" + Palette.GOLD_SOFT + "><bold>ʟᴇᴀᴅᴇʀʙᴏᴀʀᴅꜱ</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<gray>Top 10 across every stat.</gray>",
                "",
                "<yellow>▸ Open rankings</yellow>"
            )));

        inv.setItem(37, customIcon(
            "ref_dice",
            "<" + Palette.ICE + "><bold>ᴘʀᴀᴄᴛɪᴄᴇ</bold></" + Palette.ICE + ">",
            List.of("<gray>Empty gym, 24s shot clock, AI defender.</gray>", "", "<aqua>▸ Step on court</aqua>")));

        int claimable = profile.unclaimedQuestCount();
        inv.setItem(39, customIcon(
            "ref_trophy",
            "<" + Palette.GOLD_SOFT + "><bold>ᴘʀᴏɢʀᴇꜱꜱ</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<gray>Quests:</gray> " + (claimable > 0 ? "<" + Palette.SUCCESS + "><bold>" + claimable + " ready</bold></" + Palette.SUCCESS + ">" : "<white>none ready</white>"),
                "<gray>Unread mail:</gray> <white>" + plugin.socialService().unreadCount(profile) + "</white>",
                "",
                "<yellow>▸ Left-Click: Quest board</yellow>",
                "<aqua>▸ Right-Click: Achievements</aqua>",
                "<gold>▸ Shift-Click: Mailbox</gold>")));

        inv.setItem(41, customIcon(
            "ref_clock",
            "<" + Palette.ICE + "><bold>ᴍᴀᴛᴄʜ ʜɪꜱᴛᴏʀʏ</bold></" + Palette.ICE + ">",
            List.of(
                "<gray>Your last " + profile.matchHistory().size() + " box scores.</gray>",
                "",
                "<aqua>▸ Review games</aqua>")));

        inv.setItem(43, customIcon(
            "ref_gear",
            "<" + Palette.MUTED + "><bold>ꜱᴇᴛᴛɪɴɢꜱ</bold></" + Palette.MUTED + ">",
            List.of("<gray>Visuals, sounds, cameras, language.</gray>", "", "<white>▸ Configure</white>")));

        if (busy) {
            inv.setItem(45, customIcon(
                "ref_exit",
                "<" + Palette.DANGER + "><bold>ʟᴇᴀᴠᴇ</bold></" + Palette.DANGER + ">",
                List.of("<gray>Step out of the match or queue.</gray>")));
        }

        // Corner door: staff get the studio, everyone else the community invite.
        if (Perms.admin(player)) {
            inv.setItem(53, customIcon(
                "court_wand",
                "<" + Palette.ICE + "><bold>ꜱᴛᴜᴅɪᴏ</bold></" + Palette.ICE + ">",
                List.of(
                    "<gray>Maintenance, arenas, pack push, saves.</gray>",
                    "",
                    "<" + Palette.SUCCESS + ">▸ Left-Click: Server control</" + Palette.SUCCESS + ">",
                    "<aqua>▸ Right-Click: Arena studio</aqua>")));
        } else {
            inv.setItem(53, customIcon(
                "icon_discord",
                "<" + Palette.ICE + "><bold>ᴅɪꜱᴄᴏʀᴅ</bold></" + Palette.ICE + ">",
                List.of("<gray>The community hub.</gray>", "", "<" + Palette.ICE + ">▸ Get the invite</" + Palette.ICE + ">")));
        }

        inv.setItem(GuiChrome.SLOT_BACK, GuiChrome.closeIcon(GuiMenuManager::customIcon));
        applyStandardRail(inv, player);
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
    }

    public void openPlayModes(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.PLAY_MODES);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_PLAY, tr(player, "gui.title.play", "PLAY MODES"))
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        String[] modeIcons = {"icon_duel", "icon_team_duo", "icon_team", "icon_battle"};
        GameModeType[] modes = {
            GameModeType.DUEL_1V1,
            GameModeType.STREET_2V2,
            GameModeType.PRO_3V3,
            GameModeType.FULL_5V5
        };
        int[] slots = {10, 12, 14, 16};
        int winReward = plugin.settings().economy().winCoins();
        int lossPenalty = plugin.settings().economy().lossCoins();

        for (int i = 0; i < modes.length; i++) {
            GameModeType mode = modes[i];
            int q = plugin.gameManager().queuedCount(mode);
            boolean myQueue = plugin.gameManager().getQueuedMode(player.getUniqueId()) == mode;
            inv.setItem(slots[i], customIcon(
                modeIcons[i],
                "<" + Palette.WARN + "><bold>" + mode.smallCapsTitle() + "</bold></" + Palette.WARN + ">",
                List.of(
                    "<gray>Format:</gray> <white>" + mode.playersPerTeam() + "v" + mode.playersPerTeam() + "</white>",
                    "<gray>In Queue:</gray> <" + Palette.SUCCESS + ">" + q + "/" + mode.maxPlayers() + "</" + Palette.SUCCESS + ">",
                    "<gray>Stakes:</gray> <" + Palette.SUCCESS + ">+" + winReward + " Coins</" + Palette.SUCCESS + "> <gray>/</gray> <" + Palette.DANGER + ">-" + lossPenalty + " Coins</" + Palette.DANGER + ">",
                    "",
                    myQueue ? "<" + Palette.SUCCESS + ">✔ Currently Queued (Click to leave)</" + Palette.SUCCESS + ">" : "<yellow>▸ Click to join queue</yellow>"
                )
            ));
        }

        inv.setItem(28, customIcon(
            "ref_dice",
            "<" + Palette.SUCCESS + "><bold>Solo Practice Court</bold></" + Palette.SUCCESS + ">",
            List.of("<gray>Warm up with the 24s clock & AI Defender</gray>", "", "<green>▸ Enter Court</green>")
        ));

        inv.setItem(30, customIcon(
            "ref_calendar_check",
            "<" + Palette.GOLD_SOFT + "><bold>3-Point Contest</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<gray>5 Racks • 25 Balls • 60 Seconds</gray>",
                "<gray>Earn up to</gray> <" + Palette.GOLD_SOFT + ">+" + plugin.settings().economy().threePointContestMaxCoins() + " Coins</" + Palette.GOLD_SOFT + ">",
                "",
                "<yellow>▸ Start Contest</yellow>"
            )
        ));

        inv.setItem(32, customIcon(
            "icon_spectate",
            "<" + Palette.ICE + "><bold>Spectate Live Match</bold></" + Palette.ICE + ">",
            List.of("<gray>Watch an ongoing match live in 3D</gray>", "", "<aqua>▸ Spectate</aqua>")
        ));

        inv.setItem(34, customIcon(
            "icon_portal",
            "<" + Palette.VIOLET + "><bold>3D Squad Locker Room</bold></" + Palette.VIOLET + ">",
            List.of("<gray>Preview your full-body squad & loadout</gray>", "", "<light_purple>▸ Enter Stage</light_purple>")
        ));

        inv.setItem(49, backButton(player));

        inv.setItem(40, customIcon(
            "icon_bot_on",
            "<" + Palette.VIOLET + "><bold>Training & AI Squad</bold></" + Palette.VIOLET + ">",
            List.of(
                "<gray>Choose the skill of the AI players,</gray>",
                "<gray>fill the court and manage your bots.</gray>",
                "<gray>Current difficulty:</gray> " + currentBotDifficultyLabel(player),
                "",
                "<yellow>▸ Click to open the AI panel</yellow>"
            )
        ));
        holder.slotStringMap().put(40, "OPEN_BOT_PANEL");

        applyStandardRail(inv, player);
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.25f);
    }

    /**
     * Round 33: the confirm menu's own lore says "Closing this menu cancels" — but nothing listened
     * to the close, so pressing Escape left the purchase pending and the shop closed for good. Now
     * Escape cancels the purchase (nothing was spent) and re-opens the menu the player came from.
     */
    @EventHandler
    public void onConfirmMenuClosed(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof PurchaseConfirmMenu menu)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player) || menu.isSettled()) {
            return;
        }
        Runnable back = PurchaseConfirmMenu.cancelPending(player);
        Msg.send(player, Msg.info("Purchase cancelled — nothing was spent."));
        if (back != null && player.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, back);
        }
    }

    private String currentBotDifficultyLabel(Player player) {
        fr.bosketball.game.MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        var difficulty = session != null
            ? session.botDifficulty()
            : fr.bosketball.game.bot.BotDifficulty.byNameOrNull(
                plugin.settings().matchmaking().botDifficulty());
        var resolved = difficulty != null ? difficulty : fr.bosketball.game.bot.BotDifficulty.NORMAL;
        return "<" + hex(resolved.colorRgb()) + "><bold>" + resolved.displayName() + "</bold></" + hex(resolved.colorRgb()) + ">";
    }

    private static String hex(int rgb) {
        return String.format(java.util.Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    public void openBotPanel(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.BOT_PANEL);
        Inventory inv = Bukkit.createInventory(holder, 45, Glyphs.customGuiOverlayTitle(
            Glyphs.GUI_OVERLAY_PLAY_BOT, tr(player, "gui.title.bots", "TRAINING & AI")));
        holder.setInventory(inv);
        fillBackdrop(inv);

        fr.bosketball.game.MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        var difficulty = session != null
            ? session.botDifficulty()
            : fr.bosketball.game.bot.BotDifficulty.byNameOrNull(
                plugin.settings().matchmaking().botDifficulty());
        if (difficulty == null) {
            difficulty = fr.bosketball.game.bot.BotDifficulty.NORMAL;
        }

        inv.setItem(4, customIcon("icon_bot_on",
            "<" + Palette.VIOLET + "><bold>ᴛʀᴀɪɴɪɴɢ & ᴀɪ</bold></" + Palette.VIOLET + ">",
            List.of(
                "<gray>Difficulty:</gray> " + currentBotDifficultyLabel(player),
                "<gray>AI on court:</gray> <white>" + (session != null ? session.botSquad().size() : 0) + "</white>",
                "",
                session == null
                    ? "<red>You are not on a court yet — start Practice or a match.</red>"
                    : "<gray>Changes apply instantly, mid-match.</gray>")));

        var tiers = fr.bosketball.game.bot.BotDifficulty.values();
        int[] slots = {19, 22, 25};
        String[] icons = {"icon_easy", "icon_normal", "ref_upgrade"};
        for (int i = 0; i < tiers.length && i < slots.length; i++) {
            var tier = tiers[i];
            boolean active = tier == difficulty;
            holder.slotStringMap().put(slots[i], "BOT_DIFF:" + tier.name());
            inv.setItem(slots[i], customIcon(icons[i],
                "<" + hex(tier.colorRgb()) + "><bold>" + (active ? "● " : "") + tier.displayName()
                    + "</bold></" + hex(tier.colorRgb()) + ">",
                List.of(
                    "<gray>Style:</gray> <white>" + tier.flavor() + "</white>",
                    "<gray>Shot accuracy:</gray> <white>" + Math.round(tier.shotAccuracy() * 100) + "%</white>",
                    "<gray>Reaction:</gray> <white>" + tier.reactionTicks() + " ticks</white>",
                    "",
                    active ? "<" + Palette.SUCCESS + ">✔ Currently active</" + Palette.SUCCESS + ">"
                        : "<yellow>▸ Click to set this difficulty</yellow>")));
        }

        holder.slotStringMap().put(30, "BOT_FILL");
        inv.setItem(30, customIcon("icon_bot_on",
            "<" + Palette.ICE + "><bold>ꜰɪʟʟ ᴛʜᴇ ᴄᴏᴜʀᴛ</bold></" + Palette.ICE + ">",
            List.of("<gray>Fill every empty slot with AI.</gray>",
                "", "<aqua>▸ Click to fill</aqua>")));
        holder.slotStringMap().put(32, "BOT_DEFENDER");
        inv.setItem(32, customIcon("icon_defense",
            "<" + Palette.SUCCESS + "><bold>ꜱᴘᴀʀʀɪɴɢ ᴅᴇꜰᴇɴᴅᴇʀ</bold></" + Palette.SUCCESS + ">",
            List.of("<gray>Toggle the practice sparring defender.</gray>",
                "", "<green>▸ Click to toggle</green>")));
        holder.slotStringMap().put(34, "BOT_CLEAR");
        inv.setItem(34, customIcon("icon_bot_off",
            "<" + Palette.DANGER + "><bold>ʀᴇᴍᴏᴠᴇ ᴀɪ</bold></" + Palette.DANGER + ">",
            List.of("<gray>Send every AI player off the court.</gray>",
                "", "<red>▸ Click to clear</red>")));
        holder.slotStringMap().put(40, "BACK");
        inv.setItem(40, backButton(player));
        applyStandardRail(inv, player);
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.75f, 1.2f);
    }

    public static List<ShopItemEntry> collectUnownedShopItems(PlayerProfile profile, ShopCategory filter) {
        return collectShopItems(profile, filter, false);
    }

    public static List<ShopItemEntry> collectShopCatalogItems(PlayerProfile profile, ShopCategory filter) {
        return collectShopItems(profile, filter, true);
    }

    private static List<ShopItemEntry> collectShopItems(PlayerProfile profile, ShopCategory filter, boolean includeOwned) {
        List<ShopItemEntry> list = new ArrayList<>();
        if (profile == null) {
            return list;
        }
        ShopCategory cat = filter != null ? filter : ShopCategory.ALL;

        if (cat == ShopCategory.ALL || cat == ShopCategory.BALLS) {
            List<ShopItemEntry> balls = new ArrayList<>();
            for (BallSkin skin : BallSkin.values()) {
                if ((includeOwned || !profile.hasUnlockedBall(skin)) && skin.priceCoins() > 0) {
                    balls.add(new ShopItemEntry(ShopCategory.BALLS, skin, null, null, skin.priceCoins()));
                }
            }
            balls.sort(Comparator.comparingInt(ShopItemEntry::priceCoins));
            list.addAll(balls);
        }
        if (cat == ShopCategory.ALL || cat == ShopCategory.DUNKS) {
            List<ShopItemEntry> dunks = new ArrayList<>();
            for (DunkStyle dunk : DunkStyle.values()) {
                if ((includeOwned || !profile.hasUnlockedDunk(dunk)) && dunk.priceCoins() > 0) {
                    dunks.add(new ShopItemEntry(ShopCategory.DUNKS, null, dunk, null, dunk.priceCoins()));
                }
            }
            dunks.sort(Comparator.comparingInt(ShopItemEntry::priceCoins));
            list.addAll(dunks);
        }
        if (cat == ShopCategory.ALL || cat == ShopCategory.TAGS) {
            List<ShopItemEntry> tags = new ArrayList<>();
            for (ChatTag tag : ChatTag.values()) {
                if (!tag.isSpecialUnpurchasable()
                    && (includeOwned || !tag.isUnlocked(profile))
                    && tag.priceCoins() > 0) {
                    tags.add(new ShopItemEntry(ShopCategory.TAGS, null, null, tag, tag.priceCoins()));
                }
            }
            tags.sort(Comparator.comparingInt(ShopItemEntry::priceCoins).thenComparing(e -> e.chatTag().displayName()));
            list.addAll(tags);
        }
        return list;
    }

    public static boolean isShopEntryOwned(Player player, PlayerProfile profile, ShopItemEntry entry) {
        if (profile == null || entry == null) {
            return false;
        }
        if (entry.ballSkin() != null) {
            return profile.hasUnlockedBall(entry.ballSkin())
                || (player != null && player.hasPermission("bosketball.cosmetics.all"));
        }
        if (entry.dunkStyle() != null) {
            return profile.hasUnlockedDunk(entry.dunkStyle())
                || (player != null && player.hasPermission("bosketball.cosmetics.all"));
        }
        if (entry.chatTag() != null) {
            return entry.chatTag().canBeUsedBy(
                profile,
                player != null ? player.getName() : profile.lastKnownName(),
                player != null && fr.bosketball.util.Perms.has(player, fr.bosketball.util.Perms.TAGS_ALL),
                player != null && fr.bosketball.util.Perms.has(player, fr.bosketball.util.Perms.STAFF)
            );
        }
        return false;
    }

    public static boolean isShopEntryEquipped(PlayerProfile profile, ShopItemEntry entry) {
        if (profile == null || entry == null) {
            return false;
        }
        if (entry.ballSkin() != null) {
            return profile.selectedBallSkin() == entry.ballSkin();
        }
        if (entry.dunkStyle() != null) {
            return profile.selectedDunkStyle() == entry.dunkStyle();
        }
        return entry.chatTag() != null && profile.selectedTag() == entry.chatTag();
    }

    public static List<ChatTag> visibleVaultTags(Player player, PlayerProfile profile) {
        List<ChatTag> list = new ArrayList<>();
        String pName = player != null ? player.getName() : (profile != null ? profile.lastKnownName() : "");
        for (ChatTag tag : ChatTag.values()) {
            if (tag.isSpecialUnpurchasable()) {
                if (tag.canBeUsedBy(profile, pName, false,
                    player != null && fr.bosketball.util.Perms.has(player, fr.bosketball.util.Perms.STAFF))) {
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
        if (plugin.tutorialManager() != null) {
            plugin.tutorialManager().notifyAction(player, "shop", null);
        }
        openShop(player, ShopCategory.ALL, 0);
    }

    public void openShop(Player player, ShopCategory category, int requestedPage) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        ShopCategory cat = category != null ? category : ShopCategory.ALL;

        List<ShopItemEntry> unownedBalls = collectUnownedShopItems(profile, ShopCategory.BALLS);
        List<ShopItemEntry> unownedDunks = collectUnownedShopItems(profile, ShopCategory.DUNKS);
        List<ShopItemEntry> unownedTags = collectUnownedShopItems(profile, ShopCategory.TAGS);
        int totalUnowned = unownedBalls.size() + unownedDunks.size() + unownedTags.size();

        List<ShopItemEntry> catalogBalls = collectShopCatalogItems(profile, ShopCategory.BALLS);
        List<ShopItemEntry> catalogDunks = collectShopCatalogItems(profile, ShopCategory.DUNKS);
        List<ShopItemEntry> catalogTags = collectShopCatalogItems(profile, ShopCategory.TAGS);

        int totalPages;
        List<ShopItemEntry> catEntries;
        if (cat == ShopCategory.ALL) {
            catEntries = catalogTags;
            totalPages = Math.max(1, (catalogTags.size() + SHELF_TAG_SLOTS.length - 1) / SHELF_TAG_SLOTS.length);
        } else {
            catEntries = switch (cat) {
                case BALLS -> catalogBalls;
                case DUNKS -> catalogDunks;
                case TAGS -> catalogTags;
                default -> collectShopCatalogItems(profile, cat);
            };
            totalPages = Math.max(1, (catEntries.size() + SHOP_GRID_SLOTS.length - 1) / SHOP_GRID_SLOTS.length);
        }
        catEntries = applyShopView(player, profile, catEntries);
        int shopGridSize = cat == ShopCategory.ALL ? SHELF_TAG_SLOTS.length : SHOP_GRID_SLOTS.length;
        totalPages = Math.max(1, (catEntries.size() + shopGridSize - 1) / shopGridSize);
        int page = Math.max(0, Math.min(totalPages - 1, requestedPage));

        BosketHolder holder = new BosketHolder(MenuType.SHOP, cat, page);
        char shopOverlay = (cat == ShopCategory.ALL) ? Glyphs.GUI_OVERLAY_SHOP : Glyphs.GUI_OVERLAY_SHOP_GRID;
        Inventory inv = Bukkit.createInventory(holder, 54, Glyphs.customGuiOverlayTitle(shopOverlay,
            tr(player, "gui.title.shop." + cat.name().toLowerCase(java.util.Locale.ROOT), cat.label())));
        holder.setInventory(inv);
        fillBackdrop(inv);

        var econ = plugin.settings().economy();
        inv.setItem(0, customIcon(
            "ref_coins",
            "<" + Palette.GOLD_SOFT + "><bold>Your Wallet</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                coinStr(profile.coins()),
                "",
                "<gray>How to earn Coins:</gray>",
                "<dark_gray>• +" + econ.winCoins() + " Win • +" + econ.mvpBonusCoins() + " MVP • +" + econ.twoPointCoins() + "/2PT • +" + econ.threePointCoins() + "/3PT</dark_gray>",
                "<dark_gray>• +" + econ.assistCoins() + "/AST • +" + econ.ggRewardCoins() + " GG • 3PT Contest (up to +" + econ.threePointContestMaxCoins() + ")</dark_gray>",
                "",
                "<gray>Sort:</gray> <white>" + shopSortOf(player.getUniqueId()).label() + "</white>"
                    + " <dark_gray>•</dark_gray> <yellow>▸ Click to cycle</yellow>",
                "<gray>Search:</gray> <white>"
                    + (shopSearchOf(player.getUniqueId()).isBlank() ? "none" : shopSearchOf(player.getUniqueId()))
                    + "</white> <dark_gray>•</dark_gray> <yellow>/bb shop search <text></yellow>"
            )
        ));

        inv.setItem(2, buildShopTabButton(
            cat == ShopCategory.ALL,
            "ref_shop",
            "Shop Shelves",
            totalUnowned,
            "Full catalog overview — owned items stay visible and can be equipped here; count = remaining to unlock"
        ));

        ItemStack ballTabIcon = BallSkin.GOLD.createItemStack();
        ItemMeta btm = ballTabIcon.getItemMeta();
        if (btm != null) {
            boolean active = (cat == ShopCategory.BALLS);
            btm.displayName(Msg.parse((active ? "<" + Palette.SUCCESS + "><bold>● " : "<" + Palette.GOLD + "><bold>") + "Basketballs (" + unownedBalls.size() + ")</bold>" + (active ? "</" + Palette.SUCCESS + ">" : "</" + Palette.GOLD + ">")));
            btm.lore(List.of(
                Msg.parse("<gray>3D Square Basketball Skins</gray>"),
                Msg.parse("<gray>Catalog:</gray> <white>" + catalogBalls.size() + "</white> <dark_gray>•</dark_gray> <gray>Count above = remaining to unlock; owned skins stay visible.</gray>"),
                Msg.parse(""),
                Msg.parse(active ? "<" + Palette.SUCCESS + ">✔ Viewing Basketballs</" + Palette.SUCCESS + ">" : "<yellow>▸ Click to filter Basketballs</yellow>")
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
            "Full catalog of aerial Slam Dunk animations — count = remaining to unlock; owned dunks stay visible"
        ));
        inv.setItem(5, buildShopTabButton(
            cat == ShopCategory.TAGS,
            "ref_bag",
            "Collector Tags",
            unownedTags.size(),
            "Full catalog of Chat, [TAB] & Locker Room badges sorted by Rarity — count = remaining to unlock; owned tags stay visible"
        ));

        inv.setItem(8, customIcon(
            "ref_chest",
            "<" + Palette.VIOLET + "><bold>My Locker & Vault</bold></" + Palette.VIOLET + ">",
            List.of(
                "<gray>Equip your already-owned</gray>",
                "<gray>Basketballs, Dunks & Chat Tags!</gray>",
                "",
                "<yellow>▸ Left-Click: Balls & Dunks</yellow>",
                "<aqua>▸ Right-Click: Chat Tags</aqua>"
            )
        ));

        if (cat == ShopCategory.ALL) {
            if (catalogBalls.isEmpty() && catalogDunks.isEmpty() && catalogTags.isEmpty()) {
                inv.setItem(22, customIcon(
                    "ref_cup_gold",
                    "<" + Palette.MUTED + "><bold>Shop Catalog Empty</bold></" + Palette.MUTED + ">",
                    List.of("<gray>No purchasable cosmetics are configured.</gray>")
                ));
            } else {

                inv.setItem(9, customIcon(
                    "icon_star_shield",
                    "<" + Palette.WARN + "><bold>🏀 Basketballs ▸</bold></" + Palette.WARN + ">",
                    List.of(
                        "<gray>Catalog:</gray> <white>" + catalogBalls.size() + " skins</white> <dark_gray>•</dark_gray> <gray>Remaining:</gray> <white>" + unownedBalls.size() + "</white>",
                        "<gray>Owned skins stay here and can be equipped.</gray>",
                        "",
                        "<yellow>▸ Click to view Basketballs tab</yellow>"
                    )
                ));
                if (catalogBalls.isEmpty()) {
                    inv.setItem(10, customIcon("ref_cup_gold", "<" + Palette.MUTED + "><bold>No Basketballs in Catalog</bold></" + Palette.MUTED + ">", List.of("<gray>No purchasable basketball skins are configured.</gray>")));
                } else {
                    for (int i = 0; i < SHELF_BALL_SLOTS.length && i < catalogBalls.size(); i++) {
                        int s = SHELF_BALL_SLOTS[i];
                        ShopItemEntry e = catalogBalls.get(i);
                        inv.setItem(s, buildShopEntryCard(player, profile, e));
                        holder.shopSlotMap().put(s, e);
                    }
                }

                inv.setItem(18, customIcon(
                    "dunk_meteor_jam",
                    "<" + Palette.WARN + "><bold>🔥 Dunk Styles ▸</bold></" + Palette.WARN + ">",
                    List.of(
                        "<gray>Catalog:</gray> <white>" + catalogDunks.size() + " dunks</white> <dark_gray>•</dark_gray> <gray>Remaining:</gray> <white>" + unownedDunks.size() + "</white>",
                        "<gray>Owned dunks stay here and can be equipped.</gray>",
                        "",
                        "<yellow>▸ Click to view Signature Dunks tab</yellow>"
                    )
                ));
                if (catalogDunks.isEmpty()) {
                    inv.setItem(19, customIcon("ref_cup_gold", "<" + Palette.MUTED + "><bold>No Dunk Styles in Catalog</bold></" + Palette.MUTED + ">", List.of("<gray>No purchasable dunk styles are configured.</gray>")));
                } else {
                    for (int i = 0; i < SHELF_DUNK_SLOTS.length && i < catalogDunks.size(); i++) {
                        int s = SHELF_DUNK_SLOTS[i];
                        ShopItemEntry e = catalogDunks.get(i);
                        inv.setItem(s, buildShopEntryCard(player, profile, e));
                        holder.shopSlotMap().put(s, e);
                    }
                }

                inv.setItem(27, customIcon(
                    "ref_bag",
                    "<" + Palette.ICE + "><bold>🏷 Chat Tags ▸</bold></" + Palette.ICE + ">",
                    List.of(
                        "<gray>Catalog:</gray> <white>" + catalogTags.size() + " tags</white> <dark_gray>•</dark_gray> <gray>Remaining:</gray> <white>" + unownedTags.size() + "</white>",
                        "<gray>Owned tags stay here, sorted Common → Legendary.</gray>",
                        "",
                        "<aqua>▸ Click for full Collector Tags grid</aqua>"
                    )
                ));
                if (catalogTags.isEmpty()) {
                    inv.setItem(28, customIcon("ref_cup_gold", "<" + Palette.MUTED + "><bold>No Collector Tags in Catalog</bold></" + Palette.MUTED + ">", List.of("<gray>No purchasable collector tags are configured.</gray>")));
                } else {
                    int startIdx = page * SHELF_TAG_SLOTS.length;
                    for (int i = 0; i < SHELF_TAG_SLOTS.length && (startIdx + i) < catalogTags.size(); i++) {
                        int s = SHELF_TAG_SLOTS[i];
                        ShopItemEntry e = catalogTags.get(startIdx + i);
                        inv.setItem(s, buildShopEntryCard(player, profile, e));
                        holder.shopSlotMap().put(s, e);
                    }
                }
            }
        } else {
            if (catEntries.isEmpty()) {
                inv.setItem(22, customIcon(
                    "ref_cup_gold",
                    "<" + Palette.MUTED + "><bold>No " + cat.label() + " in Catalog</bold></" + Palette.MUTED + ">",
                    List.of("<gray>No purchasable items are configured for this category.</gray>")
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

        if (page > 0) {
            inv.setItem(45, customIcon(
                "icon_prev",
                "<yellow><bold>◂ Previous Page</bold></yellow>",
                List.of("<gray>Page " + page + " of " + totalPages + "</gray>")
            ));
        }
        inv.setItem(48, customIcon(
            "dunk_tomahawk",
            "<" + Palette.VIOLET + "><bold>My Basketballs & Dunks</bold></" + Palette.VIOLET + ">",
            List.of("<gray>Equip your owned Basketballs & Dunks</gray>", "", "<yellow>▸ Open Locker</yellow>")
        ));
        inv.setItem(49, backButton(player));
        inv.setItem(50, customIcon(
            "ref_bag",
            "<" + Palette.ICE + "><bold>My Chat Tags</bold></" + Palette.ICE + ">",
            List.of("<gray>Equip or remove your unlocked Tags</gray>", "", "<aqua>▸ Open Tag Vault</aqua>")
        ));
        if (page + 1 < totalPages) {
            inv.setItem(53, customIcon(
                "icon_next",
                "<yellow><bold>Next Page ▸</bold></yellow>",
                List.of("<gray>Page " + (page + 2) + " of " + totalPages + "</gray>")
            ));
        }

        applyStandardRail(inv, player);
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.24f);
    }

    private ItemStack buildShopTabButton(boolean active, String modelId, String title, int count, String desc) {
        ItemStack stack = customIcon(
            modelId,
            (active ? "<" + Palette.SUCCESS + "><bold>● " : "<" + Palette.MUTED + "><bold>") + title + " (" + count + ")</bold>" + (active ? "</" + Palette.SUCCESS + ">" : "</" + Palette.MUTED + ">"),
            List.of(
                "<gray>" + desc + "</gray>",
                "",
                active ? "<" + Palette.SUCCESS + ">✔ Active Tab</" + Palette.SUCCESS + ">" : "<yellow>▸ Click to view</yellow>"
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
        boolean owned = isShopEntryOwned(player, profile, entry);
        boolean equipped = owned && isShopEntryEquipped(profile, entry);
        boolean canAfford = profile.coins() >= entry.priceCoins();
        fr.bosketball.util.TooltipStyles.Rarity rarity =
            fr.bosketball.util.TooltipStyles.forCoins(entry.priceCoins());
        String action = equipped
            ? "<" + Palette.SUCCESS + "><bold>✔ Owned & Equipped</bold></" + Palette.SUCCESS + ">"
            : owned
                ? "<yellow><bold>▸ Click to Equip (Owned)</bold></yellow>"
                : canAfford
                    ? "<" + Palette.SUCCESS + "><bold>▸ Click to Buy & Equip (" + entry.priceCoins() + " Coins)</bold></" + Palette.SUCCESS + ">"
                    : "<" + Palette.DANGER + ">▸ Need " + (entry.priceCoins() - profile.coins()) + " more Coins</" + Palette.DANGER + ">";
        String ownershipLine = owned
            ? "<" + Palette.SUCCESS + ">✔ Owned — saved in My Locker</" + Palette.SUCCESS + ">"
            : "<gray>Price:</gray> " + coinStr(entry.priceCoins());

        if (entry.ballSkin() != null) {
            BallSkin skin = entry.ballSkin();
            ItemStack card = skin.createItemStack();
            ItemMeta meta = card.getItemMeta();
            if (meta != null) {
                meta.displayName(Msg.parse(skin.displayNameMm()));
                meta.lore(buildBallHoverLore(skin, ownershipLine, action));
                card.setItemMeta(meta);
            }
            return equipped
                ? markEquippedGlow(fr.bosketball.util.TooltipStyles.decorate(card, rarity))
                : fr.bosketball.util.TooltipStyles.decorate(card, rarity);
        }
        if (entry.dunkStyle() != null) {
            DunkStyle dunk = entry.dunkStyle();
            ItemStack card = customIcon(
                dunk.iconModelId(),
                dunk.displayNameMm(),
                List.of(
                    "<dark_gray>Signature Aerial Dunk Style</dark_gray>",
                    "<gray>" + dunk.descriptionSmallCaps() + "</gray>",
                    ownershipLine,
                    "",
                    action
                )
            );
            return equipped
                ? markEquippedGlow(fr.bosketball.util.TooltipStyles.decorate(card, rarity))
                : fr.bosketball.util.TooltipStyles.decorate(card, rarity);
        }
        ChatTag tag = entry.chatTag();
        ItemStack card = customIcon(
            tag.cardModelId(),
            "<" + Palette.TEXT + "><bold>" + tag.displayName() + "</bold></" + Palette.TEXT + "> <!shadow><white>" + tag.glyph() + "</white></!shadow>",
            List.of(
                "<dark_gray>Collector Tag • </dark_gray>" + tag.tierLabel(),
                "<gray>" + tag.loreHint() + "</gray>",
                "<gray>Full Badge:</gray> <!shadow><white>" + tag.glyph() + "</white></!shadow>",
                "<gray>In Chat:</gray> <!shadow><white>" + profile.effectiveRank().pillGlyph() + "</white></!shadow> <!shadow><white>" + tag.glyph() + "</white></!shadow> <white>" + player.getName() + "</white>",
                ownershipLine,
                "",
                action
            )
        );
        return equipped
            ? markEquippedGlow(fr.bosketball.util.TooltipStyles.decorate(card, rarity))
            : fr.bosketball.util.TooltipStyles.decorate(card, rarity);
    }

    private static ItemStack markEquippedGlow(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static final int[] COSMETICS_BALL_SLOTS = {9, 10, 11, 12, 14, 15, 16, 17};

    public void openCosmetics(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.COSMETICS);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_COSMETICS, tr(player, "gui.title.cosmetics", "COSMETICS"))
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

        inv.setItem(7, customIcon(
            "ref_chest",
            "<" + Palette.GOLD_SOFT + "><bold>Your Wallet</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                coinStr(profile.coins()),
                "<gray>Win matches & score buckets to earn Coins!</gray>"
            )
        ));
        inv.setItem(8, customIcon(
            "ref_shop",
            "<" + Palette.SUCCESS + "><bold>Shop</bold></" + Palette.SUCCESS + ">",
            List.of("<gray>Browse the full Shop catalog — owned items stay visible</gray>", "", "<green>▸ Open Shop</green>")
        ));

        BallSkin[] balls = BallSkin.values();
        for (int i = 0; i < balls.length && i < COSMETICS_BALL_SLOTS.length; i++) {
            BallSkin skin = balls[i];
            boolean unlocked = profile.hasUnlockedBall(skin) || player.hasPermission("bosketball.cosmetics.all");
            boolean selected = profile.selectedBallSkin() == skin;

            ItemStack ballItem = skin.createItemStack();
            ItemMeta meta = ballItem.getItemMeta();
            if (meta != null) {
                meta.displayName(Msg.parse(skin.displayNameMm()));
                String ownershipMm = unlocked ? "<" + Palette.SUCCESS + ">✔ Unlocked in Locker</" + Palette.SUCCESS + ">" : "<gray>Price:</gray> " + coinStr(skin.priceCoins());
                String actionLine = selected
                    ? "<" + Palette.SUCCESS + "><bold>✔ Equipped</bold></" + Palette.SUCCESS + ">"
                    : (unlocked
                        ? "<yellow>▸ Click to equip</yellow>"
                        : "<" + Palette.GOLD_SOFT + ">▸ Buy for " + skin.priceCoins() + " Coins</" + Palette.GOLD_SOFT + ">");
                meta.lore(buildBallHoverLore(skin, ownershipMm, actionLine));
                ballItem.setItemMeta(meta);
            }
            inv.setItem(COSMETICS_BALL_SLOTS[i], ballItem);
        }

        DunkStyle[] dunks = DunkStyle.values();
        int[] dunkSlots = {29, 30, 31, 32, 33};
        for (int i = 0; i < dunks.length && i < dunkSlots.length; i++) {
            DunkStyle dunk = dunks[i];
            boolean unlocked = profile.hasUnlockedDunk(dunk) || player.hasPermission("bosketball.cosmetics.all");
            boolean selected = profile.selectedDunkStyle() == dunk;

            String actionLine = selected
                ? "<" + Palette.SUCCESS + "><bold>✔ Equipped</bold></" + Palette.SUCCESS + ">"
                : (unlocked
                    ? "<yellow>▸ Click to equip</yellow>"
                    : "<" + Palette.GOLD_SOFT + ">▸ Buy for " + dunk.priceCoins() + " Coins</" + Palette.GOLD_SOFT + ">");

            ItemStack dunkItem = customIcon(
                dunk.iconModelId(),
                dunk.displayNameMm(),
                List.of(
                    "<gray>" + dunk.descriptionSmallCaps() + "</gray>",
                    unlocked ? "<" + Palette.SUCCESS + ">Unlocked</" + Palette.SUCCESS + ">" : "<gray>Price:</gray> " + coinStr(dunk.priceCoins()),
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

        inv.setItem(48, customIcon(
            "ref_bag",
            "<" + Palette.ICE + "><bold>Collector Chat Tags</bold></" + Palette.ICE + ">",
            List.of("<gray>Equip or unlock Collector Chat Tags</gray>", "", "<aqua>▸ Open Tag Vault</aqua>")
        ));
        inv.setItem(49, backButton(player));
        inv.setItem(50, customIcon(
            "ref_shop",
            "<" + Palette.SUCCESS + "><bold>Shop</bold></" + Palette.SUCCESS + ">",
            List.of("<gray>Browse the full Shop catalog — owned items stay visible</gray>", "", "<green>▸ Open Shop</green>")
        ));
        applyStandardRail(inv, player);
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.22f);
    }

    private static boolean matchesTagFilter(ChatTag tag, TagVaultFilter filter, Player player, PlayerProfile profile) {
        if (filter == null || filter == TagVaultFilter.ALL) {
            return true;
        }
        return switch (filter) {
            case COMMON -> tag.priceCoins() > 0 && tag.priceCoins() <= 250;
            case RARE -> tag.priceCoins() > 250 && tag.priceCoins() <= 500;
            case EPIC -> tag.priceCoins() > 500 && tag.priceCoins() <= 1000;
            case LEGENDARY -> tag.priceCoins() > 1000 || tag.isSpecialUnpurchasable();
            case OWNED -> tag.canBeUsedBy(profile, player != null ? player.getName() : "",
                player != null && fr.bosketball.util.Perms.has(player, fr.bosketball.util.Perms.TAGS_ALL),
                player != null && fr.bosketball.util.Perms.has(player, fr.bosketball.util.Perms.STAFF));
            default -> true;
        };
    }

    public void openTagsVault(Player player) {
        openTagsVault(player, TagVaultFilter.ALL, 0);
    }

    public void openTagsVault(Player player, int requestedPage) {
        openTagsVault(player, TagVaultFilter.ALL, requestedPage);
    }

    public void openTagsVault(Player player, TagVaultFilter requestedFilter, int requestedPage) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        profile.syncExclusiveFounderState();
        TagVaultFilter filter = requestedFilter != null ? requestedFilter : TagVaultFilter.ALL;

        List<ChatTag> allVisible = visibleVaultTags(player, profile);
        List<ChatTag> tags = new ArrayList<>();
        for (ChatTag t : allVisible) {
            if (matchesTagFilter(t, filter, player, profile)) {
                tags.add(t);
            }
        }

        int perPage = SHOP_GRID_SLOTS.length;
        int totalPages = Math.max(1, (tags.size() + perPage - 1) / perPage);
        int page = Math.max(0, Math.min(totalPages - 1, requestedPage));

        BosketHolder holder = new BosketHolder(MenuType.TAGS_VAULT, filter, page);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_TAGS, tr(player, "gui.title.tags", "CHAT TAGS"))
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        inv.setItem(0, customIcon(
            "ref_coins",
            "<" + Palette.GOLD_SOFT + "><bold>Your Balance</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                coinStr(profile.coins()),
                "<gray>Play matches to earn Coins!</gray>"
            )
        ));

        TagVaultFilter[] tabFilters = TagVaultFilter.values();
        for (int i = 0; i < tabFilters.length && (1 + i) <= 6; i++) {
            TagVaultFilter tf = tabFilters[i];
            int count = 0;
            for (ChatTag ct : allVisible) {
                if (matchesTagFilter(ct, tf, player, profile)) {
                    count++;
                }
            }
            boolean active = (tf == filter);
            ItemStack tabBtn = customIcon(
                tf.iconId(),
                (active ? "<" + Palette.SUCCESS + "><bold>● " : "<" + Palette.MUTED + "><bold>") + tf.label() + " (" + count + ")</bold>" + (active ? "</" + Palette.SUCCESS + ">" : "</" + Palette.MUTED + ">"),
                List.of(
                    active ? "<" + Palette.SUCCESS + ">✔ Active Filter</" + Palette.SUCCESS + ">" : "<yellow>▸ Click to filter</yellow>"
                )
            );
            if (active) {
                ItemMeta m = tabBtn.getItemMeta();
                if (m != null) {
                    m.addEnchant(Enchantment.UNBREAKING, 1, true);
                    m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    tabBtn.setItemMeta(m);
                }
            }
            inv.setItem(1 + i, tabBtn);
        }

        inv.setItem(8, customIcon(
            "ref_shop",
            "<" + Palette.GOLD + "><bold>Shop</bold></" + Palette.GOLD + ">",
            List.of("<gray>Browse organized shelves of Balls, Dunks & Tags</gray>", "", "<yellow>▸ Open Shop</yellow>")
        ));

        int startIdx = page * perPage;
        if (tags.isEmpty()) {
            inv.setItem(22, customIcon(
                "ref_bag",
                "<" + Palette.MUTED + "><bold>No Tags in This Filter</bold></" + Palette.MUTED + ">",
                List.of("<gray>Select another rarity tab above or visit the Shop!</gray>")
            ));
        } else {
            for (int i = 0; i < perPage && (startIdx + i) < tags.size(); i++) {
                int slot = SHOP_GRID_SLOTS[i];
                ChatTag tag = tags.get(startIdx + i);
                boolean equipped = profile.selectedTag() == tag;
                boolean unlocked = tag.canBeUsedBy(profile, player.getName(),
                    fr.bosketball.util.Perms.has(player, fr.bosketball.util.Perms.TAGS_ALL),
                    fr.bosketball.util.Perms.has(player, fr.bosketball.util.Perms.STAFF));

                String status = equipped
                    ? "<" + Palette.SUCCESS + "><bold>✔ Equipped (Click to remove)</bold></" + Palette.SUCCESS + ">"
                    : (unlocked
                        ? "<yellow>▸ Click to equip</yellow>"
                        : "<" + Palette.GOLD_SOFT + ">▸ Buy for " + tag.priceCoins() + " Coins</" + Palette.GOLD_SOFT + ">");

                String ownershipLine = tag.isSpecialUnpurchasable()
                    ? "<" + Palette.EMBER + ">▪ Exclusive Honor (Not sold in Shop)</" + Palette.EMBER + ">"
                    : (unlocked ? "<" + Palette.SUCCESS + ">✔ Unlocked</" + Palette.SUCCESS + ">" : "<gray>Price:</gray> " + coinStr(tag.priceCoins()));

                ItemStack tagCard = customIcon(
                    tag.cardModelId(),
                    "<" + Palette.TEXT + "><bold>" + tag.displayName() + "</bold></" + Palette.TEXT + "> <!shadow><white>" + tag.glyph() + "</white></!shadow>",
                    List.of(
                        "<gray>Rarity:</gray> <white>" + tag.tierLabel() + "</white>",
                        "<gray>" + tag.loreHint() + "</gray>",
                        "<gray>Badge:</gray> <!shadow><white>" + tag.glyph() + "</white></!shadow>",
                        "<gray>In Chat:</gray> <!shadow><white>" + profile.effectiveRank().pillGlyph() + "</white></!shadow> <!shadow><white>" + tag.glyph() + "</white></!shadow> <white>" + player.getName() + "</white>",
                        ownershipLine,
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
                holder.tagSlotMap().put(slot, tag);
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
            "ref_exit",
            "<" + Palette.DANGER + "><bold>Unequip Tag</bold></" + Palette.DANGER + ">",
            List.of("<gray>Clear your active chat tag</gray>")
        ));
        inv.setItem(49, backButton(player));
        inv.setItem(50, customIcon(
            "dunk_tomahawk",
            "<" + Palette.VIOLET + "><bold>Basketballs & Dunks</bold></" + Palette.VIOLET + ">",
            List.of("<gray>Switch to Basketball & Dunk Locker</gray>")
        ));
        if (page + 1 < totalPages) {
            inv.setItem(53, customIcon(
                "icon_next",
                "<yellow><bold>Next Page ▸</bold></yellow>",
                List.of("<gray>Page " + (page + 2) + " of " + totalPages + "</gray>")
            ));
        }

        applyStandardRail(inv, player);
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.28f);
    }

    public void openLeaderboards(Player player, LeaderboardCategory category) {
        BosketHolder holder = new BosketHolder(MenuType.LEADERBOARDS, category);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_LEADERBOARDS, tr(player, "gui.title.leaderboards", "LEADERBOARDS"))
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        LeaderboardCategory[] cats = LeaderboardCategory.values();
        String[] catIcons = {"ref_cup_gold", "ref_coins", "ref_trophy", "ref_calendar_check", "icon_dunk", "icon_star_shield", "icon_medal_gold", "ref_trophy"};
        int[] catSlots = {0, 1, 2, 3, 5, 6, 7, 8};
        for (int i = 0; i < cats.length && i < catSlots.length; i++) {
            LeaderboardCategory c = cats[i];
            boolean active = c == category;
            inv.setItem(catSlots[i], customIcon(
                catIcons[i],
                (active ? "<" + Palette.GOLD_SOFT + "><bold>▪ " : "<gray>") + c.label() + (active ? " ▪</bold></" + Palette.GOLD_SOFT + ">" : "</gray>"),
                List.of(active ? "<" + Palette.SUCCESS + ">✔ Viewing</" + Palette.SUCCESS + ">" : "<yellow>▸ Click to view</yellow>")
            ));
        }

        inv.setItem(4, customIcon(
            "ref_medal_gold",
            "<" + Palette.GOLD_SOFT + "><bold>Top #1 / #2 / #3 Server ELO Badges</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<gray>The Top 3 ELO players on the server earn</gray>",
                "<gray>live badges in Chat, /gg, [TAB] & Scoreboard:</gray>",
                "  <!shadow><white>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 1) + "</white></!shadow> <" + Palette.GOLD + "><bold>#1 Gold ELO Champion</bold></" + Palette.GOLD + ">",
                "  <!shadow><white>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 2) + "</white></!shadow> <" + Palette.TEXT + "><bold>#2 Silver ELO Runner-Up</bold></" + Palette.TEXT + ">",
                "  <!shadow><white>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 3) + "</white></!shadow> <" + Palette.AMBER + "><bold>#3 Bronze ELO Contender</bold></" + Palette.AMBER + ">",
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
        applyStandardRail(inv, player);
        player.openInventory(inv);

        for (int i = 0; i < top.size() && i < podiumSlots.length; i++) {
            final PlayerProfile entry = top.get(i);
            final int slot = podiumSlots[i];
            final int place = i + 1;
            if (needsSkinRefresh(entry.uuid())) {
                requestRealHeadAsync(entry.uuid(), entry.lastKnownName(), () -> {
                    if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inv) {
                        return;
                    }
                    inv.setItem(slot, buildLeaderboardSkull(place, entry, category));
                    player.updateInventory();
                });
            }
        }
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.15f);
    }

    /** Slots of the eight chapter cards: two rows of four, symmetric on the 9-column grid. */
    private static final int[] TUTORIAL_CHAPTER_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25};

    /**
     * Tutorial hub (Round 30) — the eight chapters laid out as a symmetric 4×2 board.
     *
     * <p>What changed: the old screen put the eighth card at slot 19, under the first row, so the
     * grid looked broken; it also claimed "the tour advances by itself" (it no longer does) and had
     * no idea whether you were mid-tour. Every card now shows its state, its objective and its
     * reward, and the header shows live progress.
     */
    public void openTutorial(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.TUTORIAL);
        Inventory inv = Bukkit.createInventory(holder, 54,
            Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_HUB, tr(player, "gui.title.tutorial", "NEW PLAYER GUIDE")));
        holder.setInventory(inv);
        fillBackdrop(inv);

        var tuto = plugin.tutorialManager();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        List<TutorialManager.Step> steps = TutorialManager.steps();
        boolean finished = profile.tutorialCompleted();
        int reached = finished ? steps.size() : Math.clamp(profile.tutorialStep(), 0, steps.size());
        boolean running = tuto != null && tuto.isRunning(player);
        int current = running ? tuto.currentStep(player) : -1;
        boolean validated = running && tuto.currentStepValidated(player);

        for (int i = 0; i < steps.size() && i < TUTORIAL_CHAPTER_SLOTS.length; i++) {
            TutorialManager.Step step = steps.get(i);
            boolean done = i < reached;
            boolean active = i == current;
            String marker = active ? "▶" : (done ? "✔" : "○");
            String colour = active ? "<" + Palette.GOLD_SOFT + ">" : (done ? "<" + Palette.SUCCESS + ">" : "<" + Palette.MUTED + ">");
            String state = active
                ? (validated ? "<" + Palette.SUCCESS + "><bold>✔ validated — next chapter incoming</bold></" + Palette.SUCCESS + ">"
                             : "<" + Palette.GOLD_SOFT + "><bold>▶ in progress</bold></" + Palette.GOLD_SOFT + ">")
                : (done ? "<" + Palette.SUCCESS + ">✔ completed</" + Palette.SUCCESS + ">"
                        : "<gray>○ not reached yet</gray>");
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + step.subtitle() + "</gray>");
            lore.add("");
            lore.add("<dark_gray>Objective</dark_gray> <dark_gray>»</dark_gray> <white>" + step.objective() + "</white>");
            lore.add("<dark_gray>Reward</dark_gray> <dark_gray>»</dark_gray> "
                + (step.revealCoins() > 0 ? Msg.coinsBadge(step.revealCoins()) : "<gray>completion bonus</gray>"));
            lore.add("");
            lore.add(state);
            lore.add(active ? "<dark_gray>Click to re-read the chapter</dark_gray>" : "<gray>▸ Click to jump here</gray>");
            inv.setItem(TUTORIAL_CHAPTER_SLOTS[i], customIcon(
                step.iconModel(),
                colour + marker + " <bold>" + (i + 1) + "/" + steps.size() + " " + step.title() + "</bold>",
                lore));
        }

        inv.setItem(4, customIcon("icon_book",
            "<" + Palette.GOLD_SOFT + "><bold>ɴᴇᴡ ᴘʟᴀʏᴇʀ ɢᴜɪᴅᴇ</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<gray>Progress:</gray> <white>" + reached + "/" + steps.size() + "</white>",
                progressBar(reached, steps.size()),
                finished ? "<" + Palette.SUCCESS + ">You finished the tour — free to replay it.</" + Palette.SUCCESS + ">"
                         : "<gray>Finish it once to claim the completion bonus.</gray>",
                "<gray>Chapter rewards:</gray> " + Msg.coinsBadge(plugin.settings().tutorial().completionCoins()
                    + (tuto != null ? tuto.totalStepRewards() : 0)) + " <dark_gray>+ "
                    + plugin.settings().tutorial().completionXp() + " XP</dark_gray>",
                "",
                runnerLine(running, current, steps.size())
            )));

        inv.setItem(45, customIcon("ref_play",
            finished ? "<" + Palette.ICE + "><bold>ʀᴇᴘʟᴀʏ ᴛʜᴇ ᴛᴏᴜʀ</bold></" + Palette.ICE + ">"
                     : "<" + Palette.SUCCESS + "><bold>" + (reached > 0 ? "ʀᴇꜱᴜᴍᴇ ᴛʜᴇ ᴛᴏᴜʀ" : "ꜱᴛᴀʀᴛ ᴛʜᴇ ᴛᴏᴜʀ") + "</bold></" + Palette.SUCCESS + ">",
            List.of("<gray>Cinematic intro, then chapter 1.</gray>",
                "<dark_gray>Chapter rewards are paid once.</dark_gray>", "",
                finished ? "<aqua>▸ Click to replay</aqua>" : "<green>▸ Click to start</green>")));
        inv.setItem(47, customIcon("ref_arrow_right",
            "<" + Palette.ICE + "><bold>ɴᴇxᴛ ᴄʜᴀᴘᴛᴇʀ</bold></" + Palette.ICE + ">",
            List.of("<gray>Skip to the next chapter.</gray>",
                "<dark_gray>Skips never pay the chapter reward.</dark_gray>", "",
                "<aqua>▸ Click to continue</aqua>")));
        inv.setItem(51, customIcon("ref_cross",
            "<" + Palette.DANGER + "><bold>ᴘᴀᴜꜱᴇ ᴛᴜᴛᴏʀɪᴀʟ</bold></" + Palette.DANGER + ">",
            List.of("<gray>Stop here — your progress is saved.</gray>",
                "<gray>Markers, boss bar and effects are cleared.</gray>", "",
                "<red>▸ Click to pause</red>")));
        inv.setItem(49, backButton(player));

        applyStandardRail(inv, player);
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.25f);
    }

    /** One line that tells the player exactly where the tour stands. */
    private static String runnerLine(boolean running, int current, int total) {
        if (!running) {
            return "<gray>Status:</gray> <white>not running</white>";
        }
        return "<gray>Status:</gray> <" + Palette.GOLD_SOFT + "><bold>chapter " + (current + 1) + "/" + total + "</bold></" + Palette.GOLD_SOFT + ">";
    }

    private static String progressBar(int done, int total) {
        int filled = total <= 0 ? 0 : Math.round((float) done / total * 20f);
        StringBuilder bar = new StringBuilder("<dark_gray>[</dark_gray>");
        for (int i = 0; i < 20; i++) {
            bar.append(i < filled ? "<" + Palette.SUCCESS + ">█</" + Palette.SUCCESS + ">" : "<dark_gray>░</dark_gray>");
        }
        bar.append("<dark_gray>]</dark_gray>");
        return bar.toString();
    }

    public void openMatchHistory(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.MATCH_HISTORY);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_HISTORY, tr(player, "gui.title.history", "MATCH HISTORY"))
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        List<MatchHistoryEntry> history = profile.matchHistory();
        if (history.isEmpty()) {
            inv.setItem(22, customIcon(
                "ref_clock",
                "<" + Palette.GOLD + "><bold>No Matches Played Yet</bold></" + Palette.GOLD + ">",
                List.of("<gray>Complete a 1v1, 2v2, 3v3 or 5v5 game</gray>", "<gray>to view your box scores here.</gray>")
            ));
        } else {
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 31};
            for (int i = 0; i < history.size() && i < slots.length; i++) {
                MatchHistoryEntry h = history.get(i);
                String delta = h.eloDelta() >= 0 ? "<" + Palette.SUCCESS + ">+" + h.eloDelta() + " ELO</" + Palette.SUCCESS + ">" : "<" + Palette.DANGER + ">" + h.eloDelta() + " ELO</" + Palette.DANGER + ">";
                inv.setItem(slots[i], customIcon(
                    h.won() ? "ref_cup_gold" : "ref_clock",
                    (h.won() ? "<" + Palette.SUCCESS + "><bold>✔ Victory" : "<" + Palette.DANGER + "><bold>✖ Defeat") + " • " + h.modeKey() + "</bold>",
                    List.of(
                        "<gray>Date:</gray> <white>" + h.formattedDate() + "</white>",
                        "<" + Palette.EMBER + ">Ignis " + h.ignisScore() + "</" + Palette.EMBER + "> <gray>—</gray> <" + Palette.ICE + ">" + h.frostScore() + " Frost</" + Palette.ICE + ">",
                        "<gray>Box Score:</gray> <yellow>" + h.points() + " PTS</yellow> <dark_gray>•</dark_gray> <aqua>" + h.assists() + " AST</aqua> <dark_gray>•</dark_gray> <green>" + h.rebounds() + " REB</green>",
                        "<gray>Rating:</gray> " + delta
                    )
                ));
            }
        }

        inv.setItem(49, backButton(player));
        applyStandardRail(inv, player);
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
    }

    public void openFriendsMenu(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.FRIENDS_MENU);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_FRIENDS, tr(player, "gui.title.friends", "FRIENDS"))
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile myProf = plugin.statsManager().getOrCreate(player);
        int groupSize = plugin.socialManager().partySizeOrOne(player.getUniqueId());

        inv.setItem(3, customIcon(
            "icon_star_shield",
            "<" + Palette.SUCCESS + "><bold>● Friends (" + myProf.friends().size() + ")</bold></" + Palette.SUCCESS + ">",
            List.of("<" + Palette.SUCCESS + ">✔ Viewing Friends List</" + Palette.SUCCESS + ">")
        ));
        inv.setItem(5, customIcon(
            "ref_friends",
            "<" + Palette.GOLD + "><bold>Party & Team Name (" + groupSize + "/4)</bold></" + Palette.GOLD + ">",
            List.of(
                "<gray>Create a Party, invite up to 4 friends,</gray>",
                "<gray>and set your custom</gray> <" + Palette.GOLD + ">Team Name</" + Palette.GOLD + "><gray>!</gray>",
                "",
                "<yellow>▸ Click to switch to Party Menu</yellow>"
            )
        ));
        inv.setItem(7, customIcon(
            "ref_medal_gold",
            "<" + Palette.ICE + "><bold>How Friends Work</bold></" + Palette.ICE + ">",
            List.of(
                "<gray>• Add friends via</gray> <white>➕ Add Online Player</white> <gray>or</gray> <white>/f <name></white>",
                "<gray>• Left-Click an online friend to</gray> <" + Palette.SUCCESS + ">Invite to Party</" + Palette.SUCCESS + ">",
                "<gray>• Shift + Left-Click to</gray> <" + Palette.ICE + ">Challenge to a 1v1 Duel</" + Palette.ICE + ">",
                "<gray>• Right-Click to</gray> <" + Palette.DANGER + ">Remove Friend</" + Palette.DANGER + ">"
            )
        ));

        int[] gridSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };
        int cursor = 0;

        for (java.util.UUID reqId : plugin.socialManager().incomingFriendRequests(player.getUniqueId())) {
            if (cursor >= gridSlots.length) break;
            OfflinePlayer off = Bukkit.getOfflinePlayer(reqId);
            PlayerProfile reqProf = plugin.statsManager().getIfPresent(reqId);
            String rName = off.getName() != null ? off.getName() : (reqProf != null ? reqProf.lastKnownName() : "Player");
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) skull.getItemMeta();
            if (sm != null) {
                Player onlineRequester = Bukkit.getPlayer(reqId);
                applyPlayerHead(sm, onlineRequester != null ? onlineRequester : off, reqId, rName);
                sm.displayName(Msg.parse("<" + Palette.GOLD + "><bold>📩 Request: " + rName + "</bold></" + Palette.GOLD + ">"));
                sm.lore(List.of(
                    Msg.parse("<gray>Sent you a friend request!</gray>"),
                    Msg.parse(""),
                    Msg.parse("<" + Palette.SUCCESS + "><bold>▸ Left-Click: Accept Friend Request</bold></" + Palette.SUCCESS + ">"),
                    Msg.parse("<" + Palette.DANGER + ">▸ Right-Click: Decline</" + Palette.DANGER + ">")
                ));
                skull.setItemMeta(sm);
            }
            inv.setItem(gridSlots[cursor++], skull);
        }

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
                    ? "<" + Palette.GOLD + ">🏀 In Match (" + sess.mode().id().toUpperCase(java.util.Locale.ROOT) + ")</" + Palette.GOLD + ">"
                    : "<" + Palette.SUCCESS + ">● Online — In Lobby</" + Palette.SUCCESS + ">";
            } else {
                statusLine = "<" + Palette.DIM + ">○ Offline</" + Palette.DIM + ">";
            }

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) skull.getItemMeta();
            if (sm != null) {
                applyPlayerHead(sm, off, fid, fName);
                sm.displayName(Msg.parse((onlineFriend != null ? "<" + Palette.SUCCESS + "><bold>" : "<" + Palette.MUTED + "><bold>") + fName + "</bold> <!shadow><white>" + fRank.pillGlyph() + "</white></!shadow>"));
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Msg.parse("<gray>Status:</gray> " + statusLine));
                lore.add(Msg.parse("<gray>Rank:</gray> <white>" + fRank.plainDisplayName() + "</white> <dark_gray>•</dark_gray> <" + Palette.GOLD + ">" + fElo + " ELO</" + Palette.GOLD + ">"));
                lore.add(Msg.parse(""));
                if (onlineFriend != null && onlineFriend.isOnline()) {
                    lore.add(Msg.parse("<" + Palette.SUCCESS + ">▸ Left-Click: Invite to Party</" + Palette.SUCCESS + ">"));
                    lore.add(Msg.parse("<" + Palette.ICE + ">▸ Shift + Left-Click: 1v1 Duel</" + Palette.ICE + ">"));
                }
                lore.add(Msg.parse("<" + Palette.DANGER + ">▸ Right-Click: Remove Friend</" + Palette.DANGER + ">"));
                sm.lore(lore);
                skull.setItemMeta(sm);
            }
            inv.setItem(gridSlots[cursor++], skull);
        }

        if (cursor == 0) {
            inv.setItem(22, customIcon(
                "icon_friend_add",
                "<" + Palette.GOLD + "><bold>No Friends Added Yet</bold></" + Palette.GOLD + ">",
                List.of(
                    "<gray>Click</gray> <" + Palette.SUCCESS + "><bold>➕ Add Online Player</bold></" + Palette.SUCCESS + "> <gray>below</gray>",
                    "<gray>to send a friend request in 1 click!</gray>"
                )
            ));
        }

        inv.setItem(47, customIcon(
            "icon_friend_add",
            "<" + Palette.SUCCESS + "><bold>➕ Add Online Player</bold></" + Palette.SUCCESS + ">",
            List.of("<gray>Browse online players & click their head</gray>", "<gray>to send a friend request!</gray>", "", "<green>▸ Browse Online Players</green>")
        ));
        inv.setItem(48, customIcon(
            "icon_message",
            "<" + Palette.ICE + "><bold>⌨ Type Player Name</bold></" + Palette.ICE + ">",
            List.of("<gray>Type a player's username in chat</gray>", "<gray>to send them a friend request.</gray>", "", "<aqua>▸ Click to type in chat</aqua>")
        ));
        inv.setItem(49, backButton(player));
        inv.setItem(51, customIcon(
            "icon_party_invites",
            "<" + Palette.GOLD + "><bold>🏷 Party & Custom Team Name</bold></" + Palette.GOLD + ">",
            List.of("<gray>Manage your Party & rename your Team!</gray>", "", "<yellow>▸ Open Party Menu</yellow>")
        ));

        applyStandardRail(inv, player);
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
    }

    public void openAddFriendOnlineMenu(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.ADD_FRIEND_ONLINE);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_FRIENDS, tr(player, "gui.title.friends", "FRIENDS"))
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
                "icon_party_unavailable",
                "<" + Palette.MUTED + "><bold>No Other Unfriended Players Online</bold></" + Palette.MUTED + ">",
                List.of("<gray>Everyone online is already on your friends list!</gray>")
            ));
        } else {
            for (int i = 0; i < candidates.size() && i < slots.length; i++) {
                Player other = candidates.get(i);
                PlayerProfile oProf = plugin.statsManager().getOrCreate(other);
                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta sm = (SkullMeta) skull.getItemMeta();
                if (sm != null) {
                    applyPlayerHead(sm, other, other.getUniqueId(), other.getName());
                    sm.displayName(Msg.parse("<" + Palette.ICE + "><bold>" + other.getName() + "</bold></" + Palette.ICE + "> <!shadow><white>" + oProf.effectiveRank().pillGlyph() + "</white></!shadow>"));
                    sm.lore(List.of(
                        Msg.parse("<gray>ELO:</gray> <" + Palette.GOLD + ">" + oProf.elo() + "</" + Palette.GOLD + "> <dark_gray>•</dark_gray> <gray>Rank:</gray> <white>" + oProf.effectiveRank().plainDisplayName() + "</white>"),
                        Msg.parse(""),
                        Msg.parse("<" + Palette.SUCCESS + "><bold>▸ Left-Click: Send Friend Request</bold></" + Palette.SUCCESS + ">"),
                        Msg.parse("<" + Palette.GOLD + ">▸ Right-Click: Invite to Party</" + Palette.GOLD + ">")
                    ));
                    skull.setItemMeta(sm);
                }
                inv.setItem(slots[i], skull);
            }
        }

        inv.setItem(49, customIcon("ref_arrow_left", "<" + Palette.GOLD + "><bold>◂ Back to Friends</bold></" + Palette.GOLD + ">", List.of("<gray>Return to your Friends List</gray>")));
        applyStandardRail(inv, player);
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.25f);
    }

    public void openPartyMenu(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.PARTY_MENU);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_FRIENDS, tr(player, "gui.title.friends", "FRIENDS"))
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile myProf = plugin.statsManager().getOrCreate(player);
        SocialPartyManager.Party party = plugin.socialManager().getParty(player.getUniqueId());
        int partySize = plugin.socialManager().partySizeOrOne(player.getUniqueId());
        boolean isLeader = party == null || party.isLeader(player.getUniqueId());
        String currentTeamName = (party != null && party.hasCustomTeamName()) ? party.teamName() : "Default (Ignis / Frost)";

        inv.setItem(3, customIcon(
            "ref_friends",
            "<" + Palette.SUCCESS + "><bold>Friends (" + myProf.friends().size() + ")</bold></" + Palette.SUCCESS + ">",
            List.of("<gray>View friends, online status & requests</gray>", "", "<green>▸ Switch to Friends List</green>")
        ));

        java.util.UUID incomingPartyLeaderId = plugin.socialManager().pendingPartyInviteLeader(player.getUniqueId());
        if (incomingPartyLeaderId != null) {
            Player inviter = Bukkit.getPlayer(incomingPartyLeaderId);
            if (inviter != null && inviter.isOnline()) {
                ItemStack inviteSkull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta ism = (SkullMeta) inviteSkull.getItemMeta();
                if (ism != null) {
                    applyPlayerHead(ism, inviter, inviter.getUniqueId(), inviter.getName());
                    ism.displayName(Msg.parse("<" + Palette.SUCCESS + "><bold>📩 Party Invite: " + inviter.getName() + "</bold></" + Palette.SUCCESS + ">"));
                    ism.lore(List.of(
                        Msg.parse("<gray>Invited you to join their party!</gray>"),
                        Msg.parse(""),
                        Msg.parse("<" + Palette.SUCCESS + "><bold>▸ Left-Click: Accept & Join Party</bold></" + Palette.SUCCESS + ">"),
                        Msg.parse("<" + Palette.DANGER + ">▸ Right-Click: Decline Invite</" + Palette.DANGER + ">")
                    ));
                    inviteSkull.setItemMeta(ism);
                }
                inv.setItem(4, inviteSkull);
            }
        }

        inv.setItem(5, customIcon(
            "icon_team",
            "<" + Palette.GOLD + "><bold>● Party & Team Name (" + partySize + "/4)</bold></" + Palette.GOLD + ">",
            List.of("<" + Palette.SUCCESS + ">✔ Viewing Party Menu</" + Palette.SUCCESS + ">")
        ));

        inv.setItem(13, customIcon(
            "ref_bag",
            "<" + Palette.GOLD + "><bold>🏷 Party Team Name: </bold></" + Palette.GOLD + "><white><bold>" + currentTeamName + "</bold></white>",
            List.of(
                "<gray>Create your own Team Name (2–16 chars)!</gray>",
                "<gray>Displayed live on the</gray> <white>Scoreboard, Match BossBar,</white>",
                "<white>Court Jumbotron & Locker Room</white> <gray>when your party plays!</gray>",
                "",
                isLeader ? "<" + Palette.SUCCESS + "><bold>▸ Left-Click: Type New Team Name in Chat</bold></" + Palette.SUCCESS + ">" : "<" + Palette.DANGER + ">Only the Party Leader can rename the team</" + Palette.DANGER + ">",
                isLeader ? "<" + Palette.DANGER + ">▸ Right-Click: Reset to Default</" + Palette.DANGER + ">" : "<dark_gray>Command: /party name <TeamName></dark_gray>"
            )
        ));

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
                    applyPlayerHead(sm, m, m.getUniqueId(), m.getName());
                    sm.displayName(Msg.parse((memberIsLeader ? "<" + Palette.GOLD + "><bold>👑 " : "<" + Palette.ICE + "><bold>") + m.getName() + "</bold> <!shadow><white>" + mProf.effectiveRank().pillGlyph() + "</white></!shadow>"));
                    List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                    lore.add(Msg.parse("<gray>Role:</gray> " + (memberIsLeader ? "<" + Palette.GOLD + ">Party Leader</" + Palette.GOLD + ">" : "<white>Party Member</white>")));
                    lore.add(Msg.parse("<gray>ELO:</gray> <" + Palette.GOLD + ">" + mProf.elo() + "</" + Palette.GOLD + ">"));
                    if (isLeader && !m.getUniqueId().equals(player.getUniqueId())) {
                        lore.add(Msg.parse(""));
                        lore.add(Msg.parse("<" + Palette.GOLD + ">▸ Left-Click: Promote to Party Leader</" + Palette.GOLD + ">"));
                        lore.add(Msg.parse("<" + Palette.DANGER + ">▸ Right-Click: Remove from Party</" + Palette.DANGER + ">"));
                    }
                    sm.lore(lore);
                    skull.setItemMeta(sm);
                }
                inv.setItem(memberSlots[i], skull);
            } else {
                inv.setItem(memberSlots[i], customIcon(
                    "icon_party_unavailable",
                    "<" + Palette.DIM + "><bold>Empty Party Slot #" + (i + 1) + "</bold></" + Palette.DIM + ">",
                    List.of("<gray>Click to invite an online player or friend!</gray>", "", "<green>▸ Invite Players</green>")
                ));
            }
        }

        inv.setItem(31, customIcon(
            "icon_party_invites",
            "<" + Palette.SUCCESS + "><bold>➕ Invite Players to Party</bold></" + Palette.SUCCESS + ">",
            List.of("<gray>Invite friends or online players (up to 4)!</gray>", "", "<green>▸ Browse Online Players</green>")
        ));

        inv.setItem(47, customIcon(
            "ref_arrow_big_right",
            "<" + Palette.WARN + "><bold>▶ Play Match Together</bold></" + Palette.WARN + ">",
            List.of("<gray>Queue 2v2, 3v3 or 5v5 together on the same team!</gray>", "", "<yellow>▸ Choose Game Mode</yellow>")
        ));
        if (party != null) {
            inv.setItem(48, customIcon(
                "icon_party_remove",
                "<" + Palette.DANGER + "><bold>Leave Party</bold></" + Palette.DANGER + ">",
                List.of("<gray>Leave or disband your current party</gray>")
            ));
        }
        inv.setItem(49, backButton(player));

        applyStandardRail(inv, player);
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
    }

    public void openPartyInviteMenu(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.PARTY_INVITE_ONLINE);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_PARTY, tr(player, "gui.title.friends", "FRIENDS"))
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
                "icon_party_unavailable",
                "<" + Palette.MUTED + "><bold>No Other Players Available</bold></" + Palette.MUTED + ">",
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
                    applyPlayerHead(sm, other, other.getUniqueId(), other.getName());
                    sm.displayName(Msg.parse((isFriend ? "<" + Palette.SUCCESS + "><bold>▪ " : "<" + Palette.ICE + "><bold>") + other.getName() + "</bold> <!shadow><white>" + oProf.effectiveRank().pillGlyph() + "</white></!shadow>"));
                    sm.lore(List.of(
                        Msg.parse((isFriend ? "<" + Palette.SUCCESS + ">Friend</" + Palette.SUCCESS + "> <dark_gray>•</dark_gray> " : "") + "<gray>ELO:</gray> <" + Palette.GOLD + ">" + oProf.elo() + "</" + Palette.GOLD + ">"),
                        Msg.parse(""),
                        Msg.parse("<" + Palette.SUCCESS + "><bold>▸ Click to Invite to Party</bold></" + Palette.SUCCESS + ">")
                    ));
                    skull.setItemMeta(sm);
                }
                inv.setItem(SHOP_GRID_SLOTS[i], skull);
            }
        }

        inv.setItem(49, customIcon("ref_arrow_left", "<" + Palette.GOLD + "><bold>◂ Back to Party</bold></" + Palette.GOLD + ">", List.of("<gray>Return to Party Menu</gray>")));
        applyStandardRail(inv, player);
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.25f);
    }

    public void openRanksGuide(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.RANKS_GUIDE);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_RANKS, tr(player, "gui.title.ranks", "RANKS GUIDE"))
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        fr.bosketball.model.Rank myCompRank = profile.competitiveRank();
        fr.bosketball.model.Rank nextRank = myCompRank.nextCompetitiveRank();

        String progressText = nextRank != null
            ? "<" + Palette.GOLD + "><bold>" + profile.elo() + "</bold></" + Palette.GOLD + "> <gray>/</gray> <white>" + nextRank.minElo() + " ELO</white> <dark_gray>(+" + Math.max(0, nextRank.minElo() - profile.elo()) + " needed for " + nextRank.plainDisplayName() + ")</dark_gray>"
            : "<" + Palette.GOLD_SOFT + "><bold>" + profile.elo() + " ELO — Maximum Tier Reached (MVP)!</bold></" + Palette.GOLD_SOFT + ">";

        inv.setItem(4, customIcon(
            "ref_cup_gold",
            "<" + Palette.WARN + "><bold>Your Competitive Rating</bold></" + Palette.WARN + ">",
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
                ? "<" + Palette.SUCCESS + "><bold>▪ YOUR CURRENT RANK ▪</bold></" + Palette.SUCCESS + ">"
                : (reached
                    ? "<" + Palette.SUCCESS + ">✔ Unlocked Tier</" + Palette.SUCCESS + ">"
                    : "<" + Palette.GOLD + ">Requires +" + missing + " more ELO (" + r.minElo() + " ELO)</" + Palette.GOLD + ">");

            inv.setItem(rankSlots[i], fr.bosketball.util.TooltipStyles.decorate(customIcon(
                current ? "icon_star_shield" : (reached ? "ref_cup_gold" : "ref_arrow_big_right"),
                "<!shadow><white>" + r.pillGlyph() + "</white></!shadow> <" + Palette.TEXT + "><bold>" + r.plainDisplayName() + "</bold></" + Palette.TEXT + ">",
                List.of(
                    "<gray>Threshold:</gray> <white>" + r.eloRangeDescription() + "</white>",
                    "",
                    stateLine
                )
            ), fr.bosketball.util.TooltipStyles.forRank(r)));
        }

        inv.setItem(38, customIcon(
            "ref_cup_gold",
            "<" + Palette.GOLD + "><bold>Top #1 / #2 / #3 ELO Badges</bold></" + Palette.GOLD + ">",
            List.of(
                "<gray>The 3 highest-ELO players on the entire server</gray>",
                "<gray>automatically wear live Prestige Badges:</gray>",
                "  <!shadow><white>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 1) + "</white></!shadow> <" + Palette.GOLD + ">#1 Gold Badge</" + Palette.GOLD + "> <gray>(1st ELO)</gray>",
                "  <!shadow><white>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 2) + "</white></!shadow> <" + Palette.TEXT + ">#2 Silver Badge</" + Palette.TEXT + "> <gray>(2nd ELO)</gray>",
                "  <!shadow><white>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 3) + "</white></!shadow> <" + Palette.AMBER + ">#3 Bronze Badge</" + Palette.AMBER + "> <gray>(3rd ELO)</gray>",
                "<gray>Shown in Chat, /gg, [TAB], Locker Room & Sidebar!</gray>"
            )
        ));

        inv.setItem(40, customIcon(
            "dunk_meteor_jam",
            "<" + Palette.SUCCESS + "><bold>Manual Skill Controls</bold></" + Palette.SUCCESS + ">",
            List.of(
                "<" + Palette.SUCCESS + ">• Hold Right-Click (Slot 1):</" + Palette.SUCCESS + "> <white>Draw Bow Power (0% → 100%)</white>",
                "  <gray>Release Right-Click at 85%–100% Full Draw for a Green Swish!</gray>",
                "<" + Palette.GOLD + ">• Slam Dunk:</" + Palette.GOLD + "> <white>Get inside the Paint + face the Rim + Click once,</white>",
                "  <gray>the slam resolves by itself (20 s cooldown).</gray>",
                "<" + Palette.ICE + ">• Press Q (or Slot 2):</" + Palette.ICE + "> <white>Quick Pass / Mid-Air Alley-Oop</white>",
                "<" + Palette.VIOLET + ">• Sneak / Shift (or the Crossover item):</" + Palette.VIOLET + "> <white>Directional dash (steer with WASD)</white>",
                "<" + Palette.DANGER + ">• Left-Click on Defense:</" + Palette.DANGER + "> <white>Steal Ball / Jump Block</white>"
            )
        ));

        var econGuide = plugin.settings().economy();
        inv.setItem(42, customIcon(
            "ref_coins",
            "<" + Palette.GOLD_SOFT + "><bold>Coins & Collector Tags</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<gray>• Earn Coins in every match (+" + econGuide.winCoins() + " Win, +" + econGuide.mvpBonusCoins() + " MVP,</gray>",
                "<gray>  +" + econGuide.twoPointCoins() + "/2PT, +" + econGuide.threePointCoins() + "/3PT, +" + econGuide.assistCoins() + "/AST, +" + econGuide.ggRewardCoins() + " GG)!</gray>",
                "<gray>• Spend Coins in the</gray> <yellow>Shop</yellow> <gray>on Balls, Dunks & Tags.</gray>",
                "<gray>• Exclusive Honor Tags:</gray>",
                "  <!shadow><white>" + ChatTag.FOUNDER.glyph() + "</white></!shadow> <" + Palette.EMBER + ">Founder</" + Palette.EMBER + "> <gray>— Exclusive to MaxLananas</gray>",
                "  <!shadow><white>" + ChatTag.MYPINEAPPLE.glyph() + "</white></!shadow> <" + Palette.GOLD + ">MyPineapple</" + Palette.GOLD + "> <gray>— Official Team Exclusive</gray>"
            )
        ));

        inv.setItem(49, backButton(player));
        applyStandardRail(inv, player);
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.22f);
    }

    public void openMapStudio(Player player) {
        if (!Perms.map(player)) {
            Msg.send(player, Msg.error("Arena creation requires <white>bosketball.map</white>."));
            return;
        }
        BosketHolder holder = new BosketHolder(MenuType.MAP_STUDIO);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_STUDIO,
                tr(player, "gui.title.studio", "ARENA STUDIO"))
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        var session = plugin.mapEditor().getOrBindSession(player, null);
        fr.bosketball.arena.Arena arena = session != null ? plugin.arenaManager().getArena(session.arenaId) : null;
        String arenaLabel = arena != null ? arena.id() : "None yet (Click Auto-Setup or Create!)";

        inv.setItem(4, customIcon(
            "ref_arrow_big_right",
            "<" + Palette.ICE + "><bold>Editing Arena: </bold></" + Palette.ICE + "><white><bold>" + arenaLabel + "</bold></white>",
            List.of(
                "<gray>Super-easy 2-step setup on any custom floor:</gray>",
                "<yellow>1.</yellow> <white>Get the Wand</white> <gray>& Left/Right-Click 2 opposite floor corners</gray>",
                "<yellow>2.</yellow> <white>Click</white> <" + Palette.SUCCESS + "><bold>⚡ 1-Click Auto-Setup Full Court</bold></" + Palette.SUCCESS + "><gray>!</gray>",
                "<gray>(Or fine-tune Hoops, Scoreboard & White Lines individually below!)</gray>"
            )
        ));

        inv.setItem(19, customIcon(
            "court_wand",
            "<" + Palette.ICE + "><bold>1. Get Floor Corner Wand</bold></" + Palette.ICE + ">",
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
            "<" + Palette.SUCCESS + "><bold>⚡ 2. 1-Click Auto-Setup Full Court!</bold></" + Palette.SUCCESS + ">",
            List.of(
                "<gray>Automatically places EVERYTHING at once:</gray>",
                "<white>✔ Both Hoops, Backboards & Closed Nets</white>",
                "<white>✔ Flush White Court Lines (3PT, Keys, Half-Court)</white>",
                "<white>✔ Overhead 4-Sided Scoreboard (Jumbotron)</white>",
                "<white>✔ Out-of-Bounds & Invisible Ball Barriers</white>",
                "",
                "<" + Palette.SUCCESS + "><bold>▸ Click to Auto-Build Court Now!</bold></" + Palette.SUCCESS + ">"
            )
        ));

        inv.setItem(23, customIcon(
            "icon_dunk",
            "<" + Palette.DANGER + "><bold>Place Hoops & Backboards Here</bold></" + Palette.DANGER + ">",
            List.of(
                "<gray>Stand where you want a basket & face the court:</gray>",
                "<" + Palette.DANGER + ">▸ Left-Click: Place Ignis Target Hoop Here</" + Palette.DANGER + ">",
                "<" + Palette.ICE + ">▸ Right-Click: Place Frost Target Hoop Here</" + Palette.ICE + ">"
            )
        ));

        boolean linesOn = arena != null && arena.isCourtLinesEnabled();
        String lineMat = arena != null ? arena.courtLinePrimaryMaterial().name() : "WHITE_CONCRETE";
        inv.setItem(25, customIcon(
            "ref_bag",
            "<" + Palette.TEXT + "><bold>White Court Lines: </bold></" + Palette.TEXT + ">" + (linesOn ? "<" + Palette.SUCCESS + "><bold>ON</bold></" + Palette.SUCCESS + ">" : "<" + Palette.DANGER + "><bold>ᴏꜰꜰ</bold></" + Palette.DANGER + ">"),
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
            "ref_cup_gold",
            "<" + Palette.GOLD + "><bold>Overhead Scoreboard: </bold></" + Palette.GOLD + ">" + (jumboOn ? "<" + Palette.SUCCESS + "><bold>ON</bold></" + Palette.SUCCESS + ">" : "<" + Palette.MUTED + "><bold>ᴏꜰꜰ</bold></" + Palette.MUTED + ">"),
            List.of(
                "<gray>Places the 4-sided live Scoreboard</gray>",
                "<gray>at your current position (fly up above center court!).</gray>",
                "",
                "<yellow>▸ Left-Click: Place Scoreboard Here</yellow>",
                "<" + Palette.DANGER + ">▸ Right-Click: Hide Scoreboard</" + Palette.DANGER + ">"
            )
        ));

        inv.setItem(31, customIcon(
            "ref_friends",
            "<" + Palette.SUCCESS + "><bold>Set Team Spawns Here</bold></" + Palette.SUCCESS + ">",
            List.of(
                "<gray>Optional custom spawn override at your feet:</gray>",
                "<" + Palette.DANGER + ">▸ Left-Click: Set Ignis Spawn #1 Here</" + Palette.DANGER + ">",
                "<" + Palette.ICE + ">▸ Right-Click: Set Frost Spawn #1 Here</" + Palette.ICE + ">"
            )
        ));

        inv.setItem(33, customIcon(
            "icon_portal",
            "<" + Palette.VIOLET + "><bold>Build Full Stadium Structure</bold></" + Palette.VIOLET + ">",
            List.of(
                "<gray>Generates a complete hardwood stadium</gray>",
                "<gray>(Blocks + Bleachers + Hoops + Scoreboard)!</gray>",
                "",
                "<light_purple>▸ Shift-Click to Generate Full Stadium</light_purple>"
            )
        ));

        inv.setItem(47, customIcon(
            "ref_gear",
            "<" + Palette.ICE + "><bold>Toggle 3D Particle Blueprint</bold></" + Palette.ICE + ">",
            List.of("<gray>Show/hide live court boundary & hoop particles</gray>", "", "<aqua>▸ Click to toggle</aqua>")
        ));
        inv.setItem(49, backButton(player));
        inv.setItem(51, customIcon(
            "ref_arrow_big_right",
            "<" + Palette.SUCCESS + "><bold>💾 Save & Test in Practice!</bold></" + Palette.SUCCESS + ">",
            List.of("<gray>Saves arena & teleports you onto the court</gray>", "<gray>with a basketball right away!</gray>", "", "<green>▸ Save & Play Now</green>")
        ));

        applyStandardRail(inv, player);
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.75f, 1.2f);
    }

    public void openSpectateBrowser(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.SPECTATE_BROWSER);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_SPECTATE, tr(player, "gui.title.spectate", "SPECTATE"))
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        List<fr.bosketball.game.MatchSession> live = plugin.gameManager().spectatableSessions();
        if (live.isEmpty()) {
            inv.setItem(22, customIcon(
                "icon_spectate",
                "<" + Palette.MUTED + "><bold>No Live Matches Right Now</bold></" + Palette.MUTED + ">",
                List.of("<gray>Start or queue a match to jump onto the court!</gray>")
            ));
        } else {
            for (int i = 0; i < live.size() && i < SHOP_GRID_SLOTS.length; i++) {
                var sess = live.get(i);
                int slot = SHOP_GRID_SLOTS[i];
                holder.slotStringMap().put(slot, sess.arena().id());
                String ignisTeam = sess.teamDisplayName(fr.bosketball.model.TeamSide.IGNIS);
                String frostTeam = sess.teamDisplayName(fr.bosketball.model.TeamSide.FROST);
                inv.setItem(slot, customIcon(
                    "icon_battle",
                    "<" + Palette.ICE + "><bold>🏀 " + sess.arena().displayName() + " • " + sess.mode().smallCapsTitle() + "</bold></" + Palette.ICE + ">",
                    List.of(
                        "<" + Palette.EMBER + "><bold>" + ignisTeam + " " + sess.score(fr.bosketball.model.TeamSide.IGNIS) + "</bold></" + Palette.EMBER + "> <dark_gray>—</dark_gray> <" + Palette.ICE + "><bold>" + sess.score(fr.bosketball.model.TeamSide.FROST) + " " + frostTeam + "</bold></" + Palette.ICE + ">",
                        "<gray>Clock:</gray> <white>" + sess.formattedClock() + "</white> <dark_gray>•</dark_gray> <gray>Players:</gray> <white>" + sess.participantCount() + "</white>",
                        "",
                        "<" + Palette.SUCCESS + "><bold>▸ Click to Spectate Live</bold></" + Palette.SUCCESS + ">"
                    )
                ));
            }
        }

        inv.setItem(49, backButton(player));
        applyStandardRail(inv, player);
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.22f);
    }

    public void openPlayerInteractMenu(Player viewer, Player target) {
        if (viewer == null || target == null || !target.isOnline()) {
            return;
        }
        BosketHolder holder = new BosketHolder(MenuType.PLAYER_INTERACT);
        holder.setTargetPlayerId(target.getUniqueId());
        Inventory inv = Bukkit.createInventory(
            holder,
            27,
            guiTitle(Glyphs.ICON_STAR, "<" + Palette.ICE + "><bold>" + target.getName() + "</bold></" + Palette.ICE + ">")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile myProf = plugin.statsManager().getOrCreate(viewer);
        PlayerProfile tProf = plugin.statsManager().getOrCreate(target);
        boolean alreadyFriends = myProf.isFriend(target.getUniqueId());

        inv.setItem(4, buildPlayerSkullCard(target, tProf));

        inv.setItem(11, customIcon(
            "icon_duel",
            "<" + Palette.WARN + "><bold>⚔ Challenge to 1v1 Duel</bold></" + Palette.WARN + ">",
            List.of(
                "<gray>Send or accept a Ranked 1v1 Duel</gray>",
                "<gray>against <white>" + target.getName() + "</white>!</gray>",
                "",
                "<yellow>▸ Click to Duel</yellow>"
            )
        ));
        inv.setItem(13, customIcon(
            "icon_party_invites",
            "<" + Palette.SUCCESS + "><bold>🎉 Invite to Party</bold></" + Palette.SUCCESS + ">",
            List.of(
                "<gray>Team up in a Party (up to 4 players)</gray>",
                "<gray>& play 2v2, 3v3 or 5v5 together!</gray>",
                "",
                "<green>▸ Click to Invite</green>"
            )
        ));
        inv.setItem(15, customIcon(
            alreadyFriends ? "icon_friend_remove" : "icon_friend_add",
            alreadyFriends
                ? "<" + Palette.SUCCESS + "><bold>✔ Friends with " + target.getName() + "</bold></" + Palette.SUCCESS + ">"
                : "<" + Palette.ICE + "><bold>💚 Add Friend</bold></" + Palette.ICE + ">",
            alreadyFriends
                ? List.of("<gray>Already on your Friends List!</gray>", "", "<" + Palette.DANGER + ">▸ Right-Click to Remove Friend</" + Palette.DANGER + ">")
                : List.of("<gray>Send a friend request in 1 click!</gray>", "", "<aqua>▸ Click to Add Friend</aqua>")
        ));

        viewer.openInventory(inv);
        Msg.playPersonalPackSound(viewer, "ui.click", 0.75f, 1.25f);
    }

    public void openServerAdminPanel(Player admin) {
        if (!Perms.admin(admin)) {
            Msg.send(admin, Msg.error("The Server Control Panel requires <white>bosketball.admin</white>."));
            return;
        }
        BosketHolder holder = new BosketHolder(MenuType.SERVER_ADMIN);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            guiTitle(Glyphs.ICON_CROWN, "<" + Palette.GOLD + "><bold>Server Owner Control Center</bold></" + Palette.GOLD + ">")
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        boolean maint = plugin.settings().motd().maintenanceMode();
        boolean chatMuted = plugin.isGlobalChatMuted();
        boolean building = plugin.protectionManager().isInBuildMode(admin.getUniqueId());

        inv.setItem(10, customIcon(
            maint ? "ref_lock" : "ref_check",
            "<" + Palette.GOLD + "><bold>🛠 Maintenance Mode: </bold></" + Palette.GOLD + ">" + (maint ? "<" + Palette.DANGER + "><bold>ᴀᴄᴛɪᴠᴇ</bold></" + Palette.DANGER + ">" : "<" + Palette.SUCCESS + "><bold>ᴘᴜʙʟɪᴄ ᴏᴘᴇɴ</bold></" + Palette.SUCCESS + ">"),
            List.of(
                "<gray>Locks server logins to Staff/OPs only &</gray>",
                "<gray>displays the Maintenance MOTD banner.</gray>",
                "",
                maint ? "<" + Palette.SUCCESS + ">▸ Click to Reopen Server to Public</" + Palette.SUCCESS + ">" : "<" + Palette.DANGER + ">▸ Click to Enable Maintenance Mode</" + Palette.DANGER + ">"
            )
        ));

        inv.setItem(12, customIcon(
            chatMuted ? "icon_sound_off" : "icon_message",
            "<" + Palette.ICE + "><bold>🔇 Global Chat: </bold></" + Palette.ICE + ">" + (chatMuted ? "<" + Palette.DANGER + "><bold>ᴍᴜᴛᴇᴅ</bold></" + Palette.DANGER + ">" : "<" + Palette.SUCCESS + "><bold>ᴏᴘᴇɴ</bold></" + Palette.SUCCESS + ">"),
            List.of(
                "<gray>Temporarily hush global chat for</gray>",
                "<gray>tournaments or official announcements.</gray>",
                "",
                "<yellow>▸ Click to toggle Global Chat Mute</yellow>"
            )
        ));

        inv.setItem(14, customIcon(
            "court_wand",
            "<" + Palette.VIOLET + "><bold>🧱 Builder Mode: </bold></" + Palette.VIOLET + ">" + (building ? "<" + Palette.SUCCESS + "><bold>ᴇɴᴀʙʟᴇᴅ</bold></" + Palette.SUCCESS + ">" : "<" + Palette.MUTED + "><bold>ᴅɪꜱᴀʙʟᴇᴅ</bold></" + Palette.MUTED + ">"),
            List.of(
                "<gray>Toggles Creative Mode & manual block</gray>",
                "<gray>editing alongside FAWE & Axiom.</gray>",
                "",
                "<light_purple>▸ Click to toggle /build</light_purple>"
            )
        ));

        inv.setItem(16, customIcon(
            "ref_home",
            "<" + Palette.SUCCESS + "><bold>📍 Set Lobby Spawn Here</bold></" + Palette.SUCCESS + ">",
            List.of(
                "<gray>Saves your exact current coordinates &</gray>",
                "<gray>look direction as the server Lobby Spawn.</gray>",
                "",
                "<green>▸ Click to save Lobby Spawn</green>"
            )
        ));

        String shaShort = plugin.resourcePackHost().sha1Hex();
        if (shaShort.length() > 10) {
            shaShort = shaShort.substring(0, 10) + "…";
        }
        inv.setItem(22, customIcon(
            "ref_cup_gold",
            "<" + Palette.GOLD_SOFT + "><bold>📊 Live Network Telemetry</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<gray>Online Players:</gray> <white>" + Bukkit.getOnlinePlayers().size() + "</white>",
                "<gray>In Match:</gray> <" + Palette.SUCCESS + ">" + plugin.gameManager().activePlayerCount() + "</" + Palette.SUCCESS + "> <dark_gray>•</dark_gray> <gray>In Queue:</gray> <" + Palette.ICE + ">" + plugin.gameManager().queuedCount() + "</" + Palette.ICE + ">",
                "<gray>Arenas Registered:</gray> <white>" + plugin.arenaManager().allArenas().size() + "</white>",
                "<gray>Player Profiles:</gray> <white>" + plugin.statsManager().allProfiles().size() + "</white>",
                "<gray>Pack SHA-1:</gray> <aqua>" + (shaShort.isEmpty() ? "N/A" : shaShort) + "</aqua>"
            )
        ));

        inv.setItem(28, customIcon(
            "ref_arrow_big_right",
            "<" + Palette.ICE + "><bold>🏟 Open OP Arena Studio</bold></" + Palette.ICE + ">",
            List.of(
                "<gray>1-Click Auto-Setup Courts, Hoops,</gray>",
                "<gray>White Court Lines & Scoreboards!</gray>",
                "",
                "<aqua>▸ Open Arena Studio</aqua>"
            )
        ));

        inv.setItem(30, customIcon(
            "icon_shelves",
            "<" + Palette.GOLD + "><bold>📦 Push Resource Pack to All</bold></" + Palette.GOLD + ">",
            List.of(
                "<gray>Sends the official Resource Pack prompt</gray>",
                "<gray>to every currently online player.</gray>",
                "",
                "<yellow>▸ Click to broadcast Resource Pack</yellow>"
            )
        ));

        inv.setItem(32, customIcon(
            "ref_chest",
            "<" + Palette.SUCCESS + "><bold>💾 Force-Save All Data Now</bold></" + Palette.SUCCESS + ">",
            List.of(
                "<gray>Atomically flushes all player profiles,</gray>",
                "<gray>arenas & leaderboards to disk.</gray>",
                "",
                "<green>▸ Click to flush to disk</green>"
            )
        ));

        inv.setItem(34, customIcon(
            "icon_reload",
            "<" + Palette.MUTED + "><bold>🔄 Reload Configs & Permissions</bold></" + Palette.MUTED + ">",
            List.of(
                "<gray>Reloads config.yml, shop.yml, spawn.yml,</gray>",
                "<gray>permissions.yml & MOTD live!</gray>",
                "",
                "<white>▸ Click to reload</white>"
            )
        ));

        int holoCount = plugin.lobbyHolograms() != null ? plugin.lobbyHolograms().allHolograms().size() : 0;
        inv.setItem(38, customIcon(
            "trophy_mvp",
            "<" + Palette.GOLD + "><bold>🏆 Place 3D Leaderboard Hologram</bold></" + Palette.GOLD + ">",
            List.of(
                "<gray>Spawns a rotating 3D Trophy + Top 5</gray>",
                "<gray>Leaderboard right where you stand!</gray>",
                "",
                "<yellow>Left-Click:</yellow> <white>Top ELO Hologram</white>",
                "<aqua>Right-Click:</aqua> <white>Top Scorers Hologram</white>"
            )
        ));
        inv.setItem(40, customIcon(
            "prop_play_emblem",
            "<" + Palette.SUCCESS + "><bold>✨ Place 3D Play / Shop Station</bold></" + Palette.SUCCESS + ">",
            List.of(
                "<gray>Spawns an interactive clickable 3D</gray>",
                "<gray>station in the Lobby (" + holoCount + " active)!</gray>",
                "",
                "<green>Left-Click:</green> <white>3D Play Station</white>",
                "<gold>Right-Click:</gold> <white>3D Shop Crate</white>"
            )
        ));
        var hof = plugin.hallOfFame();
        inv.setItem(44, customIcon(
            "ref_cup_gold",
            "<" + Palette.GOLD_SOFT + "><bold>🏛 Physical Hall of Fame</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<gray>A real 3D podium with the skins of</gray>",
                "<gray>the season's #1 / #2 / #3 players.</gray>",
                "",
                "<gray>Current:</gray> <white>" + (hof == null ? "Not placed" : hof.summary()) + "</white>",
                "",
                "<yellow>Left-Click:</yellow> <white>Build it where you stand</white>",
                "<aqua>Right-Click:</aqua> <white>Refresh from leaderboard</white>",
                "<" + Palette.DANGER + ">Shift-Click:</" + Palette.DANGER + "> <white>Remove the podium</white>"
            )
        ));

        inv.setItem(42, customIcon(
            "icon_delete",
            "<" + Palette.DANGER + "><bold>🗑 Remove Nearest 3D Hologram</bold></" + Palette.DANGER + ">",
            List.of(
                "<gray>Deletes the closest 3D Hologram Station</gray>",
                "<gray>within 8 blocks of your position.</gray>",
                "",
                "<" + Palette.DANGER + ">▸ Click to remove nearest</" + Palette.DANGER + ">"
            )
        ));

        inv.setItem(49, backButton(admin));
        admin.openInventory(inv);
        Msg.playPersonalPackSound(admin, "ui.click", 0.75f, 1.2f);
    }

    public void openSettings(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.SETTINGS);
        Inventory inv = Bukkit.createInventory(
            holder,
            45,
            Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_SETTINGS, tr(player, "gui.title.settings", "SETTINGS"))
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        PlayerSettings st = profile.settings();

        inv.setItem(19, settingToggle("icon_dunk", "Court Animations", "Dribble & shot gather animations", st.customAnimations()));
        inv.setItem(20, settingToggle("ref_region", "Team Floor Rings", "Ignis & Frost floor rings on court", st.floorAuraRings()));
        inv.setItem(21, settingToggle(st.customSounds() ? "icon_sound_on" : "icon_sound_off", "Custom Sound Effects", "Swish, buzzer, whistle & court audio", st.customSounds()));
        inv.setItem(22, settingToggle("icon_camera", "Third-Person Visuals", "Show extra cosmetic effects in F5", st.selfBodyRigInF5()));
        inv.setItem(23, settingToggle("icon_replay", "Highlight Replay Camera", "Instant replay camera on clutch buckets", st.cinematicScoreCam()));
        inv.setItem(24, settingToggle("icon_party_invites", "Party Invites", "Allow incoming Party invitations", st.partyInvites()));
        inv.setItem(25, settingToggle("ref_friends", "Status in [TAB]", "Show your live status (In Match, Queue, AFK…)", st.showStatusInTab()));
        inv.setItem(28, settingToggle("ref_trophy", "Quest Notifications", "Toast when a quest or contract completes", st.showQuestNotifications()));
        inv.setItem(29, settingToggle("ref_medal_gold", "Chat Announcements", "Server broadcasts in your chat", st.chatAnnouncements()));
        int soundPercent = Math.round(st.uiSoundVolume() * 100f);
        inv.setItem(30, customIcon(
            "icon_sound_on",
            "<" + Palette.GOLD + "><bold>UI Sound Volume</bold></" + Palette.GOLD + ">",
            List.of(
                "<gray>Current:</gray> <white>" + soundPercent + "%</white>",
                volumeBar(soundPercent),
                "",
                "<gray>Shot-clock ticks, whistles, buzzers,</gray>",
                "<gray>clicks and every pack sound effect.</gray>",
                "",
                "<yellow>▸ Right-click to raise</yellow>",
                "<yellow>▸ Left-click to lower</yellow>"
            )
        ));

        var lang = plugin.lang().forPlayer(player);
        inv.setItem(31, customIcon(
            "ref_medal_gold",
            "<" + Palette.ICE + "><bold>🌐 Language</bold></" + Palette.ICE + ">",
            List.of(
                "<gray>Current:</gray> <white>" + lang.locale().flag() + " " + lang.locale().displayName() + "</white>",
                "<gray>Base language:</gray> <white>English</white>",
                "<gray>Coverage:</gray> <white>" + fr.bosketball.i18n.Lang.coverage(lang.locale()) + "%</white>",
                "",
                "<gray>All menus, messages & broadcasts</gray>",
                "<gray>are translated into your language.</gray>",
                "",
                "<yellow>▸ Click to switch language</yellow>"
            )
        ));

        inv.setItem(40, backButton(player));
        applyStandardRail(inv, player);
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.22f);
    }

    private String volumeBar(int percent) {
        int filled = Math.round(percent / 10f);
        StringBuilder bar = new StringBuilder("<dark_gray>[</dark_gray>");
        for (int i = 0; i < 10; i++) {
            if (i < filled) {
                bar.append(percent <= 20 ? "<red>|</red>" : percent <= 50 ? "<yellow>|</yellow>" : "<" + Palette.SUCCESS + ">|</" + Palette.SUCCESS + ">");
            } else {
                bar.append("<dark_gray>|</dark_gray>");
            }
        }
        return bar.append("<dark_gray>]</dark_gray>").toString();
    }

    private ItemStack settingToggle(String iconName, String title, String desc, boolean enabled) {
        return customIcon(
            iconName,
            "<white><bold>" + title + "</bold></white>",
            List.of(
                "<gray>" + desc + "</gray>",
                "",
                enabled ? "<" + Palette.SUCCESS + "><bold>● Enabled</bold></" + Palette.SUCCESS + ">" : "<" + Palette.DANGER + "><bold>○ Disabled</bold></" + Palette.DANGER + ">",
                "<yellow>▸ Click to toggle</yellow>"
            )
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof PurchaseConfirmMenu confirmMenu) {
            confirmMenu.handleClick(plugin, player, event);
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof BosketHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != holder.getInventory()) {
            return;
        }
        int slot = event.getSlot();

        if (slot == GuiChrome.SLOT_HOME && holder.type() != MenuType.MAIN_HUB) {
            openMainHub(player);
            return;
        }
        if (slot == GuiChrome.SLOT_SEASON && holder.type() != MenuType.SEASON) {
            openSeason(player);
            return;
        }
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

        switch (holder.type()) {
            case MAIN_HUB -> {
                switch (slot) {
                    case 13, 31 -> openRanksGuide(player);
                    case 20 -> openPlayModes(player);
                    case 22 -> {
                        if (event.isRightClick()) {
                            openCosmetics(player);
                        } else {
                            openShop(player);
                        }
                    }
                    case 24 -> {
                        if (event.isRightClick()) {
                            openTagsVault(player, 0);
                        } else {
                            openLockerRoom(player);
                        }
                    }
                    case 29 -> {
                        if (event.isShiftClick()) {
                            openGuildMenu(player);
                        } else if (event.isRightClick()) {
                            openPartyMenu(player);
                        } else {
                            openFriendsMenu(player);
                        }
                    }
                    case 33 -> openLeaderboards(player, LeaderboardCategory.ELO);
                    case 37 -> {
                        player.closeInventory();
                        plugin.gameManager().startPractice(player);
                    }
                    case 39 -> {
                        if (event.isShiftClick()) {
                            openMailbox(player);
                        } else if (event.isRightClick()) {
                            openAchievements(player, null, 0);
                        } else {
                            openQuests(player);
                        }
                    }
                    case 41 -> openMatchHistory(player);
                    case 43 -> openSettings(player);
                    case 45 -> {
                        player.closeInventory();
                        plugin.gameManager().leaveAll(player, true);
                    }
                    case 53 -> {
                        if (Perms.admin(player)) {
                            if (event.isRightClick()) {
                                openMapStudio(player);
                            } else {
                                openServerAdminPanel(player);
                            }
                        } else {
                            Msg.sendDiscordCard(player, plugin.settings().links().discord());
                        }
                    }
                    case GuiChrome.SLOT_BACK -> player.closeInventory();
                    default -> {
                    }
                }
            }
            case SEASON -> {
                if (slot == GuiChrome.SLOT_BACK) {
                    openMainHub(player);
                    return;
                }
                if (slot == 53) {
                    player.closeInventory();
                    return;
                }
                if (slot == 8) {
                    if (plugin.seasonService() != null) {
                        plugin.seasonService().claimPending(player);
                        openSeason(player);
                    }
                    return;
                }
                if (slot >= 36 && slot <= 44) {
                    var targets = seasonStandingTargets.get(player.getUniqueId());
                    int index = slot - 36;
                    if (targets != null && index >= 0 && index < targets.size()) {
                        UUID targetId = targets.get(index);
                        Player target = Bukkit.getPlayer(targetId);
                        if (target != null && target.isOnline()) {
                            openPlayerInteractMenu(player, target);
                        } else {
                            Msg.send(player, Msg.info("That player is offline — the standing still counts."));
                        }
                    }
                }
            }
            case PLAY_MODES -> {
                if (slot == 40) {
                    openBotPanel(player);
                    return;
                }
                switch (slot) {
                    case 10 -> joinModeFromGui(player, GameModeType.DUEL_1V1);
                    case 12 -> joinModeFromGui(player, GameModeType.STREET_2V2);
                    case 14 -> joinModeFromGui(player, GameModeType.PRO_3V3);
                    case 16 -> joinModeFromGui(player, GameModeType.FULL_5V5);
                    case 28 -> {
                        player.closeInventory();
                        plugin.gameManager().startPractice(player);
                    }
                    case 30 -> joinModeFromGui(player, GameModeType.THREE_POINT_CONTEST);
                    case 32 -> {
                        List<fr.bosketball.game.MatchSession> live = plugin.gameManager().spectatableSessions();
                        if (live.isEmpty()) {
                            Msg.sendAlert(player, "No live match is currently running to spectate.");
                            Msg.playPersonalPackSound(player, "ui.click", 0.7f, 0.85f);
                        } else if (live.size() > 1) {
                            openSpectateBrowser(player);
                        } else {
                            player.closeInventory();
                            plugin.gameManager().spectateSession(player, live.get(0));
                        }
                    }
                    case 34 -> {
                        player.closeInventory();
                        openCosmetics(player);
                    }
                    case 49 -> navigateBack(player);
                }
            }
            // Round 33: the AI panel was reachable from /play but had NO click case at all — every
            // button (difficulty, fill, sparring defender, clear, back) silently did nothing.
            case BOT_PANEL -> {
                if (slot == 40 || slot == GuiChrome.SLOT_BACK) {
                    openPlayModes(player);
                    return;
                }
                String action = holder.slotStringMap().get(slot);
                if (action == null) {
                    return;
                }
                fr.bosketball.game.MatchSession botSession =
                    plugin.gameManager().getSessionOf(player.getUniqueId());
                if (action.startsWith("BOT_DIFF:")) {
                    var tier = fr.bosketball.game.bot.BotDifficulty.byNameOrNull(
                        action.substring("BOT_DIFF:".length()));
                    if (tier == null) {
                        return;
                    }
                    if (botSession == null) {
                        Msg.send(player, Msg.info("Start Practice or a match first (<white>/play</white>), "
                            + "then the AI difficulty applies to your court."));
                        return;
                    }
                    botSession.setBotDifficulty(tier);
                    Msg.send(player, Msg.success("AI difficulty set to <white>" + tier.displayName()
                        + "</white> — they spawn on the next fill."));
                    Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
                    openBotPanel(player);
                    return;
                }
                if (botSession == null) {
                    Msg.send(player, Msg.info("You are not on a court yet — start <white>Practice</white> "
                        + "or queue with <white>/play</white>."));
                    return;
                }
                switch (action) {
                    case "BOT_FILL" -> {
                        botSession.fillWithBots(botSession.botDifficulty());
                        Msg.send(player, Msg.success("AI players filled the empty slots."));
                        Msg.playPersonalPackSound(player, "ui.click", 0.8f, 1.25f);
                    }
                    case "BOT_DEFENDER" -> botSession.toggleSparringBot(player);
                    case "BOT_CLEAR" -> {
                        botSession.clearBots();
                        Msg.send(player, Msg.success("Every AI player left the court."));
                        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 0.85f);
                    }
                    case "BACK" -> {
                        openPlayModes(player);
                        return;
                    }
                    default -> {
                        return;
                    }
                }
                openBotPanel(player);
            }
            case SPECTATE_BROWSER -> {
                if (slot == 49) {
                    openPlayModes(player);
                    return;
                }
                String arenaId = holder.slotStringMap().get(slot);
                if (arenaId != null) {
                    var targetSession = plugin.gameManager().getSessionByArena(arenaId);

                    if (targetSession == null) {
                        Msg.send(player, Msg.warn("That match just ended — pick another one."));
                        openSpectateBrowser(player);
                        return;
                    }
                    player.closeInventory();
                    plugin.gameManager().spectateSession(player, targetSession);
                }
            }
            case PLAYER_INTERACT -> {
                java.util.UUID tid = holder.targetPlayerId();
                Player target = tid != null ? Bukkit.getPlayer(tid) : null;
                if (target == null || !target.isOnline()) {
                    player.closeInventory();
                    Msg.send(player, Msg.warn("That player went offline."));
                    return;
                }
                switch (slot) {
                    case 11 -> {
                        player.closeInventory();
                        plugin.gameManager().sendOrAcceptDuel(player, target);
                    }
                    case 13 -> {
                        plugin.socialManager().inviteToParty(player, target);
                        player.closeInventory();
                    }
                    case 15 -> {
                        if (profile.isFriend(target.getUniqueId()) && event.isRightClick()) {
                            plugin.socialManager().removeFriend(player, target.getUniqueId(), target.getName());
                        } else if (!profile.isFriend(target.getUniqueId())) {
                            plugin.socialManager().sendOrAcceptFriendRequest(player, target);
                        }
                        openPlayerInteractMenu(player, target);
                    }
                }
            }
            case SERVER_ADMIN -> {
                if (!Perms.admin(player)) {
                    player.closeInventory();
                    return;
                }
                if (slot == 44) {
                    var hof = plugin.hallOfFame();
                    if (hof == null) {
                        return;
                    }
                    if (event.isShiftClick()) {
                        hof.remove();
                        Msg.send(player, Msg.success("Hall of Fame podium removed."));
                    } else if (event.isRightClick()) {
                        hof.refresh();
                        Msg.send(player, Msg.success("Hall of Fame refreshed from the live leaderboard."));
                    } else {
                        hof.placeHere(player);
                    }
                    openServerAdminPanel(player);
                    return;
                }
                switch (slot) {
                    case 10 -> {
                        boolean next = !plugin.settings().motd().maintenanceMode();
                        plugin.setMaintenanceModeLive(next);
                        Msg.send(player, Msg.success("Maintenance Mode is now " + (next ? "<" + Palette.DANGER + "><bold>ᴀᴄᴛɪᴠᴇ</bold></" + Palette.DANGER + ">" : "<" + Palette.SUCCESS + "><bold>OFF (Public Open)</bold></" + Palette.SUCCESS + ">") + "."));
                        openServerAdminPanel(player);
                    }
                    case 12 -> {
                        boolean nextMute = !plugin.isGlobalChatMuted();
                        plugin.setGlobalChatMuted(nextMute);
                        Bukkit.broadcast(Msg.prefixed(nextMute
                            ? "<" + Palette.DANGER + "><bold>Global chat has been muted by an administrator.</bold></" + Palette.DANGER + ">"
                            : "<" + Palette.SUCCESS + "><bold>Global chat is now open!</bold></" + Palette.SUCCESS + ">"));
                        openServerAdminPanel(player);
                    }
                    case 14 -> {
                        boolean buildNow = plugin.protectionManager().toggleBuildMode(player);
                        Msg.send(player, Msg.success("Builder Mode: " + (buildNow ? "<" + Palette.SUCCESS + ">ENABLED</" + Palette.SUCCESS + ">" : "<" + Palette.DANGER + ">DISABLED</" + Palette.DANGER + ">")));
                        openServerAdminPanel(player);
                    }
                    case 16 -> {
                        plugin.protectionManager().setLobbySpawn(player.getLocation());
                        Msg.send(player, Msg.success("Saved Lobby Spawn at your exact position & rotation!"));
                        openServerAdminPanel(player);
                    }
                    case 28 -> {
                        if (player.isOp()) {
                            openMapStudio(player);
                        } else {
                            Msg.send(player, Msg.error("Arena Studio is strictly OP-only."));
                        }
                    }
                    case 30 -> {
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            plugin.resourcePackHost().sendPackTo(online);
                        }
                        Msg.send(player, Msg.success("Sent official Resource Pack to all " + Bukkit.getOnlinePlayers().size() + " online players."));
                    }
                    case 32 -> {
                        plugin.statsManager().flushDirtyAsync(false);
                        plugin.arenaManager().save();
                        Msg.send(player, Msg.success("Flushed all dirty player profiles & arenas to disk."));
                    }
                    case 34 -> {
                        plugin.reloadBosketConfig();
                        Msg.send(player, Msg.success("Reloaded config.yml, shop.yml, permissions.yml & MOTD live!"));
                        openServerAdminPanel(player);
                    }
                    case 38 -> {
                        if (!Perms.admin(player)) {
                            Msg.sendAlert(player, "Administrator permission required.");
                            return;
                        }
                        var kind = event.isRightClick()
                            ? LobbyHologramManager.StationKind.POINTS
                            : LobbyHologramManager.StationKind.ELO;
                        player.closeInventory();
                        plugin.lobbyHolograms().createStationAt(player.getLocation(), kind);
                        Msg.send(player, Msg.success("Placed 3D <white><bold>" + kind.name() + "</bold></white> Hologram Leaderboard at your location!"));
                    }
                    case 40 -> {
                        if (!Perms.admin(player)) {
                            Msg.sendAlert(player, "Administrator permission required.");
                            return;
                        }
                        var kind = event.isRightClick()
                            ? LobbyHologramManager.StationKind.SHOP
                            : LobbyHologramManager.StationKind.PLAY;
                        player.closeInventory();
                        plugin.lobbyHolograms().createStationAt(player.getLocation(), kind);
                        Msg.send(player, Msg.success("Placed interactive 3D <white><bold>" + kind.name() + "</bold></white> Station at your location!"));
                    }
                    case 42 -> {
                        if (!Perms.admin(player)) {
                            Msg.sendAlert(player, "Administrator permission required.");
                            return;
                        }
                        if (plugin.lobbyHolograms().removeNearest(player.getLocation(), 8.0)) {
                            Msg.send(player, Msg.success("Removed nearest 3D Hologram Station."));
                        } else {
                            Msg.sendAlert(player, "No 3D Hologram Station within 8 blocks.");
                        }
                        openServerAdminPanel(player);
                    }
                    case 49 -> navigateBack(player);
                }
            }
            case SHOP -> {
                ShopCategory cat = holder.shopCategory();
                int page = holder.page();
                if (slot == 0) {
                    ShopSort nextSort = shopSortOf(player.getUniqueId()).next();
                    setShopSort(player.getUniqueId(), nextSort);
                    Msg.playPersonalPackSound(player, "ui.click", 0.75f, 1.25f);
                    openShop(player, cat, page);
                    return;
                }
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
                if (slot == 49) {
                    navigateBack(player);
                    return;
                }
                if (slot == 48) {
                    openTagsVault(player, 0);
                    return;
                }
                if (slot == 8 || slot == 50) {
                    openShop(player);
                    return;
                }
                BallSkin[] balls = BallSkin.values();
                for (int i = 0; i < COSMETICS_BALL_SLOTS.length && i < balls.length; i++) {
                    if (slot == COSMETICS_BALL_SLOTS[i]) {
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
                TagVaultFilter filter = holder.tagFilter();
                int page = holder.page();
                if (slot >= 1 && slot <= 6) {
                    TagVaultFilter[] tabFilters = TagVaultFilter.values();
                    openTagsVault(player, tabFilters[slot - 1], 0);
                    return;
                }
                if (slot == 8) {
                    openShop(player);
                    return;
                }
                if (slot == 45 && page > 0) {
                    openTagsVault(player, filter, page - 1);
                    return;
                }
                if (slot == 53) {
                    openTagsVault(player, filter, page + 1);
                    return;
                }
                if (slot == 48) {
                    profile.clearEquippedTags();
                    plugin.statsManager().saveProfileAsync(profile);
                    notifyLiveUiRefresh(player);
                    Msg.send(player, Msg.info("Unequipped your active chat tag."));
                    openTagsVault(player, filter, page);
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
                ChatTag clickedTag = holder.tagSlotMap().get(slot);
                if (clickedTag != null) {
                    handleTagClick(player, profile, clickedTag, filter, page);
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
            case TUTORIAL -> {
                var tuto = plugin.tutorialManager();
                int[] chapterSlots = TUTORIAL_CHAPTER_SLOTS;
                for (int i = 0; i < chapterSlots.length; i++) {
                    if (slot == chapterSlots[i]) {
                        player.closeInventory();
                        if (tuto != null) {
                            tuto.gotoStep(player, i);
                        }
                        return;
                    }
                }
                switch (slot) {
                    case 45 -> {
                        player.closeInventory();
                        if (tuto != null) {
                            tuto.restart(player);
                        }
                    }
                    case 47 -> {
                        player.closeInventory();
                        if (tuto != null) {
                            if (tuto.isRunning(player)) {
                                tuto.advance(player, true);
                            } else {
                                tuto.start(player);
                            }
                        }
                    }
                    case 51 -> {
                        player.closeInventory();
                        if (tuto != null) {
                            tuto.stop(player, false);
                        }
                    }
                    default -> {
                    }
                }
            }
            case MATCH_HISTORY, RANKS_GUIDE -> {
                if (slot == 49) {
                    navigateBack(player);
                }
            }
            case FRIENDS_PARTY, FRIENDS_MENU, FRIENDS_RICH -> {
                if (slot == 5 || slot == 51) {
                    openPartyMenu(player);
                    return;
                }
                if (slot == 47) {
                    openAddFriendOnlineMenu(player);
                    return;
                }

                if (slot == 53) {
                    navigateBack(player);
                    return;
                }
                if (slot >= 10 && slot <= 43) {

                    String meta = holder.slotStringMap().get(slot);
                    if (meta != null && meta.startsWith("FRIEND:")) {
                        java.util.UUID friendId;
                        try {
                            friendId = java.util.UUID.fromString(meta.substring("FRIEND:".length()));
                        } catch (IllegalArgumentException ignored) {
                            return;
                        }
                        Player other = Bukkit.getPlayer(friendId);
                        if (other == null) {
                            if (profile.isFriend(friendId)) {
                                plugin.socialManager().removeFriend(player, friendId, friendId.toString());
                                openFriendsRich(player);
                            }
                            return;
                        }
                        if (event.isShiftClick()) {
                            if (!profile.isFriend(friendId)) {
                                Msg.send(player, Msg.warn(other.getName() + " is not in your friends list yet."));
                            } else if (plugin.gameManager().isInMatch(player.getUniqueId())) {
                                Msg.send(player, Msg.warn("You cannot change your party while in a match."));
                            } else {
                                plugin.socialManager().inviteToParty(player, other);
                            }
                        } else if (!profile.isFriend(friendId)) {
                            plugin.socialManager().sendOrAcceptFriendRequest(player, other);
                            openFriendsRich(player);
                        } else if (event.isRightClick()) {
                            plugin.socialManager().removeFriend(player, friendId, other.getName());
                            openFriendsRich(player);
                        } else {
                            openPlayerInteractMenu(player, other);
                        }
                        return;
                    }
                }
                if (slot == 48) {
                    if (holder.type() == MenuType.FRIENDS_RICH) {
                        openPartyInviteMenu(player);
                    } else {
                        plugin.socialManager().beginChatPrompt(player, SocialPartyManager.ChatPromptType.ADD_FRIEND_NAME);
                    }
                    return;
                }
                if (slot == 50) {
                    openMailbox(player);
                    return;
                }
                if (slot == 49) {
                    navigateBack(player);
                    return;
                }
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() instanceof SkullMeta sm) {
                    java.util.UUID tid = skullTargetId(sm);
                    if (tid != null) {
                        String tName = skullTargetName(sm);
                        if (plugin.socialManager().incomingFriendRequests(player.getUniqueId()).contains(tid)) {
                            if (event.isRightClick()) {
                                plugin.socialManager().declineFriendRequest(player, tid, tName);
                            } else {
                                Player onlineReq = Bukkit.getPlayer(tid);
                                if (onlineReq != null) {

                                    if (!profile.isFriend(tid)) {
                                        plugin.socialManager().acceptFriendRequest(player, onlineReq);
                                    }
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
                                plugin.gameManager().sendOrAcceptDuel(player, onlineFriend);
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
                    java.util.UUID targetId = skullTargetId(sm);
                    if (targetId != null) {
                        Player onlineTarget = Bukkit.getPlayer(targetId);
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
                    java.util.UUID targetId = skullTargetId(sm);
                    if (targetId != null) {
                        Player onlineTarget = Bukkit.getPlayer(targetId);
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
                        java.util.UUID targetId = skullTargetId(sm);
                        if (targetId != null && !targetId.equals(player.getUniqueId())) {
                            if (event.isRightClick()) {
                                plugin.socialManager().kickFromParty(player, targetId);
                            } else {
                                plugin.socialManager().promotePartyLeader(player, targetId);
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
                if (!Perms.map(player)) {
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
                if (slot == 31) {
                    var next = plugin.lang().nextLanguage(player);
                    plugin.statsManager().saveProfileAsync(profile);
                    Msg.send(player, Msg.success("Language set to <white>"
                        + next.locale().flag() + " " + next.locale().displayName() + "</white>."));
                    openSettings(player);
                    return;
                }
                if (slot == 30) {
                    float current = st.uiSoundVolume();
                    float next = event.isRightClick()
                        ? Math.min(1.0f, current + 0.1f)
                        : Math.max(0.0f, current - 0.1f);
                    st.setUiSoundVolume(next);
                    plugin.statsManager().saveProfileAsync(profile);
                    Msg.playPersonalPackSound(player, "ui.click", 0.9f, 1.1f + next * 0.4f);
                    openSettings(player);
                    return;
                }
                switch (slot) {
                    case 19 -> st.setCustomAnimations(!st.customAnimations());
                    case 20 -> st.setFloorAuraRings(!st.floorAuraRings());
                    case 21 -> st.setCustomSounds(!st.customSounds());
                    case 22 -> st.setSelfBodyRigInF5(!st.selfBodyRigInF5());
                    case 23 -> st.setCinematicScoreCam(!st.cinematicScoreCam());
                    case 24 -> st.setPartyInvites(!st.partyInvites());
                    case 25 -> st.setShowStatusInTab(!st.showStatusInTab());
                    case 28 -> st.setShowQuestNotifications(!st.showQuestNotifications());
                    case 29 -> st.setChatAnnouncements(!st.chatAnnouncements());
                    default -> {
                        return;
                    }
                }
                plugin.statsManager().saveProfileAsync(profile);
                notifyLiveUiRefresh(player);
                openSettings(player);
            }

            case QUESTS -> {
                if (slot == 53) {
                    navigateBack(player);
                    return;
                }
                if (slot == 49) {
                    int earned = plugin.progressService().claimCompletedQuests(player, profile);
                    if (earned <= 0) {
                        Msg.send(player, Msg.warn("No completed quest to claim yet — keep playing!"));
                    } else {
                        Msg.send(player, Msg.success("Quests claimed: <white>+" + earned + " Coins</white>"));
                    }
                    openQuests(player);
                    return;
                }

                List<PlayerQuest> clickedBoard = null;
                int clickedIndex = -1;
                if (slot >= 10 && slot <= 13) {
                    clickedBoard = profile.dailyQuests();
                    clickedIndex = slot - 10;
                } else if (slot >= 19 && slot <= 22) {
                    clickedBoard = profile.weeklyQuests();
                    clickedIndex = slot - 19;
                } else if (slot == 28 || slot == 29) {
                    clickedBoard = profile.contracts();
                    clickedIndex = slot - 28;
                }
                if (clickedBoard != null) {
                    if (clickedIndex >= clickedBoard.size()) {
                        return;
                    }
                    PlayerQuest clicked = clickedBoard.get(clickedIndex);
                    if (clicked == null) {
                        return;
                    }
                    if (clicked.claimed()) {
                        Msg.send(player, Msg.info("That quest has already been claimed."));
                        return;
                    }
                    if (!clicked.isComplete()) {
                        Msg.send(player, Msg.warn("Not finished yet: <white>"
                            + clicked.progress() + "/" + clicked.target() + "</white>."));
                        return;
                    }
                    clicked.setClaimed(true);
                    clicked.setRewarded(true);
                    profile.addEarnedCoins(clicked.rewardCoins());
                    profile.addXp(clicked.rewardXp());
                    plugin.statsManager().markDirty(player.getUniqueId());
                    plugin.statsManager().saveProfileAsync(profile);
                    plugin.progressService().evaluateAchievements(profile, true);
                    Msg.playPersonalPackSound(player, "ui.click", 1.0f, 1.45f);
                    Msg.send(player, Msg.success("Quest claimed: <white>+" + clicked.rewardCoins()
                        + " Coins</white>, <white>+" + clicked.rewardXp() + " XP</white>."));
                    openQuests(player);
                    return;
                }
                if (slot == 32) {
                    PlayerQuest rerolled = plugin.progressService().rerollDailyQuest(profile, System.currentTimeMillis());
                    if (rerolled == null) {
                        Msg.send(player, Msg.error("Cannot reroll: no open daily quest or not enough Coins."));
                    } else {
                        Msg.playPersonalPackSound(player, "ui.click", 0.9f, 1.3f);
                        Msg.send(player, Msg.success("New daily quest: <white>" + rerolled.title() + "</white>"));
                    }
                    openQuests(player);
                    return;
                }
                if (slot == 31) {
                    PlayerQuest rolled = plugin.progressService().rerollContract(profile, System.currentTimeMillis());
                    if (rolled == null) {
                        Msg.send(player, Msg.error("Not enough Coins to reroll this contract."));
                    } else {
                        Msg.playPersonalPackSound(player, "ui.click", 0.9f, 1.25f);
                        Msg.send(player, Msg.success("New contract: <white>" + rolled.title() + "</white>"));
                    }
                    openQuests(player);
                }
            }

            case ACHIEVEMENTS -> {
                if (slot == 53) {
                    navigateBack(player);
                    return;
                }
                String filter = holder.slotStringMap().get(1000);
                int page = parsePage(holder.slotStringMap().get(1001));
                if (slot == 48 && "PREV".equals(holder.slotStringMap().get(48))) {
                    openAchievements(player, filter == null || filter.isEmpty() ? null : filter, Math.max(0, page - 1));
                    return;
                }
                if (slot == 50 && "NEXT".equals(holder.slotStringMap().get(50))) {
                    openAchievements(player, filter == null || filter.isEmpty() ? null : filter, page + 1);
                    return;
                }
                int[] tabSlots = {36, 37, 38, 39, 40, 41, 42};
                Achievement.AchievementCategory[] cats = Achievement.AchievementCategory.values();
                for (int i = 0; i < cats.length && i < tabSlots.length; i++) {
                    if (slot == tabSlots[i]) {
                        String next = (filter != null && filter.equalsIgnoreCase(cats[i].name())) ? null : cats[i].name();
                        openAchievements(player, next, 0);
                        return;
                    }
                }
            }

            case MAILBOX -> {
                if (slot == 53) {
                    navigateBack(player);
                    return;
                }
                if (slot == 48) {
                    plugin.socialService().markAllRead(profile);
                    plugin.statsManager().saveProfileAsync(profile);
                    openMailbox(player);
                    return;
                }
                if (slot == 50) {
                    plugin.socialService().clearMail(profile);
                    plugin.statsManager().saveProfileAsync(profile);
                    openMailbox(player);
                    return;
                }
                String mailKey = holder.slotStringMap().get(slot);
                if (mailKey != null && mailKey.startsWith("MAIL:")) {
                    int index = parsePage(mailKey.substring(5));
                    List<OfflineMail> mails = plugin.socialService().mails(profile);
                    if (index >= 0 && index < mails.size()) {
                        OfflineMail mail = mails.get(index);

                        plugin.socialService().markRead(profile, index);
                        if (mail.hasAction() && mail.actionCommand() != null && !mail.actionCommand().isBlank()) {
                            player.closeInventory();
                            player.performCommand(mail.actionCommand().startsWith("/")
                                ? mail.actionCommand().substring(1)
                                : mail.actionCommand());
                            return;
                        }
                        openMailbox(player);
                    }
                }
            }

            case GUILDS -> {
                new GuildGuis(plugin).handle(player, holder.slotStringMap().get(slot), event);
            }

            case WARNINGS -> {
                if (slot == 53) {
                    navigateBack(player);
                    return;
                }
                String warnKey = holder.slotStringMap().get(slot);
                if (warnKey == null) {
                    return;
                }
                if (warnKey.startsWith("WARNPLAYER:")) {
                    openWarningsPanel(player, java.util.UUID.fromString(warnKey.substring(11)));
                    return;
                }
                if (warnKey.equals("WARNALL")) {
                    openWarningsPanel(player, null);
                    return;
                }
                if (warnKey.startsWith("WARNTARGET:")) {
                    java.util.UUID id = java.util.UUID.fromString(warnKey.substring(11));
                    int removed;
                    if (event.isRightClick()) {
                        removed = plugin.warnService().clear(id);
                    } else {
                        removed = plugin.warnService().unwarnLast(id) ? 1 : 0;
                    }
                    if (removed == 0) {
                        Msg.send(player, Msg.warn("No warning left to remove for <white>"
                            + plugin.warnService().nameOf(id) + "</white>."));
                    } else {
                        Msg.send(player, Msg.success("Removed <white>" + removed + "</white> warning"
                            + (removed == 1 ? "" : "s") + " from <gold>"
                            + plugin.warnService().nameOf(id) + "</gold>."));
                    }
                    openWarningsPanel(player, plugin.warnService().count(id) > 0 ? id : null);
                    return;
                }
                if (warnKey.startsWith("WARNENTRY:")) {
                    String[] parts = warnKey.substring(10).split(":", 2);
                    if (parts.length == 2) {
                        java.util.UUID id = java.util.UUID.fromString(parts[0]);
                        boolean removed = plugin.warnService().unwarn(id, parts[1]);
                        Msg.send(player, removed
                            ? Msg.success("Warning removed.")
                            : Msg.warn("That warning no longer exists."));
                        openWarningsPanel(player, plugin.warnService().count(id) > 0 ? id : null);
                    }
                    return;
                }
            }

            case REPORTS -> {
                if (slot == 53) {
                    navigateBack(player);
                    return;
                }
                String reportKey = holder.slotStringMap().get(slot);
                if (reportKey != null && reportKey.startsWith("REPORT:")) {
                    String id = reportKey.substring(7);
                    Report target = null;
                    for (Report r : plugin.reportService().openReports()) {
                        if (String.valueOf(r.id()).equals(id)) {
                            target = r;
                            break;
                        }
                    }
                    if (target != null) {
                        plugin.reportService().resolve(target, player.getName(),
                            event.isRightClick() ? "DISMISSED" : "RESOLVED");
                        Msg.send(player, Msg.success("Report <white>#" + id + "</white> "
                            + (event.isRightClick() ? "dismissed." : "resolved.")));
                    }
                    openReportsPanel(player);
                }
            }
        }
    }

    private static int parsePage(String raw) {
        if (raw == null || raw.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String shopEntryTitle(ShopItemEntry entry) {
        if (entry.ballSkin() != null) {
            return stripMiniTags(entry.ballSkin().displayNameMm());
        }
        if (entry.dunkStyle() != null) {
            return stripMiniTags(entry.dunkStyle().displayNameMm());
        }
        if (entry.chatTag() != null) {
            return entry.chatTag().displayName();
        }
        return "Item";
    }

    static String stripMiniTags(String raw) {
        return raw == null ? "" : raw.replaceAll("<[^>]+>", "").trim();
    }

    private void handleShopEntryPurchase(Player player, PlayerProfile profile, ShopItemEntry entry, ShopCategory cat, int page) {
        if (entry == null || (entry.ballSkin() == null && entry.dunkStyle() == null && entry.chatTag() == null)) {
            return;
        }

        if (isShopEntryOwned(player, profile, entry)) {
            boolean alreadyEquipped = isShopEntryEquipped(profile, entry);
            if (entry.ballSkin() != null) {
                profile.setSelectedBallSkin(entry.ballSkin());
                Msg.send(player, alreadyEquipped
                    ? Msg.info(entry.ballSkin().displayNameMm() + " <gray>is already equipped.</gray>")
                    : Msg.success("Equipped owned " + entry.ballSkin().displayNameMm() + "."));
            } else if (entry.dunkStyle() != null) {
                profile.setSelectedDunkStyle(entry.dunkStyle());
                Msg.send(player, alreadyEquipped
                    ? Msg.info(entry.dunkStyle().displayNameMm() + " <gray>is already equipped.</gray>")
                    : Msg.success("Equipped owned " + entry.dunkStyle().displayNameMm() + "."));
            } else if (entry.chatTag() != null) {
                profile.setSelectedTag(entry.chatTag());
                Msg.send(player, alreadyEquipped
                    ? Msg.info("<white>" + entry.chatTag().displayName() + "</white> <gray>is already equipped.</gray>")
                    : Msg.success("Equipped owned tag <!shadow><white>" + entry.chatTag().glyph() + "</white></!shadow> <white>" + entry.chatTag().displayName() + "</white>."));
            }
            if (!alreadyEquipped) {
                plugin.statsManager().saveProfileAsync(profile);
                notifyLiveUiRefresh(player);
            }
            Msg.playPersonalPackSound(player, "ui.click", 0.75f, alreadyEquipped ? 1.05f : 1.25f);
            openShop(player, cat, page);
            return;
        }

        int price = entry.priceCoins();
        if (profile.coins() < price) {
            int missing = price - profile.coins();
            Msg.send(player, Msg.error("Not enough Coins! You need <" + Palette.GOLD_SOFT + ">" + missing + " more Coins</" + Palette.GOLD_SOFT + ">."));
            Msg.playPersonalPackSound(player, "game.buzzer", 0.55f, 1.35f);
            return;
        }
        final ShopItemEntry shopEntry = entry;
        final ShopCategory shopCat = cat;
        final int shopPage = page;
        PurchaseConfirmMenu.setCancelAction(player, () -> openShop(player, shopCat, shopPage));
        PurchaseConfirmMenu.open(plugin, player, new PurchaseConfirmMenu.Purchase(
            shopEntryTitle(entry),
            "shop item",
            buildShopEntryCard(player, profile, entry),
            price,
            "Purchased for <" + Palette.GOLD_SOFT + ">" + price + " Coins</" + Palette.GOLD_SOFT + ">!",
            buyer -> applyShopEntryPurchase(buyer, shopEntry, shopCat, shopPage)
        ));
        return;
    }

    private void applyShopEntryPurchase(Player player, ShopItemEntry entry, ShopCategory cat, int page) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        int price = entry.priceCoins();

        if (entry.ballSkin() != null) {
            BallSkin skin = entry.ballSkin();
            profile.unlockBall(skin);
            profile.setSelectedBallSkin(skin);
            Msg.send(player, Msg.success("Purchased & equipped " + skin.displayNameMm() + " for <" + Palette.GOLD_SOFT + ">" + price + " Coins</" + Palette.GOLD_SOFT + ">!"));
        } else if (entry.dunkStyle() != null) {
            DunkStyle dunk = entry.dunkStyle();
            profile.unlockDunk(dunk);
            profile.setSelectedDunkStyle(dunk);
            Msg.send(player, Msg.success("Purchased & equipped " + dunk.displayNameMm() + " for <" + Palette.GOLD_SOFT + ">" + price + " Coins</" + Palette.GOLD_SOFT + ">!"));
        } else if (entry.chatTag() != null) {
            ChatTag tag = entry.chatTag();
            profile.unlockTag(tag);
            profile.setSelectedTag(tag);
            Msg.send(player, Msg.success("Purchased & equipped <!shadow><white>" + tag.glyph() + "</white></!shadow> <white>" + tag.displayName() + "</white> for <" + Palette.GOLD_SOFT + ">" + price + " Coins</" + Palette.GOLD_SOFT + ">!"));
        }

        plugin.statsManager().saveProfileAsync(profile);
        notifyLiveUiRefresh(player);
        Msg.send(player, Msg.info("<gray>Saved to My Locker — its Shop card stays here as</gray> <" + Palette.SUCCESS + ">Owned & Equipped</" + Palette.SUCCESS + "><gray>.</gray>"));
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
                Msg.send(player, Msg.info("3D Particle Blueprint: " + (active ? "<" + Palette.SUCCESS + ">ON</" + Palette.SUCCESS + ">" : "<" + Palette.DANGER + ">OFF</" + Palette.DANGER + ">")));
                openMapStudio(player);
            }
            case 49 -> navigateBack(player);
            case 51 -> {
                var s = plugin.mapEditor().getOrBindSession(player, null);
                fr.bosketball.arena.Arena arena = s != null ? plugin.arenaManager().getArena(s.arenaId) : plugin.mapEditor().autoSetupFullCourt(player);
                if (arena != null) {
                    arena.spawnDisplays();
                    plugin.arenaManager().save();
                    player.closeInventory();
                    player.performCommand("bb practice " + arena.id());
                }
            }
            default -> {
            }
        }
    }

    private void handleTagClick(Player player, PlayerProfile profile, ChatTag tag, TagVaultFilter filter, int page) {
        if (tag.isSpecialUnpurchasable()) {
            if (!tag.canBeUsedBy(profile, player.getName(), false,
                    fr.bosketball.util.Perms.has(player, fr.bosketball.util.Perms.STAFF))) {
                Msg.send(player, Msg.error(tag.isStaffExclusive()
                    ? "The <white>MODS</white> tag is reserved for the staff team (<white>bosketball.staff</white>)."
                    : "This exclusive tag is not available for purchase."));
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
            openTagsVault(player, filter, page);
            return;
        }

        boolean unlocked = tag.canBeUsedBy(profile, player.getName(),
            fr.bosketball.util.Perms.has(player, fr.bosketball.util.Perms.TAGS_ALL),
            fr.bosketball.util.Perms.has(player, fr.bosketball.util.Perms.STAFF));
        if (!unlocked) {

            final ChatTag target = tag;
            final TagVaultFilter backFilter = filter;
            final int backPage = page;
            final int price = tag.priceCoins();
            if (profile.coins() < price) {
                int missing = price - profile.coins();
                Msg.send(player, Msg.error("Not enough Coins! You need <" + Palette.GOLD_SOFT + ">" + missing + " more Coins</" + Palette.GOLD_SOFT + "> to unlock <white>" + tag.displayName() + "</white>."));
                Msg.playPersonalPackSound(player, "game.buzzer", 0.55f, 1.35f);
                return;
            }
            PurchaseConfirmMenu.setCancelAction(player, () -> openTagsVault(player, backFilter, backPage));
            PurchaseConfirmMenu.open(plugin, player, new PurchaseConfirmMenu.Purchase(
                tag.displayName(),
                tag.tierLabel() + " chat tag",
                customIcon(tag.cardModelId(), "<white><bold>" + tag.displayName() + "</bold></white>",
                    java.util.List.of("<gray>Rarity:</gray> <white>" + tag.tierLabel() + "</white>",
                        "<gray>Shows next to your name in chat.</gray>")),
                price,
                "Unlocked & equipped <!shadow><white>" + tag.glyph() + "</white></!shadow> <white>" + tag.displayName() + "</white> for <" + Palette.GOLD_SOFT + ">" + price + " Coins</" + Palette.GOLD_SOFT + ">!",
                buyer -> {
                    PlayerProfile fresh = plugin.statsManager().getOrCreate(buyer);
                    fresh.unlockTag(target);
                    fresh.setSelectedTag(target);
                    notifyLiveUiRefresh(buyer);
                    openTagsVault(buyer, backFilter, backPage);
                }
            ));
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
        openTagsVault(player, filter, page);
    }

    private void handleBallClick(Player player, PlayerProfile profile, BallSkin skin) {
        boolean unlocked = profile.hasUnlockedBall(skin) || player.hasPermission("bosketball.cosmetics.all");
        if (!unlocked) {
            int price = skin.priceCoins();
            if (profile.coins() < price) {
                int missing = price - profile.coins();
                Msg.send(player, Msg.error("Not enough Coins! You need <" + Palette.GOLD_SOFT + ">" + missing + " more Coins</" + Palette.GOLD_SOFT + "> to unlock " + skin.displayNameMm() + "."));
                Msg.playPersonalPackSound(player, "game.buzzer", 0.55f, 1.35f);
                return;
            }
            final BallSkin chosenBall = skin;
            PurchaseConfirmMenu.setCancelAction(player, () -> openCosmetics(player));
            PurchaseConfirmMenu.open(plugin, player, new PurchaseConfirmMenu.Purchase(
                stripMiniTags(skin.displayNameMm()),
                "basketball",
                skin.createItemStack(),
                price,
                "Purchased & equipped " + skin.displayNameMm() + " for <" + Palette.GOLD_SOFT + ">" + price + " Coins</" + Palette.GOLD_SOFT + ">!",
                buyer -> {
                    PlayerProfile fresh = plugin.statsManager().getOrCreate(buyer);
                    fresh.unlockBall(chosenBall);
                    fresh.setSelectedBallSkin(chosenBall);
                    notifyLiveUiRefresh(buyer);
                    openCosmetics(buyer);
                }
            ));
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
            if (profile.coins() < price) {
                int missing = price - profile.coins();
                Msg.send(player, Msg.error("Not enough Coins! You need <" + Palette.GOLD_SOFT + ">" + missing + " more Coins</" + Palette.GOLD_SOFT + "> to unlock " + dunk.displayNameMm() + "."));
                Msg.playPersonalPackSound(player, "game.buzzer", 0.55f, 1.35f);
                return;
            }
            final DunkStyle chosenDunk = dunk;
            PurchaseConfirmMenu.setCancelAction(player, () -> openCosmetics(player));
            PurchaseConfirmMenu.open(plugin, player, new PurchaseConfirmMenu.Purchase(
                stripMiniTags(dunk.displayNameMm()),
                "signature dunk",
                customIcon(dunk.iconModelId(), dunk.displayNameMm(),
                    java.util.List.of("<gray>Your finish at the rim.</gray>")),
                price,
                "Purchased & equipped " + dunk.displayNameMm() + " for <" + Palette.GOLD_SOFT + ">" + price + " Coins</" + Palette.GOLD_SOFT + ">!",
                buyer -> {
                    PlayerProfile fresh = plugin.statsManager().getOrCreate(buyer);
                    fresh.unlockDunk(chosenDunk);
                    fresh.setSelectedDunkStyle(chosenDunk);
                    notifyLiveUiRefresh(buyer);
                    openCosmetics(buyer);
                }
            ));
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
        if (event.getView().getTopInventory().getHolder() instanceof BosketHolder
            || event.getView().getTopInventory().getHolder() instanceof PurchaseConfirmMenu) {
            event.setCancelled(true);
        }
    }

    private void notifyLiveUiRefresh(Player player) {
        if (plugin.sidebarManager() != null) {
            plugin.sidebarManager().clearPlayerCache(player.getUniqueId());
        }
        if (plugin.tabListManager() != null) {
            plugin.tabListManager().updateAll(plugin.gameManager());
        }
    }

    public void navigateBackFromSubMenu(Player player) {
        navigateBack(player);
    }

    private void navigateBack(Player player) {
        openMainHub(player);
    }

    private void joinModeFromGui(Player player, GameModeType mode) {
        if (mode == GameModeType.THREE_POINT_CONTEST) {
            player.closeInventory();
            plugin.gameManager().joinQueue(player, mode);
            return;
        }
        player.closeInventory();
        if (plugin.gameManager().getQueuedMode(player.getUniqueId()) == mode) {
            plugin.gameManager().leaveQueue(player, true);
        } else {
            plugin.gameManager().joinQueue(player, mode);
        }
    }

    private final Map<UUID, List<UUID>> seasonStandingTargets = new ConcurrentHashMap<>();

    /** Drops the per-player UI memory (shop sort/search, season snapshot) when they leave. */
    public void forgetPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        shopSortModes.remove(playerId);
        shopSearchQueries.remove(playerId);
        seasonStandingTargets.remove(playerId);
    }

    public void openSeason(Player player) {
        openSeason(player, 0);
    }

    public void openSeason(Player player, int page) {
        Inventory inv = makeSeasonInventory(player);
        if (inv == null) {
            Msg.send(player, Msg.warn("Ranked seasons are disabled on this server."));
            return;
        }
        applyStandardRail(inv, player);
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.6f, 1.1f);
    }

    private Inventory makeSeasonInventory(Player player) {
        var season = plugin.seasonService();
        if (season == null || season.state() == null) {
            return null;
        }
        fr.bosketball.season.SeasonState state = season.state();
        BosketHolder holder = new BosketHolder(MenuType.SEASON);
        Inventory inv = Bukkit.createInventory(
            holder,
            54,
            Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_SEASON, tr(player, "gui.title.season", "RANKED SEASON"))
        );
        holder.setInventory(inv);
        fillBackdrop(inv);

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        UUID id = player.getUniqueId();
        int required = season.placementsRequired();
        int done = Math.max(0, required - season.placementsRemaining(id));
        int seasonBest = season.bestElo(id);
        int shownElo = seasonBest > 0 ? seasonBest : profile.elo();
        fr.bosketball.model.Rank tier = fr.bosketball.season.SeasonMath.tierFor(shownElo);
        boolean provisional = season.isProvisional(id);
        fr.bosketball.season.SeasonRecord record = season.record(id);
        var pending = season.pendingReward(id);

        inv.setItem(0, customIcon("ref_trophy",
            "<" + Palette.GOLD_SOFT + "><bold>" + state.name() + "</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<gray>Ranked cycle:</gray> <white>" + state.name() + "</white>",
                "<gray>Ends in:</gray> <" + Palette.GOLD + "><bold>" + state.remainingLabel() + "</bold></" + Palette.GOLD + ">",
                "<gray>Server seed:</gray> <white>" + plugin.settings().season().durationDays() + " days</white>",
                "",
                "<dark_gray>Every ranked match feeds this season's ladder.</dark_gray>"
            )));

        int percent = state.progressPercent();
        int filled = Math.round(percent / 10.0f);
        StringBuilder bar = new StringBuilder("<" + Palette.WARN + "><bold>");
        for (int i = 0; i < 10; i++) {
            if (i == filled) {
                bar.append("</" + Palette.WARN + "><dark_gray><bold>");
            }
            bar.append('|');
        }
        bar.append("</bold></dark_gray>");
        inv.setItem(2, customIcon("ref_calendar_check",
            "<" + Palette.ICE + "><bold>ꜱᴇᴀꜱᴏɴ ᴘʀᴏɢʀᴇꜱꜱ</bold></" + Palette.ICE + ">",
            List.of(
                "<gray>" + bar + "</gray>",
                "<white>" + percent + "%</white> <gray>of the cycle elapsed</gray>",
                "",
                required > 0
                    ? (provisional
                        ? "<" + Palette.GOLD + ">Placements:</" + Palette.GOLD + "> <white>" + done + "/" + required + "</white> <gray>— ELO swings x2</gray>"
                        : "<" + Palette.SUCCESS + ">Placed</" + Palette.SUCCESS + "> <gray>— your tier is official</gray>")
                    : "<gray>No placement requirement this season.</gray>"
            )));

        inv.setItem(4, customIcon(rankIconModel(tier),
            "<" + hexOf(tier.accentColor()) + "><bold>YOUR TIER: " + tier.plainDisplayName().toUpperCase(java.util.Locale.ROOT) + "</bold></" + hexOf(tier.accentColor()) + ">",
            List.of(
                "<gray>Season peak:</gray> <" + Palette.ICE + "><bold>" + shownElo + " ELO</bold></" + Palette.ICE + ">",
                "<gray>Live rating:</gray> <white>" + profile.elo() + "</white>" + (provisional ? " <" + Palette.GOLD + ">(provisional)</" + Palette.GOLD + ">" : ""),
                "<gray>Next tier:</gray> <white>" + nextTierText(shownElo) + "</white>",
                "",
                provisional
                    ? "<" + Palette.GOLD + ">Finish your placements to lock this tier in.</" + Palette.GOLD + ">"
                    : "<gray>Tier locked for the season reward payout.</gray>"
            )));

        int tierIndex = fr.bosketball.season.SeasonMath.competitiveTierIndex(tier);
        int projectedCoins = seasonProjectedCoins(tierIndex);
        inv.setItem(6, customIcon("ref_coins",
            "<" + Palette.GOLD_SOFT + "><bold>ꜱᴇᴀꜱᴏɴ ᴘᴀʏᴏᴜᴛ</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<gray>If the season ended now:</gray>",
                "<" + Palette.GOLD_SOFT + ">+" + projectedCoins + " Coins</" + Palette.GOLD_SOFT + "> <dark_gray>•</dark_gray> <" + Palette.ICE + ">+" + seasonProjectedXp(tierIndex) + " XP</" + Palette.ICE + ">",
                "<dark_gray>Podium bonus:</dark_gray> <white>+"
                    + plugin.settings().season().top1Coins() + " / +"
                    + plugin.settings().season().top2Coins() + " / +"
                    + plugin.settings().season().top3Coins() + "</white>",
                "",
                "<gray>Trophies won:</gray> <" + Palette.GOLD_SOFT + ">" + season.trophies(id) + "</" + Palette.GOLD_SOFT + ">"
            )));

        if (pending != null) {
            inv.setItem(8, customIcon("ref_check",
                "<" + Palette.SUCCESS + "><bold>ᴄʟᴀɪᴍ ʀᴇᴡᴀʀᴅꜱ</bold></" + Palette.SUCCESS + ">",
                List.of(
                    "<gray>A past season payout is waiting:</gray>",
                    "<" + Palette.GOLD_SOFT + ">+" + pending.coins() + " Coins</" + Palette.GOLD_SOFT + "> <dark_gray>•</dark_gray> <" + Palette.ICE + ">+" + pending.xp() + " XP</" + Palette.ICE + ">",
                    "<gray>Source:</gray> <white>" + pending.label() + "</white>",
                    "",
                    "<yellow>▸ Click to claim</yellow>"
                )));
        } else {
            inv.setItem(8, customIcon("ref_lock",
                "<" + Palette.MUTED + "><bold>ɴᴏ ᴘᴇɴᴅɪɴɢ ᴘᴀʏᴏᴜᴛ</bold></" + Palette.MUTED + ">",
                List.of("<gray>Season rewards are paid automatically</gray>", "<gray>when the cycle closes.</gray>")));
        }

        List<fr.bosketball.model.Rank> track = List.of(
            fr.bosketball.model.Rank.ROOKIE, fr.bosketball.model.Rank.HOOPER, fr.bosketball.model.Rank.PRO,
            fr.bosketball.model.Rank.ALL_STAR, fr.bosketball.model.Rank.SUPERSTAR, fr.bosketball.model.Rank.LEGEND,
            fr.bosketball.model.Rank.MVP
        );
        for (int i = 0; i < track.size(); i++) {
            fr.bosketball.model.Rank rank = track.get(i);
            int slot = 19 + i;
            boolean current = rank == tier;
            boolean reached = shownElo >= rank.minElo();
            String accent = hexOf(rank.accentColor());
            String tierOpen = current ? "<" + Palette.GOLD_SOFT + "><bold>▶ "
                : reached ? "<" + accent + "><bold>" : "<" + Palette.DIM + "><bold>";
            String tierClose = current ? "</" + Palette.GOLD_SOFT + ">" : reached ? "</" + accent + ">" : "</" + Palette.DIM + ">";
            ItemStack tierItem = customIcon(rankIconModel(rank),
                tierOpen + rank.plainDisplayName().toUpperCase(java.util.Locale.ROOT) + "</bold>" + tierClose,
                List.of(
                    "<gray>ELO range:</gray> <white>" + rank.minElo() + " – " + fr.bosketball.season.SeasonMath.tierCeiling(rank) + "</white>",
                    "<gray>Reward:</gray> <" + Palette.GOLD_SOFT + ">+" + seasonProjectedCoins(i) + " Coins</" + Palette.GOLD_SOFT + "> <dark_gray>•</dark_gray> <" + Palette.ICE + ">+" + seasonProjectedXp(i) + " XP</" + Palette.ICE + ">",
                    "",
                    current ? "<" + Palette.GOLD_SOFT + ">▸ You are here</" + Palette.GOLD_SOFT + ">"
                        : reached ? "<" + Palette.SUCCESS + ">✔ Reached this season</" + Palette.SUCCESS + ">"
                        : "<gray>Keep climbing to unlock this payout.</gray>"
                ));
            if (current) {
                ItemMeta meta = tierItem.getItemMeta();
                if (meta != null) {
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    tierItem.setItemMeta(meta);
                }
            }
            inv.setItem(slot, tierItem);
        }

        int matches = record == null ? 0 : record.matches();
        int wins = record == null ? 0 : record.wins();
        double winRate = record == null ? 0.0 : record.winRatePercent();
        inv.setItem(28, customIcon("ref_clock",
            "<" + Palette.ICE + "><bold>ꜱᴇᴀꜱᴏɴ ʀᴇᴄᴏʀᴅ</bold></" + Palette.ICE + ">",
            List.of(
                "<gray>Ranked matches:</gray> <white>" + matches + "</white>",
                "<gray>Record:</gray> <" + Palette.SUCCESS + ">" + wins + "W</" + Palette.SUCCESS + "> <dark_gray>-</dark_gray> <" + Palette.DANGER + ">" + Math.max(0, matches - wins) + "L</" + Palette.DANGER + ">",
                "<gray>Win rate:</gray> <white>" + String.format(java.util.Locale.ROOT, "%.1f%%", winRate) + "</white>",
                "",
                "<dark_gray>Only ranked queues count toward the season.</dark_gray>"
            )));

        inv.setItem(30, customIcon("ref_cup_gold",
            "<" + Palette.GOLD_SOFT + "><bold>ꜱᴇᴀꜱᴏɴ ꜱᴛᴀɴᴅɪɴɢꜱ</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<gray>Best peak rating of the cycle.</gray>",
                "<gray>Top 3 earn a trophy and a podium bonus.</gray>",
                "",
                "<yellow>▸ Click a player below to inspect</yellow>"
            )));

        inv.setItem(32, customIcon("ref_upgrade",
            "<" + Palette.VIOLET + "><bold>ꜱᴏꜰᴛ ʀᴇꜱᴇᴛ</bold></" + Palette.VIOLET + ">",
            List.of(
                "<gray>At the final whistle every rating is pulled</gray>",
                "<gray>back toward</gray> <white>" + plugin.settings().season().softResetAnchor() + " ELO</white> <gray>and</gray>",
                "<white>" + Math.round(plugin.settings().season().softResetKeepRatio() * 100) + "%</white> <gray>of your climb survives.</gray>",
                "",
                "<dark_gray>Placements reopen — the ladder is never settled.</dark_gray>"
            )));

        var standings = season.standings(9);
        List<UUID> targets = new ArrayList<>();
        for (int i = 0; i < standings.size(); i++) {
            var standing = standings.get(i);
            targets.add(standing.uuid());
            String placeColor = switch (i) {
                case 0 -> Palette.GOLD_SOFT;
                case 1 -> Palette.MUTED;
                case 2 -> Palette.AMBER;
                default -> Palette.MUTED;
            };
            ItemStack head = headIcon(standing.uuid(), standing.name(),
                "<" + placeColor + "><bold>#" + standing.place() + "</bold></" + placeColor + "> <white>" + standing.name() + "</white>",
                List.of(
                    "<gray>Peak:</gray> <" + Palette.ICE + "><bold>" + standing.bestElo() + " ELO</bold></" + Palette.ICE + ">",
                    "<gray>Tier:</gray> <white>" + standing.tier().plainDisplayName() + "</white>"
                        + (standing.provisional() ? " <" + Palette.GOLD + ">(placements)</" + Palette.GOLD + ">" : ""),
                    "<gray>Record:</gray> <" + Palette.SUCCESS + ">" + standing.wins() + "W</" + Palette.SUCCESS + "> <dark_gray>-</dark_gray> <" + Palette.DANGER + ">"
                        + Math.max(0, standing.matches() - standing.wins()) + "L</" + Palette.DANGER + ">",
                    "",
                    "<yellow>▸ Click to inspect</yellow>"
                ));
            inv.setItem(36 + i, head);
        }
        seasonStandingTargets.put(id, targets);

        GuiChrome.applyRail(inv, GuiMenuManager::customIcon,
            "Return to the lobby hub", true, true, false, state.name(), null, state.remainingLabel());
        inv.setItem(53, GuiChrome.closeIcon(GuiMenuManager::customIcon));
        return inv;
    }

    private int seasonProjectedCoins(int tierIndex) {
        var rewards = plugin.settings().season().tierCoins();
        if (rewards == null || rewards.isEmpty()) {
            return 0;
        }
        int idx = Math.clamp(tierIndex, 0, rewards.size() - 1);
        Integer value = rewards.get(idx);
        return value == null ? 0 : Math.max(0, value);
    }

    private int seasonProjectedXp(int tierIndex) {
        var rewards = plugin.settings().season().tierXp();
        if (rewards == null || rewards.isEmpty()) {
            return 0;
        }
        int idx = Math.clamp(tierIndex, 0, rewards.size() - 1);
        Integer value = rewards.get(idx);
        return value == null ? 0 : Math.max(0, value);
    }

    private String nextTierText(int elo) {
        fr.bosketball.model.Rank next = fr.bosketball.season.SeasonMath.tierFor(elo).nextCompetitiveRank();
        if (next == null) {
            return "MAX TIER";
        }
        int remaining = Math.max(0, next.minElo() - elo);
        return "+" + remaining + " ELO → " + next.plainDisplayName();
    }

    private static String rankIconModel(fr.bosketball.model.Rank rank) {
        return switch (rank) {
            case ROOKIE -> "ref_upgrade";
            case HOOPER -> "ref_calendar_check";
            case PRO -> "icon_defense";
            case ALL_STAR -> "icon_medal_gold";
            case SUPERSTAR -> "icon_star_shield";
            case LEGEND -> "ref_trophy";
            case MVP -> "ref_cup_gold";
            default -> "icon_guild";
        };
    }

    private static String hexOf(net.kyori.adventure.text.format.TextColor color) {
        return color == null ? Palette.TEXT : String.format(java.util.Locale.ROOT, "#%06X", color.value() & 0xFFFFFF);
    }

    private static boolean isFiller(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasItemModel()
            && "bosketball".equals(meta.getItemModel().getNamespace())
            && "ui_invisible".equals(meta.getItemModel().getKey());
    }

    private void applyStandardRail(Inventory inv, Player player) {
        if (inv == null || inv.getSize() != 54 || player == null) {
            return;
        }
        var season = plugin.seasonService();
        String seasonName = season == null || season.state() == null
            ? plugin.settings().general().seasonName()
            : season.state().name();
        PlayerProfile profile = plugin.statsManager().getIfPresent(player.getUniqueId());
        String status = null;
        String remaining = null;
        if (season != null && season.state() != null) {
            remaining = season.state().remainingLabel();
            status = profile == null ? null : season.seasonStatusLine(profile);
        }
        if (isFiller(inv.getItem(GuiChrome.SLOT_BACK))) {
            inv.setItem(GuiChrome.SLOT_BACK, GuiChrome.backIcon(GuiMenuManager::customIcon, "Return to the lobby hub"));
        }
        if (isFiller(inv.getItem(GuiChrome.SLOT_HOME))) {
            inv.setItem(GuiChrome.SLOT_HOME, GuiChrome.homeIcon(GuiMenuManager::customIcon, null));
        }
        if (isFiller(inv.getItem(GuiChrome.SLOT_SEASON))) {
            inv.setItem(GuiChrome.SLOT_SEASON, GuiChrome.seasonIcon(GuiMenuManager::customIcon, seasonName, status, remaining));
        }
    }

    private ItemStack backButton(Player player) {
        return customIcon("ref_arrow_left", "<" + Palette.GOLD + "><bold>◂ Back</bold></" + Palette.GOLD + ">",
            List.of("<gray>Return to BosketBall menu</gray>"));
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

    public ItemStack headIcon(java.util.UUID ownerId, String ownerName, String nameMm, List<String> loreMm) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.SkullMeta skull) {
            Player live = ownerId == null ? null : Bukkit.getPlayer(ownerId);
            applyPlayerHead(skull, live, ownerId, ownerName);
            skull.displayName(Msg.parse(Glyphs.smallCapsLabels(nameMm)));
            List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
            for (String line : loreMm) {
                lore.add(Msg.parse(Glyphs.smallCapsLabels(line)));
            }
            skull.lore(lore);
            head.setItemMeta(skull);
        }
        return head;
    }

    public static ItemStack customIcon(String modelName, String nameMm, List<String> loreMm) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey("bosketball", modelName));
            meta.displayName(Msg.parse(Glyphs.smallCapsLabels(nameMm)));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>(loreMm.size());
            for (String line : loreMm) {
                lore.add(Msg.parse(Glyphs.smallCapsLabels(line)));
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
            applyPlayerHead(meta, player, player.getUniqueId(), player.getName());
            fr.bosketball.model.Rank compRank = p.competitiveRank();
            fr.bosketball.model.Rank nextRank = compRank.nextCompetitiveRank();
            String nextLine = nextRank != null
                ? "<gray>Next Rank:</gray> <" + Palette.GOLD + ">" + p.elo() + "/" + nextRank.minElo() + " ELO</" + Palette.GOLD + "> <dark_gray>(→ " + nextRank.plainDisplayName() + ")</dark_gray>"
                : "<gray>Rank Progress:</gray> <" + Palette.GOLD_SOFT + ">MAX TIER (MVP)</" + Palette.GOLD_SOFT + ">";
            meta.displayName(Msg.parse("<" + Palette.WARN + "><bold>" + player.getName() + "</bold></" + Palette.WARN + "> <!shadow><white>" + p.effectiveRank().pillGlyph() + "</white></!shadow>"));
            meta.lore(List.of(
                Msg.parse("<gray>Competitive Tier:</gray> <white>" + compRank.plainDisplayName() + "</white>"),
                Msg.parse(nextLine),
                Msg.parse("<gray>Coins:</gray> " + coinStr(p.coins())),
                Msg.parse("<gray>Wins:</gray> <" + Palette.SUCCESS + ">" + p.wins() + "</" + Palette.SUCCESS + "> <dark_gray>•</dark_gray> <gray>Win Rate:</gray> <white>" + String.format(java.util.Locale.ROOT, "%.1f%%", p.winRatePercent()) + "</white>"),
                Msg.parse("<gray>Points:</gray> <white>" + p.points() + "</white> <dark_gray>•</dark_gray> <gray>3PT:</gray> <aqua>" + p.threePointers() + "</aqua> <dark_gray>•</dark_gray> <gray>Dunks:</gray> <red>" + p.dunks() + "</red>"),
                Msg.parse("<gray>Assists:</gray> <" + Palette.SUCCESS + ">" + p.assists() + "</" + Palette.SUCCESS + "> <dark_gray>•</dark_gray> <gray>Steals:</gray> <light_purple>" + p.steals() + "</light_purple>"),
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

            Player online = Bukkit.getPlayer(p.uuid());
            applyPlayerHead(meta, online, p.uuid(), p.lastKnownName());
            String medal = switch (place) {
                case 1 -> "<" + Palette.GOLD + "><bold>🥇 #1 ";
                case 2 -> "<" + Palette.TEXT + "><bold>🥈 #2 ";
                case 3 -> "<" + Palette.AMBER + "><bold>🥉 #3 ";
                default -> "<gray><bold>#" + place + " ";
            };
            String topEloGlyph = (cat == LeaderboardCategory.ELO && place >= 1 && place <= 3)
                ? " <!shadow><white>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, place) + "</white></!shadow>"
                : "";
            meta.displayName(Msg.parse(medal + p.lastKnownName() + "</bold> <!shadow><white>" + p.effectiveRank().pillGlyph() + "</white></!shadow>" + topEloGlyph));
            meta.lore(List.of(
                Msg.parse("<gray>" + cat.label() + ":</gray> <" + Palette.GOLD_SOFT + "><bold>" + cat.extract(p) + "</bold></" + Palette.GOLD_SOFT + ">"),
                Msg.parse("<gray>ELO:</gray> <" + Palette.GOLD + ">" + p.elo() + "</" + Palette.GOLD + "> <dark_gray>•</dark_gray> <gray>Wins:</gray> <" + Palette.SUCCESS + ">" + p.wins() + "</" + Palette.SUCCESS + ">")
            ));
            skull.setItemMeta(meta);
        }
        return skull;
    }
}
