package fr.bosketball.arena.schematic;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class NbtReader {

    static final int TAG_END = 0;
    static final int TAG_BYTE = 1;
    static final int TAG_SHORT = 2;
    static final int TAG_INT = 3;
    static final int TAG_LONG = 4;
    static final int TAG_FLOAT = 5;
    static final int TAG_DOUBLE = 6;
    static final int TAG_BYTE_ARRAY = 7;
    static final int TAG_STRING = 8;
    static final int TAG_LIST = 9;
    static final int TAG_COMPOUND = 10;
    static final int TAG_INT_ARRAY = 11;
    static final int TAG_LONG_ARRAY = 12;

    private final DataInputStream in;

    private NbtReader(InputStream stream) {
        this.in = new DataInputStream(stream);
    }

    static Map<String, Object> readRoot(InputStream stream) throws IOException {
        return new NbtReader(stream).readDocument();
    }

    private Map<String, Object> readDocument() throws IOException {
        int type = in.readUnsignedByte();
        if (type == TAG_END) {
            return new LinkedHashMap<>();
        }
        readName();
        if (type != TAG_COMPOUND) {
            throw new IOException("Root NBT tag is not a compound (type " + type + ")");
        }
        return readCompoundPayload();
    }

    private String readName() throws IOException {
        int len = in.readUnsignedShort();
        if (len == 0) {
            return "";
        }
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private Map<String, Object> readCompoundPayload() throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        while (true) {
            int type = in.readUnsignedByte();
            if (type == TAG_END) {
                return map;
            }
            String name = readName();
            Object value = readPayload(type);
            if (value != null) {
                map.put(name, value);
            }
        }
    }

    private Object readPayload(int type) throws IOException {
        switch (type) {
            case TAG_BYTE:
                return in.readByte();
            case TAG_SHORT:
                return in.readShort();
            case TAG_INT:
                return in.readInt();
            case TAG_LONG:
                return in.readLong();
            case TAG_FLOAT:
                return in.readFloat();
            case TAG_DOUBLE:
                return in.readDouble();
            case TAG_BYTE_ARRAY: {
                int len = readArrayLength();
                byte[] bytes = new byte[len];
                in.readFully(bytes);
                return bytes;
            }
            case TAG_STRING:
                return readName();
            case TAG_LIST: {
                int elementType = in.readUnsignedByte();
                int len = readArrayLength();
                List<Object> list = new ArrayList<>(Math.min(len, 4096));
                for (int i = 0; i < len; i++) {
                    list.add(elementType == TAG_END ? null : readPayload(elementType));
                }
                return list;
            }
            case TAG_COMPOUND:
                return readCompoundPayload();
            case TAG_INT_ARRAY: {
                int len = readArrayLength();
                int[] values = new int[len];
                for (int i = 0; i < len; i++) {
                    values[i] = in.readInt();
                }
                return values;
            }
            case TAG_LONG_ARRAY: {
                int len = readArrayLength();
                long[] values = new long[len];
                for (int i = 0; i < len; i++) {
                    values[i] = in.readLong();
                }
                return values;
            }
            default:
                throw new IOException("Unknown NBT tag type " + type);
        }
    }

    private int readArrayLength() throws IOException {
        int len = in.readInt();
        if (len < 0) {
            return 0;
        }

        if (len > 64 * 1024 * 1024) {
            throw new IOException("NBT collection length " + len + " is implausible");
        }
        return len;
    }
}
