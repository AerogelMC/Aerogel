package dev.aerogel.loader.context;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * List-compatible MPSC generation queue used by vanilla ChunkMap.
 *
 * <p>ChunkMap calls {@code forEach(...)} followed by {@code clear()}. A single
 * atomic head exchange is the exact generation boundary. Producers publish to
 * the new head immediately while the sole consumer reverses its detached LIFO
 * chain back to FIFO order. Both publication and detachment are O(1), even when
 * world generation has accumulated a very large generation.</p>
 */
public final class ConcurrentGenerationTaskList<E> extends AbstractList<E> {
    private final AtomicReference<Node<E>> head = new AtomicReference<>();

    @Override
    public boolean add(E element) {
        Objects.requireNonNull(element, "element");
        while (true) {
            Node<E> observed = head.get();
            Node<E> created = new Node<>(element, observed);
            if (head.compareAndSet(observed, created)) break;
        }
        return true;
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action, "action");
        Node<E> generation = reverse(head.getAndSet(null));
        while (generation != null) {
            action.accept(generation.value);
            generation = generation.next;
        }
    }

    @Override
    public void clear() {
        // runGenerationTasks already removed precisely its marked generation.
    }

    @Override
    public int size() {
        int size = 0;
        for (Node<E> node = head.get(); node != null; node = node.next) size++;
        return size;
    }

    @Override
    public Iterator<E> iterator() {
        ArrayList<E> snapshot = new ArrayList<>();
        for (Node<E> node = head.get(); node != null; node = node.next) {
            snapshot.add(node.value);
        }
        Collections.reverse(snapshot);
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

    private static <E> Node<E> reverse(Node<E> node) {
        Node<E> previous = null;
        while (node != null) {
            Node<E> next = node.next;
            node.next = previous;
            previous = node;
            node = next;
        }
        return previous;
    }

    private static final class Node<E> {
        private final E value;
        private Node<E> next;

        private Node(E value, Node<E> next) {
            this.value = value;
            this.next = next;
        }
    }
}
