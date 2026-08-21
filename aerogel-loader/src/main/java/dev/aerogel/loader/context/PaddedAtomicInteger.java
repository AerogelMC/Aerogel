package dev.aerogel.loader.context;

import jdk.internal.vm.annotation.Contended;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

@Contended
final class PaddedAtomicInteger {
    private static final VarHandle VALUE;

    static {
        try {
            VALUE = MethodHandles.lookup().findVarHandle(
                PaddedAtomicInteger.class, "value", int.class);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private volatile int value;

    PaddedAtomicInteger() { }
    PaddedAtomicInteger(int initial) { value = initial; }

    int get() { return (int) VALUE.getVolatile(this); }
    void set(int next) { VALUE.setVolatile(this, next); }
    int incrementAndGet() { return (int) VALUE.getAndAdd(this, 1) + 1; }
    int decrementAndGet() { return (int) VALUE.getAndAdd(this, -1) - 1; }
    boolean compareAndSet(int expected, int next) {
        return VALUE.compareAndSet(this, expected, next);
    }
}
