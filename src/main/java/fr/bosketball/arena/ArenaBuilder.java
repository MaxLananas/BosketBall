package fr.bosketball.arena;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;
import org.bukkit.util.Vector;

/**
 * 1-Command Pro Basketball Stadium Builder (/bb arena build <name>)!
 * Generates:
 *  - Two-tone NBA parquet hardwood court (31x17) + dark basalt apron
 *  - Crisp quartz court lines, center jump circle, painted Ignis/Frost keys & rasterized 3PT arcs
 *  - 4-tier wrap-around sloped stadium grandstands with colorful crowd seat mosaic
 *  - Scorer's table, team benches, 4 corner floodlight towers & overhead Jumbotron housing!
 */
public final class ArenaBuilder {

    private static final Material[] SEAT_PALETTE = {
        Material.RED_CONCRETE,
        Material.BLUE_CONCRETE,
        Material.LIGHT_BLUE_CONCRETE,
        Material.YELLOW_CONCRETE,
        Material.WHITE_CONCRETE,
        Material.GRAY_CONCRETE
    };

    private ArenaBuilder() {
    }

    public static Arena buildFullStadium(String id, String displayName, Location origin) {
        World world = origin.getWorld();
        int cx = origin.getBlockX();
        int fy = origin.getBlockY();
        int cz = origin.getBlockZ();

        CourtGeometry geo = new CourtGeometry(cx + 0.5, fy + 1.0, cz + 0.5);
        if (world == null) {
            return new Arena(id, displayName, "world", geo);
        }

        int halfW = 9;   // Court blocks X: [-9 .. +9]
        int halfL = 15;  // Court blocks Z: [-15 .. +15]
        int apronW = 12;
        int apronL = 18;

        // 1. Clear stadium air volume cleanly
        for (int dx = -apronW - 6; dx <= apronW + 6; dx++) {
            for (int dz = -apronL - 6; dz <= apronL + 6; dz++) {
                for (int dy = 1; dy <= 15; dy++) {
                    world.getBlockAt(cx + dx, fy + dy, cz + dz).setType(Material.AIR, false);
                }
            }
        }

        // 2. Sub-floor & Dark Courtside Apron
        for (int dx = -apronW - 5; dx <= apronW + 5; dx++) {
            for (int dz = -apronL - 5; dz <= apronL + 5; dz++) {
                Material mat = Material.POLISHED_DEEPSLATE;
                if (Math.abs(dx) == apronW && Math.abs(dz) <= apronL
                    || Math.abs(dz) == apronL && Math.abs(dx) <= apronW) {
                    mat = Material.SEA_LANTERN; // Sleek LED courtside perimeter strip!
                }
                world.getBlockAt(cx + dx, fy, cz + dz).setType(mat, false);
            }
        }

        // 3. Two-Tone NBA Hardwood Parquet Floor + Court Markings
        Vector northRim = geo.northHoopRim();
        Vector southRim = geo.southHoopRim();

        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dz = -halfL; dz <= halfL; dz++) {
                double bx = cx + dx + 0.5;
                double bz = cz + dz + 0.5;
                Block floorBlock = world.getBlockAt(cx + dx, fy, cz + dz);

                boolean boundary = (Math.abs(dx) == halfW || Math.abs(dz) == halfL);
                boolean halfCourtLine = (dz == 0);
                double centerDist = Math.hypot(dx, dz);
                boolean centerRing = (centerDist >= 2.5 && centerDist <= 3.4);

                double dNorth = Math.hypot(bx - northRim.getX(), bz - northRim.getZ());
                double dSouth = Math.hypot(bx - southRim.getX(), bz - southRim.getZ());
                boolean northArc = (Math.abs(dNorth - CourtGeometry.THREE_POINT_ARC_RADIUS) <= 0.52 && dz <= -4)
                    || (Math.abs(dx) == 7 && dz <= -11);
                boolean southArc = (Math.abs(dSouth - CourtGeometry.THREE_POINT_ARC_RADIUS) <= 0.52 && dz >= 4)
                    || (Math.abs(dx) == 7 && dz >= 11);

                boolean inNorthPaint = (Math.abs(dx) <= 2 && dz <= -halfL + 5);
                boolean inSouthPaint = (Math.abs(dx) <= 2 && dz >= halfL - 5);

                if (boundary || halfCourtLine || centerRing || northArc || southArc) {
                    floorBlock.setType(Material.SMOOTH_QUARTZ, false);
                } else if (centerDist < 2.5) {
                    floorBlock.setType((Math.abs(dx) + Math.abs(dz)) % 2 == 0 ? Material.GOLD_BLOCK : Material.ORANGE_CONCRETE, false);
                } else if (inNorthPaint) {
                    floorBlock.setType((Math.abs(dx) == 2 || dz == -halfL + 5) ? Material.SMOOTH_QUARTZ : Material.RED_TERRACOTTA, false);
                } else if (inSouthPaint) {
                    floorBlock.setType((Math.abs(dx) == 2 || dz == halfL - 5) ? Material.SMOOTH_QUARTZ : Material.LIGHT_BLUE_TERRACOTTA, false);
                } else {
                    // Alternating herringbone hardwood lanes!
                    Material wood = ((dx + 20) / 2 + (dz + 30) / 4) % 2 == 0
                        ? Material.STRIPPED_OAK_WOOD
                        : Material.STRIPPED_BIRCH_WOOD;
                    floorBlock.setType(wood, false);
                    if (floorBlock.getBlockData() instanceof Orientable orientable) {
                        orientable.setAxis(Axis.Z);
                        floorBlock.setBlockData(orientable, false);
                    }
                }
            }
        }

        // 4. Scorer's Table & Team Benches along West Sideline
        for (int dz = -3; dz <= 3; dz++) {
            world.getBlockAt(cx - halfW - 2, fy + 1, cz + dz).setType(
                Math.abs(dz) % 2 == 0 ? Material.SEA_LANTERN : Material.POLISHED_BLACKSTONE_BRICKS, false
            );
        }
        for (int dz = -9; dz <= -5; dz++) {
            world.getBlockAt(cx - halfW - 2, fy + 1, cz + dz).setType(Material.RED_WOOL, false);
        }
        for (int dz = 5; dz <= 9; dz++) {
            world.getBlockAt(cx - halfW - 2, fy + 1, cz + dz).setType(Material.LIGHT_BLUE_WOOL, false);
        }

        // 5. 4-Tier Sloped Stadium Grandstands (Vibrant Mosaic Crowd Stands like image-1.png!)
        for (int tier = 1; tier <= 4; tier++) {
            int ringX = apronW + tier;
            int ringZ = apronL + tier;
            int y = fy + tier;

            for (int dx = -ringX; dx <= ringX; dx++) {
                setStandBlock(world, cx + dx, y, cz - ringZ, dx, tier);
                setStandBlock(world, cx + dx, y, cz + ringZ, dx, tier);
            }
            for (int dz = -ringZ; dz <= ringZ; dz++) {
                setStandBlock(world, cx - ringX, y, cz + dz, dz, tier);
                setStandBlock(world, cx + ringX, y, cz + dz, dz, tier);
            }
        }

        // 6. Outer Stadium Perimeter Wall behind Tier 4
        int wallX = apronW + 5;
        int wallZ = apronL + 5;
        for (int dy = 1; dy <= 6; dy++) {
            Material wallMat = (dy == 6) ? Material.SEA_LANTERN : Material.POLISHED_BLACKSTONE_BRICKS;
            for (int dx = -wallX; dx <= wallX; dx++) {
                world.getBlockAt(cx + dx, fy + dy, cz - wallZ).setType(wallMat, false);
                world.getBlockAt(cx + dx, fy + dy, cz + wallZ).setType(wallMat, false);
            }
            for (int dz = -wallZ; dz <= wallZ; dz++) {
                world.getBlockAt(cx - wallX, fy + dy, cz + dz).setType(wallMat, false);
                world.getBlockAt(cx + wallX, fy + dy, cz + dz).setType(wallMat, false);
            }
        }

        // 7. 4 Corner Broadcast Floodlight Towers
        int[][] corners = {
            {-apronW - 4, -apronL - 4},
            {apronW + 4, -apronL - 4},
            {-apronW - 4, apronL + 4},
            {apronW + 4, apronL + 4}
        };
        for (int[] c : corners) {
            int tx = cx + c[0];
            int tz = cz + c[1];
            for (int dy = 1; dy <= 12; dy++) {
                world.getBlockAt(tx, fy + dy, tz).setType(Material.POLISHED_DEEPSLATE_WALL, false);
            }
            for (int lx = -1; lx <= 1; lx++) {
                for (int lz = -1; lz <= 1; lz++) {
                    world.getBlockAt(tx + lx, fy + 13, tz + lz).setType(Material.OCHRE_FROGLIGHT, false);
                }
            }
        }

        // 8. L-Shaped Padded Stanchion Support Towers & Center-Hung 5x3x5 Jumbotron
        Arena arena = new Arena(id, displayName, world.getName(), geo);
        ensureHoopAndJumbotronStructures(arena);
        arena.spawnDisplays();
        return arena;
    }

    /**
     * Builds (or seamlessly upgrades in an already-existing stadium!) the two floor-anchored
     * L-shaped Padded NBA Stanchion Towers behind both baselines and the 5x3x5 Overhead Jumbotron!
     */
    public static void ensureHoopAndJumbotronStructures(Arena arena) {
        World world = arena.world();
        if (world == null) {
            return;
        }
        CourtGeometry geo = arena.geometry();
        int cx = (int) Math.floor(geo.centerX());
        int fy = (int) Math.round(geo.floorY() - 1.0);
        int cz = (int) Math.floor(geo.centerZ());
        int halfL = (int) Math.round(geo.halfLength());

        // North Stanchion (Ignis Home End: Red protective padding + L-boom neck reaching cz - halfL at y = fy + 4)
        buildHoopStanchionTower(world, cx, fy, cz - halfL - 1, +1, Material.RED_WOOL);
        // South Stanchion (Frost Home End: Blue protective padding + L-boom neck reaching cz + halfL at y = fy + 4)
        buildHoopStanchionTower(world, cx, fy, cz + halfL + 1, -1, Material.LIGHT_BLUE_WOOL);

        // 5x3x5 Pro Overhead Jumbotron Housing (wide enough that 4 flush LED screens never overlap at corners!)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean corner = (Math.abs(dx) == 2 && Math.abs(dz) == 2);
                boolean edgeMid = (dx == 0 || dz == 0) && (Math.abs(dx) == 2 || Math.abs(dz) == 2);

                // Bottom bezel (fy + 10 = floorY + 9.0)
                world.getBlockAt(cx + dx, fy + 10, cz + dz).setType(
                    corner ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS, false
                );
                // Middle LED screen backing wall (fy + 11 = floorY + 10.0)
                world.getBlockAt(cx + dx, fy + 11, cz + dz).setType(
                    corner ? Material.POLISHED_BLACKSTONE_BRICKS : Material.BLACK_CONCRETE, false
                );
                // Top crown & arena downlights (fy + 12 = floorY + 11.0)
                world.getBlockAt(cx + dx, fy + 12, cz + dz).setType(
                    corner ? Material.GOLD_BLOCK : (edgeMid ? Material.OCHRE_FROGLIGHT : Material.POLISHED_BLACKSTONE_BRICKS),
                    false
                );
            }
        }
        // Central overhead suspension rigging above Jumbotron
        world.getBlockAt(cx, fy + 13, cz).setType(Material.POLISHED_DEEPSLATE_WALL, false);
        world.getBlockAt(cx, fy + 14, cz).setType(Material.POLISHED_DEEPSLATE_WALL, false);
    }

    private static void buildHoopStanchionTower(World world, int x, int fy, int pillarZ, int courtDirZ, Material padMat) {
        // Heavy rear anchor base behind pillar
        world.getBlockAt(x, fy + 1, pillarZ - courtDirZ).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
        // Padded lower post (fy + 1..2) + heavy gunmetal upper column (fy + 3..4)
        world.getBlockAt(x, fy + 1, pillarZ).setType(padMat, false);
        world.getBlockAt(x, fy + 2, pillarZ).setType(padMat, false);
        world.getBlockAt(x, fy + 3, pillarZ).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
        world.getBlockAt(x, fy + 4, pillarZ).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
        // Horizontal boom neck (fy + 4, right above baseline) that meets the 3D hoop's stanchion_arm seamlessly!
        world.getBlockAt(x, fy + 4, pillarZ + courtDirZ).setType(Material.POLISHED_BLACKSTONE_WALL, false);
    }

    private static void setStandBlock(World world, int x, int y, int z, int index, int tier) {
        // Fill solid support underneath
        for (int subY = y - 1; subY >= y - tier; subY--) {
            world.getBlockAt(x, subY, z).setType(Material.DEEPSLATE_TILES, false);
        }
        // Aisles every 9 blocks
        if (Math.floorMod(index, 9) == 0) {
            world.getBlockAt(x, y, z).setType(Material.SMOOTH_QUARTZ_SLAB, false);
            return;
        }
        int hash = Math.abs(x * 31 + z * 17 + tier * 13);
        Material seat = SEAT_PALETTE[hash % SEAT_PALETTE.length];
        world.getBlockAt(x, y, z).setType(seat, false);
    }
}
