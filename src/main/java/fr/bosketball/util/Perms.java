package fr.bosketball.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Central registry of every permission the plugin understands.
 *
 * <p>Two invariants are enforced by the test suite:
 * <ul>
 *   <li>every node declared here is declared in {@code plugin.yml} with the same default, so it is
 *       visible in {@code /lp verbose} and in permission listings;</li>
 *   <li>every node is checked through {@link #has(CommandSender, String)}, which walks the dotted
 *       hierarchy — granting {@code bosketball.admin} therefore grants {@code bosketball.admin.elo}
 *       even on a bare Bukkit permission attachment.</li>
 * </ul>
 */
public final class Perms {

    /** Umbrella node: everything a server administrator can do. */
    public static final String ADMIN = "bosketball.admin";
    /** Umbrella node: every moderation tool. */
    public static final String STAFF = "bosketball.staff";
    /** Arena / schematic authoring. */
    public static final String MAP = "bosketball.map";
    /** Build mode, FAWE & Axiom. */
    public static final String BUILD = "bosketball.build";
    /** Base gameplay (default true). */
    public static final String PLAY = "bosketball.play";

    public static final String COINS = "bosketball.admin.coins";
    public static final String ELO = "bosketball.admin.elo";
    public static final String RANK = "bosketball.admin.rank";
    public static final String TAGS = "bosketball.admin.tags";
    public static final String SEASON = "bosketball.admin.season";
    public static final String HOLO = "bosketball.admin.holo";
    public static final String ARENA = "bosketball.admin.arena";
    public static final String RELOAD = "bosketball.admin.reload";
    public static final String MAINTENANCE = "bosketball.admin.maintenance";
    public static final String MUTECHAT = "bosketball.admin.mutechat";
    public static final String STRESS = "bosketball.admin.stress";
    public static final String BACKUP = "bosketball.admin.backup";
    public static final String AUDIT = "bosketball.admin.audit";
    public static final String DISPLAYS = "bosketball.admin.displays";
    public static final String ITEMS = "bosketball.admin.items";
    public static final String MATCH = "bosketball.admin.match";
    public static final String PROFILER = "bosketball.admin.profiler";
    public static final String PERMS = "bosketball.admin.perms";

    public static final String STAFF_REPORTS = "bosketball.staff.reports";
    public static final String STAFF_WARN = "bosketball.staff.warn";
    public static final String STAFF_MUTE = "bosketball.staff.mute";
    public static final String STAFF_VANISH = "bosketball.staff.vanish";
    public static final String STAFF_FREEZE = "bosketball.staff.freeze";
    public static final String STAFF_SPECTATE = "bosketball.staff.spectate";
    public static final String STAFF_MATCH = "bosketball.staff.match";
    public static final String STAFF_BYPASS = "bosketball.staff.bypass";

    public static final String COSMETICS_ALL = "bosketball.cosmetics.all";
    public static final String TAGS_ALL = "bosketball.tags.all";
    public static final String VIP = "bosketball.vip";
    public static final String FOUNDER = "bosketball.founder";

    /**
     * A declared node. {@code defaultMode} is the literal {@code default:} value written into
     * {@code plugin.yml}: {@code true} (everyone), {@code op} (operators) or {@code false}
     * (never granted by the server, only by a rank plugin).
     */
    public record NodeSpec(String id, String description, String defaultMode) {

        public boolean opOnly() {
            return "op".equals(defaultMode);
        }

    }

    private static final List<NodeSpec> NODES = List.of(
        new NodeSpec(PLAY, "Join queues, courts, tournaments and every player menu", "true"),
        new NodeSpec(BUILD, "Toggle /build: manual block edits with FAWE & Axiom", "op"),
        new NodeSpec(MAP, "Arena authoring: /bb map, /bb arena and the map editor", "op"),
        new NodeSpec(STAFF, "Umbrella node: every moderation tool (staff.*)", "op"),
        new NodeSpec(STAFF_REPORTS, "Read and resolve /reports", "op"),
        new NodeSpec(STAFF_WARN, "Warn, unwarn and list warnings", "op"),
        new NodeSpec(STAFF_MUTE, "Mute chat, bypass the mute and talk while muted", "op"),
        new NodeSpec(STAFF_VANISH, "Vanish from the tab list and the lobby", "op"),
        new NodeSpec(STAFF_FREEZE, "Freeze / unfreeze a player and freeze a whole match", "op"),
        new NodeSpec(STAFF_SPECTATE, "Spectate any court without playing", "op"),
        new NodeSpec(STAFF_MATCH, "Supervise matches: force-start, stop and re-queue", "op"),
        new NodeSpec(STAFF_BYPASS, "Bypass lobby protection, illegal-item filters and build locks", "op"),
        new NodeSpec(ADMIN, "Umbrella node: full BosketBall administration (admin.*)", "op"),
        new NodeSpec(COINS, "Give, take or set Coins on any profile", "op"),
        new NodeSpec(ELO, "Give, take or set ELO on any profile", "op"),
        new NodeSpec(RANK, "Assign staff ranks (/bb rank)", "op"),
        new NodeSpec(TAGS, "Grant or revoke any chat tag", "op"),
        new NodeSpec(SEASON, "Start, end or reconfigure the ranked season", "op"),
        new NodeSpec(HOLO, "Place, move or remove holograms and NPC stations", "op"),
        new NodeSpec(ARENA, "Rebuild, repaste, delete or teleport to arenas", "op"),
        new NodeSpec(RELOAD, "Reload the configuration and push the resource pack", "op"),
        new NodeSpec(MAINTENANCE, "Toggle maintenance mode (admins keep access)", "op"),
        new NodeSpec(MUTECHAT, "Mute or unmute the global chat", "op"),
        new NodeSpec(STRESS, "Run the internal load simulation (/bb stress)", "op"),
        new NodeSpec(BACKUP, "Trigger an immediate data backup", "op"),
        new NodeSpec(AUDIT, "Open the diagnostic report (/bb audit)", "op"),
        new NodeSpec(DISPLAYS, "Inspect and rebuild HUDs, sidebars and displays", "op"),
        new NodeSpec(ITEMS, "Spawn balls, dunks, tags and admin items, bypass cosmetic locks", "op"),
        new NodeSpec(MATCH, "Force-start, stop, save or fill matches with bots", "op"),
        new NodeSpec(PROFILER, "Drive the internal tick profiler", "op"),
        new NodeSpec(PERMS, "Manage groups and permissions (/perm)", "op"),
        new NodeSpec(COSMETICS_ALL, "Every basketball skin and signature dunk unlocked", "false"),
        new NodeSpec(TAGS_ALL, "Every chat tag unlocked (except founder-only ones)", "false"),
        new NodeSpec(VIP, "VIP tier marker used by the rank manager", "false"),
        new NodeSpec(FOUNDER, "Founder-only cosmetics, tags and badge", "false")
    );

    private Perms() {
    }

    public static List<NodeSpec> nodes() {
        return NODES;
    }

    public static List<String> nodeIds() {
        List<String> out = new ArrayList<>(NODES.size());
        for (NodeSpec n : NODES) {
            out.add(n.id());
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Resolves a node against a sender: console and OPs always pass, then the exact node, then every
     * dotted parent (with and without the {@code .*} wildcard). An explicit deny on the node itself
     * wins over any inherited grant, exactly like a permissions plugin.
     */
    public static boolean has(CommandSender sender, String node) {
        if (sender == null || node == null || node.isBlank()) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (player.isOp()) {
            return true;
        }
        if (player.isPermissionSet(node)) {
            return player.hasPermission(node);
        }
        if (player.hasPermission(node)) {
            return true;
        }
        int idx = node.length();
        while ((idx = node.lastIndexOf('.', idx - 1)) > 0) {
            String parent = node.substring(0, idx);
            if (player.isPermissionSet(parent)) {
                return player.hasPermission(parent);
            }
            if (player.hasPermission(parent) || player.hasPermission(parent + ".*")) {
                return true;
            }
        }
        return false;
    }

    /** Staff tools: admins inherit them through the dotted hierarchy. */
    public static boolean staff(CommandSender sender) {
        return has(sender, STAFF);
    }

    public static boolean admin(CommandSender sender) {
        return has(sender, ADMIN);
    }

    public static boolean map(CommandSender sender) {
        return has(sender, MAP);
    }

    public static boolean build(CommandSender sender) {
        return has(sender, BUILD);
    }

    /**
     * Granular gate with a uniform message. Returns {@code true} when the sender may proceed;
     * otherwise the refusal is already sent to them.
     */
    public static boolean check(CommandSender sender, String node, String what) {
        if (has(sender, node)) {
            return true;
        }
        Msg.send(sender, Msg.error("You need <white>" + node + "</white> to " + what + "."));
        return false;
    }

    /** Nodes that inherit from the given umbrella node (used by the help cards and /perm nodes). */
    /** Case-insensitive node lookup, tolerant of the leading {@code -} negation marker. */
    public static String normalize(String node) {
        if (node == null) {
            return "";
        }
        String trimmed = node.trim();
        if (trimmed.startsWith("-")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
