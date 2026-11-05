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
        final int index;
        final Arena arena;
        final int originX;
        final int originY;
        final int originZ;
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
    private volatile boolean bakedCourtLines;
    private volatile String loadError;
    private volatile World world;
    private long lastLoadAttemptMillis;
    private String lastLoggedError;
    private int serverTickCounter = 0;

    private static final long LOAD_RETRY_MILLIS = 10_000L;

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
        try {
            File file = resolveSchematicFile(cfg.schematic());
            schematic = CourtSchematic.load(file);
            schematic.setRotation(cfg.rotation());
            lastLoggedError = null;
            plugin.getLogger().info("[Courts] Loaded " + file.getName() + " — "
                + schematic.width() + "x" + schematic.height() + "x" + schematic.length()
                + ", " + schematic.nonAirBlocks() + " solid blocks, palette "
                + schematic.paletteSize() + ", " + schematic.footprintChunks() + " chunk(s)/court.");
            plugin.getLogger().info("[Courts] Orientation: " + schematic.rotationDegrees()
                + " deg rotation applied on paste - footprint " + schematic.pasteWidth() + "x"
                + schematic.pasteLength() + " - hoop axis "
                + (hoopAxisAlongX(cfg.hoopFacing(), cfg.facing(), schematic.pasteWidth(), schematic.pasteLength())
                    ? "east-west" : "north-south")
                + " - attachables placed in a second pass: " + schematic.attachableCount()
                + " - block entities to restore: " + schematic.blockEntities().size()
                + " (" + schematic.signCount() + " sign(s)).");
            plugin.getLogger().info("[Courts] Court centre: offset from the paste origin = "
                + String.format(java.util.Locale.ROOT, "%.2f, %.2f", schematic.localCenterX(), schematic.localCenterZ())
                + " (" + (schematic.hasCourtBox()
                    ? "middle of the coloured court " + schematic.courtLength() + "x" + schematic.courtWidth()
                        + ", " + schematic.courtPaintedBlocks() + " painted block(s)"
                    : "middle of the footprint - no coloured court detected") + ").");
            plugin.getLogger().info("[Courts] Calibration: deck surface = local Y "
                + schematic.floorOffsetAtCenter() + " (floor blocks at Y "
                + (schematic.floorOffsetAtCenter() - 1) + "), line blocks on the deck = "
                + schematic.deckLineMarkings() + ".");
        } catch (Throwable ex) {
            schematic = null;
            loadError = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();

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
        throw new IllegalStateException("terrain.schem not found — save it with //copy then //schem save terrain,"
            + " or drop it in " + new File(schematicsDir, bare + ".schem").getAbsolutePath());
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
            plugin.getLogger().info("[Courts] No terrain.schem on disk - using the bundled default court ("
                + target.getAbsolutePath() + "). Replace it with your own //schem save any time.");
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

        int floorY = cfg.originY();
        int markings = schem.deckLineMarkings();
        bakedCourtLines = cfg.flushFloor() && schem.deckHasLineMarkings();
        plugin.getLogger().info("[Courts] Court lines: "
            + (bakedCourtLines ? "painted in the build (no extra display)" : "drawn by the plugin"));
        if (bakedCourtLines) {
            plugin.getLogger().info("[Courts] Using the " + markings + " painted line block(s) baked in "
                + schem.name() + " - no overlay lines are spawned.");
        } else {
            plugin.getLogger().info("[Courts] No painted line markings on the deck of " + schem.name()
                + " (" + markings + " line block(s) found) - the plugin draws the court lines itself.");
        }
        int pasteOriginY = floorY - schem.floorOffsetAtCenter();
        if (pasteOriginY < -60) {
            plugin.getLogger().warning("[Courts] terrain.schem extends " + (-pasteOriginY)
                + " blocks below the configured floor — clamping. Raise schematic-courts.origin-y.");
            pasteOriginY = -60;
        }

        for (int i = 0; i < count; i++) {
            int col = i % columns;
            int row = i / columns;

            int originX = cfg.originX() + col * spacing;
            int originZ = cfg.originZ() + row * spacing;
            String id = baseId + "_" + (i + 1);

            Arena arena = new Arena(id, "Court #" + (i + 1), world.getName(),
                geometryFor(cfg, schem, originX, pasteOriginY, originZ, floorY));
            arena.setCustomMap(true);
            arena.setJumbotronEnabled(false);

            arena.geometry().setBakedCourtLines(bakedCourtLines);
            arena.setCourtLinesEnabled(true);
            slots.put(i + 1, new Slot(i + 1, arena, originX, pasteOriginY, originZ));
        }
        if (Math.max(schem.pasteWidth(), schem.pasteLength()) >= spacing - 8) {
            plugin.getLogger().warning("[Courts] spacing-blocks (" + spacing + ") is too small for a "
                + schem.pasteWidth() + "x" + schem.pasteLength() + " build — neighbouring courts will overlap. "
                + "Raise schematic-courts.spacing-blocks.");
        }
        plugin.getLogger().info("[Courts] " + count + " instance(s) laid out on a " + columns
            + "-column grid, spacing " + spacing + " blocks, world '" + world.getName() + "'.");
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

        final int budget = config().maxBlocksPerTick();
        Slot job = pasteQueue.peek();
        if (job != null && !isBusy(job)) {
            boolean writeAir = job.hasHostedSession || !config().skipEmptyAir();
            if (!job.mainPasted) {
                if (job.pasteCursor == 0) {
                    purgeLooseEntities(job);
                }
                CourtSchematic.PasteProgress progress = schematic.pasteSlice(
                    world, job.originX, job.originY, job.originZ, writeAir, budget, job.pasteCursor);
                job.pasteCursor = progress.nextIndex();
                if (progress.complete()) {
                    job.mainPasted = true;
                    job.attachCursor = 0;
                }
            } else {
                // Second pass: attachables (signs, torches, ladders, rails ...) land after their support.
                CourtSchematic.PasteProgress progress = schematic.pasteAttachables(
                    world, job.originX, job.originY, job.originZ, budget, job.attachCursor);
                job.attachCursor = progress.nextIndex();
                if (progress.complete()) {
                    schematic.closeDoors(world, job.originX, job.originY, job.originZ);
                    job.repairedBlocks = schematic.repairAttachables(
                        world, job.originX, job.originY, job.originZ);
                    if (job.repairedBlocks > 0) {
                        plugin.getLogger().info("[Courts] Court " + job.arena.id() + ": "
                            + job.repairedBlocks + " attachable block(s) had to be put back after the paste.");
                    }
                    job.restoredSigns = schematic.applyBlockEntities(
                        world, job.originX, job.originY, job.originZ);
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
        slot.arena.geometry().setBakedCourtLines(bakedCourtLines);
        slot.arena.spawnDisplays();
        plugin.getLogger().fine("[Courts] Court " + slot.arena.id() + " ready ("
            + schematic.nonAirBlocks() + " blocks, " + slot.restoredSigns + " sign(s) restored).");
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
        lines.add("<gray>Schematic:</gray> <white>" + schematic.name() + "</white> <dark_gray>"
            + schematic.width() + "x" + schematic.height() + "x" + schematic.length()
            + "</dark_gray> <gray>(" + schematic.nonAirBlocks() + " blocks, Sponge v"
            + schematic.formatVersion() + ", rotation " + schematic.rotationDegrees() + "°, "
            + schematic.signCount() + " panneaux)</gray>");
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
