package fr.bosketball.game;

import fr.bosketball.model.GameQueueMode;

public enum GameModeType {
    DUEL_1V1("1v1", "Ranked 1v1", 1, 2, true, false),
    STREET_2V2("2v2", "Ranked 2v2", 2, 2, true, false),
    PRO_3V3("3v3", "Ranked 3v3", 3, 4, true, false),
    FULL_5V5("5v5", "Ranked 5v5", 5, 4, true, false),
    CASUAL_1V1("casual-1v1", "Casual 1v1", 1, 2, true, true),
    CASUAL_3V3("casual-3v3", "Casual 3v3", 3, 4, true, true),
    CASUAL_5V5("casual-5v5", "Casual 5v5", 5, 4, true, true),
    THREE_POINT_CONTEST("3pt", "3-Point Contest", 1, 1, false, false),
    PRACTICE("practice", "Practice", 1, 1, false, false);

    private final String id;
    private final String displayTitle;
    private final int playersPerTeam;
    private final int minTotalPlayersToStart;
    private final boolean competitiveTeamMatch;
    private final boolean casual;

    GameModeType(String id, String displayTitle, int playersPerTeam, int minTotalPlayersToStart,
                 boolean competitiveTeamMatch, boolean casual) {
        this.id = id;
        this.displayTitle = displayTitle;
        this.playersPerTeam = playersPerTeam;
        this.minTotalPlayersToStart = minTotalPlayersToStart;
        this.competitiveTeamMatch = competitiveTeamMatch;
        this.casual = casual;
    }

    public String id() {
        return id;
    }

    public String smallCapsTitle() {
        return fr.bosketball.util.Glyphs.smallCaps(displayTitle);
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

    public boolean casual() {
        return casual;
    }

    public String rosterKey() {
        return playersPerTeam + "v" + playersPerTeam;
    }

    public GameQueueMode queueMode() {
        return casual ? GameQueueMode.CASUAL : GameQueueMode.RANKED;
    }

    public GameModeType casualTwin() {
        return switch (this) {
            case DUEL_1V1 -> CASUAL_1V1;
            case PRO_3V3 -> CASUAL_3V3;
            case FULL_5V5 -> CASUAL_5V5;
            case STREET_2V2 -> CASUAL_1V1;
            default -> this;
        };
    }

    public GameModeType rankedTwin() {
        return switch (this) {
            case CASUAL_1V1 -> DUEL_1V1;
            case CASUAL_3V3 -> PRO_3V3;
            case CASUAL_5V5 -> FULL_5V5;
            default -> this;
        };
    }

    public static GameModeType forRosterAndQueue(int playersPerTeam, GameQueueMode queueMode) {
        boolean casual = queueMode == GameQueueMode.CASUAL;
        return switch (playersPerTeam) {
            case 1 -> casual ? CASUAL_1V1 : DUEL_1V1;
            case 2 -> casual ? CASUAL_1V1 : STREET_2V2;
            case 3 -> casual ? CASUAL_3V3 : PRO_3V3;
            default -> casual ? CASUAL_5V5 : FULL_5V5;
        };
    }

    public int maxPlayers() {
        return competitiveTeamMatch ? playersPerTeam * 2 : 1;
    }

    public static GameModeType byIdOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String norm = raw.trim().toLowerCase(java.util.Locale.ROOT);
        for (GameModeType m : values()) {
            if (m.id.equals(norm) || m.name().equalsIgnoreCase(norm)) {
                return m;
            }
        }
        return null;
    }
}
