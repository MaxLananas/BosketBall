package net.md_5.bungee.api.chat.hover.content;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
/** Compile-only stub. */
public abstract class Content {
    public abstract HoverEvent.Action requiredAction();
    public void assertAction(HoverEvent.Action input) { }
    public BaseComponent toComponent() { return null; }
}
