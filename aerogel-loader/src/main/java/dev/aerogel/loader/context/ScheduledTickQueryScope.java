package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ticks.ScheduledTick;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Preserves LevelTicks' per-dispatch {@code willTickThisTick} view when the
 * actual scheduled action is transferred to a Context owner.
 *
 * <p>Vanilla removes the current tick from its queue before invoking it, so a
 * query observes only entries that follow the current entry.  Aerogel cannot
 * leave Context workers reading that mutable world queue.  A tick pass instead
 * publishes one immutable order index and each routed action carries its exact
 * position in that order.</p>
 */
public final class ScheduledTickQueryScope {
    private static final ContextWorkerLocal<Binding> CURRENT = ContextWorkerLocal.create();

    private ScheduledTickQueryScope() { }

    public static Snapshot snapshot(Collection<? extends ScheduledTick<?>> ticks) {
        return new Snapshot(ticks);
    }

    static Snapshot snapshotInOrderForTest(Object type, BlockPos... positions) {
        return new Snapshot(type, positions);
    }

    public static void run(
        Object levelTicks, Snapshot snapshot, int dispatchOrder, Runnable action
    ) {
        Objects.requireNonNull(levelTicks, "levelTicks");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(action, "action");
        Binding previous = CURRENT.get();
        CURRENT.set(new Binding(levelTicks, snapshot, dispatchOrder));
        try {
            action.run();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    /** Carries the current query view across an asynchronous ownership handoff. */
    public static Runnable propagate(Runnable action) {
        Objects.requireNonNull(action, "action");
        Binding captured = CURRENT.get();
        if (captured == null) return action;
        return () -> {
            Binding previous = CURRENT.get();
            CURRENT.set(captured);
            try {
                action.run();
            } finally {
                if (previous == null) CURRENT.remove();
                else CURRENT.set(previous);
            }
        };
    }

    /** Marks the scheduled tick whose Context action has actually begun. */
    public static void beginCurrent() {
        Binding binding = CURRENT.get();
        if (binding != null) binding.snapshot.begin(binding.dispatchOrder);
    }

    /**
     * Returns null when vanilla owns the query. Native work without a direct
     * scheduled-tick binding reads the lock-free published view for this tick.
     */
    public static Boolean willTick(
        Object levelTicks, Snapshot published, BlockPos position, Object type
    ) {
        Binding binding = CURRENT.get();
        boolean pending;
        if (binding != null && binding.levelTicks == levelTicks) {
            // Vanilla removes entries in dispatch order. Parallel wall-clock
            // start order must not make a logically later scheduled tick appear
            // to have already run from this action's point of view.
            pending = binding.snapshot.orderOf(type, position) > binding.dispatchOrder;
        } else {
            if (published == null) return null;
            pending = published.isPending(type, position);
        }
        return pending;
    }

    public static final class Snapshot {
        private final Object2IntOpenCustomHashMap<ScheduledTick<?>> order;
        private final AtomicLongArray begun;
        private final Object testType;
        private final BlockPos[] testPositions;

        private Snapshot(Collection<? extends ScheduledTick<?>> ticks) {
            order = new Object2IntOpenCustomHashMap<>(ScheduledTick.UNIQUE_TICK_HASH);
            order.defaultReturnValue(-1);
            int index = 0;
            for (ScheduledTick<?> tick : ticks) order.put(tick, index++);
            begun = new AtomicLongArray((index + Long.SIZE - 1) / Long.SIZE);
            testType = null;
            testPositions = null;
        }

        private Snapshot(Object type, BlockPos[] positions) {
            order = null;
            begun = new AtomicLongArray((positions.length + Long.SIZE - 1) / Long.SIZE);
            testType = type;
            testPositions = positions.clone();
        }

        private void begin(int dispatchOrder) {
            if (dispatchOrder < 0) return;
            int word = dispatchOrder >>> 6;
            long bit = 1L << (dispatchOrder & 63);
            begun.getAndAccumulate(word, bit, (current, update) -> current | update);
        }

        private boolean isPending(Object type, BlockPos position) {
            int dispatchOrder = orderOf(type, position);
            if (dispatchOrder < 0) return false;
            long word = begun.get(dispatchOrder >>> 6);
            return (word & (1L << (dispatchOrder & 63))) == 0L;
        }

        private int orderOf(Object type, BlockPos position) {
            if (testPositions == null) {
                return order.getInt(ScheduledTick.probe(type, position));
            }
            if (type != testType) return -1;
            for (int index = 0; index < testPositions.length; index++) {
                BlockPos candidate = testPositions[index];
                if (candidate.getX() == position.getX()
                    && candidate.getY() == position.getY()
                    && candidate.getZ() == position.getZ()) {
                    return index;
                }
            }
            return -1;
        }

        int orderOfForTest(Object type, BlockPos position) {
            return orderOf(type, position);
        }

    }

    private record Binding(Object levelTicks, Snapshot snapshot, int dispatchOrder) { }
}
