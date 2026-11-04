package fr.bosketball.storage;

import fr.bosketball.model.PlayerProfile;
import java.util.List;
import java.util.UUID;

/**
 * Storage abstraction sitting behind {@code StatsManager}.
 *
 * <p>The YAML backend is always authoritative (human readable, zero setup, survives any
 * crash). A SQL backend is an optional <em>mirror</em> that unlocks real {@code ORDER BY}
 * leaderboard queries, cross-server reads and external dashboards.</p>
 */
public interface ProfileBackend {

    /** Human readable backend name shown by {@code /bb status}. */
    String name();

    /** True when the backend is usable right now (driver present & connection alive). */
    boolean isAvailable();

    /** Creates the schema if needed. */
    void initialize();

    /** Mirrors a batch of profiles (called from the async IO thread). */
    void upsertAll(List<PlayerProfile> profiles);

    /** Batch upsert used at shutdown. */
    void flush();

    /** SQL-style ordered top-N; falls back to the in-memory index when unavailable. */
    List<Row> topBy(String column, int limit);

    /** Closes any pooled resource. */
    void shutdown();

    /** A projection row returned by the SQL backend. */
    record Row(UUID uuid, String username, int elo, int coins, int wins, int points, int dunks, int mvps, int assists) {
    }
}
