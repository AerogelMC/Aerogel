package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ticks.ScheduledTick;

import java.util.Collection;
import java.util.Objects;

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

    /** Returns null when vanilla owns the query, otherwise the exact routed view. */
    public static Boolean willTick(Object levelTicks, BlockPos position, Object type) {
        Binding binding = CURRENT.get();
        if (binding == null || binding.levelTicks != levelTicks) return null;
        return binding.snapshot.orderOf(type, position) > binding.dispatchOrder;
    }

    public static final class Snapshot {
        private final Object2IntOpenCustomHashMap<ScheduledTick<?>> order;

        private Snapshot(Collection<? extends ScheduledTick<?>> ticks) {
            order = new Object2IntOpenCustomHashMap<>(ScheduledTick.UNIQUE_TICK_HASH);
            order.defaultReturnValue(-1);
            int index = 0;
            for (ScheduledTick<?> tick : ticks) order.put(tick, index++);
        }

        private int orderOf(Object type, BlockPos position) {
            return order.getInt(ScheduledTick.probe(type, position));
        }
    }

    private record Binding(Object levelTicks, Snapshot snapshot, int dispatchOrder) { }
}
