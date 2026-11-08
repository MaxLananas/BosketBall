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
import fr.bosketball.ui.kit.MenuSpec;
import fr.bosketball.ui.kit.MenuItems;
import fr.bosketball.ui.kit.NavigationStack;
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
        TUTORIAL,
        INTERACTION_ERROR
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
        private int page;
        private final Map<Integer, ShopItemEntry> shopSlotMap = new HashMap<>();
        private final Map<Integer, ChatTag> tagSlotMap = new HashMap<>();
        private final Map<Integer, String> slotStringMap = new HashMap<>();
        private java.util.UUID targetPlayerId;
        private Inventory inventory;
        private MenuSpec spec;
        private net.kyori.adventure.text.Component title;
        private String view = "";

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

        public void setPage(int page) {
            this.page = Math.max(0, page);
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

        public MenuSpec spec() { return spec; }
        public void setSpec(MenuSpec spec) { this.spec = spec; }
        public net.kyori.adventure.text.Component title() { return title; }
        public void setTitle(net.kyori.adventure.text.Component title) { this.title = title; }
        public String view() { return view; }
        public void setView(String view) { this.view = view == null ? "" : view; }

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
        return Msg.parse("<!shadow><#F8FAFC>" + iconGlyph + "</#F8FAFC></!shadow> " + titleMm);
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
        return "<!shadow><#F8FAFC>" + Glyphs.CHAT_ICON_COIN + "</#F8FAFC></!shadow> <" + Palette.GOLD_SOFT + "><bold>" + amount + " Coins</bold></" + Palette.GOLD_SOFT + ">";
    }

    private static List<net.kyori.adventure.text.Component> buildBallHoverLore(BallSkin skin, String ownershipMm, String actionMm) {
        String r0 = "<!shadow><#F8FAFC>" + Glyphs.ballPreviewStrip(skin, 0) + "</#F8FAFC></!shadow>  <#64748B>3D Square Basketball</#64748B>";
        String r1 = "<!shadow><#F8FAFC>" + Glyphs.ballPreviewStrip(skin, 1) + "</#F8FAFC></!shadow>  <#94A3B8>" + skin.loreLine() + "</#94A3B8>";
        String r2 = "<!shadow><#F8FAFC>" + Glyphs.ballPreviewStrip(skin, 2) + "</#F8FAFC></!shadow>  " + ownershipMm;
        String r3 = "<!shadow><#F8FAFC>" + Glyphs.ballPreviewStrip(skin, 3) + "</#F8FAFC></!shadow>  " + actionMm;
        return List.of(Msg.parse(r0), Msg.parse(r1), Msg.parse(r2), Msg.parse(r3));
    }

    public void openMainHub(Player player) {
        backStacks.remove(player.getUniqueId());
        currentFrames.remove(player.getUniqueId());
        BosketHolder holder = new BosketHolder(MenuType.MAIN_HUB);
        char hubOverlay = Perms.admin(player) ? Glyphs.GUI_OVERLAY_HUB_OP : Glyphs.GUI_OVERLAY_HUB;
        MenuSpec.Builder spec = MenuSpec.builder(6, "LOBBY");
        holder.setTitle(Glyphs.customGuiOverlayTitle(hubOverlay, tr(player, "gui.title.hub", "LOBBY")));

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
        spec.item(13, MenuSpec.Item.visual(buildPlayerSkullCard(player, profile), click -> handleHubClick(player, holder, click)));

        String rankLine = season != null && season.enabled() && season.isProvisional(id)
            ? "<" + Palette.GOLD + ">Placements " + Math.max(0, season.placementsRequired() - season.placementsRemaining(id))
                + "/" + season.placementsRequired() + "</" + Palette.GOLD + ">"
            : "<#F8FAFC>" + profile.elo() + " ELO</#F8FAFC>";

        spec.item(20, MenuSpec.Item.visual(customIcon(
            "ref_play",
            "<" + Palette.WARN + "><bold>ᴘʟᴀʏ</bold></" + Palette.WARN + ">",
            List.of(
                "<#94A3B8>Ranked 1v1, 2v2, 3v3 & 5v5,</#94A3B8>",
                "<#94A3B8>solo drills and AI sparring.</#94A3B8>",
                "",
                "<#94A3B8>In queue:</#94A3B8> <" + Palette.SUCCESS + ">" + queue + "</" + Palette.SUCCESS + "> <#64748B>·</#64748B> <#94A3B8>On court:</#94A3B8> <" + Palette.GOLD + ">" + live + "</" + Palette.GOLD + ">",
                "<#94A3B8>Rank:</#94A3B8> " + rankLine,
                "",
                "<#FDE047>▸ Choose a mode</#FDE047>"
            )), click -> handleHubClick(player, holder, click)));

        spec.item(22, MenuSpec.Item.visual(customIcon(
            "ref_shop",
            "<" + Palette.GOLD + "><bold>ꜱʜᴏᴘ</bold></" + Palette.GOLD + ">",
            List.of(
                "<#94A3B8>Balls, dunks, tag emblems and bundles.</#94A3B8>",
                "",
                "<#94A3B8>Still locked:</#94A3B8> <#38BDF8>" + countUnownedShopItems(profile) + " items</#38BDF8>",
                "",
                "<#FDE047>▸ Left-Click: browse the shop</#FDE047>",
                "<#38BDF8>▸ Right-Click: your collection</#38BDF8>"
            )), click -> handleHubClick(player, holder, click)));

        ChatTag activeTag = profile.selectedTag();
        String equippedTagStr = activeTag == null
            ? "<#94A3B8>None</#94A3B8>"
            : "<!shadow><#F8FAFC>" + activeTag.glyph() + "</#F8FAFC></!shadow> <#F8FAFC>" + activeTag.displayName() + "</#F8FAFC>";
        spec.item(24, MenuSpec.Item.visual(customIcon(
            "ref_clothing",
            "<" + Palette.ICE + "><bold>ʟᴏᴄᴋᴇʀ</bold></" + Palette.ICE + ">",
            List.of(
                "<#94A3B8>What you wear on and off the court.</#94A3B8>",
                "",
                "<#94A3B8>Ball:</#94A3B8> " + profile.selectedBallSkin().displayNameMm(),
                "<#94A3B8>Tag:</#94A3B8> " + equippedTagStr,
                "",
                "<#38BDF8>▸ Left-Click: cosmetics & locker</#38BDF8>",
                "<#C084FC>▸ Right-Click: chat tags</#C084FC>"
            )), click -> handleHubClick(player, holder, click)));

        int groupSize = plugin.socialManager().partySizeOrOne(id);
        int onlineFriends = plugin.socialManager().countOnlineFriends(id);
        var myGuild = plugin.guildService().guildOf(id);
        spec.item(29, MenuSpec.Item.visual(customIcon(
            "ref_member",
            "<" + Palette.SUCCESS + "><bold>ꜱᴏᴄɪᴀʟ</bold></" + Palette.SUCCESS + ">",
            List.of(
                "<#94A3B8>Friends, party and guild - the</#94A3B8>",
                "<#94A3B8>people you actually play with.</#94A3B8>",
                "",
                "<#94A3B8>Friends online:</#94A3B8> <" + Palette.SUCCESS + ">" + onlineFriends + "</" + Palette.SUCCESS + ">",
                "<#94A3B8>Party:</#94A3B8> <#F8FAFC>" + groupSize + "/4</#F8FAFC>",
                "<#94A3B8>Guild:</#94A3B8> " + (myGuild != null ? myGuild.displayTag() + " <#94A3B8>(" + myGuild.members().size() + ")</#94A3B8>" : "<#F8FAFC>None</#F8FAFC>"),
                "",
                "<#FDE047>▸ Left-Click: Friends</#FDE047>",
                "<#38BDF8>▸ Right-Click: Party</#38BDF8>",
                "<#FBBF24>▸ Shift-Click: Guild</#FBBF24>"
            )), click -> handleHubClick(player, holder, click)));

        spec.item(31, MenuSpec.Item.visual(customIcon(
            "ref_medal_gold",
            "<" + Palette.VIOLET + "><bold>ʀᴀɴᴋꜱ ɢᴜɪᴅᴇ</bold></" + Palette.VIOLET + ">",
            List.of(
                "<#94A3B8>Rookie to MVP: every tier, badge</#94A3B8>",
                "<#94A3B8>and the full control list.</#94A3B8>",
                "",
                "<" + Palette.VIOLET + ">▸ Open the guide</" + Palette.VIOLET + ">"
            )), click -> handleHubClick(player, holder, click)));

        spec.item(33, MenuSpec.Item.visual(customIcon(
            "ref_cup_gold",
            "<" + Palette.GOLD_SOFT + "><bold>ʟᴇᴀᴅᴇʀʙᴏᴀʀᴅꜱ</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<#94A3B8>Top 10 across every stat.</#94A3B8>",
                "",
                "<#FDE047>▸ Open rankings</#FDE047>"
            )), click -> handleHubClick(player, holder, click)));

        spec.item(37, MenuSpec.Item.visual(customIcon(
            "ref_dice",
            "<" + Palette.ICE + "><bold>ᴘʀᴀᴄᴛɪᴄᴇ</bold></" + Palette.ICE + ">",
            List.of("<#94A3B8>Empty gym, 24s shot clock, AI defender.</#94A3B8>", "", "<#38BDF8>▸ Step on court</#38BDF8>")), click -> handleHubClick(player, holder, click)));

        int claimable = profile.unclaimedQuestCount();
        spec.item(39, MenuSpec.Item.visual(customIcon(
            "ref_trophy",
            "<" + Palette.GOLD_SOFT + "><bold>ᴘʀᴏɢʀᴇꜱꜱ</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<#94A3B8>Quests:</#94A3B8> " + (claimable > 0 ? "<" + Palette.SUCCESS + "><bold>" + claimable + " ready</bold></" + Palette.SUCCESS + ">" : "<#F8FAFC>none ready</#F8FAFC>"),
                "<#94A3B8>Unread mail:</#94A3B8> <#F8FAFC>" + plugin.socialService().unreadCount(profile) + "</#F8FAFC>",
                "",
                "<#FDE047>▸ Left-Click: Quest board</#FDE047>",
                "<#38BDF8>▸ Right-Click: Achievements</#38BDF8>",
                "<#FBBF24>▸ Shift-Click: Mailbox</#FBBF24>")), click -> handleHubClick(player, holder, click)));

        spec.item(41, MenuSpec.Item.visual(customIcon(
            "ref_clock",
            "<" + Palette.ICE + "><bold>ᴍᴀᴛᴄʜ ʜɪꜱᴛᴏʀʏ</bold></" + Palette.ICE + ">",
            List.of(
                "<#94A3B8>Your last " + profile.matchHistory().size() + " box scores.</#94A3B8>",
                "",
                "<#38BDF8>▸ Review games</#38BDF8>")), click -> handleHubClick(player, holder, click)));

        spec.item(43, MenuSpec.Item.visual(customIcon(
            "ref_gear",
            "<" + Palette.MUTED + "><bold>ꜱᴇᴛᴛɪɴɢꜱ</bold></" + Palette.MUTED + ">",
            List.of("<#94A3B8>Visuals, sounds, cameras, language.</#94A3B8>", "", "<#F8FAFC>▸ Configure</#F8FAFC>")), click -> handleHubClick(player, holder, click)));

        if (busy) {
            spec.item(44, MenuSpec.Item.visual(customIcon(
                "ref_exit",
                "<" + Palette.DANGER + "><bold>ʟᴇᴀᴠᴇ</bold></" + Palette.DANGER + ">",
                List.of("<#94A3B8>Step out of the match or queue.</#94A3B8>")), click -> handleHubClick(player, holder, click)));
        }

        // Corner door: staff get the studio, everyone else the community invite.
        if (Perms.admin(player)) {
            spec.item(40, MenuSpec.Item.visual(customIcon(
                "court_wand",
                "<" + Palette.ICE + "><bold>ꜱᴛᴜᴅɪᴏ</bold></" + Palette.ICE + ">",
                List.of(
                    "<#94A3B8>Maintenance, arenas, pack push, saves.</#94A3B8>",
                    "",
                    "<" + Palette.SUCCESS + ">▸ Left-Click: Server control</" + Palette.SUCCESS + ">",
                    "<#38BDF8>▸ Right-Click: Arena studio</#38BDF8>")), click -> handleHubClick(player, holder, click)));
        } else {
            spec.item(40, MenuSpec.Item.visual(customIcon(
                "icon_discord",
                "<" + Palette.ICE + "><bold>ᴅɪꜱᴄᴏʀᴅ</bold></" + Palette.ICE + ">",
                List.of("<#94A3B8>The community hub.</#94A3B8>", "", "<" + Palette.ICE + ">▸ Get the invite</" + Palette.ICE + ">")), click -> handleHubClick(player, holder, click)));
        }

        
        show(player, holder, spec);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
    }

    public void openPlayModes(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.PLAY_MODES);
        MenuSpec.Builder spec = MenuSpec.builder(6, "PLAY MODES");
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_PLAY, tr(player, "gui.title.play", "PLAY MODES")));

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
            spec.item(slots[i], MenuSpec.Item.visual(customIcon(
                modeIcons[i],
                "<" + Palette.WARN + "><bold>" + mode.smallCapsTitle() + "</bold></" + Palette.WARN + ">",
                List.of(
                    "<#94A3B8>Format:</#94A3B8> <#F8FAFC>" + mode.playersPerTeam() + "v" + mode.playersPerTeam() + "</#F8FAFC>",
                    "<#94A3B8>In Queue:</#94A3B8> <" + Palette.SUCCESS + ">" + q + "/" + mode.maxPlayers() + "</" + Palette.SUCCESS + ">",
                    "<#94A3B8>Stakes:</#94A3B8> <" + Palette.SUCCESS + ">+" + winReward + " Coins</" + Palette.SUCCESS + "> <#94A3B8>/</#94A3B8> <" + Palette.DANGER + ">-" + lossPenalty + " Coins</" + Palette.DANGER + ">",
                    "",
                    myQueue ? "<" + Palette.SUCCESS + ">✔ Currently Queued (Click to leave)</" + Palette.SUCCESS + ">" : "<#FDE047>▸ Click to join queue</#FDE047>"
                )
            ), click -> handlePlayClick(player, holder, click)));
        }

        spec.item(28, MenuSpec.Item.visual(customIcon(
            "ref_dice",
            "<" + Palette.SUCCESS + "><bold>Solo Practice Court</bold></" + Palette.SUCCESS + ">",
            List.of("<#94A3B8>Warm up with the 24s clock and a defender</#94A3B8>", "", "<#4ADE80>▸ Enter Court</#4ADE80>")
        ), click -> handlePlayClick(player, holder, click)));

        spec.item(30, MenuSpec.Item.visual(customIcon(
            "ref_calendar_check",
            "<" + Palette.GOLD_SOFT + "><bold>3-Point Contest</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<#94A3B8>5 Racks · 25 Balls · 60 Seconds</#94A3B8>",
                "<#94A3B8>Earn up to</#94A3B8> <" + Palette.GOLD_SOFT + ">+" + plugin.settings().economy().threePointContestMaxCoins() + " Coins</" + Palette.GOLD_SOFT + ">",
                "",
                "<#FDE047>▸ Start Contest</#FDE047>"
            )
        ), click -> handlePlayClick(player, holder, click)));

        spec.item(32, MenuSpec.Item.visual(customIcon(
            "icon_spectate",
            "<" + Palette.ICE + "><bold>Spectate Live Match</bold></" + Palette.ICE + ">",
            List.of("<#94A3B8>Watch an ongoing match live in 3D</#94A3B8>", "", "<#38BDF8>▸ Spectate</#38BDF8>")
        ), click -> handlePlayClick(player, holder, click)));

        spec.item(34, MenuSpec.Item.visual(customIcon(
            "icon_portal",
            "<" + Palette.VIOLET + "><bold>3D Squad Locker Room</bold></" + Palette.VIOLET + ">",
            List.of("<#94A3B8>Preview your full-body squad & loadout</#94A3B8>", "", "<#C084FC>▸ Enter Stage</#C084FC>")
        ), click -> handlePlayClick(player, holder, click)));

        

        spec.item(40, MenuSpec.Item.visual(customIcon(
            "icon_bot_on",
            "<" + Palette.VIOLET + "><bold>Training & AI Squad</bold></" + Palette.VIOLET + ">",
            List.of(
                "<#94A3B8>Choose the skill of the AI players,</#94A3B8>",
                "<#94A3B8>fill the court and manage your bots.</#94A3B8>",
                "<#94A3B8>Current difficulty:</#94A3B8> " + currentBotDifficultyLabel(player),
                "",
                "<#FDE047>▸ Click to open the AI panel</#FDE047>"
            )
        ), click -> handlePlayClick(player, holder, click)));
        holder.slotStringMap().put(40, "OPEN_BOT_PANEL");
        show(player, holder, spec);
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
        Msg.send(player, Msg.info("Purchase canceled — nothing was spent."));
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
        MenuSpec.Builder spec = MenuSpec.builder(6, "TRAINING & AI");
        holder.setTitle(Glyphs.customGuiOverlayTitle(
            Glyphs.GUI_OVERLAY_PLAY_BOT, tr(player, "gui.title.bots", "TRAINING & AI")));

        fr.bosketball.game.MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        var difficulty = session != null
            ? session.botDifficulty()
            : fr.bosketball.game.bot.BotDifficulty.byNameOrNull(
                plugin.settings().matchmaking().botDifficulty());
        if (difficulty == null) {
            difficulty = fr.bosketball.game.bot.BotDifficulty.NORMAL;
        }

        spec.item(4, MenuSpec.Item.infoVisual(customIcon("icon_bot_on",
            "<" + Palette.VIOLET + "><bold>ᴛʀᴀɪɴɪɴɢ & ᴀɪ</bold></" + Palette.VIOLET + ">",
            List.of(
                "<#94A3B8>Difficulty:</#94A3B8> " + currentBotDifficultyLabel(player),
                "<#94A3B8>AI on court:</#94A3B8> <#F8FAFC>" + (session != null ? session.botSquad().size() : 0) + "</#F8FAFC>",
                "",
                session == null
                    ? "<#F87171>You are not on a court yet — start Practice or a match.</#F87171>"
                    : "<#94A3B8>Changes apply instantly, mid-match.</#94A3B8>"))));

        var tiers = fr.bosketball.game.bot.BotDifficulty.values();
        int[] slots = {19, 22, 25};
        String[] icons = {"icon_easy", "icon_normal", "icon_pro"};
        for (int i = 0; i < tiers.length && i < slots.length; i++) {
            var tier = tiers[i];
            boolean active = tier == difficulty;
            holder.slotStringMap().put(slots[i], "BOT_DIFF:" + tier.name());
            spec.item(slots[i], MenuSpec.Item.visual(customIcon(icons[i],
                "<" + hex(tier.colorRgb()) + "><bold>" + (active ? "● " : "") + tier.displayName()
                    + "</bold></" + hex(tier.colorRgb()) + ">",
                List.of(
                    "<#94A3B8>Style:</#94A3B8> <#F8FAFC>" + tier.flavor() + "</#F8FAFC>",
                    "<#94A3B8>Shot accuracy:</#94A3B8> <#F8FAFC>" + Math.round(tier.shotAccuracy() * 100) + "%</#F8FAFC>",
                    "<#94A3B8>Reaction:</#94A3B8> <#F8FAFC>" + tier.reactionTicks() + " ticks</#F8FAFC>",
                    "",
                    active ? "<" + Palette.SUCCESS + ">✔ Currently active</" + Palette.SUCCESS + ">"
                        : "<#FDE047>▸ Click to set this difficulty</#FDE047>")), click -> handleBotClick(player, holder, click)));
        }

        holder.slotStringMap().put(30, "BOT_FILL");
        spec.item(30, MenuSpec.Item.visual(customIcon("icon_bot_on",
            "<" + Palette.ICE + "><bold>ꜰɪʟʟ ᴛʜᴇ ᴄᴏᴜʀᴛ</bold></" + Palette.ICE + ">",
            List.of("<#94A3B8>Fill every empty slot with AI.</#94A3B8>",
                "", "<#38BDF8>▸ Click to fill</#38BDF8>")), click -> handleBotClick(player, holder, click)));
        holder.slotStringMap().put(32, "BOT_DEFENDER");
        spec.item(32, MenuSpec.Item.visual(customIcon("icon_defense",
            "<" + Palette.SUCCESS + "><bold>ꜱᴘᴀʀʀɪɴɢ ᴅᴇꜰᴇɴᴅᴇʀ</bold></" + Palette.SUCCESS + ">",
            List.of("<#94A3B8>Toggle the practice sparring defender.</#94A3B8>",
                "", "<#4ADE80>▸ Click to toggle</#4ADE80>")), click -> handleBotClick(player, holder, click)));
        holder.slotStringMap().put(34, "BOT_CLEAR");
        spec.item(34, MenuSpec.Item.visual(customIcon("icon_bot_off",
            "<" + Palette.DANGER + "><bold>ʀᴇᴍᴏᴠᴇ ᴀɪ</bold></" + Palette.DANGER + ">",
            List.of("<#94A3B8>Send every AI player off the court.</#94A3B8>",
                "", "<#F87171>▸ Click to clear</#F87171>")), click -> handleBotClick(player, holder, click)));
        holder.slotStringMap().put(40, "BACK");
        
        show(player, holder, spec);
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
        MenuSpec.Builder spec = MenuSpec.builder(6, "gui.title.shop.");
        holder.setTitle(Glyphs.customGuiOverlayTitle(shopOverlay,
            tr(player, "gui.title.shop." + cat.name().toLowerCase(java.util.Locale.ROOT), cat.label())));

        var econ = plugin.settings().economy();
        spec.item(0, MenuSpec.Item.visual(customIcon(
            "ref_coins",
            "<" + Palette.GOLD_SOFT + "><bold>Your Wallet</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                coinStr(profile.coins()),
                "",
                "<#94A3B8>How to earn Coins:</#94A3B8>",
                "<#64748B>· +" + econ.winCoins() + " Win · +" + econ.mvpBonusCoins() + " MVP · +" + econ.twoPointCoins() + "/2PT · +" + econ.threePointCoins() + "/3PT</#64748B>",
                "<#64748B>· +" + econ.assistCoins() + "/AST · +" + econ.ggRewardCoins() + " GG · 3PT Contest (up to +" + econ.threePointContestMaxCoins() + ")</#64748B>",
                "",
                "<#94A3B8>Sort:</#94A3B8> <#F8FAFC>" + shopSortOf(player.getUniqueId()).label() + "</#F8FAFC>"
                    + " <#64748B>·</#64748B> <#FDE047>▸ Click to cycle</#FDE047>",
                "<#94A3B8>Search:</#94A3B8> <#F8FAFC>"
                    + (shopSearchOf(player.getUniqueId()).isBlank() ? "none" : shopSearchOf(player.getUniqueId()))
                    + "</#F8FAFC> <#64748B>·</#64748B> <#FDE047>/bb shop search <text></#FDE047>"
            )
        ), click -> handleShopClick(player, holder, click)));

        spec.item(2, MenuSpec.Item.visual(buildShopTabButton(
            cat == ShopCategory.ALL,
            "ref_shop",
            "Shop Shelves",
            totalUnowned,
            "Full catalog overview — owned items stay visible and can be equipped here; count = remaining to unlock"
        ), click -> handleShopClick(player, holder, click)));

        ItemStack ballTabIcon = BallSkin.BLACKTOP.createItemStack();
        ItemMeta btm = ballTabIcon.getItemMeta();
        if (btm != null) {
            boolean active = (cat == ShopCategory.BALLS);
            btm.displayName(Msg.parse((active ? "<" + Palette.SUCCESS + "><bold>● " : "<" + Palette.GOLD + "><bold>") + "Basketballs (" + unownedBalls.size() + ")</bold>" + (active ? "</" + Palette.SUCCESS + ">" : "</" + Palette.GOLD + ">")));
            btm.lore(List.of(
                Msg.parse("<#94A3B8>3D Square Basketball Skins</#94A3B8>"),
                Msg.parse("<#94A3B8>Catalog:</#94A3B8> <#F8FAFC>" + catalogBalls.size() + "</#F8FAFC> <#64748B>·</#64748B> <#94A3B8>Count above = remaining to unlock; owned skins stay visible.</#94A3B8>"),
                Msg.parse(""),
                Msg.parse(active ? "<" + Palette.SUCCESS + ">✔ Viewing Basketballs</" + Palette.SUCCESS + ">" : "<#FDE047>▸ Click to filter Basketballs</#FDE047>")
            ));
            if (active) {
                btm.addEnchant(Enchantment.UNBREAKING, 1, true);
                btm.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            ballTabIcon.setItemMeta(btm);
        }
        spec.item(3, MenuSpec.Item.visual(ballTabIcon, click -> handleShopClick(player, holder, click)));

        spec.item(4, MenuSpec.Item.visual(buildShopTabButton(
            cat == ShopCategory.DUNKS,
            "dunk_meteor_jam",
            "Signature Dunks",
            unownedDunks.size(),
            "Full catalog of aerial Slam Dunk animations — count = remaining to unlock; owned dunks stay visible"
        ), click -> handleShopClick(player, holder, click)));
        spec.item(5, MenuSpec.Item.visual(buildShopTabButton(
            cat == ShopCategory.TAGS,
            "ref_bag",
            "Collector Tags",
            unownedTags.size(),
            "Full catalog of Chat, [TAB] & Locker Room badges sorted by Rarity — count = remaining to unlock; owned tags stay visible"
        ), click -> handleShopClick(player, holder, click)));

        spec.item(8, MenuSpec.Item.visual(customIcon(
            "ref_chest",
            "<" + Palette.VIOLET + "><bold>My Locker & Vault</bold></" + Palette.VIOLET + ">",
            List.of(
                "<#94A3B8>Equip your already-owned</#94A3B8>",
                "<#94A3B8>Balls, dunks and chat tags</#94A3B8>",
                "",
                "<#FDE047>▸ Left-Click: Balls & Dunks</#FDE047>",
                "<#38BDF8>▸ Right-Click: Chat Tags</#38BDF8>"
            )
        ), click -> handleShopClick(player, holder, click)));

        if (cat == ShopCategory.ALL) {
            if (catalogBalls.isEmpty() && catalogDunks.isEmpty() && catalogTags.isEmpty()) {
                spec.item(22, MenuItems.empty("No purchasable cosmetics are configured.", "Choose a mode", click -> openPlayModes(player)));
            } else {

                spec.item(9, MenuSpec.Item.visual(customIcon(
                    "icon_star_shield",
                    "<" + Palette.WARN + "><bold> Basketballs ▸</bold></" + Palette.WARN + ">",
                    List.of(
                        "<#94A3B8>Catalog:</#94A3B8> <#F8FAFC>" + catalogBalls.size() + " skins</#F8FAFC> <#64748B>·</#64748B> <#94A3B8>Remaining:</#94A3B8> <#F8FAFC>" + unownedBalls.size() + "</#F8FAFC>",
                        "<#94A3B8>Owned skins stay here and can be equipped.</#94A3B8>",
                        "",
                        "<#FDE047>▸ Click to view Basketballs tab</#FDE047>"
                    )
                ), click -> handleShopClick(player, holder, click)));
                if (catalogBalls.isEmpty()) {
                    spec.item(10, MenuItems.empty("No basketball skins are configured.", "View all shop", click -> openShop(player, ShopCategory.ALL, 0)));
                } else {
                    for (int i = 0; i < SHELF_BALL_SLOTS.length && i < catalogBalls.size(); i++) {
                        int s = SHELF_BALL_SLOTS[i];
                        ShopItemEntry e = catalogBalls.get(i);
                        spec.item(s, MenuSpec.Item.visual(buildShopEntryCard(player, profile, e), click -> handleShopClick(player, holder, click)));
                        holder.shopSlotMap().put(s, e);
                    }
                }

                spec.item(18, MenuSpec.Item.visual(customIcon(
                    "dunk_meteor_jam",
                    "<" + Palette.WARN + "><bold> Dunk Styles ▸</bold></" + Palette.WARN + ">",
                    List.of(
                        "<#94A3B8>Catalog:</#94A3B8> <#F8FAFC>" + catalogDunks.size() + " dunks</#F8FAFC> <#64748B>·</#64748B> <#94A3B8>Remaining:</#94A3B8> <#F8FAFC>" + unownedDunks.size() + "</#F8FAFC>",
                        "<#94A3B8>Owned dunks stay here and can be equipped.</#94A3B8>",
                        "",
                        "<#FDE047>▸ Click to view Signature Dunks tab</#FDE047>"
                    )
                ), click -> handleShopClick(player, holder, click)));
                if (catalogDunks.isEmpty()) {
                    spec.item(19, MenuItems.empty("No dunk styles are configured.", "View all shop", click -> openShop(player, ShopCategory.ALL, 0)));
                } else {
                    for (int i = 0; i < SHELF_DUNK_SLOTS.length && i < catalogDunks.size(); i++) {
                        int s = SHELF_DUNK_SLOTS[i];
                        ShopItemEntry e = catalogDunks.get(i);
                        spec.item(s, MenuSpec.Item.visual(buildShopEntryCard(player, profile, e), click -> handleShopClick(player, holder, click)));
                        holder.shopSlotMap().put(s, e);
                    }
                }

                spec.item(27, MenuSpec.Item.visual(customIcon(
                    "ref_bag",
                    "<" + Palette.ICE + "><bold> Chat Tags ▸</bold></" + Palette.ICE + ">",
                    List.of(
                        "<#94A3B8>Catalog:</#94A3B8> <#F8FAFC>" + catalogTags.size() + " tags</#F8FAFC> <#64748B>·</#64748B> <#94A3B8>Remaining:</#94A3B8> <#F8FAFC>" + unownedTags.size() + "</#F8FAFC>",
                        "<#94A3B8>Owned tags stay here, sorted Common → Legendary.</#94A3B8>",
                        "",
                        "<#38BDF8>▸ Click for full Collector Tags grid</#38BDF8>"
                    )
                ), click -> handleShopClick(player, holder, click)));
                if (catalogTags.isEmpty()) {
                    spec.item(28, MenuItems.empty("No collector tags are configured.", "View all shop", click -> openShop(player, ShopCategory.ALL, 0)));
                } else {
                    int startIdx = page * SHELF_TAG_SLOTS.length;
                    for (int i = 0; i < SHELF_TAG_SLOTS.length && (startIdx + i) < catalogTags.size(); i++) {
                        int s = SHELF_TAG_SLOTS[i];
                        ShopItemEntry e = catalogTags.get(startIdx + i);
                        spec.item(s, MenuSpec.Item.visual(buildShopEntryCard(player, profile, e), click -> handleShopClick(player, holder, click)));
                        holder.shopSlotMap().put(s, e);
                    }
                }
            }
        } else {
            if (catEntries.isEmpty()) {
                spec.item(22, MenuItems.empty("No purchasable items are configured for this category.", "View all shop", click -> openShop(player, ShopCategory.ALL, 0)));
            } else {
                int startIdx = page * SHOP_GRID_SLOTS.length;
                for (int i = 0; i < SHOP_GRID_SLOTS.length && (startIdx + i) < catEntries.size(); i++) {
                    int s = SHOP_GRID_SLOTS[i];
                    ShopItemEntry e = catEntries.get(startIdx + i);
                    spec.item(s, MenuSpec.Item.visual(buildShopEntryCard(player, profile, e), click -> handleShopClick(player, holder, click)));
                    holder.shopSlotMap().put(s, e);
                }
            }
        }

        if (page > 0) {
            spec.item(45, MenuSpec.Item.visual(customIcon(
                "icon_prev",
                "<#FDE047><bold>◂ Previous Page</bold></#FDE047>",
                List.of("<#94A3B8>Page " + page + " of " + totalPages + "</#94A3B8>")
            ), click -> handleShopClick(player, holder, click)));
        }
        spec.item(47, MenuSpec.Item.visual(customIcon(
            "dunk_tomahawk",
            "<" + Palette.VIOLET + "><bold>My Basketballs & Dunks</bold></" + Palette.VIOLET + ">",
            List.of("<#94A3B8>Equip your owned Basketballs & Dunks</#94A3B8>", "", "<#FDE047>▸ Open Locker</#FDE047>")
        ), click -> handleShopClick(player, holder, click)));
        
        spec.item(50, MenuSpec.Item.visual(customIcon(
            "ref_bag",
            "<" + Palette.ICE + "><bold>My Chat Tags</bold></" + Palette.ICE + ">",
            List.of("<#94A3B8>Equip or remove your unlocked Tags</#94A3B8>", "", "<#38BDF8>▸ Open Tag Vault</#38BDF8>")
        ), click -> handleShopClick(player, holder, click)));
        if (page + 1 < totalPages) {
            spec.item(53, MenuSpec.Item.visual(customIcon(
                "icon_next",
                "<#FDE047><bold>Next Page ▸</bold></#FDE047>",
                List.of("<#94A3B8>Page " + (page + 2) + " of " + totalPages + "</#94A3B8>")
            ), click -> handleShopClick(player, holder, click)));
        }
        show(player, holder, spec);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.24f);
    }

    private ItemStack buildShopTabButton(boolean active, String modelId, String title, int count, String desc) {
        ItemStack stack = customIcon(
            modelId,
            (active ? "<" + Palette.SUCCESS + "><bold>● " : "<" + Palette.MUTED + "><bold>") + title + " (" + count + ")</bold>" + (active ? "</" + Palette.SUCCESS + ">" : "</" + Palette.MUTED + ">"),
            List.of(
                "<#94A3B8>" + desc + "</#94A3B8>",
                "",
                active ? "<" + Palette.SUCCESS + ">✔ Active Tab</" + Palette.SUCCESS + ">" : "<#FDE047>▸ Click to view</#FDE047>"
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
                ? "<#FDE047><bold>▸ Click to Equip (Owned)</bold></#FDE047>"
                : canAfford
                    ? "<" + Palette.SUCCESS + "><bold>▸ Click to Buy & Equip (" + entry.priceCoins() + " Coins)</bold></" + Palette.SUCCESS + ">"
                    : "<" + Palette.DANGER + ">▸ Need " + (entry.priceCoins() - profile.coins()) + " more Coins</" + Palette.DANGER + ">";
        String ownershipLine = owned
            ? "<" + Palette.SUCCESS + ">✔ Owned — saved in My Locker</" + Palette.SUCCESS + ">"
            : "<#94A3B8>Price:</#94A3B8> " + coinStr(entry.priceCoins());

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
                    "<#64748B>Signature Aerial Dunk Style</#64748B>",
                    "<#94A3B8>" + dunk.descriptionSmallCaps() + "</#94A3B8>",
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
            "<" + Palette.TEXT + "><bold>" + tag.displayName() + "</bold></" + Palette.TEXT + "> <!shadow><#F8FAFC>" + tag.glyph() + "</#F8FAFC></!shadow>",
            List.of(
                "<#64748B>Collector Tag · </#64748B>" + tag.tierLabel(),
                "<#94A3B8>" + tag.loreHint() + "</#94A3B8>",
                "<#94A3B8>Badge:</#94A3B8> <!shadow><#F8FAFC>" + tag.glyph() + "</#F8FAFC></!shadow>",
                "<#94A3B8>In Chat:</#94A3B8> <!shadow><#F8FAFC>" + profile.effectiveRank().pillGlyph() + "</#F8FAFC></!shadow> <!shadow><#F8FAFC>" + tag.glyph() + "</#F8FAFC></!shadow> <#F8FAFC>" + player.getName() + "</#F8FAFC>",
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

    public void openCosmetics(Player player, int page) {
        openCosmetics(player);
    }

    public void openCosmetics(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.COSMETICS);
        MenuSpec.Builder spec = MenuSpec.builder(6, "COSMETICS");
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_COSMETICS, tr(player, "gui.title.cosmetics", "COSMETICS")));

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

        spec.item(7, MenuSpec.Item.infoVisual(customIcon(
            "ref_chest",
            "<" + Palette.GOLD_SOFT + "><bold>Your Wallet</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                coinStr(profile.coins()),
                "<#94A3B8>Win matches & score buckets to earn Coins!</#94A3B8>"
            )
        )));
        spec.item(8, MenuSpec.Item.visual(customIcon(
            "ref_shop",
            "<" + Palette.SUCCESS + "><bold>Shop</bold></" + Palette.SUCCESS + ">",
            List.of("<#94A3B8>Browse the full Shop catalog — owned items stay visible</#94A3B8>", "", "<#4ADE80>▸ Open Shop</#4ADE80>")
        ), click -> handleCosmeticsClick(player, holder, click)));

        BallSkin[] balls = BallSkin.values();
        for (int i = 0; i < balls.length && i < COSMETICS_BALL_SLOTS.length; i++) {
            BallSkin skin = balls[i];
            boolean unlocked = profile.hasUnlockedBall(skin) || player.hasPermission("bosketball.cosmetics.all");
            boolean selected = profile.selectedBallSkin() == skin;

            ItemStack ballItem = skin.createItemStack();
            ItemMeta meta = ballItem.getItemMeta();
            if (meta != null) {
                meta.displayName(Msg.parse(skin.displayNameMm()));
                String ownershipMm = unlocked ? "<" + Palette.SUCCESS + ">✔ Unlocked in Locker</" + Palette.SUCCESS + ">" : "<#94A3B8>Price:</#94A3B8> " + coinStr(skin.priceCoins());
                String actionLine = selected
                    ? "<" + Palette.SUCCESS + "><bold>✔ Equipped</bold></" + Palette.SUCCESS + ">"
                    : (unlocked
                        ? "<#FDE047>▸ Click to equip</#FDE047>"
                        : "<" + Palette.GOLD_SOFT + ">▸ Buy for " + skin.priceCoins() + " Coins</" + Palette.GOLD_SOFT + ">");
                meta.lore(buildBallHoverLore(skin, ownershipMm, actionLine));
                ballItem.setItemMeta(meta);
            }
            spec.item(COSMETICS_BALL_SLOTS[i], MenuSpec.Item.visual(ballItem, click -> handleCosmeticsClick(player, holder, click)));
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
                    ? "<#FDE047>▸ Click to equip</#FDE047>"
                    : "<" + Palette.GOLD_SOFT + ">▸ Buy for " + dunk.priceCoins() + " Coins</" + Palette.GOLD_SOFT + ">");

            ItemStack dunkItem = customIcon(
                dunk.iconModelId(),
                dunk.displayNameMm(),
                List.of(
                    "<#94A3B8>" + dunk.descriptionSmallCaps() + "</#94A3B8>",
                    unlocked ? "<" + Palette.SUCCESS + ">Unlocked</" + Palette.SUCCESS + ">" : "<#94A3B8>Price:</#94A3B8> " + coinStr(dunk.priceCoins()),
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
            spec.item(dunkSlots[i], MenuSpec.Item.visual(dunkItem, click -> handleCosmeticsClick(player, holder, click)));
        }

        spec.item(47, MenuSpec.Item.visual(customIcon(
            "ref_bag",
            "<" + Palette.ICE + "><bold>Collector Chat Tags</bold></" + Palette.ICE + ">",
            List.of("<#94A3B8>Equip or unlock Collector Chat Tags</#94A3B8>", "", "<#38BDF8>▸ Open Tag Vault</#38BDF8>")
        ), click -> handleCosmeticsClick(player, holder, click)));
        
        spec.item(50, MenuSpec.Item.visual(customIcon(
            "ref_shop",
            "<" + Palette.SUCCESS + "><bold>Shop</bold></" + Palette.SUCCESS + ">",
            List.of("<#94A3B8>Browse the full Shop catalog — owned items stay visible</#94A3B8>", "", "<#4ADE80>▸ Open Shop</#4ADE80>")
        ), click -> handleCosmeticsClick(player, holder, click)));
        show(player, holder, spec);
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
        MenuSpec.Builder spec = MenuSpec.builder(6, "CHAT TAGS");
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_TAGS, tr(player, "gui.title.tags", "CHAT TAGS")));

        spec.item(0, MenuSpec.Item.infoVisual(customIcon(
            "ref_coins",
            "<" + Palette.GOLD_SOFT + "><bold>Your Balance</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                coinStr(profile.coins()),
                "<#94A3B8>Play matches to earn coins</#94A3B8>"
            )
        )));

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
                    active ? "<" + Palette.SUCCESS + ">✔ Active Filter</" + Palette.SUCCESS + ">" : "<#FDE047>▸ Click to filter</#FDE047>"
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
            spec.item(1 + i, MenuSpec.Item.visual(tabBtn, click -> handleTagsClick(player, holder, click)));
        }

        spec.item(8, MenuSpec.Item.visual(customIcon(
            "ref_shop",
            "<" + Palette.GOLD + "><bold>Shop</bold></" + Palette.GOLD + ">",
            List.of("<#94A3B8>Browse organized shelves of Balls, Dunks & Tags</#94A3B8>", "", "<#FDE047>▸ Open Shop</#FDE047>")
        ), click -> handleTagsClick(player, holder, click)));

        int startIdx = page * perPage;
        if (tags.isEmpty()) {
            spec.item(22, MenuItems.empty("No tags match this filter.", "Show all tags", click -> openTagsVault(player, TagVaultFilter.ALL, 0)));
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
                        ? "<#FDE047>▸ Click to equip</#FDE047>"
                        : "<" + Palette.GOLD_SOFT + ">▸ Buy for " + tag.priceCoins() + " Coins</" + Palette.GOLD_SOFT + ">");

                String ownershipLine = tag.isSpecialUnpurchasable()
                    ? "<" + Palette.EMBER + ">▪ Exclusive Honor (Not sold in Shop)</" + Palette.EMBER + ">"
                    : (unlocked ? "<" + Palette.SUCCESS + ">✔ Unlocked</" + Palette.SUCCESS + ">" : "<#94A3B8>Price:</#94A3B8> " + coinStr(tag.priceCoins()));

                ItemStack tagCard = customIcon(
                    tag.cardModelId(),
                    "<" + Palette.TEXT + "><bold>" + tag.displayName() + "</bold></" + Palette.TEXT + "> <!shadow><#F8FAFC>" + tag.glyph() + "</#F8FAFC></!shadow>",
                    List.of(
                        "<#94A3B8>Rarity:</#94A3B8> <#F8FAFC>" + tag.tierLabel() + "</#F8FAFC>",
                        "<#94A3B8>" + tag.loreHint() + "</#94A3B8>",
                        "<#94A3B8>Badge:</#94A3B8> <!shadow><#F8FAFC>" + tag.glyph() + "</#F8FAFC></!shadow>",
                        "<#94A3B8>In Chat:</#94A3B8> <!shadow><#F8FAFC>" + profile.effectiveRank().pillGlyph() + "</#F8FAFC></!shadow> <!shadow><#F8FAFC>" + tag.glyph() + "</#F8FAFC></!shadow> <#F8FAFC>" + player.getName() + "</#F8FAFC>",
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
                spec.item(slot, MenuSpec.Item.visual(tagCard, click -> handleTagsClick(player, holder, click)));
                holder.tagSlotMap().put(slot, tag);
            }
        }

        if (page > 0) {
            spec.item(45, MenuSpec.Item.visual(customIcon(
                "icon_prev",
                "<#FDE047><bold>◂ Previous Page</bold></#FDE047>",
                List.of("<#94A3B8>Page " + page + " of " + totalPages + "</#94A3B8>")
            ), click -> handleTagsClick(player, holder, click)));
        }
        spec.item(47, MenuSpec.Item.visual(customIcon(
            "ref_exit",
            "<" + Palette.DANGER + "><bold>Unequip Tag</bold></" + Palette.DANGER + ">",
            List.of("<#94A3B8>Clear your active chat tag</#94A3B8>")
        ), click -> handleTagsClick(player, holder, click)));
        
        spec.item(50, MenuSpec.Item.visual(customIcon(
            "dunk_tomahawk",
            "<" + Palette.VIOLET + "><bold>Basketballs & Dunks</bold></" + Palette.VIOLET + ">",
            List.of("<#94A3B8>Switch to Basketball & Dunk Locker</#94A3B8>")
        ), click -> handleTagsClick(player, holder, click)));
        if (page + 1 < totalPages) {
            spec.item(53, MenuSpec.Item.visual(customIcon(
                "icon_next",
                "<#FDE047><bold>Next Page ▸</bold></#FDE047>",
                List.of("<#94A3B8>Page " + (page + 2) + " of " + totalPages + "</#94A3B8>")
            ), click -> handleTagsClick(player, holder, click)));
        }
        show(player, holder, spec);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.28f);
    }

    public void openLeaderboards(Player player, LeaderboardCategory category) {
        BosketHolder holder = new BosketHolder(MenuType.LEADERBOARDS, category);
        MenuSpec.Builder spec = MenuSpec.builder(6, "LEADERBOARDS");
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_LEADERBOARDS, tr(player, "gui.title.leaderboards", "LEADERBOARDS")));

        LeaderboardCategory[] cats = LeaderboardCategory.values();
        String[] catIcons = {"ref_cup_gold", "ref_coins", "ref_trophy", "ref_calendar_check", "icon_dunk", "icon_star_shield", "icon_medal_gold", "ref_trophy"};
        int[] catSlots = {0, 1, 2, 3, 5, 6, 7, 8};
        for (int i = 0; i < cats.length && i < catSlots.length; i++) {
            LeaderboardCategory c = cats[i];
            boolean active = c == category;
            spec.item(catSlots[i], MenuSpec.Item.visual(customIcon(
                catIcons[i],
                (active ? "<" + Palette.GOLD_SOFT + "><bold>▪ " : "<#94A3B8>") + c.label() + (active ? " ▪</bold></" + Palette.GOLD_SOFT + ">" : "</#94A3B8>"),
                List.of(active ? "<" + Palette.SUCCESS + ">✔ Viewing</" + Palette.SUCCESS + ">" : "<#FDE047>▸ Click to view</#FDE047>")
            ), click -> handleLeaderboardsClick(player, holder, click)));
        }

        spec.item(4, MenuSpec.Item.visual(customIcon(
            "ref_medal_gold",
            "<" + Palette.GOLD_SOFT + "><bold>Top #1 / #2 / #3 Server ELO Badges</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<#94A3B8>The Top 3 ELO players on the server earn</#94A3B8>",
                "<#94A3B8>live badges in Chat, /gg, [TAB] & Scoreboard:</#94A3B8>",
                "  <!shadow><#F8FAFC>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 1) + "</#F8FAFC></!shadow> <" + Palette.GOLD + "><bold>#1 Gold ELO Champion</bold></" + Palette.GOLD + ">",
                "  <!shadow><#F8FAFC>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 2) + "</#F8FAFC></!shadow> <" + Palette.TEXT + "><bold>#2 Silver ELO Runner-Up</bold></" + Palette.TEXT + ">",
                "  <!shadow><#F8FAFC>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 3) + "</#F8FAFC></!shadow> <" + Palette.AMBER + "><bold>#3 Bronze ELO Contender</bold></" + Palette.AMBER + ">",
                "",
                "<#FDE047>▸ Click to open full Ranks & Badges Guide</#FDE047>"
            )
        ), click -> handleLeaderboardsClick(player, holder, click)));

        List<PlayerProfile> top = plugin.statsManager().topBy(category, 10);
        int[] podiumSlots = {13, 21, 23, 29, 30, 31, 32, 33, 39, 41};
        for (int i = 0; i < top.size() && i < podiumSlots.length; i++) {
            spec.item(podiumSlots[i], MenuSpec.Item.visual(buildLeaderboardSkull(i + 1, top.get(i), category), click -> handleLeaderboardsClick(player, holder, click)));
        }
        if (top.isEmpty()) {
            spec.item(22, MenuItems.empty("No results on this board yet.", "Join ranked", click -> openPlayModes(player)));
        }
        show(player, holder, spec);

        for (int i = 0; i < top.size() && i < podiumSlots.length; i++) {
            final PlayerProfile entry = top.get(i);
            final int slot = podiumSlots[i];
            final int place = i + 1;
            if (needsSkinRefresh(entry.uuid())) {
                requestRealHeadAsync(entry.uuid(), entry.lastKnownName(), () -> {
                    if (!player.isOnline() || holder.getInventory() == null || player.getOpenInventory().getTopInventory() != holder.getInventory()) {
                        return;
                    }
                    refreshSlot(player, holder, slot, MenuSpec.Item.visual(buildLeaderboardSkull(place, entry, category), click -> handleLeaderboardsClick(player, holder, click)));
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
        MenuSpec.Builder spec = MenuSpec.builder(6, "NEW PLAYER GUIDE");
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_HUB, tr(player, "gui.title.tutorial", "NEW PLAYER GUIDE")));

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
                        : "<#94A3B8>○ not reached yet</#94A3B8>");
            List<String> lore = new ArrayList<>();
            lore.add("<#94A3B8>" + step.subtitle() + "</#94A3B8>");
            lore.add("");
            lore.add("<#64748B>Objective</#64748B> <#64748B>»</#64748B> <#F8FAFC>" + step.objective() + "</#F8FAFC>");
            lore.add("<#64748B>Reward</#64748B> <#64748B>»</#64748B> "
                + (step.revealCoins() > 0 ? Msg.coinsBadge(step.revealCoins()) : "<#94A3B8>completion bonus</#94A3B8>"));
            lore.add("");
            lore.add(state);
            lore.add(active ? "<#64748B>Click to re-read the chapter</#64748B>" : "<#94A3B8>▸ Click to jump here</#94A3B8>");
            spec.item(TUTORIAL_CHAPTER_SLOTS[i], MenuSpec.Item.visual(customIcon(
                step.iconModel(),
                colour + marker + " <bold>" + (i + 1) + "/" + steps.size() + " " + step.title() + "</bold>",
                lore), click -> handleTutorialClick(player, holder, click)));
        }

        spec.item(4, MenuSpec.Item.infoVisual(customIcon("icon_book",
            "<" + Palette.GOLD_SOFT + "><bold>ɴᴇᴡ ᴘʟᴀʏᴇʀ ɢᴜɪᴅᴇ</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<#94A3B8>Progress:</#94A3B8> <#F8FAFC>" + reached + "/" + steps.size() + "</#F8FAFC>",
                progressBar(reached, steps.size()),
                finished ? "<" + Palette.SUCCESS + ">You finished the tour — free to replay it.</" + Palette.SUCCESS + ">"
                         : "<#94A3B8>Finish it once to claim the completion bonus.</#94A3B8>",
                "<#94A3B8>Chapter rewards:</#94A3B8> " + Msg.coinsBadge(plugin.settings().tutorial().completionCoins()
                    + (tuto != null ? tuto.totalStepRewards() : 0)) + " <#64748B>+ "
                    + plugin.settings().tutorial().completionXp() + " XP</#64748B>",
                "",
                runnerLine(running, current, steps.size())
            ))));

        spec.item(44, MenuSpec.Item.visual(customIcon("ref_play",
            finished ? "<" + Palette.ICE + "><bold>ʀᴇᴘʟᴀʏ ᴛʜᴇ ᴛᴏᴜʀ</bold></" + Palette.ICE + ">"
                     : "<" + Palette.SUCCESS + "><bold>" + (reached > 0 ? "ʀᴇꜱᴜᴍᴇ ᴛʜᴇ ᴛᴏᴜʀ" : "ꜱᴛᴀʀᴛ ᴛʜᴇ ᴛᴏᴜʀ") + "</bold></" + Palette.SUCCESS + ">",
            List.of("<#94A3B8>Cinematic intro, then chapter 1.</#94A3B8>",
                "<#64748B>Chapter rewards are paid once.</#64748B>", "",
                finished ? "<#38BDF8>▸ Click to replay</#38BDF8>" : "<#4ADE80>▸ Click to start</#4ADE80>")), click -> handleTutorialClick(player, holder, click)));
        spec.item(47, MenuSpec.Item.visual(customIcon("ref_arrow_right",
            "<" + Palette.ICE + "><bold>ɴᴇxᴛ ᴄʜᴀᴘᴛᴇʀ</bold></" + Palette.ICE + ">",
            List.of("<#94A3B8>Skip to the next chapter.</#94A3B8>",
                "<#64748B>Skips never pay the chapter reward.</#64748B>", "",
                "<#38BDF8>▸ Click to continue</#38BDF8>")), click -> handleTutorialClick(player, holder, click)));
        spec.item(51, MenuSpec.Item.visual(customIcon("ref_cross",
            "<" + Palette.DANGER + "><bold>ᴘᴀᴜꜱᴇ ᴛᴜᴛᴏʀɪᴀʟ</bold></" + Palette.DANGER + ">",
            List.of("<#94A3B8>Stop here — your progress is saved.</#94A3B8>",
                "<#94A3B8>Markers, boss bar and effects are cleared.</#94A3B8>", "",
                "<#F87171>▸ Click to pause</#F87171>")), click -> handleTutorialClick(player, holder, click)));
        
        show(player, holder, spec);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.25f);
    }

    /** One line that tells the player exactly where the tour stands. */
    private static String runnerLine(boolean running, int current, int total) {
        if (!running) {
            return "<#94A3B8>Status:</#94A3B8> <#F8FAFC>not running</#F8FAFC>";
        }
        return "<#94A3B8>Status:</#94A3B8> <" + Palette.GOLD_SOFT + "><bold>chapter " + (current + 1) + "/" + total + "</bold></" + Palette.GOLD_SOFT + ">";
    }

    private static String progressBar(int done, int total) {
        int filled = total <= 0 ? 0 : Math.round((float) done / total * 20f);
        StringBuilder bar = new StringBuilder("<#64748B>[</#64748B>");
        for (int i = 0; i < 20; i++) {
            bar.append(i < filled ? "<" + Palette.SUCCESS + ">█</" + Palette.SUCCESS + ">" : "<#64748B>░</#64748B>");
        }
        bar.append("<#64748B>]</#64748B>");
        return bar.toString();
    }

    public void openMatchHistory(Player player) {
        openMatchHistory(player, 0);
    }

    public void openMatchHistory(Player player, int requestedPage) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        MenuSpec.Page<MatchHistoryEntry> page = MenuSpec.Page.of(profile.matchHistory(), requestedPage, MenuSpec.GRID.length);
        BosketHolder holder = new BosketHolder(MenuType.MATCH_HISTORY, page.index());
        MenuSpec.Builder spec = MenuSpec.builder(6, "Match history");
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_HISTORY, tr(player, "gui.title.history", "MATCH HISTORY")));
        for (int i = 0; i < page.entries().size(); i++) {
            MatchHistoryEntry h = page.entries().get(i);
            String result = h.won() ? "Victory" : "Defeat";
            spec.item(MenuSpec.GRID[i], MenuItems.info(h.won() ? "ref_cup_gold" : "ref_clock", result + " " + MenuItems.SEPARATOR + " " + h.modeKey(), List.of(
                MenuItems.row("Date", h.formattedDate()),
                MenuItems.row("Score", "Ignis " + h.ignisScore() + " " + MenuItems.SEPARATOR + " " + h.frostScore() + " Frost"),
                MenuItems.row("Box score", h.points() + " PTS " + MenuItems.SEPARATOR + " " + h.assists() + " AST " + MenuItems.SEPARATOR + " " + h.rebounds() + " REB"),
                MenuItems.row("ELO", (h.eloDelta() >= 0 ? "+" : "") + h.eloDelta()))));
        }
        if (page.empty()) {
            spec.item(22, MenuItems.empty("No matches played yet.", "Play a match", click -> openPlayModes(player)));
        }
        spec.item(47, MenuItems.action("ref_play", "Play next match", MenuItems.lore("Choose a mode and queue up."), click -> openPlayModes(player)));
        spec.pagination(page, click -> openMatchHistory(player, page.previous()), click -> openMatchHistory(player, page.next()));
        show(player, holder, spec);
    }

    public void openFriendsMenu(Player player) {
        openFriendsMenu(player, 0);
    }

    public void openFriendsMenu(Player player, int requestedPage) {
        BosketHolder holder = new BosketHolder(MenuType.FRIENDS_MENU);
        MenuSpec.Builder spec = MenuSpec.builder(6, "FRIENDS");
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_FRIENDS, tr(player, "gui.title.friends", "FRIENDS")));

        PlayerProfile myProf = plugin.statsManager().getOrCreate(player);
        int groupSize = plugin.socialManager().partySizeOrOne(player.getUniqueId());

        spec.item(3, MenuSpec.Item.infoVisual(customIcon(
            "icon_star_shield",
            "<" + Palette.SUCCESS + "><bold>● Friends (" + myProf.friends().size() + ")</bold></" + Palette.SUCCESS + ">",
            List.of("<" + Palette.SUCCESS + ">✔ Viewing Friends List</" + Palette.SUCCESS + ">")
        )));
        spec.item(5, MenuSpec.Item.visual(customIcon(
            "ref_friends",
            "<" + Palette.GOLD + "><bold>Party & Team Name (" + groupSize + "/4)</bold></" + Palette.GOLD + ">",
            List.of(
                "<#94A3B8>Create a Party, invite up to 4 friends,</#94A3B8>",
                "<#94A3B8>and set your custom</#94A3B8> <" + Palette.GOLD + ">Team Name</" + Palette.GOLD + "><#94A3B8>!</#94A3B8>",
                "",
                "<#FDE047>▸ Click to switch to Party Menu</#FDE047>"
            )
        ), click -> handleFriendsClick(player, holder, click)));
        spec.item(7, MenuSpec.Item.infoVisual(customIcon(
            "ref_medal_gold",
            "<" + Palette.ICE + "><bold>How Friends Work</bold></" + Palette.ICE + ">",
            List.of(
                "<#94A3B8>· Add friends via</#94A3B8> <#F8FAFC>+ Add Online Player</#F8FAFC> <#94A3B8>or</#94A3B8> <#F8FAFC>/f <name></#F8FAFC>",
                "<#94A3B8>· Left-Click an online friend to</#94A3B8> <" + Palette.SUCCESS + ">Invite to Party</" + Palette.SUCCESS + ">",
                "<#94A3B8>· Shift + Left-Click to</#94A3B8> <" + Palette.ICE + ">Challenge to a 1v1 Duel</" + Palette.ICE + ">",
                "<#94A3B8>· Right-Click to</#94A3B8> <" + Palette.DANGER + ">Remove Friend</" + Palette.DANGER + ">"
            )
        )));

        int[] gridSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };
        List<MenuSpec.Item> rows = new ArrayList<>();

        for (java.util.UUID reqId : plugin.socialManager().incomingFriendRequests(player.getUniqueId())) {
            OfflinePlayer off = Bukkit.getOfflinePlayer(reqId);
            PlayerProfile reqProf = plugin.statsManager().getIfPresent(reqId);
            String rName = off.getName() != null ? off.getName() : (reqProf != null ? reqProf.lastKnownName() : "Player");
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) skull.getItemMeta();
            if (sm != null) {
                Player onlineRequester = Bukkit.getPlayer(reqId);
                applyPlayerHead(sm, onlineRequester != null ? onlineRequester : off, reqId, rName);
                sm.displayName(Msg.parse("<" + Palette.GOLD + "><bold> Request: " + rName + "</bold></" + Palette.GOLD + ">"));
                sm.lore(List.of(
                    Msg.parse("<#94A3B8>Sent you a friend request</#94A3B8>"),
                    Msg.parse(""),
                    Msg.parse("<" + Palette.SUCCESS + "><bold>▸ Left-Click: Accept Friend Request</bold></" + Palette.SUCCESS + ">"),
                    Msg.parse("<" + Palette.DANGER + ">▸ Right-Click: Decline</" + Palette.DANGER + ">")
                ));
                skull.setItemMeta(sm);
            }
            rows.add(MenuSpec.Item.visual(skull, click -> handleFriendsClick(player, holder, click)));
        }

        List<java.util.UUID> sortedFriends = new ArrayList<>(myProf.friends());
        sortedFriends.sort((a, b) -> {
            boolean oa = Bukkit.getPlayer(a) != null;
            boolean ob = Bukkit.getPlayer(b) != null;
            return Boolean.compare(ob, oa);
        });

        for (java.util.UUID fid : sortedFriends) {
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
                    ? "<" + Palette.GOLD + "> In Match (" + sess.mode().id().toUpperCase(java.util.Locale.ROOT) + ")</" + Palette.GOLD + ">"
                    : "<" + Palette.SUCCESS + ">● Online — In Lobby</" + Palette.SUCCESS + ">";
            } else {
                statusLine = "<" + Palette.DIM + ">○ Offline</" + Palette.DIM + ">";
            }

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) skull.getItemMeta();
            if (sm != null) {
                applyPlayerHead(sm, off, fid, fName);
                sm.displayName(Msg.parse((onlineFriend != null ? "<" + Palette.SUCCESS + "><bold>" : "<" + Palette.MUTED + "><bold>") + fName + "</bold> <!shadow><#F8FAFC>" + fRank.pillGlyph() + "</#F8FAFC></!shadow>"));
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Msg.parse("<#94A3B8>Status:</#94A3B8> " + statusLine));
                lore.add(Msg.parse("<#94A3B8>Rank:</#94A3B8> <#F8FAFC>" + fRank.plainDisplayName() + "</#F8FAFC> <#64748B>·</#64748B> <" + Palette.GOLD + ">" + fElo + " ELO</" + Palette.GOLD + ">"));
                lore.add(Msg.parse(""));
                if (onlineFriend != null && onlineFriend.isOnline()) {
                    lore.add(Msg.parse("<" + Palette.SUCCESS + ">▸ Left-Click: Invite to Party</" + Palette.SUCCESS + ">"));
                    lore.add(Msg.parse("<" + Palette.ICE + ">▸ Shift + Left-Click: 1v1 Duel</" + Palette.ICE + ">"));
                }
                lore.add(Msg.parse("<" + Palette.DANGER + ">▸ Right-Click: Remove Friend</" + Palette.DANGER + ">"));
                sm.lore(lore);
                skull.setItemMeta(sm);
            }
            rows.add(MenuSpec.Item.visual(skull, click -> handleFriendsClick(player, holder, click)));
        }

        MenuSpec.Page<MenuSpec.Item> page = MenuSpec.Page.of(rows, requestedPage, gridSlots.length);
        holder.setPage(page.index());
        spec.page(gridSlots, page, item -> item);
        if (page.empty()) {
            spec.item(22, MenuItems.empty("No friends or requests yet.", "Add a friend", click -> openAddFriendOnlineMenu(player)));
        }

        spec.item(47, MenuSpec.Item.visual(customIcon(
            "icon_friend_add",
            "<" + Palette.SUCCESS + "><bold>+ Add Online Player</bold></" + Palette.SUCCESS + ">",
            List.of("<#94A3B8>Browse online players & click their head</#94A3B8>", "<#94A3B8>to send a friend request!</#94A3B8>", "", "<#4ADE80>▸ Browse Online Players</#4ADE80>")
        ), click -> handleFriendsClick(player, holder, click)));
        spec.item(40, MenuSpec.Item.visual(customIcon(
            "icon_message",
            "<" + Palette.ICE + "><bold>⌨ Type Player Name</bold></" + Palette.ICE + ">",
            List.of("<#94A3B8>Type a player's username in chat</#94A3B8>", "<#94A3B8>to send them a friend request.</#94A3B8>", "", "<#38BDF8>▸ Click to type in chat</#38BDF8>")
        ), click -> handleFriendsClick(player, holder, click)));
        
        spec.item(51, MenuSpec.Item.visual(customIcon(
            "icon_party_invites",
            "<" + Palette.GOLD + "><bold> Party & Custom Team Name</bold></" + Palette.GOLD + ">",
            List.of("<#94A3B8>Party and team settings</#94A3B8>", "", "<#FDE047>▸ Open Party Menu</#FDE047>")
        ), click -> handleFriendsClick(player, holder, click)));
        show(player, holder, spec);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
    }

    public void openAddFriendOnlineMenu(Player player) {
        openAddFriendOnlineMenu(player, 0);
    }

    public void openAddFriendOnlineMenu(Player player, int requestedPage) {
        BosketHolder holder = new BosketHolder(MenuType.ADD_FRIEND_ONLINE);
        MenuSpec.Builder spec = MenuSpec.builder(6, "FRIENDS");
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_FRIENDS, tr(player, "gui.title.friends", "FRIENDS")));

        PlayerProfile myProf = plugin.statsManager().getOrCreate(player);
        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
            .<Player>map(p -> p)
            .filter(p -> !p.getUniqueId().equals(player.getUniqueId()) && !myProf.isFriend(p.getUniqueId()))
            .toList();
        MenuSpec.Page<Player> page = MenuSpec.Page.of(candidates, requestedPage, SHOP_GRID_SLOTS.length);
        holder.setPage(page.index());

        int[] slots = SHOP_GRID_SLOTS;
        if (page.empty()) {
            spec.item(22, MenuItems.empty("Everyone online is already your friend.", "Open friends", click -> openFriendsMenu(player)));
        } else {
            for (int i = 0; i < page.entries().size(); i++) {
                Player other = page.entries().get(i);
                PlayerProfile oProf = plugin.statsManager().getOrCreate(other);
                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta sm = (SkullMeta) skull.getItemMeta();
                if (sm != null) {
                    applyPlayerHead(sm, other, other.getUniqueId(), other.getName());
                    sm.displayName(Msg.parse("<" + Palette.ICE + "><bold>" + other.getName() + "</bold></" + Palette.ICE + "> <!shadow><#F8FAFC>" + oProf.effectiveRank().pillGlyph() + "</#F8FAFC></!shadow>"));
                    sm.lore(List.of(
                        Msg.parse("<#94A3B8>ELO:</#94A3B8> <" + Palette.GOLD + ">" + oProf.elo() + "</" + Palette.GOLD + "> <#64748B>·</#64748B> <#94A3B8>Rank:</#94A3B8> <#F8FAFC>" + oProf.effectiveRank().plainDisplayName() + "</#F8FAFC>"),
                        Msg.parse(""),
                        Msg.parse("<" + Palette.SUCCESS + "><bold>▸ Left-Click: Send Friend Request</bold></" + Palette.SUCCESS + ">"),
                        Msg.parse("<" + Palette.GOLD + ">▸ Right-Click: Invite to Party</" + Palette.GOLD + ">")
                    ));
                    skull.setItemMeta(sm);
                }
                spec.item(slots[i], MenuSpec.Item.visual(skull, click -> handleAddFriendClick(player, holder, click)));
            }
        }

        show(player, holder, spec);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.25f);
    }

    public void openPartyMenu(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.PARTY_MENU);
        MenuSpec.Builder spec = MenuSpec.builder(6, "FRIENDS");
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_FRIENDS, tr(player, "gui.title.friends", "FRIENDS")));

        PlayerProfile myProf = plugin.statsManager().getOrCreate(player);
        SocialPartyManager.Party party = plugin.socialManager().getParty(player.getUniqueId());
        int partySize = plugin.socialManager().partySizeOrOne(player.getUniqueId());
        boolean isLeader = party == null || party.isLeader(player.getUniqueId());
        String currentTeamName = (party != null && party.hasCustomTeamName()) ? party.teamName() : "Default (Ignis / Frost)";

        spec.item(3, MenuSpec.Item.visual(customIcon(
            "ref_friends",
            "<" + Palette.SUCCESS + "><bold>Friends (" + myProf.friends().size() + ")</bold></" + Palette.SUCCESS + ">",
            List.of("<#94A3B8>View friends, online status & requests</#94A3B8>", "", "<#4ADE80>▸ Switch to Friends List</#4ADE80>")
        ), click -> handlePartyClick(player, holder, click)));

        java.util.UUID incomingPartyLeaderId = plugin.socialManager().pendingPartyInviteLeader(player.getUniqueId());
        if (incomingPartyLeaderId != null) {
            Player inviter = Bukkit.getPlayer(incomingPartyLeaderId);
            if (inviter != null && inviter.isOnline()) {
                ItemStack inviteSkull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta ism = (SkullMeta) inviteSkull.getItemMeta();
                if (ism != null) {
                    applyPlayerHead(ism, inviter, inviter.getUniqueId(), inviter.getName());
                    ism.displayName(Msg.parse("<" + Palette.SUCCESS + "><bold> Party Invite: " + inviter.getName() + "</bold></" + Palette.SUCCESS + ">"));
                    ism.lore(List.of(
                        Msg.parse("<#94A3B8>Invited you to a party</#94A3B8>"),
                        Msg.parse(""),
                        Msg.parse("<" + Palette.SUCCESS + "><bold>▸ Left-Click: Accept & Join Party</bold></" + Palette.SUCCESS + ">"),
                        Msg.parse("<" + Palette.DANGER + ">▸ Right-Click: Decline Invite</" + Palette.DANGER + ">")
                    ));
                    inviteSkull.setItemMeta(ism);
                }
                spec.item(4, MenuSpec.Item.visual(inviteSkull, click -> handlePartyClick(player, holder, click)));
            }
        }

        spec.item(5, MenuSpec.Item.infoVisual(customIcon(
            "icon_team",
            "<" + Palette.GOLD + "><bold>● Party & Team Name (" + partySize + "/4)</bold></" + Palette.GOLD + ">",
            List.of("<" + Palette.SUCCESS + ">✔ Viewing Party Menu</" + Palette.SUCCESS + ">")
        )));

        spec.item(13, MenuSpec.Item.visual(customIcon(
            "ref_bag",
            "<" + Palette.GOLD + "><bold> Party Team Name: </bold></" + Palette.GOLD + "><#F8FAFC><bold>" + currentTeamName + "</bold></#F8FAFC>",
            List.of(
                "<#94A3B8>Team name, 2 to 16 characters</#94A3B8>",
                "<#94A3B8>Displayed live on the</#94A3B8> <#F8FAFC>Scoreboard, Match BossBar,</#F8FAFC>",
                "<#F8FAFC>Court Jumbotron & Locker Room</#F8FAFC> <#94A3B8>when your party plays!</#94A3B8>",
                "",
                isLeader ? "<" + Palette.SUCCESS + "><bold>▸ Left-Click: Type New Team Name in Chat</bold></" + Palette.SUCCESS + ">" : "<" + Palette.DANGER + ">Only the Party Leader can rename the team</" + Palette.DANGER + ">",
                isLeader ? "<" + Palette.DANGER + ">▸ Right-Click: Reset to Default</" + Palette.DANGER + ">" : "<#64748B>Command: /party name <TeamName></#64748B>"
            )
        ), click -> handlePartyClick(player, holder, click)));

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
                    sm.displayName(Msg.parse((memberIsLeader ? "<" + Palette.GOLD + "><bold> " : "<" + Palette.ICE + "><bold>") + m.getName() + "</bold> <!shadow><#F8FAFC>" + mProf.effectiveRank().pillGlyph() + "</#F8FAFC></!shadow>"));
                    List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                    lore.add(Msg.parse("<#94A3B8>Role:</#94A3B8> " + (memberIsLeader ? "<" + Palette.GOLD + ">Party Leader</" + Palette.GOLD + ">" : "<#F8FAFC>Party Member</#F8FAFC>")));
                    lore.add(Msg.parse("<#94A3B8>ELO:</#94A3B8> <" + Palette.GOLD + ">" + mProf.elo() + "</" + Palette.GOLD + ">"));
                    if (isLeader && !m.getUniqueId().equals(player.getUniqueId())) {
                        lore.add(Msg.parse(""));
                        lore.add(Msg.parse("<" + Palette.GOLD + ">▸ Left-Click: Promote to Party Leader</" + Palette.GOLD + ">"));
                        lore.add(Msg.parse("<" + Palette.DANGER + ">▸ Right-Click: Remove from Party</" + Palette.DANGER + ">"));
                    }
                    sm.lore(lore);
                    skull.setItemMeta(sm);
                }
                spec.item(memberSlots[i], MenuSpec.Item.visual(skull, click -> handlePartyClick(player, holder, click)));
            } else {
                spec.item(memberSlots[i], MenuSpec.Item.visual(customIcon(
                    "icon_party_unavailable",
                    "<" + Palette.DIM + "><bold>Empty Party Slot #" + (i + 1) + "</bold></" + Palette.DIM + ">",
                    List.of("<#94A3B8>Click to invite someone</#94A3B8>", "", "<#4ADE80>▸ Invite Players</#4ADE80>")
                ), click -> handlePartyClick(player, holder, click)));
            }
        }

        spec.item(31, MenuSpec.Item.visual(customIcon(
            "icon_party_invites",
            "<" + Palette.SUCCESS + "><bold>+ Invite Players to Party</bold></" + Palette.SUCCESS + ">",
            List.of("<#94A3B8>Invite up to four players</#94A3B8>", "", "<#4ADE80>▸ Browse Online Players</#4ADE80>")
        ), click -> handlePartyClick(player, holder, click)));

        spec.item(47, MenuSpec.Item.visual(customIcon(
            "ref_arrow_big_right",
            "<" + Palette.WARN + "><bold>▶ Play Match Together</bold></" + Palette.WARN + ">",
            List.of("<#94A3B8>Queue as a team</#94A3B8>", "", "<#FDE047>▸ Choose Game Mode</#FDE047>")
        ), click -> handlePartyClick(player, holder, click)));
        if (party != null) {
            spec.item(40, MenuSpec.Item.visual(customIcon(
                "icon_party_remove",
                "<" + Palette.DANGER + "><bold>Leave Party</bold></" + Palette.DANGER + ">",
                List.of("<#94A3B8>Leave or disband your current party</#94A3B8>")
            ), click -> handlePartyClick(player, holder, click)));
        }
        
        show(player, holder, spec);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
    }

    public void openPartyInviteMenu(Player player) {
        openPartyInviteMenu(player, 0);
    }

    public void openPartyInviteMenu(Player player, int requestedPage) {
        BosketHolder holder = new BosketHolder(MenuType.PARTY_INVITE_ONLINE);
        MenuSpec.Builder spec = MenuSpec.builder(6, "FRIENDS");
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_PARTY, tr(player, "gui.title.friends", "FRIENDS")));

        PlayerProfile myProf = plugin.statsManager().getOrCreate(player);
        SocialPartyManager.Party party = plugin.socialManager().getParty(player.getUniqueId());

        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
            .<Player>map(p -> p)
            .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
            .filter(p -> party == null || !party.members().contains(p.getUniqueId()))
            .sorted((a, b) -> Boolean.compare(myProf.isFriend(b.getUniqueId()), myProf.isFriend(a.getUniqueId())))
            .toList();
        MenuSpec.Page<Player> page = MenuSpec.Page.of(candidates, requestedPage, SHOP_GRID_SLOTS.length);
        holder.setPage(page.index());

        if (page.empty()) {
            spec.item(22, MenuItems.empty("Everyone online is already in your party.", "Open party", click -> openPartyMenu(player)));
        } else {
            for (int i = 0; i < page.entries().size(); i++) {
                Player other = page.entries().get(i);
                PlayerProfile oProf = plugin.statsManager().getOrCreate(other);
                boolean isFriend = myProf.isFriend(other.getUniqueId());
                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta sm = (SkullMeta) skull.getItemMeta();
                if (sm != null) {
                    applyPlayerHead(sm, other, other.getUniqueId(), other.getName());
                    sm.displayName(Msg.parse((isFriend ? "<" + Palette.SUCCESS + "><bold>▪ " : "<" + Palette.ICE + "><bold>") + other.getName() + "</bold> <!shadow><#F8FAFC>" + oProf.effectiveRank().pillGlyph() + "</#F8FAFC></!shadow>"));
                    sm.lore(List.of(
                        Msg.parse((isFriend ? "<" + Palette.SUCCESS + ">Friend</" + Palette.SUCCESS + "> <#64748B>·</#64748B> " : "") + "<#94A3B8>ELO:</#94A3B8> <" + Palette.GOLD + ">" + oProf.elo() + "</" + Palette.GOLD + ">"),
                        Msg.parse(""),
                        Msg.parse("<" + Palette.SUCCESS + "><bold>▸ Click to Invite to Party</bold></" + Palette.SUCCESS + ">")
                    ));
                    skull.setItemMeta(sm);
                }
                spec.item(SHOP_GRID_SLOTS[i], MenuSpec.Item.visual(skull, click -> handlePartyInviteClick(player, holder, click)));
            }
        }

        show(player, holder, spec);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.25f);
    }

    public void openRanksGuide(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.RANKS_GUIDE);
        MenuSpec.Builder spec = MenuSpec.builder(6, "RANKS GUIDE");
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_RANKS, tr(player, "gui.title.ranks", "RANKS GUIDE")));

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        fr.bosketball.model.Rank myCompRank = profile.competitiveRank();
        fr.bosketball.model.Rank nextRank = myCompRank.nextCompetitiveRank();

        String progressText = nextRank != null
            ? "<" + Palette.GOLD + "><bold>" + profile.elo() + "</bold></" + Palette.GOLD + "> <#94A3B8>/</#94A3B8> <#F8FAFC>" + nextRank.minElo() + " ELO</#F8FAFC> <#64748B>(+" + Math.max(0, nextRank.minElo() - profile.elo()) + " needed for " + nextRank.plainDisplayName() + ")</#64748B>"
            : "<" + Palette.GOLD_SOFT + "><bold>" + profile.elo() + " ELO · top tier reached</bold></" + Palette.GOLD_SOFT + ">";

        spec.item(4, MenuSpec.Item.infoVisual(customIcon(
            "ref_cup_gold",
            "<" + Palette.WARN + "><bold>Your Competitive Rating</bold></" + Palette.WARN + ">",
            List.of(
                "<#94A3B8>Rank:</#94A3B8> <!shadow><#F8FAFC>" + myCompRank.pillGlyph() + "</#F8FAFC></!shadow> <#F8FAFC><bold>" + myCompRank.plainDisplayName() + "</bold></#F8FAFC>",
                "<#94A3B8>Progress:</#94A3B8> " + progressText,
                "",
                "<#94A3B8>· Everyone starts at</#94A3B8> <#F8FAFC><bold>1,000 ELO (Rookie)</bold></#F8FAFC><#94A3B8>!</#94A3B8>",
                "<#94A3B8>· Win Ranked 1v1, 2v2, 3v3 or 5v5 matches to gain ELO</#94A3B8>",
                "<#94A3B8>  and rank up</#94A3B8>"
            )
        )));

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

            spec.item(rankSlots[i], MenuSpec.Item.infoVisual(fr.bosketball.util.TooltipStyles.decorate(customIcon(
                current ? "icon_star_shield" : (reached ? "ref_cup_gold" : "ref_arrow_big_right"),
                "<!shadow><#F8FAFC>" + r.pillGlyph() + "</#F8FAFC></!shadow> <" + Palette.TEXT + "><bold>" + r.plainDisplayName() + "</bold></" + Palette.TEXT + ">",
                List.of(
                    "<#94A3B8>Threshold:</#94A3B8> <#F8FAFC>" + r.eloRangeDescription() + "</#F8FAFC>",
                    "",
                    stateLine
                )
            ), fr.bosketball.util.TooltipStyles.forRank(r))));
        }

        spec.item(38, MenuSpec.Item.infoVisual(customIcon(
            "ref_cup_gold",
            "<" + Palette.GOLD + "><bold>Top #1 / #2 / #3 ELO Badges</bold></" + Palette.GOLD + ">",
            List.of(
                "<#94A3B8>The 3 highest-ELO players on the entire server</#94A3B8>",
                "<#94A3B8>automatically wear live Prestige Badges:</#94A3B8>",
                "  <!shadow><#F8FAFC>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 1) + "</#F8FAFC></!shadow> <" + Palette.GOLD + ">#1 Gold Badge</" + Palette.GOLD + "> <#94A3B8>(1st ELO)</#94A3B8>",
                "  <!shadow><#F8FAFC>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 2) + "</#F8FAFC></!shadow> <" + Palette.TEXT + ">#2 Silver Badge</" + Palette.TEXT + "> <#94A3B8>(2nd ELO)</#94A3B8>",
                "  <!shadow><#F8FAFC>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 3) + "</#F8FAFC></!shadow> <" + Palette.AMBER + ">#3 Bronze Badge</" + Palette.AMBER + "> <#94A3B8>(3rd ELO)</#94A3B8>",
                "<#94A3B8>Shown in Chat, /gg, [TAB], Locker Room & Sidebar!</#94A3B8>"
            )
        )));

        spec.item(40, MenuSpec.Item.infoVisual(customIcon(
            "dunk_meteor_jam",
            "<" + Palette.SUCCESS + "><bold>Manual Skill Controls</bold></" + Palette.SUCCESS + ">",
            List.of(
                "<" + Palette.SUCCESS + ">· Hold Right-Click (Slot 1):</" + Palette.SUCCESS + "> <#F8FAFC>Draw Bow Power (0% → 100%)</#F8FAFC>",
                "  <#94A3B8>Release right-click at 85–100% for a green</#94A3B8>",
                "<" + Palette.GOLD + ">· Slam Dunk:</" + Palette.GOLD + "> <#F8FAFC>Get inside the Paint + face the Rim + Click once,</#F8FAFC>",
                "  <#94A3B8>the slam resolves by itself (20 s cooldown).</#94A3B8>",
                "<" + Palette.ICE + ">· Press Q (or Slot 2):</" + Palette.ICE + "> <#F8FAFC>Quick Pass / Mid-Air Alley-Oop</#F8FAFC>",
                "<" + Palette.VIOLET + ">· Sneak / Shift (or the Crossover item):</" + Palette.VIOLET + "> <#F8FAFC>Directional dash (steer with WASD)</#F8FAFC>",
                "<" + Palette.DANGER + ">· Left-Click on Defense:</" + Palette.DANGER + "> <#F8FAFC>Steal Ball / Jump Block</#F8FAFC>"
            )
        )));

        var econGuide = plugin.settings().economy();
        spec.item(42, MenuSpec.Item.infoVisual(customIcon(
            "ref_coins",
            "<" + Palette.GOLD_SOFT + "><bold>Coins & Collector Tags</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<#94A3B8>· Earn Coins in every match (+" + econGuide.winCoins() + " Win, +" + econGuide.mvpBonusCoins() + " MVP,</#94A3B8>",
                "<#94A3B8>  +" + econGuide.twoPointCoins() + "/2PT, +" + econGuide.threePointCoins() + "/3PT, +" + econGuide.assistCoins() + "/AST, +" + econGuide.ggRewardCoins() + " GG)!</#94A3B8>",
                "<#94A3B8>· Spend Coins in the</#94A3B8> <#FDE047>Shop</#FDE047> <#94A3B8>on Balls, Dunks & Tags.</#94A3B8>",
                "<#94A3B8>· Exclusive Honor Tags:</#94A3B8>",
                "  <!shadow><#F8FAFC>" + ChatTag.FOUNDER.glyph() + "</#F8FAFC></!shadow> <" + Palette.EMBER + ">Founder</" + Palette.EMBER + "> <#94A3B8>— Exclusive to MaxLananas</#94A3B8>",
                "  <!shadow><#F8FAFC>" + ChatTag.MYPINEAPPLE.glyph() + "</#F8FAFC></!shadow> <" + Palette.GOLD + ">MyPineapple</" + Palette.GOLD + "> <#94A3B8>— Official Team Exclusive</#94A3B8>"
            )
        )));

        
        spec.item(43, MenuItems.action("icon_podium", "View leaderboards", MenuItems.lore("See who leads the server."), click -> openLeaderboards(player)));
        show(player, holder, spec);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.22f);
    }

    public void openMapStudio(Player player) {
        if (!Perms.map(player)) {
            Msg.send(player, Msg.error("Arena creation requires <#F8FAFC>bosketball.map</#F8FAFC>."));
            return;
        }
        BosketHolder holder = new BosketHolder(MenuType.MAP_STUDIO);
        MenuSpec.Builder spec = MenuSpec.builder(6, "ARENA STUDIO");
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_STUDIO,
                tr(player, "gui.title.studio", "ARENA STUDIO")));

        var session = plugin.mapEditor().getOrBindSession(player, null);
        fr.bosketball.arena.Arena arena = session != null ? plugin.arenaManager().getArena(session.arenaId) : null;
        String arenaLabel = arena != null ? arena.id() : "None yet (Click Auto-Setup or Create!)";

        spec.item(4, MenuSpec.Item.infoVisual(customIcon(
            "ref_arrow_big_right",
            "<" + Palette.ICE + "><bold>Editing Arena: </bold></" + Palette.ICE + "><#F8FAFC><bold>" + arenaLabel + "</bold></#F8FAFC>",
            List.of(
                "<#94A3B8>Super-easy 2-step setup on any custom floor:</#94A3B8>",
                "<#FDE047>1.</#FDE047> <#F8FAFC>Get the Wand</#F8FAFC> <#94A3B8>& Left/Right-Click 2 opposite floor corners</#94A3B8>",
                "<#FDE047>2.</#FDE047> <#F8FAFC>Click</#F8FAFC> <" + Palette.SUCCESS + "><bold> 1-Click Auto-Setup Full Court</bold></" + Palette.SUCCESS + "><#94A3B8>!</#94A3B8>",
                "<#94A3B8>Or fine-tune each part below</#94A3B8>"
            )
        )));

        spec.item(19, MenuSpec.Item.visual(customIcon(
            "court_wand",
            "<" + Palette.ICE + "><bold>1. Get Floor Corner Wand</bold></" + Palette.ICE + ">",
            List.of(
                "<#94A3B8>Gives you the Arena Setup Wand:</#94A3B8>",
                "<#FDE047>· Left-Click Floor Block:</#FDE047> <#F8FAFC>Corner #1</#F8FAFC>",
                "<#FDE047>· Right-Click Floor Block:</#FDE047> <#F8FAFC>Corner #2</#F8FAFC>",
                "<#38BDF8>· Shift + Right-Click Air:</#38BDF8> <#F8FAFC>Re-open this Studio</#F8FAFC>",
                "",
                "<#38BDF8>▸ Click to receive Wand</#38BDF8>"
            )
        ), click -> handleMapClick(player, holder, click)));

        spec.item(21, MenuSpec.Item.visual(customIcon(
            "icon_star_shield",
            "<" + Palette.SUCCESS + "><bold> 2. 1-Click Auto-Setup Full Court!</bold></" + Palette.SUCCESS + ">",
            List.of(
                "<#94A3B8>Automatically places EVERYTHING at once:</#94A3B8>",
                "<#F8FAFC>✔ Both Hoops, Backboards & Closed Nets</#F8FAFC>",
                "<#F8FAFC>✔ Flush White Court Lines (3PT, Keys, Half-Court)</#F8FAFC>",
                "<#F8FAFC>✔ Overhead 4-Sided Scoreboard (Jumbotron)</#F8FAFC>",
                "<#F8FAFC>✔ Out-of-Bounds & Invisible Ball Barriers</#F8FAFC>",
                "",
                "<" + Palette.SUCCESS + "><bold>▸ Click to Auto-Build Court Now!</bold></" + Palette.SUCCESS + ">"
            )
        ), click -> handleMapClick(player, holder, click)));

        spec.item(23, MenuSpec.Item.visual(customIcon(
            "icon_dunk",
            "<" + Palette.DANGER + "><bold>Place Hoops & Backboards Here</bold></" + Palette.DANGER + ">",
            List.of(
                "<#94A3B8>Stand where you want a basket & face the court:</#94A3B8>",
                "<" + Palette.DANGER + ">▸ Left-Click: Place Ignis Target Hoop Here</" + Palette.DANGER + ">",
                "<" + Palette.ICE + ">▸ Right-Click: Place Frost Target Hoop Here</" + Palette.ICE + ">"
            )
        ), click -> handleMapClick(player, holder, click)));

        boolean linesOn = arena != null && arena.isCourtLinesEnabled();
        String lineMat = arena != null ? arena.courtLinePrimaryMaterial().name() : "WHITE_CONCRETE";
        spec.item(25, MenuSpec.Item.visual(customIcon(
            "ref_bag",
            "<" + Palette.TEXT + "><bold>White Court Lines: </bold></" + Palette.TEXT + ">" + (linesOn ? "<" + Palette.SUCCESS + "><bold>ON</bold></" + Palette.SUCCESS + ">" : "<" + Palette.DANGER + "><bold>ᴏꜰꜰ</bold></" + Palette.DANGER + ">"),
            List.of(
                "<#94A3B8>Material:</#94A3B8> <#F8FAFC>" + lineMat + "</#F8FAFC>",
                "<#94A3B8>Preset:</#94A3B8> <#38BDF8>" + (arena != null ? arena.courtLinesPreset().name() : "FULL") + "</#38BDF8>",
                "",
                "<#FDE047>▸ Left-Click: Toggle White Lines ON / OFF</#FDE047>",
                "<#38BDF8>▸ Right-Click: Cycle Preset (FULL / MINIMAL / STREET)</#38BDF8>"
            )
        ), click -> handleMapClick(player, holder, click)));

        boolean jumboOn = arena != null && arena.isJumbotronEnabled();
        spec.item(29, MenuSpec.Item.visual(customIcon(
            "ref_cup_gold",
            "<" + Palette.GOLD + "><bold>Overhead Scoreboard: </bold></" + Palette.GOLD + ">" + (jumboOn ? "<" + Palette.SUCCESS + "><bold>ON</bold></" + Palette.SUCCESS + ">" : "<" + Palette.MUTED + "><bold>ᴏꜰꜰ</bold></" + Palette.MUTED + ">"),
            List.of(
                "<#94A3B8>Places the 4-sided live Scoreboard</#94A3B8>",
                "<#94A3B8>at your current position (fly up above center court!).</#94A3B8>",
                "",
                "<#FDE047>▸ Left-Click: Place Scoreboard Here</#FDE047>",
                "<" + Palette.DANGER + ">▸ Right-Click: Hide Scoreboard</" + Palette.DANGER + ">"
            )
        ), click -> handleMapClick(player, holder, click)));

        spec.item(31, MenuSpec.Item.visual(customIcon(
            "ref_friends",
            "<" + Palette.SUCCESS + "><bold>Set Team Spawns Here</bold></" + Palette.SUCCESS + ">",
            List.of(
                "<#94A3B8>Optional custom spawn override at your feet:</#94A3B8>",
                "<" + Palette.DANGER + ">▸ Left-Click: Set Ignis Spawn #1 Here</" + Palette.DANGER + ">",
                "<" + Palette.ICE + ">▸ Right-Click: Set Frost Spawn #1 Here</" + Palette.ICE + ">"
            )
        ), click -> handleMapClick(player, holder, click)));

        spec.item(33, MenuSpec.Item.visual(customIcon(
            "icon_portal",
            "<" + Palette.VIOLET + "><bold>Build Full Stadium Structure</bold></" + Palette.VIOLET + ">",
            List.of(
                "<#94A3B8>Generates a complete hardwood stadium</#94A3B8>",
                "<#94A3B8>(blocks, bleachers, hoops, scoreboard)</#94A3B8>",
                "",
                "<#C084FC>▸ Shift-Click to Generate Full Stadium</#C084FC>"
            )
        ), click -> handleMapClick(player, holder, click)));

        spec.item(47, MenuSpec.Item.visual(customIcon(
            "ref_gear",
            "<" + Palette.ICE + "><bold>Toggle 3D Particle Blueprint</bold></" + Palette.ICE + ">",
            List.of("<#94A3B8>Show/hide live court boundary & hoop particles</#94A3B8>", "", "<#38BDF8>▸ Click to toggle</#38BDF8>")
        ), click -> handleMapClick(player, holder, click)));
        
        spec.item(51, MenuSpec.Item.visual(customIcon(
            "ref_arrow_big_right",
            "<" + Palette.SUCCESS + "><bold> Save & Test in Practice!</bold></" + Palette.SUCCESS + ">",
            List.of("<#94A3B8>Saves arena & teleports you onto the court</#94A3B8>", "<#94A3B8>with a basketball right away!</#94A3B8>", "", "<#4ADE80>▸ Save & Play Now</#4ADE80>")
        ), click -> handleMapClick(player, holder, click)));
        show(player, holder, spec);
        Msg.playPersonalPackSound(player, "ui.click", 0.75f, 1.2f);
    }

    public void openSpectateBrowser(Player player) {
        openSpectateBrowser(player, 0);
    }

    public void openSpectateBrowser(Player player, int requestedPage) {
        List<fr.bosketball.game.MatchSession> live = plugin.gameManager().spectatableSessions();
        MenuSpec.Page<fr.bosketball.game.MatchSession> page = MenuSpec.Page.of(live, requestedPage, SHOP_GRID_SLOTS.length);
        BosketHolder holder = new BosketHolder(MenuType.SPECTATE_BROWSER, page.index());
        MenuSpec.Builder spec = MenuSpec.builder(6, "Spectate");
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_SPECTATE, tr(player, "gui.title.spectate", "SPECTATE")));
        for (int i = 0; i < page.entries().size(); i++) {
            var sess = page.entries().get(i);
            int slot = SHOP_GRID_SLOTS[i];
            holder.slotStringMap().put(slot, sess.arena().id());
            spec.item(slot, MenuItems.action("icon_battle", "Spectate " + sess.arena().displayName(), List.of(
                MenuItems.row("Mode", MenuItems.plain(sess.mode().smallCapsTitle())),
                MenuItems.row("Score", sess.teamDisplayName(fr.bosketball.model.TeamSide.IGNIS) + " " + sess.score(fr.bosketball.model.TeamSide.IGNIS)
                    + " " + MenuItems.SEPARATOR + " " + sess.score(fr.bosketball.model.TeamSide.FROST) + " " + sess.teamDisplayName(fr.bosketball.model.TeamSide.FROST)),
                MenuItems.row("Clock", sess.formattedClock()), MenuItems.row("Players", sess.participantCount())),
                click -> handleSpectateClick(player, holder, click)));
        }
        if (page.empty()) {
            spec.item(22, MenuItems.empty("No live match is running right now.", "Choose a mode", click -> openPlayModes(player)));
        }
        spec.pagination(page, click -> openSpectateBrowser(player, page.previous()), click -> openSpectateBrowser(player, page.next()));
        show(player, holder, spec);
    }

    public void openPlayerInteractMenu(Player viewer, Player target) {
        if (viewer == null || target == null || !target.isOnline()) {
            return;
        }
        BosketHolder holder = new BosketHolder(MenuType.PLAYER_INTERACT);
        holder.setTargetPlayerId(target.getUniqueId());
        MenuSpec.Builder spec = MenuSpec.builder(6, "Player");
        holder.setTitle(guiTitle(Glyphs.ICON_STAR, "<" + Palette.ICE + "><bold>" + target.getName() + "</bold></" + Palette.ICE + ">"));

        PlayerProfile myProf = plugin.statsManager().getOrCreate(viewer);
        PlayerProfile tProf = plugin.statsManager().getOrCreate(target);
        boolean alreadyFriends = myProf.isFriend(target.getUniqueId());

        spec.item(4, MenuSpec.Item.visual(buildPlayerSkullCard(target, tProf), click -> handleInteractClick(viewer, holder, click)));

        spec.item(11, MenuSpec.Item.visual(customIcon(
            "icon_duel",
            "<" + Palette.WARN + "><bold> Challenge to 1v1 Duel</bold></" + Palette.WARN + ">",
            List.of(
                "<#94A3B8>Send or accept a Ranked 1v1 Duel</#94A3B8>",
                "<#94A3B8>against <#F8FAFC>" + target.getName() + "</#F8FAFC>!</#94A3B8>",
                "",
                "<#FDE047>▸ Click to Duel</#FDE047>"
            )
        ), click -> handleInteractClick(viewer, holder, click)));
        spec.item(13, MenuSpec.Item.visual(customIcon(
            "icon_party_invites",
            "<" + Palette.SUCCESS + "><bold> Invite to Party</bold></" + Palette.SUCCESS + ">",
            List.of(
                "<#94A3B8>Team up in a Party (up to 4 players)</#94A3B8>",
                "<#94A3B8>Play 2v2, 3v3 or 5v5 together</#94A3B8>",
                "",
                "<#4ADE80>▸ Click to Invite</#4ADE80>"
            )
        ), click -> handleInteractClick(viewer, holder, click)));
        spec.item(15, MenuSpec.Item.visual(customIcon(
            alreadyFriends ? "icon_friend_remove" : "icon_friend_add",
            alreadyFriends
                ? "<" + Palette.SUCCESS + "><bold>✔ Friends with " + target.getName() + "</bold></" + Palette.SUCCESS + ">"
                : "<" + Palette.ICE + "><bold> Add Friend</bold></" + Palette.ICE + ">",
            alreadyFriends
                ? List.of("<#94A3B8>Already on your friends list</#94A3B8>", "", "<" + Palette.DANGER + ">▸ Right-Click to Remove Friend</" + Palette.DANGER + ">")
                : List.of("<#94A3B8>Send a friend request</#94A3B8>", "", "<#38BDF8>▸ Click to Add Friend</#38BDF8>")
        ), click -> handleInteractClick(viewer, holder, click)));

        show(viewer, holder, spec);
        Msg.playPersonalPackSound(viewer, "ui.click", 0.75f, 1.25f);
    }

    public void openServerAdminPanel(Player admin) {
        if (!Perms.admin(admin)) {
            Msg.send(admin, Msg.error("The Server Control Panel requires <#F8FAFC>bosketball.admin</#F8FAFC>."));
            return;
        }
        BosketHolder holder = new BosketHolder(MenuType.SERVER_ADMIN);
        MenuSpec.Builder spec = MenuSpec.builder(6, "Server admin");
        holder.setTitle(guiTitle(Glyphs.ICON_CROWN, "<" + Palette.GOLD + "><bold>Server Owner Control Center</bold></" + Palette.GOLD + ">"));

        boolean maint = plugin.settings().motd().maintenanceMode();
        boolean chatMuted = plugin.isGlobalChatMuted();
        boolean building = plugin.protectionManager().isInBuildMode(admin.getUniqueId());

        spec.item(10, MenuSpec.Item.visual(customIcon(
            maint ? "ref_lock" : "ref_check",
            "<" + Palette.GOLD + "><bold> Maintenance Mode: </bold></" + Palette.GOLD + ">" + (maint ? "<" + Palette.DANGER + "><bold>ᴀᴄᴛɪᴠᴇ</bold></" + Palette.DANGER + ">" : "<" + Palette.SUCCESS + "><bold>ᴘᴜʙʟɪᴄ ᴏᴘᴇɴ</bold></" + Palette.SUCCESS + ">"),
            List.of(
                "<#94A3B8>Locks server logins to Staff/OPs only &</#94A3B8>",
                "<#94A3B8>displays the Maintenance MOTD banner.</#94A3B8>",
                "",
                maint ? "<" + Palette.SUCCESS + ">▸ Click to Reopen Server to Public</" + Palette.SUCCESS + ">" : "<" + Palette.DANGER + ">▸ Click to Enable Maintenance Mode</" + Palette.DANGER + ">"
            )
        ), click -> handleAdminClick(admin, holder, click)));

        spec.item(12, MenuSpec.Item.visual(customIcon(
            chatMuted ? "icon_sound_off" : "icon_message",
            "<" + Palette.ICE + "><bold> Global Chat: </bold></" + Palette.ICE + ">" + (chatMuted ? "<" + Palette.DANGER + "><bold>ᴍᴜᴛᴇᴅ</bold></" + Palette.DANGER + ">" : "<" + Palette.SUCCESS + "><bold>ᴏᴘᴇɴ</bold></" + Palette.SUCCESS + ">"),
            List.of(
                "<#94A3B8>Temporarily hush global chat for</#94A3B8>",
                "<#94A3B8>tournaments or official announcements.</#94A3B8>",
                "",
                "<#FDE047>▸ Click to toggle Global Chat Mute</#FDE047>"
            )
        ), click -> handleAdminClick(admin, holder, click)));

        spec.item(14, MenuSpec.Item.visual(customIcon(
            "court_wand",
            "<" + Palette.VIOLET + "><bold> Builder Mode: </bold></" + Palette.VIOLET + ">" + (building ? "<" + Palette.SUCCESS + "><bold>ᴇɴᴀʙʟᴇᴅ</bold></" + Palette.SUCCESS + ">" : "<" + Palette.MUTED + "><bold>ᴅɪꜱᴀʙʟᴇᴅ</bold></" + Palette.MUTED + ">"),
            List.of(
                "<#94A3B8>Toggles Creative Mode & manual block</#94A3B8>",
                "<#94A3B8>editing alongside FAWE & Axiom.</#94A3B8>",
                "",
                "<#C084FC>▸ Click to toggle /build</#C084FC>"
            )
        ), click -> handleAdminClick(admin, holder, click)));

        spec.item(16, MenuSpec.Item.visual(customIcon(
            "ref_home",
            "<" + Palette.SUCCESS + "><bold> Set Lobby Spawn Here</bold></" + Palette.SUCCESS + ">",
            List.of(
                "<#94A3B8>Saves your exact current coordinates &</#94A3B8>",
                "<#94A3B8>look direction as the server Lobby Spawn.</#94A3B8>",
                "",
                "<#4ADE80>▸ Click to save Lobby Spawn</#4ADE80>"
            )
        ), click -> handleAdminClick(admin, holder, click)));

        String shaShort = plugin.resourcePackHost().sha1Hex();
        if (shaShort.length() > 10) {
            shaShort = shaShort.substring(0, 10) + "…";
        }
        spec.item(22, MenuSpec.Item.infoVisual(customIcon(
            "ref_cup_gold",
            "<" + Palette.GOLD_SOFT + "><bold> Live Network Telemetry</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<#94A3B8>Online Players:</#94A3B8> <#F8FAFC>" + Bukkit.getOnlinePlayers().size() + "</#F8FAFC>",
                "<#94A3B8>In Match:</#94A3B8> <" + Palette.SUCCESS + ">" + plugin.gameManager().activePlayerCount() + "</" + Palette.SUCCESS + "> <#64748B>·</#64748B> <#94A3B8>In Queue:</#94A3B8> <" + Palette.ICE + ">" + plugin.gameManager().queuedCount() + "</" + Palette.ICE + ">",
                "<#94A3B8>Arenas Registered:</#94A3B8> <#F8FAFC>" + plugin.arenaManager().allArenas().size() + "</#F8FAFC>",
                "<#94A3B8>Player Profiles:</#94A3B8> <#F8FAFC>" + plugin.statsManager().allProfiles().size() + "</#F8FAFC>",
                "<#94A3B8>Pack SHA-1:</#94A3B8> <#38BDF8>" + (shaShort.isEmpty() ? "N/A" : shaShort) + "</#38BDF8>"
            )
        )));

        spec.item(28, MenuSpec.Item.visual(customIcon(
            "ref_arrow_big_right",
            "<" + Palette.ICE + "><bold> Open OP Arena Studio</bold></" + Palette.ICE + ">",
            List.of(
                "<#94A3B8>1-Click Auto-Setup Courts, Hoops,</#94A3B8>",
                "<#94A3B8>White Court Lines & Scoreboards!</#94A3B8>",
                "",
                "<#38BDF8>▸ Open Arena Studio</#38BDF8>"
            )
        ), click -> handleAdminClick(admin, holder, click)));

        spec.item(30, MenuSpec.Item.visual(customIcon(
            "icon_shelves",
            "<" + Palette.GOLD + "><bold> Push Resource Pack to All</bold></" + Palette.GOLD + ">",
            List.of(
                "<#94A3B8>Sends the official Resource Pack prompt</#94A3B8>",
                "<#94A3B8>to every currently online player.</#94A3B8>",
                "",
                "<#FDE047>▸ Click to broadcast Resource Pack</#FDE047>"
            )
        ), click -> handleAdminClick(admin, holder, click)));

        spec.item(32, MenuSpec.Item.visual(customIcon(
            "ref_chest",
            "<" + Palette.SUCCESS + "><bold> Force-Save All Data Now</bold></" + Palette.SUCCESS + ">",
            List.of(
                "<#94A3B8>Atomically flushes all player profiles,</#94A3B8>",
                "<#94A3B8>arenas & leaderboards to disk.</#94A3B8>",
                "",
                "<#4ADE80>▸ Click to flush to disk</#4ADE80>"
            )
        ), click -> handleAdminClick(admin, holder, click)));

        spec.item(34, MenuSpec.Item.visual(customIcon(
            "icon_reload",
            "<" + Palette.MUTED + "><bold> Reload Configs & Permissions</bold></" + Palette.MUTED + ">",
            List.of(
                "<#94A3B8>Reloads config.yml, shop.yml, spawn.yml,</#94A3B8>",
                "<#94A3B8>permissions.yml & MOTD live!</#94A3B8>",
                "",
                "<#F8FAFC>▸ Click to reload</#F8FAFC>"
            )
        ), click -> handleAdminClick(admin, holder, click)));

        int holoCount = plugin.lobbyHolograms() != null ? plugin.lobbyHolograms().allHolograms().size() : 0;
        spec.item(38, MenuSpec.Item.visual(customIcon(
            "trophy_mvp",
            "<" + Palette.GOLD + "><bold> Place 3D Leaderboard Hologram</bold></" + Palette.GOLD + ">",
            List.of(
                "<#94A3B8>Spawns a rotating 3D Trophy + Top 5</#94A3B8>",
                "<#94A3B8>Leaderboard</#94A3B8>",
                "",
                "<#FDE047>Left-Click:</#FDE047> <#F8FAFC>Top ELO Hologram</#F8FAFC>",
                "<#38BDF8>Right-Click:</#38BDF8> <#F8FAFC>Top Scorers Hologram</#F8FAFC>"
            )
        ), click -> handleAdminClick(admin, holder, click)));
        spec.item(40, MenuSpec.Item.visual(customIcon(
            "prop_play_emblem",
            "<" + Palette.SUCCESS + "><bold> Place 3D Play / Shop Station</bold></" + Palette.SUCCESS + ">",
            List.of(
                "<#94A3B8>Spawns an interactive clickable 3D</#94A3B8>",
                "<#94A3B8>station in the Lobby (" + holoCount + " active)!</#94A3B8>",
                "",
                "<#4ADE80>Left-Click:</#4ADE80> <#F8FAFC>3D Play Station</#F8FAFC>",
                "<#FBBF24>Right-Click:</#FBBF24> <#F8FAFC>3D Shop Crate</#F8FAFC>"
            )
        ), click -> handleAdminClick(admin, holder, click)));
        var hof = plugin.hallOfFame();
        spec.item(44, MenuSpec.Item.visual(customIcon(
            "ref_cup_gold",
            "<" + Palette.GOLD_SOFT + "><bold> Physical Hall of Fame</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<#94A3B8>A real 3D podium with the skins of</#94A3B8>",
                "<#94A3B8>the season's #1 / #2 / #3 players.</#94A3B8>",
                "",
                "<#94A3B8>Current:</#94A3B8> <#F8FAFC>" + (hof == null ? "Not placed" : hof.summary()) + "</#F8FAFC>",
                "",
                "<#FDE047>Left-Click:</#FDE047> <#F8FAFC>Build it where you stand</#F8FAFC>",
                "<#38BDF8>Right-Click:</#38BDF8> <#F8FAFC>Refresh from leaderboard</#F8FAFC>",
                "<" + Palette.DANGER + ">Shift-Click:</" + Palette.DANGER + "> <#F8FAFC>Remove the podium</#F8FAFC>"
            )
        ), click -> handleAdminClick(admin, holder, click)));

        spec.item(42, MenuSpec.Item.visual(customIcon(
            "icon_delete",
            "<" + Palette.DANGER + "><bold> Remove Nearest 3D Hologram</bold></" + Palette.DANGER + ">",
            List.of(
                "<#94A3B8>Deletes the closest 3D Hologram Station</#94A3B8>",
                "<#94A3B8>within 8 blocks of your position.</#94A3B8>",
                "",
                "<" + Palette.DANGER + ">▸ Click to remove nearest</" + Palette.DANGER + ">"
            )
        ), click -> handleAdminClick(admin, holder, click)));

        
        show(admin, holder, spec);
        Msg.playPersonalPackSound(admin, "ui.click", 0.75f, 1.2f);
    }

    public void openSettings(Player player) {
        BosketHolder holder = new BosketHolder(MenuType.SETTINGS);
        MenuSpec.Builder spec = MenuSpec.builder(6, "SETTINGS");
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_SETTINGS, tr(player, "gui.title.settings", "SETTINGS")));

        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        PlayerSettings st = profile.settings();

        spec.item(19, MenuSpec.Item.visual(settingToggle("icon_dunk", "Court Animations", "Dribble & shot gather animations", st.customAnimations()), click -> handleSettingsClick(player, holder, click)));
        spec.item(20, MenuSpec.Item.visual(settingToggle("ref_region", "Team Floor Rings", "Ignis & Frost floor rings on court", st.floorAuraRings()), click -> handleSettingsClick(player, holder, click)));
        spec.item(21, MenuSpec.Item.visual(settingToggle(st.customSounds() ? "icon_sound_on" : "icon_sound_off", "Custom Sound Effects", "Swish, buzzer, whistle & court audio", st.customSounds()), click -> handleSettingsClick(player, holder, click)));
        spec.item(22, MenuSpec.Item.visual(settingToggle("icon_camera", "Third-Person Visuals", "Show extra cosmetic effects in F5", st.selfBodyRigInF5()), click -> handleSettingsClick(player, holder, click)));
        spec.item(23, MenuSpec.Item.visual(settingToggle("icon_replay", "Highlight Replay Camera", "Instant replay camera on clutch buckets", st.cinematicScoreCam()), click -> handleSettingsClick(player, holder, click)));
        spec.item(24, MenuSpec.Item.visual(settingToggle("icon_party_invites", "Party Invites", "Allow incoming Party invitations", st.partyInvites()), click -> handleSettingsClick(player, holder, click)));
        spec.item(25, MenuSpec.Item.visual(settingToggle("ref_friends", "Status in [TAB]", "Show your live status (In Match, Queue, AFK…)", st.showStatusInTab()), click -> handleSettingsClick(player, holder, click)));
        spec.item(28, MenuSpec.Item.visual(settingToggle("ref_trophy", "Quest Notifications", "Toast when a quest or contract completes", st.showQuestNotifications()), click -> handleSettingsClick(player, holder, click)));
        spec.item(29, MenuSpec.Item.visual(settingToggle("ref_medal_gold", "Chat Announcements", "Server broadcasts in your chat", st.chatAnnouncements()), click -> handleSettingsClick(player, holder, click)));
        int soundPercent = Math.round(st.uiSoundVolume() * 100f);
        spec.item(30, MenuSpec.Item.visual(customIcon(
            "icon_sound_on",
            "<" + Palette.GOLD + "><bold>UI Sound Volume</bold></" + Palette.GOLD + ">",
            List.of(
                "<#94A3B8>Current:</#94A3B8> <#F8FAFC>" + soundPercent + "%</#F8FAFC>",
                volumeBar(soundPercent),
                "",
                "<#94A3B8>Shot-clock ticks, whistles, buzzers,</#94A3B8>",
                "<#94A3B8>clicks and every pack sound effect.</#94A3B8>",
                "",
                "<#FDE047>▸ Right-click to raise</#FDE047>",
                "<#FDE047>▸ Left-click to lower</#FDE047>"
            )
        ), click -> handleSettingsClick(player, holder, click)));

        var lang = plugin.lang().forPlayer(player);
        spec.item(31, MenuSpec.Item.visual(customIcon(
            "ref_medal_gold",
            "<" + Palette.ICE + "><bold> Language</bold></" + Palette.ICE + ">",
            List.of(
                "<#94A3B8>Current:</#94A3B8> <#F8FAFC>" + lang.locale().flag() + " " + lang.locale().displayName() + "</#F8FAFC>",
                "<#94A3B8>Base language:</#94A3B8> <#F8FAFC>English</#F8FAFC>",
                "<#94A3B8>Coverage:</#94A3B8> <#F8FAFC>" + fr.bosketball.i18n.Lang.coverage(lang.locale()) + "%</#F8FAFC>",
                "",
                "<#94A3B8>All menus, messages & broadcasts</#94A3B8>",
                "<#94A3B8>are translated into your language.</#94A3B8>",
                "",
                "<#FDE047>▸ Click to switch language</#FDE047>"
            )
        ), click -> handleSettingsClick(player, holder, click)));

        
        show(player, holder, spec);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.22f);
    }

    private String volumeBar(int percent) {
        int filled = Math.round(percent / 10f);
        StringBuilder bar = new StringBuilder("<#64748B>[</#64748B>");
        for (int i = 0; i < 10; i++) {
            if (i < filled) {
                bar.append(percent <= 20 ? "<#F87171>|</#F87171>" : percent <= 50 ? "<#FDE047>|</#FDE047>" : "<" + Palette.SUCCESS + ">|</" + Palette.SUCCESS + ">");
            } else {
                bar.append("<#64748B>\u00b7</#64748B>");
            }
        }
        return bar.append("<#64748B>]</#64748B>").toString();
    }

    private ItemStack settingToggle(String iconName, String title, String desc, boolean enabled) {
        return customIcon(
            iconName,
            "<#F8FAFC><bold>" + title + "</bold></#F8FAFC>",
            List.of(
                "<#94A3B8>" + desc + "</#94A3B8>",
                "",
                enabled ? "<" + Palette.SUCCESS + "><bold>● Enabled</bold></" + Palette.SUCCESS + ">" : "<" + Palette.DANGER + "><bold>○ Disabled</bold></" + Palette.DANGER + ">",
                "<#FDE047>▸ Click to toggle</#FDE047>"
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
        if (event.getClickedInventory() != holder.getInventory() || holder.spec() == null) {
            return;
        }
        MenuSpec.Item item = holder.spec().items().get(event.getRawSlot());
        if (item == null || item.disabled() || item.action() == null) {
            return;
        }
        item.action().click(new MenuSpec.Click(player, event.getRawSlot(), event.isRightClick(),
            event.isShiftClick(), event.getCurrentItem()));
    }

    private void handleHubClick(Player player, BosketHolder holder, MenuSpec.Click click) {
        int slot = click.slot();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

                switch (slot) {
                    case 13, 31 -> openRanksGuide(player);
                    case 20 -> openPlayModes(player);
                    case 22 -> {
                        if (click.right()) {
                            openCosmetics(player);
                        } else {
                            openShop(player);
                        }
                    }
                    case 24 -> {
                        if (click.right()) {
                            openTagsVault(player, 0);
                        } else {
                            openLockerRoom(player);
                        }
                    }
                    case 29 -> {
                        if (click.shift()) {
                            openGuildMenu(player);
                        } else if (click.right()) {
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
                        if (click.shift()) {
                            openMailbox(player);
                        } else if (click.right()) {
                            openAchievements(player, null, 0);
                        } else {
                            openQuests(player);
                        }
                    }
                    case 41 -> openMatchHistory(player);
                    case 43 -> openSettings(player);
                    case 44 -> {
                        player.closeInventory();
                        plugin.gameManager().leaveAll(player, true);
                    }
                    case 40 -> {
                        if (Perms.admin(player)) {
                            if (click.right()) {
                                openMapStudio(player);
                            } else {
                                openServerAdminPanel(player);
                            }
                        } else {
                            Msg.sendDiscordCard(player, plugin.settings().links().discord());
                        }
                    }
                    default -> {
                    }
                }
            
    }

    private void handleSeasonClick(Player player, BosketHolder holder, MenuSpec.Click click) {
        int slot = click.slot();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

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

    private void handlePlayClick(Player player, BosketHolder holder, MenuSpec.Click click) {
        int slot = click.slot();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

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
                }
            
    }

    private void handleBotClick(Player player, BosketHolder holder, MenuSpec.Click click) {
        int slot = click.slot();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

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
                        Msg.send(player, Msg.info("Start Practice or a match first (<#F8FAFC>/play</#F8FAFC>), "
                            + "then the AI difficulty applies to your court."));
                        return;
                    }
                    botSession.setBotDifficulty(tier);
                    Msg.send(player, Msg.success("AI difficulty set to <#F8FAFC>" + tier.displayName()
                        + "</#F8FAFC> — they spawn on the next fill."));
                    Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
                    openBotPanel(player);
                    return;
                }
                if (botSession == null) {
                    Msg.send(player, Msg.info("You are not on a court yet — start <#F8FAFC>Practice</#F8FAFC> "
                        + "or queue with <#F8FAFC>/play</#F8FAFC>."));
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

    private void handleSpectateClick(Player player, BosketHolder holder, MenuSpec.Click click) {
        int slot = click.slot();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

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

    private void handleInteractClick(Player player, BosketHolder holder, MenuSpec.Click click) {
        int slot = click.slot();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

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
                        if (profile.isFriend(target.getUniqueId()) && click.right()) {
                            plugin.socialManager().removeFriend(player, target.getUniqueId(), target.getName());
                        } else if (!profile.isFriend(target.getUniqueId())) {
                            plugin.socialManager().sendOrAcceptFriendRequest(player, target);
                        }
                        openPlayerInteractMenu(player, target);
                    }
                }
            
    }

    private void handleAdminClick(Player player, BosketHolder holder, MenuSpec.Click click) {
        int slot = click.slot();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

                if (!Perms.admin(player)) {
                    player.closeInventory();
                    return;
                }
                if (slot == 44) {
                    var hof = plugin.hallOfFame();
                    if (hof == null) {
                        return;
                    }
                    if (click.shift()) {
                        hof.remove();
                        Msg.send(player, Msg.success("Hall of Fame podium removed."));
                    } else if (click.right()) {
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
                        var kind = click.right()
                            ? LobbyHologramManager.StationKind.POINTS
                            : LobbyHologramManager.StationKind.ELO;
                        player.closeInventory();
                        plugin.lobbyHolograms().createStationAt(player.getLocation(), kind);
                        Msg.send(player, Msg.success("Placed 3D <#F8FAFC><bold>" + kind.name() + "</bold></#F8FAFC> Hologram Leaderboard at your location"));
                    }
                    case 40 -> {
                        if (!Perms.admin(player)) {
                            Msg.sendAlert(player, "Administrator permission required.");
                            return;
                        }
                        var kind = click.right()
                            ? LobbyHologramManager.StationKind.SHOP
                            : LobbyHologramManager.StationKind.PLAY;
                        player.closeInventory();
                        plugin.lobbyHolograms().createStationAt(player.getLocation(), kind);
                        Msg.send(player, Msg.success("Placed interactive 3D <#F8FAFC><bold>" + kind.name() + "</bold></#F8FAFC> Station at your location"));
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
                }
            
    }

    private void handleShopClick(Player player, BosketHolder holder, MenuSpec.Click click) {
        int slot = click.slot();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

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
                    if (click.right()) {
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
                if (slot == 47) {
                    openCosmetics(player);
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

    private void handleCosmeticsClick(Player player, BosketHolder holder, MenuSpec.Click click) {
        int slot = click.slot();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

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

    private void handleTagsClick(Player player, BosketHolder holder, MenuSpec.Click click) {
        int slot = click.slot();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

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
                if (slot == 47) {
                    profile.clearEquippedTags();
                    plugin.statsManager().saveProfileAsync(profile);
                    notifyLiveUiRefresh(player);
                    Msg.send(player, Msg.info("Unequipped your active chat tag."));
                    openTagsVault(player, filter, page);
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

    private void handleLeaderboardsClick(Player player, BosketHolder holder, MenuSpec.Click click) {
        int slot = click.slot();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

                if (slot == 4) {
                    openRanksGuide(player);
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

    private void handleTutorialClick(Player player, BosketHolder holder, MenuSpec.Click click) {
        int slot = click.slot();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

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
                    case 44 -> {
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

    private void handleFriendsClick(Player player, BosketHolder holder, MenuSpec.Click click) {
        int slot = click.slot();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

                if (slot == 5 || slot == 51) {
                    openPartyMenu(player);
                    return;
                }
                if (slot == 47) {
                    openAddFriendOnlineMenu(player);
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
                        if (click.shift()) {
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
                        } else if (click.right()) {
                            plugin.socialManager().removeFriend(player, friendId, other.getName());
                            openFriendsRich(player);
                        } else {
                            openPlayerInteractMenu(player, other);
                        }
                        return;
                    }
                }
                if (slot == 40) {
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
                ItemStack clicked = click.clicked();
                if (clicked != null && clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() instanceof SkullMeta sm) {
                    java.util.UUID tid = skullTargetId(sm);
                    if (tid != null) {
                        String tName = skullTargetName(sm);
                        if (plugin.socialManager().incomingFriendRequests(player.getUniqueId()).contains(tid)) {
                            if (click.right()) {
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
                                    Msg.send(player, Msg.success("Accepted friend request from <#F8FAFC>" + tName + "</#F8FAFC>."));
                                }
                            }
                            openFriendsMenu(player);
                            return;
                        }
                        if (click.right()) {
                            plugin.socialManager().removeFriend(player, tid, tName);
                            openFriendsMenu(player);
                            return;
                        }
                        Player onlineFriend = Bukkit.getPlayer(tid);
                        if (onlineFriend != null && onlineFriend.isOnline()) {
                            if (click.shift()) {
                                player.closeInventory();
                                plugin.gameManager().sendOrAcceptDuel(player, onlineFriend);
                                return;
                            }
                            plugin.socialManager().inviteToParty(player, onlineFriend);
                        } else {
                            Msg.send(player, Msg.error("<#F8FAFC>" + tName + "</#F8FAFC> is currently offline."));
                        }
                        openFriendsMenu(player);
                    }
                }
            
    }

    private void handleAddFriendClick(Player player, BosketHolder holder, MenuSpec.Click click) {
        int slot = click.slot();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

                ItemStack clicked = click.clicked();
                if (clicked != null && clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() instanceof SkullMeta sm) {
                    java.util.UUID targetId = skullTargetId(sm);
                    if (targetId != null) {
                        Player onlineTarget = Bukkit.getPlayer(targetId);
                        if (onlineTarget != null && onlineTarget.isOnline()) {
                            if (click.right()) {
                                plugin.socialManager().inviteToParty(player, onlineTarget);
                            } else {
                                plugin.socialManager().sendOrAcceptFriendRequest(player, onlineTarget);
                            }
                        }
                        openAddFriendOnlineMenu(player);
                    }
                }
            
    }

    private void handlePartyInviteClick(Player player, BosketHolder holder, MenuSpec.Click click) {
        int slot = click.slot();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

                ItemStack clicked = click.clicked();
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

    private void handlePartyClick(Player player, BosketHolder holder, MenuSpec.Click click) {
        int slot = click.slot();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

                if (slot == 3) {
                    openFriendsMenu(player);
                    return;
                }
                if (slot == 4 && plugin.socialManager().pendingPartyInviteLeader(player.getUniqueId()) != null) {
                    if (click.right()) {
                        plugin.socialManager().declinePartyInvite(player);
                    } else {
                        plugin.socialManager().acceptPartyInvite(player, (String) null);
                    }
                    openPartyMenu(player);
                    return;
                }
                if (slot == 13) {
                    if (click.right()) {
                        plugin.socialManager().setPartyTeamName(player, "reset");
                        openPartyMenu(player);
                    } else {
                        plugin.socialManager().beginChatPrompt(player, SocialPartyManager.ChatPromptType.PARTY_TEAM_NAME);
                    }
                    return;
                }
                if (slot == 31 || slot == 20 || slot == 21 || slot == 23 || slot == 24) {
                    ItemStack clicked = click.clicked();
                    if (clicked != null && clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() instanceof SkullMeta sm) {
                        java.util.UUID targetId = skullTargetId(sm);
                        if (targetId != null && !targetId.equals(player.getUniqueId())) {
                            if (click.right()) {
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
                if (slot == 40) {
                    plugin.socialManager().leaveParty(player);
                    openPartyMenu(player);
                    return;
                }
                if (slot == 49) {
                    navigateBack(player);
                }
            
    }

    private void handleMapClick(Player player, BosketHolder holder, MenuSpec.Click click) {
        int slot = click.slot();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

                if (!Perms.map(player)) {
                    player.closeInventory();
                    return;
                }
                handleMapStudioClick(player, slot, click.right(), click.shift());
            
    }

    private void handleSettingsClick(Player player, BosketHolder holder, MenuSpec.Click click) {
        int slot = click.slot();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

                if (slot == 40) {
                    navigateBack(player);
                    return;
                }
                PlayerSettings st = profile.settings();
                if (slot == 31) {
                    var next = plugin.lang().nextLanguage(player);
                    plugin.statsManager().saveProfileAsync(profile);
                    Msg.send(player, Msg.success("Language set to <#F8FAFC>"
                        + next.locale().flag() + " " + next.locale().displayName() + "</#F8FAFC>."));
                    openSettings(player);
                    return;
                }
                if (slot == 30) {
                    float current = st.uiSoundVolume();
                    float next = click.right()
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
                    ? Msg.info(entry.ballSkin().displayNameMm() + " <#94A3B8>is already equipped.</#94A3B8>")
                    : Msg.success("Equipped owned " + entry.ballSkin().displayNameMm() + "."));
            } else if (entry.dunkStyle() != null) {
                profile.setSelectedDunkStyle(entry.dunkStyle());
                Msg.send(player, alreadyEquipped
                    ? Msg.info(entry.dunkStyle().displayNameMm() + " <#94A3B8>is already equipped.</#94A3B8>")
                    : Msg.success("Equipped owned " + entry.dunkStyle().displayNameMm() + "."));
            } else if (entry.chatTag() != null) {
                profile.setSelectedTag(entry.chatTag());
                Msg.send(player, alreadyEquipped
                    ? Msg.info("<#F8FAFC>" + entry.chatTag().displayName() + "</#F8FAFC> <#94A3B8>is already equipped.</#94A3B8>")
                    : Msg.success("Equipped owned tag <!shadow><#F8FAFC>" + entry.chatTag().glyph() + "</#F8FAFC></!shadow> <#F8FAFC>" + entry.chatTag().displayName() + "</#F8FAFC>."));
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
            Msg.send(player, Msg.success("Purchased & equipped <!shadow><#F8FAFC>" + tag.glyph() + "</#F8FAFC></!shadow> <#F8FAFC>" + tag.displayName() + "</#F8FAFC> for <" + Palette.GOLD_SOFT + ">" + price + " Coins</" + Palette.GOLD_SOFT + ">!"));
        }

        plugin.statsManager().saveProfileAsync(profile);
        notifyLiveUiRefresh(player);
        Msg.send(player, Msg.info("<#94A3B8>Saved to My Locker — its Shop card stays here as</#94A3B8> <" + Palette.SUCCESS + ">Owned & Equipped</" + Palette.SUCCESS + "><#94A3B8>.</#94A3B8>"));
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
                Msg.send(player, Msg.success("Auto-built full court <#F8FAFC><bold>" + built.id() + "</bold></#F8FAFC> (Hoops + Backboards + White Lines + Scoreboard + Barriers)!"));
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
                    ? "The <#F8FAFC>MODS</#F8FAFC> tag is reserved for the staff team (<#F8FAFC>bosketball.staff</#F8FAFC>)."
                    : "This exclusive tag is not available for purchase."));
                return;
            }
            if (profile.selectedTag() == tag) {
                profile.clearEquippedTags();
                Msg.send(player, Msg.info("Unequipped <#F8FAFC>" + tag.displayName() + "</#F8FAFC>."));
            } else {
                profile.setSelectedTag(tag);
                Msg.send(player, Msg.success("Equipped exclusive tag <!shadow><#F8FAFC>" + tag.glyph() + "</#F8FAFC></!shadow> <#F8FAFC>" + tag.displayName() + "</#F8FAFC>."));
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
                Msg.send(player, Msg.error("Not enough Coins! You need <" + Palette.GOLD_SOFT + ">" + missing + " more Coins</" + Palette.GOLD_SOFT + "> to unlock <#F8FAFC>" + tag.displayName() + "</#F8FAFC>."));
                Msg.playPersonalPackSound(player, "game.buzzer", 0.55f, 1.35f);
                return;
            }
            PurchaseConfirmMenu.setCancelAction(player, () -> openTagsVault(player, backFilter, backPage));
            PurchaseConfirmMenu.open(plugin, player, new PurchaseConfirmMenu.Purchase(
                tag.displayName(),
                tag.tierLabel() + " chat tag",
                customIcon(tag.cardModelId(), "<#F8FAFC><bold>" + tag.displayName() + "</bold></#F8FAFC>",
                    java.util.List.of("<#94A3B8>Rarity:</#94A3B8> <#F8FAFC>" + tag.tierLabel() + "</#F8FAFC>",
                        "<#94A3B8>Shows next to your name in chat.</#94A3B8>")),
                price,
                "Unlocked & equipped <!shadow><#F8FAFC>" + tag.glyph() + "</#F8FAFC></!shadow> <#F8FAFC>" + tag.displayName() + "</#F8FAFC> for <" + Palette.GOLD_SOFT + ">" + price + " Coins</" + Palette.GOLD_SOFT + ">!",
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
            Msg.send(player, Msg.info("Unequipped <#F8FAFC>" + tag.displayName() + "</#F8FAFC>."));
        } else {
            profile.setSelectedTag(tag);
            Msg.send(player, Msg.success("Equipped tag <!shadow><#F8FAFC>" + tag.glyph() + "</#F8FAFC></!shadow> <#F8FAFC>" + tag.displayName() + "</#F8FAFC>."));
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
                    java.util.List.of("<#94A3B8>Your finish at the rim.</#94A3B8>")),
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

    void navigateBack(Player player) {
        UUID id = player.getUniqueId();
        NavigationStack<MenuFrame> history = backStacks.get(id);
        MenuFrame frame = history == null ? null : history.pop();
        if (frame == null) {
            currentFrames.remove(id);
            player.closeInventory();
            return;
        }
        restoringFrames.add(id);
        try {
            openFrame(player, frame);
        } finally {
            restoringFrames.remove(id);
        }
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
    private final Map<UUID, NavigationStack<MenuFrame>> backStacks = new ConcurrentHashMap<>();
    private final Map<UUID, MenuFrame> currentFrames = new ConcurrentHashMap<>();
    private final Set<UUID> restoringFrames = ConcurrentHashMap.newKeySet();

    /** Everything needed to reopen a screen with the same filter, page and target. */
    public record MenuFrame(MenuType type, LeaderboardCategory leaderboard, ShopCategory shop,
                            TagVaultFilter tag, int page, UUID target, String view) {
        static MenuFrame of(BosketHolder holder) {
            return new MenuFrame(holder.type(), holder.leaderboardCategory(), holder.shopCategory(),
                holder.tagFilter(), holder.page(), holder.targetPlayerId(), holder.view());
        }
    }

    private enum MenuError { SEASON_OFF, PLAYER_OFFLINE }

    /** The only inventory writer for declared menus. */
    void show(Player player, BosketHolder holder, MenuSpec.Builder builder) {
        var season = plugin.seasonService();
        String seasonLabel = season == null || season.state() == null
            ? plugin.settings().general().seasonName() : season.state().name();
        UUID id = player.getUniqueId();
        NavigationStack<MenuFrame> history = backStacks.computeIfAbsent(id, ignored -> new NavigationStack<>());
        MenuFrame previous = history.peek();
        String backLabel = previous == null ? "Close the lobby menu" : "Return to the previous screen";
        MenuSpec spec = builder.navigation(
            click -> navigateBack(player),
            click -> openMainHub(player),
            click -> player.closeInventory(),
            holder.type() == MenuType.SEASON ? null : click -> openSeason(player),
            backLabel, seasonLabel).build();
        var title = holder.title() == null
            ? Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_HUB, spec.title()) : holder.title();
        Inventory inventory = Bukkit.createInventory(holder, spec.size(), title);
        holder.setInventory(inventory);
        holder.setSpec(spec);
        ItemStack filler = invisibleFiller();
        for (int slot = 0; slot < spec.size(); slot++) {
            inventory.setItem(slot, filler);
        }
        spec.render(item -> renderMenuItem(item)).forEach(inventory::setItem);
        MenuFrame next = MenuFrame.of(holder);
        MenuFrame current = currentFrames.get(id);
        if (!restoringFrames.contains(id) && current != null && !current.equals(next)) {
            history.push(current);
        }
        currentFrames.put(id, next);
        player.openInventory(inventory);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
    }

    private static ItemStack renderMenuItem(MenuSpec.Item item) {
        return item.visualOr(customIcon(item.icon(), item.name(), item.lore()));
    }

    private MenuSpec.Item headRow(UUID target, MenuSpec.Item row) {
        return MenuItems.head(headIcon(target, row.name(), row.lore()), row);
    }

    void refreshSlot(Player player, BosketHolder holder, int slot, MenuSpec.Item item) {
        if (player.isOnline() && holder.spec() != null && holder.spec().items().containsKey(slot)
            && player.getOpenInventory().getTopInventory() == holder.getInventory()) {
            holder.getInventory().setItem(slot, renderMenuItem(item));
            player.updateInventory();
        }
    }

    private void openFrame(Player player, MenuFrame frame) {
        switch (frame.type()) {
            case MAIN_HUB -> openMainHub(player);
            case PLAY_MODES -> openPlayModes(player);
            case BOT_PANEL -> openBotPanel(player);
            case SPECTATE_BROWSER -> openSpectateBrowser(player, frame.page());
            case SHOP -> openShop(player, frame.shop(), frame.page());
            case COSMETICS -> openCosmetics(player, frame.page());
            case TAGS_VAULT -> openTagsVault(player, frame.tag(), frame.page());
            case LEADERBOARDS -> openLeaderboards(player, frame.leaderboard());
            case MATCH_HISTORY -> openMatchHistory(player, frame.page());
            case FRIENDS_MENU -> openFriendsMenu(player, frame.page());
            case FRIENDS_RICH -> progressionGuis().openFriends(player, frame.page());
            case ADD_FRIEND_ONLINE -> openAddFriendOnlineMenu(player, frame.page());
            case PARTY_INVITE_ONLINE -> openPartyInviteMenu(player, frame.page());
            case PARTY_MENU -> openPartyMenu(player);
            case PLAYER_INTERACT -> {
                Player target = frame.target() == null ? null : Bukkit.getPlayer(frame.target());
                if (target == null) {
                    openError(player, MenuError.PLAYER_OFFLINE);
                } else {
                    openPlayerInteractMenu(player, target);
                }
            }
            case RANKS_GUIDE -> openRanksGuide(player);
            case MAP_STUDIO -> openMapStudio(player);
            case SERVER_ADMIN -> openServerAdminPanel(player);
            case SETTINGS -> openSettings(player);
            case QUESTS -> openQuests(player);
            case ACHIEVEMENTS -> openAchievements(player, frame.view(), frame.page());
            case MAILBOX -> progressionGuis().openMailbox(player, frame.page());
            case REPORTS -> progressionGuis().openReportsPanel(player, frame.page());
            case WARNINGS -> progressionGuis().openWarningsPanel(player, frame.target(), frame.page());
            case GUILDS -> new GuildGuis(plugin).restore(player, frame.view(), frame.page());
            case SEASON -> openSeason(player);
            case TUTORIAL -> openTutorial(player);
            case INTERACTION_ERROR -> openError(player, MenuError.valueOf(frame.view()));
        }
    }

    private void openError(Player player, MenuError error) {
        BosketHolder holder = new BosketHolder(MenuType.INTERACTION_ERROR);
        holder.setView(error.name());
        MenuSpec.Builder spec = MenuSpec.builder(6, "Court update");
        String explanation = error == MenuError.SEASON_OFF
            ? "Ranked seasons are off on this server." : "That player left the server.";
        spec.item(22, MenuItems.error(explanation, "Choose a mode", click -> openPlayModes(player)));
        show(player, holder, spec);
    }

    /** Drops the per-player UI memory (shop sort/search, season snapshot, breadcrumbs) when they leave. */
    public void forgetPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        shopSortModes.remove(playerId);
        shopSearchQueries.remove(playerId);
        seasonStandingTargets.remove(playerId);
        backStacks.remove(playerId);
        currentFrames.remove(playerId);
        restoringFrames.remove(playerId);
    }

    public void openSeason(Player player) {
        openSeason(player, 0);
    }

    public void openSeason(Player player, int requestedPage) {
        var season = plugin.seasonService();
        if (season == null || season.state() == null) {
            openError(player, MenuError.SEASON_OFF);
            return;
        }
        var state = season.state();
        BosketHolder holder = new BosketHolder(MenuType.SEASON);
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_SEASON, tr(player, "gui.title.season", "RANKED SEASON")));
        MenuSpec.Builder spec = MenuSpec.builder(6, "Ranked season");
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        UUID id = player.getUniqueId();
        int required = season.placementsRequired();
        int done = Math.max(0, required - season.placementsRemaining(id));
        int best = season.bestElo(id) > 0 ? season.bestElo(id) : profile.elo();
        var tier = fr.bosketball.season.SeasonMath.tierFor(best);
        boolean provisional = season.isProvisional(id);
        var record = season.record(id);
        var pending = season.pendingReward(id);
        int tierIndex = fr.bosketball.season.SeasonMath.competitiveTierIndex(tier);

        spec.item(0, MenuSpec.Item.info("ref_trophy", state.name(), List.of(
            MenuItems.detail("Ends in", state.remainingLabel()),
            MenuItems.detail("Cycle", plugin.settings().season().durationDays() + " days"),
            MenuItems.muted("Every ranked match feeds this ladder."))));
        spec.item(2, MenuSpec.Item.info("ref_calendar_check", "Season progress", List.of(
            MenuItems.detail("Elapsed", state.progressPercent() + "%"),
            required > 0 ? MenuItems.detail("Placements", done + "/" + required) : MenuItems.muted("No placements required."),
            MenuItems.muted(provisional ? "Finish placements to lock your tier." : "Your tier is official."))));
        spec.item(4, MenuSpec.Item.info(rankIconModel(tier), "Your tier: " + tier.plainDisplayName(), List.of(
            MenuItems.detail("Season peak", best + " ELO"), MenuItems.detail("Live rating", profile.elo() + " ELO"),
            MenuItems.detail("Next tier", nextTierText(best)))));
        spec.item(6, MenuSpec.Item.info("ref_coins", "Season payout", List.of(
            MenuItems.detail("Coins", seasonProjectedCoins(tierIndex)), MenuItems.detail("XP", seasonProjectedXp(tierIndex)),
            MenuItems.detail("Trophies", season.trophies(id)), MenuItems.muted("Projected if the season ended now."))));
        if (pending == null) {
            spec.item(8, MenuItems.disabled("ref_lock", "Nothing to claim", "Play ranked matches; payouts arrive when the season closes."));
        } else {
            spec.item(8, MenuSpec.Item.action("ref_check", "Claim season rewards", List.of(
                MenuItems.detail("Coins", pending.coins()), MenuItems.detail("XP", pending.xp()), MenuItems.detail("Source", pending.label())),
                click -> {
                    season.claimPending(player);
                    openSeason(player, holder.page());
                }));
        }
        var track = List.of(fr.bosketball.model.Rank.ROOKIE, fr.bosketball.model.Rank.HOOPER,
            fr.bosketball.model.Rank.PRO, fr.bosketball.model.Rank.ALL_STAR, fr.bosketball.model.Rank.SUPERSTAR,
            fr.bosketball.model.Rank.LEGEND, fr.bosketball.model.Rank.MVP);
        for (int i = 0; i < track.size(); i++) {
            var rank = track.get(i);
            boolean current = rank == tier;
            boolean reached = best >= rank.minElo();
            String status = current ? "You are here" : reached ? "Reached this season" : "Not reached";
            var item = MenuSpec.Item.info(rankIconModel(rank), rank.plainDisplayName(), List.of(
                MenuItems.detail("ELO", rank.minElo() + " to " + fr.bosketball.season.SeasonMath.tierCeiling(rank)),
                MenuItems.detail("Coins", seasonProjectedCoins(i)), MenuItems.detail("XP", seasonProjectedXp(i)),
                MenuItems.muted(status))).selected(current);
            spec.item(19 + i, item);
        }
        int matches = record == null ? 0 : record.matches();
        int wins = record == null ? 0 : record.wins();
        spec.item(28, MenuSpec.Item.info("ref_clock", "Season record", List.of(
            MenuItems.detail("Matches", matches), MenuItems.detail("Wins", wins), MenuItems.detail("Losses", Math.max(0, matches - wins)),
            MenuItems.detail("Win rate", String.format(java.util.Locale.ROOT, "%.1f%%", record == null ? 0.0 : record.winRatePercent())))));
        spec.item(30, MenuSpec.Item.info("icon_podium", "Season standings", List.of(MenuItems.muted("Best peak rating of this cycle."))));
        spec.item(32, MenuSpec.Item.info("ref_upgrade", "Soft reset", List.of(
            MenuItems.detail("Anchor", plugin.settings().season().softResetAnchor() + " ELO"),
            MenuItems.detail("Kept", Math.round(plugin.settings().season().softResetKeepRatio() * 100) + "%"),
            MenuItems.muted("Placements reopen at the final whistle."))));

        var allStandings = season.standings(0);
        var page = MenuSpec.Page.of(allStandings, requestedPage, 9);
        holder.setPage(page.index());
        List<UUID> targets = new ArrayList<>();
        for (int i = 0; i < page.entries().size(); i++) {
            var standing = page.entries().get(i);
            targets.add(standing.uuid());
            var row = MenuItems.row("#" + standing.place() + " " + standing.name(), standing.bestElo() + " ELO",
                standing.tier().plainDisplayName() + (standing.provisional() ? " (placements)" : ""),
                click -> {
                    Player target = Bukkit.getPlayer(standing.uuid());
                    if (target == null) openError(player, MenuError.PLAYER_OFFLINE);
                    else openPlayerInteractMenu(player, target);
                });
            spec.item(36 + i, headRow(standing.uuid(), row));
        }
        seasonStandingTargets.put(id, targets);
        if (page.empty()) {
            spec.item(36, MenuItems.empty("No standings yet. Be the first on the ladder.", "Join ranked", click -> openPlayModes(player)));
        }
        spec.pagination(page, ignored -> openSeason(player, page.previous()), ignored -> openSeason(player, page.next()));
        show(player, holder, spec);
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

    private static ItemStack cachedInvisibleFiller;

    static ItemStack invisibleFiller() {
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

    public ItemStack headIcon(java.util.UUID ownerId, String nameMm, List<String> loreMm) {
        Player live = ownerId == null ? null : Bukkit.getPlayer(ownerId);
        String ownerName = live != null ? live.getName() : stripMiniTags(nameMm);
        return headIcon(ownerId, ownerName, nameMm, loreMm);
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
                ? "<#94A3B8>Next Rank:</#94A3B8> <" + Palette.GOLD + ">" + p.elo() + "/" + nextRank.minElo() + " ELO</" + Palette.GOLD + "> <#64748B>(→ " + nextRank.plainDisplayName() + ")</#64748B>"
                : "<#94A3B8>Rank Progress:</#94A3B8> <" + Palette.GOLD_SOFT + ">MAX TIER (MVP)</" + Palette.GOLD_SOFT + ">";
            meta.displayName(Msg.parse("<" + Palette.WARN + "><bold>" + player.getName() + "</bold></" + Palette.WARN + "> <!shadow><#F8FAFC>" + p.effectiveRank().pillGlyph() + "</#F8FAFC></!shadow>"));
            meta.lore(List.of(
                Msg.parse("<#94A3B8>Competitive Tier:</#94A3B8> <#F8FAFC>" + compRank.plainDisplayName() + "</#F8FAFC>"),
                Msg.parse(nextLine),
                Msg.parse("<#94A3B8>Coins:</#94A3B8> " + coinStr(p.coins())),
                Msg.parse("<#94A3B8>Wins:</#94A3B8> <" + Palette.SUCCESS + ">" + p.wins() + "</" + Palette.SUCCESS + "> <#64748B>·</#64748B> <#94A3B8>Win Rate:</#94A3B8> <#F8FAFC>" + String.format(java.util.Locale.ROOT, "%.1f%%", p.winRatePercent()) + "</#F8FAFC>"),
                Msg.parse("<#94A3B8>Points:</#94A3B8> <#F8FAFC>" + p.points() + "</#F8FAFC> <#64748B>·</#64748B> <#94A3B8>3PT:</#94A3B8> <#38BDF8>" + p.threePointers() + "</#38BDF8> <#64748B>·</#64748B> <#94A3B8>Dunks:</#94A3B8> <#F87171>" + p.dunks() + "</#F87171>"),
                Msg.parse("<#94A3B8>Assists:</#94A3B8> <" + Palette.SUCCESS + ">" + p.assists() + "</" + Palette.SUCCESS + "> <#64748B>·</#64748B> <#94A3B8>Steals:</#94A3B8> <#C084FC>" + p.steals() + "</#C084FC>"),
                Msg.parse("<#94A3B8>Playtime:</#94A3B8> <#F8FAFC>" + p.formattedPlayTime() + "</#F8FAFC>"),
                Msg.parse(""),
                Msg.parse("<#FDE047>▸ Click to view ELO Ranks & Badges Guide</#FDE047>")
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
                case 1 -> "<" + Palette.GOLD + "><bold> #1 ";
                case 2 -> "<" + Palette.TEXT + "><bold> #2 ";
                case 3 -> "<" + Palette.AMBER + "><bold> #3 ";
                default -> "<#94A3B8><bold>#" + place + " ";
            };
            String topEloGlyph = (cat == LeaderboardCategory.ELO && place >= 1 && place <= 3)
                ? " <!shadow><#F8FAFC>" + Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, place) + "</#F8FAFC></!shadow>"
                : "";
            meta.displayName(Msg.parse(medal + p.lastKnownName() + "</bold> <!shadow><#F8FAFC>" + p.effectiveRank().pillGlyph() + "</#F8FAFC></!shadow>" + topEloGlyph));
            meta.lore(List.of(
                Msg.parse("<#94A3B8>" + cat.label() + ":</#94A3B8> <" + Palette.GOLD_SOFT + "><bold>" + cat.extract(p) + "</bold></" + Palette.GOLD_SOFT + ">"),
                Msg.parse("<#94A3B8>ELO:</#94A3B8> <" + Palette.GOLD + ">" + p.elo() + "</" + Palette.GOLD + "> <#64748B>·</#64748B> <#94A3B8>Wins:</#94A3B8> <" + Palette.SUCCESS + ">" + p.wins() + "</" + Palette.SUCCESS + ">")
            ));
            skull.setItemMeta(meta);
        }
        return skull;
    }
}
