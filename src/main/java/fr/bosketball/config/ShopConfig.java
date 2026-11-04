package fr.bosketball.config;

import fr.bosketball.model.BallSkin;
import fr.bosketball.model.ChatTag;
import fr.bosketball.model.DunkStyle;
import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Human-readable {@code shop.yml} manager allowing server admins to customize every
 * Chat Tag, Basketball Skin, and Dunk Style Coin price and reload live via {@code /bb reload}.
 */
public final class ShopConfig {

    private static final String HEADER = """
        # ===========================================================================
        #   BOSKETBALL — SHOP & COSMETICS PRICE CONFIGURATION (shop.yml)
        #   Set any item's Coin price to 0 to make it unlocked by default for everyone.
        #   Reload live in-game after editing with: /bb reload
        # ===========================================================================
        """;

    private static volatile Map<ChatTag, Integer> tagPriceOverrides = Map.of();
    private static volatile Map<BallSkin, Integer> ballPriceOverrides = Map.of();
    private static volatile Map<DunkStyle, Integer> dunkPriceOverrides = Map.of();

    private ShopConfig() {
    }

    public static int priceOf(ChatTag tag, int defaultCoins) {
        if (defaultCoins < 0 || (tag != null && tag.isSpecialUnpurchasable())) {
            return -1;
        }
        Integer custom = tagPriceOverrides.get(tag);
        return custom != null ? custom : defaultCoins;
    }

    public static int priceOf(BallSkin skin, int defaultCoins) {
        Integer custom = ballPriceOverrides.get(skin);
        return custom != null ? custom : defaultCoins;
    }

    public static int priceOf(DunkStyle dunk, int defaultCoins) {
        Integer custom = dunkPriceOverrides.get(dunk);
        return custom != null ? custom : defaultCoins;
    }

    public static void resetOverrides() {
        tagPriceOverrides = Map.of();
        ballPriceOverrides = Map.of();
        dunkPriceOverrides = Map.of();
    }

    public static void loadOrCreate(File dataFolder, Logger logger) {
        if (dataFolder == null) {
            return;
        }
        File file = new File(dataFolder, "shop.yml");
        boolean dirty = !file.exists();
        YamlConfiguration yml = AtomicYamlWriter.loadSafe(file, logger);

        EnumMap<BallSkin, Integer> balls = new EnumMap<>(BallSkin.class);
        for (BallSkin skin : BallSkin.values()) {
            String key = "basketball-skins." + skin.name().toLowerCase(java.util.Locale.ROOT);
            if (!yml.contains(key)) {
                yml.set(key, skin.defaultPriceCoins());
                dirty = true;
            }
            balls.put(skin, Math.max(0, yml.getInt(key, skin.defaultPriceCoins())));
        }

        EnumMap<DunkStyle, Integer> dunks = new EnumMap<>(DunkStyle.class);
        for (DunkStyle style : DunkStyle.values()) {
            String key = "dunk-styles." + style.name().toLowerCase(java.util.Locale.ROOT);
            if (!yml.contains(key)) {
                yml.set(key, style.defaultPriceCoins());
                dirty = true;
            }
            dunks.put(style, Math.max(0, yml.getInt(key, style.defaultPriceCoins())));
        }

        EnumMap<ChatTag, Integer> tags = new EnumMap<>(ChatTag.class);
        for (ChatTag tag : ChatTag.values()) {
            if (tag.isSpecialUnpurchasable()) {
                continue;
            }
            String tierGroup = tierFolderName(tag.defaultPriceCoins());
            String key = "chat-tags." + tierGroup + "." + tag.id();
            // Also check flat chat-tags.<id> if an admin placed it directly under chat-tags
            String flatKey = "chat-tags." + tag.id();
            int resolved;
            if (yml.contains(flatKey) && !yml.isConfigurationSection(flatKey)) {
                resolved = Math.max(0, yml.getInt(flatKey, tag.defaultPriceCoins()));
            } else {
                if (!yml.contains(key)) {
                    yml.set(key, tag.defaultPriceCoins());
                    dirty = true;
                }
                resolved = Math.max(0, yml.getInt(key, tag.defaultPriceCoins()));
            }
            tags.put(tag, resolved);
        }

        ballPriceOverrides = Map.copyOf(balls);
        dunkPriceOverrides = Map.copyOf(dunks);
        tagPriceOverrides = Map.copyOf(tags);

        if (dirty) {
            AtomicYamlWriter.writeYamlAtomically(file, HEADER, yml, logger);
        }
    }

    private static String tierFolderName(int defaultPrice) {
        if (defaultPrice >= 2000) {
            return "legendary";
        }
        if (defaultPrice >= 1000) {
            return "epic";
        }
        if (defaultPrice >= 500) {
            return "rare";
        }
        return "common";
    }
}
