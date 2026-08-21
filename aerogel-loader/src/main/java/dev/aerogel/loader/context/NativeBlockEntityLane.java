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
        pending.add(new Request(List.copyOf(blockEntities), action));
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
        if (!context.submitNative(() -> NativeTickCoordinator.runNative(
            request.blockEntities, request.action, this::scheduleNext), rejected)) {
            rejected.run();
        }
    }

    private record Request(
        List<TickingBlockEntity> blockEntities,
        Consumer<TickingBlockEntity> action
    ) { }
}
