package fr.bosketball.season;

import fr.bosketball.model.Rank;
import java.util.UUID;

public record SeasonStanding(
    int place,
    UUID uuid,
    String name,
    int bestElo,
    int matches,
    int wins,
    Rank tier,
    boolean provisional
) {

    public double winRatePercent() {
        return matches <= 0 ? 0.0 : (wins * 100.0 / matches);
    }
}
