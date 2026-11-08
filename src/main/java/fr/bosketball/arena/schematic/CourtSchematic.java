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
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.DyeColor;

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
    private final List<SavedBlockEntity> blockEntities;

    private static BlockData airData() {
        return Material.AIR.createBlockData();
    }

    private BlockData[] resolved;

    private BlockData[] rotatedResolved;

    private int rotationDegrees;

    private int[] attachableIndices;

    private int floorOffsetAtCenter = Integer.MIN_VALUE;

    private CourtSchematic(String name, int width, int height, int length,
                           int offsetX, int offsetY, int offsetZ,
                           int[] blocks, String[] palette, int nonAirBlocks,
                           int dataVersion, int formatVersion, int blockEntityCount,
                           List<SavedBlockEntity> blockEntities) {
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
        this.blockEntities = blockEntities == null ? List.of() : List.copyOf(blockEntities);
    }

    /**
     * A block entity (sign, banner, chest, …) carried by the schematic, with local coordinates.
     */
    public record SavedBlockEntity(int x, int y, int z, String id, Map<String, Object> data) {

        public boolean isSign() {
            String key = id == null ? "" : id;
            return key.endsWith("sign") || key.endsWith("hanging_sign");
        }

        /** A head / skull block entity: the only place a custom skin can hide in a .schem. */
        public boolean isSkull() {
            String key = id == null ? "" : id;
            return key.endsWith("skull") || key.equals("minecraft:head") || key.endsWith("_head");
        }

        /** True when the payload carries something worth restoring after a paste. */
        public boolean carriesPayload() {
            return data != null && !data.isEmpty();
        }
    }

    /** Palette entry at local coordinates ("minecraft:air" when out of bounds). */
    public String blockNameAt(int x, int y, int z) {
        if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= length) {
            return "minecraft:air";
        }
        return palette[blocks[(y * length + z) * width + x]];
    }

    public List<SavedBlockEntity> blockEntities() {
        return blockEntities;
    }

    /** Custom heads carried by the build (player heads pasted with their skin). */
    public int headCount() {
        int count = 0;
        for (SavedBlockEntity entity : blockEntities) {
            if (entity.isSkull()) {
                count++;
            }
        }
        return count;
    }

    public int signCount() {
        int count = 0;
        for (SavedBlockEntity entity : blockEntities) {
            if (entity.isSign()) {
                count++;
            }
        }
        return count;
    }

    /** Rotation applied when pasting: 0 or 180 degrees around the vertical axis. */
    public int rotationDegrees() {
        return rotationDegrees;
    }

    public void setRotation(int degrees) {
        int normalized = Math.floorMod(Math.round(degrees / 90f) * 90, 360);
        if (this.rotationDegrees != normalized) {
            this.rotatedResolved = null;
            this.paintedCenterComputed = false;
            this.paintedCenterX = -1;
            this.paintedCenterZ = -1;
        }
        this.rotationDegrees = normalized;
    }

    public boolean isRotated() {
        return rotationDegrees != 0;
    }

    /** Width of the pasted build — the X footprint swaps at 90/270. */
    public int pasteWidth() {
        return rotationDegrees == 90 || rotationDegrees == 270 ? length : width;
    }

    /** Length of the pasted build — the Z footprint swaps at 90/270. */
    public int pasteLength() {
        return rotationDegrees == 90 || rotationDegrees == 270 ? width : length;
    }

    public StructureRotation structureRotation() {
        return switch (rotationDegrees) {
            case 90 -> StructureRotation.CLOCKWISE_90;
            case 180 -> StructureRotation.CLOCKWISE_180;
            case 270 -> StructureRotation.COUNTERCLOCKWISE_90;
            default -> StructureRotation.NONE;
        };
    }

    /** Destination X of a source block once the paste rotation is applied. */
    int localX(int x, int z) {
        return switch (rotationDegrees) {
            case 90 -> length - 1 - z;
            case 180 -> width - 1 - x;
            case 270 -> z;
            default -> x;
        };
    }

    /** Destination Z of a source block once the paste rotation is applied. */
    int localZ(int x, int z) {
        return switch (rotationDegrees) {
            case 90 -> x;
            case 180 -> length - 1 - z;
            case 270 -> width - 1 - x;
            default -> z;
        };
    }

    /** Centre of the pasted footprint, in blocks, relative to the paste origin. */
    public double pasteCenterX() {
        return pasteWidth() / 2.0;
    }

    public double pasteCenterZ() {
        return pasteLength() / 2.0;
    }

    /** The largest build this plugin will decode: 8 million blocks, ten minutes of pasting. */
    public static final long MAX_SCHEMATIC_BLOCKS = 8_000_000L;

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
            InputStream decoded = b0 == 0x1F && b1 == 0x8B
                ? new GZIPInputStream(raw, 64 * 1024)
                : b0 == 0x78
                    ? new InflaterInputStream(raw, new java.util.zip.Inflater(), 64 * 1024)
                    : raw;
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
        // A real court is a few hundred thousand blocks. The volume is computed in long because
        // w * h * l overflows an int into a negative array size, and a plausible-looking volume
        // that is simply enormous would be an OutOfMemoryError instead of a message.
        long volume = (long) w * (long) h * (long) l;
        if (volume > MAX_SCHEMATIC_BLOCKS) {
            throw new IOException("Schematic is too large: " + w + "x" + h + "x" + l + " = " + volume
                + " blocks (limit " + MAX_SCHEMATIC_BLOCKS + "). Re-save a smaller terrain.");
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
        int expected = (int) volume;
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
        List<SavedBlockEntity> saved = new ArrayList<>();
        if (tileEntities != null) {
            for (Object raw : tileEntities) {
                Map<String, Object> compound = asCompound(raw);
                if (compound == null) {
                    continue;
                }
                int[] pos = asIntArray(compound.get("Pos"));
                if (pos == null || pos.length < 3) {
                    int[] shortForm = asIntArray(new int[]{
                        intOf(compound, "x", Integer.MIN_VALUE),
                        intOf(compound, "y", Integer.MIN_VALUE),
                        intOf(compound, "z", Integer.MIN_VALUE)});
                    if (shortForm[0] == Integer.MIN_VALUE || shortForm[1] == Integer.MIN_VALUE
                        || shortForm[2] == Integer.MIN_VALUE) {
                        continue;
                    }
                    pos = shortForm;
                }
                String id = stringOf(compound.get("Id"));
                saved.add(new SavedBlockEntity(pos[0], pos[1], pos[2],
                    id == null ? "" : id, blockEntityPayload(compound)));
            }
        }

        String fileName = file.getName();
        String base = fileName.contains(".")
            ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        return new CourtSchematic(base, w, h, l, offsetX, offsetY, offsetZ, indices, palette,
            nonAir, intOf(data, "DataVersion", -1), version, blockEntities, saved);
    }

    /**
     * Payload of a saved block entity (sign text, skull skin, banner pattern…).
     *
     * <p>Sponge <b>v3</b> nests it under {@code Data}; Sponge <b>v2</b> (Axiom, FAWE 2.6, most
     * 1.20 builds) writes the payload <b>flat</b> inside the block-entity compound itself. Reading
     * only {@code Data} silently threw the sign text and the custom-head profile away — the sign
     * came back blank and the head came back as a default Steve head.</p>
     */
    static Map<String, Object> blockEntityPayload(Map<String, Object> compound) {
        if (compound == null) {
            return Map.of();
        }
        Map<String, Object> nested = asCompound(compound.get("Data"));
        if (nested != null) {
            return nested;
        }
        Map<String, Object> flat = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : compound.entrySet()) {
            switch (entry.getKey()) {
                case "Id", "Pos", "Data", "x", "y", "z", "keepPacked", "metadata" -> {
                    // Coordinates / identity, already read separately.
                }
                default -> flat.put(entry.getKey(), entry.getValue());
            }
        }
        return flat;
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
        int index = Math.clamp(fromIndex, 0, blocks.length);
        int limit = sliceLimit(index, maxBlocks, blocks.length);
        int written = 0;
        int plane = width * length;

        if (rotationDegrees != 0) {
            while (index < blocks.length && index < limit) {
                int y = index / plane;
                int inPlane = index - y * plane;
                int z = inPlane / width;
                int x = inPlane - z * width;
                BlockData block = data[blocks[index]];
                index++;
                if (block == null || (!writeAir && isAirData(block))) {
                    continue;
                }
                place(world, originX + localX(x, z), originY + y, originZ + localZ(x, z), block);
                written++;
            }
            return new PasteProgress(written, index, index >= blocks.length);
        }

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
                place(world, wx, wy, wz, block);
                visible++;
            }
            written += visible;
            index += spanX;
        }
        return new PasteProgress(written, index, index >= blocks.length);
    }

    private BlockData[] resolvedData() {
        if (rotationDegrees != 0) {
            BlockData[] rotatedLocal = rotatedResolved;
            if (rotatedLocal != null) {
                return rotatedLocal;
            }
            BlockData[] base = resolvedDataUnrotated();
            BlockData[] turned = new BlockData[base.length];
            for (int i = 0; i < base.length; i++) {
                BlockData entry = base[i];
                if (entry == null) {
                    continue;
                }
                BlockData copy = entry.clone();
                copy.rotate(structureRotation());
                turned[i] = copy;
            }
            rotatedResolved = turned;
            return turned;
        }
        return resolvedDataUnrotated();
    }

    private BlockData[] resolvedDataUnrotated() {
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
            built[i] = resolveEntry(i);
        }
        resolved = built;
        return built;
    }

    /**
     * Turns one palette entry into a block state this server can actually paste, and <b>never
     * throws</b>.
     *
     * <p>A single bad entry of the palette used to be able to ruin a court: the block state was
     * created once, eagerly, and anything unknown (a block from a newer Minecraft, a property that
     * no longer exists, a typo in a hand-edited file) fell back to <i>stone</i> — a wall through the
     * middle of the floor. Now the entry is retried in order:</p>
     *
     * <ol>
     *   <li>exactly as written;</li>
     *   <li>with <b>one</b> block-state property removed (keeps stairs/waterlogging/trapdoors);</li>
     *   <li>as the bare material, with the server's default state;</li>
     *   <li>as <b>air</b>, and the entry is reported in the log with its exact name.</li>
     * </ol>
     */
    private BlockData resolveEntry(int index) {
        String raw = palette[index];
        boolean first = true;
        for (String candidate : resolutionCandidates(raw)) {
            String spec = candidate.contains(":") ? candidate : "minecraft:" + candidate;
            try {
                BlockData data = Bukkit.createBlockData(spec);
                forceClosed(data);
                if (!first) {
                    repairedPaletteEntries++;
                    paletteNotes.add("'" + raw + "' -> '" + candidate
                        + "' (a block-state property of this server is different)");
                    Bukkit.getLogger().warning("[BosketBall] Schematic block '" + raw
                        + "' has an unknown block-state property here — pasting it as '"
                        + candidate + "' instead.");
                }
                return data;
            } catch (Throwable ignored) {
                // try the next candidate, never abort the court
            }
            first = false;
        }
        droppedPaletteEntries++;
        paletteNotes.add("'" + raw + "' dropped (no such block on this server): pasted as air");
        Bukkit.getLogger().warning("[BosketBall] Schematic block '" + raw + "' does not exist on this"
            + " server — it is pasted as air so the rest of the court stays intact. Re-save the build"
            + " from a server of the same version to keep it.");
        return airData();
    }

    /**
     * Paste candidates for one palette entry, simplest last. Pure: no Bukkit call, so the order
     * (exact, then each property dropped, then the bare material) is unit-tested.
     */
    public static List<String> resolutionCandidates(String raw) {
        List<String> out = new ArrayList<>(4);
        if (raw == null || raw.isBlank()) {
            return out;
        }
        String spec = raw.trim();
        out.add(spec);
        int open = spec.indexOf('[');
        if (open <= 0 || !spec.endsWith("]")) {
            return out;
        }
        String material = spec.substring(0, open);
        String body = spec.substring(open + 1, spec.length() - 1);
        String[] properties = body.isEmpty() ? new String[0] : body.split(",");
        if (properties.length > 0 && properties.length <= 16) {
            for (int skip = 0; skip < properties.length; skip++) {
                StringBuilder candidate = new StringBuilder(material).append('[');
                for (int i = 0, written = 0; i < properties.length; i++) {
                    if (i == skip) {
                        continue;
                    }
                    if (written++ > 0) {
                        candidate.append(',');
                    }
                    candidate.append(properties[i]);
                }
                candidate.append(']');
                String text = candidate.toString();
                if (!out.contains(text)) {
                    out.add(text);
                }
            }
        }
        if (!out.contains(material)) {
            out.add(material);
        }
        return out;
    }

    private final List<String> paletteNotes = new ArrayList<>();
    private int droppedPaletteEntries;
    private int repairedPaletteEntries;

    /** Palette entries this server cannot paste at all: they are written as air. */
    public int droppedPaletteEntries() {
        resolvedData();
        return droppedPaletteEntries;
    }

    /** Palette entries that lost an unknown block-state property but are still pasted. */
    public int repairedPaletteEntries() {
        resolvedData();
        return repairedPaletteEntries;
    }

    /** Human-readable report of every palette entry the paste had to rewrite (empty = perfect). */
    public List<String> paletteNotes() {
        resolvedData();
        return List.copyOf(paletteNotes);
    }

    private int writeFailures;

    /** Blocks the world refused to hold (unplaceable / removed by the server during the paste). */
    public int writeFailures() {
        return writeFailures;
    }

    public void resetWriteFailures() {
        writeFailures = 0;
    }

    /**
     * One guarded block write. A build can always contain one block the server refuses at runtime
     * (a block entity it cannot create, a state dropped by a data pack…): it must cost one block,
     * never the whole court.
     */
    private void place(World world, int x, int y, int z, BlockData data) {
        try {
            world.setBlockData(x, y, z, data);
        } catch (Throwable ex) {
            writeFailures++;
        }
    }

    /** Blocks that fall off when their support block is missing (signs, torches, ...). */
    public static boolean isAttachableName(String name) {
        if (name == null) {
            return false;
        }
        String n = name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
        int bracket = n.indexOf('[');
        if (bracket > 0) {
            n = n.substring(0, bracket);
        }
        if (isAirName(n)) {
            return false;
        }
        return n.endsWith("_sign")
            || n.endsWith("_banner")
            || n.endsWith("_button")
            || n.endsWith("_pressure_plate")
            || n.endsWith("_torch")
            || n.equals("ladder")
            || n.equals("lever")
            || n.equals("tripwire")
            || n.equals("rail")
            || n.endsWith("_rail")
            || n.equals("vine")
            || n.endsWith("_carpet")
            || n.endsWith("_trapdoor")
            || n.endsWith("_fence_gate")
            || n.equals("lantern")
            || n.endsWith("_lantern")
            || n.endsWith("_candle")
            || n.endsWith("_candle_cake")
            || n.equals("flower_pot") || n.endsWith("_flower_pot")
            || n.endsWith("_wall_head")
            || n.endsWith("_head") && !n.equals("head")
            || n.endsWith("_skull")
            || n.endsWith("_hanging_sign")
            || n.equals("bell")
            || n.equals("soul_wall_torch")
            || n.equals("redstone_torch") || n.equals("redstone_wall_torch")
            || n.endsWith("_chain")
            || n.endsWith("_coral_fan") || n.endsWith("_coral_wall_fan")
            || n.endsWith("_mushroom")
            || n.endsWith("_fungus")
            || n.endsWith("_sapling")
            || n.endsWith("_roots") || n.endsWith("_sprouts")
            // Round 39: the outdoor build plants its decor straight on the sand. A flower, a tuft
            // of dry grass or a dripleaf leaf that lands before its support block pops and turns
            // into an item, so they now join the second pass like signs and torches.
            || n.endsWith("_grass")
            || n.endsWith("_bush")
            || n.endsWith("_dripleaf")
            || n.equals("carrots") || n.equals("potatoes") || n.equals("beetroots") || n.equals("wheat")
            || n.equals("azalea") || n.equals("flowering_azalea");
    }

    /** Indices (in stream order) of attachable blocks — pasted in a second pass so they never drop. */
    public int[] attachableIndices() {
        int[] cached = attachableIndices;
        if (cached != null) {
            return cached;
        }
        int[] tmp = new int[Math.max(16, blocks.length / 64)];
        int n = 0;
        for (int i = 0; i < blocks.length; i++) {
            if (!isAttachableName(palette[blocks[i]])) {
                continue;
            }
            if (n == tmp.length) {
                tmp = java.util.Arrays.copyOf(tmp, tmp.length * 2);
            }
            tmp[n++] = i;
        }
        int[] result = java.util.Arrays.copyOf(tmp, n);
        attachableIndices = result;
        return result;
    }

    public int attachableCount() {
        return attachableIndices().length;
    }

    /** Blocks that can be left ajar by a paste (doors and trapdoors). */
    public static boolean isDoorName(String name) {
        if (name == null) {
            return false;
        }
        String n = name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
        int bracket = n.indexOf('[');
        if (bracket > 0) {
            n = n.substring(0, bracket);
        }
        return n.endsWith("_door");
    }

    /**
     * Last safety pass: every attachable of the build (signs, levers, buttons, torches, ladders,
     * rails, trapdoors…) must exist in the world with its exact material. Anything that popped
     * while the neighbours were being written is simply put back.
     *
     * @return how many blocks had to be restored
     */
    public int repairAttachables(World world, int originX, int originY, int originZ) {
        return repairAttachablesSlice(world, originX, originY, originZ, Integer.MAX_VALUE, 0).written();
    }

    /** A resumable repair pass; the ceiling counts examined attachables, not just writes. */
    public PasteProgress repairAttachablesSlice(World world, int originX, int originY, int originZ,
                                                int maxBlocks, int fromIndex) {
        int repaired = 0;
        int plane = width * length;
        BlockData[] data = resolvedData();
        int[] indices = attachableIndices();
        int cursor = Math.clamp(fromIndex, 0, indices.length);
        int limit = sliceLimit(cursor, maxBlocks, indices.length);
        while (cursor < limit) {
            int index = indices[cursor++];
            if (hopeless.contains(index)) {
                continue;
            }
            int y = index / plane;
            int inPlane = index - y * plane;
            int z = inPlane / width;
            int x = inPlane - z * width;
            BlockData expected = data[blocks[index]];
            if (expected == null || isAirData(expected)) {
                continue;
            }
            org.bukkit.block.Block block =
                world.getBlockAt(originX + localX(x, z), originY + y, originZ + localZ(x, z));
            BlockData current = block.getBlockData();
            if (current.getMaterial() == expected.getMaterial() && current.matches(expected)) {
                strikeLess(index);
                continue;
            }
            try {
                block.setBlockData(expected, false);
                repaired++;
            } catch (Throwable ex) {
                hopeless.add(index);
                continue;
            }
            // A decoration that the server physically refuses to keep (a leaf with nothing under
            // it, a flower on sand, a moss patch on stone…) would otherwise be re-pasted every
            // single second, forever. Two strikes and the plank is given up: the court stays clean.
            if (strike(index) >= MAX_REPAIR_STRIKES) {
                hopeless.add(index);
                abandonedDecorations++;
            }
        }
        return new PasteProgress(repaired, cursor, cursor >= indices.length);
    }

    /** How many times a block may be put back before the paste admits it cannot survive here. */
    static final int MAX_REPAIR_STRIKES = 2;

    private final java.util.Map<Integer, Integer> repairStrikes = new java.util.HashMap<>();
    private final java.util.Set<Integer> hopeless = new java.util.HashSet<>();
    private int abandonedDecorations;

    private int strike(int index) {
        return repairStrikes.merge(index, 1, Integer::sum);
    }

    private void strikeLess(int index) {
        if (repairStrikes.remove(index) != null) {
            // it survived on its own: forget the previous strike
        }
    }

    /** Decorations the server cannot keep, abandoned after {@link #MAX_REPAIR_STRIKES} attempts. */
    public int abandonedDecorations() {
        return abandonedDecorations;
    }

    /** Decorations currently under repair watch (for tests / status output). */
    public int watchedDecorations() {
        return repairStrikes.size();
    }

    /** Fresh paste: the repair counters start over. */
    public void resetRepairState() {
        repairStrikes.clear();
        hopeless.clear();
        abandonedDecorations = 0;
        writeFailures = 0;
    }

    public int doorCount() {
        int count = 0;
        for (int i = 0; i < blocks.length; i++) {
            if (isDoorName(palette[blocks[i]])) {
                count++;
            }
        }
        return count;
    }

    /**
     * Last pass: force every door / trapdoor / gate of the pasted build back to closed and
     * unpowered, so a court never comes back with a door ajar.
     *
     * @return how many blocks were corrected
     */
    public int closeDoors(World world, int originX, int originY, int originZ) {
        return closeDoorsSlice(world, originX, originY, originZ, Integer.MAX_VALUE, 0).written();
    }

    /** A resumable door pass; every scanned schematic index consumes the budget. */
    public PasteProgress closeDoorsSlice(World world, int originX, int originY, int originZ,
                                         int maxBlocks, int fromIndex) {
        int fixed = 0;
        int plane = width * length;
        int cursor = Math.clamp(fromIndex, 0, blocks.length);
        int limit = sliceLimit(cursor, maxBlocks, blocks.length);
        while (cursor < limit) {
            int index = cursor++;
            if (!isDoorName(palette[blocks[index]])) {
                continue;
            }
            int y = index / plane;
            int inPlane = index - y * plane;
            int z = inPlane / width;
            int x = inPlane - z * width;
            org.bukkit.block.Block block = world.getBlockAt(originX + localX(x, z), originY + y, originZ + localZ(x, z));
            String material = block.getType().name();
            if (!material.endsWith("_DOOR")) {
                continue;
            }
            BlockData data = block.getBlockData();
            boolean changed = false;
            if (data instanceof org.bukkit.block.data.Openable openable && openable.isOpen()) {
                openable.setOpen(false);
                changed = true;
            }
            if (data instanceof org.bukkit.block.data.Powerable powerable && powerable.isPowered()) {
                powerable.setPowered(false);
                changed = true;
            }
            if (changed) {
                block.setBlockData(data, false);
                fixed++;
            }
        }
        return new PasteProgress(fixed, cursor, cursor >= blocks.length);
    }

    /** Second pass: place the attachable blocks once their support blocks already exist. */
    public PasteProgress pasteAttachables(World world, int originX, int originY, int originZ,
                                          int maxBlocks, int fromIndex) {
        int[] indices = attachableIndices();
        BlockData[] data = resolvedData();
        int cursor = Math.max(0, fromIndex);
        int limit = sliceLimit(cursor, maxBlocks, indices.length);
        int plane = width * length;
        int written = 0;
        while (cursor < limit) {
            int index = indices[cursor++];
            int y = index / plane;
            int inPlane = index - y * plane;
            int z = inPlane / width;
            int x = inPlane - z * width;
            BlockData block = data[blocks[index]];
            if (block == null || isAirData(block)) {
                continue;
            }
            place(world, originX + localX(x, z), originY + y, originZ + localZ(x, z), block);
            written++;
        }
        return new PasteProgress(written, cursor, cursor >= indices.length);
    }

    /**
     * Re-attaches the schematic's block-entity payloads (sign text, colour, wax) after a paste.
     *
     * @return how many block entities were restored
     */
    public int applyBlockEntities(World world, int originX, int originY, int originZ) {
        return applyBlockEntitiesSlice(world, originX, originY, originZ, Integer.MAX_VALUE, 0).written();
    }

    /** A resumable block-entity pass, bounded by payloads examined. */
    public PasteProgress applyBlockEntitiesSlice(World world, int originX, int originY, int originZ,
                                                 int maxBlocks, int fromIndex) {
        int restored = 0;
        int cursor = Math.clamp(fromIndex, 0, blockEntities.size());
        int limit = sliceLimit(cursor, maxBlocks, blockEntities.size());
        while (cursor < limit) {
            SavedBlockEntity entity = blockEntities.get(cursor++);
            boolean signEntity = entity.isSign();
            boolean skullEntity = entity.isSkull();
            if (!signEntity && !skullEntity) {
                continue;
            }
            int wx = originX + localX(entity.x(), entity.z());
            int wy = originY + entity.y();
            int wz = originZ + localZ(entity.x(), entity.z());
            org.bukkit.block.Block block = world.getBlockAt(wx, wy, wz);
            org.bukkit.block.BlockState state = block.getState();
            if (signEntity) {
                if (!(state instanceof Sign sign)) {
                    continue;
                }
                applySignData(sign, entity.data());
                sign.update(true, false);
                restored++;
            } else if (state instanceof org.bukkit.block.Skull skull) {
                if (SchematicHeads.apply(skull, entity.data())) {
                    restored++;
                }
            }
        }
        return new PasteProgress(restored, cursor, cursor >= blockEntities.size());
    }

    /** Overflow-safe upper bound shared by the finishing passes. */
    public static int sliceLimit(int cursor, int maxBlocks, int total) {
        if (total < 0 || cursor < 0 || cursor > total || maxBlocks <= 0) {
            throw new IllegalArgumentException("invalid slice bounds");
        }
        return (int) Math.min((long) total, (long) cursor + maxBlocks);
    }

    /** Names of the custom heads of the build, so their skins can be resolved in the background. */
    public List<String> headNames() {
        return SchematicHeads.namesIn(blockEntities);
    }

    private static void applySignData(Sign sign, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        applySignSide(sign, org.bukkit.block.sign.Side.FRONT, asCompound(data.get("front_text")));
        applySignSide(sign, org.bukkit.block.sign.Side.BACK, asCompound(data.get("back_text")));
        Object waxed = data.get("is_waxed");
        if (waxed instanceof Byte b) {
            sign.setWaxed(b != 0);
        } else if (waxed instanceof Boolean bool) {
            sign.setWaxed(bool);
        }
    }

    private static void applySignSide(Sign sign, org.bukkit.block.sign.Side side,
                                      Map<String, Object> text) {
        if (text == null) {
            return;
        }
        org.bukkit.block.sign.SignSide view = sign.getSide(side);
        List<Object> messages = asList(text.get("messages"));
        if (messages != null) {
            for (int i = 0; i < 4 && i < messages.size(); i++) {
                String line = jsonPlainText(asString(messages.get(i)));
                // Never wipe a line that already carries text: an empty payload in the .schem
                // (saved after one bad paste) must not erase what the sign currently shows.
                if (!line.isEmpty()) {
                    view.line(i, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection().deserialize(line));
                }
            }
        }
        String color = asString(text.get("color"));
        if (color != null) {
            DyeColor dye = dyeOf(color);
            if (dye != null) {
                view.setColor(dye);
            }
        }
        Object glowing = text.get("has_glowing_text");
        if (glowing instanceof Byte b) {
            view.setGlowingText(b != 0);
        } else if (glowing instanceof Boolean bool) {
            view.setGlowingText(bool);
        }
    }

    private static DyeColor dyeOf(String name) {
        if (name == null) {
            return null;
        }
        for (DyeColor color : DyeColor.values()) {
            if (color.name().equalsIgnoreCase(name)) {
                return color;
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : (value == null ? "" : String.valueOf(value));
    }

    /** Minimal JSON-text unwrapping: {@code {"text":"Hello"}} becomes {@code Hello}. */
    public static String jsonPlainText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String text = raw.trim();
        if (!text.startsWith("{") && !text.startsWith("[") && !text.startsWith("\"")) {
            return raw;
        }
        try {
            Object parsed = new JsonTextReader(text).readValue();
            StringBuilder out = new StringBuilder();
            flattenJsonText(parsed, out);
            return out.length() == 0 ? "" : out.toString();
        } catch (RuntimeException ex) {
            return raw;
        }
    }

    private static void flattenJsonText(Object node, StringBuilder out) {
        if (node == null) {
            return;
        }
        if (node instanceof String s) {
            out.append(s);
            return;
        }
        if (node instanceof List<?> list) {
            for (Object child : list) {
                flattenJsonText(child, out);
            }
            return;
        }
        if (node instanceof Map<?, ?> map) {
            Object text = map.get("text");
            if (text != null) {
                out.append(text);
            }
            Object extra = map.get("extra");
            if (extra != null) {
                flattenJsonText(extra, out);
            }
        }
    }

    /** Tiny JSON reader — the plugin has no Gson dependency at schematic level. */
    private static final class JsonTextReader {
        private final String src;
        private int pos;

        JsonTextReader(String src) {
            this.src = src;
        }

        Object readValue() {
            skipSpace();
            if (pos >= src.length()) {
                throw new IllegalArgumentException("unexpected end");
            }
            char c = src.charAt(pos);
            if (c == '{') {
                return readObject();
            }
            if (c == '[') {
                return readArray();
            }
            if (c == '"') {
                return readString();
            }
            int start = pos;
            while (pos < src.length() && ",}]".indexOf(src.charAt(pos)) < 0) {
                pos++;
            }
            return src.substring(start, pos).trim();
        }

        private Map<String, Object> readObject() {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            pos++;
            while (true) {
                skipSpace();
                if (pos >= src.length()) {
                    return map;
                }
                if (src.charAt(pos) == '}') {
                    pos++;
                    return map;
                }
                if (src.charAt(pos) == ',') {
                    pos++;
                    continue;
                }
                String key = readString();
                skipSpace();
                if (pos < src.length() && src.charAt(pos) == ':') {
                    pos++;
                }
                map.put(key, readValue());
            }
        }

        private List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            pos++;
            while (true) {
                skipSpace();
                if (pos >= src.length()) {
                    return list;
                }
                if (src.charAt(pos) == ']') {
                    pos++;
                    return list;
                }
                if (src.charAt(pos) == ',') {
                    pos++;
                    continue;
                }
                list.add(readValue());
            }
        }

        private String readString() {
            StringBuilder out = new StringBuilder();
            if (pos < src.length() && src.charAt(pos) == '"') {
                pos++;
            }
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\' && pos < src.length()) {
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case 'n' -> out.append('\n');
                        case 't' -> out.append('\t');
                        case 'r' -> out.append('\r');
                        case 'u' -> {
                            if (pos + 4 <= src.length()) {
                                out.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                                pos += 4;
                            }
                        }
                        default -> out.append(esc);
                    }
                    continue;
                }
                out.append(c);
            }
            return out.toString();
        }

        private void skipSpace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }
    }

    /**
     * Only real doors are forced closed: a trapdoor left {@code open=true} on purpose (wall
     * decoration, step edge) must stay exactly as saved, and a lever/button must keep its
     * powered state. Everything else keeps the exact block state of the schematic.
     */
    private static void forceClosed(BlockData data) {
        if (!(data instanceof org.bukkit.block.data.Openable openable)) {
            return;
        }
        String name = data.getMaterial().name();
        if (!name.endsWith("_DOOR")) {
            return;
        }
        if (openable.isOpen()) {
            openable.setOpen(false);
        }
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

    private int ceilingAboveFloor = Integer.MIN_VALUE;

    /**
     * Height (in blocks) between the playing surface and the first ceiling layer of the build
     * (the roof). {@code -1} when the build is open on top.
     *
     * <p>This is what keeps a long shot inside the arena without removing the roof: the ball
     * stops on an invisible plane placed just under it.</p>
     */
    public int ceilingAboveFloor() {
        int cached = ceilingAboveFloor;
        if (cached != Integer.MIN_VALUE) {
            return cached;
        }
        ceilingAboveFloor = -1;
        int[] layers = structuralLayers();
        int deck = Math.max(0, Math.min(height - 1, floorOffsetAtCenter() - 1));
        int footprint = Math.max(1, width * length);
        int need = Math.max(48, (int) (footprint * 0.30));
        for (int y = deck + 3; y < height; y++) {
            if (layers[y] >= need) {
                ceilingAboveFloor = Math.max(4, y - floorOffsetAtCenter());
                break;
            }
        }
        return ceilingAboveFloor;
    }

    public boolean hasCeiling() {
        return ceilingAboveFloor() > 0;
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
        if (hasCourtBox()) {
            return rotateX(courtCenterRawX(), courtCenterRawZ());
        }
        computePaintedCenter();
        return preferPainted(paintedCenterX, pasteCenterX());
    }

    /**
     * The painted markings win only when they really disagree with the middle of the footprint
     * (a court with stands, a tunnel or a jumbotron on one side is not centred in its own box).
     * A small difference is just the pattern of the lines, and the box middle is more stable.
     */
    private static double preferPainted(double painted, double geometric) {
        if (painted < 0) {
            return geometric;
        }
        return Math.abs(painted - geometric) > 1.0 ? painted : geometric;
    }

    public double localCenterZ() {
        if (hasCourtBox()) {
            return rotateZ(courtCenterRawX(), courtCenterRawZ());
        }
        computePaintedCenter();
        return preferPainted(paintedCenterZ, pasteCenterZ());
    }

    private int courtMinX = -1;

    private int courtMinZ = -1;

    private int courtMaxX = -1;

    private int courtMaxZ = -1;

    private int courtPaintedBlocks;


    private boolean courtDetected;

    private String courtFamilyName;

    private double paintedCenterX = -1;

    private double paintedCenterZ = -1;

    private boolean paintedCenterComputed;

    /**
     * The real court inside the build: the coloured floor rectangle of the deck layer
     * (orange wool / concrete, wood planks, terracotta…). Stands, walls, tunnels and roofs are
     * ignored, which is exactly what a basketball court needs.
     */
    private void detectCourt() {
        if (courtDetected) {
            return;
        }
        courtDetected = true;
        int layer = Math.max(0, Math.min(height - 1, floorOffsetAtCenter() - 1));
        int base = layer * width * length;
        java.util.Map<String, int[]> boxes = new java.util.LinkedHashMap<>();
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                String family = courtFamily(palette[blocks[base + z * width + x]]);
                if (family == null) {
                    continue;
                }
                int[] box = boxes.computeIfAbsent(family,
                    k -> new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, 0});
                box[0] = Math.min(box[0], x);
                box[1] = Math.min(box[1], z);
                box[2] = Math.max(box[2], x);
                box[3] = Math.max(box[3], z);
                box[4]++;
            }
        }
        boolean paintedBoxExists = false;
        for (String family : boxes.keySet()) {
            if (!isNeutralSurface(family)) {
                paintedBoxExists = true;
                break;
            }
        }
        double bestScore = 0;
        int[] best = null;
        String bestFamily = null;
        for (java.util.Map.Entry<String, int[]> entry : boxes.entrySet()) {
            String family = entry.getKey();
            int[] box = entry.getValue();
            int spanX = box[2] - box[0] + 1;
            int spanZ = box[3] - box[1] + 1;
            if (spanX < 8 || spanZ < 8) {
                continue;
            }
            double aspect = Math.max(spanX, spanZ) / (double) Math.min(spanX, spanZ);
            if (aspect < 1.15 || aspect > 2.6) {
                continue;
            }
            int area = spanX * spanZ;
            if (box[4] < Math.max(64, (int) (area * 0.30))) {
                continue;
            }
            if (isNeutralSurface(family) && paintedBoxExists) {
                // A plain sand deck is only a court when nothing was painted: a 25x31 cyan court
                // on a 75x101 sand street must stay the 25x31 court.
                continue;
            }
            double score = area;
            if (score > bestScore) {
                bestScore = score;
                best = box;
                bestFamily = family;
            }
        }
        if (best == null) {
            return;
        }
        courtFamilyName = bestFamily;
        courtMinX = best[0];
        courtMinZ = best[1];
        courtMaxX = best[2];
        courtMaxZ = best[3];
        int area = courtSpanX() * courtSpanZ();
        courtPaintedBlocks = Math.max(0, area - best[4]);
    }

    /** Prefix for a surface that can be a court but is never preferred over a painted one. */
    static final String NEUTRAL_SURFACE = "beach:";

    static boolean isNeutralSurface(String family) {
        return family != null && family.startsWith(NEUTRAL_SURFACE);
    }

    /**
     * Colour/material family of a block, or {@code null} when it cannot be a court floor.
     *
     * <p>Sand used to be no family at all, so a beach court — a sand deck with white paint on it —
     * had no detectable court rectangle: the geometry fell back to the middle of the schematic
     * footprint and the painted lines landed off the deck. Sand, gravel and dirt paths are now
     * families, tagged as neutral: they are only used when nothing painted was found, because a
     * sand deck under a blue concrete court must never win.
     */
    static String courtFamily(String rawName) {
        if (rawName == null) {
            return null;
        }
        String n = rawName.startsWith("minecraft:") ? rawName.substring("minecraft:".length()) : rawName;
        int bracket = n.indexOf('[');
        if (bracket > 0) {
            n = n.substring(0, bracket);
        }
        if (n.equals("air") || n.equals("cave_air") || n.equals("void_air") || n.equals("light")) {
            return null;
        }
        String[] suffixes = {"_concrete_powder", "_concrete", "_wool", "_terracotta", "_carpet", "_planks"};
        for (String suffix : suffixes) {
            if (n.endsWith(suffix) && n.length() > suffix.length()) {
                return n.substring(0, n.length() - suffix.length());
            }
        }
        if (n.equals("quartz_block") || n.equals("smooth_quartz") || n.equals("snow_block")) {
            return "quartz";
        }
        if (n.equals("sand") || n.equals("red_sand") || n.equals("suspicious_sand")
            || n.equals("gravel") || n.equals("dirt_path") || n.equals("coarse_dirt")
            || n.equals("grass_block") || n.equals("moss_block")
            || n.endsWith("_sandstone")) {
            return NEUTRAL_SURFACE + n;
        }
        return null;
    }

    public boolean hasCourtBox() {
        detectCourt();
        return courtMinX >= 0;
    }

    /**
     * True when the court rectangle the geometry is fitted to is a painted one (concrete, wool,
     * terracotta, planks, quartz). A plain sand deck is not: those courts rely on the paint drawn
     * by the plugin, which is why they get the blue floor.
     */
    public boolean hasPaintedCourtBox() {
        detectCourt();
        return courtMinX >= 0 && !isNeutralSurface(courtFamilyName);
    }

    public int courtSpanX() {
        return courtMaxX - courtMinX + 1;
    }

    public int courtSpanZ() {
        return courtMaxZ - courtMinZ + 1;
    }

    /** Basket-to-basket length of the coloured court, in blocks. */
    public int courtLength() {
        return Math.max(courtSpanX(), courtSpanZ());
    }

    /** Sideline-to-sideline width of the coloured court, in blocks. */
    public int courtWidth() {
        return Math.min(courtSpanX(), courtSpanZ());
    }

    /** True when the baskets sit on the east-west axis of the file. */
    public boolean courtAlongX() {
        return courtSpanX() >= courtSpanZ();
    }

    public double courtCenterRawX() {
        return (courtMinX + courtMaxX + 1) / 2.0;
    }

    public double courtCenterRawZ() {
        return (courtMinZ + courtMaxZ + 1) / 2.0;
    }

    /** Blocks of the court box that are not the court floor itself: painted lines, keys, logos. */
    public int courtPaintedBlocks() {
        detectCourt();
        return courtPaintedBlocks;
    }

    private double rotateX(double x, double z) {
        return switch (rotationDegrees) {
            case 90 -> length - z;
            case 180 -> width - x;
            case 270 -> z;
            default -> x;
        };
    }

    private double rotateZ(double x, double z) {
        return switch (rotationDegrees) {
            case 90 -> x;
            case 180 -> length - z;
            case 270 -> width - x;
            default -> z;
        };
    }

    /**
     * Centre of the painted court (white markings on the deck), in paste space, relative to the
     * paste origin. Falls back to the middle of the footprint when the build has no painted lines.
     *
     * <p>This is what aligns the rims / the mid-court circle with the build instead of with the
     * bounding box of the whole arena (stands, tunnel, jumbotron… all make the box asymmetric).</p>
     */
    private void computePaintedCenter() {
        if (paintedCenterComputed) {
            return;
        }
        paintedCenterComputed = true;
        // Markings can sit on the deck or one/two blocks above it (painted slabs, carpets, ...).
        int floorLayer = Math.max(0, Math.min(height - 1, floorOffsetAtCenter() - 1));
        int topLayer = Math.min(height - 1, floorLayer + 2);
        double sumX = 0;
        double sumZ = 0;
        int count = 0;
        for (int layer = floorLayer; layer <= topLayer; layer++) {
            int base = layer * width * length;
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    if (!isLineMarking(palette[blocks[base + z * width + x]])) {
                        continue;
                    }
                    sumX += x + 0.5;
                    sumZ += z + 0.5;
                    count++;
                }
            }
        }
        if (count < MIN_LINE_MARKINGS) {
            return;
        }
        double cx = sumX / count;
        double cz = sumZ / count;
        paintedCenterX = switch (rotationDegrees) {
            case 90 -> length - cz;
            case 180 -> width - cx;
            case 270 -> cz;
            default -> cx;
        };
        paintedCenterZ = switch (rotationDegrees) {
            case 90 -> cx;
            case 180 -> length - cz;
            case 270 -> width - cx;
            default -> cz;
        };
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

    private static String stringOf(Object value) {
        return value instanceof String s ? s : null;
    }

    private static int intOf(Map<String, Object> compound, String key, int fallback) {
        Object value = compound.get(key);
        return value instanceof Number n ? n.intValue() : fallback;
    }
}
