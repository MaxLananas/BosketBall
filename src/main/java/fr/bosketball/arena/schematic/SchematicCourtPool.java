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
    private int serverTickCounter = 0;

    public SchematicCourtPool(BosketBallPlugin plugin) {
        this.plugin = plugin;
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
        try {
            File file = resolveSchematicFile(cfg.schematic());
            schematic = CourtSchematic.load(file);
            plugin.getLogger().info("[Courts] Loaded " + file.getName() + " — "
                + schematic.width() + "x" + schematic.height() + "x" + schematic.length()
                + ", " + schematic.nonAirBlocks() + " solid blocks, palette "
                + schematic.paletteSize() + ", " + schematic.footprintChunks() + " chunk(s)/court.");
        } catch (Throwable ex) {
            schematic = null;
            loadError = ex.getMessage();
            plugin.getLogger().warning("[Courts] Cannot load schematic '" + cfg.schematic() + "': "
                + ex.getMessage());
            return false;
        }

        world = ensureWorld(cfg);
        if (world == null) {
            loadError = "could not create world " + cfg.worldName();
            return false;
        }
        buildSlots(cfg);
        if (cfg.pasteOnEnable()) {
            for (Slot s : slots.values()) {
                enqueuePaste(s);
            }
        }
        return true;
    }

    /** Finds {@code terrain.schem} in the plugin folder, its {@code schematics/} subfolder or {@code plugins/}. */
    private File resolveSchematicFile(String configured) {
        File dataFolder = plugin.getDataFolder();
        List<File> candidates = new ArrayList<>();
        candidates.add(new File(dataFolder, configured));
        candidates.add(new File(new File(dataFolder, "schematics"), configured));
        candidates.add(new File(new File(dataFolder, "schematics"), configured + ".schem"));
        candidates.add(new File(configured));
        if (!configured.toLowerCase(Locale.ROOT).endsWith(".schem")
            && !configured.toLowerCase(Locale.ROOT).endsWith(".schematic")) {
            candidates.add(new File(dataFolder, configured + ".schem"));
            candidates.add(new File(new File(dataFolder, "schematics"), configured + ".schem"));
        }
        // Zero-configuration migration path: FAWE/WorldEdit keep their own schematic folder, so a
        // file the admin just saved with //schem save is picked up without moving it by hand.
        String bare = configured.endsWith(".schem") || configured.endsWith(".schematic")
            ? configured.substring(0, configured.lastIndexOf('.')) : configured;
        File pluginsDir = dataFolder.getParentFile();
        if (pluginsDir != null) {
            for (String editor : new String[]{"FastAsyncWorldEdit", "WorldEdit"}) {
                File dir = new File(new File(pluginsDir, editor), "schematics");
                candidates.add(new File(dir, configured));
                candidates.add(new File(dir, bare + ".schem"));
            }
        }
        for (File f : candidates) {
            if (f.isFile()) {
                return f;
            }
        }
        File schematicsDir = new File(dataFolder, "schematics");
        if (!schematicsDir.isDirectory() && !schematicsDir.mkdirs()) {
            plugin.getLogger().warning("[Courts] Could not create " + schematicsDir);
        }
        throw new IllegalStateException("Schematic not found — put it in " + schematicsDir.getAbsolutePath()
            + File.separator + bare + ".schem  (//schem save terrain does this for you when the plugin"
            + " folder is symlinked, otherwise copy it there)");
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

        for (int i = 0; i < count; i++) {
            int col = i % columns;
            int row = i / columns;
            int originX = cfg.originX() + col * spacing;
            int originZ = cfg.originZ() + row * spacing;
            int originY = cfg.originY();
            String id = baseId + "_" + (i + 1);

            Arena arena = new Arena(id, "Court #" + (i + 1), world.getName(), geometryFor(cfg, schem, originX, originY, originZ));
            arena.setCustomMap(true);
            arena.setJumbotronEnabled(false);
            // The pasted build already contains the painted lines: spawning ~80 extra flat
            // BlockDisplays per court would be pure overhead at 50 instances.
            arena.geometry().setBakedCourtLines(true);
            arena.setCourtLinesEnabled(cfg.flushFloor());
            slots.put(i + 1, new Slot(i + 1, arena, originX, originY, originZ));
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
     * Builds the physics geometry for one instance: court bounds auto-fitted to the build footprint,
     * both rims derived from the floor Y and the hoop axis, and a barrier cage tight around the map.
     */
    private CourtGeometry geometryFor(BosketConfig.SchematicCourts cfg, CourtSchematic schem,
                                      int originX, int originY, int originZ) {
        boolean eastWest = schem.width() > schem.length();
        double spanU = eastWest ? schem.width() : schem.length();   // hoop-to-hoop axis
        double spanV = eastWest ? schem.length() : schem.width();   // across the court
        double centerX = originX + schem.width() * 0.5 + cfg.centerOffsetX();
        double centerZ = originZ + schem.length() * 0.5 + cfg.centerOffsetZ();

        double halfLength = Math.max(4.0, spanU * 0.5 - 1.0);
        double halfWidth = Math.max(4.0, spanV * 0.5 - 1.0);

        // floorY is auto-calibrated from the build itself (top solid block of the centre column),
        // then shifted by the manual fine-tune offset.
        double floor = originY + schem.floorOffsetAtCenter() + cfg.floorRelativeY();

        CourtGeometry geo = new CourtGeometry(centerX, floor, centerZ, halfLength, halfWidth);

        // Physics bounds = the actual playable rectangle, not the default stadium envelope.
        geo.setCourtBounds(centerX - halfLength, centerZ - halfWidth, centerX + halfLength, centerZ + halfWidth, floor);

        double rimInset = Math.max(0.5, Math.min(cfg.rimInset(), halfLength - 1.0));
        double rimY = floor + cfg.rimRelativeY();
        // Hoop axis. Explicit `hoop-facing` wins; otherwise we use the direction the admin was
        // facing when copying ("west" -> the baskets sit on the X axis), and only fall back to
        // "longest side = hoop-to-hoop axis" when neither is conclusive.
        String axis = cfg.hoopFacing().toLowerCase(Locale.ROOT);
        boolean alongX;
        if (axis.startsWith("auto")) {
            String facing = cfg.facing().toLowerCase(Locale.ROOT);
            if (facing.startsWith("east") || facing.startsWith("west")) {
                alongX = true;
            } else if (facing.startsWith("north") || facing.startsWith("south")) {
                alongX = false;
            } else {
                alongX = eastWest;
            }
        } else {
            alongX = axis.startsWith("east") || axis.startsWith("west") || axis.startsWith("x");
        }
        if (alongX) {
            geo.setNorthHoop(new Vector(centerX - halfLength + rimInset, rimY, centerZ), new Vector(1, 0, 0), true);
            geo.setSouthHoop(new Vector(centerX + halfLength - rimInset, rimY, centerZ), new Vector(-1, 0, 0), true);
        } else {
            geo.setNorthHoop(new Vector(centerX, rimY, centerZ - halfLength + rimInset), new Vector(0, 0, 1), true);
            geo.setSouthHoop(new Vector(centerX, rimY, centerZ + halfLength - rimInset), new Vector(0, 0, -1), true);
        }
        return geo;
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
        for (Slot s : slots.values()) {
            if (exclude != null && exclude.contains(s.arena.id())) {
                continue;
            }
            MatchSession session = plugin.gameManager().getSessionByArena(s.arena.id());
            boolean busy = session != null && session.state() != MatchSession.State.FINISHED;
            if (busy || s.dirty || !s.pasted || s.pasteCursor > 0) {
                continue;
            }
            s.arena.ensureDisplaysHealthy();
            return s.arena;
        }
        // Nothing clean left: paste the next dirty/never-pasted instance and try again next tick.
        for (Slot s : slots.values()) {
            MatchSession session = plugin.gameManager().getSessionByArena(s.arena.id());
            boolean busy = session != null && session.state() != MatchSession.State.FINISHED;
            if (!busy && (!s.pasted || s.dirty)) {
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
        if (!enabled() || world == null || schematic == null) {
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
            CourtSchematic.PasteProgress progress = schematic.pasteSlice(
                world, job.originX, job.originY, job.originZ, config().skipEmptyAir(), budget, job.pasteCursor);
            job.pasteCursor = progress.nextIndex();
            if (progress.complete()) {
                pasteQueue.poll();
                job.pasteCursor = 0;
                job.pasted = true;
                job.dirty = false;
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
        // Keep at least two clean courts ready so the next player never waits for a paste. Cheap
        // (a paste is ~1 ms) and bounded, so it can never monopolise the tick.
        int warm = 0;
        for (Slot s : slots.values()) {
            if (s.pasted && !s.dirty) {
                warm++;
            }
        }
        if (warm < 2 && pasteQueue.size() < 2) {
            for (Slot s : slots.values()) {
                if (!s.pasted && !isBusy(s) && s.pasteCursor == 0) {
                    enqueuePaste(s);
                    break;
                }
            }
        }
        // ── Cleanup of finished courts ──────────────────────────────────────
        // Any arena whose session emptied out is dirty by definition (players may have built).
        for (Slot s : slots.values()) {
            if (s.dirty) {
                continue;
            }
            MatchSession session = plugin.gameManager().getSessionByArena(s.arena.id());
            if (session != null && session.state() == MatchSession.State.FINISHED) {
                s.dirty = true;
            }
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
            + "</dark_gray> <gray>(" + schematic.nonAirBlocks() + " blocks)</gray>");
        return lines;
    }

}
