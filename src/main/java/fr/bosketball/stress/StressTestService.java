package fr.bosketball.stress;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Synthetic load test: simulates the per-player work the plugin does every tick so the server can
 * be stress-tested with zero real players.
 *
 * <p>What is simulated <b>for real</b> (same code paths as production): the lobby sidebar renderer
 * (rank resolution, ELO line, season provider, party/friend lookup, glyph markup parsing, scoreboard
 * objective/team/score churn for every simulated player). What is <b>not</b> simulated: network
 * packets, chunk sending and Mojang authentication \u2014 those live on the Netty threads, not on the
 * server thread this test measures.</p>
 *
 * <p>Nothing is written to disk: the simulated players are throw-away {@link PlayerProfile}
 * instances that never enter the stats store, and every spawned entity is tagged and removed.</p>
 */
public final class StressTestService {

    public static final String STRESS_TAG = "bosketball_stress";

    private static final int TICKS_PER_REAL_TICK = 10;
    /** Simulated ticks played before the clock starts: JIT and the MiniMessage cache warm up here. */
    static final int WARMUP_TICKS = 40;

    private final BosketBallPlugin plugin;
    private final List<Entity> spawnedEntities = new ArrayList<>();

    private BukkitTask task;
    private List<PlayerProfile> fleet = List.of();
    private List<Scoreboard> boards = List.of();
    private int simulatedTicks;
    private int simulatedTick;
    private long sidebarNanos;
    private long renders;
    private long wallStartNanos;
    private double tickTimeBefore;
    private long heapBefore;

    public StressTestService(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return task != null;
    }

    /** Simulated ticks that actually count, warm-up excluded. Pure, unit-tested. */
    public static int measuredTicks(int total, int warmup) {
        return Math.max(1, total - Math.max(0, warmup));
    }

    /** Budget share of a 50 ms tick, in percent. Pure, unit-tested. */
    public static double budgetShare(double msPerTick) {
        return msPerTick / 50.0 * 100.0;
    }

    /** Human verdict for a projected per-tick cost. Pure, unit-tested. */
    public static String verdict(double msPerTick) {
        double share = budgetShare(msPerTick);
        if (share <= 5.0) {
            return "<#4ADE80>\u2705 comfortable</#4ADE80>";
        }
        if (share <= 20.0) {
            return "<#FBBF24>\u26a0 noticeable but fine</#FBBF24>";
        }
        if (share <= 50.0) {
            return "<#FB923C>\u26a0 heavy</#FB923C>";
        }
        return "<#F87171>\u274c too expensive</#F87171>";
    }

    /**
     * Starts a run. {@code players} simulated players, {@code ticks} simulated game ticks
     * (400 = 20 s of play at 20 TPS), executed {@link #TICKS_PER_REAL_TICK} simulated ticks per
     * real tick so the server keeps ticking while the test runs.
     */
    public boolean start(CommandSender issuer, int players, int ticks) {
        if (task != null) {
            Msg.send(issuer, Msg.error("A stress test is already running \u2014 <white>/bb stress stop</white> first."));
            return false;
        }
        if (players < 1 || players > 500) {
            Msg.send(issuer, Msg.error("Player count must be between 1 and 500."));
            return false;
        }
        simulatedTicks = Math.max(20, Math.min(20_000, ticks));
        simulatedTick = 0;
        sidebarNanos = 0L;
        renders = 0L;
        wallStartNanos = System.nanoTime();
        heapBefore = usedHeap();
        tickTimeBefore = averageTickTime();

        this.pendingIssuer = issuer;
        fleet = buildFleet(players);
        boards = new ArrayList<>(players);
        var manager = Bukkit.getScoreboardManager();
        for (int i = 0; i < players; i++) {
            boards.add(manager != null ? manager.getNewScoreboard() : null);
        }
        spawnDisplayEntities(players * 6);

        Msg.send(issuer, Msg.info("<#38BDF8><bold>\u25b6 Stress test</bold></#38BDF8> <white>" + players
            + "</white> simulated players \u00b7 <white>" + simulatedTicks + "</white> simulated ticks ("
            + (simulatedTicks / 20) + " s of play) \u00b7 <white>" + spawnedEntities.size()
            + "</white> display entities"));
        Msg.send(issuer, "<gray>Sidebar renderer, rank/ELO/season lookups and scoreboard churn run for real;"
            + " packets and chunk sending cannot be simulated. Everything is wiped at the end.</gray>");

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::runBatch, 1L, 1L);
        return true;
    }

    private void runBatch() {
        try {
            for (int i = 0; i < TICKS_PER_REAL_TICK && simulatedTick < simulatedTicks; i++, simulatedTick++) {
                if (simulatedTick % 10 != 0) {
                    continue;
                }
                boolean counting = simulatedTick >= WARMUP_TICKS;
                for (int index = 0; index < fleet.size(); index++) {
                    PlayerProfile profile = fleet.get(index);
                    Scoreboard board = boards.get(index);
                    if (board == null) {
                        continue;
                    }
                    long started = System.nanoTime();
                    plugin.sidebarManager().simulateRender(
                        profile, profile.uuid(), profile.lastKnownName(), plugin.gameManager(), board);
                    if (counting) {
                        sidebarNanos += System.nanoTime() - started;
                        renders++;
                    }
                }
            }
            if (simulatedTick >= simulatedTicks) {
                finish();
            }
        } catch (Throwable failure) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "[Stress] test aborted", failure);
            abort("internal error");
        }
    }

    private void finish() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        double elapsedMs = (System.nanoTime() - wallStartNanos) / 1_000_000.0;
        double msPerTick = sidebarNanos / 1_000_000.0 / measuredTicks(simulatedTicks, WARMUP_TICKS);
        int players = fleet.size();
        double perRender = renders == 0 ? 0.0 : sidebarNanos / 1_000_000.0 / renders;
        int entities = spawnedEntities.size();
        double tickTimeDuring = averageTickTime();
        long heapAfter = usedHeap();

        removeEntities();
        forgetSimulatedPlayers();
        boards = List.of();

        CommandSender issuer = lastIssuer();
        List<String> lines = new ArrayList<>();
        lines.add(" <gray>Simulated players:</gray> <white>" + players + "</white>"
            + " <dark_gray>\u00b7</dark_gray> <gray>ticks:</gray> <white>" + simulatedTicks + "</white>"
            + " <dark_gray>\u00b7</dark_gray> <gray>measured after " + WARMUP_TICKS
            + " warm-up ticks</gray> <dark_gray>\u00b7</dark_gray> <gray>wall:</gray> <white>"
            + String.format(Locale.ROOT, "%.2f", elapsedMs / 1000.0) + " s</white>");
        lines.add(" <gray>Sidebar:</gray> <white>" + renders + "</white> renders <dark_gray>\u00b7</dark_gray> "
            + "<white>" + ms(msPerTick) + " ms/tick</white> <gray>at " + players + " players"
            + " (</gray><white>" + ms(perRender) + " ms</white><gray> per render)</gray>");
        lines.add(" <gray>Tick budget:</gray> <white>" + pct(budgetShare(msPerTick)) + "</white>"
            + " <gray>of 50 ms \u2192</gray> " + verdict(msPerTick));
        lines.add(" <gray>Display entities:</gray> <white>" + entities + "</white>"
            + " <dark_gray>\u00b7</dark_gray> <gray>avg tick:</gray> <white>" + ms(tickTimeDuring)
            + " ms</white> <gray>(before test " + ms(tickTimeBefore) + " ms)</gray>");
        lines.add(" <gray>TPS:</gray> <white>" + String.format(Locale.ROOT, "%.2f", Bukkit.getTPS()[0])
            + "</white> <dark_gray>\u00b7</dark_gray> <gray>heap:</gray> <white>"
            + mb(heapBefore) + " \u2192 " + mb(heapAfter) + " Mo</white>");
        lines.add(" <dark_gray>Note: packets, chunk sending and authentication run off the server"
            + " thread and are not part of this number.</dark_gray>");
        if (issuer != null) {
            Msg.sendFramedCard(issuer, "Stress test \u2014 " + players + " players", lines);
        }
        plugin.getLogger().info("[Stress] " + players + " players: sidebar " + ms(msPerTick)
            + " ms/tick (" + pct(budgetShare(msPerTick)) + " of the 50 ms budget), entities "
            + entities + ", avg tick " + ms(tickTimeDuring) + " ms.");
    }

    private CommandSender pendingIssuer;

    private CommandSender lastIssuer() {
        return pendingIssuer;
    }

    /** Aborts the current run and removes every simulated entity. */
    public void abort(String reason) {
        if (task != null) {
            task.cancel();
            task = null;
        }
        removeEntities();
        forgetSimulatedPlayers();
        boards = List.of();
        if (reason != null) {
            plugin.getLogger().warning("[Stress] aborted: " + reason);
        }
    }

    /** Drops the render cache of the simulated profiles so repeated runs do not grow it. */
    private void forgetSimulatedPlayers() {
        try {
            for (PlayerProfile profile : fleet) {
                plugin.sidebarManager().clearPlayerCache(profile.uuid());
            }
        } catch (RuntimeException ignored) {
            // the manager may already be gone during shutdown
        }
        fleet = List.of();
    }

    /** Safety net called on plugin disable and at startup: no stress entity may ever survive. */
    public static void purgeStressEntities() {
        try {
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity.getScoreboardTags().contains(STRESS_TAG)) {
                        entity.remove();
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // worlds may already be unloading
        }
    }

    /** Spreads the simulated population over the rank ladder so rank lookups are realistic. */
    public static int eloFor(int index, int total) {
        int low = 400;
        int high = 2400;
        if (total <= 1) {
            return low;
        }
        return low + (int) Math.round((high - low) * (index % total) / (double) (total - 1));
    }

    private List<PlayerProfile> buildFleet(int players) {
        List<PlayerProfile> out = new ArrayList<>(players);
        java.util.Random random = new java.util.Random(0xB05CE7L);
        for (int i = 0; i < players; i++) {
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Stress" + (i + 1));
            int elo = eloFor(i, players);
            profile.setElo(elo);
            profile.setCoins(500 + random.nextInt(20_000));
            profile.setWins(random.nextInt(400));
            profile.setLosses(random.nextInt(400));
            profile.setPlayTimeSeconds(600L + random.nextInt(400_000));
            out.add(profile);
        }
        return out;
    }

    private void spawnDisplayEntities(int count) {
        if (count <= 0) {
            return;
        }
        World world = courtsWorld();
        if (world == null) {
            return;
        }
        Location base = new Location(world, 0.5, world.getMinHeight() + 40, 0.5);
        for (int i = 0; i < Math.min(count, 6000); i++) {
            double dx = (i % 60) * 1.5;
            double dz = (i / 60) * 1.5;
            Location location = base.clone().add(dx, 0, dz);
            try {
                ItemDisplay display = world.spawn(location, ItemDisplay.class, entity -> {
                    entity.addScoreboardTag(STRESS_TAG);
                    entity.setPersistent(false);
                    entity.setItemStack(new ItemStack(Material.AMETHYST_SHARD));
                    entity.setBillboard(Display.Billboard.CENTER);
                });
                spawnedEntities.add(display);
            } catch (RuntimeException failure) {
                plugin.getLogger().warning("[Stress] could not spawn display entity: " + failure.getMessage());
                break;
            }
        }
    }

    private World courtsWorld() {
        String configured = plugin.settings() != null && plugin.settings().schematicCourts() != null
            ? plugin.settings().schematicCourts().worldName()
            : null;
        World world = configured != null ? Bukkit.getWorld(configured) : null;
        if (world != null) {
            return world;
        }
        for (World candidate : Bukkit.getWorlds()) {
            if (candidate.getEnvironment() == World.Environment.NORMAL) {
                return candidate;
            }
        }
        return null;
    }

    private void removeEntities() {
        for (Entity entity : spawnedEntities) {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        spawnedEntities.clear();
        purgeStressEntities();
    }

    private static double averageTickTime() {
        try {
            return Bukkit.getAverageTickTime();
        } catch (Throwable unsupported) {
            return 0.0;
        }
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static String ms(double value) {
        if (value >= 10.0) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        if (value >= 1.0) {
            return String.format(Locale.ROOT, "%.2f", value);
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String pct(double value) {
        return String.format(Locale.ROOT, "%.1f %%", value);
    }

    private static String mb(long bytes) {
        return String.valueOf(bytes / (1024L * 1024L));
    }
}
