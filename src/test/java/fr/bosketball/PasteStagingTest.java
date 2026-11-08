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

    @Test public void cancellationCommandsExposeClearSingleLineEnglishFeedback() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/fr/bosketball/command/BosketCommand.java"));
        assertTrue(source.contains("pool.cancelPaste(args[3])"));
        assertTrue(source.contains("pool.resumePaste(args[3])"));
        assertTrue(source.contains("Court paste paused. The court remains unavailable until resumed."));
        assertTrue(source.contains("Court paste resumed."));
        assertTrue(source.contains("No queued paste found for that court."));
        assertTrue(source.contains("No paused paste found for that court."));
    }

    @Test public void cancellationAndResumeKeepTheExactFinishingCursor() throws Exception {
        SchematicCourtPool pool = new SchematicCourtPool(null);
        Class<?> slotType = Class.forName("fr.bosketball.arena.schematic.SchematicCourtPool$Slot");
        var constructor = slotType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var arena = new fr.bosketball.arena.Arena("court-1", "Court", "world", null);
        Object slot = constructor.newInstance(arena, 0, 0, 0, null, 0, false);
        var slotsField = SchematicCourtPool.class.getDeclaredField("slots");
        var queueField = SchematicCourtPool.class.getDeclaredField("pasteQueue");
        slotsField.setAccessible(true);
        queueField.setAccessible(true);
        @SuppressWarnings("unchecked") var slots = (java.util.Map<Integer, Object>) slotsField.get(pool);
        @SuppressWarnings("unchecked") var queue = (java.util.Deque<Object>) queueField.get(pool);
        slots.put(1, slot);
        queue.add(slot);
        var phase = slotType.getDeclaredField("finishPhase");
        var cursor = slotType.getDeclaredField("finishCursor");
        var main = slotType.getDeclaredField("mainPasted");
        var dirty = slotType.getDeclaredField("dirty");
        var pasted = slotType.getDeclaredField("pasted");
        for (var field : new java.lang.reflect.Field[]{phase, cursor, main, dirty, pasted}) field.setAccessible(true);
        phase.setInt(slot, 2);
        cursor.setInt(slot, 37);
        main.setBoolean(slot, true);
        pasted.setBoolean(slot, true);
        assertTrue(pool.cancelPaste("COURT-1"));
        assertTrue(queue.isEmpty());
        assertTrue(dirty.getBoolean(slot));
        assertTrue(!pasted.getBoolean(slot));
        assertTrue(!pool.cancelPaste("court-1"));
        assertTrue(pool.resumePaste("court-1"));
        assertEquals(1, queue.size());
        assertEquals(2, phase.getInt(slot));
        assertEquals(37, cursor.getInt(slot));
        assertTrue(main.getBoolean(slot));
        assertTrue(!pool.resumePaste("court-1"));
        assertEquals(1, queue.size());
    }

    @Test public void attachableAndRepairPassesResumeWithoutExceedingBudget() throws Exception {
        File source = SchematicTestWriter.writeSpongeV2WithFlatBlockEntities();
        try {
            CourtSchematic schematic = CourtSchematic.load(source);
            var resolved = CourtSchematic.class.getDeclaredField("resolved");
            resolved.setAccessible(true);
            var air = fakeBlockData(org.bukkit.Material.AIR);
            resolved.set(schematic, new org.bukkit.block.data.BlockData[]{air,
                fakeBlockData(org.bukkit.Material.SANDSTONE), fakeBlockData(org.bukkit.Material.OAK_SIGN),
                fakeBlockData(org.bukkit.Material.PLAYER_HEAD)});
            java.util.concurrent.atomic.AtomicInteger writes = new java.util.concurrent.atomic.AtomicInteger();
            org.bukkit.block.Block block = (org.bukkit.block.Block) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.block.Block.class.getClassLoader(), new Class<?>[]{org.bukkit.block.Block.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getBlockData")) return air;
                    if (method.getName().equals("setBlockData")) { writes.incrementAndGet(); return null; }
                    throw new AssertionError("Unexpected block call: " + method.getName());
                });
            org.bukkit.World world = (org.bukkit.World) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.World.class.getClassLoader(), new Class<?>[]{org.bukkit.World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getBlockAt")) return block;
                    if (method.getName().equals("setBlockData")) { writes.incrementAndGet(); return null; }
                    throw new AssertionError("Unexpected world call: " + method.getName());
                });
            assertEquals(2, schematic.attachableCount());
            for (boolean repair : new boolean[]{false, true}) {
                int cursor = 0;
                for (int i = 0; i < 2; i++) {
                    int before = writes.get();
                    var progress = repair ? schematic.repairAttachablesSlice(world, 0, 64, 0, 1, cursor)
                        : schematic.pasteAttachables(world, 0, 64, 0, 1, cursor);
                    assertEquals(1, progress.nextIndex() - cursor);
                    assertEquals(1, progress.written());
                    assertEquals(1, writes.get() - before);
                    cursor = progress.nextIndex();
                    assertEquals(i == 1, progress.complete());
                }
            }
        } finally {
            source.delete();
        }
    }

    @Test public void blockEntityFinishingPassResumesWithBoundedWorldReads() throws Exception {
        File source = SchematicTestWriter.writeSpongeV2WithFlatBlockEntities();
        try {
            CourtSchematic schematic = CourtSchematic.load(source);
            java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
            org.bukkit.block.Block block = (org.bukkit.block.Block) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.block.Block.class.getClassLoader(), new Class<?>[]{org.bukkit.block.Block.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getState")) return null;
                    throw new AssertionError("Unexpected block call: " + method.getName());
                });
            org.bukkit.World world = (org.bukkit.World) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.World.class.getClassLoader(), new Class<?>[]{org.bukkit.World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getBlockAt")) { reads.incrementAndGet(); return block; }
                    throw new AssertionError("Unexpected world call: " + method.getName());
                });
            var first = schematic.applyBlockEntitiesSlice(world, 0, 64, 0, 1, 0);
            assertEquals(1, first.nextIndex());
            assertTrue(!first.complete());
            assertEquals(1, reads.get());
            var second = schematic.applyBlockEntitiesSlice(world, 0, 64, 0, 1, first.nextIndex());
            assertEquals(2, second.nextIndex());
            assertTrue(second.complete());
            assertEquals(2, reads.get());
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
