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
 * 3D PBR Sphere Basketball Skins & Custom 3D Prop Item Factory for Paper 26.2.
 * Uses ItemMeta#setItemModel(NamespacedKey) introduced in modern Paper / 26.2 so zero vanilla items are overwritten!
 */
public enum BallSkin {
    CLASSIC(
        "basketball_classic",
        "<gradient:#FF8A28:#FFB347><bold>🏀 ʙᴀʟʟᴏɴ ᴘʀᴏ ᴄʟᴀꜱꜱɪǫᴜᴇ</bold></gradient>",
        "ᴄᴜɪʀ ɢʀᴀɪɴᴇ ᴏꜰꜰɪᴄɪᴇʟ ʙᴏꜱᴋᴇᴛʙᴀʟʟ 26.2",
        0
    ),
    STREET(
        "basketball_street",
        "<gradient:#94A3B8:#FBBF24><bold>🏀 ꜱᴛʀᴇᴇᴛ ᴀꜱᴘʜᴀʟᴛ ᴏʀ</bold></gradient>",
        "ᴄᴜɪʀ ɴᴏɪʀ ʙɪᴛᴜᴍᴇ ᴀ ꜱɪʟʟᴏɴꜱ ᴅᴏʀᴇꜱ",
        0
    ),
    SAKURA(
        "basketball_sakura",
        "<gradient:#F472B6:#FBCFE8><bold>🏀 ꜱᴀᴋᴜʀᴀ ʙʟᴏꜱꜱᴏᴍ</bold></gradient>",
        "ᴇᴅɪᴛɪᴏɴ ꜰʟᴇᴜʀ ᴅᴇ ᴄᴇʀɪꜱɪᴇʀ & ɪᴠᴏɪʀᴇ",
        1050
    ),
    MONEY(
        "basketball_money",
        "<gradient:#3DB8FF:#FFFFFF:#FF4D4D><bold>🏀 ᴍᴏɴᴇʏ ʙᴀʟʟ ᴀʟʟ-ꜱᴛᴀʀ</bold></gradient>",
        "ᴇᴅɪᴛɪᴏɴ ᴛʀɪᴄᴏʟᴏʀᴇ ᴄᴏɴᴄᴏᴜʀꜱ ᴀ 3 ᴘᴏɪɴᴛꜱ",
        1150
    ),
    GOLD(
        "basketball_gold",
        "<gradient:#FACC15:#F59E0B><bold>🏀 ʀᴏʏᴀʟ 24ᴋ ɢᴏʟᴅ</bold></gradient>",
        "ʟɪɴɢᴏᴛ ᴅ'ᴏʀ ᴄɪꜱᴇʟᴇ ᴘᴏᴜʀ ᴄʜᴀᴍᴘɪᴏɴꜱ",
        1300
    ),
    NEON(
        "basketball_neon",
        "<gradient:#00F0FF:#FF2BD6><bold>🏀 ᴄʏʙᴇʀ ɴᴇᴏɴ ᴘᴜʟꜱᴇ</bold></gradient>",
        "ꜱɪʟʟᴏɴꜱ ʟᴜᴍɪɴᴇꜱᴄᴇɴᴛꜱ ᴄʏᴀɴ & ᴍᴀɢᴇɴᴛᴀ",
        1450
    ),
    GALAXY(
        "basketball_galaxy",
        "<gradient:#818CF8:#38BDF8><bold>🏀 ɴᴇʙᴜʟᴀ ɢᴀʟᴀxʏ</bold></gradient>",
        "ᴛᴏᴜʀʙɪʟʟᴏɴ ᴄᴏꜱᴍɪǫᴜᴇ & ᴄᴏᴜᴛᴜʀᴇꜱ ᴀꜱᴛʀᴀʟᴇꜱ",
        1650
    ),
    FIRE(
        "basketball_fire",
        "<gradient:#FF3B00:#FFD43B><bold>🔥 ɴʙᴀ ᴊᴀᴍ ᴏɴ-ꜰɪʀᴇ</bold></gradient>",
        "ᴛᴇxᴛᴜʀᴇ ᴍᴀɢᴍᴀ ᴀɴɪᴍᴇᴇ • ꜱᴇʀɪᴇ ᴅᴇ 3 ᴘᴀɴɪᴇʀꜱ",
        1800
    );

    public static final String NAMESPACE = "bosketball";

    private final String modelId;
    private final String displayMiniMessage;
    private final String descriptionSmallCaps;
    private final int unlockElo;

    BallSkin(String modelId, String displayMiniMessage, String descriptionSmallCaps, int unlockElo) {
        this.modelId = modelId;
        this.displayMiniMessage = displayMiniMessage;
        this.descriptionSmallCaps = descriptionSmallCaps;
        this.unlockElo = unlockElo;
    }

    public String modelId() {
        return modelId;
    }

    public String displayMiniMessage() {
        return displayMiniMessage;
    }

    public String descriptionSmallCaps() {
        return descriptionSmallCaps;
    }

    public int unlockElo() {
        return unlockElo;
    }

    public String displayNameMm() {
        return displayMiniMessage;
    }

    public Rank requiredRank() {
        return Rank.fromElo(unlockElo);
    }

    public ItemStack createItemStack() {
        return createBallItem(false);
    }

    public NamespacedKey namespacedKey() {
        return new NamespacedKey(NAMESPACE, modelId);
    }

    public ItemStack createBallItem(boolean forDisplayEntity) {
        ItemStack stack = new ItemStack(Material.SLIME_BALL);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(namespacedKey());
            if (!forDisplayEntity) {
                meta.displayName(Msg.parse(displayMiniMessage).decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(
                    Msg.parse("<gray>" + descriptionSmallCaps).decoration(TextDecoration.ITALIC, false),
                    Msg.parse(""),
                    Msg.parse("<yellow>▸ ᴍᴀɪɴᴛᴇɴɪʀ ᴄʟɪᴄ-ᴅʀᴏɪᴛ</yellow> <dark_gray>•</dark_gray> <white>ᴊᴀᴜɢᴇ ᴅᴇ ᴛɪʀ</white>").decoration(TextDecoration.ITALIC, false),
                    Msg.parse("<aqua>▸ ᴛᴏᴜᴄʜᴇ ᴊᴇᴛᴇʀ (Q)</aqua> <dark_gray>•</dark_gray> <white>ᴘᴀꜱꜱᴇ / ᴀʟʟᴇʏ-ᴏᴏᴘ</white>").decoration(TextDecoration.ITALIC, false),
                    Msg.parse("<light_purple>▸ ᴀᴄᴄʀᴏᴜᴘɪʀ (ꜱʜɪꜰᴛ)</light_purple> <dark_gray>•</dark_gray> <white>ᴄʀᴏꜱꜱᴏᴠᴇʀ ᴀɴᴋʟᴇ-ʙʀᴇᴀᴋᴇʀ</white>").decoration(TextDecoration.ITALIC, false),
                    Msg.parse("<gold>▸ ꜱᴘʀɪɴᴛ + ꜱᴀᴜᴛ ʀᴀǫᴜᴇᴛᴛᴇ</gold> <dark_gray>•</dark_gray> <white>ꜱʟᴀᴍ ᴅᴜɴᴋ 3ᴅ</white>").decoration(TextDecoration.ITALIC, false)
                ));
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }
            stack.setItemMeta(meta);
        }
        if (!forDisplayEntity) {
            // Enables 1-tick exact hold-and-release Jump-Shot Meter via PlayerStopUsingItemEvent with 0 eating sound,
            // using TRIDENT overhead arm-raise so the character lifts their shooting arm high like a real jump shooter!
            stack.setData(
                DataComponentTypes.CONSUMABLE,
                Consumable.consumable()
                    .consumeSeconds(5.0f)
                    .animation(ItemUseAnimation.TRIDENT)
                    .sound(Key.key(NAMESPACE, "empty"))
                    .hasConsumeParticles(false)
                    .build()
            );
        }
        return stack;
    }

    public static ItemStack createArchitectWandItem(String arenaId) {
        String label = (arenaId == null || arenaId.isBlank()) ? "ɴᴏᴜᴠᴇʟʟᴇ ᴍᴀᴘ" : arenaId;
        return createCustomModelItem(
            Material.BLAZE_ROD,
            "court_wand",
            "<gradient:#00F0FF:#FFD43B><bold>📐 ʙᴀɢᴜᴇᴛᴛᴇ ᴀʀᴄʜɪᴛᴇᴄᴛᴇ (" + label + ")</bold></gradient>",
            List.of(
                "<gray>ᴏᴜᴛɪʟ ᴅᴇ ᴄʀᴇᴀᴛɪᴏɴ ᴅᴇ ᴍᴀᴘ ʙᴀꜱᴋᴇᴛ ꜱᴜʀ-ᴍᴇꜱᴜʀᴇ</gray>",
                "",
                "<yellow>▸ ᴄʟɪᴄ-ɢᴀᴜᴄʜᴇ ʙʟᴏᴄ</yellow> <dark_gray>•</dark_gray> <white>ᴄᴏɪɴ ᴛᴇʀʀᴀɪɴ #1 (ᴘᴏꜱ1 + ꜱᴏʟ)</white>",
                "<yellow>▸ ᴄʟɪᴄ-ᴅʀᴏɪᴛ ʙʟᴏᴄ</yellow> <dark_gray>•</dark_gray> <white>ᴄᴏɪɴ ᴛᴇʀʀᴀɪɴ #2 (ᴘᴏꜱ2)</white>",
                "<aqua>▸ ꜱʜɪꜰᴛ + ᴄʟɪᴄ-ɢᴀᴜᴄʜᴇ</aqua> <dark_gray>•</dark_gray> <white>ʙᴀʀʀɪᴇʀᴇ ᴍᴀᴘ #1 (ᴀɴᴛɪ-ꜱᴏʀᴛɪᴇ)</white>",
                "<aqua>▸ ꜱʜɪꜰᴛ + ᴄʟɪᴄ-ᴅʀᴏɪᴛ</aqua> <dark_gray>•</dark_gray> <white>ʙᴀʀʀɪᴇʀᴇ ᴍᴀᴘ #2 (ᴀɴᴛɪ-ꜱᴏʀᴛɪᴇ)</white>",
                "<green>▸ /ʙʙ ᴍᴀᴘ ʜᴏᴏᴘ <ɪɢɴɪꜱ|ꜰʀᴏꜱᴛ></green> <dark_gray>•</dark_gray> <white>ᴘᴏꜱᴇʀ ʟᴇꜱ ᴘᴀɴɪᴇʀꜱ</white>",
                "<gold>▸ /ʙʙ ᴍᴀᴘ ꜱᴇᴛꜱᴘᴀᴡɴ <ɪɢɴɪꜱ|ꜰʀᴏꜱᴛ></gold> <dark_gray>•</dark_gray> <white>ꜱᴘᴀᴡɴꜱ ᴇǫᴜɪᴘᴇꜱ</white>"
            )
        );
    }

    public static ItemStack createSparringBotItem(boolean active) {
        return createCustomModelItem(
            Material.IRON_INGOT,
            active ? "icon_bot_on" : "icon_bot_off",
            active
                ? "<gradient:#3DB8FF:#4CFF88><bold>🤖 ᴅᴇꜰᴇɴꜱᴇᴜʀ ɪᴀ : ᴀᴄᴛɪꜰ</bold></gradient>"
                : "<gradient:#9AA8BC:#FFFFFF><bold>🤖 ᴅᴇꜰᴇɴꜱᴇᴜʀ ɪᴀ : ᴅᴇꜱᴀᴄᴛɪᴠᴇ</bold></gradient>",
            List.of(
                "<gray>ᴄʟɪᴄ-ᴅʀᴏɪᴛ (ᴏᴜ /ʙʙ ʙᴏᴛ) ᴘᴏᴜʀ ᴀᴄᴛɪᴠᴇʀ/ʀᴇᴛɪʀᴇʀ</gray>",
                "<aqua>▸ ᴛᴇꜱᴛᴇᴢ ᴠᴏꜱ ᴄʀᴏꜱꜱᴏᴠᴇʀꜱ, ᴛɪʀꜱ ᴄᴏɴᴛᴇꜱᴛᴇꜱ & ᴘᴏꜱᴛᴇʀ ᴅᴜɴᴋꜱ !</aqua>"
            )
        );
    }

    public static ItemStack createDefensiveStanceItem() {
        return createCustomModelItem(
            Material.PRISMARINE_SHARD,
            "icon_defense",
            "<aqua><bold>🛡 ᴅᴇꜰᴇɴꜱᴇ : ᴄᴏɴᴛʀᴇ & ɪɴᴛᴇʀᴄᴇᴘᴛɪᴏɴ</bold></aqua>",
            List.of(
                "<gray>ᴀᴘᴘʀᴏᴄʜᴇᴢ ʟᴇ ʙᴀʟʟᴏɴ ʟɪʙʀᴇ ᴘᴏᴜʀ ʟᴇ ʀᴀᴍᴀꜱꜱᴇʀ !</gray>",
                "",
                "<green>▸ ᴄʟɪᴄ-ɢᴀᴜᴄʜᴇ ꜱᴜʀ ʟᴇ ᴘᴏʀᴛᴇᴜʀ</green> <dark_gray>•</dark_gray> <white>ɪɴᴛᴇʀᴄᴇᴘᴛɪᴏɴ</white>",
                "<aqua>▸ ꜱᴀᴜᴛ + ᴄʟɪᴄ-ɢᴀᴜᴄʜᴇ ᴇɴ ʟ'ᴀɪʀ</aqua> <dark_gray>•</dark_gray> <white>ᴄᴏɴᴛʀᴇ (ʙʟᴏᴄᴋ)</white>"
            )
        );
    }

    public static ItemStack createPlaybookItem() {
        return createCustomModelItem(
            Material.BOOK,
            "icon_star_shield",
            "<gradient:#FF7A18:#FFD43B><bold>✦ ᴍᴇɴᴜ ʙᴏꜱᴋᴇᴛʙᴀʟʟ 26.2 ✦</bold></gradient>",
            List.of(
                "<gray>ᴄʟɪᴄ-ᴅʀᴏɪᴛ ᴘᴏᴜʀ ᴏᴜᴠʀɪʀ ʟᴇ ᴄᴇɴᴛʀᴇ ʙᴏꜱᴋᴇᴛʙᴀʟʟ</gray>",
                "<yellow>▸ ᴍᴀᴛᴄʜꜱ • ᴠᴇꜱᴛɪᴀɪʀᴇ 3ᴅ • ᴛᴀɢꜱ • ᴀᴍɪꜱ • ʜɪꜱᴛᴏʀɪǫᴜᴇ</yellow>"
            )
        );
    }

    public static void equipLobbyHotbarIfEmpty(org.bukkit.entity.Player player, boolean forceOverwrite) {
        // Symmetrical 5-slot lobby hotbar matching 203500.png (Slots 0, 1, _, _, 4, _, _, 7, 8)!
        for (int emptySlot : new int[]{2, 3, 5, 6}) {
            ItemStack cur = player.getInventory().getItem(emptySlot);
            if (cur != null && cur.getItemMeta() != null && cur.getItemMeta().getItemModel() != null
                && NAMESPACE.equals(cur.getItemMeta().getItemModel().getNamespace())) {
                player.getInventory().setItem(emptySlot, null);
            }
        }
        setIfSlotFreeOrBosketIcon(player, 0, forceOverwrite, createCustomModelItem(
            Material.AMETHYST_SHARD, "icon_portal",
            "<gradient:#B86BFF:#6CEEFF><bold>🌀 ꜱᴄᴇɴᴇ 3ᴅ & ꜰɪʟᴇ ᴅ'ᴀᴛᴛᴇɴᴛᴇ</bold></gradient>",
            List.of("<gray>ᴍᴇɴᴜ ᴄᴀᴍᴇʀᴀ 3ᴅ & ꜱᴀʟʟᴇ ᴅ'ᴀᴛᴛᴇɴᴛᴇ (/ʙʙ 3ᴅ)</gray>")
        ));
        setIfSlotFreeOrBosketIcon(player, 1, forceOverwrite, createCustomModelItem(
            Material.BOOK, "icon_star_shield",
            "<gradient:#FF7A18:#FFD43B><bold>✦ ᴍᴇɴᴜ ʙᴏꜱᴋᴇᴛʙᴀʟʟ 26.2 ✦</bold></gradient>",
            List.of(
                "<gray>ᴄʟɪᴄ-ᴅʀᴏɪᴛ ᴘᴏᴜʀ ᴏᴜᴠʀɪʀ ʟᴇ ᴄᴇɴᴛʀᴇ ʙᴏꜱᴋᴇᴛʙᴀʟʟ</gray>",
                "<yellow>▸ ᴍᴀᴛᴄʜꜱ • ᴠᴇꜱᴛɪᴀɪʀᴇ 3ᴅ • [F] ᴛᴀʙ ᴄᴇɴᴛʀᴀʟ ᴄᴜꜱᴛᴏᴍ</yellow>"
            )
        ));
        setIfSlotFreeOrBosketIcon(player, 4, forceOverwrite, createCustomModelItem(
            Material.GOLD_INGOT, "icon_chest",
            "<gradient:#FFD43B:#FF8A28><bold>🎁 ᴄᴏꜱᴍᴇᴛɪǫᴜᴇꜱ & 54 ᴛᴀɢꜱ</bold></gradient>",
            List.of("<gray>ʙᴀʟʟᴏɴꜱ ᴄᴀʀʀᴇꜱ 3ᴅ • ᴅᴜɴᴋꜱ • 72 ʙᴀᴅɢᴇꜱ ɢʟᴏꜱꜱʏ</gray>")
        ));
        setIfSlotFreeOrBosketIcon(player, 7, forceOverwrite, createCustomModelItem(
            Material.BOOK, "icon_book",
            "<gradient:#F8C678:#FFE9B8><bold>📖 ᴄᴀʀʀɪᴇʀᴇ, ᴀᴍɪꜱ & ʜɪꜱᴛᴏʀɪǫᴜᴇ</bold></gradient>",
            List.of("<gray>ᴠᴏꜱ ᴅᴇʀɴɪᴇʀꜱ ᴍᴀᴛᴄʜꜱ, ᴀᴍɪꜱ, ꜱᴛᴀᴛꜱ & ᴘᴀʀᴀᴍᴇᴛʀᴇꜱ</gray>")
        ));
        setIfSlotFreeOrBosketIcon(player, 8, forceOverwrite, createCustomModelItem(
            Material.IRON_INGOT, "icon_podium",
            "<gradient:#E6F0FF:#7CC8FF><bold>🏆 ᴄʟᴀꜱꜱᴇᴍᴇɴᴛꜱ & ᴘᴏᴅɪᴜᴍ ᴇʟᴏ</bold></gradient>",
            List.of("<gray>ᴛᴏᴘ 10 ᴇʟᴏ • ʙᴀᴅɢᴇꜱ #1/#2/#3 • ɢʀᴏᴜᴘᴇ</gray>")
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
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_DYE);
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
