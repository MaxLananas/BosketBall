package fr.bosketball.arena.schematic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import fr.bosketball.arena.Arena;
import fr.bosketball.arena.CourtGeometry;
import fr.bosketball.arena.CourtPaint;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.Test;

/**
 * A beach court, end to end: a sand deck in a file, the court rectangle found on it, the blue slab
 * and the paint fitted to that rectangle.
 *
 * <p>This is the case the plugin used to get wrong twice. Sand was not a court family at all, so
 * the deck had no rectangle and the lines were fitted to the middle of the whole build. Then the
 * three-point line was measured on X and Z instead of on the basket axis, so a deck whose baselines
 * face east and west — the shape of most sand courts — got a shrunken arc.
 */
public class SandDeckCourtTest {

    private static final int DECK_WIDTH = 34;
    private static final int DECK_LENGTH = 22;

    /** A flat sand deck, exactly the file an operator would save from a beach build. */
    private static byte[] sandDeck(boolean whitePerimeter) throws IOException {
        int w = DECK_WIDTH;
        int h = 3;
        int l = DECK_LENGTH;
        String[] palette = {"minecraft:air", "minecraft:sandstone", "minecraft:sand",
            "minecraft:white_concrete"};
        int[] indices = new int[w * h * l];
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    int index = (y * l + z) * w + x;
                    int value = y == 0 ? 1 : y == 1 ? 2 : 0;
                    if (whitePerimeter && y == 1
                        && (x == 0 || z == 0 || x == w - 1 || z == l - 1)) {
                        value = 3;
                    }
                    indices[index] = value;
                }
            }
        }
        return encode(w, h, l, palette, indices);
    }

    private static byte[] encode(int w, int h, int l, String[] palette, int[] indices)
        throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(raw)) {
            out.writeByte(10);            // root compound
            out.writeUTF("");

            out.writeByte(3);             // TAG_Int: Version
            out.writeUTF("Version");
            out.writeInt(2);

            out.writeByte(2);             // TAG_Short: Height
            out.writeUTF("Height");
            out.writeShort(h);
            out.writeByte(2);             // TAG_Short: Length
            out.writeUTF("Length");
            out.writeShort(l);
            out.writeByte(2);             // TAG_Short: Width
            out.writeUTF("Width");
            out.writeShort(w);

            out.writeByte(11);            // TAG_Int_Array: Offset
            out.writeUTF("Offset");
            out.writeInt(3);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(0);

            out.writeByte(10);            // TAG_Compound: Palette
            out.writeUTF("Palette");
            for (int i = 0; i < palette.length; i++) {
                out.writeByte(3);
                out.writeUTF(palette[i]);
                out.writeInt(i);
            }
            out.writeByte(0);             // end of Palette

            ByteArrayOutputStream packing = new ByteArrayOutputStream();
            for (int value : indices) {
                int v = value;
                while ((v & ~0x7F) != 0) {
                    packing.write((v & 0x7F) | 0x80);
                    v >>>= 7;
                }
                packing.write(v);
            }
            byte[] packed = packing.toByteArray();
            out.writeByte(7);             // TAG_Byte_Array: BlockData
            out.writeUTF("BlockData");
            out.writeInt(packed.length);
            out.write(packed);

            out.writeByte(0);             // end of root
        }
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
            out.write(raw.toByteArray());
        }
        return gz.toByteArray();
    }

    private static Path temp(byte[] content) throws IOException {
        Path file = Files.createTempFile("bosket-beach", ".schem");
        Files.write(file, content);
        file.toFile().deleteOnExit();
        return file;
    }

    @Test
    public void aSandDeckIsFoundAsTheCourtRectangle() throws Exception {
        CourtSchematic deck = CourtSchematic.load(temp(sandDeck(false)).toFile());
        assertTrue("a sand deck is a court", deck.hasCourtBox());
        assertFalse("a plain deck paints nothing: the plugin brings the floor", deck.hasPaintedCourtBox());
        assertEquals("the rectangle is the deck, not the middle of the file", DECK_WIDTH, deck.courtSpanX());
        assertEquals(DECK_LENGTH, deck.courtSpanZ());
        assertEquals(DECK_WIDTH, deck.courtLength());
        assertEquals(DECK_LENGTH, deck.courtWidth());
        assertTrue("the long side is the basket axis", deck.courtAlongX());
        assertEquals("the deck top is the playing surface", 2, deck.floorOffsetAtCenter());
        assertFalse("nothing is painted in the build", deck.deckHasLineMarkings());
    }

    @Test
    public void aPaintedDeckStillGetsThePluginsFloorAndLines() throws Exception {
        // Real beach courts are sand with a white line laid in it. That line is not a coloured
        // court: it must not stop the blue slab, or the paint would show sand with tape on it.
        CourtSchematic deck = CourtSchematic.load(temp(sandDeck(true)).toFile());
        assertTrue(deck.hasCourtBox());
        assertFalse("white tape on sand is not a painted court", deck.hasPaintedCourtBox());
        assertTrue("but the build does carry line blocks", deck.deckHasLineMarkings());
        // The tape is the boundary: the sand it encloses is the deck the slab is fitted to. Before
        // the fix nothing was detected at all and the paint landed in the middle of the file.
        assertEquals("the deck inside its own tape", DECK_WIDTH - 2, deck.courtSpanX());
        assertEquals(DECK_LENGTH - 2, deck.courtSpanZ());
    }

    @Test
    public void thePaintOfASandDeckFitsItsSlab() throws Exception {
        CourtSchematic deck = CourtSchematic.load(temp(sandDeck(false)).toFile());
        CourtGeometry g = fittedGeometry(deck);

        assertEquals("the deck's long axis is the basket axis", true, g.hoopsAlongX());
        assertEquals("a beach court has the real three-point arc",
            CourtGeometry.THREE_POINT_ARC_RADIUS, g.threePointArcRadius(), 1e-9);
        assertEquals(CourtGeometry.THREE_POINT_CORNER_X, g.threePointCornerLateral(), 1e-9);

        for (Arena.CourtLinesPreset preset : Arena.CourtLinesPreset.values()) {
            List<CourtPaint.Tape> plan = CourtPaint.plan(g, preset, 0.095);
            assertTrue("a " + preset + " beach court has paint", plan.size() > 10);
            for (CourtPaint.Tape t : plan) {
                double minX = Math.min(t.x0(), t.x1()) - t.width() * 0.5;
                double maxX = Math.max(t.x0(), t.x1()) + t.width() * 0.5;
                double minZ = Math.min(t.z0(), t.z1()) - t.width() * 0.5;
                double maxZ = Math.max(t.z0(), t.z1()) + t.width() * 0.5;
                assertTrue("no " + t.role() + " may hang off the blue slab on " + preset,
                    minX >= g.minX() - 1e-9 && maxX <= g.maxX() + 1e-9
                        && minZ >= g.minZ() - 1e-9 && maxZ <= g.maxZ() + 1e-9);
            }
        }

        // And the slab itself never leaves the deck: the sand shows around it, never through it.
        assertTrue("the slab starts on the deck", g.minX() >= -DECK_WIDTH / 2.0 - 1e-9);
        assertTrue("and ends there", g.maxX() <= DECK_WIDTH / 2.0 + 1e-9);
        assertTrue(g.minZ() >= -DECK_LENGTH / 2.0 - 1e-9);
        assertTrue(g.maxZ() <= DECK_LENGTH / 2.0 + 1e-9);
    }

    /**
     * The fit {@code SchematicCourtPool.geometryFor} performs, on a deck centred at the origin:
     * the configured half-extents shrink to the deck, and the spans are laid on the basket axis.
     */
    private static CourtGeometry fittedGeometry(CourtSchematic deck) {
        boolean alongX = deck.hasCourtBox() ? deck.courtAlongX() : true;
        double halfBasketToBasket = Math.min(15.0, deck.courtLength() / 2.0);
        double halfAcross = Math.min(9.0, deck.courtWidth() / 2.0);
        double[] spans = SchematicCourtPool.axisSpans(alongX, deck.pasteWidth(), deck.pasteLength(),
            halfBasketToBasket, halfAcross);
        double halfZ = spans[0];
        double halfX = spans[1];
        CourtGeometry g = new CourtGeometry(0, 64, 0, halfZ, halfX);
        g.setCourtBounds(-halfX, -halfZ, halfX, halfZ, 64);
        double halfBasketSpan = alongX ? halfX : halfZ;
        double rimInset = Math.max(0.5, Math.min(1.25, halfBasketSpan - 1.0));
        if (alongX) {
            g.setNorthHoop(new org.bukkit.util.Vector(-halfBasketSpan + rimInset, 67, 0),
                new org.bukkit.util.Vector(1, 0, 0), true);
            g.setSouthHoop(new org.bukkit.util.Vector(halfBasketSpan - rimInset, 67, 0),
                new org.bukkit.util.Vector(-1, 0, 0), true);
        } else {
            g.setNorthHoop(new org.bukkit.util.Vector(0, 67, -halfBasketSpan + rimInset),
                new org.bukkit.util.Vector(0, 0, 1), true);
            g.setSouthHoop(new org.bukkit.util.Vector(0, 67, halfBasketSpan - rimInset),
                new org.bukkit.util.Vector(0, 0, -1), true);
        }
        return g;
    }
}
