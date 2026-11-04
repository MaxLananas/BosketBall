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

    public void rebuildAll() {
        for (LeaderboardCategory category : LeaderboardCategory.values()) {
            rebuild(category);
        }
    }

    private void rebuildAllSafely() {
        try {
            rebuildAll();
        } catch (Throwable ignored) {

        }
    }

    public void rebuild(LeaderboardCategory category) {
        List<PlayerProfile> top = statsManager.topByUncached(category, cacheSize);
        cache.put(category, List.copyOf(top));
        lastBuiltAt.get(category).set(System.currentTimeMillis());
    }

    public List<PlayerProfile> top(LeaderboardCategory category) {
        List<PlayerProfile> snapshot = cache.get(category);
        if (snapshot == null || snapshot.isEmpty()) {
            rebuild(category);
            return cache.getOrDefault(category, List.of());
        }
        return snapshot;
    }

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
