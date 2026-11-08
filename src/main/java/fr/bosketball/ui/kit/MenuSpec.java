package fr.bosketball.ui.kit;

import fr.bosketball.util.Palette;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Immutable chest layout. Builders declare slots; only the renderer places items. */
public final class MenuSpec {
    public static final int COLUMNS = 9;
    public static final int MAX_ROWS = 6;
    public static final int MAX_SLOTS = COLUMNS * MAX_ROWS;
    public static final int[] GRID = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    @FunctionalInterface
    public interface Action {
        void click(Click click);
    }

    /** The callback, not the displayed item or client NBT, is the authority for an action. */
    public record Click(Player player, int slot, boolean right, boolean shift, ItemStack clicked) {
        public Click {
            Objects.requireNonNull(player, "player");
            if (slot < 0 || slot >= MAX_SLOTS) {
                throw new IllegalArgumentException("Slot outside chest: " + slot);
            }
            clicked = clicked == null ? null : clicked.clone();
        }
    }

    public record Item(String icon, String name, List<String> lore, ItemStack visual,
                       boolean glow, boolean disabled, Action action) {
        public Item {
            Objects.requireNonNull(icon, "icon");
            name = validateName(name);
            lore = List.copyOf(lore);
            visual = visual == null ? null : visual.clone();
            if (disabled && action != null) {
                throw new IllegalArgumentException("A disabled item cannot carry an action: " + name);
            }
            if (disabled && lore.stream().noneMatch(line -> !line.isBlank())) {
                throw new IllegalArgumentException("A disabled item must explain why: " + name);
            }
        }

        public static Item info(String icon, String name, List<String> lore) {
            return new Item(icon, name, lore, null, false, false, null);
        }

        public static Item action(String icon, String name, List<String> lore, Action action) {
            return new Item(icon, name, lore, null, false, false, Objects.requireNonNull(action, "action"));
        }

        public static Item disabled(String icon, String name, List<String> lore) {
            return new Item(icon, name, lore, null, false, true, null);
        }

        public static Item visual(ItemStack stack, Action action) {
            Objects.requireNonNull(stack, "stack");
            var meta = Objects.requireNonNull(stack.getItemMeta(), "item meta");
            var name = meta.displayName();
            var lore = meta.lore();
            return new Item("ui_invisible",
                name == null ? "" : MenuItems.actionLabel(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name)),
                lore == null ? List.of() : lore.stream()
                    .map(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()::serialize).toList(),
                stack, false, false, Objects.requireNonNull(action, "action"));
        }

        public static Item infoVisual(ItemStack stack) {
            Objects.requireNonNull(stack, "stack");
            var meta = Objects.requireNonNull(stack.getItemMeta(), "item meta");
            var name = meta.displayName();
            var lore = meta.lore();
            return new Item("ui_invisible",
                name == null ? "" : MenuItems.plain(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name)),
                lore == null ? List.of() : lore.stream()
                    .map(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()::serialize).toList(),
                stack, false, false, null);
        }

        public static Item visual(ItemStack stack, String name, List<String> lore, Action action) {
            return new Item("visual", name, lore, stack, false, false, action);
        }

        public Item selected() {
            return selected(true);
        }

        public Item selected(boolean value) {
            return value ? new Item(icon, name, lore, visual, true, disabled, action) : this;
        }

        public ItemStack visualOr(ItemStack fallback) {
            ItemStack result = (visual == null ? fallback : visual).clone();
            var meta = result.getItemMeta();
            if (meta != null) {
                boolean migrated = visual != null && !"visual".equals(icon);
                meta.displayName(fr.bosketball.util.Msg.parse(migrated
                    ? (action == null ? MenuItems.label(name) : MenuItems.button(name)) : name));
                if ("visual".equals(icon) || visual == null) {
                    meta.lore(lore.stream().map(fr.bosketball.util.Msg::parse).toList());
                }
                meta.removeItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                if (glow) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                } else {
                    for (var enchantment : List.copyOf(meta.getEnchants().keySet())) {
                        meta.removeEnchant(enchantment);
                    }
                }
                result.setItemMeta(meta);
            }
            return result;
        }
    }

    public record Page<T>(List<T> all, List<T> entries, int index, int count, int capacity) {
        public Page {
            if (capacity <= 0 || capacity > MAX_SLOTS) {
                throw new IllegalArgumentException("Invalid page capacity: " + capacity);
            }
            all = List.copyOf(all);
            int pages = Math.max(1, (all.size() + capacity - 1) / capacity);
            index = Math.clamp(index, 0, pages - 1);
            int start = index * capacity;
            entries = List.copyOf(all.subList(start, Math.min(all.size(), start + capacity)));
            count = pages;
        }

        public static <T> Page<T> of(List<T> all, int requested, int capacity) {
            return new Page<>(all, List.of(), requested, 0, capacity);
        }

        public boolean empty() {
            return all.isEmpty();
        }

        public int start() {
            return index * capacity;
        }

        public int previous() {
            return Math.max(0, index - 1);
        }

        public int next() {
            return Math.min(count - 1, index + 1);
        }
    }

    private final int rows;
    private final String title;
    private final Map<Integer, Item> items;

    private MenuSpec(Builder builder) {
        rows = builder.rows;
        title = validateName(builder.title);
        items = Collections.unmodifiableMap(new LinkedHashMap<>(builder.items));
        if (!items.containsKey(GuiChrome.SLOT_BACK) || !items.containsKey(GuiChrome.SLOT_CLOSE)) {
            throw new IllegalArgumentException("Every menu needs back and close controls");
        }
        if (items.values().stream().noneMatch(item -> item.action() != null
            && !GuiChrome.isNavigationSlot(items.keySet().stream().filter(slot -> items.get(slot) == item).findFirst().orElse(-1)))) {
            throw new IllegalArgumentException("Every menu needs a forward action");
        }
    }

    public int rows() { return rows; }
    public int size() { return rows * COLUMNS; }
    public String title() { return title; }
    public Map<Integer, Item> items() { return items; }

    /** Rendering is independent of Bukkit so the complete slot contract can be tested off-server. */
    public <T> Map<Integer, T> render(Function<Item, T> factory) {
        Map<Integer, T> result = new LinkedHashMap<>();
        items.forEach((slot, item) -> result.put(slot, factory.apply(item)));
        return Collections.unmodifiableMap(result);
    }

    public static Builder builder(int rows, String title) {
        return new Builder(rows, title);
    }

    public static String validateName(String raw) {
        Objects.requireNonNull(raw, "name");
        String clean = raw.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
        if (clean.isEmpty() || !clean.equals(raw.replaceAll("<[^>]+>", ""))) {
            throw new IllegalArgumentException("Empty or poorly spaced item name: " + raw);
        }
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (Character.isLetter(c) && c > 127 && c < 0xE000) {
                throw new IllegalArgumentException("Use plain English item names: " + raw);
            }
        }
        if (clean.indexOf('|') >= 0 || clean.indexOf(Character.toString(0x2022)) >= 0
            || clean.indexOf(Character.toString(0x2726)) >= 0 || clean.indexOf(Character.toString(0x2605)) >= 0
            || clean.indexOf(Character.toString(0x2727)) >= 0 || clean.indexOf(Character.toString(0x2606)) >= 0) {
            throw new IllegalArgumentException("Unsupported name ornament: " + raw);
        }
        return raw;
    }

    public static final class Builder {
        private final int rows;
        private final String title;
        private final Map<Integer, Item> items = new LinkedHashMap<>();

        private Builder(int rows, String title) {
            if (rows != MAX_ROWS) {
                throw new IllegalArgumentException("The shared navigation rail requires six rows");
            }
            this.rows = rows;
            this.title = title;
        }

        public Builder item(int slot, Item item) {
            if (slot < 0 || slot >= rows * COLUMNS) {
                throw new IllegalArgumentException("Slot outside menu: " + slot);
            }
            if (items.putIfAbsent(slot, Objects.requireNonNull(item, "item")) != null) {
                throw new IllegalArgumentException("Duplicate menu slot: " + slot);
            }
            return this;
        }

        public Builder info(int slot, String icon, String name, List<String> lore) {
            return item(slot, Item.info(icon, name, lore));
        }

        public Builder action(int slot, String icon, String name, List<String> lore, Action action) {
            return item(slot, Item.action(icon, name, lore, action));
        }

        public Builder disabled(int slot, String icon, String name, List<String> lore) {
            return item(slot, Item.disabled(icon, name, lore));
        }

        public <T> Builder page(int[] slots, Page<T> page, Function<T, Item> factory) {
            if (slots.length != page.capacity()) {
                throw new IllegalArgumentException("Page capacity differs from its hit areas");
            }
            for (int i = 0; i < page.entries().size(); i++) {
                item(slots[i], factory.apply(page.entries().get(i)));
            }
            return this;
        }

        public Builder empty(boolean empty, Item item) {
            return empty ? item(22, item) : this;
        }

        public Builder navigation(Action back, Action home, Action close, Action season,
                                  String backLabel, String seasonLabel) {
            action(GuiChrome.SLOT_BACK, "ref_arrow_left", "Go back",
                List.of(Palette.of(Palette.MUTED, backLabel)), back);
            action(GuiChrome.SLOT_HOME, "ref_home", "Open lobby",
                List.of(Palette.of(Palette.MUTED, "Leave this screen and return to the lobby")), home);
            action(GuiChrome.SLOT_CLOSE, "ref_cross", "Close menu",
                List.of(Palette.of(Palette.MUTED, "Return to the court")), close);
            if (season == null) {
                info(GuiChrome.SLOT_SEASON, "ref_trophy", seasonLabel,
                    List.of(Palette.of(Palette.MUTED, "You are viewing this season")));
            } else {
                action(GuiChrome.SLOT_SEASON, "ref_trophy", "Open season",
                    List.of(Palette.of(Palette.MUTED, seasonLabel)), season);
            }
            return this;
        }

        public Builder pagination(Page<?> page, Action previous, Action next) {
            if (page.index() > 0) {
                action(GuiChrome.SLOT_PREV, "icon_prev", "Show previous page",
                    List.of(Palette.of(Palette.MUTED, "Page " + page.index() + " of " + page.count())), previous);
            }
            if (page.index() + 1 < page.count()) {
                action(GuiChrome.SLOT_NEXT, "icon_next", "Show next page",
                    List.of(Palette.of(Palette.MUTED, "Page " + (page.index() + 2) + " of " + page.count())), next);
            }
            return this;
        }

        public MenuSpec build() {
            return new MenuSpec(this);
        }
    }
}
