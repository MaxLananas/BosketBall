package fr.bosketball.ui.kit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/** Breadcrumb history. Refreshing a screen never pushes it; revisiting a screen removes its loop. */
public final class NavigationStack<T> {
    public static final int LIMIT = 16;
    private final Deque<T> entries = new ArrayDeque<>();

    public void push(T entry) {
        Objects.requireNonNull(entry, "entry");
        if (!entries.isEmpty() && entries.peekLast().equals(entry)) {
            return;
        }
        entries.removeIf(entry::equals);
        entries.addLast(entry);
        while (entries.size() > LIMIT) {
            entries.removeFirst();
        }
    }

    public T pop() {
        return entries.pollLast();
    }

    public T peek() {
        return entries.peekLast();
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public List<T> snapshot() {
        return List.copyOf(new ArrayList<>(entries));
    }

    public void clear() {
        entries.clear();
    }
}
