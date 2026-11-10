package fr.bosketball.ui;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.ui.kit.MenuItems;
import fr.bosketball.ui.kit.MenuSpec;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class PurchaseConfirmMenu implements InventoryHolder {
    public record Purchase(String title, String subtitle, ItemStack preview, int priceCoins,
                           String successMessage, Consumer<Player> apply) { }

    private static final int[] SLOT_ACCEPT = {19, 20, 21};
    private static final int[] SLOT_DECLINE = {23, 24, 25};
    private static final Map<UUID, Purchase> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Runnable> CANCEL = new ConcurrentHashMap<>();

    private final UUID owner;
    private Inventory inventory;
    private MenuSpec spec;
    private boolean settled;

    private PurchaseConfirmMenu(UUID owner) {
        this.owner = owner;
    }

    public static void open(BosketBallPlugin plugin, Player player, Purchase purchase) {
        if (plugin == null || player == null || purchase == null) {
            return;
        }
        PurchaseConfirmMenu menu = new PurchaseConfirmMenu(player.getUniqueId());
        Component title = Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_CONFIRM,
            GuiMenuManager.tr(player, "gui.title.confirm", "CONFIRM PURCHASE"));
        Inventory inv = Bukkit.createInventory(menu, 54, title);
        menu.inventory = inv;
        PENDING.put(player.getUniqueId(), purchase);
        menu.render(plugin, player, purchase);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.15f);
    }

    public static void setCancelAction(Player player, Runnable action) {
        if (player == null) return;
        if (action == null) CANCEL.remove(player.getUniqueId());
        else CANCEL.put(player.getUniqueId(), action);
    }

    public static void abandon(Player player) {
        if (player != null) PENDING.remove(player.getUniqueId());
    }

    public static Runnable cancelPending(Player player) {
        if (player == null) return null;
        PENDING.remove(player.getUniqueId());
        return CANCEL.remove(player.getUniqueId());
    }

    public static void clear(Player player) {
        if (player == null) return;
        PENDING.remove(player.getUniqueId());
        CANCEL.remove(player.getUniqueId());
    }

    private void render(BosketBallPlugin plugin, Player player, Purchase purchase) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        int balance = profile.coins();
        int after = balance - purchase.priceCoins();
        boolean affordable = after >= 0;
        MenuSpec.Builder builder = MenuSpec.builder(6, "Confirm purchase");
        List<String> wallet = new java.util.ArrayList<>(MenuItems.price(purchase.priceCoins(), balance));
        wallet.add(affordable ? MenuItems.row("After", Msg.count(after) + " coins")
            : MenuItems.row("Shortfall", MenuItems.shortfall(purchase.priceCoins(), balance)));
        builder.item(0, MenuItems.info("ref_coins", "Your wallet", wallet));
        ItemStack preview = purchase.preview();
        builder.item(13, preview == null ? MenuItems.info("ref_chest", purchase.title(), MenuItems.lore(purchase.subtitle()))
            : MenuSpec.Item.visual(preview, purchase.title(), MenuItems.lore(purchase.subtitle()), null));
        for (int slot : SLOT_ACCEPT) {
            builder.item(slot, affordable
                ? MenuItems.action("ref_check", "Buy " + purchase.title(), List.of(
                    MenuItems.row("Price", Msg.count(purchase.priceCoins()) + " coins"),
                    MenuItems.row("After", Msg.count(after) + " coins"), MenuItems.muted("Click to confirm.")),
                    click -> confirm(plugin, player, purchase))
                : MenuItems.unaffordable(purchase.title(), purchase.priceCoins(), balance, "Play a match to earn coins."));
        }
        for (int slot : SLOT_DECLINE) {
            builder.item(slot, MenuItems.action("ref_cross", "Cancel purchase", MenuItems.lore("Keep your coins."),
                click -> decline(plugin, player)));
        }
        builder.item(40, MenuItems.info("ref_info", "Nothing is spent yet", MenuItems.lore("Coins are taken only when you confirm. Closing cancels.")));
        String seasonName = plugin.seasonService() == null || plugin.seasonService().state() == null
            ? plugin.settings().general().seasonName() : plugin.seasonService().state().name();
        spec = builder.navigation(click -> decline(plugin, player), click -> {
            PENDING.remove(player.getUniqueId());
            CANCEL.remove(player.getUniqueId());
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> plugin.guiMenu().openMainHub(player));
        }, click -> decline(plugin, player), null, "Cancel this purchase", seasonName).build();
        ItemStack filler = GuiMenuManager.invisibleFiller();
        for (int slot = 0; slot < spec.size(); slot++) inventory.setItem(slot, filler);
        spec.render(item -> item.visualOr(GuiMenuManager.customIcon(item.icon(), item.name(), item.lore()))).forEach(inventory::setItem);
    }

    public boolean isSettled() {
        return settled;
    }

    public boolean handleClick(BosketBallPlugin plugin, Player player, InventoryClickEvent event) {
        event.setCancelled(true);
        Purchase purchase = PENDING.get(player.getUniqueId());
        if (purchase == null) {
            player.closeInventory();
            return true;
        }
        int slot = event.getRawSlot();
        MenuSpec.Item item = spec == null ? null : spec.items().get(slot);
        if (item != null && item.action() != null) {
            item.action().click(new MenuSpec.Click(player, slot, event.isRightClick(), event.isShiftClick(), event.getCurrentItem()));
            return true;
        }
        return slot < inventory.getSize();
    }

    private void confirm(BosketBallPlugin plugin, Player player, Purchase purchase) {
        if (PENDING.remove(player.getUniqueId()) == null) {
            return;
        }
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        if (profile.coins() < purchase.priceCoins() || !profile.spendCoins(purchase.priceCoins())) {
            Msg.send(player, Msg.error(MenuItems.shortfall(purchase.priceCoins(), profile.coins()) + ". Purchase canceled."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.1f);
            decline(plugin, player);
            return;
        }
        settled = true;
        player.closeInventory();
        try {
            purchase.apply().accept(player);
        } catch (RuntimeException ex) {
            profile.addCoins(purchase.priceCoins());
            plugin.statsManager().saveProfileAsync(profile);
            plugin.getLogger().warning("[purchase] grant failed, refunded " + purchase.priceCoins()
                + " to " + player.getName() + ": " + ex.getMessage());
            Msg.send(player, Msg.error("Something went wrong. Your coins were refunded."));
            return;
        }
        plugin.statsManager().saveProfileAsync(profile);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
        Msg.send(player, Msg.success(purchase.successMessage()));
        Runnable back = CANCEL.remove(player.getUniqueId());
        if (back != null) Bukkit.getScheduler().runTask(plugin, back);
    }

    private void decline(BosketBallPlugin plugin, Player player) {
        PENDING.remove(player.getUniqueId());
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.8f);
        Msg.send(player, Msg.info("Purchase canceled. Nothing was spent."));
        Runnable back = CANCEL.remove(player.getUniqueId());
        if (back != null) Bukkit.getScheduler().runTask(plugin, back);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID owner() {
        return owner;
    }
}
