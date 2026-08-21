package dev.aerogel.loader.context;

import jdk.internal.vm.annotation.Contended;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

@Contended
final class PaddedLongAdder {
    private static final VarHandle VALUE;

    static {
        try {
            VALUE = MethodHandles.lookup().findVarHandle(
                PaddedLongAdder.class, "value", long.class);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private volatile long value;

    void increment() { VALUE.getAndAdd(this, 1L); }
    void add(long delta) { VALUE.getAndAdd(this, delta); }
    long sum() { return (long) VALUE.getVolatile(this); }
}
