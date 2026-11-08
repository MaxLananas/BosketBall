package fr.bosketball.server;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.config.BosketConfig;
import fr.bosketball.config.MotdText;
import fr.bosketball.util.Msg;
import fr.bosketball.util.Perms;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.util.CachedServerIcon;

public final class MotdManager implements Listener {

    /**
     * Round 29 - the banner has one copy per server state instead of a single rotating one:
     * {@link #ONLINE} while the server is open, {@link #MAINTENANCE} while it is closed for works
     * ({@code motd.maintenance-mode}), and {@link #CLOSED} during a stop / restart.
     */
    public enum State {
        ONLINE,
        STARTING,
        MAINTENANCE,
        CLOSED
    }

    public static final int MOTD_LINE_WIDTH_PX = 268;
    private static final int SPACE_ADVANCE_PX = 4;

    private final BosketBallPlugin plugin;
    private final AtomicInteger rotationCursor = new AtomicInteger(0);
    private volatile CachedServerIcon cachedIcon;

    public MotdManager(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public void reloadIcon() {
        if (plugin == null) {
            return;
        }
        BosketConfig.Motd cfg = plugin.settings().motd();
        try {
            File serverRoot = plugin.getDataFolder().getAbsoluteFile().getParentFile();
            if (serverRoot != null) {
                serverRoot = serverRoot.getParentFile();
            }
            File rootIcon = serverRoot != null ? new File(serverRoot, "server-icon.png") : null;
            if (cfg.autoInstallServerIcon() && rootIcon != null && !rootIcon.exists()) {
                try (InputStream in = plugin.getResource("server-icon.png")) {
                    if (in != null) {
                        Files.copy(in, rootIcon.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        plugin.getLogger().info("Installed 1:1 64x64 BosketBall server-icon.png in server root.");
                    }
                }
            }
            if (Bukkit.getServer() != null) {
                if (rootIcon != null && rootIcon.exists()) {
                    this.cachedIcon = Bukkit.loadServerIcon(rootIcon);
                } else {
                    try (InputStream in = plugin.getResource("server-icon.png")) {
                        if (in != null) {
                            BufferedImage img = ImageIO.read(in);
                            if (img != null) {
                                this.cachedIcon = Bukkit.loadServerIcon(img);
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not cache server-icon.png: " + t.getMessage());
        }
    }

    /** The version string of a closed state: the square mark, never a changelog line. */
    public static String versionString(State state) {
        return switch (state) {
            case MAINTENANCE -> "\u00a7e\u25aa Maintenance";
            case CLOSED -> "\u00a77\u25aa Restarting";
            case STARTING -> "\u00a76\u25aa Starting up";
            default -> "";
        };
    }

    /**
     * The whole hover list as data, so the ping payload can be measured without a server. Every row
     * uses the house mark and stays under {@link ScreenBounds#PING_SAMPLE_CHARS}; no domain, no
     * patch notes, no marketing. Live counts are the one thing a returning player actually wants.
     */
    public static List<String> listedPlayers(State state, int inMatch, int inQueue) {
        List<String> rows = new ArrayList<>();
        rows.add("\u00a76\u00a7l\u25aa BOSKETBALL \u25aa");
        switch (state) {
            case MAINTENANCE -> {
                rows.add("\u00a77\u25aa Courts getting a fresh coat");
                rows.add("\u00a77\u25aa Back on the court soon");
                rows.add("\u00a77\u25aa Sideline: \u00a7f/discord");
            }
            case CLOSED -> {
                rows.add("\u00a77\u25aa Server restarting");
                rows.add("\u00a77\u25aa See you in a moment");
            }
            case STARTING -> {
                rows.add("\u00a77\u25aa Lights coming on");
                rows.add("\u00a77\u25aa Courts being prepared");
                rows.add("\u00a77\u25aa Joins open in a few seconds");
            }
            default -> {
                rows.add("\u00a77\u25aa On court: \u00a7f" + inMatch);
                rows.add("\u00a77\u25aa In queue: \u00a7f" + inQueue);
                rows.add("\u00a77\u25aa Sideline: \u00a7f/party");
            }
        }
        return rows;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onServerListPing(PaperServerListPingEvent event) {
        BosketConfig.Motd cfg = plugin.settings().motd();
        if (!cfg.enabled()) {
            return;
        }

        State state = currentState(cfg);
        event.motd(buildMotdComponent(cfg, state));

        if (cfg.maxPlayersDisplay() > 0) {
            event.setMaxPlayers(cfg.maxPlayersDisplay());
        }

        if (state != State.ONLINE) {
            event.setVersion(versionString(state));
            event.setProtocolVersion(-1);
        }

        try {
            var sample = event.getListedPlayers();
            sample.clear();
            int inMatch = plugin.gameManager() != null ? plugin.gameManager().activePlayerCount() : 0;
            int inQueue = plugin.gameManager() != null ? plugin.gameManager().queuedCount() : 0;
            List<String> rows = listedPlayers(state, inMatch, inQueue);
            if (!ScreenBounds.fitsPingSample(rows)) {
                rows = listedPlayers(State.ONLINE, 0, 0);
            }
            for (String row : rows) {
                sample.add(new PaperServerListPingEvent.ListedPlayerInfo(row,
                    UUID.nameUUIDFromBytes(row.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            }
        } catch (Throwable ignored) {
        }

        CachedServerIcon icon = this.cachedIcon;
        if (icon != null) {
            try {
                event.setServerIcon(icon);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Maintenance gate.
     *
     * <p>{@code PlayerLoginEvent} is deprecated in favour of {@code PlayerConnectionValidateLoginEvent},
     * but that event only exposes the connection: the bypass check below reads Bukkit permissions
     * ({@code bosketball.staff} / {@code bosketball.build}), which cannot be resolved before the
     * {@link Player} exists. Kicking at join instead would let the player see the world for a tick
     * and would fire every join listener, so the deprecated event stays, deliberately: it is still
     * the only pre-join hook that hands us a {@code Player}.
     */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (plugin == null) {
            return;
        }
        boolean startingUp = plugin.isStartingUp();
        if (!startingUp && !plugin.settings().motd().maintenanceMode()) {
            return;
        }
        Player player = event.getPlayer();

        if (plugin.permissionManager() != null) {
            plugin.permissionManager().applyPermissions(player);
        }
        if (canBypassMaintenance(player)) {
            return;
        }
        // Round 40 — the boot window gets its own screen: a player who reconnects the second the
        // server comes back should read "starting up", not a lobby that is still being pasted.
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER,
            startingUp ? ServerScreens.starting(plugin, player) : ServerScreens.maintenance(plugin));
    }

    /**
     * Round 28: the vanilla "Server closed" screen is replaced by the branded shutdown screen, so a
     * restart / stop looks like the rest of the server instead of a red wall of text.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerKick(org.bukkit.event.player.PlayerKickEvent event) {
        if (plugin == null || event == null || event.getPlayer() == null) {
            return;
        }
        if (!ServerScreens.isServerClosing(event.reason())) {
            return;
        }
        event.reason(ServerScreens.shutdown(plugin, event.getPlayer()));
    }

    public static boolean canBypassMaintenance(Player player) {
        if (player == null) {
            return false;
        }
        if (Perms.staff(player) || Perms.build(player)) {
            return true;
        }
        var plug = BosketBallPlugin.getInstance();
        if (plug == null || plug.settings() == null) {
            return false;
        }
        return isWhitelistedForMaintenance(player.getName(), player.getUniqueId(),
            plug.settings().motd().maintenanceWhitelist());
    }

    public static boolean isWhitelistedForMaintenance(String name, java.util.UUID id, java.util.List<String> whitelist) {
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        for (String raw : whitelist) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String entry = raw.trim();
            if (name != null && entry.equalsIgnoreCase(name)) {
                return true;
            }
            if (id != null && entry.equalsIgnoreCase(id.toString())) {
                return true;
            }
        }
        return false;
    }

    /** Which copy the banner must use right now. */
    public State currentState(BosketConfig.Motd cfg) {
        return resolveState(plugin != null && plugin.isShuttingDown(),
            plugin != null && plugin.isStartingUp(),
            cfg != null && cfg.maintenanceMode());
    }

    /**
     * Pure resolution of the banner state, so the four cases are unit-tested without a server.
     *
     * <p>Order matters: a stopping server always wins (it will not come back immediately), then a
     * booting one, then maintenance, and finally the normal banner.</p>
     */
    public static State resolveState(boolean shuttingDown, boolean startingUp, boolean maintenance) {
        if (shuttingDown) {
            return State.CLOSED;
        }
        if (startingUp) {
            return State.STARTING;
        }
        if (maintenance) {
            return State.MAINTENANCE;
        }
        return State.ONLINE;
    }

    public Component buildMotdComponent(BosketConfig.Motd cfg) {
        return buildMotdComponent(cfg, currentState(cfg));
    }

    public Component buildMotdComponent(BosketConfig.Motd cfg, State state) {
        String rawHeader;
        String rawSub;
        switch (state) {
            case MAINTENANCE -> {
                rawHeader = cfg.maintenanceHeader();
                rawSub = cfg.maintenanceSubtitle();
            }
            case CLOSED -> {
                rawHeader = cfg.closedHeader();
                rawSub = cfg.closedSubtitle();
            }
            case STARTING -> {
                rawHeader = cfg.startingHeader();
                rawSub = cfg.startingSubtitle();
            }
            default -> {
                rawHeader = cfg.header();
                List<String> subs = cfg.subtitles();
                int idx = Math.floorMod(rotationCursor.getAndIncrement(), Math.max(1, subs.size()));
                rawSub = subs.get(idx);
            }
        }
        // A config written by hand can be longer than the ping payload allows. Fall back to the
        // shipped banner rather than sending a description the client will cut in half.
        if (!ScreenBounds.fitsMotd(rawHeader, rawSub)
            || !ScreenBounds.isMiniMessageSafe(rawHeader) || !ScreenBounds.isMiniMessageSafe(rawSub)) {
            rawHeader = MotdText.DEFAULT_HEADER;
            rawSub = MotdText.DEFAULT_SUBTITLES.get(0);
        }
        Component line1 = centerLine(Msg.parse(rawHeader));
        Component line2 = centerLine(Msg.parse(rawSub));

        return Component.text()
            .append(line1)
            .append(Component.newline())
            .append(line2)
            .asComponent();
    }

    public static Component centerLine(Component line) {
        if (line == null) {
            return Component.empty();
        }
        int contentPx = measureComponentPixels(line, false);
        int leftMarginPx = Math.max(0, (MOTD_LINE_WIDTH_PX - contentPx) / 2);
        int spaces = leftMarginPx / SPACE_ADVANCE_PX;
        if (spaces <= 0) {
            return line;
        }
        return Component.text(" ".repeat(spaces))
            .decoration(TextDecoration.BOLD, false)
            .append(line);
    }

    public static int measureComponentPixels(Component comp, boolean inheritedBold) {
        if (comp == null) {
            return 0;
        }
        TextDecoration.State boldState = comp.decoration(TextDecoration.BOLD);
        boolean bold = boldState == TextDecoration.State.TRUE
            || (boldState == TextDecoration.State.NOT_SET && inheritedBold);

        int total = 0;
        if (comp instanceof TextComponent tc) {
            String content = tc.content();
            for (int i = 0; i < content.length(); ) {
                int cp = content.codePointAt(i);
                i += Character.charCount(cp);
                total += charAdvancePx(cp, bold);
            }
        }
        for (Component child : comp.children()) {
            total += measureComponentPixels(child, bold);
        }
        return total;
    }

    public static int charAdvancePx(int cp, boolean bold) {
        if (cp == ' ') {
            return bold ? 5 : 4;
        }
        int base = switch (cp) {
            case '!', ',', '.', ':', ';', '|', 'i', '\'', '¡' -> 2;
            case 'l', '`', 'ɪ' -> 3;
            case 'I', 't', '[', ']', '"', '*', '(', ')', '{', '}', '<', '>' -> 4;
            case 'f', 'k' -> 5;
            case '@', '~', '', '⚠', '', 0x1F525 -> 7;
            case '▪', '▸' -> 4;
            case '•', '·' -> 3;
            default -> 6;
        };
        return bold ? base + 1 : base;
    }
}
