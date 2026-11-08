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
