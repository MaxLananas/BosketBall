package it.unimi.dsi.fastutil.ints;
import java.util.HashMap;
/** Compile-only stub: real fastutil is provided by the Paper server at runtime. */
public class Int2ObjectOpenHashMap<V> extends HashMap<Integer, V> {
    public Int2ObjectOpenHashMap() { super(); }
    public Int2ObjectOpenHashMap(int expectedSize) { super(Math.max(16, expectedSize)); }
    public Int2ObjectOpenHashMap(int expectedSize, float loadFactor) { super(Math.max(16, expectedSize), loadFactor); }
    public V get(int key) { return super.get(key); }
    public V put(int key, V value) { return super.put(key, value); }
    public boolean containsKey(int key) { return super.containsKey(key); }
    public V remove(int key) { return super.remove(key); }
}
