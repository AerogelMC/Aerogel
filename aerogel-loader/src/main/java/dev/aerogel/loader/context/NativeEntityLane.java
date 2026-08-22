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
        if (entities.isEmpty()) return;
        pending.add(new Request(new ArrayList<>(entities), action));
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
            pending.clear();
            active.set(false);
        };
        Consumer<Entity> ownedAction = entity -> context.runEntity(entity, request.action);
        long[] scope = ContextServiceImpl.entityTickScope(context, request.entities);
        if (!context.submitNative(scope, () -> NativeTickCoordinator.runNativeAfterGlobalCommit(
            request.entities, ownedAction, this::scheduleNext), rejected)) {
            rejected.run();
        }
    }

    private record Request(List<Entity> entities, Consumer<Entity> action) {
    }
}
