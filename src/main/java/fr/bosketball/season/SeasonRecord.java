package fr.bosketball.season;

import java.util.UUID;

public final class SeasonRecord {

    private final UUID uuid;
    private String name;
    private int matches;
    private int wins;
    private int bestElo;
    private int placementsDone;
    private int coinsEarned;
    private int xpEarned;

    public SeasonRecord(UUID uuid, String name, int bestElo) {
        this.uuid = uuid;
        this.name = name == null || name.isBlank() ? "Player" : name;
        this.bestElo = SeasonMath.clampElo(bestElo);
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }

    public int matches() {
        return matches;
    }

    public void setMatches(int matches) {
        this.matches = Math.max(0, matches);
    }

    public int wins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = Math.max(0, wins);
    }

    public int losses() {
        return Math.max(0, matches - wins);
    }

    public int bestElo() {
        return bestElo;
    }

    public void setBestElo(int bestElo) {
        this.bestElo = SeasonMath.clampElo(bestElo);
    }

    public void noteElo(int elo) {
        this.bestElo = Math.max(this.bestElo, SeasonMath.clampElo(elo));
    }

    public void noteMatch(boolean won, int eloAfter) {
        this.matches++;
        if (won) {
            this.wins++;
        }
        this.placementsDone++;
        noteElo(eloAfter);
    }

    public int placementsDone() {
        return placementsDone;
    }

    public void setPlacementsDone(int placementsDone) {
        this.placementsDone = Math.max(0, placementsDone);
    }

    public int coinsEarned() {
        return coinsEarned;
    }

    public void addCoinsEarned(int coins) {
        if (coins > 0) {
            this.coinsEarned += coins;
        }
    }

    public int xpEarned() {
        return xpEarned;
    }

    public void addXpEarned(int xp) {
        if (xp > 0) {
            this.xpEarned += xp;
        }
    }

    public boolean hasPlayed() {
        return matches > 0;
    }

    public double winRatePercent() {
        return matches <= 0 ? 0.0 : (wins * 100.0 / matches);
    }

    public void resetForNewSeason(int startingElo) {
        this.matches = 0;
        this.wins = 0;
        this.placementsDone = 0;
        this.coinsEarned = 0;
        this.xpEarned = 0;
        this.bestElo = SeasonMath.clampElo(startingElo);
    }
}
