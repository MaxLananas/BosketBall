package fr.bosketball.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Answers one question, a million times a second: can anybody actually see this?
 *
 * <p>Lobby props (crate ring, holograms, tutorial markers) animate every tick. With nobody around,
 * that work is pure waste: the entities move, the packets are built, the transformations are
 * allocated, and no client ever receives them. A distance check against the players of the world
 * costs nanoseconds when the world is empty.</p>
 */
public final class Viewers {

    private Viewers() {
    }

    /** True when at least one player is within {@code radius} blocks of {@code centre}. */
    public static boolean anyWithin(Location centre, double radius) {
        if (centre == null) {
            return false;
        }
        org.bukkit.World world = centre.getWorld();
        if (world == null) {
            return false;
        }
        java.util.List<? extends Player> players = world.getPlayers();
        if (players.isEmpty()) {
            return false;
        }
        double radiusSq = radius * radius;
        double cx = centre.getX();
        double cy = centre.getY();
        double cz = centre.getZ();
        for (Player player : players) {
            double dx = player.getX() - cx;
            double dy = player.getY() - cy;
            double dz = player.getZ() - cz;
            if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                return true;
            }
        }
        return false;
    }
}
