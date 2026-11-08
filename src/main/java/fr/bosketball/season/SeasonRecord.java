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

    public void noteElo(int elo) {
        this.bestElo = Math.max(this.bestElo, SeasonMath.clampElo(elo));
    }

    public void noteMatch(boolean won, int eloAfter) {
        // Saturating counters: an `int` wrapped around after 2.1 billion matches in theory, and a
        // corrupted file could already hold a negative value that then won more games and showed
        // a negative match count in the rewards GUI.
        this.matches = saturatedAdd(this.matches, 1);
        if (won) {
            this.wins = saturatedAdd(this.wins, 1);
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
            this.coinsEarned = saturatedAdd(this.coinsEarned, coins);
        }
    }

    /**
     * {@code a + b} clamped into {@code [0, Integer.MAX_VALUE]}.
     *
     * <p>Round 42: the counters were plain {@code +=}. A season record is persisted and re-read, so
     * a corrupted or hostile value plus one more game wrapped the total negative and the rewards
     * GUI showed a negative "coins earned" for the whole season.
     */
    private static int saturatedAdd(int a, int b) {
        long sum = (long) a + (long) b;
        return (int) Math.max(0L, Math.min((long) Integer.MAX_VALUE, sum));
    }

    private static int saturate(int value) {
        return Math.max(0, value);
    }

    public int xpEarned() {
        return xpEarned;
    }

    public void addXpEarned(int xp) {
        if (xp > 0) {
            this.xpEarned = saturatedAdd(this.xpEarned, xp);
        }
    }

    public boolean hasPlayed() {
        return matches > 0;
    }

    public double winRatePercent() {
        if (matches <= 0) {
            return 0.0;
        }
        // wins can only exceed matches after a corrupted file; the rate is clamped instead of
        // showing 140 % in the profile card.
        double rate = wins * 100.0 / matches;
        return Math.max(0.0, Math.min(100.0, rate));
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
