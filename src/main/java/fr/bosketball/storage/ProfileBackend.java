package fr.bosketball.storage;

import fr.bosketball.model.PlayerProfile;
import java.util.List;
import java.util.UUID;

public interface ProfileBackend {

    String name();

    boolean isAvailable();

    void initialize();

    void upsertAll(List<PlayerProfile> profiles);

    void flush();

    List<Row> topBy(String column, int limit);

    void shutdown();

    record Row(UUID uuid, String username, int elo, int coins, int wins, int points, int dunks, int mvps, int assists) {
    }
}
