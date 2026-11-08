package fr.bosketball.game.bot;

import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.util.Vector;

/** Select a short in-bounds detour only after the caller verifies its collision clearance. */
public final class BotRecovery {
    private BotRecovery() { }

    public static Vector choose(Vector position, int firstDirection, Function<Vector, Vector> clamp,
                                Predicate<Vector> clearPath) {
        for (int i = 0; i < 8; i++) {
            double angle = Math.floorMod(firstDirection + i, 8) * Math.PI / 4.0;
            Vector candidate = clamp.apply(position.clone().add(
                new Vector(Math.cos(angle), 0, Math.sin(angle)).multiply(1.5)));
            Vector delta = candidate.clone().subtract(position).setY(0);
            if (delta.lengthSquared() >= 0.49 && clearPath.test(candidate)) return candidate;
        }
        return null;
    }
}
