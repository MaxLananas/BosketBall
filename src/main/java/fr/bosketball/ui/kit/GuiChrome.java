package fr.bosketball.ui.kit;

import fr.bosketball.util.Palette;
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

    public static final String ACCENT = Palette.WARN;
    public static final String INK = Palette.TEXT;
    public static final String MUTED = Palette.MUTED;

    private GuiChrome() {
    }

    @FunctionalInterface
    public interface IconFactory {
        ItemStack icon(String modelName, String nameMm, List<String> loreMm);
    }

    public static ItemStack homeIcon(IconFactory icons, String hint) {
        return icons.icon("ref_home",
            "<" + ACCENT + "><bold>LOBBY</bold></" + ACCENT + ">",
            List.of(
                "<#94A3B8>" + (hint == null ? "Return to the main hub" : hint) + "</#94A3B8>",
                "",
                "<#64748B>The four doors are always one click away.</#64748B>"
            ));
    }

    public static ItemStack backIcon(IconFactory icons, String label) {
        return icons.icon("ref_arrow_left",
            "<" + ACCENT + "><bold>◂ BACK</bold></" + ACCENT + ">",
            List.of(
                "<#94A3B8>" + (label == null ? "Return to the previous screen" : label) + "</#94A3B8>",
                "",
                "<#64748B>ESC closes the menu.</#64748B>"
            ));
    }

    public static ItemStack closeIcon(IconFactory icons) {
        return icons.icon("ref_cross",
            "<" + Palette.DANGER + "><bold>CLOSE</bold></" + Palette.DANGER + ">",
            List.of("<#94A3B8>Close this menu</#94A3B8>"));
    }

    public static ItemStack seasonIcon(IconFactory icons, String seasonName, String status, String remaining) {
        return icons.icon("ref_trophy",
            "<" + Palette.GOLD + "><bold>" + (seasonName == null ? "RANKED SEASON" : seasonName) + "</bold></" + Palette.GOLD + ">",
            List.of(
                "<#94A3B8>" + (status == null || status.isBlank() ? "Your seasonal tier & rewards" : status) + "</#94A3B8>",
                "<#94A3B8>Ends in:</#94A3B8> <#F8FAFC>" + (remaining == null ? "—" : remaining) + "</#F8FAFC>",
                "",
                "<#FDE047>▸ Open the Season hub</#FDE047>"
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
