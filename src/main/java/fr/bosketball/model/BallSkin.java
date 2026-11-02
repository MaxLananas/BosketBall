package fr.bosketball.model;

import fr.bosketball.util.Msg;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import java.util.List;
import net.kyori.adventure.key.Key;
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
        return name().charAt(0) + name().substring(1).toLowerCase();
    }

    public int priceCoins() {
        return priceCoins;
    }

    public int coinPrice() {
        return priceCoins;
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

    public ItemStack createBallItem(boolean forDisplayEntity) {
        ItemStack stack = new ItemStack(Material.SLIME_BALL);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(namespacedKey());
            if (!forDisplayEntity) {
                meta.displayName(Msg.parse(displayMiniMessage).decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(
                    Msg.parse("<gray>" + description + "</gray>").decoration(TextDecoration.ITALIC, false),
                    Msg.parse(""),
                    Msg.parse("<yellow>Hold Right-Click</yellow> <dark_gray>•</dark_gray> <gray>Jump Shot</gray>").decoration(TextDecoration.ITALIC, false),
                    Msg.parse("<aqua>Drop Key (Q)</aqua> <dark_gray>•</dark_gray> <gray>Pass / Lob</gray>").decoration(TextDecoration.ITALIC, false),
                    Msg.parse("<light_purple>Sneak (Shift)</light_purple> <dark_gray>•</dark_gray> <gray>Crossover</gray>").decoration(TextDecoration.ITALIC, false),
                    Msg.parse("<gold>Sprint + Drive</gold> <dark_gray>•</dark_gray> <gray>Slam Dunk</gray>").decoration(TextDecoration.ITALIC, false)
                ));
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }
            stack.setItemMeta(meta);
        }
        if (!forDisplayEntity) {
            stack.setData(
                DataComponentTypes.CONSUMABLE,
                Consumable.consumable()
                    .consumeSeconds(5.0f)
                    .animation(ItemUseAnimation.BOW)
                    .sound(Key.key(NAMESPACE, "empty"))
                    .hasConsumeParticles(false)
                    .build()
            );
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
        for (int emptySlot : new int[]{2, 3, 5, 6}) {
            ItemStack cur = player.getInventory().getItem(emptySlot);
            if (cur != null && cur.getItemMeta() != null && cur.getItemMeta().getItemModel() != null
                && NAMESPACE.equals(cur.getItemMeta().getItemModel().getNamespace())) {
                player.getInventory().setItem(emptySlot, null);
            }
        }
        setIfSlotFreeOrBosketIcon(player, 0, forceOverwrite, createCustomModelItem(
            Material.BOOK, "icon_star_shield",
            "<#FF8C1A><bold>Play</bold></#FF8C1A>",
            List.of("<gray>Join Ranked 1v1–5v5, Practice or 3-Point Contest</gray>")
        ));
        setIfSlotFreeOrBosketIcon(player, 1, forceOverwrite, createCustomModelItem(
            Material.AMETHYST_SHARD, "icon_portal",
            "<#38BDF8><bold>Locker Room</bold></#38BDF8>",
            List.of("<gray>Preview your player, basketballs & dunk styles</gray>")
        ));
        setIfSlotFreeOrBosketIcon(player, 4, forceOverwrite, createCustomModelItem(
            Material.GOLD_INGOT, "icon_chest",
            "<#FBBF24><bold>Shop & Cosmetics</bold></#FBBF24>",
            List.of("<gray>Unlock Tags, Basketballs & Dunk Styles with Coins</gray>")
        ));
        setIfSlotFreeOrBosketIcon(player, 7, forceOverwrite, createCustomModelItem(
            Material.BOOK, "icon_book",
            "<#CBD5E1><bold>Profile & Party</bold></#CBD5E1>",
            List.of("<gray>View your stats, match history, party & settings</gray>")
        ));
        setIfSlotFreeOrBosketIcon(player, 8, forceOverwrite, createCustomModelItem(
            Material.IRON_INGOT, "icon_podium",
            "<#60A5FA><bold>Leaderboards</bold></#60A5FA>",
            List.of("<gray>View top ranked players on the server</gray>")
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
