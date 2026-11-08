package fr.bosketball.model;

public enum ProgressStat {
    MATCHES_PLAYED,
    MATCHES_WON,
    POINTS_SCORED,
    TWO_POINTERS,
    THREE_POINTERS,
    DUNKS,
    ASSISTS,
    REBOUNDS,
    STEALS,
    BLOCKS,
    ANKLE_BREAKERS,
    MVPS,
    COINS_EARNED,
    THREE_POINT_CONTESTS,
    THREE_POINT_CONTEST_SCORE,
    /**
     * Crates opened over the player's whole career.
     *
     * <p>Round 84: this used to be {@link #QUEUE_JOINS}, whose career value was
     * {@code dailyCratesOpened()} — today's counter, not a lifetime one. The "Crate Opener"
     * achievement therefore needed ten crates in a single day (impossible under a daily limit)
     * and the "Graduate" achievement unlocked on the first crate of the day instead of on
     * finishing the tutorial.
     */
    CRATES_OPENED,
    /** Whether the player has finished the tutorial. */
    TUTORIAL_FINISHED,
    QUEUE_JOINS,
    FRIENDS_ADDED,
    PARTY_MATCHES,
    CASUAL_MATCHES,
    RANKED_MATCHES,
    WIN_STREAK_BEST;

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
