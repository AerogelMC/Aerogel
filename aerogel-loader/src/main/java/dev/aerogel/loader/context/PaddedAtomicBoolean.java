package dev.aerogel.loader.context;

import jdk.internal.vm.annotation.Contended;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

@Contended
final class PaddedAtomicBoolean {
    private static final VarHandle VALUE;

    static {
        try {
            VALUE = MethodHandles.lookup().findVarHandle(
                PaddedAtomicBoolean.class, "value", int.class);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private volatile int value;

    PaddedAtomicBoolean() { }
    PaddedAtomicBoolean(boolean initial) { value = initial ? 1 : 0; }

    boolean get() { return (int) VALUE.getVolatile(this) != 0; }
    void set(boolean next) { VALUE.setVolatile(this, next ? 1 : 0); }
    boolean compareAndSet(boolean expected, boolean next) {
        return VALUE.compareAndSet(this, expected ? 1 : 0, next ? 1 : 0);
    }
}
