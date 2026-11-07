package fr.bosketball.arena;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.config.BosketConfig;
import fr.bosketball.game.MatchSession;
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

        }

        @Override
        public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
            return null;
        }
    }

    private final BosketBallPlugin plugin;
    private final Map<Integer, Arena> pooledCourts = new ConcurrentHashMap<>();

    public PracticeArenaPool(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public World ensurePracticeWorld() {
        if (Bukkit.getServer() == null) {
            return null;
        }
        String worldName = plugin.settings().practice().worldName();
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            return existing;
        }
        World created = new WorldCreator(worldName)
            .type(WorldType.FLAT)
            .generator(new VoidCourtGenerator())
            .generateStructures(false)
            .createWorld();
        if (created != null) {
            created.setAutoSave(false);
            created.setTime(6000L);
            if (plugin.protectionManager() != null) {
                plugin.protectionManager().applySafeWorldRules(created);
            }
        }
        return created;
    }

    public synchronized Arena acquireFreePracticeArena(Function<String, MatchSession> activeSessionLookup) {
        BosketConfig.Practice cfg = plugin.settings().practice();
        int maxInstances = cfg.maxInstances();

        for (int idx = 1; idx <= maxInstances; idx++) {
            Arena existing = pooledCourts.get(idx);
            if (existing != null) {
                MatchSession s = activeSessionLookup.apply(existing.id());
                if (s == null || s.state() == MatchSession.State.FINISHED) {
                    existing.ensureDisplaysHealthy();
                    return existing;
                }
            }
        }

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
