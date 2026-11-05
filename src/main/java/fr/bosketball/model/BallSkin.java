package fr.bosketball.model;

import fr.bosketball.util.Msg;
import java.util.List;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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
    public static final int LEAVE_QUEUE_LOBBY_SLOT = 8;

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
            "ref_exit",
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

    /**
     * The lobby hotbar is a fixed seven-door toolbar: one glossy badge per menu, centred on
     * slots 1..7 so the row reads as a single balanced block, with the two edge slots kept
     * free (slot 8 hosts the "leave queue" badge while matchmaking).
     */
    public static final int[] LOBBY_HOTBAR_SLOTS = {1, 2, 3, 4, 5, 6, 7};

    /** Item-model ids of the seven badges, in slot order. Mirrored by tools/hotbar_icons.py. */
    public static final String[] LOBBY_HOTBAR_MODELS = {
        "ref_play", "ref_shop", "ref_clothing", "ref_catalogue", "ref_member", "ref_medal_gold", "ref_cup_gold"
    };

    public static void equipLobbyHotbarIfEmpty(org.bukkit.entity.Player player, boolean forceOverwrite) {
        for (int slot : new int[]{0, 8}) {
            ItemStack cur = player.getInventory().getItem(slot);
            if (cur != null && cur.getItemMeta() != null && cur.getItemMeta().getItemModel() != null
                && NAMESPACE.equals(cur.getItemMeta().getItemModel().getNamespace())) {
                if (slot == LEAVE_QUEUE_LOBBY_SLOT && "ref_exit".equals(cur.getItemMeta().getItemModel().getKey())) {
                    continue;
                }
                player.getInventory().setItem(slot, null);
            }
        }
        ItemStack[] icons = lobbyHotbarIcons();
        for (int i = 0; i < LOBBY_HOTBAR_SLOTS.length; i++) {
            setIfSlotFreeOrBosketIcon(player, LOBBY_HOTBAR_SLOTS[i], forceOverwrite, icons[i]);
        }
    }

    /** Builds the seven badge items once, in slot order (same order as {@link #LOBBY_HOTBAR_SLOTS}). */
    public static ItemStack[] lobbyHotbarIcons() {
        return new ItemStack[]{
            createCustomModelItem(Material.BOOK, "ref_play",
                "<#FF8C1A><bold>ᴘʟᴀʏ</bold></#FF8C1A>",
                List.of(
                    "<gray>Ranked 1v1 to 5v5, practice court,</gray>",
                    "<gray>three-point contest and spectating.</gray>",
                    "",
                    "<#4ADE80>▸ Left-Click: pick a mode</#4ADE80>"
                )),
            createCustomModelItem(Material.GOLD_INGOT, "ref_shop",
                "<#FBBF24><bold>ꜱʜᴏᴘ</bold></#FBBF24>",
                List.of(
                    "<gray>Balls, dunk styles, tag emblems, bundles.</gray>",
                    "",
                    "<yellow>▸ Left-Click: browse the shop</yellow>",
                    "<aqua>▸ Right-Click: your collection</aqua>"
                )),
            createCustomModelItem(Material.AMETHYST_SHARD, "ref_clothing",
                "<#38BDF8><bold>ʟᴏᴄᴋᴇʀ</bold></#38BDF8>",
                List.of(
                    "<gray>Everything you can wear: balls, dunks,</gray>",
                    "<gray>tags and the squad stage.</gray>",
                    "",
                    "<aqua>▸ Left-Click: cosmetics & locker</aqua>",
                    "<light_purple>▸ Right-Click: chat tags</light_purple>"
                )),
            createCustomModelItem(Material.PAPER, "ref_catalogue",
                "<#C084FC><bold>ᴍᴇɴᴜ</bold></#C084FC>",
                List.of(
                    "<gray>Season, quests, mail, history, settings</gray>",
                    "<gray>and the arena studio - all in one place.</gray>",
                    "",
                    "<#C084FC>▸ Left-Click: open the hub</#C084FC>"
                )),
            createCustomModelItem(Material.FEATHER, "ref_member",
                "<#34D399><bold>ꜱᴏᴄɪᴀʟ</bold></#34D399>",
                List.of(
                    "<gray>Friends, party and guild.</gray>",
                    "",
                    "<green>▸ Left-Click: friends list</green>",
                    "<yellow>▸ Right-Click: party</yellow>",
                    "<gold>▸ Shift-Click: guild</gold>"
                )),
            createCustomModelItem(Material.NAME_TAG, "ref_medal_gold",
                "<#F472B6><bold>ʀᴀɴᴋꜱ</bold></#F472B6>",
                List.of(
                    "<gray>Rookie to MVP: every tier, badge</gray>",
                    "<gray>and the full control list.</gray>",
                    "",
                    "<#F9A8D4>▸ Left-Click: open the guide</#F9A8D4>"
                )),
            createCustomModelItem(Material.IRON_INGOT, "ref_cup_gold",
                "<#FDE047><bold>ᴛᴏᴘ</bold></#FDE047>",
                List.of(
                    "<gray>Top 10 per stat, updated live.</gray>",
                    "",
                    "<#FDE047>▸ Left-Click: leaderboards</#FDE047>"
                ))
        };
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
