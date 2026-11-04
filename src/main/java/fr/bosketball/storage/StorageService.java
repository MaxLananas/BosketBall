package fr.bosketball.storage;

import fr.bosketball.config.BosketConfig;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.stats.StatsManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Owns the optional SQL mirror and the rotating backups of {@code players/} + {@code arenas.yml}.
 *
 * <p>Design rule: <b>YAML stays the source of truth.</b> If the database is missing, slow or
 * broken, gameplay keeps working and the server merely logs a warning. That is what makes
 * this safe to enable on a live server.</p>
 */
public final class StorageService {

    private final Logger logger;
    private final File dataFolder;
    private final StatsManager statsManager;

    private volatile ProfileBackend backend;
    private volatile boolean mirrorEnabled = false;
    private volatile int syncIntervalSeconds = 60;
    private volatile int backupIntervalHours = 12;
    private volatile int backupKeep = 10;
    private ScheduledExecutorService scheduler;

    public StorageService(File dataFolder, StatsManager statsManager, Logger logger) {
        this.dataFolder = dataFolder;
        this.statsManager = statsManager;
        this.logger = logger;
    }

    /** Reads {@code storage:} from config and brings up the mirror + backup scheduler. */
    public void start(BosketConfig.Storage config) {
        stop();
        this.syncIntervalSeconds = Math.max(10, config.dbSyncIntervalSeconds());
        this.backupIntervalHours = config.backupIntervalHours();
        this.backupKeep = config.backupKeep();

        if (config.usesDatabase()) {
            boolean mysql = "mysql".equalsIgnoreCase(config.backend());
            String sqlitePath = new File(dataFolder, config.sqliteFile()).getAbsolutePath();
            ProfileBackend created = new SqlProfileBackend(
                logger, mysql, sqlitePath,
                config.dbHost(), config.dbPort(), config.dbName(), config.dbUser(), config.dbPassword()
            );
            created.initialize();
            this.backend = created;
            this.mirrorEnabled = created.isAvailable();
            if (mirrorEnabled) {
                created.upsertAll(new ArrayList<>(statsManager.allProfiles()));
            }
        } else {
            this.backend = null;
            this.mirrorEnabled = false;
        }

        if (!config.backupsEnabled()) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BosketBall-Storage");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tickSafely, 30L, 30L, TimeUnit.SECONDS);
        if (backupIntervalHours > 0) {
            scheduler.scheduleAtFixedRate(this::backupNowSafely, backupIntervalHours, backupIntervalHours, TimeUnit.HOURS);
        }
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (backend != null) {
            backend.shutdown();
            backend = null;
        }
        mirrorEnabled = false;
    }

    public boolean mirrorActive() {
        return mirrorEnabled && backend != null && backend.isAvailable();
    }

    public String backendName() {
        return backend == null ? "YAML" : backend.name() + (mirrorActive() ? " (mirror)" : " (offline)");
    }

    public ProfileBackend backend() {
        return backend;
    }

    private long lastSyncMillis = 0L;

    private void tickSafely() {
        if (!mirrorActive()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSyncMillis < syncIntervalSeconds * 1000L) {
            return;
        }
        lastSyncMillis = now;
        try {
            backend.upsertAll(new ArrayList<>(statsManager.allProfiles()));
        } catch (Throwable ignored) {
        }
    }

    /** Forces an immediate mirror sync (used by {@code /bb save}). */
    public void syncNow() {
        if (!mirrorActive()) {
            return;
        }
        lastSyncMillis = System.currentTimeMillis();
        backend.upsertAll(new ArrayList<>(statsManager.allProfiles()));
    }

    /** SQL-ordered leaderboard rows, or an empty list when the mirror is off. */
    public List<ProfileBackend.Row> queryTop(String column, int limit) {
        if (!mirrorActive()) {
            return List.of();
        }
        return backend.topBy(column, limit);
    }

    // =========================================================================
    //  BACKUPS
    // =========================================================================

    private void backupNowSafely() {
        try {
            backupNow();
        } catch (Throwable ex) {
            if (logger != null) {
                logger.warning("[storage] Backup failed: " + ex.getMessage());
            }
        }
    }

    /**
     * Zips {@code players/}, {@code arenas.yml}, {@code holograms.yml}, {@code shop.yml} and
     * {@code config.yml} into {@code backups/bosketball-YYYYMMDD-HHmm.zip}, then prunes old
     * archives beyond the retention count.
     */
    public File backupNow() throws IOException {
        File backupDir = new File(dataFolder, "backups");
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw new IOException("cannot create " + backupDir);
        }
        // Second resolution: two backups in the same minute used to overwrite each other and the
        // prune step then deleted the survivor, so a manual /bb backup right before a restart
        // could vanish entirely.
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File target = new File(backupDir, "bosketball-" + stamp + ".zip");
        int suffix = 2;
        while (target.exists()) {
            target = new File(backupDir, "bosketball-" + stamp + "-" + suffix++ + ".zip");
        }

        // Flush SYNCHRONOUSLY so the archive is current. flushDirtyAsync() only queued the writes
        // and returned immediately, so the zip captured the previous copy of every dirty profile.
        try {
            statsManager.save();
        } catch (Throwable ex) {
            if (logger != null) {
                logger.warning("[storage] Pre-backup flush failed: " + ex.getMessage());
            }
        }

        List<Path> sources = new ArrayList<>();
        File players = new File(dataFolder, "players");
        if (players.isDirectory()) {
            try (var stream = Files.walk(players.toPath())) {
                stream.filter(Files::isRegularFile).forEach(sources::add);
            }
        }
        for (String name : new String[]{"arenas.yml", "holograms.yml", "shop.yml", "permissions.yml", "guilds.yml", "config.yml"}) {
            Path p = new File(dataFolder, name).toPath();
            if (Files.isRegularFile(p)) {
                sources.add(p);
            }
        }
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target.toPath()))) {
            for (Path src : sources) {
                ZipEntry entry = new ZipEntry(dataFolder.toPath().relativize(src).toString().replace('\\', '/'));
                zos.putNextEntry(entry);
                Files.copy(src, zos);
                zos.closeEntry();
            }
        }
        pruneBackups(backupDir);
        if (logger != null) {
            logger.info("[storage] Backup written: " + target.getName() + " (" + sources.size() + " files).");
        }
        return target;
    }

    private void pruneBackups(File backupDir) {
        File[] files = backupDir.listFiles((d, n) -> n.startsWith("bosketball-") && n.endsWith(".zip"));
        if (files == null || files.length <= backupKeep) {
            return;
        }
        List<File> sorted = new ArrayList<>(List.of(files));
        sorted.sort(Comparator.comparingLong(File::lastModified).reversed());
        for (int i = backupKeep; i < sorted.size(); i++) {
            try {
                Files.deleteIfExists(sorted.get(i).toPath());
            } catch (IOException ignored) {
            }
        }
    }

    /** Restores the newest backup over the live data folder (staff command). */
    public File latestBackup() {
        File backupDir = new File(dataFolder, "backups");
        File[] files = backupDir.listFiles((d, n) -> n.startsWith("bosketball-") && n.endsWith(".zip"));
        if (files == null || files.length == 0) {
            return null;
        }
        List<File> sorted = new ArrayList<>(List.of(files));
        sorted.sort(Comparator.comparingLong(File::lastModified).reversed());
        return sorted.get(0);
    }
}
