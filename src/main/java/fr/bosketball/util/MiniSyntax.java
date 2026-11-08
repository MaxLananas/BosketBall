package fr.bosketball.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Set;

/**
 * Self-healing MiniMessage checks.
 *
 * <p>Every message the plugin sends goes through {@link #repair(String)}. A single stray closing tag,
 * an unclosed cursor, a placeholder such as {@code <player>} or a plain {@code <} used as text used to
 * either throw a {@code ParsingException} (the message silently disappeared) or print the raw tag in
 * chat ({@code </bold>}, {@code <#4ADE80>}). This class balances every tag, drops closers that were
 * never opened, closes what is left open and escapes anything that is not a real tag, so a message can
 * never leak markup again.</p>
 */
public final class MiniSyntax {

    /** Every tag name MiniMessage understands (standard + the extras the pack uses). */
    private static final Set<String> TAGS = Set.of(
        "bold", "b", "italic", "em", "i", "underlined", "u", "strikethrough", "st",
        "obfuscated", "obf", "reset", "newline", "br",
        "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
        "gold", "gray", "grey", "dark_gray", "dark_grey", "blue", "green", "aqua",
        "red", "light_purple", "yellow", "white",
        "color", "colour", "gradient", "rainbow", "transition",
        "font", "hover", "click", "key", "lang", "score", "selector", "translate",
        "insertion", "shadow", "shadow_color", "raw_style"
    );

    private MiniSyntax() {
    }

    /** True when {@code input} needs no repair at all. */
    public static boolean isClean(String input) {
        return input == null || input.indexOf('<') < 0 || repair(input).equals(input);
    }

    /**
     * Returns {@code input} with balanced tags: stray closers removed, unclosed tags closed, and every
     * {@code <...>} that is not a real MiniMessage tag escaped so it renders as literal text.
     */
    public static String repair(String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }
        if (input.indexOf('<') < 0 && input.indexOf('\\') < 0) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        Deque<String[]> open = new ArrayDeque<>();
        int i = 0;
        int n = input.length();
        while (i < n) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < n && input.charAt(i + 1) == '<') {
                out.append("\\<");
                i += 2;
                continue;
            }
            if (c != '<') {
                out.append(c);
                i++;
                continue;
            }
            int end = tagEnd(input, i);
            if (end < 0) {
                out.append("\\<");
                i++;
                continue;
            }
            String body = input.substring(i + 1, end);
            if (!isTag(body)) {
                if (body.trim().startsWith("/")) {
                    i = end + 1;                        // a closing tag we do not know: never printable
                    continue;
                }
                // "<player>", "<2.7m", "<1-365>" … documentation placeholder: render it literally.
                out.append("\\<");
                i++;
                continue;
            }
            String trimmed = body.trim();
            if (trimmed.endsWith("/")) {
                out.append('<').append(body).append('>');
                i = end + 1;
                continue;
            }
            if (trimmed.startsWith("/")) {
                String name = closeName(trimmed);
                if (name.isEmpty()) {
                    i = end + 1;                        // "</>" – drop it
                    continue;
                }
                if (open.isEmpty()) {
                    i = end + 1;                        // stray closer: drop, never print it
                    continue;
                }
                if (open.peek()[0].equals(name)) {
                    open.pop();
                    out.append('<').append(body).append('>');
                    i = end + 1;
                    continue;
                }
                if (containsKey(open, name)) {
                    // Re-open nesting in the right order: close what sits above it, then this one.
                    while (!open.isEmpty() && !open.peek()[0].equals(name)) {
                        out.append("</").append(open.pop()[1]).append('>');
                    }
                    if (!open.isEmpty()) {
                        open.pop();
                    }
                    out.append('<').append(body).append('>');
                    i = end + 1;
                    continue;
                }
                i = end + 1;                            // closer for a tag that was never opened
                continue;
            }
            open.push(new String[]{openName(trimmed), openCloseForm(trimmed)});
            out.append('<').append(body).append('>');
            i = end + 1;
        }
        while (!open.isEmpty()) {
            out.append("</").append(open.pop()[1]).append('>');
        }
        return out.toString();
    }

    /** Removes every tag and unescapes literals: the last-resort fallback when parsing still fails. */
    public static String stripTags(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length());
        int i = 0;
        int n = input.length();
        while (i < n) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < n && input.charAt(i + 1) == '<') {
                out.append('<');
                i += 2;
                continue;
            }
            if (c == '<') {
                int end = tagEnd(input, i);
                if (end >= 0) {
                    i = end + 1;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** Index of the {@code >} that closes the tag starting at {@code start}, or -1. */
    private static int tagEnd(String input, int start) {
        char quote = 0;
        int i = start + 1;
        int limit = Math.min(input.length(), start + 512);
        while (i < limit) {
            char c = input.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '>') {
                return i;
            } else if (c == '<' || c == '\n') {
                return -1;                              // not a tag after all
            }
            i++;
        }
        return -1;
    }

    /** True when the tag body is a real MiniMessage tag (name, optional args, optional leading / or !). */
    static boolean isTag(String body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty() || trimmed.equals("/")) {
            return false;
        }
        String main = unquoted(trimmed);
        if (main.indexOf(' ') >= 0 || main.indexOf('\t') >= 0) {
            return false;
        }
        String head = main.startsWith("/") ? main.substring(1) : main;
        if (head.startsWith("!")) {
            head = head.substring(1);
        }
        if (head.isEmpty()) {
            return false;
        }
        String name = head;
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(0, colon);
        }
        int slash = name.indexOf('/');
        if (slash >= 0) {
            name = name.substring(0, slash);
        }
        if (name.startsWith("#") || name.startsWith("$")) {
            return hex(name.substring(1)) || name.substring(1).contains(".");
        }
        return TAGS.contains(name.toLowerCase(Locale.ROOT));
    }

    private static boolean hex(String value) {
        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (hex.length() != 6 && hex.length() != 3 && hex.length() != 8) {
            return false;
        }
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** The body without quoted arguments: {@code hover:show_text:'<#4ADE80>x</#4ADE80>'} → {@code hover:show_text}. */
    private static String unquoted(String body) {
        StringBuilder out = new StringBuilder(body.length());
        char quote = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                continue;
            }
            out.append(c);
        }
        return out.toString().trim();
    }

    private static String closeName(String body) {
        String name = body.trim().substring(1).trim();
        if (name.startsWith("!")) {
            name = name.substring(1);
        }
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(0, colon);
        }
        return name;
    }

    private static boolean containsKey(Deque<String[]> stack, String key) {
        for (String[] entry : stack) {
            if (entry[0].equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** The exact form needed to close a tag: {@code !shadow} must be closed by {@code </!shadow>}. */
    private static String openCloseForm(String body) {
        String name = body.trim();
        name = name.startsWith("!") ? "!" + cutArgs(name.substring(1)) : cutArgs(name);
        return name;
    }

    private static String cutArgs(String name) {
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(0, colon) : name;
    }

    private static String openName(String body) {
        String name = body.trim();
        if (name.startsWith("!")) {
            name = name.substring(1);
        }
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(0, colon);
        }
        return name;
    }
}
