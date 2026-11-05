package fr.bosketball.ui;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class PurchaseConfirmMenu implements InventoryHolder {

    public record Purchase(
        String title,
        String subtitle,
        ItemStack preview,
        int priceCoins,
        String successMessage,
        Consumer<Player> apply
    ) {
    }

    private static final int SLOT_WALLET = 0;
    private static final int SLOT_PREVIEW = 13;

    private static final int[] SLOT_ACCEPT = {19, 20, 21};
    private static final int[] SLOT_DECLINE = {23, 24, 25};
    private static final int SLOT_INFO = 40;

    private static final Map<UUID, Purchase> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Runnable> CANCEL = new ConcurrentHashMap<>();

    private final UUID owner;
    private Inventory inventory;

    private boolean settled;

    private PurchaseConfirmMenu(UUID owner) {
        this.owner = owner;
    }

    public static void open(BosketBallPlugin plugin, Player player, Purchase purchase) {
        if (plugin == null || player == null || purchase == null) {
            return;
        }
        PurchaseConfirmMenu menu = new PurchaseConfirmMenu(player.getUniqueId());

        Component title = Glyphs.customGuiOverlayTitle(
            Glyphs.GUI_OVERLAY_CONFIRM, GuiMenuManager.tr(player, "gui.title.confirm", "CONFIRM PURCHASE"));
        Inventory inv = Bukkit.createInventory(menu, 54, title);
        menu.inventory = inv;
        PENDING.put(player.getUniqueId(), purchase);
        menu.render(plugin, player, purchase);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.15f);
    }

    public static void setCancelAction(Player player, Runnable action) {
        if (player == null) {
            return;
        }
        if (action == null) {
            CANCEL.remove(player.getUniqueId());
        } else {
            CANCEL.put(player.getUniqueId(), action);
        }
    }

    public static boolean isPending(Player player) {
        return player != null && PENDING.containsKey(player.getUniqueId());
    }

    public static void abandon(Player player) {
        if (player == null) {
            return;
        }
        PENDING.remove(player.getUniqueId());
    }

    public static void clear(Player player) {
        if (player == null) {
            return;
        }
        PENDING.remove(player.getUniqueId());
        CANCEL.remove(player.getUniqueId());
    }

    private void render(BosketBallPlugin plugin, Player player, Purchase purchase) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        ItemStack invisible = blank();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, invisible);
        }

        int after = profile.coins() - purchase.priceCoins();
        inventory.setItem(SLOT_WALLET, icon(
            Material.GOLD_INGOT,
            "<#FDE047><bold>Your Wallet</bold></#FDE047>",
            List.of(
                "<gray>Balance:</gray> <#FDE047>" + profile.coins() + " Coins</#FDE047>",
                "<gray>Price:</gray> <#FDE047>" + purchase.priceCoins() + " Coins</#FDE047>",
                after >= 0
                    ? "<gray>After:</gray> <#4ADE80>" + after + " Coins</#4ADE80>"
                    : "<gray>Missing:</gray> <#F87171>" + Math.abs(after) + " Coins</#F87171>"
            )
        ));

        ItemStack preview = purchase.preview() == null
            ? icon(Material.CHEST, "<white>Item</white>", List.of())
            : purchase.preview().clone();
        inventory.setItem(SLOT_PREVIEW, preview);

        boolean affordable = after >= 0;

        for (int slot : SLOT_ACCEPT) {

            inventory.setItem(slot, backing(affordable ? Material.LIME_STAINED_GLASS_PANE
                : Material.GRAY_STAINED_GLASS_PANE, " "));
            inventory.setItem(slot, icon(
                affordable ? Material.LIME_DYE : Material.GRAY_DYE,
                affordable
                    ? "<#4ADE80><bold>ACCEPT</bold></#4ADE80>"
                    : "<#94A3B8><bold>CANNOT AFFORD</bold></#94A3B8>",
                affordable
                    ? List.of(
                        "<gray>Buy <white>" + purchase.title() + "</white></gray>",
                        "<gray>Cost:</gray> <#FDE047>" + purchase.priceCoins() + " Coins</#FDE047>",
                        "",
                        "<#4ADE80><bold>▸ CLICK TO CONFIRM</bold></#4ADE80>"
                    )
                    : List.of(
                        "<gray>You need <#F87171>" + Math.abs(after) + " more Coins</#F87171></gray>",
                        "<gray>Play a match or open the crate.</gray>"
                    )
            ));
        }

        for (int slot : SLOT_DECLINE) {
            inventory.setItem(slot, backing(Material.RED_STAINED_GLASS_PANE, " "));
            inventory.setItem(slot, icon(
                Material.RED_DYE,
                "<#F87171><bold>DECLINE</bold></#F87171>",
                List.of(
                    "<gray>Keep your Coins.</gray>",
                    "",
                    "<#F87171><bold>▸ CLICK TO CANCEL</bold></#F87171>"
                )
            ));
        }

        inventory.setItem(SLOT_INFO, icon(
            Material.KNOWLEDGE_BOOK,
            "<#38BDF8><bold>Nothing is spent yet</bold></#38BDF8>",
            List.of(
                "<gray>Coins are only taken when you click",
                "<#4ADE80>ACCEPT</#4ADE80><gray>. Closing this menu cancels.</gray>"
            )
        ));
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
        if (isAccept(slot) || isDecline(slot)) {
            if (isAccept(slot)) {
                confirm(plugin, player, purchase);
            } else {
                decline(plugin, player);
            }
            return true;
        }

        return slot < inventory.getSize();
    }

    private void confirm(BosketBallPlugin plugin, Player player, Purchase purchase) {

        if (PENDING.remove(player.getUniqueId()) == null) {
            return;
        }
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        if (profile.coins() < purchase.priceCoins()) {
            Msg.send(player, Msg.error("Not enough Coins any more — the purchase was cancelled."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.1f);
            decline(plugin, player);
            return;
        }

        if (!profile.spendCoins(purchase.priceCoins())) {
            Msg.send(player, Msg.error("Not enough Coins any more — the purchase was cancelled."));
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
            Msg.send(player, Msg.error("Something went wrong — your Coins were refunded."));
            return;
        }
        plugin.statsManager().saveProfileAsync(profile);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
        Msg.send(player, Msg.success(purchase.successMessage()));
        Runnable back = CANCEL.remove(player.getUniqueId());
        if (back != null) {
            Bukkit.getScheduler().runTask(plugin, back);
        }
    }

    private void decline(BosketBallPlugin plugin, Player player) {
        PENDING.remove(player.getUniqueId());
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.8f);
        Msg.send(player, Msg.info("Purchase cancelled — nothing was spent."));
        Runnable back = CANCEL.remove(player.getUniqueId());
        if (back != null) {
            Bukkit.getScheduler().runTask(plugin, back);
        }
    }

    private static boolean isAccept(int slot) {
        for (int s : SLOT_ACCEPT) {
            if (s == slot) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDecline(int slot) {
        for (int s : SLOT_DECLINE) {
            if (s == slot) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack backing(Material glass, String name) {
        ItemStack stack = new ItemStack(glass);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.parse(name));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack blank() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.parse(name).decoration(TextDecoration.ITALIC, false));
            if (!lore.isEmpty()) {
                meta.lore(lore.stream()
                    .map(line -> line.isEmpty()
                        ? Component.empty()
                        : Msg.parse(line).decoration(TextDecoration.ITALIC, false))
                    .map(c -> (Component) c.colorIfAbsent(TextColor.color(0xCBD5E1)))
                    .toList());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID owner() {
        return owner;
    }
}
