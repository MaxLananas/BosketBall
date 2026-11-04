package fr.bosketball.arena.schematic;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.CourtGeometry;
import fr.bosketball.config.BosketConfig;
import fr.bosketball.game.MatchSession;
import fr.bosketball.util.Msg;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.Vector;

/**
 * Instanced courts pasted from a WorldEdit / FastAsyncWorldEdit schematic — one private copy per
 * player, laid out on a wide grid in a dedicated void world, far away from the lobby.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>One file, decoded once.</b> {@code terrain.schem} is parsed a single time into flat
 *       palette-index arrays ({@link CourtSchematic}); every court is a replay of those arrays.
 *       No chunk I/O, no re-parsing, no FAWE dependency.</li>
 *   <li><b>Budgeted pasting.</b> Work is spread over ticks ({@code max-blocks-per-tick}) so even 50
 *       courts never stall a tick or disconnect anybody.</li>
 *   <li><b>Self-cleaning.</b> When a session ends, the court is reverted to air and re-pasted
 *       before it can be handed out again, so a modified build never reaches the next player.</li>
 *   <li><b>Cheap displays.</b> Pasted courts skip the synthetic floor line BlockDisplays (the build
 *       already has lines) and only spawn the two 3D hoops + shot clocks.</li>
 * </ul>
 */
public final class SchematicCourtPool {

    /** Void generator: no terrain, no caves, no entities — the schematic is the only geometry. */
    private static final class VoidGenerator extends ChunkGenerator {
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
    }

    /** One fixed biome for the whole court world (no per-chunk biome noise, uniform skylight). */
    private static final class FixedBiomeProvider extends BiomeProvider {
        private final Biome biome;

        FixedBiomeProvider(Biome biome) {
            this.biome = biome;
        }

        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
            return biome;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo worldInfo) {
            return List.of(biome);
        }
    }

    /** Per-slot bookkeeping: geometry, paste state and the one-shot cleanup after a match. */
    private static final class Slot {
        final int index;
        final Arena arena;
        final int originX;
        final int originY;
        final int originZ;
        boolean pasted;
        boolean dirty;
        /** Last session seen on this slot; a change of session means the build was played on. */
        MatchSession lastSession;
        /** True once a player has been on this slot since the last paste (air must be rewritten). */
        boolean hasHostedSession;
        int revertAtTick = -1;
        /** Flat index the in-flight paste resumes from (0 = not started). */
        int pasteCursor = 0;

        Slot(int index, Arena arena, int originX, int originY, int originZ) {
            this.index = index;
            this.arena = arena;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
        }
    }

    private final BosketBallPlugin plugin;
    private final Map<Integer, Slot> slots = new LinkedHashMap<>();
    private final Deque<Slot> pasteQueue = new ArrayDeque<>();

    private volatile CourtSchematic schematic;
    private volatile String loadError;
    private volatile World world;
    private long lastLoadAttemptMillis;
    private String lastLoggedError;
    private int serverTickCounter = 0;

    /** How often a failed schematic load is retried while the pool is unusable. */
    private static final long LOAD_RETRY_MILLIS = 10_000L;

    /** Courts pasted at startup; every other one is pasted on demand (one tick each). */
    private static final int PREWARM_COURTS = 4;

    public SchematicCourtPool(BosketBallPlugin plugin) {
        this.plugin = plugin;
        this.lastLoadAttemptMillis = System.currentTimeMillis();
    }

    // =========================================================================
    //  ACCESSORS
    // =========================================================================

    public BosketConfig.SchematicCourts config() {
        return plugin.settings().schematicCourts();
    }

    public boolean enabled() {
        return config().enabled();
    }

    /** True when a usable schematic is loaded and courts can be handed out. */
    public boolean ready() {
        return enabled() && schematic != null;
    }

    public CourtSchematic schematic() {
        return schematic;
    }

    public String loadError() {
        return loadError;
    }

    public World world() {
        return world;
    }

    public int instanceCount() {
        return slots.size();
    }

    public int pastedCount() {
        int n = 0;
        for (Slot s : slots.values()) {
            if (s.pasted) {
                n++;
            }
        }
        return n;
    }

    public Collection<Arena> allCourts() {
        List<Arena> out = new ArrayList<>(slots.size());
        for (Slot s : slots.values()) {
            out.add(s.arena);
        }
        return out;
    }

    // =========================================================================
    //  BOOTSTRAP
    // =========================================================================

    /**
     * Loads the schematic, creates the court world and registers every instance. Safe to call
     * twice ({@code /bb arena schematic reload}).
     *
     * @return true when at least one court is available
     */
    public boolean bootstrap() {
        BosketConfig.SchematicCourts cfg = config();
        loadError = null;
        if (!cfg.enabled()) {
            plugin.getLogger().info("[Courts] schematic-courts.enabled=false — using generated stadiums.");
            return false;
        }
        // Re-allocating the slots while somebody is playing would strand that match on an arena
        // object nobody hands out any more. Refuse, and say why.
        if (busyCount() > 0) {
            loadError = "reload skipped (" + busyCount() + " court(s) in use) — the current setup stays active";
            plugin.getLogger().warning("[Courts] " + loadError + " — try again once they are free.");
            return ready();
        }
        try {
            File file = resolveSchematicFile(cfg.schematic());
            schematic = CourtSchematic.load(file);
            lastLoggedError = null;
            plugin.getLogger().info("[Courts] Loaded " + file.getName() + " — "
                + schematic.width() + "x" + schematic.height() + "x" + schematic.length()
                + ", " + schematic.nonAirBlocks() + " solid blocks, palette "
                + schematic.paletteSize() + ", " + schematic.footprintChunks() + " chunk(s)/court.");
        } catch (Throwable ex) {
            schematic = null;
            loadError = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            // The loader is retried every 10 s while it fails, so only log a *new* problem: an
            // admin who is still saving the file does not need the same warning 8 000 times a day.
            if (!loadError.equals(lastLoggedError)) {
                lastLoggedError = loadError;
                plugin.getLogger().warning("[Courts] Cannot load schematic '" + cfg.schematic() + "': "
                    + loadError);
            }
            return false;
        }

        world = ensureWorld(cfg);
        if (world == null) {
            loadError = "could not create world " + cfg.worldName();
            return false;
        }
        buildSlots(cfg);
        if (cfg.retireLegacyPracticeWorld()) {
            retireLegacyPracticeWorld();
        }
        if (cfg.pasteOnEnable()) {
            // Pre-warm only the first few courts: pasting all 50 up front costs 50x the CPU and
            // ~50x the chunk I/O for nothing, since 25 concurrent players never need more than
            // 25 and the rest are pasted on demand (a paste takes one tick).
            int warm = Math.min(slots.size(), PREWARM_COURTS);
            for (int i = 1; i <= warm; i++) {
                Slot s = slots.get(i);
                if (s != null) {
                    enqueuePaste(s);
                }
            }
            plugin.getLogger().info("[Courts] Pre-warming " + warm + " court(s); the other "
                + (slots.size() - warm) + " are pasted on demand.");
        }
        return true;
    }

    /**
     * Finds {@code terrain.schem}. Every place FAWE / WorldEdit may have put it is searched, so a
     * plain {@code //schem save terrain} works with no file shuffling:
     * <ol>
     *   <li>{@code plugins/BosketBall/} and {@code plugins/BosketBall/schematics/};</li>
     *   <li>the configured path itself (absolute, or relative to the server root);</li>
     *   <li>{@code plugins/FastAsyncWorldEdit/schematics/} and {@code plugins/WorldEdit/schematics/},
     *       including one level of sub-folders (FAWE's per-player / per-category layout).</li>
     * </ol>
     * The first candidate that exists on disk wins.
     */
    private File resolveSchematicFile(String configured) {
        File dataFolder = plugin.getDataFolder();
        String lower = configured.toLowerCase(Locale.ROOT);
        String bare = lower.endsWith(".schem") || lower.endsWith(".schematic")
            || lower.endsWith(".schem2") || lower.endsWith(".schem3")
            ? configured.substring(0, configured.lastIndexOf('.')) : configured;
        String[] extensions = {"", ".schem", ".schematic", ".schem2", ".schem3"};

        List<File> candidates = new ArrayList<>();
        candidates.add(new File(configured));
        candidates.add(new File(dataFolder, configured));
        for (String ext : extensions) {
            candidates.add(new File(dataFolder, bare + ext));
            candidates.add(new File(new File(dataFolder, "schematics"), bare + ext));
        }

        File pluginsDir = dataFolder.getParentFile();
        if (pluginsDir != null) {
            for (String editor : new String[]{"FastAsyncWorldEdit", "WorldEdit"}) {
                File dir = new File(new File(pluginsDir, editor), "schematics");
                candidates.add(new File(dir, configured));
                for (String ext : extensions) {
                    candidates.add(new File(dir, bare + ext));
                }
            }
        }
        for (File f : candidates) {
            if (f.isFile()) {
                return f;
            }
        }
        // One level of sub-folders: FAWE 2.x can save under schematics/<player-uuid>/ or a category.
        if (pluginsDir != null) {
            for (String editor : new String[]{"FastAsyncWorldEdit", "WorldEdit"}) {
                File root = new File(new File(pluginsDir, editor), "schematics");
                File[] subDirs = root.listFiles(File::isDirectory);
                if (subDirs == null) {
                    continue;
                }
                for (File sub : subDirs) {
                    for (String ext : new String[]{"", ".schem", ".schematic"}) {
                        File f = new File(sub, bare + ext);
                        if (f.isFile()) {
                            return f;
                        }
                    }
                }
            }
        }

        File schematicsDir = new File(dataFolder, "schematics");
        if (!schematicsDir.isDirectory() && !schematicsDir.mkdirs()) {
            plugin.getLogger().warning("[Courts] Could not create " + schematicsDir);
        }
        throw new IllegalStateException("terrain.schem not found — save it with //copy then //schem save terrain,"
            + " or drop it in " + new File(schematicsDir, bare + ".schem").getAbsolutePath());
    }

    /**
     * Deletes the old {@code bosketball_practice} world (the "ugly arena" grid built by
     * {@link fr.bosketball.arena.PracticeArenaPool}) once the instanced courts are live.
     *
     * <p>Nothing is sent there any more, so keeping the folder would only mean loading 25 stale
     * chunks of a stadium nobody can reach — and, worse, it is what made a training session look
     * like it "still used the old arena". Disable with
     * {@code schematic-courts.retire-legacy-practice-world: false} to keep it.</p>
     */
    private void retireLegacyPracticeWorld() {
        String legacyName = plugin.settings().practice().worldName();
        if (legacyName == null || legacyName.isBlank() || legacyName.equalsIgnoreCase(world.getName())) {
            return;
        }
        World legacy = Bukkit.getWorld(legacyName);
        if (legacy == null) {
            File folder = new File(world.getWorldFolder().getParentFile(), legacyName);
            if (folder.isDirectory()) {
                deleteRecursively(folder);
                plugin.getLogger().info("[Courts] Removed the unused legacy practice world folder '"
                    + legacyName + "'.");
            }
            return;
        }
        try {
            for (Player p : new ArrayList<>(legacy.getPlayers())) {
                Location lobby = plugin.protectionManager() != null
                    ? plugin.protectionManager().resolveLobbySpawn(p) : null;
                if (lobby != null && lobby.getWorld() != null) {
                    p.teleport(lobby);
                }
            }
            File folder = legacy.getWorldFolder();
            if (!Bukkit.unloadWorld(legacy, false)) {
                plugin.getLogger().warning("[Courts] Could not unload the legacy practice world '"
                    + legacyName + "' — it will be retired on the next restart.");
                return;
            }
            deleteRecursively(folder);
            plugin.getLogger().info("[Courts] Retired the legacy practice world '" + legacyName
                + "' (schematic courts replace it).");
        } catch (Throwable ex) {
            plugin.getLogger().warning("[Courts] Could not retire '" + legacyName + "': " + ex.getMessage());
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    private World ensureWorld(BosketConfig.SchematicCourts cfg) {
        World existing = Bukkit.getWorld(cfg.worldName());
        if (existing != null) {
            return existing;
        }
        try {
            World created = new WorldCreator(cfg.worldName())
                .type(WorldType.FLAT)
                .generator(new VoidGenerator())
                .biomeProvider(new FixedBiomeProvider(Biome.PLAINS))
                .generateStructures(false)
                .createWorld();
            if (created == null) {
                return null;
            }
            created.setAutoSave(false);
            created.setKeepSpawnInMemory(false);
            created.setTime(6000L);
            created.setStorm(false);
            created.setThundering(false);
            if (plugin.protectionManager() != null) {
                plugin.protectionManager().applySafeWorldRules(created);
            }
            return created;
        } catch (Throwable ex) {
            plugin.getLogger().warning("[Courts] World creation failed: " + ex.getMessage());
            return null;
        }
    }

    private void buildSlots(BosketConfig.SchematicCourts cfg) {
        slots.clear();
        pasteQueue.clear();
        CourtSchematic schem = schematic;
        int count = cfg.instances();
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(count)));
        int spacing = cfg.spacingBlocks();
        String baseId = cfg.worldName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");

        // `origin-y` is the FLOOR level, not the bottom corner of the build: that is what an admin
        // means by "the ground is at y=64". The paste corner is shifted down by however deep the
        // build extends below its deck, so the deck always lands exactly on origin-y.
        int floorY = cfg.originY();
        int pasteOriginY = floorY - schem.floorOffsetAtCenter();
        if (pasteOriginY < -60) {
            plugin.getLogger().warning("[Courts] terrain.schem extends " + (-pasteOriginY)
                + " blocks below the configured floor — clamping. Raise schematic-courts.origin-y.");
            pasteOriginY = -60;
        }

        for (int i = 0; i < count; i++) {
            int col = i % columns;
            int row = i / columns;
            // Column-major on X, row-major on Z. A court is centred on its own footprint, so the
            // origin must be the MIN CORNER: shift it so the build's midpoint lands on the cell.
            int originX = cfg.originX() + col * spacing;
            int originZ = cfg.originZ() + row * spacing;
            String id = baseId + "_" + (i + 1);

            Arena arena = new Arena(id, "Court #" + (i + 1), world.getName(),
                geometryFor(cfg, schem, originX, pasteOriginY, originZ, floorY));
            arena.setCustomMap(true);
            arena.setJumbotronEnabled(false);
            // The pasted build already contains the painted lines: spawning ~80 extra flat
            // BlockDisplays per court would be pure overhead at 50 instances.
            arena.geometry().setBakedCourtLines(true);
            arena.setCourtLinesEnabled(cfg.flushFloor());
            slots.put(i + 1, new Slot(i + 1, arena, originX, pasteOriginY, originZ));
        }
        if (Math.max(schem.width(), schem.length()) >= spacing - 8) {
            plugin.getLogger().warning("[Courts] spacing-blocks (" + spacing + ") is too small for a "
                + schem.width() + "x" + schem.length() + " build — neighbouring courts will overlap. "
                + "Raise schematic-courts.spacing-blocks.");
        }
        plugin.getLogger().info("[Courts] " + count + " instance(s) laid out on a " + columns
            + "-column grid, spacing " + spacing + " blocks, world '" + world.getName() + "'.");
    }

    /**
     * Builds the physics geometry for one instance.
     *
     * <p>Everything is derived from the build itself:</p>
     * <ul>
     *   <li><b>Centre</b> = the point the admin stood on for {@code //copy} (the {@code Offset} tag),
     *       so a centred copy is centred even when the paste origin is a corner;</li>
     *   <li><b>Hoop axis</b> = the long side of the build. A basketball court is always longer
     *       basket-to-basket than it is wide; using {@code facing} here was wrong, because
     *       {@code facing: west} says nothing about which way the build's long side runs;</li>
     *   <li><b>Playable rectangle</b> = the long side minus a small apron, so the two hoops sit
     *       close to the painted end lines instead of floating anywhere in the middle;</li>
     *   <li><b>Floor</b> = the auto-calibrated deck top (see {@link CourtSchematic#floorOffsetAtCenter()}).</li>
     * </ul>
     */
    private CourtGeometry geometryFor(BosketConfig.SchematicCourts cfg, CourtSchematic schem,
                                      int originX, int pasteOriginY, int originZ, int floorY) {
        double centerX = originX + schem.localCenterX() + cfg.centerOffsetX();
        double centerZ = originZ + schem.localCenterZ() + cfg.centerOffsetZ();

        // Hoop axis: explicitly configured, else the build's longest horizontal side.
        boolean alongX = hoopAxisAlongX(cfg.hoopFacing(), cfg.facing(), schem.width(), schem.length());

        double[] spans = axisSpans(alongX, schem.width(), schem.length(),
            cfg.halfLength(), cfg.halfWidth());
        double halfZ = spans[0];
        double halfX = spans[1];
        // Distance from the centre to a basket, i.e. half of the long side.
        double halfBasketSpan = alongX ? halfX : halfZ;

        double floor = floorY + cfg.floorRelativeY();

        CourtGeometry geo = new CourtGeometry(centerX, floor, centerZ, halfZ, halfX);
        geo.setCourtBounds(centerX - halfX, centerZ - halfZ, centerX + halfX, centerZ + halfZ, floor);
        // Tight cage. The default envelope (court + 8.5 blocks) would let players and the ball
        // walk off the edge of a 15-wide pasted build and fall into the void; here the barrier is
        // the playable rectangle plus half a block.
        geo.setBarrierBounds(centerX - halfX - 0.5, centerZ - halfZ - 0.5,
            centerX + halfX + 0.5, centerZ + halfZ + 0.5, floor + 16.5);

        double rimInset = Math.max(0.5, Math.min(cfg.rimInset(), halfBasketSpan - 1.0));
        double rimY = floor + cfg.rimRelativeY();
        if (alongX) {
            geo.setNorthHoop(new Vector(centerX - halfBasketSpan + rimInset, rimY, centerZ), new Vector(1, 0, 0), true);
            geo.setSouthHoop(new Vector(centerX + halfBasketSpan - rimInset, rimY, centerZ), new Vector(-1, 0, 0), true);
        } else {
            geo.setNorthHoop(new Vector(centerX, rimY, centerZ - halfBasketSpan + rimInset), new Vector(0, 0, 1), true);
            geo.setSouthHoop(new Vector(centerX, rimY, centerZ + halfBasketSpan - rimInset), new Vector(0, 0, -1), true);
        }
        return geo;
    }

    /**
     * Maps the semantic half-sizes (basket-to-basket / across) onto the real axes.
     *
     * <p>In {@link CourtGeometry} {@code halfLength} is the <b>Z</b> half-extent and
     * {@code halfWidth} the <b>X</b> one — they are not named after the hoop axis. Passing the long
     * side in as {@code halfWidth} produced a corridor-shaped court with both rims in the middle of
     * a sideline, so this conversion is isolated here and unit-tested.</p>
     *
     * @return {@code {halfZ, halfX}}; the configured values only ever shrink (never stretch) the
     *         build's own footprint
     */
    public static double[] axisSpans(boolean alongX, int width, int length,
                                     double configuredBasketToBasket, double configuredAcross) {
        double maxHalfZ = Math.max(4.0, length * 0.5 - 1.0);
        double maxHalfX = Math.max(4.0, width * 0.5 - 1.0);
        double basketToBasket = Math.max(4.0, configuredBasketToBasket);
        double across = Math.max(4.0, configuredAcross);
        if (alongX) {
            return new double[]{Math.min(maxHalfZ, across), Math.min(maxHalfX, basketToBasket)};
        }
        return new double[]{Math.min(maxHalfZ, basketToBasket), Math.min(maxHalfX, across)};
    }

    /**
     * Which horizontal axis the two baskets sit on.
     *
     * <p>{@code auto} picks the build's <b>longest side</b>: a basketball court is always longer
     * basket-to-basket than it is wide, so the geometry follows the build itself instead of a
     * config guess. An explicit {@code hoop-facing} always wins, and a perfectly square build
     * falls back to the direction the admin faced when copying.</p>
     */
    public static boolean hoopAxisAlongX(String hoopFacing, String facing, int width, int length) {
        String axis = hoopFacing == null ? "auto" : hoopFacing.trim().toLowerCase(Locale.ROOT);
        if (axis.startsWith("east") || axis.startsWith("west") || axis.startsWith("x")) {
            return true;
        }
        if (axis.startsWith("north") || axis.startsWith("south") || axis.startsWith("z")) {
            return false;
        }
        if (width == length) {
            String dir = facing == null ? "" : facing.trim().toLowerCase(Locale.ROOT);
            return dir.startsWith("east") || dir.startsWith("west");
        }
        return width > length;
    }

    // =========================================================================
    //  DISTRIBUTION
    // =========================================================================

    /**
     * Returns an idle, fully pasted court, pasting the next clean instance on demand.
     *
     * @param exclude arena ids already reserved by the caller (e.g. a duel target)
     */
    public Arena acquireFreeArena(Collection<String> exclude) {
        if (!ready()) {
            return null;
        }
        // `placeholder-until-pasted: false` trades the short wait for an instant court: the player
        // is dropped on a slot whose paste is still running (the scheduler finishes it under them).
        boolean allowUnfinished = !config().placeholderUntilPasted();
        for (Slot s : slots.values()) {
            if (exclude != null && exclude.contains(s.arena.id())) {
                continue;
            }
            refreshDirty(s);
            MatchSession session = plugin.gameManager().getSessionByArena(s.arena.id());
            boolean busy = session != null && session.state() != MatchSession.State.FINISHED;
            boolean notReady = allowUnfinished ? !s.pasted && s.pasteCursor == 0 : (!s.pasted || s.pasteCursor > 0);
            if (busy || s.dirty || notReady) {
                continue;
            }
            s.arena.ensureDisplaysHealthy();
            return s.arena;
        }
        // Nothing clean left: paste the next dirty/never-pasted instance and try again next tick.
        for (Slot s : slots.values()) {
            refreshDirty(s);
            if (!isBusy(s) && (!s.pasted || s.dirty)) {
                enqueuePaste(s);
                return null;
            }
        }
        return null;
    }

    /**
     * Court for a solo / practice session: a private copy, so a player training never watches
     * somebody else's match and never has to wait for a stadium slot.
     */
    public Arena acquireFreePracticeArena() {
        return acquireFreeArena(null);
    }

    public Arena courtById(String arenaId) {
        if (arenaId == null) {
            return null;
        }
        for (Slot s : slots.values()) {
            if (s.arena.id().equalsIgnoreCase(arenaId)) {
                return s.arena;
            }
        }
        return null;
    }

    public boolean isSchematicCourt(String arenaId) {
        return courtById(arenaId) != null;
    }

    private void enqueuePaste(Slot slot) {
        if (!pasteQueue.contains(slot)) {
            pasteQueue.add(slot);
        }
    }

    // =========================================================================
    //  TICK: budgeted pasting / reverting
    // =========================================================================

    /** Called once per server tick from the plugin scheduler. */
    public void tick() {
        if (!enabled()) {
            return;
        }
        // Self-healing startup: if `terrain.schem` was missing (or unreadable) when the server came
        // up, keep looking for it instead of requiring a manual `/bb arena schematic reload`. A
        // 10-second throttle makes this free, and a successful retry boots the whole pool.
        if (schematic == null) {
            long now = System.currentTimeMillis();
            if (now - lastLoadAttemptMillis >= LOAD_RETRY_MILLIS) {
                lastLoadAttemptMillis = now;
                if (bootstrap()) {
                    plugin.getLogger().info("[Courts] terrain.schem appeared — instanced courts are live.");
                }
            }
            return;
        }
        if (world == null) {
            return;
        }
        serverTickCounter++;
        // Bandwidth-budgeted pasting: one court may span several ticks, and no tick ever writes
        // more than `max-blocks-per-tick` blocks (10 000 writes ~ 1 ms; the default 40 000 lets a
        // whole court go through in a single tick while staying bounded on slow hardware).
        final int budget = config().maxBlocksPerTick();
        Slot job = pasteQueue.peek();
        if (job != null && !isBusy(job)) {
            if (job.pasteCursor == 0) {
                purgeLooseEntities(job);
            }
            // First paste on a virgin void slot: air is already air, so skipping it is free speed.
            // Re-paste after a session: air MUST be written, or whatever a player built stays there.
            boolean writeAir = job.hasHostedSession || !config().skipEmptyAir();
            CourtSchematic.PasteProgress progress = schematic.pasteSlice(
                world, job.originX, job.originY, job.originZ, writeAir, budget, job.pasteCursor);
            job.pasteCursor = progress.nextIndex();
            if (progress.complete()) {
                pasteQueue.poll();
                job.pasteCursor = 0;
                job.pasted = true;
                job.dirty = false;
                job.hasHostedSession = false;
                job.revertAtTick = -1;
                finishPaste(job);
            }
        }
        // Give a finished court a short grace period (players are still reading the MVP screen)
        // before wiping it, so the ceremony never happens over a bottomless pit.
        for (Slot s : slots.values()) {
            if (s.dirty && s.revertAtTick < 0) {
                s.revertAtTick = serverTickCounter + Math.max(0, config().unpasteDelaySeconds()) * 20;
            }
            if (s.revertAtTick > 0 && serverTickCounter >= s.revertAtTick) {
                s.revertAtTick = -1;
                if (!isBusy(s)) {
                    enqueuePaste(s);
                }
            }
        }
        // ── Pre-warm ────────────────────────────────────────────────────────
        // Keep a handful of clean courts ready so the next players never wait for a paste. Cheap
        // (a paste is ~1 ms) and bounded, so it can never monopolise the tick.
        int warm = 0;
        for (Slot s : slots.values()) {
            if (s.pasted && !s.dirty) {
                warm++;
            }
        }
        if (warm < PREWARM_COURTS && pasteQueue.size() < PREWARM_COURTS) {
            for (Slot s : slots.values()) {
                if (!s.pasted && !isBusy(s) && s.pasteCursor == 0) {
                    enqueuePaste(s);
                    break;
                }
            }
        }
        // ── Cleanup of played-out courts ────────────────────────────────────
        // Any court a session has touched is dirty by definition (players may have built).
        for (Slot s : slots.values()) {
            refreshDirty(s);
        }
    }

    /**
     * Marks a court dirty as soon as a session has touched it. Sessions are created lazily and kept
     * around after they finish, so "a finished session exists" is not a reliable signal on its own:
     * what matters is whether this court has already hosted one.
     */
    private void refreshDirty(Slot slot) {
        MatchSession session = plugin.gameManager().getSessionByArena(slot.arena.id());
        if (session != null && session != slot.lastSession) {
            slot.lastSession = session;
            slot.dirty = true;
            slot.hasHostedSession = true;
            slot.revertAtTick = -1;
        }
    }

    private boolean isBusy(Slot slot) {
        MatchSession session = plugin.gameManager().getSessionByArena(slot.arena.id());
        return session != null && session.state() != MatchSession.State.FINISHED;
    }

    private void finishPaste(Slot slot) {
        slot.arena.geometry().setBakedCourtLines(true);
        slot.arena.spawnDisplays();
        plugin.getLogger().fine("[Courts] Court " + slot.arena.id() + " ready ("
            + schematic.nonAirBlocks() + " blocks).");
    }

    /** Drops items/arrows/bots left over from a previous match so they never reach the next one. */
    private void purgeLooseEntities(Slot slot) {
        CourtGeometry g = slot.arena.geometry();
        try {
            for (var entity : world.getNearbyEntities(
                new Location(world, g.centerX(), g.floorY() + 2, g.centerZ()),
                g.halfLength() + 6, 24, g.halfWidth() + 6)) {
                if (entity instanceof Player || entity instanceof org.bukkit.entity.ArmorStand) {
                    continue;
                }
                if (entity instanceof Item || entity instanceof org.bukkit.entity.Projectile
                    || entity instanceof org.bukkit.entity.FallingBlock) {
                    entity.remove();
                }
            }
        } catch (Throwable ignored) {
            // Best effort only — a failed cleanup must never stop the paste.
        }
    }

    /**
     * Diagnostic: re-pastes every court immediately (spread over ticks) and reports in chat.
     */
    public int repasteAll(Player requester) {
        if (!ready()) {
            return 0;
        }
        int queued = 0;
        for (Slot s : slots.values()) {
            if (!isBusy(s)) {
                enqueuePaste(s);
                queued++;
            }
        }
        if (requester != null) {
            Msg.send(requester, Msg.info("Re-pasting <white>" + queued + "</white> court(s) in the background…"));
        }
        return queued;
    }

    /** Number of courts currently hosting a live session (diagnostics / HUD). */
    public int busyCount() {
        int n = 0;
        for (Slot s : slots.values()) {
            if (isBusy(s)) {
                n++;
            }
        }
        return n;
    }

    public List<String> statusLines() {
        List<String> lines = new ArrayList<>();
        if (!enabled()) {
            lines.add("<gray>Instanced courts: <red>disabled</red></gray>");
            return lines;
        }
        if (schematic == null) {
            lines.add("<gray>Instanced courts: <red>" + (loadError == null ? "not loaded" : loadError) + "</red></gray>");
            return lines;
        }
        lines.add("<gray>Court world:</gray> <white>" + config().worldName() + "</white> <dark_gray>•</dark_gray> <gray>instances:</gray> <white>"
            + instanceCount() + "</white> <dark_gray>•</dark_gray> <gray>pasted:</gray> <white>" + pastedCount()
            + "</white> <dark_gray>•</dark_gray> <gray>busy:</gray> <white>" + busyCount() + "</white>");
        lines.add("<gray>Schematic:</gray> <white>" + schematic.name() + "</white> <dark_gray>"
            + schematic.width() + "x" + schematic.height() + "x" + schematic.length()
            + "</dark_gray> <gray>(" + schematic.nonAirBlocks() + " blocks, Sponge v"
            + schematic.formatVersion() + ")</gray>");
        Arena first = allCourts().isEmpty() ? null : allCourts().iterator().next();
        if (first != null) {
            CourtGeometry g = first.geometry();
            lines.add("<gray>Court #1 centre:</gray> <white>" + (int) g.centerX() + ", " + (int) g.floorY()
                + ", " + (int) g.centerZ() + "</white> <dark_gray>• hoop axis "
                + (g.northHoopRim().getBlockX() != g.southHoopRim().getBlockX() ? "east-west" : "north-south")
                + " • rims Y" + (int) g.northHoopRim().getY() + "</dark_gray>");
            lines.add("<gray>Playable rectangle:</gray> <white>" + (g.maxX() - g.minX()) + " x "
                + (g.maxZ() - g.minZ()) + "</white> <dark_gray>(auto-fitted to the build)</dark_gray>");
        }
        return lines;
    }

}
