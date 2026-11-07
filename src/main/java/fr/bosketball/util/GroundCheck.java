package fr.bosketball.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

/**
 * Server-side "is this player standing on something?" test.
 *
 * <p>Round 33 — {@link Player#isOnGround()} is deprecated for a reason: the value comes straight
 * from the client and is <em>never</em> validated by the server, so a modified client can simply
 * claim to be on the ground while flying and every shot/dunk bonus that reads the flag (the
 * jump-shot and the contest penalty in {@code MatchSession}) fires anyway. It also desyncs: a lag
 * spike makes a player "grounded" for a second while they are mid-air on everyone else's screen.
 *
 * <p>This helper answers the same question from the <b>server</b> side only: it takes the player's
 * real bounding box, probes the 2 cm right under the feet and looks for a block collision shape
 * there. Slabs, carpets, stairs and fences are therefore handled correctly (their shapes stop above
 * the block floor), which a naive {@code block.getType().isSolid()} test gets wrong.
 *
 * <p>A player in a boat, on a ladder or riding a mob is "not standing" — the plugin only ever asks
 * this question on a court, where the floor is always made of blocks.
 */
public final class GroundCheck {

    /** Height of the probe box under the feet, in blocks (2 cm: well under a slab). */
    private static final double PROBE_HEIGHT = 0.02;

    private GroundCheck() {
    }

    /**
     * @return {@code true} when a block collision shape sits directly under the player's feet.
     */
    public static boolean isStanding(Player player) {
        if (player == null || !player.isOnline() || !player.isValid()) {
            return false;
        }
        BoundingBox box;
        World world;
        try {
            box = player.getBoundingBox();
            world = player.getWorld();
        } catch (RuntimeException notSpawnedYet) {
            return false;
        }
        if (box == null || world == null) {
            return false;
        }

        // Inset the horizontal footprint by 1 mm so standing next to a wall never counts, and probe
        // a thin slab just below the feet.
        double minX = box.getMinX() + 1.0e-3;
        double maxX = box.getMaxX() - 1.0e-3;
        double minZ = box.getMinZ() + 1.0e-3;
        double maxZ = box.getMaxZ() - 1.0e-3;
        if (maxX <= minX || maxZ <= minZ) {
            return false;
        }
        double top = box.getMinY() - 1.0e-3;
        BoundingBox probe = new BoundingBox(minX, top - PROBE_HEIGHT, minZ, maxX, top, maxZ);

        int fromX = floor(probe.getMinX());
        int toX = floor(probe.getMaxX());
        int fromY = floor(probe.getMinY());
        int toY = floor(probe.getMaxY());
        int fromZ = floor(probe.getMinZ());
        int toZ = floor(probe.getMaxZ());

        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                for (int y = fromY; y <= toY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.isEmpty() && hits(block, x, y, z, probe)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hits(Block block, int x, int y, int z, BoundingBox probe) {
        var shape = block.getCollisionShape();
        if (shape == null) {
            return false;
        }
        for (BoundingBox piece : shape.getBoundingBoxes()) {
            if (piece.clone().shift(x, y, z).overlaps(probe)) {
                return true;
            }
        }
        return false;
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    /** Convenience overload for a location (used when only the feet position is known). */
    public static boolean isStanding(Location feet) {
        if (feet == null || feet.getWorld() == null) {
            return false;
        }
        BoundingBox probe = new BoundingBox(
            feet.getX() - 0.3, feet.getY() - PROBE_HEIGHT - 1.0e-3, feet.getZ() - 0.3,
            feet.getX() + 0.3, feet.getY() - 1.0e-3, feet.getZ() + 0.3);
        World world = feet.getWorld();
        for (int x = floor(probe.getMinX()); x <= floor(probe.getMaxX()); x++) {
            for (int z = floor(probe.getMinZ()); z <= floor(probe.getMaxZ()); z++) {
                for (int y = floor(probe.getMinY()); y <= floor(probe.getMaxY()); y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.isEmpty() && hits(block, x, y, z, probe)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
