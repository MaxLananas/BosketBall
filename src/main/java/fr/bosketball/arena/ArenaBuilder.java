package fr.bosketball.arena;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;

public final class ArenaBuilder {

    private static final Material[] SEAT_PALETTE = {
        Material.RED_CONCRETE,
        Material.BLUE_CONCRETE,
        Material.LIGHT_BLUE_CONCRETE,
        Material.YELLOW_CONCRETE,
        Material.ORANGE_CONCRETE,
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

        int halfW = 9;
        int halfL = 15;
        int apronW = 12;
        int apronL = 18;

        for (int dx = -apronW - 6; dx <= apronW + 6; dx++) {
            for (int dz = -apronL - 6; dz <= apronL + 6; dz++) {
                for (int dy = 1; dy <= 15; dy++) {
                    world.getBlockAt(cx + dx, fy + dy, cz + dz).setType(Material.AIR, false);
                }
            }
        }

        for (int dx = -apronW - 5; dx <= apronW + 5; dx++) {
            for (int dz = -apronL - 5; dz <= apronL + 5; dz++) {
                Material mat = Material.POLISHED_DEEPSLATE;
                if (Math.abs(dx) == apronW && Math.abs(dz) <= apronL
                    || Math.abs(dz) == apronL && Math.abs(dx) <= apronW) {
                    mat = Material.OCHRE_FROGLIGHT;
                }
                world.getBlockAt(cx + dx, fy, cz + dz).setType(mat, false);
            }
        }

        paintCleanHardwoodCourt(world, cx, fy, cz, halfW, halfL);

        for (int dz = -3; dz <= 3; dz++) {
            world.getBlockAt(cx - halfW - 2, fy + 1, cz + dz).setType(
                Math.abs(dz) % 2 == 0 ? Material.OCHRE_FROGLIGHT : Material.POLISHED_BLACKSTONE_BRICKS, false
            );
        }
        for (int dz = -9; dz <= -5; dz++) {
            world.getBlockAt(cx - halfW - 2, fy + 1, cz + dz).setType(Material.RED_TERRACOTTA, false);
        }
        for (int dz = 5; dz <= 9; dz++) {
            world.getBlockAt(cx - halfW - 2, fy + 1, cz + dz).setType(Material.BLUE_TERRACOTTA, false);
        }

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

        int wallX = apronW + 5;
        int wallZ = apronL + 5;
        for (int dy = 1; dy <= 6; dy++) {
            Material wallMat = (dy == 6) ? Material.OCHRE_FROGLIGHT : Material.POLISHED_BLACKSTONE_BRICKS;
            for (int dx = -wallX; dx <= wallX; dx++) {
                world.getBlockAt(cx + dx, fy + dy, cz - wallZ).setType(wallMat, false);
                world.getBlockAt(cx + dx, fy + dy, cz + wallZ).setType(wallMat, false);
            }
            for (int dz = -wallZ; dz <= wallZ; dz++) {
                world.getBlockAt(cx - wallX, fy + dy, cz + dz).setType(wallMat, false);
                world.getBlockAt(cx + wallX, fy + dy, cz + dz).setType(wallMat, false);
            }
        }

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

        Arena arena = new Arena(id, displayName, world.getName(), geo);
        ensureHoopAndJumbotronStructures(arena);
        arena.spawnDisplays();
        return arena;
    }

    public static void paintCleanHardwoodCourt(World world, int cx, int fy, int cz, int halfW, int halfL) {
        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dz = -halfL; dz <= halfL; dz++) {
                Block floorBlock = world.getBlockAt(cx + dx, fy, cz + dz);
                double centerDist = Math.hypot(dx, dz);
                boolean inNorthPaint = (Math.abs(dx) <= 2 && dz <= -halfL + 5);
                boolean inSouthPaint = (Math.abs(dx) <= 2 && dz >= halfL - 5);

                if (centerDist < 2.2) {
                    floorBlock.setType((Math.abs(dx) + Math.abs(dz)) % 2 == 0 ? Material.GOLD_BLOCK : Material.ORANGE_TERRACOTTA, false);
                } else if (inNorthPaint) {
                    floorBlock.setType(Material.RED_TERRACOTTA, false);
                } else if (inSouthPaint) {
                    floorBlock.setType(Material.BLUE_TERRACOTTA, false);
                } else {
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
    }

    public static void ensureHoopAndJumbotronStructures(Arena arena) {
        World world = arena.world();
        if (world == null) {
            return;
        }
        CourtGeometry geo = arena.geometry();
        int cx = (int) Math.floor(geo.centerX());
        int fy = (int) Math.round(geo.floorY() - 1.0);
        int cz = (int) Math.floor(geo.centerZ());
        int halfW = (int) Math.floor(geo.halfWidth());
        int halfL = (int) Math.floor(geo.halfLength());

        paintCleanHardwoodCourt(world, cx, fy, cz, halfW, halfL);

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 1; dy <= 7; dy++) {
                for (int dz = cz - halfL - 3; dz <= cz - halfL + 2; dz++) {
                    world.getBlockAt(cx + dx, fy + dy, dz).setType(Material.AIR, false);
                }
                for (int dz = cz + halfL - 2; dz <= cz + halfL + 3; dz++) {
                    world.getBlockAt(cx + dx, fy + dy, dz).setType(Material.AIR, false);
                }
            }

            for (int dz = cz - halfL - 3; dz < cz - halfL; dz++) {
                world.getBlockAt(cx + dx, fy, dz).setType(Material.POLISHED_DEEPSLATE, false);
            }
            for (int dz = cz + halfL + 1; dz <= cz + halfL + 3; dz++) {
                world.getBlockAt(cx + dx, fy, dz).setType(Material.POLISHED_DEEPSLATE, false);
            }
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean corner = (Math.abs(dx) == 2 && Math.abs(dz) == 2);
                boolean edgeMid = (dx == 0 || dz == 0) && (Math.abs(dx) == 2 || Math.abs(dz) == 2);

                world.getBlockAt(cx + dx, fy + 10, cz + dz).setType(
                    corner ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS, false
                );
                world.getBlockAt(cx + dx, fy + 11, cz + dz).setType(
                    corner ? Material.POLISHED_BLACKSTONE_BRICKS : Material.BLACK_CONCRETE, false
                );
                world.getBlockAt(cx + dx, fy + 12, cz + dz).setType(
                    corner ? Material.GOLD_BLOCK : (edgeMid ? Material.OCHRE_FROGLIGHT : Material.POLISHED_BLACKSTONE_BRICKS),
                    false
                );
            }
        }
        world.getBlockAt(cx, fy + 13, cz).setType(Material.POLISHED_DEEPSLATE_WALL, false);
        world.getBlockAt(cx, fy + 14, cz).setType(Material.POLISHED_DEEPSLATE_WALL, false);
    }

    private static void setStandBlock(World world, int x, int y, int z, int index, int tier) {
        for (int subY = y - 1; subY >= y - tier; subY--) {
            world.getBlockAt(x, subY, z).setType(Material.DEEPSLATE_TILES, false);
        }
        if (Math.floorMod(index, 9) == 0) {
            world.getBlockAt(x, y, z).setType(Material.DEEPSLATE_TILE_SLAB, false);
            return;
        }
        int hash = Math.abs(x * 31 + z * 17 + tier * 13);
        Material seat = SEAT_PALETTE[hash % SEAT_PALETTE.length];
        world.getBlockAt(x, y, z).setType(seat, false);
    }
}
