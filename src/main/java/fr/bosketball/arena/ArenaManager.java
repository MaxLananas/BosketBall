package fr.bosketball.arena;

import static fr.bosketball.config.AtomicYamlWriter.round2;

import fr.bosketball.config.AtomicYamlWriter;
import fr.bosketball.model.TeamSide;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

public final class ArenaManager {

    private static final String HEADER = """
        # ===========================================================================
        #   BOSKETBALL — ARENAS & CUSTOM MAPS REGISTRY (arenas.yml)
        #   Coordinates are rounded cleanly to 2 decimal places.
        #   Spawn poses use the readable format: "x, y, z, yaw, pitch"
        #   Use /bb map ... or /bb arena ... in-game, or edit & run /bb reload!
        # ===========================================================================
        """;

    private final File storageFile;
    private final Logger logger;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BosketBall-ArenaIO");
        t.setDaemon(true);
        return t;
    });

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
        return arenas.get(id.toLowerCase(Locale.ROOT));
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
        Arena removed = arenas.remove(id.toLowerCase(Locale.ROOT));
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
        YamlConfiguration yaml = AtomicYamlWriter.loadSafe(storageFile, logger);
        ConfigurationSection sec = yaml.getConfigurationSection("arenas");
        if (sec == null) {
            return;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection a = sec.getConfigurationSection(id);
            if (a == null) {
                continue;
            }
            String displayName = a.getString("info.display-name", a.getString("display-name", id));
            String worldName = a.getString("info.world", a.getString("world", "world"));
            boolean customMap = a.getBoolean("info.custom-map", a.getBoolean("custom-map", false));

            double cx = a.getDouble("center.x", a.getDouble("center-x", 0.5));
            double fy = a.getDouble("center.floor-y", a.getDouble("floor-y", 64.0));
            double cz = a.getDouble("center.z", a.getDouble("center-z", 0.5));
            double hl = a.getDouble("center.half-length", a.getDouble("half-length", CourtGeometry.DEFAULT_HALF_LENGTH));
            double hw = a.getDouble("center.half-width", a.getDouble("half-width", CourtGeometry.DEFAULT_HALF_WIDTH));

            CourtGeometry geo = new CourtGeometry(cx, fy, cz, hl, hw);

            if (a.contains("court-bounds.min-x") || a.contains("court.min-x")) {
                String pfx = a.contains("court-bounds.min-x") ? "court-bounds." : "court.";
                geo.setCourtBounds(
                    a.getDouble(pfx + "min-x"),
                    a.getDouble(pfx + "min-z"),
                    a.getDouble(pfx + "max-x"),
                    a.getDouble(pfx + "max-z"),
                    fy
                );
            }

            if (a.contains("barrier-cage.min-x") || a.contains("barrier.min-x")) {
                String pfx = a.contains("barrier-cage.min-x") ? "barrier-cage." : "barrier.";
                geo.setBarrierBounds(
                    a.getDouble(pfx + "min-x"),
                    a.getDouble(pfx + "min-z"),
                    a.getDouble(pfx + "max-x"),
                    a.getDouble(pfx + "max-z"),
                    a.getDouble(pfx + "ceiling-y", fy + 16.5)
                );
            }

            ConfigurationSection northSec = a.getConfigurationSection("hoops.frost-north");
            if (northSec == null) northSec = a.getConfigurationSection("hoops.north");
            if (northSec != null && northSec.contains("x")) {
                geo.setNorthHoop(
                    new Vector(northSec.getDouble("x"), northSec.getDouble("y"), northSec.getDouble("z")),
                    new Vector(northSec.getDouble("facing-x", northSec.getDouble("fx", 0.0)), 0.0,
                        northSec.getDouble("facing-z", northSec.getDouble("fz", 1.0))),
                    northSec.getBoolean("spawn-3d-model", northSec.getBoolean("model3d", true))
                );
            }

            ConfigurationSection southSec = a.getConfigurationSection("hoops.ignis-south");
            if (southSec == null) southSec = a.getConfigurationSection("hoops.south");
            if (southSec != null && southSec.contains("x")) {
                geo.setSouthHoop(
                    new Vector(southSec.getDouble("x"), southSec.getDouble("y"), southSec.getDouble("z")),
                    new Vector(southSec.getDouble("facing-x", southSec.getDouble("fx", 0.0)), 0.0,
                        southSec.getDouble("facing-z", southSec.getDouble("fz", -1.0))),
                    southSec.getBoolean("spawn-3d-model", southSec.getBoolean("model3d", true))
                );
            }

            Arena arena = new Arena(id, displayName, worldName, geo);
            arena.setCustomMap(customMap);
            arena.setCourtLinesEnabled(a.getBoolean("court-lines.enabled", true));
            arena.setCourtLinesPreset(Arena.CourtLinesPreset.fromString(a.getString("court-lines.preset", "FULL")));
            arena.setCourtLineWidth(a.getDouble("court-lines.width", 0.095));
            arena.setCourtLinePrimaryMaterial(
                Arena.resolveBlockMaterial(a.getString("court-lines.primary-material", "WHITE_CONCRETE"), org.bukkit.Material.WHITE_CONCRETE)
            );
            arena.setCourtLineAccentMaterial(
                Arena.resolveBlockMaterial(
                    a.getString("court-lines.accent-material", customMap ? "WHITE_CONCRETE" : "YELLOW_CONCRETE"),
                    customMap ? org.bukkit.Material.WHITE_CONCRETE : org.bukkit.Material.YELLOW_CONCRETE
                )
            );
            arena.setJumbotronEnabled(a.getBoolean("jumbotron.enabled", !arena.isCustomMap()));
            if (a.contains("jumbotron.x")) {
                arena.setCustomJumbotronCenter(new Vector(
                    a.getDouble("jumbotron.x"),
                    a.getDouble("jumbotron.y"),
                    a.getDouble("jumbotron.z")
                ));
            }

            for (TeamSide side : TeamSide.values()) {
                List<String> serialized = a.getStringList("spawns." + side.name().toLowerCase(Locale.ROOT));
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

            if (!arena.isCustomMap()) {
                ArenaBuilder.ensureHoopAndJumbotronStructures(arena);
            }
            arena.spawnDisplays();
            arenas.put(arena.id(), arena);
        }
    }

    public void saveAsync() {
        String serialized = buildYamlSnapshot().saveToString();
        if (ioExecutor.isShutdown()) {
            AtomicYamlWriter.writeTextAtomically(storageFile, HEADER + serialized, logger);
        } else {
            ioExecutor.execute(() -> AtomicYamlWriter.writeTextAtomically(storageFile, HEADER + serialized, logger));
        }
    }

    public void save() {
        AtomicYamlWriter.writeYamlAtomically(storageFile, HEADER, buildYamlSnapshot(), logger);
    }

    public void shutdownAndSaveSync() {
        ioExecutor.shutdown();
        try {
            ioExecutor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        save();
    }

    private YamlConfiguration buildYamlSnapshot() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Arena arena : arenas.values()) {
            String base = "arenas." + arena.id() + ".";
            CourtGeometry g = arena.geometry();

            yaml.set(base + "info.display-name", arena.displayName());
            yaml.set(base + "info.world", arena.worldName());
            yaml.set(base + "info.custom-map", arena.isCustomMap());

            yaml.set(base + "center.x", round2(g.centerX()));
            yaml.set(base + "center.floor-y", round2(g.floorY()));
            yaml.set(base + "center.z", round2(g.centerZ()));
            yaml.set(base + "center.half-length", round2(g.halfLength()));
            yaml.set(base + "center.half-width", round2(g.halfWidth()));

            yaml.set(base + "court-bounds.min-x", round2(g.minX()));
            yaml.set(base + "court-bounds.max-x", round2(g.maxX()));
            yaml.set(base + "court-bounds.min-z", round2(g.minZ()));
            yaml.set(base + "court-bounds.max-z", round2(g.maxZ()));

            yaml.set(base + "barrier-cage.min-x", round2(g.barrierMinX()));
            yaml.set(base + "barrier-cage.max-x", round2(g.barrierMaxX()));
            yaml.set(base + "barrier-cage.min-z", round2(g.barrierMinZ()));
            yaml.set(base + "barrier-cage.max-z", round2(g.barrierMaxZ()));
            yaml.set(base + "barrier-cage.ceiling-y", round2(g.ceilingY()));

            Vector nr = g.northHoopRim();
            Vector nf = g.northHoopForward();
            yaml.set(base + "hoops.frost-north.x", round2(nr.getX()));
            yaml.set(base + "hoops.frost-north.y", round2(nr.getY()));
            yaml.set(base + "hoops.frost-north.z", round2(nr.getZ()));
            yaml.set(base + "hoops.frost-north.facing-x", round2(nf.getX()));
            yaml.set(base + "hoops.frost-north.facing-z", round2(nf.getZ()));
            yaml.set(base + "hoops.frost-north.spawn-3d-model", g.northSpawn3dHoop());

            Vector sr = g.southHoopRim();
            Vector sf = g.southHoopForward();
            yaml.set(base + "hoops.ignis-south.x", round2(sr.getX()));
            yaml.set(base + "hoops.ignis-south.y", round2(sr.getY()));
            yaml.set(base + "hoops.ignis-south.z", round2(sr.getZ()));
            yaml.set(base + "hoops.ignis-south.facing-x", round2(sf.getX()));
            yaml.set(base + "hoops.ignis-south.facing-z", round2(sf.getZ()));
            yaml.set(base + "hoops.ignis-south.spawn-3d-model", g.southSpawn3dHoop());

            yaml.set(base + "court-lines.enabled", arena.isCourtLinesEnabled());
            yaml.set(base + "court-lines.preset", arena.courtLinesPreset().name());
            yaml.set(base + "court-lines.width", round2(arena.courtLineWidth()));
            yaml.set(base + "court-lines.primary-material", arena.courtLinePrimaryMaterial().name());
            yaml.set(base + "court-lines.accent-material", arena.courtLineAccentMaterial().name());

            yaml.set(base + "jumbotron.enabled", arena.isJumbotronEnabled());
            Vector jc = arena.customJumbotronCenter();
            if (jc != null) {
                yaml.set(base + "jumbotron.x", round2(jc.getX()));
                yaml.set(base + "jumbotron.y", round2(jc.getY()));
                yaml.set(base + "jumbotron.z", round2(jc.getZ()));
            }

            for (Map.Entry<TeamSide, List<Arena.SpawnPose>> e : arena.allCustomSpawns().entrySet()) {
                if (!e.getValue().isEmpty()) {
                    List<String> lines = e.getValue().stream().map(ArenaManager::formatPose).toList();
                    yaml.set(base + "spawns." + e.getKey().name().toLowerCase(Locale.ROOT), lines);
                }
            }
            if (arena.lobbyExitSpawn() != null) {
                yaml.set(base + "spawns.lobby", formatPose(arena.lobbyExitSpawn()));
            }
        }
        return yaml;
    }

    private static String formatPose(Arena.SpawnPose p) {
        return String.format(Locale.ROOT, "%.2f, %.2f, %.2f, %.1f, %.1f", p.x(), p.y(), p.z(), p.yaw(), p.pitch());
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
