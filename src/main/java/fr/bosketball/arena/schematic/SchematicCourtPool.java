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

public final class SchematicCourtPool {

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

    private static final class Slot {
        final Arena arena;
        final int originX;
        final int originY;
        final int originZ;
        /** Terrain of this slot: the pool holds several, and each instance keeps its own. */
        final CourtSchematic schematic;
        /** Index of that terrain in {@link #variants()} (0 = the first file of the config). */
        final int variant;
        /** True when this terrain already has its lines painted on the deck. */
        final boolean bakedLines;
        boolean pasted;
        boolean dirty;

        MatchSession lastSession;

        boolean hasHostedSession;
        int revertAtTick = -1;

        int pasteCursor = 0;
        int attachCursor = 0;
        boolean mainPasted;
        int restoredSigns;
        int repairedBlocks;

        Slot(Arena arena, int originX, int originY, int originZ,
             CourtSchematic schematic, int variant, boolean bakedLines) {
            this.arena = arena;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.schematic = schematic;
            this.variant = variant;
            this.bakedLines = bakedLines;
        }
    }

    private final BosketBallPlugin plugin;
    private final Map<Integer, Slot> slots = new LinkedHashMap<>();
    private final Deque<Slot> pasteQueue = new ArrayDeque<>();

    /** Every terrain of the pool, in config order. One entry = the historical single file. */
    private volatile List<CourtSchematic> variants = List.of();

    /** Primary terrain (the first one): kept for status output and single-file setups. */
    private volatile CourtSchematic schematic;

    private volatile String loadError;
    private volatile World world;
    private long lastLoadAttemptMillis;
    private String lastLoggedError;
    private int serverTickCounter = 0;
    private int watchdogCursor = 0;

    private static final long LOAD_RETRY_MILLIS = 10_000L;

    /** A slice above this always hurts, whatever the config says. */
    public static final int HARD_MAX_BLOCKS_PER_SLICE = 4_000;
    public static final int MIN_BLOCKS_PER_SLICE = 128;
    /** Every paste slice aims at this much server-thread time; the block count adapts to the CPU. */
    public static final double SLICE_TARGET_MS = 2.0;
    /** The very first slice is small: it teaches the throughput instead of freezing the tick. */
    public static final int FIRST_SLICE_BLOCKS = 512;
    /** How far a player must be from a court for its decoration watchdog to be pointless. */
    private static final double WATCHDOG_VIEW_RADIUS = 80.0;

    private int adaptiveSliceBlocks = FIRST_SLICE_BLOCKS;
    private double blocksPerMs = 0.0;
    private long lastSpikeWarnMillis = 0L;

    private static final int PREWARM_COURTS = 4;

    public SchematicCourtPool(BosketBallPlugin plugin) {
        this.plugin = plugin;
        this.lastLoadAttemptMillis = System.currentTimeMillis();
    }

    public BosketConfig.SchematicCourts config() {
        return plugin.settings().schematicCourts();
    }

    public boolean enabled() {
        return config().enabled();
    }

    public boolean ready() {
        return enabled() && schematic != null && !variants.isEmpty();
    }

    public CourtSchematic schematic() {
        return schematic;
    }

    /** Every terrain the pool can hand out (2 = the match lands on either one). */
    public List<CourtSchematic> variants() {
        return variants;
    }

    /**
     * Terrain of the 1-based instance {@code slotIndex}. The files alternate, so two neighbouring
     * courts are never the same build; matches additionally take a <b>random free</b> instance, so
     * each game lands on either terrain.
     */
    public static int variantIndexFor(int slotIndex, int variantCount) {
        if (variantCount <= 1) {
            return 0;
        }
        return Math.floorMod(slotIndex - 1, variantCount);
    }

    // -----------------------------------------------------------------------------------------
    // Round 41 — no two courts may ever touch.
    //
    // The owner's screen showed two builds fused into one: the grid spacing came from the config
    // and nothing checked it against the size of the builds, so a value left over from an older
    // install (64 blocks, the clamp minimum) laid a 101-block-long terrain on top of its neighbour.
    // Spacing is now a MINIMUM: the pool raises it to the largest build plus a margin, and the
    // layout additionally audits every pair of footprints and skips a court that would overlap.
    // All of it is pure so the whole contract is unit-tested without a server.
    // -----------------------------------------------------------------------------------------

    /** Blocks of void kept between two neighbouring builds. */
    public static final int COURT_MARGIN_BLOCKS = 32;

    /** Columns of the square grid that holds {@code count} courts. */
    public static int gridColumns(int count) {
        return Math.max(1, (int) Math.ceil(Math.sqrt(Math.max(1, count))));
    }

    /** Smallest spacing that keeps the largest build of the pool and its neighbour apart. */
    public static int requiredSpacing(int largestBuildSpan) {
        return Math.max(1, largestBuildSpan) + COURT_MARGIN_BLOCKS;
    }

    /** Effective spacing: the config value is a floor, never a way to make two courts overlap. */
    public static int effectiveSpacing(int configured, int largestBuildSpan) {
        return Math.max(Math.max(1, configured), requiredSpacing(largestBuildSpan));
    }

    /** True when two axis-aligned footprints share at least one block column. */
    public static boolean footprintsOverlap(
        int ax, int az, int aw, int al,
        int bx, int bz, int bw, int bl
    ) {
        return ax < bx + bw && bx < ax + aw && az < bz + bl && bz < az + al;
    }

    /** Uniform pick among the terrains of the pool: the owner asked for a real one-in-two. */
    public static int pickTerrain(int variantCount, java.util.Random random) {
        return variantCount <= 1 ? 0 : random.nextInt(variantCount);
    }

    /**
     * A random court of the wanted terrain. When that terrain has no free court left, any free
     * court is returned instead — a match must never be refused just because one build is busy.
     */
    public static <T> T pickFreeOfTerrain(
        java.util.List<T> free,
        java.util.function.ToIntFunction<T> variantOf,
        int wanted,
        java.util.Random random
    ) {
        if (free == null || free.isEmpty()) {
            return null;
        }
        java.util.List<T> same = new java.util.ArrayList<>(free.size());
        for (T item : free) {
            if (variantOf.applyAsInt(item) == wanted) {
                same.add(item);
            }
        }
        java.util.List<T> candidates = same.isEmpty() ? free : same;
        return candidates.get(random.nextInt(candidates.size()));
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

    public boolean bootstrap() {
        BosketConfig.SchematicCourts cfg = config();
        loadError = null;
        if (!cfg.enabled()) {
            plugin.getLogger().info("[Courts] schematic-courts.enabled=false — using generated stadiums.");
            return false;
        }

        if (busyCount() > 0) {
            loadError = "reload skipped (" + busyCount() + " court(s) in use) — the current setup stays active";
            plugin.getLogger().warning("[Courts] " + loadError + " — try again once they are free.");
            return ready();
        }
        // Every terrain of the pool is decoded once, then kept in flat arrays: pasting one court is
        // a plain array walk, so holding two or three builds costs a few MB and no I/O at all.
        List<CourtSchematic> loaded = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (String name : cfg.files()) {
            try {
                File file = resolveSchematicFile(name);
                CourtSchematic terrain = CourtSchematic.load(file);
                terrain.setRotation(cfg.rotation());
                loaded.add(terrain);
                logTerrain(cfg, file, terrain, loaded.size());
            } catch (Throwable ex) {
                String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                failures.add(name + ": " + reason);
                plugin.getLogger().warning("[Courts] Cannot load schematic '" + name + "': " + reason);
            }
        }
        if (loaded.isEmpty()) {
            variants = List.of();
            schematic = null;
            loadError = String.join(" | ", failures);
            if (!loadError.equals(lastLoggedError)) {
                lastLoggedError = loadError;
            }
            return false;
        }
        lastLoggedError = null;
        variants = List.copyOf(loaded);
        schematic = variants.get(0);
        if (!failures.isEmpty()) {
            plugin.getLogger().warning("[Courts] " + failures.size() + " terrain(s) of the pool could"
                + " not be loaded and are skipped: " + String.join(" | ", failures));
        }
        if (variants.size() > 1) {
            StringBuilder names = new StringBuilder();
            for (CourtSchematic terrain : variants) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(terrain.name());
            }
            plugin.getLogger().info("[Courts] " + variants.size() + " terrains rotate through the pool: "
                + names + " — each match takes a random free court, so it lands on either build.");
        }
        SchematicHeads.attach(plugin, headNames(variants), cfg.headSkins());

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

    private void logTerrain(BosketConfig.SchematicCourts cfg, File file, CourtSchematic terrain, int index) {
        plugin.getLogger().info("[Courts] Loaded " + (index > 1 ? "#" + index + " " : "") + file.getName()
            + " — " + terrain.width() + "x" + terrain.height() + "x" + terrain.length()
            + ", " + terrain.nonAirBlocks() + " solid blocks, palette "
            + terrain.paletteSize() + ", " + terrain.footprintChunks() + " chunk(s)/court.");
        plugin.getLogger().info("[Courts]   orientation: " + terrain.rotationDegrees()
            + " deg rotation applied on paste - footprint " + terrain.pasteWidth() + "x"
            + terrain.pasteLength() + " - hoop axis "
            + (hoopAxisAlongX(cfg.hoopFacing(), cfg.facing(), terrain.pasteWidth(), terrain.pasteLength())
                ? "east-west" : "north-south")
            + " - attachables placed in a second pass: " + terrain.attachableCount()
            + " - block entities to restore: " + terrain.blockEntities().size()
            + " (" + terrain.signCount() + " sign(s), " + terrain.headCount() + " custom head(s)).");
        plugin.getLogger().info("[Courts]   court centre: offset from the paste origin = "
            + String.format(java.util.Locale.ROOT, "%.2f, %.2f", terrain.localCenterX(), terrain.localCenterZ())
            + " (" + (terrain.hasCourtBox()
                ? "middle of the coloured court " + terrain.courtLength() + "x" + terrain.courtWidth()
                    + ", " + terrain.courtPaintedBlocks() + " painted block(s)"
                : "middle of the footprint - no coloured court detected") + ").");
        plugin.getLogger().info("[Courts]   calibration: deck surface = local Y "
            + terrain.floorOffsetAtCenter() + " (floor blocks at Y "
            + (terrain.floorOffsetAtCenter() - 1) + "), line blocks on the deck = "
            + terrain.deckLineMarkings() + ".");
    }

    private static List<String> headNames(List<CourtSchematic> terrains) {
        List<String> names = new ArrayList<>();
        for (CourtSchematic terrain : terrains) {
            for (String name : terrain.headNames()) {
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

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
        File extracted = extractBundledSchematic(schematicsDir, bare);
        if (extracted != null) {
            return extracted;
        }
        throw new IllegalStateException(bare + ".schem not found — save it with //copy then //schem save "
            + bare + ", or drop it in " + new File(schematicsDir, bare + ".schem").getAbsolutePath());
    }

    private File extractBundledSchematic(File schematicsDir, String bare) {
        String resource = "/courts/" + bare + ".schem";
        try (java.io.InputStream in = SchematicCourtPool.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            File target = new File(schematicsDir, bare + ".schem");
            java.nio.file.Files.copy(in, target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("[Courts] No " + bare + ".schem on disk - using the bundled default"
                + " court (" + target.getAbsolutePath() + "). Replace it with your own //schem save"
                + " any time.");
            return target;
        } catch (Throwable ex) {
            plugin.getLogger().warning("[Courts] Could not extract the bundled court: " + ex.getMessage());
            return null;
        }
    }

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
        List<CourtSchematic> pool = variants;
        CourtSchematic primary = schematic;
        int count = cfg.instances();
        int columns = gridColumns(count);
        // Round 41: the config value is a MINIMUM. The largest build of the pool plus a margin
        // always wins, so a spacing left over from an older install can never fuse two courts.
        int largestBuild = 0;
        int widestBuild = 0;
        for (CourtSchematic terrain : pool) {
            largestBuild = Math.max(largestBuild, Math.max(terrain.pasteWidth(), terrain.pasteLength()));
            widestBuild = Math.max(widestBuild, terrain.pasteWidth());
        }
        int spacing = effectiveSpacing(cfg.spacingBlocks(), largestBuild);
        if (spacing > cfg.spacingBlocks()) {
            plugin.getLogger().warning("[Courts] spacing-blocks (" + cfg.spacingBlocks()
                + ") is smaller than the largest build (" + largestBuild + " blocks) — using "
                + spacing + " so no two courts can overlap. The value in config.yml is only a minimum.");
        }
        String baseId = cfg.worldName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");

        int floorY = cfg.originY();
        plugin.getLogger().info("[Courts] Ball cage: "
            + (primary.hasCeiling()
                ? "ceiling detected " + primary.ceilingAboveFloor() + " block(s) above the floor"
                : "no roof detected (default 16.5)"));
        for (CourtSchematic terrain : pool) {
            int markings = terrain.deckLineMarkings();
            boolean baked = cfg.flushFloor() && terrain.deckHasLineMarkings();
            plugin.getLogger().info("[Courts] Court lines (" + terrain.name() + "): "
                + (baked ? "painted in the build (" + markings + " line block(s), no extra display)"
                    : "drawn by the plugin (" + markings + " line block(s) found on the deck)"));
        }

        // Footprints already placed: a court that would touch one of them is skipped, never fused.
        List<Slot> placed = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int variant = variantIndexFor(i + 1, pool.size());
            CourtSchematic schem = pool.get(variant);
            int col = i % columns;
            int row = i / columns;

            int originX = cfg.originX() + col * spacing;
            int originZ = cfg.originZ() + row * spacing;
            String id = baseId + "_" + (i + 1);

            // Each terrain is shifted on its own so that ITS deck lands exactly on origin-y.
            int pasteOriginY = floorY - schem.floorOffsetAtCenter();
            if (pasteOriginY < -60) {
                plugin.getLogger().warning("[Courts] " + schem.name() + " extends " + (-pasteOriginY)
                    + " blocks below the configured floor — clamping. Raise schematic-courts.origin-y.");
                pasteOriginY = -60;
            }
            boolean baked = cfg.flushFloor() && schem.deckHasLineMarkings();

            Arena arena = new Arena(id, "Court #" + (i + 1), world.getName(),
                geometryFor(cfg, schem, originX, pasteOriginY, originZ, floorY));
            arena.setCustomMap(true);
            arena.setJumbotronEnabled(false);

            arena.geometry().setBakedCourtLines(baked);
            arena.setCourtLinesEnabled(true);
            Slot slot = new Slot(arena, originX, pasteOriginY, originZ, schem, variant, baked);
            if (overlapsAny(placed, slot)) {
                plugin.getLogger().warning("[Courts] Court #" + (i + 1) + " would overlap a court"
                    + " already placed — skipped. spacing-blocks " + spacing + " is too tight for the"
                    + " configured grid; the pool keeps " + placed.size() + " usable court(s).");
                continue;
            }
            placed.add(slot);
            slots.put(i + 1, slot);
        }
        int skipped = count - slots.size();
        plugin.getLogger().info("[Courts] " + slots.size() + " instance(s) laid out on a " + columns
            + "-column grid, spacing " + spacing + " blocks (largest build " + largestBuild
            + ", margin " + COURT_MARGIN_BLOCKS + "), world '" + world.getName() + "'."
            + (skipped > 0 ? " " + skipped + " skipped to avoid an overlap." : ""));
        if (slots.isEmpty()) {
            plugin.getLogger().severe("[Courts] No court could be placed without an overlap —"
                + " raise schematic-courts.spacing-blocks or lower instances.");
        }
    }

    /** True when {@code candidate} shares at least one block column with a court already placed. */
    private static boolean overlapsAny(List<Slot> placed, Slot candidate) {
        for (Slot other : placed) {
            if (footprintsOverlap(
                candidate.originX, candidate.originZ,
                candidate.schematic.pasteWidth(), candidate.schematic.pasteLength(),
                other.originX, other.originZ,
                other.schematic.pasteWidth(), other.schematic.pasteLength())) {
                return true;
            }
        }
        return false;
    }

    private CourtGeometry geometryFor(BosketConfig.SchematicCourts cfg, CourtSchematic schem,
                                      int originX, int pasteOriginY, int originZ, int floorY) {
        double centerX = originX + schem.localCenterX() + cfg.centerOffsetX();
        double centerZ = originZ + schem.localCenterZ() + cfg.centerOffsetZ();

        // The coloured court of the build wins over the bounding box: stands, tunnels and roofs
        // are not part of the playing area, and the basket axis is the court's long side.
        boolean alongX = schem.hasCourtBox()
            ? schem.courtAlongX()
            : hoopAxisAlongX(cfg.hoopFacing(), cfg.facing(), schem.pasteWidth(), schem.pasteLength());

        double halfBasketToBasket = cfg.halfLength();
        double halfAcross = cfg.halfWidth();
        if (schem.hasCourtBox()) {
            halfBasketToBasket = Math.min(halfBasketToBasket, schem.courtLength() / 2.0);
            halfAcross = Math.min(halfAcross, schem.courtWidth() / 2.0);
        }

        double[] spans = axisSpans(alongX, schem.pasteWidth(), schem.pasteLength(),
            halfBasketToBasket, halfAcross);
        double halfZ = spans[0];
        double halfX = spans[1];

        double halfBasketSpan = alongX ? halfX : halfZ;

        double floor = floorY + cfg.floorRelativeY();

        CourtGeometry geo = new CourtGeometry(centerX, floor, centerZ, halfZ, halfX);
        geo.setCourtBounds(centerX - halfX, centerZ - halfZ, centerX + halfX, centerZ + halfZ, floor);

        // The invisible cage stops right under the roof of the build, so a long shot can never
        // punch through the ceiling: it bounces 5 cm below it instead.
        double cageTop = floor + 16.5;
        if (schem.hasCeiling()) {
            cageTop = floor + schem.ceilingAboveFloor() - 0.05;
        }
        geo.setBarrierBounds(centerX - halfX - 0.5, centerZ - halfZ - 0.5,
            centerX + halfX + 0.5, centerZ + halfZ + 0.5, cageTop);

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

    public Arena acquireFreeArena(Collection<String> exclude) {
        if (!ready()) {
            return null;
        }

        boolean allowUnfinished = !config().placeholderUntilPasted();
        List<Slot> free = new ArrayList<>();
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
            free.add(s);
        }
        if (!free.isEmpty()) {
            // Round 41 — a real coin flip between the terrains, then a random free court inside
            // the chosen one: with two builds in `schematics:` half of the matches land on each,
            // instead of "whichever court happens to be free first".
            java.util.Random random = java.util.concurrent.ThreadLocalRandom.current();
            int wanted = pickTerrain(variants.size(), random);
            Slot picked = pickFreeOfTerrain(free, slot -> slot.variant, wanted, random);
            if (picked == null) {
                picked = free.get(random.nextInt(free.size()));
            }
            picked.arena.ensureDisplaysHealthy();
            return picked.arena;
        }

        for (Slot s : slots.values()) {
            refreshDirty(s);
            if (!isBusy(s) && (!s.pasted || s.dirty)) {
                enqueuePaste(s);
                return null;
            }
        }
        return null;
    }

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

    public void tick() {
        if (!enabled()) {
            return;
        }

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

        // Nothing pending, nobody in the courts world: the whole pool sleeps for free.
        if (pasteQueue.isEmpty() && !hasScheduledWork()
            && (world.getPlayers().isEmpty())) {
            return;
        }

        final int budget = nextSliceBudget();
        Slot job = pasteQueue.peek();
        if (job != null && !isBusy(job)) {
            boolean writeAir = job.hasHostedSession || !config().skipEmptyAir();
            CourtSchematic terrain = job.schematic;
            if (!job.mainPasted) {
                if (job.pasteCursor == 0) {
                    purgeLooseEntities(job);
                    terrain.resetRepairState();
                }
                long sliceStartedNs = System.nanoTime();
                CourtSchematic.PasteProgress progress = terrain.pasteSlice(
                    world, job.originX, job.originY, job.originZ, writeAir, budget, job.pasteCursor);
                long sliceNanos = System.nanoTime() - sliceStartedNs;
                int sliceBlocks = Math.max(0, progress.nextIndex() - job.pasteCursor);
                observeSlice(sliceBlocks, sliceNanos / 1_000_000.0, budget);
                warnOnSliceSpike(job, sliceBlocks, sliceNanos / 1_000_000.0);
                job.pasteCursor = progress.nextIndex();
                if (progress.complete()) {
                    job.mainPasted = true;
                    job.attachCursor = 0;
                }
            } else {
                // Second pass: attachables (signs, torches, ladders, rails ...) land after their support.
                long attachStartedNs = System.nanoTime();
                CourtSchematic.PasteProgress progress = terrain.pasteAttachables(
                    world, job.originX, job.originY, job.originZ, budget, job.attachCursor);
                int attachBlocks = Math.max(0, progress.nextIndex() - job.attachCursor);
                observeSlice(attachBlocks, (System.nanoTime() - attachStartedNs) / 1_000_000.0, budget);
                job.attachCursor = progress.nextIndex();
                if (progress.complete()) {
                    terrain.closeDoors(world, job.originX, job.originY, job.originZ);
                    job.repairedBlocks = terrain.repairAttachables(
                        world, job.originX, job.originY, job.originZ);
                    if (job.repairedBlocks > 0) {
                        plugin.getLogger().info("[Courts] Court " + job.arena.id() + ": "
                            + job.repairedBlocks + " attachable block(s) had to be put back after the paste.");
                    }
                    job.restoredSigns = terrain.applyBlockEntities(
                        world, job.originX, job.originY, job.originZ);
                    sweepDecorationItems(job);
                    pasteQueue.poll();
                    job.pasteCursor = 0;
                    job.attachCursor = 0;
                    job.mainPasted = false;
                    job.pasted = true;
                    job.dirty = false;
                    job.hasHostedSession = false;
                    job.revertAtTick = -1;
                    finishPaste(job);
                }
            }
        }

        runDecorationWatchdog();

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

        for (Slot s : slots.values()) {
            refreshDirty(s);
        }
    }

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
        slot.arena.geometry().setBakedCourtLines(slot.bakedLines);
        slot.arena.spawnDisplays();
        CourtSchematic terrain = slot.schematic;
        plugin.getLogger().fine("[Courts] Court " + slot.arena.id() + " ready ("
            + terrain.name() + ", " + terrain.nonAirBlocks() + " blocks, " + slot.restoredSigns
            + " block entit(ies) restored).");
        if (terrain.writeFailures() > 0) {
            plugin.getLogger().warning("[Courts] Court " + slot.arena.id() + " (" + terrain.name()
                + "): " + terrain.writeFailures() + " block(s) were refused by the server during the"
                + " paste. The court is still playable; check the console for the exact blocks.");
        }
    }

    /**
     * Keeps the imported decoration exactly as built. Every second one court is checked: any
     * sign/lever/torch/trapdoor that disappeared is put back with its exact state, and any
     * decoration item lying on the ground is removed.
     *
     * <p>Cheap by design: one court per second, ~360 blocks read, no physics.</p>
     */
    /** Slice size for the next tick: the config cap, shrunk to what 2 ms of CPU allows today. */
    public int nextSliceBudget() {
        int cap = Math.clamp(config().maxBlocksPerTick(), MIN_BLOCKS_PER_SLICE, HARD_MAX_BLOCKS_PER_SLICE);
        return Math.clamp(Math.min(cap, adaptiveSliceBlocks), MIN_BLOCKS_PER_SLICE, cap);
    }

    /** Pure: blocks that fit in one slice at the measured throughput. Unit-tested. */
    public static int adaptiveBudget(double blocksPerMs, int cap) {
        if (blocksPerMs <= 0.0) {
            return cap;
        }
        return Math.clamp((int) Math.round(blocksPerMs * SLICE_TARGET_MS), MIN_BLOCKS_PER_SLICE, cap);
    }

    /** Learns the real cost of a block on this CPU so slices land near {@link #SLICE_TARGET_MS}. */
    void observeSlice(int blocks, double millis, int cap) {
        if (blocks <= 0 || millis < 0.05) {
            return;
        }
        double sample = blocks / millis;
        blocksPerMs = blocksPerMs <= 0.0 ? sample : blocksPerMs * 0.7 + sample * 0.3;
        adaptiveSliceBlocks = adaptiveBudget(blocksPerMs, cap);
    }

    private void warnOnSliceSpike(Slot job, int blocks, double millis) {
        if (millis < SLICE_TARGET_MS * 4) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSpikeWarnMillis < 60_000L) {
            return;
        }
        lastSpikeWarnMillis = now;
        plugin.getLogger().warning("[Courts] Court " + job.arena.id() + ": a paste slice of " + blocks
            + " block(s) took " + Math.round(millis) + " ms (target " + (int) SLICE_TARGET_MS
            + " ms) — the slice is auto-tuned down; lower schematic-courts.max-blocks-per-tick"
            + " to cap it harder.");
    }

    private boolean hasScheduledWork() {
        for (Slot s : slots.values()) {
            if (s.dirty || s.revertAtTick > 0) {
                return true;
            }
        }
        return false;
    }

    private void runDecorationWatchdog() {
        if (schematic == null || world == null || slots.isEmpty()) {
            return;
        }
        if (serverTickCounter % 20 != 0) {
            return;
        }
        // A broken torch nobody can see costs a full court scan every second: skip the whole pass.
        java.util.List<Player> viewers = world.getPlayers();
        if (viewers.isEmpty()) {
            return;
        }
        java.util.List<Slot> visited = new ArrayList<>();
        for (Slot s : slots.values()) {
            if (s.pasted && !s.dirty && !isBusy(s) && seenFrom(viewers, s)) {
                visited.add(s);
            }
        }
        if (visited.isEmpty()) {
            return;
        }
        Slot slot = visited.get(watchdogCursor % visited.size());
        watchdogCursor++;
        try {
            CourtSchematic terrain = slot.schematic;
            int repaired = terrain.repairAttachables(
                world, slot.originX, slot.originY, slot.originZ);
            // Signs restart blank when they are put back and a head skin may only have been
            // resolved in the background since the paste: re-apply the payloads every pass
            // (a few blocks, no I/O) so nothing is ever left without its text or its skin.
            terrain.applyBlockEntities(world, slot.originX, slot.originY, slot.originZ);
            if (repaired > 0) {
                plugin.getLogger().info("[Courts] Watchdog: " + repaired
                    + " decoration block(s) restored on court " + slot.arena.id() + ".");
            }
            sweepDecorationItems(slot);
        } catch (Throwable ignored) {
            // never let a watchdog pass break the court tick
        }
    }

    /** Removes decoration items that ended up on the floor of a court. */
    private boolean seenFrom(java.util.List<Player> viewers, Slot slot) {
        for (Player player : viewers) {
            if (player.getWorld() != world) {
                continue;
            }
            double dx = player.getX() - slot.originX;
            double dz = player.getZ() - slot.originZ;
            if (dx * dx + dz * dz <= WATCHDOG_VIEW_RADIUS * WATCHDOG_VIEW_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private void sweepDecorationItems(Slot slot) {
        CourtGeometry g = slot.arena.geometry();
        try {
            for (var entity : world.getNearbyEntities(
                new Location(world, g.centerX(), g.floorY() + 3, g.centerZ()),
                g.halfLength() + 4, 22, g.halfWidth() + 4)) {
                if (!(entity instanceof Item item)) {
                    continue;
                }
                org.bukkit.Material type = item.getItemStack().getType();
                if (fr.bosketball.server.ServerProtectionManager.isCourtDecoration(type)) {
                    item.remove();
                }
            }
        } catch (Throwable ignored) {
            // optional cleanup only
        }
    }

    /** True when the given world is the instanced court world owned by this pool. */
    public boolean isCourtWorld(World other) {
        return other != null && world != null && world.equals(other);
    }

    /** Signs are part of the imported build: they are never breakable by regular players. */
    public boolean isProtectedSignBlock(org.bukkit.block.Block block) {
        if (block == null || !isCourtWorld(block.getWorld())) {
            return false;
        }
        return org.bukkit.Tag.ALL_SIGNS.isTagged(block.getType());
    }

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

        }
    }

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
        for (int i = 0; i < variants.size(); i++) {
            CourtSchematic terrain = variants.get(i);
            int slotsOnThisTerrain = 0;
            for (Slot slot : slots.values()) {
                if (slot.schematic == terrain) {
                    slotsOnThisTerrain++;
                }
            }
            lines.add((i == 0 ? "<gray>Terrain:</gray> " : "<gray>       +</gray> ")
                + "<white>" + terrain.name() + "</white> <dark_gray>"
                + terrain.width() + "x" + terrain.height() + "x" + terrain.length()
                + "</dark_gray> <gray>(" + terrain.nonAirBlocks() + " blocks, Sponge v"
                + terrain.formatVersion() + ", rotation " + terrain.rotationDegrees() + "°, "
                + terrain.signCount() + " signs, " + terrain.headCount() + " heads"
                + (variants.size() > 1 ? ", " + slotsOnThisTerrain + " court(s)" : "")
                + ")</gray>");
        }
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
