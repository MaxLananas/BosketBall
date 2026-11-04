package net.md_5.bungee.api.chat;
import net.md_5.bungee.api.ChatColor;
/** Compile-only stub. */
public class TextComponent extends BaseComponent {
    private String text;
    public TextComponent() { }
    public TextComponent(TextComponent textComponent) { this.text = textComponent.text; }
    public TextComponent(BaseComponent... extras) { }
    public TextComponent(String text) { this.text = text; }
    public static BaseComponent[] fromLegacyText(String message) { return new BaseComponent[0]; }
    public static BaseComponent[] fromLegacyText(String message, ChatColor defaultColor) { return new BaseComponent[0]; }
    @Override public TextComponent duplicate() { return new TextComponent(this); }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
