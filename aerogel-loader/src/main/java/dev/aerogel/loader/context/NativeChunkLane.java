package dev.aerogel.loader.context;

import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/** A lossless, independently paced native tick lane for one LevelChunk. */
final class NativeChunkLane {
    private final ChunkContextImpl context;
    private final ConcurrentLinkedQueue<Request> pending = new ConcurrentLinkedQueue<>();
    private final PaddedAtomicBoolean active = new PaddedAtomicBoolean();

    NativeChunkLane(ChunkContextImpl context) {
        this.context = context;
    }

    void offer(LevelChunk chunk, Consumer<LevelChunk> action) {
        offer(chunk, action, () -> { });
    }

    void offer(LevelChunk chunk, Consumer<LevelChunk> action, Runnable completion) {
        pending.add(new Request(chunk, action, completion));
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
            request.completion.run();
            active.set(false);
            Request dropped;
            while ((dropped = pending.poll()) != null) dropped.completion.run();
        };
        if (!context.submitNative(() -> NativeTickCoordinator.runNative(
            List.of(request.chunk), chunk -> {
                try {
                    request.action.accept(chunk);
                } finally {
                    NaturalSpawnReservation.releaseCurrent();
                    if (!NativeTickCoordinator.afterGlobalCommit(request.completion)) {
                        request.completion.run();
                    }
                }
            }, this::scheduleNext), rejected)) {
            rejected.run();
        }
    }

    private record Request(
        LevelChunk chunk, Consumer<LevelChunk> action, Runnable completion
    ) { }
}
