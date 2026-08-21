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

    public PaddedAtomicLong() { }
    public PaddedAtomicLong(long initialValue) { value = initialValue; }

    public long get() { return (long) VALUE.getVolatile(this); }
    public void set(long updated) { VALUE.setVolatile(this, updated); }
    public long getAndSet(long updated) { return (long) VALUE.getAndSet(this, updated); }
    public long addAndGet(long delta) { return (long) VALUE.getAndAdd(this, delta) + delta; }
    public long incrementAndGet() { return (long) VALUE.getAndAdd(this, 1L) + 1L; }
}
