package net.minecraft.util;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class ClassInstanceMultiMap<T> extends AbstractCollection<T> {
    public ClassInstanceMultiMap(Class<T> baseClass) { }
    @Override public boolean add(T value) { return false; }
    @Override public boolean remove(Object value) { return false; }
    @Override public boolean contains(Object value) { return false; }
    public <S> Collection<S> find(Class<S> type) { return null; }
    @Override public Iterator<T> iterator() { return null; }
    public List<T> getAllInstances() { return null; }
    @Override public int size() { return 0; }
}
