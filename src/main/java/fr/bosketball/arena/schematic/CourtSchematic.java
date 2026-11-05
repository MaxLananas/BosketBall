package fr.bosketball.arena.schematic;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

public final class CourtSchematic {

    private final String name;
    private final int width;
    private final int height;
    private final int length;

    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;

    private final int[] blocks;
    private final String[] palette;
    private final int nonAirBlocks;
    private final int dataVersion;
    private final int formatVersion;
    private final int blockEntityCount;

    private static BlockData airData() {
        return Material.AIR.createBlockData();
    }

    private BlockData[] resolved;

    private int floorOffsetAtCenter = Integer.MIN_VALUE;

    private CourtSchematic(String name, int width, int height, int length,
                           int offsetX, int offsetY, int offsetZ,
                           int[] blocks, String[] palette, int nonAirBlocks,
                           int dataVersion, int formatVersion, int blockEntityCount) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.length = length;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.blocks = blocks;
        this.palette = palette;
        this.nonAirBlocks = nonAirBlocks;
        this.dataVersion = dataVersion;
        this.formatVersion = formatVersion;
        this.blockEntityCount = blockEntityCount;
    }

    public static CourtSchematic load(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("Schematic file not found: " + file);
        }
        Map<String, Object> root;
        try (InputStream raw = new BufferedInputStream(new FileInputStream(file), 64 * 1024)) {
            raw.mark(2);
            int b0 = raw.read();
            int b1 = raw.read();
            raw.reset();
            InputStream decoded;
            if (b0 == 0x1F && b1 == 0x8B) {
                decoded = new GZIPInputStream(raw, 64 * 1024);
            } else if (b0 == 0x78) {
                decoded = new InflaterInputStream(raw, new java.util.zip.Inflater(), 64 * 1024);
            } else {
                decoded = raw;
            }
            try (InputStream in = decoded) {
                root = NbtReader.readRoot(in);
            } catch (java.io.EOFException truncated) {

                throw new IOException("Truncated or invalid schematic file (" + file.getName()
                    + ") — re-save it with //schem save <name>", truncated);
            }
        }

        Object nested = root.get("Schematic");
        Map<String, Object> data = asCompound(nested) != null ? asCompound(nested) : root;

        int w = intOf(data, "Width", -1);
        int h = intOf(data, "Height", -1);
        int l = intOf(data, "Length", -1);
        if (w <= 0 || h <= 0 || l <= 0) {
            throw new IOException("Not a schematic: missing/zero Width, Height or Length");
        }

        Map<String, Object> blocksTag = asCompound(data.get("Blocks"));
        Map<String, Object> container = blocksTag != null ? blocksTag : data;
        int version = intOf(data, "Version", -1);

        Object paletteTag = container.get("Palette");
        if (paletteTag == null) {
            paletteTag = data.get("Palette");
        }
        String[] palette;
        Map<String, Object> paletteCompound = asCompound(paletteTag);
        if (paletteCompound != null) {
            palette = paletteFromCompound(paletteCompound);
        } else if (asList(paletteTag) != null) {
            List<Object> paletteList = asList(paletteTag);
            palette = new String[paletteList.size()];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = String.valueOf(paletteList.get(i));
            }
        } else {
            throw new IOException("Unsupported schematic (no Palette, version " + version
                + ", root tags " + data.keySet() + "). Re-save it with FastAsyncWorldEdit:"
                + " //copy then //schem save terrain");
        }

        byte[] packed = asByteArray(container.get("Data"));
        if (packed == null) {
            packed = asByteArray(container.get("BlockData"));
        }
        if (packed == null) {
            packed = asByteArray(data.get("Data"));
        }
        if (packed == null) {
            packed = asByteArray(data.get("BlockData"));
        }
        if (packed == null) {
            throw new IOException(data.containsKey("Blocks") || data.containsKey("Materials")
                ? "Unsupported schematic (no block data, version " + version + "). This looks like an"
                    + " ancient MCEdit .schematic — re-save it as .schem with FastAsyncWorldEdit"
                : "Unsupported schematic (no Data/BlockData, version " + version + ", root tags "
                    + data.keySet() + "). Re-save it with //schem save terrain");
        }
        int expected = w * h * l;
        int[] indices = new int[expected];
        int nonAir = 0;
        int cursor = 0;
        for (int i = 0; i < expected; i++) {
            int value = 0;
            int shift = 0;
            while (true) {
                if (cursor >= packed.length) {
                    throw new IOException("Truncated block data (" + i + " of " + expected + " blocks)");
                }
                int b = packed[cursor++] & 0xFF;
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    break;
                }
                shift += 7;
                if (shift > 35) {
                    throw new IOException("Corrupt var-int in block data at block " + i);
                }
            }
            if (value < 0 || value >= palette.length) {
                throw new IOException("Block " + i + " references palette entry " + value
                    + " but the palette only has " + palette.length + " entries");
            }
            indices[i] = value;
        }
        for (int i = 0; i < expected; i++) {
            if (!isAirName(palette[indices[i]])) {
                nonAir++;
            }
        }

        int offsetX = 0;
        int offsetY = 0;
        int offsetZ = 0;
        int[] offset = asIntArray(data.get("Offset"));
        if (offset != null && offset.length >= 3) {
            offsetX = offset[0];
            offsetY = offset[1];
            offsetZ = offset[2];
        }

        List<Object> tileEntities = asList(container.get("BlockEntities"));
        if (tileEntities == null) {
            tileEntities = asList(container.get("TileEntities"));
        }
        if (tileEntities == null) {
            tileEntities = asList(data.get("BlockEntities"));
        }
        if (tileEntities == null) {
            tileEntities = asList(data.get("TileEntities"));
        }
        int blockEntities = tileEntities != null ? tileEntities.size() : 0;

        String fileName = file.getName();
        String base = fileName.contains(".")
            ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        return new CourtSchematic(base, w, h, l, offsetX, offsetY, offsetZ, indices, palette,
            nonAir, intOf(data, "DataVersion", -1), version, blockEntities);
    }

    private static String[] paletteFromCompound(Map<String, Object> palette) {
        int max = -1;
        for (Object value : palette.values()) {
            if (value instanceof Number n) {
                max = Math.max(max, n.intValue());
            }
        }
        if (max < 0) {
            throw new IllegalArgumentException("Empty schematic palette");
        }
        String[] names = new String[max + 1];
        for (Map.Entry<String, Object> e : palette.entrySet()) {
            if (e.getValue() instanceof Number n) {
                int index = n.intValue();
                if (index >= 0 && index < names.length) {
                    names[index] = e.getKey();
                }
            }
        }
        for (int i = 0; i < names.length; i++) {
            if (names[i] == null) {
                names[i] = "minecraft:air";
            }
        }
        return names;
    }

    private static final double PLATFORM_COVERAGE = 0.5;
    private static final int MIN_LINE_MARKINGS = 16;

    static boolean isDecorationHelper(String name) {
        if (name == null) {
            return false;
        }
        String n = name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
        int bracket = n.indexOf('[');
        if (bracket > 0) {
            n = n.substring(0, bracket);
        }
        return n.equals("light");
    }

    static boolean isLineMarking(String name) {
        if (name == null) {
            return false;
        }
        String n = name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
        int bracket = n.indexOf('[');
        if (bracket > 0) {
            n = n.substring(0, bracket);
        }
        return n.contains("white_") || n.contains("quartz") || n.equals("snow_block")
            || n.equals("smooth_stone") || n.equals("polished_diorite");
    }

    static boolean isAirName(String name) {
        if (name == null) {
            return true;
        }
        String n = name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;

        int bracket = n.indexOf('[');
        if (bracket > 0) {
            n = n.substring(0, bracket);
        }
        return n.equals("air") || n.equals("cave_air") || n.equals("void_air") || n.equals("structure_void");
    }

    public record PasteProgress(int written, int nextIndex, boolean complete) {
    }

    public PasteProgress pasteSlice(World world, int originX, int originY, int originZ,
                                    boolean writeAir, int maxBlocks, int fromIndex) {
        BlockData[] data = resolvedData();
        int index = Math.max(0, fromIndex);
        int limit = index + Math.max(1, maxBlocks);
        int written = 0;
        int plane = width * length;
        while (index < blocks.length && index < limit) {
            int remaining = blocks.length - index;

            int y = index / plane;
            int inPlane = index - y * plane;
            int z = inPlane / width;
            int x = inPlane - z * width;

            int spanX = Math.max(1, Math.min(width - x, Math.min(remaining, limit - index)));
            int wy = originY + y;
            int wz = originZ + z;
            int visible = 0;
            for (int dx = 0; dx < spanX; dx++) {
                BlockData block = data[blocks[index + dx]];
                if (block == null || (!writeAir && isAirData(block))) {
                    continue;
                }
                int wx = originX + x + dx;
                world.setBlockData(wx, wy, wz, block);
                visible++;
            }
            written += visible;
            index += spanX;
        }
        return new PasteProgress(written, index, index >= blocks.length);
    }

    private BlockData[] resolvedData() {
        BlockData[] local = resolved;
        if (local != null) {
            return local;
        }
        BlockData[] built = new BlockData[palette.length];
        for (int i = 0; i < palette.length; i++) {
            if (isAirName(palette[i])) {

                built[i] = airData();
                continue;
            }
            String spec = palette[i].contains(":") ? palette[i] : "minecraft:" + palette[i];
            try {
                built[i] = Bukkit.createBlockData(spec);
            } catch (IllegalArgumentException ex) {

                Bukkit.getLogger().warning("[BosketBall] Schematic block '" + spec
                    + "' is unknown on this server version — using STONE instead.");
                built[i] = Material.STONE.createBlockData();
            }
        }
        resolved = built;
        return built;
    }

    private static boolean isAirData(BlockData data) {
        Material material = data.getMaterial();
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    public int floorOffsetAtCenter() {
        int cached = floorOffsetAtCenter;
        if (cached != Integer.MIN_VALUE) {
            return cached;
        }
        int[] structural = structuralLayers();
        int result = deckOffset(structural) + 1;
        floorOffsetAtCenter = result;
        return result;
    }

    private int[] structuralLayers() {
        int[] layers = new int[height];
        int widest = 0;
        for (int y = 0; y < height; y++) {
            int count = 0;
            int base = y * width * length;
            for (int i = 0; i < width * length; i++) {
                String entry = palette[blocks[base + i]];
                if (!isAirName(entry) && !isDecorationHelper(entry)) {
                    count++;
                }
            }
            layers[y] = count;
            widest = Math.max(widest, count);
        }
        return layers;
    }

    public static int deckOffset(int[] structuralLayers) {
        int widest = 0;
        for (int count : structuralLayers) {
            widest = Math.max(widest, count);
        }
        if (widest == 0) {
            return 0;
        }
        int need = Math.max(1, (int) Math.ceil(widest * PLATFORM_COVERAGE));
        int deck = -1;
        for (int y = 0; y < structuralLayers.length; y++) {
            if (structuralLayers[y] >= need) {
                deck = y;
            } else if (deck >= 0) {
                break;
            }
        }
        if (deck < 0) {
            for (int y = 0; y < structuralLayers.length; y++) {
                if (structuralLayers[y] > 0) {
                    return y;
                }
            }
            return 0;
        }
        return deck;
    }

    public boolean deckHasLineMarkings() {
        int deck = Math.max(0, Math.min(height - 1, floorOffsetAtCenter() - 1));
        int base = deck * width * length;
        int markings = 0;
        for (int i = 0; i < width * length; i++) {
            if (isLineMarking(palette[blocks[base + i]])) {
                markings++;
            }
        }
        return markings >= MIN_LINE_MARKINGS;
    }

    public int deckLineMarkings() {
        int deck = Math.max(0, Math.min(height - 1, floorOffsetAtCenter() - 1));
        int base = deck * width * length;
        int markings = 0;
        for (int i = 0; i < width * length; i++) {
            if (isLineMarking(palette[blocks[base + i]])) {
                markings++;
            }
        }
        return markings;
    }

    public double localCenterX() {
        return localCenter(offsetX, width);
    }

    public double localCenterZ() {
        return localCenter(offsetZ, length);
    }

    private static double localCenter(int offset, int span) {

        int local = -offset;
        if (local >= 0 && local <= span) {
            return local;
        }
        return span / 2.0;
    }

    public String name() {
        return name;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int length() {
        return length;
    }

    public int offsetX() {
        return offsetX;
    }

    public int offsetY() {
        return offsetY;
    }

    public int offsetZ() {
        return offsetZ;
    }

    public int nonAirBlocks() {
        return nonAirBlocks;
    }

    public int paletteSize() {
        return palette.length;
    }

    public int dataVersion() {
        return dataVersion;
    }

    public int formatVersion() {
        return formatVersion;
    }

    public int blockEntityCount() {
        return blockEntityCount;
    }

    public long footprintChunks() {
        long chunksX = (width + 15L) / 16L;
        long chunksZ = (length + 15L) / 16L;
        return chunksX * chunksZ;
    }

    public List<String> palettePreview(int maxEntries) {
        List<String> out = new ArrayList<>();
        for (String entry : palette) {
            if (!isAirName(entry)) {
                out.add(entry);
                if (out.size() >= maxEntries) {
                    break;
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asCompound(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return value instanceof List ? (List<Object>) value : null;
    }

    private static byte[] asByteArray(Object value) {
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        if (value instanceof int[] ints) {
            byte[] bytes = new byte[ints.length];
            for (int i = 0; i < ints.length; i++) {
                bytes[i] = (byte) ints[i];
            }
            return bytes;
        }
        return null;
    }

    private static int[] asIntArray(Object value) {
        return value instanceof int[] ints ? ints : null;
    }

    private static int intOf(Map<String, Object> compound, String key, int fallback) {
        Object value = compound.get(key);
        return value instanceof Number n ? n.intValue() : fallback;
    }
}
