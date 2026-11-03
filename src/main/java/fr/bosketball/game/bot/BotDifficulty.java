package fr.bosketball.game.bot;

/**
 * Skill tiers for the AI players.
 *
 * <p>Each tier drives every decision the {@link BotBrain} makes: how accurate the shot
 * release is, how often it dribbles into a bad spot, how fast it reacts on defence and how
 * long it takes to decide to pass. The three tiers are deliberately far apart so a lobby
 * player immediately feels the difference between beating an EASY bot and a PRO bot.</p>
 */
public enum BotDifficulty {

    EASY(
        "ᴇᴀꜱʏ",
        "Easy",
        "relaxed pickup game",
        0.62,   // shotAccuracy — chance the release lands in the green window
        0.34,   // threePointTendency
        0.22,   // passTendency per decision tick
        0.30,   // stealSuccess
        0.24,   // blockSuccess
        0.55,   // contestIntensity (how tight it guards)
        9,      // reactionTicks
        0.26,   // dribbleMistakeChance (turns it over)
        0.40,   // jumpshot vs drive ratio
        0x4ADE80,
        "icon_easy"
    ),
    NORMAL(
        "ɴᴏʀᴍᴀʟ",
        "Normal",
        "solid club-level hooper",
        0.78,   // shotAccuracy
        0.46,   // threePointTendency
        0.34,   // passTendency
        0.44,   // stealSuccess
        0.38,   // blockSuccess
        0.74,   // contestIntensity
        5,      // reactionTicks
        0.14,   // dribbleMistakeChance
        0.55,   // jumpshot vs drive ratio
        0xFBBF24,
        "icon_normal"
    ),
    PRO(
        "ᴘʀᴏ",
        "Pro",
        "league starter on a mission",
        0.90,   // shotAccuracy
        0.58,   // threePointTendency
        0.46,   // passTendency
        0.58,   // stealSuccess
        0.52,   // blockSuccess
        0.92,   // contestIntensity
        2,      // reactionTicks
        0.06,   // dribbleMistakeChance
        0.68,   // jumpshot vs drive ratio
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

    /** 0..1 chance a taken shot lands inside the perfect release window. */
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

    /** How closely the bot shadows the ball handler, 0..1. */
    public double contestIntensity() {
        return contestIntensity;
    }

    public int reactionTicks() {
        return reactionTicks;
    }

    public double dribbleMistakeChance() {
        return dribbleMistakeChance;
    }

    /** 1.0 = always shoots the jumper, 0.0 = always drives the lane. */
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
