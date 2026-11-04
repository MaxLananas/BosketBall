package fr.bosketball.server;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.config.AtomicYamlWriter;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

public final class PermissionManager implements Listener {

    private static final String HEADER = """
        # ===========================================================================
        #   BOSKETBALL — STANDALONE PERMISSIONS & STAFF GROUPS (permissions.yml)
        #   Built-in permission engine with out-of-the-box FAWE & Axiom support!
        #   Prefix a permission with '-' to explicitly negate it (e.g. -axiom.use).
        #   Manage in-game with: /perm user <player> group set <group>
        #                        /perm user <player> add <node>
        #                        /perm group <group> add <node>
        # ===========================================================================
        """;

    public record GroupDefinition(
        String id,
        List<String> parents,
        List<String> permissions
    ) {
    }

    public static final class UserPermissionEntry {
        private String primaryGroup = "default";
        private final Set<String> permissions = new LinkedHashSet<>();

        public String primaryGroup() {
            return primaryGroup;
        }

        public void setPrimaryGroup(String group) {
            this.primaryGroup = (group == null || group.isBlank()) ? "default" : group.toLowerCase(Locale.ROOT);
        }

        public Set<String> permissions() {
            return permissions;
        }
    }

    private final BosketBallPlugin plugin;
    private final File storageFile;
    private final Logger logger;

    private final Map<String, GroupDefinition> groups = new ConcurrentHashMap<>();
    private final Map<UUID, UserPermissionEntry> users = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> activeAttachments = new ConcurrentHashMap<>();

    public PermissionManager(BosketBallPlugin plugin) {
        this(plugin, plugin.getDataFolder(), plugin.getLogger());
    }

    public PermissionManager(BosketBallPlugin plugin, File dataFolder, Logger logger) {
        this.plugin = plugin;
        this.storageFile = new File(dataFolder, "permissions.yml");
        this.logger = logger;
    }

    public void loadOrCreate() {
        groups.clear();
        users.clear();

        seedDefaultGroups();

        if (!storageFile.exists()) {
            save();
            refreshAllOnlinePlayers();
            return;
        }

        YamlConfiguration yml = AtomicYamlWriter.loadSafe(storageFile, logger);
        ConfigurationSection grpSec = yml.getConfigurationSection("groups");
        if (grpSec != null) {
            for (String gKey : grpSec.getKeys(false)) {
                ConfigurationSection g = grpSec.getConfigurationSection(gKey);
                if (g == null) {
                    continue;
                }
                String gid = gKey.toLowerCase(Locale.ROOT);
                List<String> parents = g.getStringList("inherit").stream()
                    .map(s -> s.toLowerCase(Locale.ROOT).trim())
                    .filter(s -> !s.isEmpty())
                    .toList();
                List<String> perms = g.getStringList("permissions").stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
                groups.put(gid, new GroupDefinition(gid, parents, perms));
            }
        }

        ConfigurationSection usrSec = yml.getConfigurationSection("users");
        if (usrSec != null) {
            for (String uuidStr : usrSec.getKeys(false)) {
                try {
                    UUID uid = UUID.fromString(uuidStr);
                    ConfigurationSection u = usrSec.getConfigurationSection(uuidStr);
                    if (u == null) {
                        continue;
                    }
                    UserPermissionEntry entry = new UserPermissionEntry();
                    entry.setPrimaryGroup(u.getString("group", "default"));
                    for (String p : u.getStringList("permissions")) {
                        if (p != null && !p.isBlank()) {
                            entry.permissions().add(p.trim());
                        }
                    }
                    users.put(uid, entry);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        refreshAllOnlinePlayers();
    }

    private void seedDefaultGroups() {
        groups.put("default", new GroupDefinition(
            "default",
            List.of(),
            List.of(
                "bosketball.play",
                "-axiom.*",
                "-axiom.use",
                "-worldedit.*",
                "-fawe.*"
            )
        ));
        groups.put("vip", new GroupDefinition(
            "vip",
            List.of("default"),
            List.of("bosketball.vip")
        ));
        groups.put("builder", new GroupDefinition(
            "builder",
            List.of("default"),
            List.of(
                "bosketball.build",
                "worldedit.*",
                "fawe.*",
                "fawe.bypass",
                "fawe.admin",
                "axiom.*",
                "axiom.use",
                "axiom.editor.*",
                "axiom.build.*",
                "minecraft.command.gamemode",
                "minecraft.command.teleport"
            )
        ));
        groups.put("mod", new GroupDefinition(
            "mod",
            List.of("vip"),
            List.of(
                "bosketball.staff",
                "minecraft.command.kick",
                "minecraft.command.ban",
                "minecraft.command.pardon",
                "minecraft.command.teleport"
            )
        ));
        groups.put("admin", new GroupDefinition(
            "admin",
            List.of("builder", "mod"),
            List.of(
                "bosketball.admin",
                "bosketball.tags.all",
                "minecraft.command.*",
                "bukkit.command.*",
                "paper.command.*"
            )
        ));
    }

    public synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();
        List<String> orderedGroups = new ArrayList<>(groups.keySet());
        orderedGroups.sort((a, b) -> {
            List<String> canonical = List.of("default", "vip", "builder", "mod", "admin");
            int ia = canonical.indexOf(a);
            int ib = canonical.indexOf(b);
            if (ia != -1 && ib != -1) return Integer.compare(ia, ib);
            if (ia != -1) return -1;
            if (ib != -1) return 1;
            return a.compareTo(b);
        });

        for (String gid : orderedGroups) {
            GroupDefinition def = groups.get(gid);
            if (def == null) {
                continue;
            }
            ConfigurationSection gs = yml.createSection("groups." + gid);
            gs.set("inherit", def.parents());
            gs.set("permissions", def.permissions());
        }

        for (Map.Entry<UUID, UserPermissionEntry> e : users.entrySet()) {
            UserPermissionEntry u = e.getValue();
            if ("default".equals(u.primaryGroup()) && u.permissions().isEmpty()) {
                continue;
            }
            ConfigurationSection us = yml.createSection("users." + e.getKey());
            if (plugin != null && plugin.statsManager() != null) {
                PlayerProfile prof = plugin.statsManager().getIfPresent(e.getKey());
                if (prof != null) {
                    us.set("name", prof.lastKnownName());
                }
            }
            us.set("group", u.primaryGroup());
            us.set("permissions", new ArrayList<>(u.permissions()));
        }

        AtomicYamlWriter.writeYamlAtomically(storageFile, HEADER, yml, logger);
    }

    public Set<String> groupNames() {
        return Collections.unmodifiableSet(groups.keySet());
    }

    public GroupDefinition getGroup(String id) {
        return id == null ? null : groups.get(id.toLowerCase(Locale.ROOT));
    }

    public String getUserGroup(UUID uuid) {
        UserPermissionEntry entry = users.get(uuid);
        if (entry != null && groups.containsKey(entry.primaryGroup())) {
            return entry.primaryGroup();
        }

        if (plugin != null && plugin.statsManager() != null) {
            PlayerProfile prof = plugin.statsManager().getIfPresent(uuid);
            if (prof != null) {
                if (prof.effectiveRank() == Rank.ADMIN) {
                    return "admin";
                }
                if (prof.effectiveRank() == Rank.REFEREE) {
                    return "mod";
                }
            }
        }
        return "default";
    }

    public boolean setUserGroup(UUID uuid, String groupId) {
        String norm = groupId.toLowerCase(Locale.ROOT);
        if (!groups.containsKey(norm)) {
            return false;
        }
        UserPermissionEntry entry = users.computeIfAbsent(uuid, k -> new UserPermissionEntry());
        entry.setPrimaryGroup(norm);
        save();
        refreshOnlinePlayer(uuid);
        return true;
    }

    public void addUserPermission(UUID uuid, String node) {
        UserPermissionEntry entry = users.computeIfAbsent(uuid, k -> new UserPermissionEntry());
        entry.permissions().remove("-" + node);
        entry.permissions().add(node.trim());
        save();
        refreshOnlinePlayer(uuid);
    }

    public boolean removeUserPermission(UUID uuid, String node) {
        UserPermissionEntry entry = users.get(uuid);
        if (entry == null) {
            return false;
        }
        boolean removed = entry.permissions().remove(node.trim()) | entry.permissions().remove("-" + node.trim());
        if (removed) {
            save();
            refreshOnlinePlayer(uuid);
        }
        return removed;
    }

    public boolean createGroup(String groupId) {
        String norm = groupId.toLowerCase(Locale.ROOT);
        if (groups.containsKey(norm)) {
            return false;
        }
        groups.put(norm, new GroupDefinition(norm, List.of("default"), List.of()));
        save();
        return true;
    }

    public boolean addGroupPermission(String groupId, String node) {
        String norm = groupId.toLowerCase(Locale.ROOT);
        GroupDefinition existing = groups.get(norm);
        if (existing == null) {
            return false;
        }
        List<String> updated = new ArrayList<>(existing.permissions());
        if (!updated.contains(node.trim())) {
            updated.add(node.trim());
        }
        groups.put(norm, new GroupDefinition(norm, existing.parents(), updated));
        save();
        refreshAllOnlinePlayers();
        return true;
    }

    public boolean removeGroupPermission(String groupId, String node) {
        String norm = groupId.toLowerCase(Locale.ROOT);
        GroupDefinition existing = groups.get(norm);
        if (existing == null) {
            return false;
        }
        List<String> updated = new ArrayList<>(existing.permissions());
        if (!updated.remove(node.trim())) {
            return false;
        }
        groups.put(norm, new GroupDefinition(norm, existing.parents(), updated));
        save();
        refreshAllOnlinePlayers();
        return true;
    }

    public Map<String, Boolean> resolveEffectivePermissions(UUID uuid) {
        Map<String, Boolean> resolved = new LinkedHashMap<>();
        String rootGroup = getUserGroup(uuid);
        collectGroupPermissions(rootGroup, resolved, new HashSet<>());

        if (plugin != null && plugin.statsManager() != null) {
            PlayerProfile prof = plugin.statsManager().getIfPresent(uuid);
            if (prof != null) {
                if (prof.effectiveRank() == Rank.ADMIN) {
                    collectGroupPermissions("admin", resolved, new HashSet<>());
                } else if (prof.effectiveRank() == Rank.REFEREE) {
                    collectGroupPermissions("mod", resolved, new HashSet<>());
                }
            }
        }

        UserPermissionEntry userEntry = users.get(uuid);
        if (userEntry != null) {
            for (String raw : userEntry.permissions()) {
                applyNode(resolved, raw);
            }
        }
        return resolved;
    }

    private void collectGroupPermissions(String groupId, Map<String, Boolean> out, Set<String> visited) {
        if (groupId == null || !visited.add(groupId)) {
            return;
        }
        GroupDefinition def = groups.get(groupId);
        if (def == null) {
            return;
        }
        for (String parent : def.parents()) {
            collectGroupPermissions(parent, out, visited);
        }
        for (String raw : def.permissions()) {
            applyNode(out, raw);
        }
    }

    private static void applyNode(Map<String, Boolean> out, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String trimmed = raw.trim();
        boolean allow = !trimmed.startsWith("-");
        String key = allow ? trimmed : trimmed.substring(1).trim();
        if (key.isEmpty()) {
            return;
        }
        out.put(key, allow);

        if (key.endsWith(".*")) {
            String base = key.substring(0, key.length() - 2);
            out.put(base, allow);
        }
    }

    public void applyPermissions(Player player) {
        if (player == null || plugin == null) {
            return;
        }
        PermissionAttachment old = activeAttachments.remove(player.getUniqueId());
        if (old != null) {
            try {
                player.removeAttachment(old);
            } catch (Throwable ignored) {
            }
        }

        PermissionAttachment attachment = player.addAttachment(plugin);
        Map<String, Boolean> effective = resolveEffectivePermissions(player.getUniqueId());
        for (Map.Entry<String, Boolean> e : effective.entrySet()) {
            attachment.setPermission(e.getKey(), e.getValue());
        }
        activeAttachments.put(player.getUniqueId(), attachment);
        try {
            player.recalculatePermissions();
            player.updateCommands();
        } catch (Throwable ignored) {
        }
    }

    public void refreshOnlinePlayer(UUID uuid) {
        if (Bukkit.getServer() == null || uuid == null) {
            return;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            applyPermissions(online);
        }
    }

    public void refreshAllOnlinePlayers() {
        if (Bukkit.getServer() == null) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            applyPermissions(p);
        }
    }

    public void clearAllAttachments() {
        if (Bukkit.getServer() == null) {
            activeAttachments.clear();
            return;
        }
        for (Map.Entry<UUID, PermissionAttachment> e : activeAttachments.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                try {
                    p.removeAttachment(e.getValue());
                } catch (Throwable ignored) {
                }
            }
        }
        activeAttachments.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        applyPermissions(event.getPlayer());
    }

    public void removePlayer(Player player) {
        if (player == null) {
            return;
        }
        PermissionAttachment old = activeAttachments.remove(player.getUniqueId());
        if (old != null) {
            try {
                player.removeAttachment(old);
            } catch (Throwable ignored) {
            }
        }
    }

    public void shutdown() {
        clearAllAttachments();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removePlayer(event.getPlayer());
    }
}
