package fr.bosketball.game.bot;

/** Offensive preferences are evaluated once per commitment, not rolled every movement tick. */
public final class BotTactics {
    public enum Action { SHOOT, PASS, DRIVE }

    public record Style(double shoot, double pass, double drive, double risk, int commitmentTicks) { }

    private BotTactics() {
    }

    public static Style style(BotDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> new Style(0.8, 0.7, 1.3, 0.8, 18);
            case NORMAL -> new Style(1.0, 1.3, 0.9, 0.5, 14);
            case PRO -> new Style(1.2, 1.0, 1.0, 0.3, 10);
        };
    }

    /** Choose the strongest available option, weighted by this player's preferences. */
    public static Action choose(Style style, double shootBias, double passBias, double driveBias,
                                double space, double rimDistance, boolean canPass) {
        return choose(style, shootBias, passBias, driveBias, space, rimDistance, canPass, false);
    }

    /**
     * Same choice, with the shot clock in the decision.
     *
     * <p>A bot used to treat a twenty-five block heave as a normal shot - the range penalty was a
     * flat 0.4 that any decent shoot bias outvoted - so bot teams hoisted from their own half and
     * the human never got to defend anything. Range now scales the shot's weight hard, and only a
     * dying shot clock buys the heave.
     */
    public static Action choose(Style style, double shootBias, double passBias, double driveBias,
                                double space, double rimDistance, boolean canPass, boolean mustGetShotOff) {
        double rangeFactor;
        if (rimDistance <= 8.5) {
            rangeFactor = 1.0;
        } else if (rimDistance <= 10.5) {
            rangeFactor = 0.7;
        } else {
            rangeFactor = mustGetShotOff ? 1.6 : 0.12;
        }
        double shoot = style.shoot() * shootBias * (space >= 2.1 ? 1.0 : style.risk() * 0.3)
            * rangeFactor;
        double pass = canPass ? style.pass() * passBias * (space < 2.1 ? 1.5 : 0.7) : 0;
        double drive = style.drive() * driveBias * (rimDistance < 4 ? 1.4 : 1.0);
        if (shoot >= pass && shoot >= drive) return Action.SHOOT;
        if (pass >= drive) return Action.PASS;
        return Action.DRIVE;
    }
}
