package net.md_5.bungee.api;
import java.util.regex.Pattern;
/** Compile-only stub. Real class is provided by the Paper server at runtime. */
public class ChatColor {
    public static final char COLOR_CHAR = '\u00A7';
    public static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)" + COLOR_CHAR + "[0-9A-FK-ORX]");
    public static final ChatColor BLACK = new ChatColor(), DARK_BLUE = new ChatColor(), DARK_GREEN = new ChatColor(),
        DARK_AQUA = new ChatColor(), DARK_RED = new ChatColor(), DARK_PURPLE = new ChatColor(), GOLD = new ChatColor(),
        GRAY = new ChatColor(), DARK_GRAY = new ChatColor(), BLUE = new ChatColor(), GREEN = new ChatColor(),
        AQUA = new ChatColor(), RED = new ChatColor(), LIGHT_PURPLE = new ChatColor(), YELLOW = new ChatColor(),
        WHITE = new ChatColor(), MAGIC = new ChatColor(), BOLD = new ChatColor(), STRIKETHROUGH = new ChatColor(),
        UNDERLINE = new ChatColor(), ITALIC = new ChatColor(), RESET = new ChatColor();
    public static ChatColor of(String string) { return WHITE; }
    public static ChatColor getByChar(char code) { return WHITE; }
    public static ChatColor getByChar(String code) { return WHITE; }
    public static String translateAlternateColorCodes(char altColorChar, String textToTranslate) { return textToTranslate; }
    public static String stripColor(String input) { return input; }
    public String getName() { return "white"; }
    public java.awt.Color getColor() { return java.awt.Color.WHITE; }
    @Override public String toString() { return ""; }
}
