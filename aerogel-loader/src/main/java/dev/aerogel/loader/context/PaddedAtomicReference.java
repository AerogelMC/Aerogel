package dev.aerogel.loader.context;

import jdk.internal.vm.annotation.Contended;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

@Contended
public final class PaddedAtomicReference<V> {
    private static final VarHandle VALUE;

    static {
        try {
            VALUE = MethodHandles.lookup().findVarHandle(
                PaddedAtomicReference.class, "value", Object.class);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private volatile Object value;

    public PaddedAtomicReference() { }
    public PaddedAtomicReference(V initial) { value = initial; }

    @SuppressWarnings("unchecked")
    public V get() { return (V) VALUE.getVolatile(this); }
    public void set(V next) { VALUE.setVolatile(this, next); }
    public boolean compareAndSet(V expected, V next) {
        return VALUE.compareAndSet(this, expected, next);
    }
    @SuppressWarnings("unchecked")
    public V getAndSet(V next) { return (V) VALUE.getAndSet(this, next); }
}
