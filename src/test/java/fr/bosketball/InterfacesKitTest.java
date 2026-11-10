package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import fr.bosketball.ui.kit.GuiChrome;
import fr.bosketball.ui.kit.MenuItems;
import fr.bosketball.ui.kit.MenuSpec;
import fr.bosketball.ui.kit.NavigationStack;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.Test;

/**
 * Agent 3 — the declarative menu contract, verified off-server.
 *
 * <p>These tests pin the guarantees the interface kit makes to every screen: a chest never exceeds
 * 54 slots, a slot is never declared twice, an item name is always plain English, a disabled item
 * always explains itself, a list paginates instead of truncating, the back stack cannot cycle, and
 * every clickable name carries a verb. They run without a Bukkit server because the kit renders
 * through a plain factory.
 */
public class InterfacesKitTest {

    private static MenuSpec.Builder menu(String title) {
        return MenuSpec.builder(6, title);
    }

    private static MenuSpec.Builder navigable(String title) {
        return menu(title)
            .action(10, "ref_play", "Play a match", List.of(), click -> { })
            .navigation(click -> { }, click -> { }, click -> { }, click -> { }, "Go back", "Season");
    }

    @Test
    public void declaredMenusAreSixRowsAndAtMost54Slots() {
        MenuSpec spec = navigable("Locker").build();
        assertEquals(6, spec.rows());
        assertEquals(54, spec.size());
        assertTrue("a chest never exceeds 54 slots", spec.size() <= MenuSpec.MAX_SLOTS);
        for (int slot : spec.items().keySet()) {
            assertTrue("slot inside the chest: " + slot, slot >= 0 && slot < MenuSpec.MAX_SLOTS);
        }
        assertEquals("the renderer places exactly the declared slots",
            spec.items().keySet(), spec.render(item -> item).keySet());
    }

    @Test
    public void duplicateSlotIsRejected() {
        MenuSpec.Builder builder = navigable("Shop");
        assertThrows(IllegalArgumentException.class,
            () -> builder.item(10, MenuSpec.Item.info("ref_coins", "Wallet", List.of())));
    }

    @Test
    public void slotOutsideTheChestIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> menu("Edge").item(-1, MenuSpec.Item.info("ref_coins", "Wallet", List.of())));
        assertThrows(IllegalArgumentException.class,
            () -> menu("Edge").item(54, MenuSpec.Item.info("ref_coins", "Wallet", List.of())));
    }

    @Test
    public void onlySixRowMenusAreAllowed() {
        assertThrows(IllegalArgumentException.class, () -> MenuSpec.builder(3, "Too small"));
        assertThrows(IllegalArgumentException.class, () -> MenuSpec.builder(7, "Too tall"));
    }

    @Test
    public void namesRejectEmptyDoubleSpaceAndNonEnglish() {
        assertThrows(IllegalArgumentException.class, () -> MenuSpec.validateName(""));
        assertThrows(IllegalArgumentException.class, () -> MenuSpec.validateName("   "));
        assertThrows(IllegalArgumentException.class, () -> MenuSpec.validateName("Open  shop"));
        assertThrows(IllegalArgumentException.class, () -> MenuSpec.validateName(" leading"));
        assertThrows(IllegalArgumentException.class, () -> MenuSpec.validateName("Équiper"));
        assertThrows(IllegalArgumentException.class, () -> MenuSpec.validateName("ᴘʟᴀʏ"));
        assertEquals("Open shop", MenuSpec.validateName("Open shop"));
        // Private-use font glyphs (rank pills, the house mark) are deliberate, not accented Latin.
        assertEquals("Open \uE001 shop", MenuSpec.validateName("Open \uE001 shop"));
    }

    @Test
    public void namesRejectPipeBulletAndStars() {
        assertThrows(IllegalArgumentException.class, () -> MenuSpec.validateName("Open | shop"));
        assertThrows(IllegalArgumentException.class, () -> MenuSpec.validateName("Open " + Character.toString(0x2022) + " shop"));
        assertThrows(IllegalArgumentException.class, () -> MenuSpec.validateName("Open " + Character.toString(0x2605) + " shop"));
        assertThrows(IllegalArgumentException.class, () -> MenuSpec.validateName("Open " + Character.toString(0x2726) + " shop"));
        // The house separator (middot) and the house mark are allowed.
        assertEquals("Wins · Losses", MenuSpec.validateName("Wins · Losses"));
    }

    @Test
    public void everyMenuNeedsBackCloseAndAForwardAction() {
        assertThrows("no back/close rail", IllegalArgumentException.class,
            () -> menu("Orphan").action(10, "ref_play", "Play", List.of(), click -> { }).build());
        assertThrows("rail only, no way forward", IllegalArgumentException.class,
            () -> menu("Dead end").navigation(click -> { }, click -> { }, click -> { }, click -> { }, "Back", "Season").build());
        MenuSpec spec = navigable("Alive").build();
        assertNotNull(spec.items().get(GuiChrome.SLOT_BACK));
        assertNotNull(spec.items().get(GuiChrome.SLOT_CLOSE));
        assertNotNull(spec.items().get(GuiChrome.SLOT_HOME));
    }

    @Test
    public void disabledItemsExplainWhyAndCarryNoAction() {
        assertThrows("a disabled item must say why", IllegalArgumentException.class,
            () -> MenuSpec.Item.disabled("ref_lock", "Locked", List.of()));
        assertThrows("a disabled item cannot carry an action", IllegalArgumentException.class,
            () -> new MenuSpec.Item("ref_lock", "Locked", List.of("Need more coins"), null, false, true, click -> { }));
        MenuSpec.Item disabled = MenuSpec.Item.disabled("ref_lock", "Locked", List.of("Need more coins"));
        assertTrue(disabled.disabled());
        assertNull(disabled.action());
    }

    @Test
    public void backStackCannotCycleAndStaysBounded() {
        NavigationStack<String> stack = new NavigationStack<>();
        stack.push("Hub");
        stack.push("Shop");
        stack.push("Tags");
        stack.push("Hub"); // revisiting removes the loop instead of growing a cycle
        assertEquals(List.of("Shop", "Tags", "Hub"), stack.snapshot());
        assertEquals("Hub", stack.pop());
        assertEquals("Tags", stack.pop());
        assertEquals("Shop", stack.pop());
        assertNull(stack.pop());

        stack.push("Shop");
        stack.push("Shop"); // refreshing the same screen never duplicates it
        assertEquals(1, stack.size());

        stack.clear();
        for (int i = 1; i <= NavigationStack.LIMIT + 4; i++) {
            stack.push("Screen " + i);
        }
        assertEquals("history is bounded", NavigationStack.LIMIT, stack.size());
        assertEquals("Screen " + (NavigationStack.LIMIT + 4), stack.peek());
    }

    @Test
    public void paginationClampsAndNeverTruncates() {
        List<Integer> all = IntStream.range(0, 57).boxed().toList();
        MenuSpec.Page<Integer> first = MenuSpec.Page.of(all, -5, 28);
        assertEquals(0, first.index());
        assertEquals(28, first.entries().size());
        assertEquals(3, first.count());

        MenuSpec.Page<Integer> last = MenuSpec.Page.of(all, 999, 28);
        assertEquals(2, last.index());
        assertEquals(1, last.entries().size());
        assertEquals(1, last.previous());
        assertEquals(2, last.next());
        assertEquals(0, first.previous());
        assertEquals(1, first.next());

        Set<Integer> seen = new HashSet<>();
        for (int page = 0; page < first.count(); page++) {
            seen.addAll(MenuSpec.Page.of(all, page, 28).entries());
        }
        assertEquals("every entry is reachable, nothing is silently dropped", 57, seen.size());
    }

    @Test
    public void shortfallIsExactAndUnaffordableIsDisabled() {
        assertEquals("Need 50 more coins", MenuItems.shortfall(120, 70));
        assertEquals("Need 5,000 more coins", MenuItems.shortfall(12000, 7000));
        assertEquals("Need 0 more coins", MenuItems.shortfall(70, 120));

        MenuSpec.Item locked = MenuItems.unaffordable("Fire ball", 120, 70, "Play a match to earn coins.");
        assertTrue(locked.disabled());
        assertNull(locked.action());
        assertTrue("the hover states the exact shortfall",
            locked.lore().stream().anyMatch(line -> line.contains("Need 50 more coins")));
        assertThrows("an affordable item is not an insufficient state", IllegalArgumentException.class,
            () -> MenuItems.unaffordable("Fire ball", 100, 100, "Play a match."));
    }

    @Test
    public void clickableNamesCarryAVerbAndEmptyStatesOfferANextStep() {
        assertEquals("shop", MenuItems.plain("ꜱʜᴏᴘ"));
        assertEquals("Open shop", MenuItems.actionLabel("ꜱʜᴏᴘ"));
        assertEquals("Play", MenuItems.actionLabel("ᴘʟᴀʏ"));
        assertEquals("Leave", MenuItems.actionLabel("ʟᴇᴀᴠᴇ"));
        assertEquals("Open friends", MenuItems.actionLabel("ꜰʀɪᴇɴᴅꜱ"));
        assertEquals("Claim all", MenuItems.actionLabel("Claim all"));
        assertEquals("Show previous page", MenuItems.actionLabel("◂ Previous Page"));

        assertNotNull("an empty list still offers a way forward",
            MenuItems.empty("No friends yet.", "Add a friend", click -> { }).action());
        assertNotNull("an error still offers a way forward",
            MenuItems.error("That player left.", "Choose a mode", click -> { }).action());
    }
}
