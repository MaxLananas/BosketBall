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

    static File writeSpongeSchematic() throws IOException {
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
            writeIntTag(out, "Version", 3);
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
            // Palette (compound: block state string -> palette id).
            out.writeByte(10);
            writeString(out, "Palette");
            for (Map.Entry<String, Integer> e : palette.entrySet()) {
                writeIntTagRaw(out, e.getKey(), e.getValue());
            }
            out.writeByte(0); // end Palette
            // BlockData (byte array of var-ints).
            out.writeByte(7);
            writeString(out, "BlockData");
            byte[] packed = packedBytes.toByteArray();
            out.writeInt(packed.length);
            out.write(packed);
            // One block entity so the count field is exercised.
            out.writeByte(9);
            writeString(out, "BlockEntities");
            out.writeByte(10);
            out.writeInt(0);
            out.writeByte(0); // end root compound
        }
        return file;
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
