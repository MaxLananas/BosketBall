package fr.bosketball.moderation;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent warning ledger for staff.
 *
 * <p>Every warning is an individual, auditable record: who was warned, by whom, for what
 * and when. Unlike the chat-filter warn points (which are transient and auto-cleared on
 * ban), these entries stay until a staff member removes them with {@code /unwarn} or the
 * plaque de warnings GUI.</p>
 */
public final class WarnService {

    /** A single warning entry. */
    public record Warn(UUID target, String targetName, UUID staff, String staffName,
                       String reason, long atMillis, String id) {
        public String ageLabel(long now) {
            long min = Math.max(0L, now - atMillis) / 60_000L;
            if (min < 60) {
                return min + "m";
            }
            long hours = min / 60;
            return hours < 48 ? hours + "h" : (hours / 24) + "d";
        }
    }

    private final BosketBallPlugin plugin;
    private final File file;
    /** Target UUID → warnings, newest first. */
    private final Map<UUID, List<Warn>> warnings = new ConcurrentHashMap<>();

    public WarnService(BosketBallPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "warnings.yml");
        load();
    }

    // =========================================================================
    //  WRITE
    // =========================================================================

    /** Issues a warning. Returns the created entry, or {@code null} when the input is invalid. */
    public Warn warn(Player target, String staffName, String reason) {
        if (target == null) {
            return null;
        }
        String cleanReason = reason == null || reason.isBlank() ? "No reason provided" : reason.trim();
        Warn entry = new Warn(target.getUniqueId(), target.getName(),
            staffName == null || staffName.isBlank() ? null : Bukkit.getOfflinePlayer(staffName).getUniqueId(),
            staffName == null || staffName.isBlank() ? "STAFF" : staffName,
            cleanReason, System.currentTimeMillis(),
            Long.toHexString(System.currentTimeMillis()) + Integer.toHexString(cleanReason.hashCode()));
        add(entry);
        saveAsync();

        Msg.send(target, Msg.warn("<bold>⚠ WARNING</bold> <gray>—</gray> <white>" + Msg.escapeMini(cleanReason) + "</white>"
            + " <dark_gray>•</dark_gray> <gray>Total:</gray> <white>" + count(target.getUniqueId()) + "</white>"));
        Msg.send(target, "<gray>Please follow the server rules. Too many warnings may lead to a sanction.</gray>");
        if (plugin.integration() != null) {
            plugin.integration().onModeration(target.getName(), "warn", cleanReason, count(target.getUniqueId()));
        }
        if (plugin.reportService() != null) {
            plugin.reportService().logSanctionBy(entry.staffName(), target.getName(), "WARN", cleanReason, 0L);
        }
        return entry;
    }

    /** Removes the most recent warning of a player. Returns {@code true} if one was removed. */
    public boolean unwarnLast(UUID target) {
        List<Warn> list = warnings.get(target);
        if (list == null) {
            return false;
        }
        synchronized (list) {
            if (list.isEmpty()) {
                return false;
            }
            list.remove(0);
            if (list.isEmpty()) {
                warnings.remove(target);
            }
        }
        saveAsync();
        return true;
    }

    /** Removes one specific warning by id. */
    public boolean unwarn(UUID target, String warnId) {
        List<Warn> list = warnings.get(target);
        if (list == null || warnId == null) {
            return false;
        }
        synchronized (list) {
            boolean removed = list.removeIf(w -> w.id().equals(warnId));
            if (list.isEmpty()) {
                warnings.remove(target);
            }
            if (removed) {
                saveAsync();
            }
            return removed;
        }
    }

    /** Clears every warning of a player. */
    public int clear(UUID target) {
        List<Warn> list = warnings.remove(target);
        if (list == null) {
            return 0;
        }
        saveAsync();
        return list.size();
    }

    // =========================================================================
    //  READ
    // =========================================================================

    public List<Warn> of(UUID target) {
        List<Warn> list = warnings.get(target);
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    public int count(UUID target) {
        List<Warn> list = warnings.get(target);
        if (list == null) {
            return 0;
        }
        synchronized (list) {
            return list.size();
        }
    }

    /** Players with at least one warning, worst first. */
    public List<UUID> warnedPlayers() {
        List<UUID> ids = new ArrayList<>(warnings.keySet());
        ids.sort(Comparator.comparingInt((UUID id) -> -count(id)));
        return ids;
    }

    /** Resolves a target by online name, then offline database, then stored warning names. */
    public UUID resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            return cached.getUniqueId();
        }
        for (Map.Entry<UUID, List<Warn>> entry : warnings.entrySet()) {
            for (Warn w : entry.getValue()) {
                if (w.targetName() != null && w.targetName().equalsIgnoreCase(name)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /** Best-known display name for a warned UUID. */
    public String nameOf(UUID id) {
        if (id == null) {
            return "?";
        }
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        for (Warn w : of(id)) {
            if (w.targetName() != null) {
                return w.targetName();
            }
        }
        String cached = Bukkit.getOfflinePlayer(id).getName();
        return cached == null ? id.toString().substring(0, 8) : cached;
    }

    public int totalWarnings() {
        int total = 0;
        for (UUID id : warnings.keySet()) {
            total += count(id);
        }
        return total;
    }

    // =========================================================================
    //  PERSISTENCE
    // =========================================================================

    private void add(Warn entry) {
        List<Warn> list = warnings.computeIfAbsent(entry.target(), k -> new ArrayList<>());
        synchronized (list) {
            list.add(0, entry);
        }
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("warnings");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID target;
            try {
                target = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            List<Map<?, ?>> raw = root.getMapList(key);
            List<Warn> list = new ArrayList<>();
            for (Map<?, ?> map : raw) {
                list.add(new Warn(target,
                    str(map.get("target-name"), "?"),
                    null,
                    str(map.get("staff"), "STAFF"),
                    str(map.get("reason"), ""),
                    toLong(map.get("at"), System.currentTimeMillis()),
                    str(map.get("id"), Integer.toHexString(list.size()))));
            }
            if (!list.isEmpty()) {
                list.sort(Comparator.comparingLong(Warn::atMillis).reversed());
                warnings.put(target, list);
            }
        }
    }

    private static String str(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static long toLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, List<Warn>> entry : warnings.entrySet()) {
            List<Map<String, Object>> raw = new ArrayList<>();
            for (Warn warn : of(entry.getKey())) {
                raw.add(Map.of(
                    "target-name", warn.targetName() == null ? "?" : warn.targetName(),
                    "staff", warn.staffName() == null ? "STAFF" : warn.staffName(),
                    "reason", warn.reason() == null ? "" : warn.reason(),
                    "at", warn.atMillis(),
                    "id", warn.id()
                ));
            }
            yaml.set("warnings." + entry.getKey(), raw);
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save warnings.yml: " + ex.getMessage());
        }
    }
}
