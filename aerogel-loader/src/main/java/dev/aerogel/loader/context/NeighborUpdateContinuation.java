package dev.aerogel.loader.context;

import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import dev.aerogel.loader.runtime.AerogelRuntime;

import java.util.Objects;

/**
 * Transfers one vanilla neighbor-update stack across exact Context ownership
 * boundaries without blocking a worker or splitting the causal update order.
 */
public final class NeighborUpdateContinuation {
    private static final ContextWorkerLocal<CollectingNeighborUpdater> ACTIVE =
        ContextWorkerLocal.create();
    private static final ContextWorkerLocal<Suspension> SUSPENDED =
        ContextWorkerLocal.create();

    private NeighborUpdateContinuation() { }

    public static CollectingNeighborUpdater current() {
        return ACTIVE.get();
    }

    static void attach(
        CollectingNeighborUpdater updater, NeighborCausalGroup group
    ) {
        ((NeighborUpdaterAsyncBridge) (Object) updater)
            .aerogel$neighborCausalGroup(group);
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

    /**
     * Admits a newly-created vanilla causal chain to the owner actor when another
     * chain already owns an intersecting Context. Calls made while count is nonzero
     * are part of the current vanilla chain and must remain in addedThisLayer.
     */
    public static boolean deferChainAdmission(
        CollectingNeighborUpdater updater, BlockPos position, Runnable chainStart
    ) {
        Objects.requireNonNull(updater, "updater");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(chainStart, "chainStart");
        if (!NeighborCausalExecution.shouldDeferChainAdmission()) return false;
        ContextThreadState.AccessScope scope = ContextThreadState.current();
        if (scope == null || !(scope.primary().world().level() instanceof ServerLevel level)) {
            return false;
        }
        return AerogelRuntime.deferNeighborChain(level, position.immutable(), chainStart);
    }

    public static void suspend(
        CollectingNeighborUpdater updater, Runnable publishContinuation
    ) {
        if (ACTIVE.get() != updater) {
            throw new IllegalStateException("Neighbor continuation lost its causal queue");
        }
        if (SUSPENDED.get() != null) {
            throw new IllegalStateException("Neighbor continuation suspended twice");
        }
        ((NeighborUpdaterAsyncBridge) (Object) updater)
            .aerogel$beginAsyncNeighborContinuation();
        SUSPENDED.set(new Suspension(updater,
            Objects.requireNonNull(publishContinuation, "publishContinuation")));
    }

    /**
     * Applies the stack transition that vanilla performs immediately after
     * NeighborUpdates.runNext returns. A cross-Context handoff cancels
     * runUpdates before that bytecode executes, so a completed entry must be
     * removed here before its continuation is resumed on the target Context.
     */
    public static boolean pauseAfterCompletedStep(CollectingNeighborUpdater updater) {
        Suspension suspension = SUSPENDED.get();
        if (suspension == null || suspension.updater != updater) return false;
        SUSPENDED.remove();
        NeighborUpdaterContinuationBridge bridge =
            (NeighborUpdaterContinuationBridge) (Object) updater;
        Object current = bridge.aerogel$neighborUpdateStack().peek();
        boolean hasRemaining = current instanceof NeighborUpdatesProgressBridge progress
            && progress.aerogel$hasRemainingNeighborUpdates();
        if (!hasRemaining && current != null) bridge.aerogel$neighborUpdateStack().pop();
        try {
            /*
             * Do not publish from inside runUpdates. A target owner can execute the
             * continuation before the cancellable source invocation has actually
             * returned, which lets two threads enter the same ArrayDeque. The native
             * completion boundary is the first point at which the whole source action
             * is guaranteed to have unwound; it is not a tick or global commit barrier.
             */
            if (!NativeTickCoordinator.deferNativeCompletion(
                suspension.publishContinuation)) {
                throw new IllegalStateException(
                    "Neighbor continuation escaped its native transaction");
            }
        } catch (Throwable failure) {
            discard(updater);
            ((NeighborUpdaterAsyncBridge) (Object) updater)
                .aerogel$endAsyncNeighborContinuation();
            throw failure;
        }
        return true;
    }

    public static Runnable resumeAfter(
        CollectingNeighborUpdater updater, Runnable targetUpdate
    ) {
        Objects.requireNonNull(updater, "updater");
        Objects.requireNonNull(targetUpdate, "targetUpdate");
        return () -> {
            try {
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
            } finally {
                ((NeighborUpdaterAsyncBridge) (Object) updater)
                    .aerogel$endAsyncNeighborContinuation();
            }
        };
    }

    public static boolean hasAsyncContinuation(CollectingNeighborUpdater updater) {
        return ((NeighborUpdaterAsyncBridge) (Object) updater)
            .aerogel$hasAsyncNeighborContinuation();
    }

    public static void cancelSuspended(CollectingNeighborUpdater updater) {
        Objects.requireNonNull(updater, "updater");
        discard(updater);
        ((NeighborUpdaterAsyncBridge) (Object) updater)
            .aerogel$endAsyncNeighborContinuation();
    }

    /** Clears worker-local control state after an exceptional native transaction. */
    static void clearWorkerState() {
        ACTIVE.remove();
        Suspension suspension = SUSPENDED.get();
        if (suspension != null) {
            discard(suspension.updater);
            ((NeighborUpdaterAsyncBridge) (Object) suspension.updater)
                .aerogel$endAsyncNeighborContinuation();
        }
        SUSPENDED.remove();
    }

    private static void discard(CollectingNeighborUpdater updater) {
        NeighborUpdaterContinuationBridge bridge =
            (NeighborUpdaterContinuationBridge) (Object) updater;
        bridge.aerogel$neighborUpdateStack().clear();
        bridge.aerogel$neighborUpdatesAddedThisLayer().clear();
        bridge.aerogel$neighborUpdateCount(0);
    }

    private record Suspension(
        CollectingNeighborUpdater updater, Runnable publishContinuation
    ) { }
}
