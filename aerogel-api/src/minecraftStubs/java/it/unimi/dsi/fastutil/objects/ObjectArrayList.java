package it.unimi.dsi.fastutil.objects;

import java.util.ArrayList;
import java.util.Collection;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class ObjectArrayList<V> extends ArrayList<V> implements ObjectCollection<V> {
    private static final long serialVersionUID = 1L;
    public ObjectArrayList(Collection<? extends V> values) { super(values); }

    @Override
    public ObjectIterator<V> iterator() {
        java.util.Iterator<V> delegate = super.iterator();
        return new ObjectIterator<>() {
            @Override public boolean hasNext() { return delegate.hasNext(); }
            @Override public V next() { return delegate.next(); }
            @Override public void remove() { delegate.remove(); }
        };
    }
}
