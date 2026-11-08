package fr.bosketball.season;

import fr.bosketball.model.Rank;

public record SeasonResult(
    int placementsDone,
    int placementsRequired,
    int matches,
    int wins,
    int bestElo,
    Rank tier,
    boolean provisional,
    boolean tierChanged,
    boolean newBest
) {

    public boolean justCompletedPlacements() {
        return placementsRequired > 0 && placementsDone == placementsRequired;
    }

    public int placementsRemaining() {
        return Math.max(0, placementsRequired - placementsDone);
    }
}
