package fr.bosketball.game;

/**
 * Supported BosketBall Game Modes.
 */
public enum GameModeType {
    DUEL_1V1("1v1", "Ranked 1v1", 1, 2, true),
    STREET_2V2("2v2", "Ranked 2v2", 2, 2, true),
    PRO_3V3("3v3", "Ranked 3v3", 3, 4, true),
    FULL_5V5("5v5", "Ranked 5v5", 5, 4, true),
    THREE_POINT_CONTEST("3pt", "3-Point Contest", 1, 1, false),
    PRACTICE("practice", "Practice", 1, 1, false);

    private final String id;
    private final String displayTitle;
    private final int playersPerTeam;
    private final int minTotalPlayersToStart;
    private final boolean competitiveTeamMatch;

    GameModeType(String id, String displayTitle, int playersPerTeam, int minTotalPlayersToStart, boolean competitiveTeamMatch) {
        this.id = id;
        this.displayTitle = displayTitle;
        this.playersPerTeam = playersPerTeam;
        this.minTotalPlayersToStart = minTotalPlayersToStart;
        this.competitiveTeamMatch = competitiveTeamMatch;
    }

    public String id() {
        return id;
    }

    public String smallCapsTitle() {
        return displayTitle;
    }

    public String plainName() {
        return displayTitle;
    }

    public int playersPerTeam() {
        return playersPerTeam;
    }

    public int minTotalPlayersToStart() {
        return minTotalPlayersToStart;
    }

    public boolean competitiveTeamMatch() {
        return competitiveTeamMatch;
    }

    public int maxPlayers() {
        return competitiveTeamMatch ? playersPerTeam * 2 : 1;
    }

    public static GameModeType byIdOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String norm = raw.trim().toLowerCase();
        for (GameModeType m : values()) {
            if (m.id.equals(norm) || m.name().equalsIgnoreCase(norm)) {
                return m;
            }
        }
        return null;
    }
}
