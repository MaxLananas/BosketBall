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

/**
 * A WordEdit / FastAsyncWorldEdit schematic, decoded once into flat arrays and replayable any
 * number of times inside a single world.
 *
 * <h2>Why flat arrays</h2>
 * A {@code .schem} stores one var-int palette index per block. We keep exactly that: an
 * {@code int[]} of palette indices plus the palette itself, and resolve the palette strings into
 * {@link BlockData} objects <em>once</em> on the main thread. A court therefore costs ~
 * {@code width*height*length*4} bytes of memory no matter how many arenas reuse it, and pasting is
 * a single array walk with no parsing, no allocation and no I/O.
 *
 * <p>Both the modern Sponge/FAWE format ({@code Schematic} root, {@code Palette} +
 * {@code BlockData}) and the legacy v1 layout are understood, and the file may be raw, gzip'd or
 * zlib'd — all three are sniffed from the first bytes instead of trusting the extension.</p>
 */
public final class CourtSchematic {

    private final String name;
    private final int width;
    private final int height;
    private final int length;
    /** Offset stored by WorldEdit ({@code /copy} origin relative to the structure corner). */
    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;
    /** Palette index per block, index = (y * length + z) * width + x. */
    private final int[] blocks;
    private final String[] palette;
    private final int nonAirBlocks;
    private final int dataVersion;
    private final int blockEntityCount;

    /** Palette resolved to live {@link BlockData}; built on first paste (main thread only). */
    private BlockData[] resolved;
    /** Cached Y offset (from the paste origin) of the floor's top surface at the centre column. */
    private int floorOffsetAtCenter = Integer.MIN_VALUE;

    private CourtSchematic(String name, int width, int height, int length,
                           int offsetX, int offsetY, int offsetZ,
                           int[] blocks, String[] palette, int nonAirBlocks,
                           int dataVersion, int blockEntityCount) {
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
        this.blockEntityCount = blockEntityCount;
    }

    // =========================================================================
    //  LOADING
    // =========================================================================

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
            try {
                root = NbtReader.readRoot(decoded);
            } catch (java.io.EOFException truncated) {
                // EOFException carries no message, which used to surface as a blank
                // "Cannot load schematic ''" in the console.
                throw new IOException("Truncated or invalid schematic file (" + file.getName()
                    + ") — re-save it with //schem save <name>", truncated);
            }
        }
        // FAWE writes `Schematic`; older WorldEdit wrote the fields straight at the root.
        Object nested = root.get("Schematic");
        Map<String, Object> data = asCompound(nested) != null ? asCompound(nested) : root;

        int w = intOf(data, "Width", -1);
        int h = intOf(data, "Height", -1);
        int l = intOf(data, "Length", -1);
        if (w <= 0 || h <= 0 || l <= 0) {
            throw new IOException("Not a schematic: missing/zero Width, Height or Length");
        }

        String[] palette;
        Map<String, Object> paletteCompound = asCompound(data.get("Palette"));
        if (paletteCompound != null) {
            palette = paletteFromCompound(paletteCompound);
        } else {
            List<Object> paletteList = asList(data.get("Palette"));
            if (paletteList == null) {
                throw new IOException("Unsupported schematic (no Palette). Re-save it with FastAsyncWorldEdit: //copy then //schem save terrain");
            }
            palette = new String[paletteList.size()];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = String.valueOf(paletteList.get(i));
            }
        }

        byte[] packed = asByteArray(data.get("BlockData"));
        if (packed == null) {
            throw new IOException("Unsupported schematic (no BlockData). This looks like an ancient MCEdit .schematic — re-save it as .schem with FastAsyncWorldEdit");
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
                    throw new IOException("Truncated BlockData (" + i + " of " + expected + " blocks)");
                }
                int b = packed[cursor++] & 0xFF;
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    break;
                }
                shift += 7;
                if (shift > 35) {
                    throw new IOException("Corrupt var-int in BlockData at block " + i);
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

        int blockEntities = asList(data.get("BlockEntities")) != null
            ? asList(data.get("BlockEntities")).size() : 0;

        String fileName = file.getName();
        String base = fileName.contains(".")
            ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        return new CourtSchematic(base, w, h, l, offsetX, offsetY, offsetZ, indices, palette,
            nonAir, intOf(data, "DataVersion", -1), blockEntities);
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

    static boolean isAirName(String name) {
        if (name == null) {
            return true;
        }
        String n = name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
        // FAWE strips the namespace; property suffixes never apply to air.
        int bracket = n.indexOf('[');
        if (bracket > 0) {
            n = n.substring(0, bracket);
        }
        return n.equals("air") || n.equals("cave_air") || n.equals("void_air") || n.equals("structure_void");
    }

    // =========================================================================
    //  PASTING
    // =========================================================================

    /**
     * Pastes the structure with its smallest corner at {@code (originX, originY, originZ)} —
     * the exact opposite of what {@code //paste} does after a centred {@code //copy}, because a
     * centred copy's origin is the player, not a corner.
     *
     * @return the number of blocks actually written
     */
    public int paste(World world, int originX, int originY, int originZ, boolean skipExistingAir) {
        return pasteSlice(world, originX, originY, originZ, skipExistingAir, Integer.MAX_VALUE, 0).written();
    }

    /** Result of one bounded paste step. */
    public record PasteProgress(int written, int nextIndex, boolean complete) {
    }

    /**
     * Pastes at most {@code maxBlocks} blocks of the structure, resuming at {@code fromIndex}.
     *
     * <p>This is what makes {@code max-blocks-per-tick} real: a 30x18x4 court is ~2 200 block
     * writes, which is a measurable tick spike on a busy server, so the pool spreads a single
     * court over a few ticks instead of stalling. Progress is pure integer state, so a paste can
     * be abandoned at any point without side effects.</p>
     */
    public PasteProgress pasteSlice(World world, int originX, int originY, int originZ,
                                    boolean skipExistingAir, int maxBlocks, int fromIndex) {
        BlockData[] data = resolvedData();
        int index = Math.max(0, fromIndex);
        int limit = index + Math.max(1, maxBlocks);
        int written = 0;
        int plane = width * length;
        while (index < blocks.length && index < limit) {
            int remaining = blocks.length - index;
            // Walk the flat array directly: index = (y * length + z) * width + x.
            int y = index / plane;
            int inPlane = index - y * plane;
            int z = inPlane / width;
            int x = inPlane - z * width;
            int spanX = Math.min(width - x, Math.min(remaining, limit - index));
            int wy = originY + y;
            int wz = originZ + z;
            for (int dx = 0; dx < spanX; dx++) {
                BlockData block = data[blocks[index + dx]];
                if (block == null) {
                    continue; // air
                }
                int wx = originX + x + dx;
                if (skipExistingAir && isAirData(block) && world.getBlockAt(wx, wy, wz).getType().isAir()) {
                    continue;
                }
                world.setBlockData(wx, wy, wz, block);
                written++;
            }
            index += spanX;
        }
        return new PasteProgress(written, index, index >= blocks.length);
    }

    /** Resolves the palette into {@link BlockData} once; must run on the main server thread. */
    private BlockData[] resolvedData() {
        BlockData[] local = resolved;
        if (local != null) {
            return local;
        }
        BlockData[] built = new BlockData[palette.length];
        for (int i = 0; i < palette.length; i++) {
            if (isAirName(palette[i])) {
                built[i] = null;
                continue;
            }
            String spec = palette[i].contains(":") ? palette[i] : "minecraft:" + palette[i];
            try {
                built[i] = Bukkit.createBlockData(spec);
            } catch (IllegalArgumentException ex) {
                // A single unknown block must not destroy the whole court: fall back to stone and
                // keep going (the log tells the admin exactly which block is at fault).
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

    /**
     * Y offset (relative to the paste origin) of the <em>top surface</em> of the build's floor,
     * measured on the centre column. This is what {@code floorY} must be: the ball bounces on it,
     * the players stand on it and both rims hang {@code rim-relative-y} blocks above it — so a
     * schematic whose base is not exactly at the copy origin still calibrates itself.
     */
    public int floorOffsetAtCenter() {
        int cached = floorOffsetAtCenter;
        if (cached != Integer.MIN_VALUE) {
            return cached;
        }
        int cx = width / 2;
        int cz = length / 2;
        int result = 0;
        for (int y = height - 1; y >= 0; y--) {
            int index = (y * length + cz) * width + cx;
            if (!isAirName(palette[blocks[index]])) {
                result = y + 1;
                break;
            }
        }
        floorOffsetAtCenter = result;
        return result;
    }

    // =========================================================================
    //  METADATA
    // =========================================================================

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

    public int blockEntityCount() {
        return blockEntityCount;
    }

    public long footprintChunks() {
        long chunksX = (width + 15L) / 16L;
        long chunksZ = (length + 15L) / 16L;
        return chunksX * chunksZ;
    }

    /** Palette entries, for {@code /bb arena schematic info} (air excluded). */
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

    // =========================================================================
    //  NBT VALUE HELPERS
    // =========================================================================

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
