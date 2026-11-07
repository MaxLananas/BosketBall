package fr.bosketball.server;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.config.BosketConfig;
import fr.bosketball.util.Msg;
import fr.bosketball.util.Perms;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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

        if (state == State.MAINTENANCE) {
            event.setVersion("\u00a7e\u2726 Maintenance");
            event.setProtocolVersion(-1);
        } else if (state == State.CLOSED) {
            event.setVersion("\u00a77\u2726 Restarting");
            event.setProtocolVersion(-1);
        }

        try {
            var sample = event.getListedPlayers();
            sample.clear();
            // Round 29: the hover list never prints the domain either - the brand name is enough.
            sample.add(new PaperServerListPingEvent.ListedPlayerInfo(
                "\u00a76\u00a7l\u2726 BOSKETBALL \u2726", UUID.nameUUIDFromBytes("bb_hdr".getBytes())));
            switch (state) {
                case MAINTENANCE -> {
                    sample.add(new PaperServerListPingEvent.ListedPlayerInfo(
                        "\u00a7e\u25c6 Court under refit", UUID.nameUUIDFromBytes("bb_m1".getBytes())));
                    sample.add(new PaperServerListPingEvent.ListedPlayerInfo(
                        "\u00a77\u25c6 Back online very soon", UUID.nameUUIDFromBytes("bb_m2".getBytes())));
                    sample.add(new PaperServerListPingEvent.ListedPlayerInfo(
                        "\u00a79\u25c6 Updates: \u00a7f/discord", UUID.nameUUIDFromBytes("bb_m3".getBytes())));
                }
                case CLOSED -> {
                    sample.add(new PaperServerListPingEvent.ListedPlayerInfo(
                        "\u00a7c\u25c6 Server restarting", UUID.nameUUIDFromBytes("bb_c1".getBytes())));
                    sample.add(new PaperServerListPingEvent.ListedPlayerInfo(
                        "\u00a77\u25c6 See you in a moment", UUID.nameUUIDFromBytes("bb_c2".getBytes())));
                }
                default -> {
                    int inMatch = plugin.gameManager() != null ? plugin.gameManager().activePlayerCount() : 0;
                    int inQueue = plugin.gameManager() != null ? plugin.gameManager().queuedCount() : 0;
                    sample.add(new PaperServerListPingEvent.ListedPlayerInfo(
                        "\u00a7e\u25c6 In Match: \u00a7f" + inMatch, UUID.nameUUIDFromBytes("bb_s1".getBytes())));
                    sample.add(new PaperServerListPingEvent.ListedPlayerInfo(
                        "\u00a7b\u25c6 In Queue: \u00a7f" + inQueue, UUID.nameUUIDFromBytes("bb_s2".getBytes())));
                    sample.add(new PaperServerListPingEvent.ListedPlayerInfo(
                        "\u00a79\u25c6 Discord: \u00a7f/discord", UUID.nameUUIDFromBytes("bb_s3".getBytes())));
                }
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (plugin == null || !plugin.settings().motd().maintenanceMode()) {
            return;
        }
        Player player = event.getPlayer();

        if (plugin.permissionManager() != null) {
            plugin.permissionManager().applyPermissions(player);
        }
        if (canBypassMaintenance(player)) {
            return;
        }
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, ServerScreens.maintenance(plugin));
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
        if (plugin != null && plugin.isShuttingDown()) {
            return State.CLOSED;
        }
        if (cfg != null && cfg.maintenanceMode()) {
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
            default -> {
                rawHeader = cfg.header();
                List<String> subs = cfg.subtitles();
                int idx = Math.floorMod(rotationCursor.getAndIncrement(), Math.max(1, subs.size()));
                rawSub = subs.get(idx);
            }
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
            case '@', '~', '✦', '✧', '★', '☆', '⚡', '⚠', '⚔', 0x1F525 -> 7;
            case '•', '·' -> 3;
            default -> 6;
        };
        return bold ? base + 1 : base;
    }
}
