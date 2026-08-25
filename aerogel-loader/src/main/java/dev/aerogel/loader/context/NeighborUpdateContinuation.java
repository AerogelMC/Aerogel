package dev.aerogel.loader.context;

import net.minecraft.world.level.redstone.CollectingNeighborUpdater;

import java.util.Objects;

/**
 * Transfers one vanilla neighbor-update stack across exact Context ownership
 * boundaries without blocking a worker or splitting the causal update order.
 */
public final class NeighborUpdateContinuation {
    private static final ContextWorkerLocal<CollectingNeighborUpdater> ACTIVE =
        ContextWorkerLocal.create();
    private static final ContextWorkerLocal<CollectingNeighborUpdater> SUSPENDED =
        ContextWorkerLocal.create();

    private NeighborUpdateContinuation() { }

    public static CollectingNeighborUpdater current() {
        return ACTIVE.get();
    }

    public static void enter(CollectingNeighborUpdater updater) {
        Objects.requireNonNull(updater, "updater");
        CollectingNeighborUpdater active = ACTIVE.get();
        if (active != null && active != updater) {
            throw new IllegalStateException("Nested neighbor update used a different causal queue");
        }
        ACTIVE.set(updater);
    }

    public static void leave(CollectingNeighborUpdater updater) {
        if (ACTIVE.get() == updater) ACTIVE.remove();
    }

    public static void suspend(CollectingNeighborUpdater updater) {
        if (ACTIVE.get() != updater) {
            throw new IllegalStateException("Neighbor continuation lost its causal queue");
        }
        SUSPENDED.set(updater);
    }

    public static boolean consumeSuspension(CollectingNeighborUpdater updater) {
        if (SUSPENDED.get() != updater) return false;
        SUSPENDED.remove();
        return true;
    }

    /**
     * Applies the stack transition that vanilla performs immediately after
     * NeighborUpdates.runNext returns. A cross-Context handoff cancels
     * runUpdates before that bytecode executes, so a completed entry must be
     * removed here before its continuation is resumed on the target Context.
     */
    public static boolean pauseAfterCompletedStep(CollectingNeighborUpdater updater) {
        if (!consumeSuspension(updater)) return false;
        NeighborUpdaterContinuationBridge bridge =
            (NeighborUpdaterContinuationBridge) (Object) updater;
        Object current = bridge.aerogel$neighborUpdateStack().peek();
        boolean hasRemaining = current instanceof NeighborUpdatesProgressBridge progress
            && progress.aerogel$hasRemainingNeighborUpdates();
        if (!hasRemaining && current != null) bridge.aerogel$neighborUpdateStack().pop();
        return true;
    }

    public static Runnable resumeAfter(
        CollectingNeighborUpdater updater, Runnable targetUpdate
    ) {
        Objects.requireNonNull(updater, "updater");
        Objects.requireNonNull(targetUpdate, "targetUpdate");
        return () -> {
            CollectingNeighborUpdater previous = ACTIVE.get();
            ACTIVE.set(updater);
            try {
                targetUpdate.run();
            } catch (Throwable failure) {
                discard(updater);
                throw failure;
            } finally {
                if (previous == null) ACTIVE.remove();
                else ACTIVE.set(previous);
            }
            ((NeighborUpdaterContinuationBridge) (Object) updater)
                .aerogel$resumeNeighborUpdates();
        };
    }

    /** Clears worker-local control state after an exceptional native transaction. */
    static void clearWorkerState() {
        ACTIVE.remove();
        SUSPENDED.remove();
    }

    private static void discard(CollectingNeighborUpdater updater) {
        NeighborUpdaterContinuationBridge bridge =
            (NeighborUpdaterContinuationBridge) (Object) updater;
        bridge.aerogel$neighborUpdateStack().clear();
        bridge.aerogel$neighborUpdatesAddedThisLayer().clear();
        bridge.aerogel$neighborUpdateCount(0);
    }
}
