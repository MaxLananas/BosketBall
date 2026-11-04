package fr.bosketball.model;

import fr.bosketball.util.Msg;
import java.util.List;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Custom Basketball Skins & Server Hotbar Item Factory.
 */
public enum BallSkin {
    CLASSIC(
        "basketball_classic",
        "<#FF9436><bold>Classic Pro</bold></#FF9436>",
        "Official pro-grain leather basketball",
        0
    ),
    STREET(
        "basketball_street",
        "<#CBD5E1><bold>Blacktop Gold</bold></#CBD5E1>",
        "Matte asphalt finish with gold seams",
        300
    ),
    SAKURA(
        "basketball_sakura",
        "<#F472B6><bold>Sakura Blossom</bold></#F472B6>",
        "Soft petal pink & ivory edition",
        500
    ),
    MONEY(
        "basketball_money",
        "<#38BDF8><bold>All-Star Money Ball</bold></#38BDF8>",
        "Tri-color 3-Point Contest edition",
        750
    ),
    GOLD(
        "basketball_gold",
        "<#FACC15><bold>Royal 24K Gold</bold></#FACC15>",
        "Polished championship gold finish",
        1000
    ),
    NEON(
        "basketball_neon",
        "<#22D3EE><bold>Neon Pulse</bold></#22D3EE>",
        "Luminous cyan & magenta seams",
        1250
    ),
    GALAXY(
        "basketball_galaxy",
        "<#818CF8><bold>Nebula Galaxy</bold></#818CF8>",
        "Deep-space cosmic swirl finish",
        1500
    ),
    FIRE(
        "basketball_fire",
        "<#FF5722><bold>Inferno</bold></#FF5722>",
        "Animated ember glow finish",
        2000
    );

    public static final String NAMESPACE = "bosketball";

    private final String modelId;
    private final String displayMiniMessage;
    private final String description;
    private final int priceCoins;

    BallSkin(String modelId, String displayMiniMessage, String description, int priceCoins) {
        this.modelId = modelId;
        this.displayMiniMessage = displayMiniMessage;
        this.description = description;
        this.priceCoins = priceCoins;
    }

    public String modelId() {
        return modelId;
    }

    public String displayMiniMessage() {
        return displayMiniMessage;
    }

    public String descriptionSmallCaps() {
        return description;
    }

    public String loreLine() {
        return description;
    }

    public String plainName() {
        return name().charAt(0) + name().substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    public int defaultPriceCoins() {
        return priceCoins;
    }

    public int priceCoins() {
        return fr.bosketball.config.ShopConfig.priceOf(this, priceCoins);
    }

    public int coinPrice() {
        return priceCoins();
    }

    public int unlockElo() {
        return 0;
    }

    public String displayNameMm() {
        return displayMiniMessage;
    }

    public ItemStack createItemStack() {
        return createBallItem(false);
    }

    public NamespacedKey namespacedKey() {
        return new NamespacedKey(NAMESPACE, modelId);
    }

    public ItemStack createMannequinHeldBallItem() {
        ItemStack stack = new ItemStack(Material.SLIME_BALL);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey(NAMESPACE, modelId + "_mannequin"));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static final int HIDDEN_BOW_ARROW_SLOT = 9;
    public static final int LEAVE_QUEUE_LOBBY_SLOT = 4;

    public static ItemStack createHiddenBowArrowItem() {
        ItemStack arrow = new ItemStack(Material.ARROW, 1);
        ItemMeta meta = arrow.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey(NAMESPACE, "ui_invisible"));
            meta.displayName(Msg.parse("<dark_gray>Shot Quiver</dark_gray>").decoration(TextDecoration.ITALIC, false));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            arrow.setItemMeta(meta);
        }
        return arrow;
    }

    public ItemStack createBallItem(boolean forDisplayEntity) {
        ItemStack stack = new ItemStack(forDisplayEntity ? Material.SLIME_BALL : Material.BOW);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(namespacedKey());
            if (!forDisplayEntity) {
                meta.setUnbreakable(true);
                meta.displayName(Msg.parse(displayMiniMessage).decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(
                    Msg.parse("<gray>" + description + "</gray>").decoration(TextDecoration.ITALIC, false),
                    Msg.parse(""),
                    Msg.parse("<#4ADE80><bold>Hold & Release Right-Click</bold></#4ADE80> <dark_gray>•</dark_gray> <white>Draw Bow Power & Shoot</white>").decoration(TextDecoration.ITALIC, false),
                    Msg.parse("<#38BDF8>Drop Key (Q)</#38BDF8> <dark_gray>•</dark_gray> <gray>Quick Pass / Alley-Oop Lob</gray>").decoration(TextDecoration.ITALIC, false),
                    Msg.parse("<#C084FC>Sneak (Shift)</#C084FC> <dark_gray>•</dark_gray> <gray>Crossover Step</gray>").decoration(TextDecoration.ITALIC, false),
                    Msg.parse("<#FBBF24>Sprint + Left-Click Near Hoop</#FBBF24> <dark_gray>•</dark_gray> <gray>Slam Dunk</gray>").decoration(TextDecoration.ITALIC, false)
                ));
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack createArchitectWandItem(String arenaId) {
        String label = (arenaId == null || arenaId.isBlank()) ? "New Arena" : arenaId;
        return createCustomModelItem(
            Material.BLAZE_ROD,
            "court_wand",
            "<#38BDF8><bold>Arena Setup Wand</bold></#38BDF8> <gray>(" + label + ")</gray>",
            List.of(
                "<gray>Select custom court & boundary corners.</gray>",
                "",
                "<yellow>Left-Click Block</yellow> <dark_gray>•</dark_gray> <white>Court Corner #1 (sets floor)</white>",
                "<yellow>Right-Click Block</yellow> <dark_gray>•</dark_gray> <white>Court Corner #2</white>",
                "<aqua>Shift + Left-Click</aqua> <dark_gray>•</dark_gray> <white>Boundary #1</white>",
                "<aqua>Shift + Right-Click</aqua> <dark_gray>•</dark_gray> <white>Boundary #2</white>"
            )
        );
    }

    public static ItemStack createSparringBotItem(boolean active) {
        return createCustomModelItem(
            Material.IRON_INGOT,
            active ? "icon_bot_on" : "icon_bot_off",
            active
                ? "<#4ADE80><bold>Defender Bot: ON</bold></#4ADE80>"
                : "<#94A3B8><bold>Defender Bot: OFF</bold></#94A3B8>",
            List.of("<gray>Right-click to toggle the practice defender.</gray>")
        );
    }

    public static ItemStack createDefensiveStanceItem() {
        return createCustomModelItem(
            Material.PRISMARINE_SHARD,
            "icon_defense",
            "<#38BDF8><bold>Defense</bold></#38BDF8>",
            List.of(
                "<gray>Left-click handler to steal.</gray>",
                "<gray>Jump + Left-click in air to block.</gray>"
            )
        );
    }

    public static ItemStack createLeaveQueueItem() {
        return createLeaveQueueItem("Queue");
    }

    public static ItemStack createLeaveQueueItem(String modeLabel) {
        return createCustomModelItem(
            Material.RED_DYE,
            "icon_leave",
            "<#F87171><bold>Leave Queue</bold></#F87171> <gray>(" + modeLabel + ")</gray>",
            List.of("<gray>Right-click to cancel matchmaking.</gray>")
        );
    }

    public static ItemStack createPlaybookItem() {
        return createCustomModelItem(
            Material.BOOK,
            "icon_star_shield",
            "<#FF8C1A><bold>Play</bold></#FF8C1A>",
            List.of("<gray>Right-click to browse game modes.</gray>")
        );
    }

    public static void equipLobbyHotbarIfEmpty(org.bukkit.entity.Player player, boolean forceOverwrite) {
        for (int emptySlot : new int[]{4, 5}) {
            ItemStack cur = player.getInventory().getItem(emptySlot);
            if (cur != null && cur.getItemMeta() != null && cur.getItemMeta().getItemModel() != null
                && NAMESPACE.equals(cur.getItemMeta().getItemModel().getNamespace())) {
                // Keep Leave Queue item in slot 4 if currently queued
                if (emptySlot == LEAVE_QUEUE_LOBBY_SLOT && "icon_leave".equals(cur.getItemMeta().getItemModel().getKey())) {
                    continue;
                }
                player.getInventory().setItem(emptySlot, null);
            }
        }
        setIfSlotFreeOrBosketIcon(player, 0, forceOverwrite, createCustomModelItem(
            Material.BOOK, "prop_play_emblem",
            "<#FF8C1A><bold>⚡ ᴘʟᴀʏ & ᴍᴏᴅᴇꜱ</bold></#FF8C1A>",
            List.of(
                "<gray>Queue Ranked 1v1–5v5, Practice or 3PT!</gray>",
                "",
                "<#4ADE80>▸ Click to Play</#4ADE80>"
            )
        ));
        setIfSlotFreeOrBosketIcon(player, 1, forceOverwrite, createCustomModelItem(
            Material.AMETHYST_SHARD, "prop_locker_jersey",
            "<#38BDF8><bold>👕 3ᴅ ʟᴏᴄᴋᴇʀ ʀᴏᴏᴍ</bold></#38BDF8>",
            List.of(
                "<gray>Full-body Squad Showcase & Loadout Studio</gray>",
                "",
                "<#38BDF8>▸ Click to Enter</#38BDF8>"
            )
        ));
        setIfSlotFreeOrBosketIcon(player, 2, forceOverwrite, createCustomModelItem(
            Material.GOLD_INGOT, "prop_shop_crate",
            "<#FBBF24><bold>🛒 ꜱʜᴏᴘ</bold></#FBBF24>",
            List.of(
                "<gray>Unlock 3D Basketballs, Dunks & Collector Tags</gray>",
                "",
                "<#FDE047>▸ Click to Browse</#FDE047>"
            )
        ));
        setIfSlotFreeOrBosketIcon(player, 3, forceOverwrite, createCustomModelItem(
            Material.NAME_TAG, "prop_tag_plaque",
            "<#E879F9><bold>🏷 ᴍʏ ʟᴏᴄᴋᴇʀ & ᴛᴀɢꜱ</bold></#E879F9>",
            List.of(
                "<yellow>Left-Click:</yellow> <white>Equip Basketballs & Dunk Styles</white>",
                "<aqua>Right-Click:</aqua> <white>Equip Chat Tags</white>"
            )
        ));
        setIfSlotFreeOrBosketIcon(player, 6, forceOverwrite, createCustomModelItem(
            Material.FEATHER, "prop_social_megaphone",
            "<#4ADE80><bold>👥 ꜰʀɪᴇɴᴅꜱ & ᴘᴀʀᴛʏ</bold></#4ADE80>",
            List.of(
                "<green>Left-Click:</green> <white>Friends List & Online Players</white>",
                "<yellow>Right-Click:</yellow> <white>Party & Custom Team Name</white>"
            )
        ));
        setIfSlotFreeOrBosketIcon(player, 7, forceOverwrite, createCustomModelItem(
            Material.BOOK, "prop_guide_clipboard",
            "<#CBD5E1><bold>📖 ᴍᴇɴᴜ & ʀᴀɴᴋꜱ ɢᴜɪᴅᴇ</bold></#CBD5E1>",
            List.of(
                "<yellow>Left-Click:</yellow> <white>All-in-One Hub & Settings</white>",
                "<aqua>Right-Click:</aqua> <white>ELO Ranks (Rookie → MVP) & Controls</white>"
            )
        ));
        setIfSlotFreeOrBosketIcon(player, 8, forceOverwrite, createCustomModelItem(
            Material.IRON_INGOT, "prop_podium_cup",
            "<#60A5FA><bold>🏆 ʟᴇᴀᴅᴇʀʙᴏᴀʀᴅꜱ</bold></#60A5FA>",
            List.of(
                "<gray>View Top #1 / #2 / #3 ELO & Server Rankings</gray>",
                "",
                "<#60A5FA>▸ Click to View</#60A5FA>"
            )
        ));
    }

    private static void setIfSlotFreeOrBosketIcon(org.bukkit.entity.Player player, int slot, boolean force, ItemStack icon) {
        ItemStack existing = player.getInventory().getItem(slot);
        if (force || existing == null || existing.getType().isAir()) {
            player.getInventory().setItem(slot, icon);
            return;
        }
        ItemMeta em = existing.getItemMeta();
        if (em != null && em.getItemModel() != null && NAMESPACE.equals(em.getItemModel().getNamespace())) {
            player.getInventory().setItem(slot, icon);
        }
    }

    public static ItemStack createCustomModelItem(Material base, String modelId, String nameMm, List<String> loreMm) {
        ItemStack stack = new ItemStack(base);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey(NAMESPACE, modelId));
            if (nameMm != null) {
                meta.displayName(Msg.parse(nameMm).decoration(TextDecoration.ITALIC, false));
            }
            if (loreMm != null && !loreMm.isEmpty()) {
                meta.lore(loreMm.stream()
                    .map(line -> Msg.parse(line).decoration(TextDecoration.ITALIC, false))
                    .toList());
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_DYE, ItemFlag.HIDE_UNBREAKABLE);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static BallSkin byNameOrDefault(String raw, BallSkin fallback) {
        if (raw == null) {
            return fallback;
        }
        for (BallSkin skin : values()) {
            if (skin.name().equalsIgnoreCase(raw.trim())) {
                return skin;
            }
        }
        return fallback;
    }
}
