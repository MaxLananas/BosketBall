package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import fr.bosketball.arena.schematic.CourtSchematic;
import fr.bosketball.arena.schematic.SchematicCourtPool;
import java.io.File;
import org.junit.Test;

public class PasteStagingTest {
    @Test public void doorPassResumesAndNeverScansMoreThanTheCeiling() throws Exception {
        File source = SchematicTestWriter.writeSpongeSchematicV3();
        try {
            CourtSchematic schematic = CourtSchematic.load(source);
            int total = schematic.width() * schematic.length() * schematic.height();
            int ceiling = SchematicCourtPool.HARD_MAX_BLOCKS_PER_SLICE;
            int budget = 127;
            int cursor = 0;
            int slices = 0;
            // This fixture has no doors, so no World access is required.
            while (cursor < total) {
                CourtSchematic.PasteProgress result = schematic.closeDoorsSlice(null, 0, 0, 0, budget, cursor);
                assertTrue(result.nextIndex() > cursor);
                assertTrue(result.nextIndex() - cursor <= ceiling);
                assertTrue(result.nextIndex() - cursor <= budget);
                assertEquals(0, result.written());
                cursor = result.nextIndex();
                assertEquals(cursor == total, result.complete());
                slices++;
            }
            assertEquals(total, cursor);
            assertTrue(slices > 1);
        } finally {
            source.delete();
        }
    }

    @Test public void mainPassResumesAndCountsActualWorldWrites() throws Exception {
        File source = SchematicTestWriter.writeSpongeSchematicV3();
        try {
            CourtSchematic schematic = CourtSchematic.load(source);
            org.bukkit.block.data.BlockData air = fakeBlockData(org.bukkit.Material.AIR);
            org.bukkit.block.data.BlockData sandstone = fakeBlockData(org.bukkit.Material.SANDSTONE);
            org.bukkit.block.data.BlockData white = fakeBlockData(org.bukkit.Material.WHITE_CONCRETE);
            var resolved = CourtSchematic.class.getDeclaredField("resolved");
            resolved.setAccessible(true);
            resolved.set(schematic, new org.bukkit.block.data.BlockData[]{air, sandstone, white});
            java.util.concurrent.atomic.AtomicInteger writes = new java.util.concurrent.atomic.AtomicInteger();
            org.bukkit.World world = (org.bukkit.World) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.World.class.getClassLoader(), new Class<?>[]{org.bukkit.World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("setBlockData")) {
                        writes.incrementAndGet();
                        return null;
                    }
                    throw new AssertionError("Unexpected world call: " + method.getName());
                });
            int cursor = 0;
            int total = schematic.width() * schematic.length() * schematic.height();
            int budget = 128;
            while (cursor < total) {
                int before = writes.get();
                var progress = schematic.pasteSlice(world, 0, 64, 0, true, budget, cursor);
                assertEquals(progress.written(), writes.get() - before);
                assertTrue(progress.nextIndex() - cursor <= budget);
                assertTrue(writes.get() - before <= budget);
                cursor = progress.nextIndex();
            }
            assertEquals(total, writes.get());
        } finally {
            source.delete();
        }
    }

    private static org.bukkit.block.data.BlockData fakeBlockData(org.bukkit.Material material) {
        return (org.bukkit.block.data.BlockData) java.lang.reflect.Proxy.newProxyInstance(
            org.bukkit.block.data.BlockData.class.getClassLoader(),
            new Class<?>[]{org.bukkit.block.data.BlockData.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getMaterial")) return material;
                throw new AssertionError("Unexpected block data call: " + method.getName());
            });
    }

    @Test public void sliceLimitCannotOverflow() {
        assertEquals(Integer.MAX_VALUE, CourtSchematic.sliceLimit(Integer.MAX_VALUE - 3, 4000, Integer.MAX_VALUE));
        assertEquals(10, CourtSchematic.sliceLimit(10, 128, 10));
    }

    @Test public void invalidSliceArgumentsFailImmediately() {
        int[][] cases = {{-1, 1, 10}, {0, 0, 10}, {11, 1, 10}, {0, 1, -1}};
        for (int[] c : cases) {
            try {
                CourtSchematic.sliceLimit(c[0], c[1], c[2]);
                fail("expected invalid slice bounds");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("slice"));
            }
        }
    }
}
