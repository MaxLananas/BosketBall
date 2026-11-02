package fr.bosketball.arena;

import fr.bosketball.model.TeamSide;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

/**
 * Registry & Full YAML Persistence for both Generated Stadiums and Custom User-Built Basketball Maps!
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

    public Arena primaryArena() {
        return firstArenaOrNull();
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

            CourtGeometry geo = new CourtGeometry(cx, fy, cz, hl, hw);
            if (a.contains("court.min-x")) {
                geo.setCourtBounds(
                    a.getDouble("court.min-x"),
                    a.getDouble("court.min-z"),
                    a.getDouble("court.max-x"),
                    a.getDouble("court.max-z"),
                    fy
                );
            }
            if (a.contains("barrier.min-x")) {
                geo.setBarrierBounds(
                    a.getDouble("barrier.min-x"),
                    a.getDouble("barrier.min-z"),
                    a.getDouble("barrier.max-x"),
                    a.getDouble("barrier.max-z"),
                    a.getDouble("barrier.ceiling-y", fy + 16.5)
                );
            }
            if (a.contains("hoops.north.x")) {
                geo.setNorthHoop(
                    new Vector(a.getDouble("hoops.north.x"), a.getDouble("hoops.north.y"), a.getDouble("hoops.north.z")),
                    new Vector(a.getDouble("hoops.north.fx", 0.0), 0.0, a.getDouble("hoops.north.fz", 1.0)),
                    a.getBoolean("hoops.north.model3d", true)
                );
            }
            if (a.contains("hoops.south.x")) {
                geo.setSouthHoop(
                    new Vector(a.getDouble("hoops.south.x"), a.getDouble("hoops.south.y"), a.getDouble("hoops.south.z")),
                    new Vector(a.getDouble("hoops.south.fx", 0.0), 0.0, a.getDouble("hoops.south.fz", -1.0)),
                    a.getBoolean("hoops.south.model3d", true)
                );
            }

            Arena arena = new Arena(id, displayName, worldName, geo);
            arena.setCustomMap(a.getBoolean("custom-map", false));
            arena.setJumbotronEnabled(a.getBoolean("jumbotron.enabled", !arena.isCustomMap()));
            if (a.contains("jumbotron.x")) {
                arena.setCustomJumbotronCenter(new Vector(
                    a.getDouble("jumbotron.x"),
                    a.getDouble("jumbotron.y"),
                    a.getDouble("jumbotron.z")
                ));
            }

            for (TeamSide side : TeamSide.values()) {
                List<String> serialized = a.getStringList("spawns." + side.name().toLowerCase());
                if (!serialized.isEmpty()) {
                    List<Arena.SpawnPose> poses = new ArrayList<>();
                    for (String s : serialized) {
                        Arena.SpawnPose parsed = parsePose(s);
                        if (parsed != null) {
                            poses.add(parsed);
                        }
                    }
                    arena.replaceCustomSpawns(side, poses);
                }
            }
            String lobbyRaw = a.getString("spawns.lobby", null);
            if (lobbyRaw != null) {
                arena.setLobbyExitSpawn(parsePose(lobbyRaw));
            }

            arena.spawnDisplays();
            arenas.put(arena.id(), arena);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Arena arena : arenas.values()) {
            String base = "arenas." + arena.id() + ".";
            CourtGeometry g = arena.geometry();
            yaml.set(base + "display-name", arena.displayName());
            yaml.set(base + "world", arena.worldName());
            yaml.set(base + "custom-map", arena.isCustomMap());
            yaml.set(base + "center-x", g.centerX());
            yaml.set(base + "floor-y", g.floorY());
            yaml.set(base + "center-z", g.centerZ());
            yaml.set(base + "half-length", g.halfLength());
            yaml.set(base + "half-width", g.halfWidth());

            yaml.set(base + "court.min-x", g.minX());
            yaml.set(base + "court.max-x", g.maxX());
            yaml.set(base + "court.min-z", g.minZ());
            yaml.set(base + "court.max-z", g.maxZ());

            yaml.set(base + "barrier.min-x", g.barrierMinX());
            yaml.set(base + "barrier.max-x", g.barrierMaxX());
            yaml.set(base + "barrier.min-z", g.barrierMinZ());
            yaml.set(base + "barrier.max-z", g.barrierMaxZ());
            yaml.set(base + "barrier.ceiling-y", g.ceilingY());

            Vector nr = g.northHoopRim();
            Vector nf = g.northHoopForward();
            yaml.set(base + "hoops.north.x", nr.getX());
            yaml.set(base + "hoops.north.y", nr.getY());
            yaml.set(base + "hoops.north.z", nr.getZ());
            yaml.set(base + "hoops.north.fx", nf.getX());
            yaml.set(base + "hoops.north.fz", nf.getZ());
            yaml.set(base + "hoops.north.model3d", g.northSpawn3dHoop());

            Vector sr = g.southHoopRim();
            Vector sf = g.southHoopForward();
            yaml.set(base + "hoops.south.x", sr.getX());
            yaml.set(base + "hoops.south.y", sr.getY());
            yaml.set(base + "hoops.south.z", sr.getZ());
            yaml.set(base + "hoops.south.fx", sf.getX());
            yaml.set(base + "hoops.south.fz", sf.getZ());
            yaml.set(base + "hoops.south.model3d", g.southSpawn3dHoop());

            yaml.set(base + "jumbotron.enabled", arena.isJumbotronEnabled());
            Vector jc = arena.customJumbotronCenter();
            if (jc != null) {
                yaml.set(base + "jumbotron.x", jc.getX());
                yaml.set(base + "jumbotron.y", jc.getY());
                yaml.set(base + "jumbotron.z", jc.getZ());
            }

            for (Map.Entry<TeamSide, List<Arena.SpawnPose>> e : arena.allCustomSpawns().entrySet()) {
                if (!e.getValue().isEmpty()) {
                    List<String> lines = e.getValue().stream().map(ArenaManager::formatPose).toList();
                    yaml.set(base + "spawns." + e.getKey().name().toLowerCase(), lines);
                }
            }
            if (arena.lobbyExitSpawn() != null) {
                yaml.set(base + "spawns.lobby", formatPose(arena.lobbyExitSpawn()));
            }
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

    private static String formatPose(Arena.SpawnPose p) {
        return String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f,%.2f,%.2f", p.x(), p.y(), p.z(), p.yaw(), p.pitch());
    }

    private static Arena.SpawnPose parsePose(String raw) {
        try {
            String[] parts = raw.split(",");
            if (parts.length < 5) {
                return null;
            }
            return new Arena.SpawnPose(
                Double.parseDouble(parts[0].trim()),
                Double.parseDouble(parts[1].trim()),
                Double.parseDouble(parts[2].trim()),
                Float.parseFloat(parts[3].trim()),
                Float.parseFloat(parts[4].trim())
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    public void despawnAll() {
        for (Arena a : arenas.values()) {
            a.despawnDisplays();
        }
    }
}
