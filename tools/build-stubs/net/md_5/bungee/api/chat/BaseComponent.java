package net.md_5.bungee.api.chat;
import java.util.List;
import net.md_5.bungee.api.ChatColor;
/** Compile-only stub. Real class is provided by the Paper server at runtime. */
public abstract class BaseComponent {
    private List<BaseComponent> extra;
    private ChatColor color;
    private String font;
    private Boolean bold, italic, underlined, strikethrough, obfuscated;
    private String insertion;
    private ClickEvent clickEvent;
    private HoverEvent hoverEvent;
    protected BaseComponent() { }
    protected BaseComponent(BaseComponent old) { }
    public abstract BaseComponent duplicate();
    public List<BaseComponent> getExtra() { return extra; }
    public void setExtra(List<BaseComponent> components) { this.extra = components; }
    public void addExtra(BaseComponent component) { }
    public void addExtra(String text) { }
    public ChatColor getColor() { return color; }
    public ChatColor getColorRaw() { return color; }
    public void setColor(ChatColor color) { this.color = color; }
    public String getFont() { return font; }
    public String getFontRaw() { return font; }
    public void setFont(String font) { this.font = font; }
    public boolean isBold() { return bold != null && bold; }
    public Boolean isBoldRaw() { return bold; }
    public void setBold(Boolean bold) { this.bold = bold; }
    public boolean isItalic() { return italic != null && italic; }
    public Boolean isItalicRaw() { return italic; }
    public void setItalic(Boolean italic) { this.italic = italic; }
    public boolean isUnderlined() { return underlined != null && underlined; }
    public Boolean isUnderlinedRaw() { return underlined; }
    public void setUnderlined(Boolean underlined) { this.underlined = underlined; }
    public boolean isStrikethrough() { return strikethrough != null && strikethrough; }
    public Boolean isStrikethroughRaw() { return strikethrough; }
    public void setStrikethrough(Boolean strikethrough) { this.strikethrough = strikethrough; }
    public boolean isObfuscated() { return obfuscated != null && obfuscated; }
    public Boolean isObfuscatedRaw() { return obfuscated; }
    public void setObfuscated(Boolean obfuscated) { this.obfuscated = obfuscated; }
    public String getInsertion() { return insertion; }
    public void setInsertion(String insertion) { this.insertion = insertion; }
    public ClickEvent getClickEvent() { return clickEvent; }
    public void setClickEvent(ClickEvent clickEvent) { this.clickEvent = clickEvent; }
    public HoverEvent getHoverEvent() { return hoverEvent; }
    public void setHoverEvent(HoverEvent hoverEvent) { this.hoverEvent = hoverEvent; }
    public boolean hasFormatting() { return false; }
    public void copyFormatting(BaseComponent component) { }
    public void copyFormatting(BaseComponent component, ComponentBuilder.FormatRetention retention, boolean replace) { }
    public void retain(ComponentBuilder.FormatRetention retention) { }
    public String toPlainText() { return ""; }
    public String toLegacyText() { return ""; }
    public static String toPlainText(BaseComponent... components) { return ""; }
    public static String toLegacyText(BaseComponent... components) { return ""; }
}
