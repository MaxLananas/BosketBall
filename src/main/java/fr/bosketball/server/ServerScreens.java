package fr.bosketball.server;

import fr.bosketball.util.Palette;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.util.Msg;
import fr.bosketball.util.SmallCaps;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Branded full-screen messages (Round 28): the disconnect screen shown when the server closes, and
 * the maintenance screens.
 *
 * <p>Rules for these screens:
 * <ul>
 *   <li>no resource-pack glyphs (custom fonts are not loaded on the disconnect screen);</li>
 *   <li>brand gradient title + small-caps section labels, a thin divider and the player's own
 *       stats so the screen feels personal;</li>
 *   <li>the Discord invite and the site, because that is where players go when the server is
 *       down;</li>
 *   <li>every line stays short: the vanilla disconnect screen wraps around 55–60 characters.</li>
 * </ul>
 */
public final class ServerScreens {

    /** Roughly the widest line the disconnect screen renders without wrapping. */
    private static final int MAX_LINE = 58;

    private ServerScreens() {
    }

    /**
     * The screen players see when the server is stopping / restarting.
     *
     * <p>Round 38 — the disconnect screen is written in English: it is the last thing a player sees,
     * and a French line on a vanilla-styled disconnect screen reads like an error rather than a
     * planned restart. Numbers, the Discord invite and the site are kept as they are.
     */
    public static Component shutdown(BosketBallPlugin plugin, Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("<gradient:" + Msg.GRAD_BRAND + "><bold>▪ BOSKETBALL ▪</bold></gradient>");
        lines.add(divider());
        lines.add("<gradient:" + Msg.GRAD_ICE + "><bold>"
            + SmallCaps.toSmallCaps("SERVER RESTARTING") + "</bold></gradient>");
        lines.add("");
        lines.add("<" + Palette.MUTED + ">The court is getting a fresh coat of paint —</" + Palette.MUTED + ">");
        lines.add("<" + Palette.MUTED + ">we are back in a moment.</" + Palette.MUTED + ">");
        lines.add("");
        String stats = shutdownStatsLine(plugin, player);
        if (stats != null) {
            lines.add(stats);
            lines.add("");
        }
        lines.add(divider());
        lines.add(discordLine(plugin));
        lines.add(siteLine(plugin));
        lines.add("");
        lines.add("<gradient:" + Msg.GRAD_MINT + "><bold>See you back on the court!</bold></gradient>");
        return Msg.parse(String.join("\n", lines));
    }

    /**
     * Kick screen for a player who tries to join while the server is still starting up.
     *
     * <p>Round 40 — English, like the shutdown screen: it is the first thing a player sees after a
     * restart, and it has to say three things in one glance: the server is fine, it is starting, and
     * when to come back.</p>
     */
    public static Component starting(BosketBallPlugin plugin, Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("<gradient:" + Msg.GRAD_BRAND + "><bold>▪ BOSKETBALL ▪</bold></gradient>");
        lines.add(divider());
        lines.add("<gradient:" + Msg.GRAD_GOLD + "><bold>"
            + SmallCaps.toSmallCaps("SERVER STARTING") + "</bold></gradient>");
        lines.add("");
        lines.add("<" + Palette.MUTED + ">The lights are coming on and the court is being</" + Palette.MUTED + ">");
        lines.add("<" + Palette.MUTED + ">prepared. Give us a few seconds.</" + Palette.MUTED + ">");
        lines.add("");
        lines.add("<" + Palette.GOLD + ">Reconnect in a moment — you will spawn right</" + Palette.GOLD + ">");
        lines.add("<" + Palette.GOLD + ">back on the sideline.</" + Palette.GOLD + ">");
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
        return Msg.parse(String.join("\n", lines));
    }

    /** Kick screen for players who are not whitelisted while maintenance mode is on. */
    public static Component maintenance(BosketBallPlugin plugin) {
        String custom = plugin == null ? null : plugin.settings().motd().maintenanceKickMessage();
        List<String> lines = new ArrayList<>();
        lines.add("<gradient:" + Msg.GRAD_BRAND + "><bold>▪ BOSKETBALL ▪</bold></gradient>");
        lines.add(divider());
        lines.add("<gradient:" + Msg.GRAD_ICE + "><bold>"
            + SmallCaps.toSmallCaps("MAINTENANCE EN COURS") + "</bold></gradient>");
        lines.add("");
        if (custom != null && !custom.isBlank()) {
            lines.add(trimLines(custom, MAX_LINE));
        } else {
            lines.add("<" + Palette.MUTED + ">The courts are being updated.</" + Palette.MUTED + ">");
            lines.add("<" + Palette.MUTED + ">The server reopens very soon.</" + Palette.MUTED + ">");
        }
        lines.add("");
        lines.add(divider());
        lines.add(discordLine(plugin));
        lines.add(siteLine(plugin));
        lines.add("");
        lines.add("<gradient:" + Msg.GRAD_GOLD + "><bold>Merci de ta patience !</bold></gradient>");
        return Msg.parse(String.join("\n", lines));
    }

    /** Kick screen used when an admin turns maintenance mode on while players are online. */
    public static Component maintenanceStarted(BosketBallPlugin plugin, Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("<gradient:" + Msg.GRAD_BRAND + "><bold>▪ BOSKETBALL ▪</bold></gradient>");
        lines.add(divider());
        lines.add("<gradient:" + Msg.GRAD_SUNSET + "><bold>"
            + SmallCaps.toSmallCaps("MAINTENANCE IMMEDIATE") + "</bold></gradient>");
        lines.add("");
        lines.add("<" + Palette.MUTED + ">Staff are updating the server right now.</" + Palette.MUTED + ">");
        lines.add("<" + Palette.MUTED + ">Come back in a minute, everything will be ready.</" + Palette.MUTED + ">");
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
        lines.add("<gradient:" + Msg.GRAD_MINT + "><bold>À tout de suite !</bold></gradient>");
        return Msg.parse(String.join("\n", lines));
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
            || plain.contains("restart")
            || plain.contains("restart");
    }

    private static String divider() {
        return "<dark_gray>──────────</dark_gray><" + Palette.GOLD + ">──────</" + Palette.GOLD + "><dark_gray>──────────</dark_gray>";
    }

    /** The same progress recap as {@link #statsLine}, worded for the English restart screen. */
    private static String shutdownStatsLine(BosketBallPlugin plugin, Player player) {
        String saved = statsLine(plugin, player);
        return saved == null ? null : saved.replace("Saved:", "Saved:");
    }

    private static String statsLine(BosketBallPlugin plugin, Player player) {
        if (plugin == null || player == null || plugin.statsManager() == null) {
            return null;
        }
        PlayerProfile profile = plugin.statsManager().getIfPresent(player.getUniqueId());
        if (profile == null) {
            return null;
        }
        int wins = profile.wins();
        return "<" + Palette.MUTED + ">Saved:</" + Palette.MUTED + "> <white>" + profile.elo() + " ELO</white>"
            + " <dark_gray>•</dark_gray> <" + Palette.GOLD_SOFT + "><bold>" + profile.coins() + " Coins</bold></" + Palette.GOLD_SOFT + ">"
            + " <dark_gray>•</dark_gray> <white>" + wins + " W</white>";
    }

    private static String discordLine(BosketBallPlugin plugin) {
        String discord = plugin == null ? null : plugin.settings().links().discord();
        if (discord == null || discord.isBlank()) {
            return "<" + Palette.BLURPLE + "><bold>Discord</bold></" + Palette.BLURPLE + "> <gray>—</gray>";
        }
        return "<" + Palette.BLURPLE + "><bold>▪ Discord</bold></" + Palette.BLURPLE + "> <white>" + Msg.escapeMini(shortUrl(discord)) + "</white>";
    }

    private static String siteLine(BosketBallPlugin plugin) {
        String domain = plugin == null ? null : plugin.settings().general().serverDomain();
        if (domain == null || domain.isBlank()) {
            return "<" + Palette.ICE + "><bold>▪ Site</bold></" + Palette.ICE + "> <gray>—</gray>";
        }
        return "<" + Palette.ICE + "><bold>▪ Site</bold></" + Palette.ICE + "> <white>" + Msg.escapeMini(domain) + "</white>";
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
