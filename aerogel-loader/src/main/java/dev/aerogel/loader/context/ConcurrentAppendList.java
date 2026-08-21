package dev.aerogel.loader.context;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Lock-free append-only List facade. Its weakly-consistent iterator exposes every
 * element that was visible while the iterator advanced, without copying the list.
 */
public final class ConcurrentAppendList<E> extends AbstractList<E> {
    private final ConcurrentLinkedQueue<E> elements = new ConcurrentLinkedQueue<>();

    @Override
    public boolean add(E element) {
        return elements.add(Objects.requireNonNull(element, "element"));
    }

    @Override
    public Iterator<E> iterator() {
        return elements.iterator();
    }

    @Override
    public E get(int index) {
        if (index < 0) throw new IndexOutOfBoundsException(index);
        int current = 0;
        for (E element : elements) {
            if (current++ == index) return element;
        }
        throw new IndexOutOfBoundsException(index);
    }

    @Override
    public int size() {
        return elements.size();
    }
}
