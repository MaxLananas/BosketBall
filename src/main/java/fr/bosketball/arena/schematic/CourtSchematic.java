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
        List<SavedBlockEntity> saved = new ArrayList<>();
        if (tileEntities != null) {
            for (Object raw : tileEntities) {
                Map<String, Object> compound = asCompound(raw);
                if (compound == null) {
                    continue;
                }
                int[] pos = asIntArray(compound.get("Pos"));
                if (pos == null || pos.length < 3) {
                    continue;
                }
                String id = stringOf(compound.get("Id"));
                Map<String, Object> dataCompound = asCompound(compound.get("Data"));
                saved.add(new SavedBlockEntity(pos[0], pos[1], pos[2],
                    id == null ? "" : id, dataCompound == null ? Map.of() : dataCompound));
            }
        }

        String fileName = file.getName();
        String base = fileName.contains(".")
            ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        return new CourtSchematic(base, w, h, l, offsetX, offsetY, offsetZ, indices, palette,
            nonAir, intOf(data, "DataVersion", -1), version, blockEntities, saved);
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
                world.setBlockData(originX + localX(x, z), originY + y, originZ + localZ(x, z), block);
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
                world.setBlockData(wx, wy, wz, block);
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
            String spec = palette[i].contains(":") ? palette[i] : "minecraft:" + palette[i];
            try {
                built[i] = Bukkit.createBlockData(spec);
                forceClosed(built[i]);
            } catch (IllegalArgumentException ex) {

                Bukkit.getLogger().warning("[BosketBall] Schematic block '" + spec
                    + "' is unknown on this server version — using STONE instead.");
                built[i] = Material.STONE.createBlockData();
            }
        }
        resolved = built;
        return built;
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
            || n.endsWith("_roots") || n.endsWith("_sprouts");
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
        int repaired = 0;
        int plane = width * length;
        BlockData[] data = resolvedData();
        for (int index : attachableIndices()) {
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
            if (block.getType() == expected.getMaterial()) {
                continue;
            }
            block.setBlockData(expected, false);
            repaired++;
        }
        return repaired;
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
        int fixed = 0;
        int plane = width * length;
        for (int index = 0; index < blocks.length; index++) {
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
        return fixed;
    }

    /** Second pass: place the attachable blocks once their support blocks already exist. */
    public PasteProgress pasteAttachables(World world, int originX, int originY, int originZ,
                                          int maxBlocks, int fromIndex) {
        int[] indices = attachableIndices();
        BlockData[] data = resolvedData();
        int cursor = Math.max(0, fromIndex);
        int limit = Math.min(indices.length, cursor + Math.max(1, maxBlocks));
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
            world.setBlockData(originX + localX(x, z), originY + y, originZ + localZ(x, z), block);
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
        int restored = 0;
        for (SavedBlockEntity entity : blockEntities) {
            String id = entity.id();
            if (!(id.endsWith("sign") || id.endsWith("hanging_sign"))) {
                continue;
            }
            int wx = originX + localX(entity.x(), entity.z());
            int wy = originY + entity.y();
            int wz = originZ + localZ(entity.x(), entity.z());
            org.bukkit.block.Block block = world.getBlockAt(wx, wy, wz);
            org.bukkit.block.BlockState state = block.getState();
            if (!(state instanceof Sign sign)) {
                continue;
            }
            applySignData(sign, entity.data());
            sign.update(true, false);
            restored++;
        }
        return restored;
    }

    @SuppressWarnings("unchecked")
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
                    view.setLine(i, line);
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

    private int courtLayer = -1;

    private boolean courtDetected;

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
        courtLayer = layer;
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
        double bestScore = 0;
        int[] best = null;
        for (int[] box : boxes.values()) {
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
            double score = area;
            if (score > bestScore) {
                bestScore = score;
                best = box;
            }
        }
        if (best == null) {
            return;
        }
        courtMinX = best[0];
        courtMinZ = best[1];
        courtMaxX = best[2];
        courtMaxZ = best[3];
        int area = courtSpanX() * courtSpanZ();
        courtPaintedBlocks = Math.max(0, area - best[4]);
    }

    /** Colour/material family of a block, or {@code null} when it cannot be a court floor. */
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
        return null;
    }

    public boolean hasCourtBox() {
        detectCourt();
        return courtMinX >= 0;
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

    public boolean hasPaintedCourt() {
        return hasCourtBox() && courtPaintedBlocks() >= 16;
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

    public double paintedCenterRawX() {
        computePaintedCenter();
        return paintedCenterX;
    }

    public double paintedCenterRawZ() {
        computePaintedCenter();
        return paintedCenterZ;
    }

    public boolean hasPaintedCenter() {
        computePaintedCenter();
        return paintedCenterX >= 0;
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
