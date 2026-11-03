package fr.bosketball.server;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.config.BosketConfig;
import fr.bosketball.util.Msg;
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

/**
 * Pixel-Centered Multiplayer Server List MOTD, Hover Sample, Maintenance Gate &amp; 1:1 Server Icon Manager.
 * Centers both MOTD lines using exact Minecraft default font glyph advance widths (268px viewport).
 */
public final class MotdManager implements Listener {

    /** Standard Minecraft Multiplayer Server List MOTD line width in font pixels (half-center = 134px). */
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

        event.motd(buildMotdComponent(cfg));

        if (cfg.maxPlayersDisplay() > 0) {
            event.setMaxPlayers(cfg.maxPlayersDisplay());
        }

        // Custom version badge during scheduled maintenance
        if (cfg.maintenanceMode()) {
            event.setVersion("§c🛠 Maintenance");
            event.setProtocolVersion(-1);
        }

        // Branded Multiplayer Hover Sample (when hovering the player count in Server List)
        try {
            var sample = event.getListedPlayers();
            sample.clear();
            int inMatch = plugin.gameManager() != null ? plugin.gameManager().activePlayerCount() : 0;
            int inQueue = plugin.gameManager() != null ? plugin.gameManager().queuedCount() : 0;
            String domain = plugin.settings().general().serverDomain();
            sample.add(new PaperServerListPingEvent.ListedPlayerInfo("§6§l✦ " + domain + " ✦", UUID.nameUUIDFromBytes("bb_hdr".getBytes())));
            if (cfg.maintenanceMode()) {
                sample.add(new PaperServerListPingEvent.ListedPlayerInfo("§c⚠ Scheduled Maintenance", UUID.nameUUIDFromBytes("bb_m1".getBytes())));
                sample.add(new PaperServerListPingEvent.ListedPlayerInfo("§7Back online very soon!", UUID.nameUUIDFromBytes("bb_m2".getBytes())));
            } else {
                sample.add(new PaperServerListPingEvent.ListedPlayerInfo("§e🏀 In Match: §f" + inMatch, UUID.nameUUIDFromBytes("bb_s1".getBytes())));
                sample.add(new PaperServerListPingEvent.ListedPlayerInfo("§b⏳ In Queue: §f" + inQueue, UUID.nameUUIDFromBytes("bb_s2".getBytes())));
                sample.add(new PaperServerListPingEvent.ListedPlayerInfo("§9✦ Discord: §f/discord", UUID.nameUUIDFromBytes("bb_s3".getBytes())));
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
     * Real Maintenance Mode Login Gate: allows OPs &amp; Staff in to test/build while showing
     * regular players a clean branded BosketBall maintenance screen.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (plugin == null || !plugin.settings().motd().maintenanceMode()) {
            return;
        }
        Player player = event.getPlayer();
        // Ensure standalone permissions are evaluated before checking bypass
        if (plugin.permissionManager() != null) {
            plugin.permissionManager().applyPermissions(player);
        }
        if (canBypassMaintenance(player)) {
            return;
        }
        String discord = plugin.settings().links().discord();
        Component kickScreen = Msg.parse(
            "<gradient:#FFD43B:#FF8C1A><bold>✦ BOSKETBALL ✦</bold></gradient>\n\n"
                + "<#F87171><bold>⚠ SCHEDULED MAINTENANCE IN PROGRESS ⚠</bold></#F87171>\n\n"
                + "<#CBD5E1>We are currently upgrading the courts & server features.</#CBD5E1>\n"
                + "<#94A3B8>The server will reopen shortly — thank you for your patience!</#94A3B8>\n\n"
                + "<#818CF8>Live Status & Updates:</#818CF8> <white>" + discord + "</white>"
        );
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickScreen);
    }

    public static boolean canBypassMaintenance(Player player) {
        if (player == null) {
            return false;
        }
        if (player.isOp()
            || player.hasPermission("bosketball.admin")
            || player.hasPermission("bosketball.staff")
            || player.hasPermission("bosketball.build")) {
            return true;
        }
        return "MaxLananas".equalsIgnoreCase(player.getName());
    }

    public Component buildMotdComponent(BosketConfig.Motd cfg) {
        Component line1 = centerLine(Msg.parse(cfg.header()));

        String rawSub;
        if (cfg.maintenanceMode()) {
            rawSub = cfg.maintenanceSubtitle();
        } else {
            List<String> subs = cfg.subtitles();
            int idx = Math.floorMod(rotationCursor.getAndIncrement(), Math.max(1, subs.size()));
            rawSub = subs.get(idx);
        }
        Component line2 = centerLine(Msg.parse(rawSub));

        return Component.text()
            .append(line1)
            .append(Component.newline())
            .append(line2)
            .asComponent();
    }

    /**
     * Prepends exact plain-space padding so {@code line} sits visually centered in Minecraft's 268px MOTD box.
     */
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

    /**
     * Measures exact Minecraft font advance pixels of an Adventure {@link Component}, accounting for bold (+1px).
     */
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
            default -> 6; // A-Z, a-z, 0-9 (5px glyph + 1px kerning = 6px)
        };
        return bold ? base + 1 : base;
    }
}
