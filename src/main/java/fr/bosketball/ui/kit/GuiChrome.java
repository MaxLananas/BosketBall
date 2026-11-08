package fr.bosketball.ui.kit;

import fr.bosketball.util.Palette;
import java.util.List;
import java.util.Set;
import org.bukkit.inventory.ItemStack;

/** Fixed hit areas and item factories for the six-row menu rail. */
public final class GuiChrome {
    public static final int SIZE = MenuSpec.MAX_SLOTS;
    public static final int HEADER_START = 0;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_HOME = 46;
    public static final int SLOT_CLOSE = 48;
    public static final int SLOT_BACK = 49;
    public static final int SLOT_SEASON = 52;
    public static final int SLOT_NEXT = 53;
    public static final String ACCENT = Palette.GOLD;
    public static final String INK = Palette.TEXT;
    public static final String MUTED = Palette.MUTED;
    private static final Set<Integer> NAVIGATION = Set.of(
        SLOT_PREV, SLOT_HOME, SLOT_CLOSE, SLOT_BACK, SLOT_SEASON, SLOT_NEXT);

    private GuiChrome() { }

    public static boolean isNavigationSlot(int slot) {
        return NAVIGATION.contains(slot);
    }

    @FunctionalInterface
    public interface IconFactory {
        ItemStack icon(String modelName, String nameMm, List<String> loreMm);
    }

    public static ItemStack homeIcon(IconFactory icons, String hint) {
        return icons.icon("ref_home", MenuItems.button("Open lobby"), List.of(
            Palette.of(Palette.MUTED, hint == null ? "Return to the lobby" : hint)));
    }

    public static ItemStack backIcon(IconFactory icons, String label) {
        return icons.icon("ref_arrow_left", MenuItems.button("Go back"), List.of(
            Palette.of(Palette.MUTED, label == null ? "Return to the previous screen" : label)));
    }

    public static ItemStack closeIcon(IconFactory icons) {
        return icons.icon("ref_cross", MenuItems.button("Close menu"),
            List.of(Palette.of(Palette.MUTED, "Return to the court")));
    }

    public static ItemStack seasonIcon(IconFactory icons, String seasonName, String status, String remaining) {
        return icons.icon("ref_trophy", MenuItems.button("Open season"), List.of(
            MenuItems.row("Season", seasonName == null ? "Ranked season" : seasonName),
            MenuItems.row("Status", status == null || status.isBlank() ? "Your tier and rewards" : status),
            MenuItems.row("Ends in", remaining == null ? "Not scheduled" : remaining)));
    }
}
