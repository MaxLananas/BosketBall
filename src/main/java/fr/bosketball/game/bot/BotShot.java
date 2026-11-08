package fr.bosketball.game.bot;

import org.bukkit.util.Vector;

/** A release is timed from the gather; pressure and unsettled feet cause early releases. */
public final class BotShot {
    private BotShot() { }

    public static int releaseTicks(BotDifficulty difficulty, double defenderDistance,
                                   double horizontalSpeed, int playerTrait) {
        int base = switch (difficulty) {
            case EASY -> 18;
            case NORMAL -> 19;
            case PRO -> 19;
        };
        int variation = switch (difficulty) {
            case EASY -> Math.floorMod(playerTrait, 7) - 3;
            case NORMAL -> Math.floorMod(playerTrait, 5) - 2;
            case PRO -> 0;
        };
        int hurried = defenderDistance < 1.5 ? 3 : defenderDistance < 2.5 ? 1 : 0;
        int wrongFoot = horizontalSpeed > 0.18 ? 1 : 0;
        return Math.clamp(base + variation - hurried - wrongFoot, 12, 21);
    }

    public static double charge(int elapsedTicks) {
        return Math.clamp(elapsedTicks / 20.0, 0.0, 1.10);
    }

    /** An early or late release catches the follow-through while it is still turning. */
    public static Vector releaseDirection(Vector look, Vector velocity, int elapsedTicks, int playerTrait) {
        Vector direction = releaseDirection(look, velocity);
        double sign = (playerTrait & 1) == 0 ? 1.0 : -1.0;
        double angle = Math.toRadians((elapsedTicks - 19) * 1.2 * sign);
        double x = direction.getX();
        double z = direction.getZ();
        return direction.setX(x * Math.cos(angle) - z * Math.sin(angle))
            .setZ(x * Math.sin(angle) + z * Math.cos(angle)).normalize();
    }

    /** Momentum makes the release drift in the direction the shooter is still moving. */
    public static Vector releaseDirection(Vector look, Vector velocity) {
        Vector direction = look.clone();
        if (direction.lengthSquared() < 1e-8) return new Vector(0, 0, 1);
        direction.normalize();
        Vector lateral = new Vector(velocity.getX(), 0, velocity.getZ());
        lateral.subtract(direction.clone().multiply(lateral.dot(direction)));
        return direction.add(lateral.multiply(0.12)).normalize();
    }
}
