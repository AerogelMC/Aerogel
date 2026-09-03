package dev.aerogel.loader.worldgen;

import dev.aerogel.loader.internal.GenerationNodeExecutorBridge;
import dev.aerogel.loader.mixin.core.GenerationChunkHolderInvoker;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.GeneratingChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkDependencies;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

/** World-owned registry of in-flight {@code (chunk, ChunkStatus)} nodes. */
public final class ChunkGenerationCoordinator {
    private final GeneratingChunkMap chunkMap;
    private final GenerationNodeExecutorBridge executor;
    private final ConcurrentHashMap<NodeKey, Node> inFlight =
        new ConcurrentHashMap<>();

    public ChunkGenerationCoordinator(
        GeneratingChunkMap chunkMap, GenerationNodeExecutorBridge executor
    ) {
        this.chunkMap = chunkMap;
        this.executor = executor;
    }

    public CompletableFuture<Void> request(
        StaticCache2D<GenerationChunkHolder> cache,
        int chunkX,
        int chunkZ,
        ChunkStatus targetStatus,
        boolean generationExpected
    ) {
        try {
            return acquire(cache, chunkX, chunkZ, targetStatus,
                generationExpected).completion;
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    public int inFlightCount() {
        return inFlight.size();
    }

    private Node acquire(
        StaticCache2D<GenerationChunkHolder> cache,
        int chunkX,
        int chunkZ,
        ChunkStatus status,
        boolean generationExpected
    ) {
        GenerationChunkHolder holder = cache.get(chunkX, chunkZ);
        if (status == ChunkStatus.EMPTY) return Node.completed(holder);

        NodeKey key = new NodeKey(chunkX, chunkZ, status);
        Node existing = inFlight.get(key);
        if (existing != null) return validateHolder(existing, holder, key);

        ChunkStatus persisted = holder.getPersistedStatus();
        boolean generates = persisted != null && status.isAfter(persisted);
        if (generates && !generationExpected) {
            throw new IllegalStateException(
                "Can't load chunk, but didn't expect to need to generate");
        }

        Node candidate = new Node(key, holder);
        existing = inFlight.putIfAbsent(key, candidate);
        if (existing != null) {
            return validateHolder(existing, holder, key);
        }

        initialize(candidate, cache, status, generates, generationExpected);
        return candidate;
    }

    private static Node validateHolder(
        Node node, GenerationChunkHolder holder, NodeKey key
    ) {
        if (node.holder != holder) {
            throw new IllegalStateException(
                "Generation holder changed while node was in flight at "
                    + key.x + "," + key.z + " for " + key.status);
        }
        return node;
    }

    private void initialize(
        Node node,
        StaticCache2D<GenerationChunkHolder> cache,
        ChunkStatus status,
        boolean generates,
        boolean generationExpected
    ) {
        try {
            ChunkPyramid pyramid = generates
                ? ChunkPyramid.GENERATION_PYRAMID
                : ChunkPyramid.LOADING_PYRAMID;
            ChunkStep step = pyramid.getStepTo(status);
            ChunkDependencies dependencies = step.directDependencies();
            int radius = dependencies.getRadius();
            int diameter = radius * 2 + 1;
            CompletableFuture<?>[] predecessors =
                new CompletableFuture<?>[diameter * diameter];
            int predecessorIndex = 0;

            for (int x = node.key.x - radius; x <= node.key.x + radius; x++) {
                for (int z = node.key.z - radius; z <= node.key.z + radius; z++) {
                    int distance = Math.max(
                        Math.abs(x - node.key.x), Math.abs(z - node.key.z));
                    ChunkStatus required = dependencies.get(distance);
                    if (required.isOrAfter(status)) {
                        throw new IllegalStateException(
                            "Non-descending ChunkStep dependency: " + required
                                + " -> " + status);
                    }
                    predecessors[predecessorIndex++] = acquire(
                        cache, x, z, required, generationExpected).completion;
                }
            }

            AtomicInteger remaining = new AtomicInteger(predecessors.length);
            for (CompletableFuture<?> predecessor : predecessors) {
                predecessor.whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        completeExceptionally(node, failure);
                    } else if (remaining.decrementAndGet() == 0
                        && !node.completion.isDone()) {
                        executor.aerogel$submitGenerationNode(
                            node.holder, () -> execute(node, step, cache));
                    }
                });
            }
        } catch (Throwable failure) {
            completeExceptionally(node, failure);
        }
    }

    private void execute(
        Node node,
        ChunkStep step,
        StaticCache2D<GenerationChunkHolder> cache
    ) {
        CompletableFuture<ChunkResult<ChunkAccess>> applied;
        try {
            applied = ((GenerationChunkHolderInvoker) node.holder)
                .aerogel$applyStep(step, chunkMap, cache);
        } catch (Throwable failure) {
            completeExceptionally(node, failure);
            throw failure;
        }
        applied.whenComplete((result, failure) -> {
            if (failure != null) {
                completeExceptionally(node, failure);
            } else if (result == null || !result.isSuccess()) {
                completeExceptionally(node, new IllegalStateException(
                    result == null ? "Chunk step returned no result"
                        : String.valueOf(result.getError())));
            } else {
                node.completion.complete(null);
                inFlight.remove(node.key, node);
            }
        });
    }

    private void completeExceptionally(Node node, Throwable failure) {
        node.completion.completeExceptionally(failure);
        inFlight.remove(node.key, node);
    }

    private record NodeKey(int x, int z, ChunkStatus status) { }

    private static final class Node {
        private final NodeKey key;
        private final GenerationChunkHolder holder;
        private final CompletableFuture<Void> completion;

        private Node(NodeKey key, GenerationChunkHolder holder) {
            this(key, holder, new CompletableFuture<>());
        }

        private Node(
            NodeKey key,
            GenerationChunkHolder holder,
            CompletableFuture<Void> completion
        ) {
            this.key = key;
            this.holder = holder;
            this.completion = completion;
        }

        private static Node completed(GenerationChunkHolder holder) {
            return new Node(null, holder, CompletableFuture.completedFuture(null));
        }
    }
}
