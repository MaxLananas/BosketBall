package fr.bosketball.arena;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Registry & YAML Persistence for all BosketBall Stadium Arenas.
 */
public final class ArenaManager {

    private final File storageFile;
    private final Logger logger;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(File dataFolder, Logger logger) {
        this.storageFile = new File(dataFolder, "arenas.yml");
        this.logger = logger;
    }

    public Collection<Arena> allArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    public Arena getArena(String id) {
        if (id == null) {
            return null;
        }
        return arenas.get(id.toLowerCase());
    }

    public Arena firstArenaOrNull() {
        return arenas.values().stream().findFirst().orElse(null);
    }

    public void registerArena(Arena arena) {
        Arena existing = arenas.put(arena.id(), arena);
        if (existing != null && existing != arena) {
            existing.despawnDisplays();
        }
        save();
    }

    public boolean deleteArena(String id) {
        Arena removed = arenas.remove(id.toLowerCase());
        if (removed != null) {
            removed.despawnDisplays();
            save();
            return true;
        }
        return false;
    }

    public void load() {
        despawnAll();
        arenas.clear();
        if (!storageFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection sec = yaml.getConfigurationSection("arenas");
        if (sec == null) {
            return;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection a = sec.getConfigurationSection(id);
            if (a == null) {
                continue;
            }
            String displayName = a.getString("display-name", id);
            String worldName = a.getString("world", "world");
            double cx = a.getDouble("center-x", 0.5);
            double fy = a.getDouble("floor-y", 64.0);
            double cz = a.getDouble("center-z", 0.5);
            double hl = a.getDouble("half-length", CourtGeometry.DEFAULT_HALF_LENGTH);
            double hw = a.getDouble("half-width", CourtGeometry.DEFAULT_HALF_WIDTH);

            Arena arena = new Arena(id, displayName, worldName, new CourtGeometry(cx, fy, cz, hl, hw));
            arena.spawnDisplays();
            arenas.put(arena.id(), arena);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Arena arena : arenas.values()) {
            String base = "arenas." + arena.id() + ".";
            yaml.set(base + "display-name", arena.displayName());
            yaml.set(base + "world", arena.worldName());
            yaml.set(base + "center-x", arena.geometry().centerX());
            yaml.set(base + "floor-y", arena.geometry().floorY());
            yaml.set(base + "center-z", arena.geometry().centerZ());
            yaml.set(base + "half-length", arena.geometry().halfLength());
            yaml.set(base + "half-width", arena.geometry().halfWidth());
        }
        try {
            File parent = storageFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(storageFile);
        } catch (IOException e) {
            if (logger != null) {
                logger.warning("Failed to save arenas.yml: " + e.getMessage());
            }
        }
    }

    public void despawnAll() {
        for (Arena a : arenas.values()) {
            a.despawnDisplays();
        }
    }
}
