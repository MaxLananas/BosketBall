package fr.bosketball.game.bot;

public enum BotDifficulty {

    EASY(
        "ᴇᴀꜱʏ",
        "Easy",
        "relaxed pickup game",
        0.62,
        0.34,
        0.22,
        0.30,
        0.24,
        0.55,
        9,
        0.26,
        0.40,
        0x4ADE80,
        "icon_easy"
    ),
    NORMAL(
        "ɴᴏʀᴍᴀʟ",
        "Normal",
        "solid club-level hooper",
        0.78,
        0.46,
        0.34,
        0.44,
        0.38,
        0.74,
        5,
        0.14,
        0.55,
        0xFBBF24,
        "icon_normal"
    ),
    PRO(
        "ᴘʀᴏ",
        "Pro",
        "league starter on a mission",
        0.90,
        0.58,
        0.46,
        0.58,
        0.52,
        0.92,
        2,
        0.06,
        0.68,
        0xFF5C4D,
        "icon_pro"
    );

    private final String smallCapsTitle;
    private final String displayName;
    private final String flavor;
    private final double shotAccuracy;
    private final double threePointTendency;
    private final double passTendency;
    private final double stealSuccess;
    private final double blockSuccess;
    private final double contestIntensity;
    private final int reactionTicks;
    private final double dribbleMistakeChance;
    private final double jumpShotRatio;
    private final int colorRgb;
    private final String iconModel;

    BotDifficulty(
        String smallCapsTitle,
        String displayName,
        String flavor,
        double shotAccuracy,
        double threePointTendency,
        double passTendency,
        double stealSuccess,
        double blockSuccess,
        double contestIntensity,
        int reactionTicks,
        double dribbleMistakeChance,
        double jumpShotRatio,
        int colorRgb,
        String iconModel
    ) {
        this.smallCapsTitle = smallCapsTitle;
        this.displayName = displayName;
        this.flavor = flavor;
        this.shotAccuracy = shotAccuracy;
        this.threePointTendency = threePointTendency;
        this.passTendency = passTendency;
        this.stealSuccess = stealSuccess;
        this.blockSuccess = blockSuccess;
        this.contestIntensity = contestIntensity;
        this.reactionTicks = reactionTicks;
        this.dribbleMistakeChance = dribbleMistakeChance;
        this.jumpShotRatio = jumpShotRatio;
        this.colorRgb = colorRgb;
        this.iconModel = iconModel;
    }

    public String smallCapsTitle() {
        return smallCapsTitle;
    }

    public String displayName() {
        return displayName;
    }

    public String flavor() {
        return flavor;
    }

    public double shotAccuracy() {
        return shotAccuracy;
    }

    public double threePointTendency() {
        return threePointTendency;
    }

    public double passTendency() {
        return passTendency;
    }

    public double stealSuccess() {
        return stealSuccess;
    }

    public double blockSuccess() {
        return blockSuccess;
    }

    public double contestIntensity() {
        return contestIntensity;
    }

    public int reactionTicks() {
        return reactionTicks;
    }

    public double dribbleMistakeChance() {
        return dribbleMistakeChance;
    }

    public double jumpShotRatio() {
        return jumpShotRatio;
    }

    public int colorRgb() {
        return colorRgb;
    }

    public String iconModel() {
        return iconModel;
    }

    public String hexColor() {
        return String.format("#%06X", colorRgb);
    }

    public BotDifficulty next() {
        BotDifficulty[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static BotDifficulty byNameOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        for (BotDifficulty d : values()) {
            if (d.name().equalsIgnoreCase(raw) || d.displayName.equalsIgnoreCase(raw)) {
                return d;
            }
        }
        return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
            case "easy", "facile", "1" -> EASY;
            case "normal", "medium", "2" -> NORMAL;
            case "pro", "hard", "difficile", "3" -> PRO;
            default -> null;
        };
    }
}
