package fr.bosketball.moderation;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.config.AtomicYamlWriter;
import fr.bosketball.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WarnService {

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

    private final Map<UUID, List<Warn>> warnings = new ConcurrentHashMap<>();

    public WarnService(BosketBallPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "warnings.yml");
        load();
    }

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

        Msg.send(target, Msg.warn("<bold>⚠ WARNING</bold> <#94A3B8>—</#94A3B8> <#F8FAFC>" + Msg.escapeMini(cleanReason) + "</#F8FAFC>"
            + " <#64748B>·</#64748B> <#94A3B8>Total:</#94A3B8> <#F8FAFC>" + count(target.getUniqueId()) + "</#F8FAFC>"));
        Msg.send(target, "<#94A3B8>Please follow the server rules. Too many warnings may lead to a sanction.</#94A3B8>");
        if (plugin.integration() != null) {
            plugin.integration().onModeration(target.getName(), "warn", cleanReason, count(target.getUniqueId()));
        }
        if (plugin.reportService() != null) {
            plugin.reportService().logSanctionBy(entry.staffName(), target.getName(), "WARN", cleanReason, 0L);
        }
        return entry;
    }

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

    public int clear(UUID target) {
        List<Warn> list = warnings.remove(target);
        if (list == null) {
            return 0;
        }
        saveAsync();
        return list.size();
    }

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

    public List<UUID> warnedPlayers() {
        List<UUID> ids = new ArrayList<>(warnings.keySet());
        ids.sort(Comparator.comparingInt((UUID id) -> -count(id)));
        return ids;
    }

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
        AtomicYamlWriter.writeYamlAtomically(file, null, yaml, plugin.getLogger());
    }
}
