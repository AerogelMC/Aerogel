package dev.aerogel.loader.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class OwnerPublicationBarrierTest {
    @Test
    void ownerGenerationSettlesOnlyAfterItsExactServerPublication() {
        AtomicInteger state = new AtomicInteger();

        CompletableFuture<Void> settled = OwnerPublicationBarrier.run(() -> {
            state.set(1);
            assertTrue(OwnerPublicationBarrier.defer(() -> state.set(2)));
        });

        assertEquals(1, state.get());
        assertFalse(settled.isDone());

        NativeTickCoordinator.pumpMainThread();

        assertEquals(2, state.get());
        assertTrue(settled.isDone());
        settled.join();
    }

    @Test
    void generationWithoutServerPublicationSettlesImmediately() {
        CompletableFuture<Void> settled = OwnerPublicationBarrier.run(() -> { });
        assertTrue(settled.isDone());
        settled.join();
    }

    @Test
    void publicationUsesTheExecutorOwnedByTheWaitingSubsystem() {
        ConcurrentLinkedQueue<Runnable> chunkExecutor = new ConcurrentLinkedQueue<>();

        CompletableFuture<Void> settled = OwnerPublicationBarrier.run(
            () -> assertTrue(OwnerPublicationBarrier.defer(() -> { })),
            chunkExecutor::add);

        NativeTickCoordinator.pumpMainThread();
        assertFalse(settled.isDone(),
            "the unrelated server queue must not publish a chunk-owned continuation");
        chunkExecutor.remove().run();
        assertTrue(settled.isDone());
        settled.join();
    }
}
