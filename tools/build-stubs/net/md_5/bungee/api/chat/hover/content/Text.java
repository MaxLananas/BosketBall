package net.md_5.bungee.api.chat.hover.content;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
/** Compile-only stub. */
public class Text extends Content {
    private Object value;
    public Text(String text) { }
    public Text(BaseComponent[] components) { }
    public Object getValue() { return value; }
    @Override public HoverEvent.Action requiredAction() { return HoverEvent.Action.SHOW_TEXT; }
}
