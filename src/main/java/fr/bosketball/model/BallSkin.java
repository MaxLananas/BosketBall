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
    MONEY(
        "basketball_money",
        "<gradient:#3DB8FF:#FFFFFF:#FF4D4D><bold>🏀 ᴍᴏɴᴇʏ ʙᴀʟʟ ᴀʟʟ-ꜱᴛᴀʀ</bold></gradient>",
        "ᴇᴅɪᴛɪᴏɴ ᴛʀɪᴄᴏʟᴏʀᴇ ᴄᴏɴᴄᴏᴜʀꜱ ᴀ 3 ᴘᴏɪɴᴛꜱ",
        1150
    ),
    NEON(
        "basketball_neon",
        "<gradient:#00F0FF:#FF2BD6><bold>🏀 ᴄʏʙᴇʀ ɴᴇᴏɴ ᴘᴜʟꜱᴇ</bold></gradient>",
        "ꜱɪʟʟᴏɴꜱ ʟᴜᴍɪɴᴇꜱᴄᴇɴᴛꜱ ᴄʏᴀɴ & ᴍᴀɢᴇɴᴛᴀ",
        1450
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
        return stack;
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
