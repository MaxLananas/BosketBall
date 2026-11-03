package net.md_5.bungee.api.chat.hover.content;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ItemTag;
/** Compile-only stub. */
public class Item extends Content {
    public Item(String id, int count, ItemTag tag) { }
    public String getId() { return ""; }
    public int getCount() { return 1; }
    public ItemTag getTag() { return null; }
    @Override public HoverEvent.Action requiredAction() { return HoverEvent.Action.SHOW_ITEM; }
}
