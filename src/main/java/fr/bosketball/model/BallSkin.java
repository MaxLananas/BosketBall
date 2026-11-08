package fr.bosketball.model;

import fr.bosketball.util.Palette;
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
        "<" + Palette.WARN + "><bold>Classic Pro</bold></" + Palette.WARN + ">",
        "Official pro-grain leather basketball",
        0
    ),
    STREET(
        "basketball_street",
        "<" + Palette.MUTED + "><bold>Blacktop Gold</bold></" + Palette.MUTED + ">",
        "Matte asphalt finish with gold seams",
        300
    ),
    SAKURA(
        "basketball_sakura",
        "<" + Palette.VIOLET + "><bold>Sakura Blossom</bold></" + Palette.VIOLET + ">",
        "Soft petal pink & ivory edition",
        500
    ),
    MONEY(
        "basketball_money",
        "<" + Palette.ICE + "><bold>All-Star Money Ball</bold></" + Palette.ICE + ">",
        "Tri-color 3-Point Contest edition",
        750
    ),
    GOLD(
        "basketball_gold",
        "<" + Palette.GOLD + "><bold>Royal 24K Gold</bold></" + Palette.GOLD + ">",
        "Polished championship gold finish",
        1000
    ),
    NEON(
        "basketball_neon",
        "<" + Palette.ICE + "><bold>Neon Pulse</bold></" + Palette.ICE + ">",
        "Luminous cyan & magenta seams",
        1250
    ),
    GALAXY(
        "basketball_galaxy",
        "<" + Palette.ICE + "><bold>Nebula Galaxy</bold></" + Palette.ICE + ">",
        "Deep-space cosmic swirl finish",
        1500
    ),
    FIRE(
        "basketball_fire",
        "<" + Palette.WARN + "><bold>Inferno</bold></" + Palette.WARN + ">",
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
            meta.displayName(Msg.parse("<#64748B>Shot Quiver</#64748B>").decoration(TextDecoration.ITALIC, false));
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
                // Round 52: the one item a player looks at for a whole match. It used to be four
                // colours of Title Case ("Hold & Release Right-Click", "Draw Bow Power & Shoot"),
                // which reads like a tooltip written by a form. A legend is keys in white, actions
                // in grey, one separator: you read it once and never think about it again.
                meta.lore(List.of(
                    Msg.parse("<#94A3B8>" + description + "</#94A3B8>").decoration(TextDecoration.ITALIC, false),
                    Msg.parse(""),
                    Msg.parse("<#F8FAFC>Right-click</#F8FAFC> <#64748B>·</#64748B> <#94A3B8>hold to charge, release to shoot</#94A3B8>").decoration(TextDecoration.ITALIC, false),
                    Msg.parse("<#F8FAFC>Q</#F8FAFC> <#64748B>·</#64748B> <#94A3B8>pass or alley-oop</#94A3B8>").decoration(TextDecoration.ITALIC, false),
                    Msg.parse("<#F8FAFC>Shift</#F8FAFC> <#64748B>·</#64748B> <#94A3B8>crossover</#94A3B8>").decoration(TextDecoration.ITALIC, false),
                    Msg.parse("<#F8FAFC>Sprint + left-click</#F8FAFC> <#64748B>·</#64748B> <#94A3B8>dunk near the rim</#94A3B8>").decoration(TextDecoration.ITALIC, false)
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
            "<" + Palette.ICE + "><bold>Arena Setup Wand</bold></" + Palette.ICE + "> <#94A3B8>(" + label + ")</#94A3B8>",
            List.of(
                "<#94A3B8>Select custom court & boundary corners.</#94A3B8>",
                "",
                "<#FDE047>Left-Click Block</#FDE047> <#64748B>·</#64748B> <#F8FAFC>Court Corner #1 (sets floor)</#F8FAFC>",
                "<#FDE047>Right-Click Block</#FDE047> <#64748B>·</#64748B> <#F8FAFC>Court Corner #2</#F8FAFC>",
                "<#38BDF8>Shift + Left-Click</#38BDF8> <#64748B>·</#64748B> <#F8FAFC>Boundary #1</#F8FAFC>",
                "<#38BDF8>Shift + Right-Click</#38BDF8> <#64748B>·</#64748B> <#F8FAFC>Boundary #2</#F8FAFC>"
            )
        );
    }

    public static ItemStack createSparringBotItem(boolean active) {
        return createCustomModelItem(
            Material.IRON_INGOT,
            active ? "icon_bot_on" : "icon_bot_off",
            active
                ? "<" + Palette.SUCCESS + "><bold>Defender Bot: ON</bold></" + Palette.SUCCESS + ">"
                : "<" + Palette.MUTED + "><bold>Defender Bot: OFF</bold></" + Palette.MUTED + ">",
            List.of("<#94A3B8>Right-click to toggle the practice defender.</#94A3B8>")
        );
    }

    public static ItemStack createDefensiveStanceItem() {
        return createCustomModelItem(
            Material.PRISMARINE_SHARD,
            "icon_defense",
            "<" + Palette.ICE + "><bold>Defense</bold></" + Palette.ICE + ">",
            List.of(
                "<#F8FAFC>Left-click</#F8FAFC> <#64748B>·</#64748B> <#94A3B8>steal</#94A3B8>",
                "<#F8FAFC>Jump + left-click</#F8FAFC> <#64748B>·</#64748B> <#94A3B8>block the shot</#94A3B8>"
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
            "<" + Palette.DANGER + "><bold>Leave Queue</bold></" + Palette.DANGER + "> <#94A3B8>(" + modeLabel + ")</#94A3B8>",
            List.of("<#94A3B8>Right-click to cancel matchmaking.</#94A3B8>")
        );
    }

    public static ItemStack createPlaybookItem() {
        return createCustomModelItem(
            Material.BOOK,
            "icon_star_shield",
            "<" + Palette.WARN + "><bold>Play</bold></" + Palette.WARN + ">",
            List.of("<#94A3B8>Right-click to browse game modes.</#94A3B8>")
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
        ItemStack[] icons = rawLobbyHotbarIcons();
        for (int i = 0; i < icons.length; i++) {
            icons[i] = fr.bosketball.util.TooltipStyles.decorate(
                icons[i], fr.bosketball.util.TooltipStyles.Rarity.RARE);
        }
        return icons;
    }

    private static ItemStack[] rawLobbyHotbarIcons() {
        return new ItemStack[]{
            createCustomModelItem(Material.BOOK, "ref_play",
                "<" + Palette.WARN + "><bold>ᴘʟᴀʏ</bold></" + Palette.WARN + ">",
                List.of(
                    "<#94A3B8>Ranked 1v1 to 5v5, practice court,</#94A3B8>",
                    "<#94A3B8>3-Point Contest and spectating.</#94A3B8>",
                    "",
                    "<" + Palette.SUCCESS + ">▸ Left-Click: pick a mode</" + Palette.SUCCESS + ">"
                )),
            createCustomModelItem(Material.GOLD_INGOT, "ref_shop",
                "<" + Palette.GOLD + "><bold>ꜱʜᴏᴘ</bold></" + Palette.GOLD + ">",
                List.of(
                    "<#94A3B8>Balls, dunk styles, tag emblems, bundles.</#94A3B8>",
                    "",
                    "<#FDE047>▸ Left-Click: browse the shop</#FDE047>",
                    "<#38BDF8>▸ Right-Click: your collection</#38BDF8>"
                )),
            createCustomModelItem(Material.AMETHYST_SHARD, "ref_clothing",
                "<" + Palette.ICE + "><bold>ʟᴏᴄᴋᴇʀ</bold></" + Palette.ICE + ">",
                List.of(
                    "<#94A3B8>Everything you can wear: balls, dunks,</#94A3B8>",
                    "<#94A3B8>tags and the squad stage.</#94A3B8>",
                    "",
                    "<#38BDF8>▸ Left-Click: cosmetics & locker</#38BDF8>",
                    "<#C084FC>▸ Right-Click: chat tags</#C084FC>"
                )),
            createCustomModelItem(Material.PAPER, "ref_catalogue",
                "<" + Palette.VIOLET + "><bold>ᴍᴇɴᴜ</bold></" + Palette.VIOLET + ">",
                List.of(
                    "<#94A3B8>Season, quests, mail, history, settings</#94A3B8>",
                    "<#94A3B8>and the arena studio - all in one place.</#94A3B8>",
                    "",
                    "<" + Palette.VIOLET + ">▸ Left-Click: open the hub</" + Palette.VIOLET + ">"
                )),
            createCustomModelItem(Material.FEATHER, "ref_member",
                "<" + Palette.SUCCESS + "><bold>ꜱᴏᴄɪᴀʟ</bold></" + Palette.SUCCESS + ">",
                List.of(
                    "<#94A3B8>Friends, party and guild.</#94A3B8>",
                    "",
                    "<#4ADE80>▸ Left-Click: friends list</#4ADE80>",
                    "<#FDE047>▸ Right-Click: party</#FDE047>",
                    "<#FBBF24>▸ Shift-Click: guild</#FBBF24>"
                )),
            createCustomModelItem(Material.NAME_TAG, "ref_medal_gold",
                "<" + Palette.VIOLET + "><bold>ʀᴀɴᴋꜱ</bold></" + Palette.VIOLET + ">",
                List.of(
                    "<#94A3B8>Rookie to MVP: every tier, badge</#94A3B8>",
                    "<#94A3B8>and the full control list.</#94A3B8>",
                    "",
                    "<" + Palette.VIOLET + ">▸ Left-Click: open the guide</" + Palette.VIOLET + ">"
                )),
            createCustomModelItem(Material.IRON_INGOT, "ref_cup_gold",
                "<" + Palette.GOLD_SOFT + "><bold>ᴛᴏᴘ</bold></" + Palette.GOLD_SOFT + ">",
                List.of(
                    "<#94A3B8>Top 10 per stat, updated live.</#94A3B8>",
                    "",
                    "<" + Palette.GOLD_SOFT + ">▸ Left-Click: leaderboards</" + Palette.GOLD_SOFT + ">"
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
