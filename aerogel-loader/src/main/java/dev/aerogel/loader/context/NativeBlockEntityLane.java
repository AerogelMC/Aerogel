package dev.aerogel.loader.context;

import net.minecraft.world.level.block.entity.TickingBlockEntity;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/** A lossless, independently paced block-entity tick lane for one chunk. */
final class NativeBlockEntityLane {
    private final ChunkContextImpl context;
    private final ConcurrentLinkedQueue<Request> pending = new ConcurrentLinkedQueue<>();
    private final PaddedAtomicBoolean active = new PaddedAtomicBoolean();

    NativeBlockEntityLane(ChunkContextImpl context) {
        this.context = context;
    }

    void offer(List<TickingBlockEntity> blockEntities, Consumer<TickingBlockEntity> action) {
        offer(blockEntities, action, null);
    }

    void offer(
        List<TickingBlockEntity> blockEntities,
        Consumer<TickingBlockEntity> action,
        NativeTickToken token
    ) {
        List<TickingBlockEntity> snapshot = List.copyOf(blockEntities);
        if (token == null) {
            enqueue(snapshot, action, null);
            return;
        }
        context.offerTickTask(token,
            tickState -> enqueue(snapshot, action, tickState), () -> { });
    }

    private void enqueue(
        List<TickingBlockEntity> blockEntities,
        Consumer<TickingBlockEntity> action,
        ChunkContextImpl.TickState tickState
    ) {
        pending.add(new Request(blockEntities, action, tickState));
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
        if (!context.submitNative(() -> NativeTickCoordinator.runNative(
            request.blockEntities, request.action, () -> {
                context.completeTickTask(request.tickState);
                scheduleNext();
            }), rejected)) {
            rejected.run();
        }
    }

    private record Request(
        List<TickingBlockEntity> blockEntities,
        Consumer<TickingBlockEntity> action,
        ChunkContextImpl.TickState tickState
    ) { }
}
