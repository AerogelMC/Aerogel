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
        offer(chunk, action, () -> { }, null);
    }

    void offer(
        LevelChunk chunk, Consumer<LevelChunk> action, NativeTickToken token
    ) {
        offer(chunk, action, () -> { }, token);
    }

    void offer(LevelChunk chunk, Consumer<LevelChunk> action, Runnable completion) {
        offer(chunk, action, completion, null);
    }

    void offer(
        LevelChunk chunk, Consumer<LevelChunk> action, Runnable completion,
        NativeTickToken token
    ) {
        if (token == null) {
            enqueue(chunk, action, completion, null);
            return;
        }
        context.offerTickTask(token,
            tickState -> enqueue(chunk, action, completion, tickState),
            completion);
    }

    private void enqueue(
        LevelChunk chunk, Consumer<LevelChunk> action, Runnable completion,
        ChunkContextImpl.TickState tickState
    ) {
        pending.add(new Request(chunk, action, completion, tickState));
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
            complete(request);
            active.set(false);
            Request dropped;
            while ((dropped = pending.poll()) != null) complete(dropped);
        };
        if (!context.submitNative(() -> NativeTickCoordinator.runNative(
            List.of(request.chunk), chunk -> {
                try {
                    request.action.accept(chunk);
                } finally {
                    NaturalSpawnReservation.releaseCurrent();
                    Runnable completion = () -> complete(request);
                    if (!NativeTickCoordinator.afterGlobalCommit(completion)) {
                        completion.run();
                    }
                }
            }, this::scheduleNext), rejected)) {
            rejected.run();
        }
    }

    private void complete(Request request) {
        try {
            request.completion.run();
        } finally {
            context.completeTickTask(request.tickState);
        }
    }

    private record Request(
        LevelChunk chunk, Consumer<LevelChunk> action, Runnable completion,
        ChunkContextImpl.TickState tickState
    ) { }
}
