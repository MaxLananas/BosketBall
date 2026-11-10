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

    /** Clamped, so a standings file with more wins than matches cannot print 180 %. */
    public double winRatePercent() {
        return matches <= 0 ? 0.0 : Math.clamp(wins * 100.0 / matches, 0.0, 100.0);
    }
}
