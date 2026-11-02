package fr.bosketball.arena;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.config.BosketConfig;
import fr.bosketball.game.MatchSession;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/**
 * Auto-Scaling Multi-Instance Practice &amp; 3-Point Contest Arena Pool!
 * Dynamically allocates up to 64+ isolated Practice courts on an 8x8 grid inside a zero-lag void world
 * ({@code bosketball_practice}) so dozens of online players can train simultaneously without occupying
 * competitive 1v1/2v2/3v3/5v5 arenas.
 */
public final class PracticeArenaPool {

    private static final class VoidCourtGenerator extends ChunkGenerator {
        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }

        @Override
        public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
            // Pure void — zero terrain generation overhead!
        }

        @Override
        public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
            return null;
        }
    }

    private final BosketBallPlugin plugin;
    private final Map<Integer, Arena> pooledCourts = new ConcurrentHashMap<>();
    private volatile World practiceWorld;

    public PracticeArenaPool(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public Collection<Arena> allocatedCourts() {
        return pooledCourts.values();
    }

    public World ensurePracticeWorld() {
        if (Bukkit.getServer() == null) {
            return null;
        }
        String worldName = plugin.settings().practice().worldName();
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            this.practiceWorld = existing;
            return existing;
        }
        World created = new WorldCreator(worldName)
            .type(WorldType.FLAT)
            .generator(new VoidCourtGenerator())
            .generateStructures(false)
            .createWorld();
        if (created != null) {
            created.setAutoSave(false);
            created.setKeepSpawnInMemory(false);
            created.setTime(6000L);
            if (plugin.protectionManager() != null) {
                plugin.protectionManager().applySafeWorldRules(created);
            }
            this.practiceWorld = created;
        }
        return created;
    }

    /**
     * Returns an idle Practice Arena from the pool immediately, or builds the next grid slot on demand!
     */
    public synchronized Arena acquireFreePracticeArena(Function<String, MatchSession> activeSessionLookup) {
        BosketConfig.Practice cfg = plugin.settings().practice();
        int maxInstances = cfg.maxInstances();

        // 1. Reuse any already-built idle pooled practice court in O(1)
        for (int idx = 1; idx <= maxInstances; idx++) {
            Arena existing = pooledCourts.get(idx);
            if (existing != null) {
                MatchSession s = activeSessionLookup.apply(existing.id());
                if (s == null || s.state() == MatchSession.State.FINISHED) {
                    if (existing.northHoopDisplay() == null || existing.southHoopDisplay() == null) {
                        existing.spawnDisplays();
                    }
                    return existing;
                }
            }
        }

        // 2. Provision a brand-new isolated practice court on the 8-column void grid
        World world = ensurePracticeWorld();
        if (world == null) {
            return null;
        }
        for (int idx = 1; idx <= maxInstances; idx++) {
            if (pooledCourts.containsKey(idx)) {
                continue;
            }
            int zeroIdx = idx - 1;
            int col = zeroIdx % 8;
            int row = zeroIdx / 8;
            int spacing = cfg.gridSpacingBlocks();
            double cx = col * spacing + 0.5;
            double cz = row * spacing + 0.5;
            String id = "practice_" + idx;
            String title = "Practice Court #" + idx;

            Location buildOrigin = new Location(world, cx, 64.0, cz);
            Arena built = ArenaBuilder.buildFullStadium(id, title, buildOrigin);
            built.setEphemeralPracticeInstance(true);
            // Return players cleanly to the main Lobby Spawn when they exit a pooled Practice court
            Location mainLobby = plugin.protectionManager() != null ? plugin.protectionManager().resolveLobbySpawn(null) : null;
            if (mainLobby != null && mainLobby.getWorld() != null && mainLobby.getWorld().equals(world)) {
                built.setLobbyExitSpawn(Arena.SpawnPose.fromLocation(mainLobby));
            }
            pooledCourts.put(idx, built);
            return built;
        }
        return null;
    }

    public static Location computeGridSlotCenter(int oneBasedIndex, int spacingBlocks) {
        int zeroIdx = Math.max(0, oneBasedIndex - 1);
        int col = zeroIdx % 8;
        int row = zeroIdx / 8;
        int safeSpacing = Math.max(96, spacingBlocks);
        return new Location(null, col * safeSpacing + 0.5, 64.0, row * safeSpacing + 0.5);
    }

    public void despawnAll() {
        for (Arena a : pooledCourts.values()) {
            a.despawnDisplays();
        }
        pooledCourts.clear();
    }
}
