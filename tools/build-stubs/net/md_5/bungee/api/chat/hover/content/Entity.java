package net.md_5.bungee.api.chat.hover.content;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
/** Compile-only stub. */
public class Entity extends Content {
    public Entity(String type, String id, BaseComponent name) { }
    public String getType() { return ""; }
    public String getId() { return ""; }
    public BaseComponent getName() { return null; }
    @Override public HoverEvent.Action requiredAction() { return HoverEvent.Action.SHOW_ENTITY; }
}
