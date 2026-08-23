package dev.aerogel.loader.internal;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * A lock-free, insertion-ordered list whose readers always traverse one immutable generation.
 * Player-list mutations are rare, while broadcasts and per-tick iterations are frequent, making
 * an immutable generation preferable to synchronising every reader or copying during iteration.
 */
public final class ConcurrentSnapshotList<E> extends AbstractList<E> implements RandomAccess {
    private final AtomicReference<List<E>> generation;

    public ConcurrentSnapshotList(Collection<? extends E> initial) {
        generation = new AtomicReference<>(List.copyOf(initial));
    }

    @Override
    public E get(int index) {
        return generation.get().get(index);
    }

    @Override
    public int size() {
        return generation.get().size();
    }

    @Override
    public boolean contains(Object value) {
        return generation.get().contains(value);
    }

    @Override
    public int indexOf(Object value) {
        return generation.get().indexOf(value);
    }

    @Override
    public int lastIndexOf(Object value) {
        return generation.get().lastIndexOf(value);
    }

    @Override
    public boolean add(E value) {
        Objects.requireNonNull(value, "value");
        mutate(list -> list.add(value));
        return true;
    }

    @Override
    public void add(int index, E value) {
        Objects.requireNonNull(value, "value");
        mutate(list -> list.add(index, value));
    }

    @Override
    public boolean addAll(Collection<? extends E> values) {
        if (values.isEmpty()) return false;
        ArrayList<E> copy = new ArrayList<>(values.size());
        for (E value : values) copy.add(Objects.requireNonNull(value, "value"));
        mutate(list -> list.addAll(copy));
        return true;
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> values) {
        if (values.isEmpty()) return false;
        ArrayList<E> copy = new ArrayList<>(values.size());
        for (E value : values) copy.add(Objects.requireNonNull(value, "value"));
        mutate(list -> list.addAll(index, copy));
        return true;
    }

    @Override
    public E set(int index, E value) {
        Objects.requireNonNull(value, "value");
        for (;;) {
            List<E> current = generation.get();
            ArrayList<E> next = new ArrayList<>(current);
            E previous = next.set(index, value);
            if (generation.compareAndSet(current, List.copyOf(next))) return previous;
        }
    }

    @Override
    public E remove(int index) {
        for (;;) {
            List<E> current = generation.get();
            ArrayList<E> next = new ArrayList<>(current);
            E removed = next.remove(index);
            if (generation.compareAndSet(current, List.copyOf(next))) return removed;
        }
    }

    @Override
    public boolean remove(Object value) {
        for (;;) {
            List<E> current = generation.get();
            int index = current.indexOf(value);
            if (index < 0) return false;
            ArrayList<E> next = new ArrayList<>(current);
            next.remove(index);
            if (generation.compareAndSet(current, List.copyOf(next))) return true;
        }
    }

    @Override
    public boolean removeAll(Collection<?> values) {
        return mutateIfChanged(list -> list.removeAll(values));
    }

    @Override
    public boolean retainAll(Collection<?> values) {
        return mutateIfChanged(list -> list.retainAll(values));
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        Objects.requireNonNull(filter, "filter");
        return mutateIfChanged(list -> list.removeIf(filter));
    }

    @Override
    public void clear() {
        generation.set(List.of());
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        Objects.requireNonNull(operator, "operator");
        mutate(list -> list.replaceAll(value -> Objects.requireNonNull(operator.apply(value), "value")));
    }

    @Override
    public void sort(Comparator<? super E> comparator) {
        mutate(list -> list.sort(comparator));
    }

    @Override
    public Object[] toArray() {
        return generation.get().toArray();
    }

    @Override
    public <T> T[] toArray(T[] target) {
        return generation.get().toArray(target);
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        generation.get().forEach(action);
    }

    @Override
    public Spliterator<E> spliterator() {
        return generation.get().spliterator();
    }

    @Override
    public Iterator<E> iterator() {
        return generation.get().iterator();
    }

    @Override
    public ListIterator<E> listIterator() {
        return generation.get().listIterator();
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return generation.get().listIterator(index);
    }

    private void mutate(Consumer<ArrayList<E>> mutation) {
        for (;;) {
            List<E> current = generation.get();
            ArrayList<E> next = new ArrayList<>(current);
            mutation.accept(next);
            if (generation.compareAndSet(current, List.copyOf(next))) return;
        }
    }

    private boolean mutateIfChanged(Predicate<ArrayList<E>> mutation) {
        for (;;) {
            List<E> current = generation.get();
            ArrayList<E> next = new ArrayList<>(current);
            if (!mutation.test(next)) return false;
            if (generation.compareAndSet(current, List.copyOf(next))) return true;
        }
    }
}
