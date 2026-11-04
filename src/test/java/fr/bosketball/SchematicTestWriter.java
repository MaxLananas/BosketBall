package fr.bosketball;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Test-only writer that produces a real FastAsyncWorldEdit {@code .schem} file (gzipped NBT,
 * Sponge v2/v3 layout) so the production reader in
 * {@link fr.bosketball.arena.schematic.CourtSchematic} is exercised end to end instead of being
 * asserted by string matching.
 *
 * <p>The generated court is a 30x4x18 slab: a sandstone floor with a white concrete centre line,
 * which real-court geometry the plugin then auto-calibrates from.</p>
 */
final class SchematicTestWriter {

    private static final int WIDTH = 30;
    private static final int HEIGHT = 4;
    private static final int LENGTH = 18;

    private SchematicTestWriter() {
    }

    /** FAWE >= 2.11 default: Sponge v3, blocks nested under {@code Schematic -> Blocks}. */
    static File writeSpongeSchematicV3() throws IOException {
        return writeSpongeSchematic(3);
    }

    /** Legacy Sponge v1: {@code Palette}/{@code BlockData} straight on the schematic compound. */
    static File writeSpongeSchematicV1() throws IOException {
        return writeSpongeSchematic(1);
    }

    static File writeSpongeSchematic() throws IOException {
        return writeSpongeSchematicV3();
    }

    private static File writeSpongeSchematic(int formatVersion) throws IOException {
        Map<String, Integer> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        palette.put("minecraft:sandstone", 1);
        palette.put("minecraft:white_concrete", 2);

        ByteArrayOutputStream packedBytes = new ByteArrayOutputStream();
        // Var-int palette indices, ordered X fastest, then Z, then Y (WorldEdit's BlockData order).
        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < LENGTH; z++) {
                for (int x = 0; x < WIDTH; x++) {
                    int index;
                    if (y > 0) {
                        index = 0;
                    } else if (z == LENGTH / 2) {
                        index = 2;
                    } else {
                        index = 1;
                    }
                    writeVarInt(packedBytes, index);
                }
            }
        }

        File file = File.createTempFile("bosketball-terrain", ".schem");
        file.deleteOnExit();
        try (OutputStream fos = new java.io.FileOutputStream(file);
             DataOutputStream out = new DataOutputStream(new GZIPOutputStream(fos))) {
            // Root compound, named "Schematic" exactly like FAWE writes it.
            out.writeByte(10);
            writeString(out, "Schematic");
            writeIntTag(out, "Version", formatVersion);
            writeIntTag(out, "DataVersion", 3953);
            writeShortTag(out, "Width", WIDTH);
            writeShortTag(out, "Height", HEIGHT);
            writeShortTag(out, "Length", LENGTH);
            // Centred copy: WorldEdit stores the negative of the player position in the selection.
            out.writeByte(11);
            writeString(out, "Offset");
            out.writeInt(3);
            out.writeInt(-WIDTH / 2);
            out.writeInt(-1);
            out.writeInt(-LENGTH / 2);
            // Metadata (written by both layouts: v3 stores the Origin here).
            out.writeByte(10);
            writeString(out, "Metadata");
            out.writeByte(10);
            writeString(out, "WorldEdit");
            out.writeByte(11);
            writeString(out, "Origin");
            out.writeInt(3);
            out.writeInt(0);
            out.writeInt(64);
            out.writeInt(0);
            out.writeByte(0); // end WorldEdit
            out.writeByte(0); // end Metadata
            byte[] packed = packedBytes.toByteArray();
            if (formatVersion >= 2) {
                // Sponge v2/v3: everything blocks-related is nested in a `Blocks` compound and the
                // var-int stream is called `Data` (v1 called it `BlockData` at the root).
                out.writeByte(10);
                writeString(out, "Blocks");
                writePalette(out, palette);
                out.writeByte(7);
                writeString(out, "Data");
                out.writeInt(packed.length);
                out.write(packed);
                out.writeByte(9); // BlockEntities list, empty
                writeString(out, "BlockEntities");
                out.writeByte(10);
                out.writeInt(0);
                out.writeByte(0); // end Blocks
            } else {
                writePalette(out, palette);
                out.writeByte(7);
                writeString(out, "BlockData");
                out.writeInt(packed.length);
                out.write(packed);
                out.writeByte(9);
                writeString(out, "BlockEntities");
                out.writeByte(10);
                out.writeInt(0);
            }
            out.writeByte(0); // end root compound
        }
        return file;
    }

    /**
     * 20x10 court with a 2-deep deck plus a 3-block mast on the centre column, used to prove the
     * floor calibration scans the whole footprint instead of trusting one column.
     */
    static File writeCourtWithMast() throws IOException {
        final int w = 20;
        final int h = 6;
        final int l = 10;
        Map<String, Integer> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        palette.put("minecraft:stone", 1);

        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    boolean deck = y <= 1;
                    boolean mast = y >= 2 && x == w / 2 && z == l / 2;
                    writeVarInt(packed, deck || mast ? 1 : 0);
                }
            }
        }
        return writeRaw(w, h, l, palette, packed.toByteArray(), 3);
    }

    private static File writeRaw(int width, int height, int length, Map<String, Integer> palette,
                                 byte[] packed, int formatVersion) throws IOException {
        File file = File.createTempFile("bosketball-terrain", ".schem");
        file.deleteOnExit();
        try (OutputStream fos = new java.io.FileOutputStream(file);
             DataOutputStream out = new DataOutputStream(new GZIPOutputStream(fos))) {
            out.writeByte(10);
            writeString(out, "Schematic");
            writeIntTag(out, "Version", formatVersion);
            writeIntTag(out, "DataVersion", 3953);
            writeShortTag(out, "Width", width);
            writeShortTag(out, "Height", height);
            writeShortTag(out, "Length", length);
            out.writeByte(11);
            writeString(out, "Offset");
            out.writeInt(3);
            out.writeInt(-width / 2);
            out.writeInt(-1);
            out.writeInt(-length / 2);
            byte[] data = packed;
            if (formatVersion >= 2) {
                out.writeByte(10);
                writeString(out, "Blocks");
                writePalette(out, palette);
                out.writeByte(7);
                writeString(out, "Data");
                out.writeInt(data.length);
                out.write(data);
                out.writeByte(9);
                writeString(out, "BlockEntities");
                out.writeByte(10);
                out.writeInt(0);
                out.writeByte(0);
            } else {
                writePalette(out, palette);
                out.writeByte(7);
                writeString(out, "BlockData");
                out.writeInt(data.length);
                out.write(data);
            }
            out.writeByte(0);
        }
        return file;
    }

    private static void writePalette(DataOutputStream out, Map<String, Integer> palette) throws IOException {
        out.writeByte(10);
        writeString(out, "Palette");
        for (Map.Entry<String, Integer> e : palette.entrySet()) {
            writeIntTagRaw(out, e.getKey(), e.getValue());
        }
        out.writeByte(0); // end Palette
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static void writeIntTag(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(3);
        writeString(out, name);
        out.writeInt(value);
    }

    private static void writeIntTagRaw(DataOutputStream out, String name, int value) throws IOException {
        writeIntTag(out, name, value);
    }

    private static void writeShortTag(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(2);
        writeString(out, name);
        out.writeShort(value);
    }
}
