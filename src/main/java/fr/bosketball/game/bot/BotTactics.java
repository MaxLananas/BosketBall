package fr.bosketball.game.bot;

/** Offensive preferences are selected once per decision, not rolled every movement tick. */
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

    /** A weighted choice reflects a player's preferences among the decisions available to them. */
    public static Action choose(Style style, double shootBias, double passBias, double driveBias,
                                double space, double rimDistance, boolean canPass, double roll) {
        double shoot = style.shoot() * shootBias * (space >= 2.1 ? 1.0 : style.risk() * 0.3)
            * (rimDistance > 11 ? 0.4 : 1.0);
        double pass = canPass ? style.pass() * passBias * (space < 2.1 ? 1.5 : 0.7) : 0;
        double drive = style.drive() * driveBias * (rimDistance < 4 ? 1.4 : 1.0);
        double choice = Math.clamp(roll, 0.0, Math.nextDown(1.0)) * (shoot + pass + drive);
        if (choice < shoot) return Action.SHOOT;
        if (choice < shoot + pass) return Action.PASS;
        return Action.DRIVE;
    }
}
