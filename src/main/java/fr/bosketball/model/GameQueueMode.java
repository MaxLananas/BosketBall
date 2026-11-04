package fr.bosketball.model;

public enum GameQueueMode {

    RANKED("ranked", "ʀᴀɴᴋᴇᴅ", true, 1.0, "icon_trophy_gold"),
    CASUAL("casual", "ᴄᴀꜱᴜᴀʟ", false, 0.40, "icon_training");

    private final String id;
    private final String smallCapsTitle;
    private final boolean ranked;
    private final double baseRewardMultiplier;
    private final String iconModel;

    GameQueueMode(String id, String smallCapsTitle, boolean ranked, double baseRewardMultiplier, String iconModel) {
        this.id = id;
        this.smallCapsTitle = smallCapsTitle;
        this.ranked = ranked;
        this.baseRewardMultiplier = baseRewardMultiplier;
        this.iconModel = iconModel;
    }

    public String id() {
        return id;
    }

    public String smallCapsTitle() {
        return smallCapsTitle;
    }

    public boolean ranked() {
        return ranked;
    }

    public boolean affectsElo() {
        return ranked;
    }

    public double baseRewardMultiplier() {
        return baseRewardMultiplier;
    }

    public String iconModel() {
        return iconModel;
    }

    public GameQueueMode opposite() {
        return this == RANKED ? CASUAL : RANKED;
    }

    public static GameQueueMode byIdOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String norm = raw.trim();
        for (GameQueueMode m : values()) {
            if (m.id.equalsIgnoreCase(norm) || m.name().equalsIgnoreCase(norm)) {
                return m;
            }
        }
        return null;
    }

    public static GameQueueMode byIdOrDefault(String raw, GameQueueMode fallback) {
        GameQueueMode found = byIdOrNull(raw);
        return found != null ? found : fallback;
    }
}
