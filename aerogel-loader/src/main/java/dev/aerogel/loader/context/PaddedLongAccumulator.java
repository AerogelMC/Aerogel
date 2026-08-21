package dev.aerogel.loader.context;

import jdk.internal.vm.annotation.Contended;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.LongBinaryOperator;

@Contended
final class PaddedLongAccumulator {
    private static final VarHandle VALUE;

    static {
        try {
            VALUE = MethodHandles.lookup().findVarHandle(
                PaddedLongAccumulator.class, "value", long.class);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private final LongBinaryOperator operation;
    private volatile long value;

    PaddedLongAccumulator(LongBinaryOperator operation, long identity) {
        this.operation = operation;
        this.value = identity;
    }

    void accumulate(long input) {
        long observed;
        long next;
        do {
            observed = (long) VALUE.getVolatile(this);
            next = operation.applyAsLong(observed, input);
            if (next == observed) return;
        } while (!VALUE.compareAndSet(this, observed, next));
    }

    long get() { return (long) VALUE.getVolatile(this); }
}
