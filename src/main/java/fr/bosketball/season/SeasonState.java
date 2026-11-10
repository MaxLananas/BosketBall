package fr.bosketball.season;

public record SeasonState(int index, String name, long startedAtMillis, long endsAtMillis) {

    public static SeasonState fresh(int index, long startedAtMillis, long durationMillis) {
        long safeStart = startedAtMillis > 0 ? startedAtMillis : System.currentTimeMillis();
        return new SeasonState(index, SeasonMath.seasonName(index), safeStart, safeStart + Math.max(1L, durationMillis));
    }

    public long remainingMillis() {
        return Math.max(0L, endsAtMillis - System.currentTimeMillis());
    }

    public boolean isOver() {
        return System.currentTimeMillis() >= endsAtMillis;
    }

    public int progressPercent() {
        return SeasonMath.progressPercent(startedAtMillis, endsAtMillis, System.currentTimeMillis());
    }

    public String remainingLabel() {
        return SeasonMath.remainingLabel(remainingMillis());
    }
}
