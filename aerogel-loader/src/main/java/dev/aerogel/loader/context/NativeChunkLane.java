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
        pending.add(new Request(chunk, action));
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
            List.of(request.chunk), request.action, this::scheduleNext), rejected)) {
            rejected.run();
        }
    }

    private record Request(LevelChunk chunk, Consumer<LevelChunk> action) { }
}
