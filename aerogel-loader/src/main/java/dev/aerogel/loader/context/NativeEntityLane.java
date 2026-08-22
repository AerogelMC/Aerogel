package dev.aerogel.loader.context;

import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/** A lossless, independently paced entity-tick lane owned by one chunk context. */
final class NativeEntityLane {
    private final ChunkContextImpl context;
    private final ConcurrentLinkedQueue<Request> pending = new ConcurrentLinkedQueue<>();
    private final PaddedAtomicBoolean active = new PaddedAtomicBoolean();

    NativeEntityLane(ChunkContextImpl context) {
        this.context = context;
    }

    void offer(List<Entity> entities, Consumer<Entity> action) {
        offer(entities, action, null);
    }

    void offer(
        List<Entity> entities, Consumer<Entity> action, NativeTickToken token
    ) {
        if (entities.isEmpty()) return;
        if (token == null) {
            enqueue(entities, action, null);
            return;
        }
        context.offerTickTask(token,
            tickState -> enqueue(entities, action, tickState), () -> { });
    }

    private void enqueue(
        List<Entity> entities, Consumer<Entity> action,
        ChunkContextImpl.TickState tickState
    ) {
        pending.add(new Request(new ArrayList<>(entities), action, tickState));
        if (active.compareAndSet(false, true)) scheduleNext();
    }

    private void scheduleNext() {
        Request request = pending.poll();
        if (request == null) {
            active.set(false);
            if (!pending.isEmpty() && active.compareAndSet(false, true)) scheduleNext();
            return;
        }

        NativeTickCoordinator.taskSubmitted();
        Runnable rejected = () -> {
            NativeTickCoordinator.taskRejected();
            context.completeTickTask(request.tickState);
            Request dropped;
            while ((dropped = pending.poll()) != null) {
                context.completeTickTask(dropped.tickState);
            }
            active.set(false);
        };
        Consumer<Entity> ownedAction = entity -> context.runEntity(entity, request.action);
        long[] scope = ContextServiceImpl.entityTickScope(context, request.entities);
        if (!context.submitNative(scope, () -> NativeTickCoordinator.runNativeAfterGlobalCommit(
            request.entities, ownedAction, () -> {
                context.completeTickTask(request.tickState);
                scheduleNext();
            }), rejected)) {
            rejected.run();
        }
    }

    private record Request(
        List<Entity> entities, Consumer<Entity> action,
        ChunkContextImpl.TickState tickState
    ) {
    }
}
