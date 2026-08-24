package dev.aerogel.loader.context;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * List-compatible MPSC generation queue used by vanilla ChunkMap.
 *
 * <p>ChunkMap calls {@code forEach(...)} followed by {@code clear()}. The marker
 * inserted by {@code forEach} is the exact linearization boundary: additions
 * before it run now, additions after it remain for the next pass. Consequently
 * {@code clear()} is intentionally a no-op; the generation was already removed
 * while being consumed.</p>
 */
public final class ConcurrentGenerationTaskList<E> extends AbstractList<E> {
    private static final Object GENERATION_END = new Object();
    private final ConcurrentLinkedQueue<Object> queue = new ConcurrentLinkedQueue<>();

    @Override
    public boolean add(E element) {
        queue.add(Objects.requireNonNull(element, "element"));
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action, "action");
        queue.add(GENERATION_END);
        Object entry;
        while ((entry = queue.poll()) != null && entry != GENERATION_END) {
            action.accept((E) entry);
        }
    }

    @Override
    public void clear() {
        // runGenerationTasks already removed precisely its marked generation.
    }

    @Override
    public int size() {
        int size = queue.size();
        for (Object entry : queue) if (entry == GENERATION_END) size--;
        return size;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<E> iterator() {
        ArrayList<E> snapshot = new ArrayList<>();
        for (Object entry : queue) {
            if (entry != GENERATION_END) snapshot.add((E) entry);
        }
        return snapshot.iterator();
    }

    @Override
    public E get(int index) {
        if (index < 0) throw new IndexOutOfBoundsException(index);
        int current = 0;
        for (E entry : this) {
            if (current++ == index) return entry;
        }
        throw new IndexOutOfBoundsException(index);
    }
}
