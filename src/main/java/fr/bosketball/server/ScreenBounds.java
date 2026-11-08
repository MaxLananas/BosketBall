package fr.bosketball.server;

import fr.bosketball.util.MiniSyntax;
import fr.bosketball.util.Msg;
import fr.bosketball.util.Palette;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * The hard limits of a server-list ping and of a disconnect screen, plus the house-style checks
 * every player-facing line has to pass.
 *
 * <p>The server list owns two lines and the client cuts each one around 45 visible characters;
 * hosting panels cap the whole description around 150 characters including codes. BosketBall also
 * centres its banner on a 268 px canvas, so a line can be short in characters and still too wide in
 * pixels (a bold wordmark is the usual offender). All three limits are checked here, where they can
 * be asserted without a server.
 *
 * <p>This is also the only place that writes the separator down. A new screen that needs a middot
 * calls {@link #separator()} instead of typing one, which is how the pipe and the bullet keep from
 * creeping back into the HUD.
 */
public final class ScreenBounds {

    /** The banner canvas used by {@link MotdManager#centerLine}. */
    public static final int MOTD_LINE_WIDTH_PX = MotdManager.MOTD_LINE_WIDTH_PX;
    /** The client truncates a MOTD line around here; colour tags do not count. */
    public static final int MOTD_LINE_VISIBLE_CHARS = 45;
    /** Two lines: the wordmark and one line of copy. */
    public static final int MOTD_LINES = 2;
    /** Practical ceiling for the whole description, codes included. */
    public static final int MOTD_RAW_CHARS = 150;
    /** A hard byte ceiling for the ping description JSON. */
    public static final int MOTD_BYTES = 2048;

    /** The vanilla disconnect screen wraps around 58 visible characters per line. */
    public static final int SCREEN_LINE_CHARS = 58;
    public static final int SCREEN_LINES = 20;
    public static final int SCREEN_BYTES = 4096;
    /** Hover-list rows in the ping payload are shorter than the banner itself. */
    public static final int PING_SAMPLE_CHARS = 48;

    private static final String SEPARATOR = "<" + Palette.DIM + ">\u00b7</" + Palette.DIM + ">";
    private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{6}");
    /** Built from code points: the star sweep reads the source text, not just the rendered glyph. */
    private static final String STARS = "" + (char) 0x2726 + (char) 0x2727
        + (char) 0x2605 + (char) 0x2606;

    private ScreenBounds() {
    }

    /** The one separator the house allows, with its dim colour. */
    public static String separator() {
        return SEPARATOR;
    }

    /** What the player actually reads, with every tag resolved away. */
    public static String plain(String mini) {
        return PlainTextComponentSerializer.plainText().serialize(Msg.parse(mini));
    }

    /** Pixel width of a MiniMessage line on the server-list canvas. */
    public static int pixelWidth(String mini) {
        return MotdManager.measureComponentPixels(Msg.parse(mini), false);
    }

    /** True when both banner lines fit the ping payload and the pixel canvas. */
    public static boolean fitsMotd(String header, String subtitle) {
        if (header == null || subtitle == null) {
            return false;
        }
        String raw = header + "\n" + subtitle;
        List<String> lines = List.of(header, subtitle);
        if (lines.size() != MOTD_LINES || raw.getBytes(StandardCharsets.UTF_8).length > MOTD_BYTES
            || raw.length() > MOTD_RAW_CHARS * MOTD_LINES) {
            return false;
        }
        for (String line : lines) {
            String visible = plain(line);
            if (visible.length() > MOTD_LINE_VISIBLE_CHARS || pixelWidth(line) > MOTD_LINE_WIDTH_PX) {
                return false;
            }
        }
        return true;
    }

    /** True when every hover-list row fits the ping payload. */
    public static boolean fitsPingSample(List<String> rows) {
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        for (String row : rows) {
            if (row == null || row.replaceAll("\u00a7.", "").length() > PING_SAMPLE_CHARS) {
                return false;
            }
        }
        return String.join("\n", rows).getBytes(StandardCharsets.UTF_8).length <= MOTD_BYTES;
    }

    /** True when a disconnect screen fits the vanilla screen without wrapping or clipping. */
    public static boolean fitsScreen(List<String> lines) {
        if (lines == null || lines.isEmpty() || lines.size() > SCREEN_LINES) {
            return false;
        }
        int bytes = 0;
        for (String line : lines) {
            if (line == null) {
                return false;
            }
            if (plain(line).length() > SCREEN_LINE_CHARS) {
                return false;
            }
            bytes += line.getBytes(StandardCharsets.UTF_8).length;
        }
        return bytes <= SCREEN_BYTES;
    }

    /**
     * All the ways a line of copy can be wrong: leaked markup, a stray closing tag, a pipe, a
     * bullet, a star, a middot that is not the house separator, a fourteenth colour, a legacy
     * colour name, or an accented Latin character. An empty list means the line is safe.
     */
    public static List<String> copyViolations(String mini) {
        List<String> problems = new ArrayList<>();
        if (mini == null || mini.isBlank()) {
            problems.add("blank copy");
            return problems;
        }
        if (!MiniSyntax.isClean(mini)) {
            problems.add("unbalanced MiniMessage: " + mini);
        }
        String rendered = plain(mini);
        if (rendered.contains("<") || rendered.contains(">") || rendered.contains("</")) {
            problems.add("leaked markup: " + rendered);
        }
        if (rendered.isBlank()) {
            problems.add("nothing renders: " + mini);
        }
        if (mini.indexOf('|') >= 0) {
            problems.add("pipe separator");
        }
        if (mini.indexOf('\u2022') >= 0) {
            problems.add("bullet separator");
        }
        for (int i = 0; i < STARS.length(); i++) {
            if (mini.indexOf(STARS.charAt(i)) >= 0 || rendered.indexOf(STARS.charAt(i)) >= 0) {
                problems.add("decorative star");
                break;
            }
        }
        if (mini.replace(SEPARATOR, "").indexOf('\u00b7') >= 0) {
            problems.add("a middot outside the house separator");
        }
        if (mini.contains(" - ")) {
            problems.add("hyphen used as a separator");
        }
        Matcher hex = HEX.matcher(mini);
        while (hex.find()) {
            String colour = hex.group().toUpperCase(Locale.ROOT);
            boolean known = false;
            for (String token : Palette.ALL) {
                if (token.equals(colour)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                problems.add("colour outside Palette: " + colour);
            }
        }
        for (String name : List.of("gray", "white", "yellow", "gold", "aqua",
            "red", "green", "dark_gray")) {
            String legacy = "<" + name + ">";
            if (mini.contains(legacy)) {
                problems.add("legacy colour name: " + name);
            }
        }
        for (int i = 0; i < rendered.length(); i++) {
            char c = rendered.charAt(i);
            if (c >= 0x00C0 && c <= 0x00FF) {
                problems.add("accented Latin character in: " + rendered);
                break;
            }
        }
        return problems;
    }

    /** True when the line is balanced, renders cleanly and obeys the house style. */
    public static boolean isMiniMessageSafe(String mini) {
        return copyViolations(mini).isEmpty();
    }
}
