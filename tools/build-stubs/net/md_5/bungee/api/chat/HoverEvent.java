package net.md_5.bungee.api.chat;
import java.util.List;
import net.md_5.bungee.api.chat.hover.content.Content;
/** Compile-only stub. */
public class HoverEvent {
    public enum Action { SHOW_TEXT, SHOW_ACHIEVEMENT, SHOW_ITEM, SHOW_ENTITY }
    private Action action; private List<Content> contents; private boolean legacy;
    public HoverEvent(Action action, List<Content> contents) { this.action = action; this.contents = contents; }
    public HoverEvent(Action action, Content content) { this.action = action; }
    public HoverEvent(Action action, BaseComponent[] value) { this.action = action; this.legacy = true; }
    public Action getAction() { return action; }
    public List<Content> getContents() { return contents; }
    public void addContent(Content content) { }
    public boolean isLegacy() { return legacy; }
    public void setLegacy(boolean legacy) { this.legacy = legacy; }
    @Deprecated public BaseComponent[] getValue() { return new BaseComponent[0]; }
}
