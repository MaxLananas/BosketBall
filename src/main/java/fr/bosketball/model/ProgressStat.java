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
