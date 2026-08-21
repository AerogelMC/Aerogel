package dev.aerogel.loader.context;

import jdk.internal.vm.annotation.Contended;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/** A cache-line isolated long whose value is declared in the contended class itself. */
@Contended
public final class PaddedAtomicLong {
    private static final VarHandle VALUE;

    static {
        try {
            VALUE = MethodHandles.lookup().findVarHandle(
                PaddedAtomicLong.class, "value", long.class);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private volatile long value;

    public long get() { return (long) VALUE.getVolatile(this); }
    public long incrementAndGet() { return (long) VALUE.getAndAdd(this, 1L) + 1L; }
}
