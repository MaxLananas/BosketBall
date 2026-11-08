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

final class SchematicTestWriter {

    private static final int WIDTH = 30;
    private static final int HEIGHT = 4;
    private static final int LENGTH = 18;

    private SchematicTestWriter() {
    }

    static File writeSpongeSchematicV3() throws IOException {
        return writeSpongeSchematic(3);
    }

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

            out.writeByte(10);
            writeString(out, "Schematic");
            writeIntTag(out, "Version", formatVersion);
            writeIntTag(out, "DataVersion", 3953);
            writeShortTag(out, "Width", WIDTH);
            writeShortTag(out, "Height", HEIGHT);
            writeShortTag(out, "Length", LENGTH);

            out.writeByte(11);
            writeString(out, "Offset");
            out.writeInt(3);
            out.writeInt(-WIDTH / 2);
            out.writeInt(-1);
            out.writeInt(-LENGTH / 2);

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
            out.writeByte(0);
            out.writeByte(0);
            byte[] packed = packedBytes.toByteArray();
            if (formatVersion >= 2) {

                out.writeByte(10);
                writeString(out, "Blocks");
                writePalette(out, palette);
                out.writeByte(7);
                writeString(out, "Data");
                out.writeInt(packed.length);
                out.write(packed);
                out.writeByte(9);
                writeString(out, "BlockEntities");
                out.writeByte(10);
                out.writeInt(0);
                out.writeByte(0);
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
            out.writeByte(0);
        }
        return file;
    }

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

    static File writeRoofedCourt() throws IOException {
        final int w = 20;
        final int h = 8;
        final int l = 10;
        Map<String, Integer> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        palette.put("minecraft:stone", 1);
        palette.put("minecraft:light[level=15,waterlogged=false]", 2);
        palette.put("minecraft:coal_block", 3);

        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    int index;
                    if (y <= 1) {
                        index = 1;
                    } else if (y <= 5) {
                        index = 2;
                    } else if (y == 6) {
                        index = 3;
                    } else {
                        index = 0;
                    }
                    writeVarInt(packed, index);
                }
            }
        }
        return writeRaw(w, h, l, palette, packed.toByteArray(), 3);
    }

    static File writePaintedCourt() throws IOException {
        final int w = 24;
        final int h = 3;
        final int l = 16;
        Map<String, Integer> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        palette.put("minecraft:stone", 1);
        palette.put("minecraft:white_concrete", 2);

        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    int index;
                    if (y != 0) {
                        index = 0;
                    } else if (z == 0 || z == l - 1 || x == 0 || x == w - 1 || z == l / 2) {
                        index = 2;
                    } else {
                        index = 1;
                    }
                    writeVarInt(packed, index);
                }
            }
        }
        return writeRaw(w, h, l, palette, packed.toByteArray(), 3);
    }

    /**
     * A Sponge <b>v2</b> file — the format Axiom and FAWE 2.6 write: {@code Palette} +
     * {@code BlockData} + {@code BlockEntities} flat at the root, and the block-entity payload
     * <b>flat inside the compound</b> (no {@code Data} wrapper). This is what
     * {@code terrainsseize.schem} looks like, so it is the regression guard for the sign text and
     * the custom-head profile.
     */
    static File writeSpongeV2WithFlatBlockEntities() throws IOException {
        final int w = 8;
        final int h = 3;
        final int l = 8;
        final int signX = 2;
        final int headX = 5;
        Map<String, Integer> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        palette.put("minecraft:sandstone", 1);
        palette.put("minecraft:oak_sign[rotation=0,waterlogged=false]", 2);
        palette.put("minecraft:player_head[powered=true,rotation=11]", 3);

        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    int index = 0;
                    if (y == 0) {
                        index = 1;
                    } else if (y == 1 && z == 4 && x == signX) {
                        index = 2;
                    } else if (y == 1 && z == 4 && x == headX) {
                        index = 3;
                    }
                    writeVarInt(packed, index);
                }
            }
        }

        File file = File.createTempFile("bosketball-flat", ".schem");
        file.deleteOnExit();
        try (OutputStream fos = new java.io.FileOutputStream(file);
             DataOutputStream out = new DataOutputStream(new GZIPOutputStream(fos))) {
            out.writeByte(10);
            writeString(out, "Schematic");
            writeIntTag(out, "Version", 2);
            writeIntTag(out, "DataVersion", 4903);
            writeShortTag(out, "Width", w);
            writeShortTag(out, "Height", h);
            writeShortTag(out, "Length", l);
            writeIntTag(out, "PaletteMax", palette.size());
            writePalette(out, palette);
            byte[] data = packed.toByteArray();
            out.writeByte(7);
            writeString(out, "BlockData");
            out.writeInt(data.length);
            out.write(data);

            out.writeByte(9);
            writeString(out, "BlockEntities");
            out.writeByte(10);
            out.writeInt(2);

            // Entry 1 — sign: flat front_text / is_waxed, exactly like Axiom writes them.
            // (A list element is a bare compound payload: named tags, then TAG_End.)
            writeStringTag(out, "Id", "minecraft:sign");
            writeIntArrayTag(out, "Pos", new int[]{signX, 1, 4});
            writeByteTag(out, "is_waxed", (byte) 0);
            out.writeByte(10);
            writeString(out, "front_text");
            writeStringTag(out, "color", "light_blue");
            writeByteTag(out, "has_glowing_text", (byte) 1);
            out.writeByte(9);
            writeString(out, "messages");
            out.writeByte(8);
            out.writeInt(4);
            writeString(out, "CREDIT");
            writeString(out, "");
            writeString(out, "@sseize");
            writeString(out, "-------------");
            out.writeByte(0);
            out.writeByte(0);

            // Entry 2 — skull: flat profile {name, properties}, no Data wrapper.
            writeStringTag(out, "Id", "minecraft:skull");
            writeIntArrayTag(out, "Pos", new int[]{headX, 1, 4});
            out.writeByte(10);
            writeString(out, "profile");
            writeStringTag(out, "name", "maxlananas");
            // texture carried by the file: the exact skin, no lookup at all
            out.writeByte(9);
            writeString(out, "properties");
            out.writeByte(10);
            out.writeInt(1);
            writeStringTag(out, "name", "textures");
            writeStringTag(out, "value", "dGVzdC10ZXh0dXJl");
            writeStringTag(out, "signature", "c2ln");
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(0);

            out.writeByte(0);
        }
        return file;
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
        out.writeByte(0);
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

    private static void writeByteTag(DataOutputStream out, String name, byte value) throws IOException {
        out.writeByte(1);
        writeString(out, name);
        out.writeByte(value);
    }

    private static void writeStringTag(DataOutputStream out, String name, String value) throws IOException {
        out.writeByte(8);
        writeString(out, name);
        writeString(out, value);
    }

    private static void writeIntArrayTag(DataOutputStream out, String name, int[] values) throws IOException {
        out.writeByte(11);
        writeString(out, name);
        out.writeInt(values.length);
        for (int value : values) {
            out.writeInt(value);
        }
    }

    private static void writeShortTag(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(2);
        writeString(out, name);
        out.writeShort(value);
    }
}
