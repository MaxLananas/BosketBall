package fr.bosketball.ui.kit;

import fr.bosketball.util.Msg;
import fr.bosketball.util.Palette;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One vocabulary for buttons, list rows, empty states and insufficient funds. */
public final class MenuItems {
    public static final String DISABLED_ICON = "ref_lock";
    public static final String EMPTY_ICON = "ref_compass";
    public static final String SEPARATOR = Palette.of(Palette.DIM, "·");

    private MenuItems() { }

    public static String button(String verbLabel) {
        return Palette.of(Palette.GOLD, "<bold>" + Msg.escapeMini(verbLabel) + "</bold>");
    }

    public static String label(String nounLabel) {
        return Palette.of(Palette.TEXT, "<bold>" + Msg.escapeMini(nounLabel) + "</bold>");
    }

    public static String muted(String text) {
        return Palette.of(Palette.MUTED, Msg.escapeMini(text));
    }

    public static String row(String label, Object value) {
        return muted(label + ":") + " " + Palette.of(Palette.TEXT, Msg.escapeMini(String.valueOf(value)));
    }

    public static String detail(String label, Object value) {
        return row(label, value);
    }

    /** Shared shape for people, guilds, invitations and results. The icon must stay visible:
     *  {@code ui_invisible} scales its GUI model to zero, so a row needs a real sprite. */
    public static MenuSpec.Item row(String title, String subtitle, String detail, MenuSpec.Action action) {
        return row("ref_member", title, subtitle, detail, action);
    }

    public static MenuSpec.Item row(String icon, String title, String subtitle, String detail, MenuSpec.Action action) {
        return action(icon, "View " + title, lore(subtitle, detail), action);
    }

    public static MenuSpec.Item head(org.bukkit.inventory.ItemStack head, MenuSpec.Item row) {
        return MenuSpec.Item.visual(head, row.name(), row.lore(), row.action());
    }

    public static List<String> lore(String... lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            result.add(line == null || line.isBlank() ? "" : muted(line));
        }
        return List.copyOf(result);
    }

    public static MenuSpec.Item action(String icon, String verbLabel, List<String> lore, MenuSpec.Action action) {
        return MenuSpec.Item.action(icon, button(verbLabel), lore, action);
    }

    public static MenuSpec.Item info(String icon, String nounLabel, List<String> lore) {
        return MenuSpec.Item.info(icon, label(nounLabel), lore);
    }

    public static MenuSpec.Item empty(String title, String explanation, String verbLabel, MenuSpec.Action action) {
        Objects.requireNonNull(action, "empty-state action");
        return action(EMPTY_ICON, verbLabel, lore(title, explanation, "Click to " + lowerFirst(verbLabel)), action);
    }

    public static MenuSpec.Item empty(String explanation, String verbLabel, MenuSpec.Action action) {
        return empty("Nothing here yet.", explanation, verbLabel, action);
    }

    public static MenuSpec.Item error(String explanation, String verbLabel, MenuSpec.Action action) {
        return action("ref_cross", verbLabel, lore(explanation, "Click to " + lowerFirst(verbLabel)), action);
    }

    public static List<String> price(int price, int balance) {
        return List.of(row("Price", Msg.count(price) + " coins"), row("Balance", Msg.count(balance) + " coins"));
    }

    public static String shortfall(int price, int balance) {
        long missing = Math.max(0L, (long) price - balance);
        return "Need " + Msg.count(missing) + " more coins";
    }

    public static MenuSpec.Item unaffordable(String nounLabel, int price, int balance, String nextStep) {
        if (balance >= price) {
            throw new IllegalArgumentException("An affordable item is not an insufficient state");
        }
        List<String> lines = new ArrayList<>(price(price, balance));
        lines.add(Palette.of(Palette.WARN, shortfall(price, balance)));
        lines.add(muted(nextStep));
        return MenuSpec.Item.disabled(DISABLED_ICON, muted(nounLabel + " · locked"), lines);
    }

    public static MenuSpec.Item disabled(String icon, String nounLabel, String reason, String nextStep) {
        return MenuSpec.Item.disabled(icon, muted(nounLabel + " · unavailable"),
            lore(reason, nextStep));
    }

    public static MenuSpec.Item disabled(String icon, String nounLabel, String reason) {
        return disabled(icon, nounLabel, reason, "Use Back or Close below.");
    }

    public static MenuSpec.Item tab(String icon, String verbLabel, int count, boolean active, MenuSpec.Action action) {
        List<String> lines = lore("Items: " + Msg.count(count), active ? "Selected" : "Click to " + lowerFirst(verbLabel));
        MenuSpec.Item item = action(icon, verbLabel + " (" + Msg.count(count) + ")", lines, action);
        return active ? item.selected() : item;
    }

    /** Reverses the old decorative alphabet before validating migrated item names. */
    public static String plain(String text) {
        String small = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            int index = small.indexOf(text.charAt(i));
            out.append(index < 0 ? text.charAt(i) : (char) ('a' + index));
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    private static final List<String> VERBS = List.of(
        "Open ", "View ", "Join ", "Leave ", "Start ", "Stop ", "Equip ", "Unequip ", "Buy ",
        "Claim ", "Clear ", "Show ", "Go ", "Close ", "Add ", "Invite ", "Accept ", "Decline ",
        "Remove ", "Toggle ", "Set ", "Get ", "Build ", "Place ", "Save ", "Push ", "Reload ",
        "Replay ", "Resume ", "Pause ", "Mark ", "Delete ", "Resolve ", "Dismiss ", "Play ",
        "Challenge ", "Promote ", "Demote ", "Create ", "Rename ", "Edit ", "Browse ", "Find ",
        "Choose ", "Raise ", "Lower ", "Reset ", "Inspect ", "Review ", "Switch ", "Send ",
        "Type ", "Reroll ", "Confirm ", "Cancel ", "Spectate ", "Read ");

    /** Migrates a noun button to an explicit verb without changing its subject. */
    public static String actionLabel(String raw) {
        String text = plain(raw)
            .replaceAll("[\\u25C2\\u25B8\\u25B9\\u2039\\u203A\\u00AB\\u00BB\\u25B6\\u25C0\\u25CF\\u25CB\\u2714\\u2716\\u2328\\u25AA]", "")
            .replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return "View details";
        }
        for (String verb : VERBS) {
            if (text.regionMatches(true, 0, verb, 0, verb.length())) {
                return text;
            }
            if (text.equalsIgnoreCase(verb.trim())) {
                return verb.trim();
            }
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("previous page")) {
            return "Show previous page";
        }
        if (lower.contains("next page")) {
            return "Show next page";
        }
        if (lower.contains("maintenance") || lower.contains("global chat") || lower.contains("builder mode")) {
            return "Toggle " + lower.split(":")[0].trim();
        }
        if (lower.contains("wallet") || lower.contains("balance")) {
            return "Change shop sort";
        }
        if (lower.contains("shop") || lower.contains("locker") || lower.contains("vault")
            || lower.contains("party") || lower.contains("friends") || lower.contains("mailbox")
            || lower.contains("guide") || lower.contains("studio") || lower.contains("panel")
            || lower.contains("social") || lower.contains("settings") || lower.contains("discord")) {
            return "Open " + lower;
        }
        if (lower.contains("toggle") || lower.contains("enabled") || lower.contains("disabled")) {
            return "Toggle " + lower;
        }
        return "View " + lower;
    }

    private static String lowerFirst(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toLowerCase(java.util.Locale.ROOT) + text.substring(1);
    }
}
