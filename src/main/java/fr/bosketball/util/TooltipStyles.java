package fr.bosketball.util;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The "Ultimate Tooltips" art the server ships (see docs/tooltips.md).
 *
 * <p>Minecraft 1.21.2 replaced the hard-coded tooltip with GUI sprites, and added the
 * {@code minecraft:tooltip_style} item component: an item whose style is {@code <ns>:<id>} is drawn
 * with {@code assets/<ns>/textures/gui/sprites/tooltip/<id>_background.png} and {@code ..._frame.png}.
 * The pack ships six of those styles (common is the default, plus uncommon, rare, epic, legendary and
 * artifact - the last two are animated through their {@code .mcmeta}), and seven rarity ribbons
 * rendered as bitmap font glyphs so a tooltip can show its tier as art instead of words.
 */
public final class TooltipStyles {

    /** Style ids shipped by the pack, mapped to the ribbon glyph and the tier colour. */
    public enum Rarity {
        COMMON("common", '\uE360', null, Palette.MUTED),
        UNCOMMON("uncommon", '\uE361', "uncommon", Palette.SUCCESS),
        RARE("rare", '\uE362', "rare", Palette.ICE),
        EPIC("epic", '\uE363', "epic", Palette.VIOLET),
        LEGENDARY("legendary", '\uE364', "legendary", Palette.GOLD),
        ARTIFACT("artifact", '\uE365', "artifact", Palette.DANGER),
        EXCLUSIVE("exclusive", '\uE366', "artifact", Palette.MUTED);

        private final String id;
        private final char ribbon;
        private final String styleId;
        private final String colour;

        Rarity(String id, char ribbon, String styleId, String colour) {
            this.id = id;
            this.ribbon = ribbon;
            this.styleId = styleId;
            this.colour = colour;
        }

        /** Sprite id, i.e. the {@code <id>} of {@code tooltip/<id>_background.png}. */
        public String id() {
            return id;
        }

        /** Private-use character bound to the ribbon bitmap in the pack font. */
        public char ribbon() {
            return ribbon;
        }

        /** Tooltip style to set on the item, or {@code null} when the default style already fits. */
        public String styleId() {
            return styleId;
        }

        public String colour() {
            return colour;
        }

        public NamespacedKey styleKey() {
            return styleId == null ? null : new NamespacedKey("minecraft", styleId);
        }
    }

    private TooltipStyles() {
    }

    /** Coin thresholds used everywhere the server sells something (tags, balls, dunks). */
    public static Rarity forCoins(int coins) {
        if (coins >= 3500) {
            return Rarity.LEGENDARY;
        }
        if (coins >= 2000) {
            return Rarity.EPIC;
        }
        if (coins >= 1000) {
            return Rarity.RARE;
        }
        if (coins >= 500) {
            return Rarity.UNCOMMON;
        }
        return Rarity.COMMON;
    }

    /** Competitive tiers get the loud styles; staff tiers get the exclusive ribbon. */
    public static Rarity forRank(fr.bosketball.model.Rank rank) {
        if (rank == null) {
            return Rarity.COMMON;
        }
        return switch (rank) {
            case ROOKIE -> Rarity.COMMON;
            case HOOPER -> Rarity.UNCOMMON;
            case PRO -> Rarity.RARE;
            case ALL_STAR -> Rarity.RARE;
            case SUPERSTAR -> Rarity.EPIC;
            case LEGEND -> Rarity.LEGENDARY;
            case MVP -> Rarity.ARTIFACT;
            case REFEREE, ADMIN -> Rarity.EXCLUSIVE;
        };
    }

    /**
     * Applies the style and puts the rarity ribbon on top of the lore. Returns the same stack so it
     * can be used inline: {@code return TooltipStyles.decorate(card, rarity);}
     */
    public static ItemStack decorate(ItemStack stack, Rarity rarity) {
        if (stack == null || rarity == null) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        NamespacedKey styleKey = rarity.styleKey();
        if (styleKey != null) {
            try {
                meta.setTooltipStyle(styleKey);
            } catch (Throwable ignored) {
                // older clients/API levels simply keep the default style
            }
        }
        List<Component> current = meta.lore();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(String.valueOf(rarity.ribbon()), NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        if (current != null) {
            lore.addAll(current);
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    /** The ribbon glyph alone, for lore lines that are composed by hand. */
    public static String ribbon(Rarity rarity) {
        return rarity == null ? "" : String.valueOf(rarity.ribbon());
    }
}
