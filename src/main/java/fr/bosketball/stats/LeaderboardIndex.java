package fr.bosketball.stats;

import fr.bosketball.model.PlayerProfile;
import fr.bosketball.stats.StatsManager.LeaderboardCategory;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory sorted leaderboard cache.
 *
 * <p>Wall holograms used to re-scan every stored profile on each refresh. This index keeps
 * the Top-N of every {@link LeaderboardCategory} pre-sorted and hands out immutable
 * snapshots in O(1), while a single background thread rebuilds it on a fixed schedule
 * (default every 5 seconds — configurable via {@code leaderboards.holo-refresh-seconds}).</p>
 */
public final class LeaderboardIndex {

    private final StatsManager statsManager;
    private final Map<LeaderboardCategory, List<PlayerProfile>> cache = new EnumMap<>(LeaderboardCategory.class);
    private final Map<LeaderboardCategory, AtomicLong> lastBuiltAt = new EnumMap<>(LeaderboardCategory.class);
    private volatile int cacheSize = 10;
    private volatile long refreshMillis = 5_000L;
    private ScheduledExecutorService scheduler;

    public LeaderboardIndex(StatsManager statsManager) {
        this.statsManager = statsManager;
        for (LeaderboardCategory c : LeaderboardCategory.values()) {
            cache.put(c, List.of());
            lastBuiltAt.put(c, new AtomicLong(0L));
        }
    }

    public void configure(int cacheSize, int refreshSeconds) {
        this.cacheSize = Math.max(3, cacheSize);
        this.refreshMillis = Math.max(1, refreshSeconds) * 1000L;
    }

    /** Starts the background rebuild loop. Safe to call twice. */
    public void start() {
        stop();
        long period = Math.max(1000L, refreshMillis);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BosketBall-LeaderboardIndex");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::rebuildAllSafely, 200L, period, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /** Rebuilds every category (bounded heap selection, no allocation per reader). */
    public void rebuildAll() {
        for (LeaderboardCategory category : LeaderboardCategory.values()) {
            rebuild(category);
        }
    }

    private void rebuildAllSafely() {
        try {
            rebuildAll();
        } catch (Throwable ignored) {
            // A leaderboard rebuild must never kill the scheduler thread
        }
    }

    public void rebuild(LeaderboardCategory category) {
        List<PlayerProfile> top = statsManager.topByUncached(category, cacheSize);
        cache.put(category, List.copyOf(top));
        lastBuiltAt.get(category).set(System.currentTimeMillis());
    }

    /** Immutable Top-N snapshot for a category (never null, may be empty). */
    public List<PlayerProfile> top(LeaderboardCategory category) {
        List<PlayerProfile> snapshot = cache.get(category);
        if (snapshot == null || snapshot.isEmpty()) {
            rebuild(category);
            return cache.getOrDefault(category, List.of());
        }
        return snapshot;
    }

    /** 1-based placement of a player in a category, or 0 when unranked. */
    public int placementOf(LeaderboardCategory category, java.util.UUID playerId) {
        int i = 1;
        for (PlayerProfile p : top(category)) {
            if (p.uuid().equals(playerId)) {
                return i;
            }
            i++;
        }
        return 0;
    }

    public long lastBuiltAt(LeaderboardCategory category) {
        AtomicLong at = lastBuiltAt.get(category);
        return at != null ? at.get() : 0L;
    }

    public int cacheSize() {
        return cacheSize;
    }

    public long refreshMillis() {
        return refreshMillis;
    }

    /** Flat summary used by the admin panel and the wall leaderboard header. */
    public List<String> summary(int limit) {
        List<String> out = new ArrayList<>();
        for (LeaderboardCategory c : LeaderboardCategory.values()) {
            List<PlayerProfile> top = top(c);
            StringBuilder sb = new StringBuilder(c.name()).append(": ");
            for (int i = 0; i < Math.min(limit, top.size()); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append("#").append(i + 1).append(' ').append(top.get(i).lastKnownName());
            }
            if (top.isEmpty()) {
                sb.append("—");
            }
            out.add(sb.toString());
        }
        return out;
    }
}
