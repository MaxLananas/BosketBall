package fr.bosketball.ui.kit;

import java.util.List;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class GuiChrome {

    public static final int SIZE = 54;
    public static final int HEADER_START = 0;

    public static final int SLOT_PREV = 45;
    public static final int SLOT_HOME = 46;
    public static final int SLOT_BACK = 49;
    public static final int SLOT_SEASON = 52;
    public static final int SLOT_NEXT = 53;
    public static final int SLOT_CLOSE = 53;

    public static final String ACCENT = "#FF8C1A";
    public static final String INK = "#F8FAFC";
    public static final String MUTED = "#94A3B8";

    private GuiChrome() {
    }

    @FunctionalInterface
    public interface IconFactory {
        ItemStack icon(String modelName, String nameMm, List<String> loreMm);
    }

    public static ItemStack homeIcon(IconFactory icons, String hint) {
        return icons.icon("icon_home",
            "<" + ACCENT + "><bold>LOBBY</bold></" + ACCENT + ">",
            List.of(
                "<gray>" + (hint == null ? "Return to the main hub" : hint) + "</gray>",
                "",
                "<dark_gray>The four doors are always one click away.</dark_gray>"
            ));
    }

    public static ItemStack backIcon(IconFactory icons, String label) {
        return icons.icon("icon_back",
            "<" + ACCENT + "><bold>◂ BACK</bold></" + ACCENT + ">",
            List.of(
                "<gray>" + (label == null ? "Return to the previous screen" : label) + "</gray>",
                "",
                "<dark_gray>ESC closes the menu.</dark_gray>"
            ));
    }

    public static ItemStack closeIcon(IconFactory icons) {
        return icons.icon("icon_leave",
            "<#F87171><bold>CLOSE</bold></#F87171>",
            List.of("<gray>Close this menu</gray>"));
    }

    public static ItemStack seasonIcon(IconFactory icons, String seasonName, String status, String remaining) {
        return icons.icon("icon_pass",
            "<#FBBF24><bold>" + (seasonName == null ? "RANKED SEASON" : seasonName) + "</bold></#FBBF24>",
            List.of(
                "<gray>" + (status == null || status.isBlank() ? "Your seasonal tier & rewards" : status) + "</gray>",
                "<gray>Ends in:</gray> <white>" + (remaining == null ? "—" : remaining) + "</white>",
                "",
                "<yellow>▸ Open the Season hub</yellow>"
            ));
    }

    public static void applyRail(
        Inventory inventory,
        IconFactory icons,
        String backLabel,
        boolean includeBack,
        boolean includeHome,
        boolean includeSeason,
        String seasonName,
        String seasonStatus,
        String seasonRemaining
    ) {
        if (inventory == null || icons == null) {
            return;
        }
        int size = inventory.getSize();
        if (includeBack && size > SLOT_BACK) {
            inventory.setItem(SLOT_BACK, backIcon(icons, backLabel));
        }
        if (includeHome && size > SLOT_HOME) {
            inventory.setItem(SLOT_HOME, homeIcon(icons, null));
        }
        if (includeSeason && size > SLOT_SEASON) {
            inventory.setItem(SLOT_SEASON, seasonIcon(icons, seasonName, seasonStatus, seasonRemaining));
        }
    }
}
