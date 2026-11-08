package fr.bosketball.server;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.util.Msg;
import fr.bosketball.util.Palette;
import fr.bosketball.util.SmallCaps;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Branded full-screen messages: the disconnect screen shown when the server closes, the boot screen
 * and the maintenance screens.
 *
 * <p>These are the last (or first) thing a player reads, so they are 100% English, short, and
 * written like a person at the door rather than a system log. The community pass removed the three
 * French lines that had survived every earlier round, replaced the last raw hex literals with
 * {@link Palette} tokens, and made the lines available as data through {@code *Lines()} so the ping
 * / screen limits can be asserted without a server.
 *
 * <p>Rules: no resource-pack glyphs (custom fonts are not loaded on a disconnect screen), one
 * title, one thin divider, the player's own stats, and every line under 58 visible characters.
 */
public final class ServerScreens {

    /** Roughly the widest line the disconnect screen renders without wrapping. */
    public static final int MAX_LINE = 58;

    private ServerScreens() {
    }

    /** The screen players see when the server is stopping / restarting. */
    public static Component shutdown(BosketBallPlugin plugin, Player player) {
        return render(shutdownLines(plugin, player));
    }

    public static List<String> shutdownLines(BosketBallPlugin plugin, Player player) {
        List<String> lines = new ArrayList<>();
        lines.add(wordmark());
        lines.add(divider());
        lines.add(gradientTitle(Msg.GRAD_ICE, "SERVER RESTARTING"));
        lines.add("");
        lines.add(muted("The court is getting a fresh coat of paint."));
        lines.add(muted("We are back in a moment."));
        lines.add("");
        String stats = statsLine(plugin, player);
        if (stats != null) {
            lines.add(stats);
            lines.add("");
        }
        lines.add(divider());
        lines.add(discordLine(plugin));
        lines.add(siteLine(plugin));
        lines.add("");
        lines.add("<gradient:" + Msg.GRAD_MINT + "><bold>See you back on the court!</bold></gradient>");
        return lines;
    }

    /** Kick screen for a player who joins while the server is still starting up. */
    public static Component starting(BosketBallPlugin plugin, Player player) {
        return render(startingLines(plugin, player));
    }

    public static List<String> startingLines(BosketBallPlugin plugin, Player player) {
        List<String> lines = new ArrayList<>();
        lines.add(wordmark());
        lines.add(divider());
        lines.add(gradientTitle(Msg.GRAD_GOLD, "SERVER STARTING"));
        lines.add("");
        lines.add(muted("The lights are coming on and the court is being"));
        lines.add(muted("prepared. Give us a few seconds."));
        lines.add("");
        lines.add(gold("Reconnect in a moment. You spawn on the sideline."));
        lines.add("");
        String stats = statsLine(plugin, player);
        if (stats != null) {
            lines.add(stats);
            lines.add("");
        }
        lines.add(divider());
        lines.add(discordLine(plugin));
        lines.add(siteLine(plugin));
        lines.add("");
        lines.add("<gradient:" + Msg.GRAD_MINT + "><bold>Tip-off in a few seconds!</bold></gradient>");
        return lines;
    }

    /** Kick screen for players who are not whitelisted while maintenance mode is on. */
    public static Component maintenance(BosketBallPlugin plugin) {
        return render(maintenanceLines(plugin));
    }

    public static List<String> maintenanceLines(BosketBallPlugin plugin) {
        String custom = plugin == null ? null : plugin.settings().motd().maintenanceKickMessage();
        List<String> lines = new ArrayList<>();
        lines.add(wordmark());
        lines.add(divider());
        lines.add(gradientTitle(Msg.GRAD_ICE, "MAINTENANCE"));
        lines.add("");
        if (custom != null && !custom.isBlank()) {
            lines.add(trimLines(custom, MAX_LINE));
        } else {
            lines.add(muted("The courts are being updated."));
            lines.add(muted("The server reopens very soon."));
        }
        lines.add("");
        lines.add(divider());
        lines.add(discordLine(plugin));
        lines.add(siteLine(plugin));
        lines.add("");
        lines.add("<gradient:" + Msg.GRAD_GOLD + "><bold>Thanks for waiting.</bold></gradient>");
        return lines;
    }

    /** Kick screen used when an admin turns maintenance mode on while players are online. */
    public static Component maintenanceStarted(BosketBallPlugin plugin, Player player) {
        return render(maintenanceStartedLines(plugin, player));
    }

    public static List<String> maintenanceStartedLines(BosketBallPlugin plugin, Player player) {
        List<String> lines = new ArrayList<>();
        lines.add(wordmark());
        lines.add(divider());
        lines.add(gradientTitle(Msg.GRAD_SUNSET, "MAINTENANCE IMMEDIATE"));
        lines.add("");
        lines.add(muted("Staff are updating the server right now."));
        lines.add(muted("Come back in a minute, everything will be ready."));
        lines.add("");
        String stats = statsLine(plugin, player);
        if (stats != null) {
            lines.add(stats);
            lines.add("");
        }
        lines.add(divider());
        lines.add(discordLine(plugin));
        lines.add(siteLine(plugin));
        lines.add("");
        lines.add("<gradient:" + Msg.GRAD_MINT + "><bold>Back in a minute.</bold></gradient>");
        return lines;
    }

    /** True when a kick reason comes from the server closing (vanilla or Paper wording). */
    public static boolean isServerClosing(Component reason) {
        if (reason == null) {
            return false;
        }
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(reason).toLowerCase(java.util.Locale.ROOT);
        return plain.contains("server closed")
            || plain.contains("server_closed")
            || plain.contains("server_shutdown")
            || plain.contains("shutting down")
            || plain.contains("server is closing")
            || plain.contains("restart");
    }

    /**
     * Renders one screen, with a bounded fallback: a hand-edited maintenance message can be longer
     * than the vanilla screen, and a clipped screen is worse than a short honest one.
     */
    public static Component render(List<String> lines) {
        List<String> safe = ScreenBounds.fitsScreen(lines) ? lines : List.of(
            wordmark(), muted("Back in a moment."));
        return Msg.parse(String.join("\n", safe));
    }

    private static String wordmark() {
        return "<gradient:" + Msg.GRAD_BRAND + "><bold>▪ BOSKETBALL ▪</bold></gradient>";
    }

    private static String gradientTitle(String stops, String title) {
        return "<gradient:" + stops + "><bold>" + SmallCaps.toSmallCaps(title) + "</bold></gradient>";
    }

    private static String muted(String value) {
        return "<" + Palette.MUTED + ">" + value + "</" + Palette.MUTED + ">";
    }

    private static String gold(String value) {
        return "<" + Palette.GOLD + ">" + value + "</" + Palette.GOLD + ">";
    }

    private static String divider() {
        return "<" + Palette.DIM + ">──────────</" + Palette.DIM + "><"
            + Palette.GOLD + ">──────</" + Palette.GOLD + "><"
            + Palette.DIM + ">──────────</" + Palette.DIM + ">";
    }

    private static String statsLine(BosketBallPlugin plugin, Player player) {
        if (plugin == null || player == null || plugin.statsManager() == null) {
            return null;
        }
        PlayerProfile profile = plugin.statsManager().getIfPresent(player.getUniqueId());
        if (profile == null) {
            return null;
        }
        return muted("Saved:") + " <" + Palette.TEXT + ">" + profile.elo() + " ELO</" + Palette.TEXT + ">"
            + " " + ScreenBounds.separator() + " <" + Palette.GOLD_SOFT + "><bold>" + profile.coins()
            + " Coins</bold></" + Palette.GOLD_SOFT + ">"
            + " " + ScreenBounds.separator() + " <" + Palette.TEXT + ">" + profile.wins()
            + " W</" + Palette.TEXT + ">";
    }

    private static String discordLine(BosketBallPlugin plugin) {
        String discord = plugin == null ? null : plugin.settings().links().discord();
        if (discord == null || discord.isBlank()) {
            return "<" + Palette.BLURPLE + "><bold>Discord</bold></" + Palette.BLURPLE + "> "
                + ScreenBounds.separator() + " " + muted("ask in chat");
        }
        return "<" + Palette.BLURPLE + "><bold>▪ Discord</bold></" + Palette.BLURPLE + "> <"
            + Palette.TEXT + ">" + Msg.escapeMini(shortUrl(discord)) + "</" + Palette.TEXT + ">";
    }

    private static String siteLine(BosketBallPlugin plugin) {
        String domain = plugin == null ? null : plugin.settings().general().serverDomain();
        if (domain == null || domain.isBlank()) {
            return "<" + Palette.ICE + "><bold>▪ Site</bold></" + Palette.ICE + "> "
                + ScreenBounds.separator() + " " + muted("ask in chat");
        }
        return "<" + Palette.ICE + "><bold>▪ Site</bold></" + Palette.ICE + "> <"
            + Palette.TEXT + ">" + Msg.escapeMini(domain) + "</" + Palette.TEXT + ">";
    }

    private static String shortUrl(String raw) {
        String url = raw.trim();
        if (url.length() <= MAX_LINE) {
            return url;
        }
        return url.substring(0, MAX_LINE - 1) + "…";
    }

    /** Wraps a user-supplied MiniMessage right before the closing screen would cut it. */
    private static String trimLines(String miniMessage, int maxLine) {
        StringBuilder out = new StringBuilder();
        for (String line : miniMessage.split("\\n")) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(line.length() <= maxLine ? line : line.substring(0, maxLine - 1) + "…");
        }
        return out.toString();
    }
}
