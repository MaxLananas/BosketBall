package fr.bosketball.arena.schematic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * A beach court is a sand deck with paint on it. Sand used to be no court family at all, so those
 * builds had no court rectangle and the painted lines were fitted to the middle of the whole
 * schematic instead of the deck.
 */
public class CourtFamilyTest {

    @Test
    public void sandAndGravelAreCourtSurfaces() {
        for (String name : new String[]{
            "minecraft:sand", "minecraft:red_sand", "minecraft:smooth_sandstone",
            "minecraft:cut_sandstone", "minecraft:gravel", "minecraft:dirt_path",
            "minecraft:coarse_dirt", "minecraft:grass_block"}) {
            String family = CourtSchematic.courtFamily(name);
            assertNotNull(name + " must be a usable court floor", family);
            assertTrue(name + " is a plain surface, never a painted one",
                CourtSchematic.isNeutralSurface(family));
        }
    }

    @Test
    public void paintedCourtsStayPainted() {
        assertEquals("blue", CourtSchematic.courtFamily("minecraft:blue_concrete"));
        assertEquals("cyan", CourtSchematic.courtFamily("minecraft:cyan_concrete"));
        assertEquals("orange", CourtSchematic.courtFamily("minecraft:orange_terracotta"));
        assertEquals("oak", CourtSchematic.courtFamily("minecraft:oak_planks"));
        assertEquals("quartz", CourtSchematic.courtFamily("minecraft:smooth_quartz"));
        for (String name : new String[]{"minecraft:blue_concrete", "minecraft:cyan_wool",
            "minecraft:orange_terracotta", "minecraft:snow_block"}) {
            assertFalse(name + " must never be tagged as a plain surface",
                CourtSchematic.isNeutralSurface(CourtSchematic.courtFamily(name)));
        }
    }

    @Test
    public void everythingElseIsNotACourt() {
        for (String name : new String[]{"minecraft:air", "minecraft:cave_air", "minecraft:stone",
            "minecraft:oak_log", "minecraft:light_gray_stained_glass", null}) {
            assertNull(String.valueOf(name), CourtSchematic.courtFamily(name));
        }
        assertFalse(CourtSchematic.isNeutralSurface(null));
    }

    @Test
    public void namespaceAndBlockStateDoNotMatter() {
        assertEquals("blue", CourtSchematic.courtFamily("blue_concrete"));
        assertEquals("blue", CourtSchematic.courtFamily("minecraft:blue_concrete[waterlogged=false]"));
        assertEquals(CourtSchematic.courtFamily("minecraft:sand"),
            CourtSchematic.courtFamily("sand[waterlogged=true]"));
    }
}
