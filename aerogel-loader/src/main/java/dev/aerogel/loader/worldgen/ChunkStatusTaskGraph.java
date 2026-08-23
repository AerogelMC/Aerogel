package dev.aerogel.loader.worldgen;

import dev.aerogel.loader.internal.GenerationNodeExecutorBridge;
import dev.aerogel.loader.mixin.core.GenerationChunkHolderInvoker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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

/**
 * A dependency graph for one vanilla chunk-generation request.
 *
 * <p>The graph contains one node per (chunk, status). Edges come exclusively
 * from the selected vanilla {@link ChunkStep#directDependencies()}; Aerogel
 * does not invent a radius or a concurrency limit. Once all predecessors are
 * complete, the node is submitted to the existing priority-aware worldgen
 * dispatcher. {@code GenerationChunkHolder.applyStep} remains the global
 * authority for status ordering and duplicate suppression across requests.</p>
 */
public final class ChunkStatusTaskGraph {
    private final GeneratingChunkMap chunkMap;
    private final StaticCache2D<GenerationChunkHolder> cache;
    private final GenerationNodeExecutorBridge executor;
    private final boolean generationExpected;
    private final Runnable cancellationAction;
    private final Map<NodeKey, Node> nodes = new HashMap<>();
    private final CompletableFuture<Void> terminal = new CompletableFuture<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile boolean successful;
    private Node root;

    public ChunkStatusTaskGraph(
        GeneratingChunkMap chunkMap,
        StaticCache2D<GenerationChunkHolder> cache,
        int centerX,
        int centerZ,
        ChunkStatus targetStatus,
        boolean generationExpected,
        Runnable cancellationAction
    ) {
        if (!(chunkMap instanceof GenerationNodeExecutorBridge nodeExecutor)) {
            throw new IllegalStateException(
                "GeneratingChunkMap does not expose the vanilla worldgen dispatcher");
        }
        this.chunkMap = chunkMap;
        this.cache = cache;
        this.executor = nodeExecutor;
        this.generationExpected = generationExpected;
        this.cancellationAction = cancellationAction;

        root = build(centerX, centerZ, targetStatus);
        prepareAndStart();
    }

    public CompletableFuture<Void> terminal() {
        return terminal;
    }

    public boolean successful() {
        return successful;
    }

    public void cancel() {
        failGraph();
    }

    public int nodeCount() {
        return nodes.size();
    }

    private Node build(int chunkX, int chunkZ, ChunkStatus status) {
        NodeKey key = new NodeKey(chunkX, chunkZ, status);
        Node existing = nodes.get(key);
        if (existing != null) return existing;

        GenerationChunkHolder holder = cache.get(chunkX, chunkZ);
        Node node = new Node(holder, status);
        nodes.put(key, node);

        // Both vanilla pyramids bottom out at EMPTY. The initial EMPTY layer
        // has already loaded the entire accumulated dependency footprint.
        if (status == ChunkStatus.EMPTY) {
            node.emptyRoot = true;
            return node;
        }

        ChunkStatus persisted = holder.getPersistedStatus();
        boolean generates = persisted != null && status.isAfter(persisted);
        if (generates && !generationExpected) {
            throw new IllegalStateException(
                "Can't load chunk, but didn't expect to need to generate");
        }

        ChunkPyramid pyramid = generates
            ? ChunkPyramid.GENERATION_PYRAMID
            : ChunkPyramid.LOADING_PYRAMID;
        node.step = pyramid.getStepTo(status);

        ChunkDependencies dependencies = node.step.directDependencies();
        int radius = dependencies.getRadius();
        for (int x = chunkX - radius; x <= chunkX + radius; x++) {
            for (int z = chunkZ - radius; z <= chunkZ + radius; z++) {
                int distance = Math.max(Math.abs(x - chunkX), Math.abs(z - chunkZ));
                ChunkStatus requiredStatus = dependencies.get(distance);
                if (requiredStatus.isOrAfter(status)) {
                    throw new IllegalStateException(
                        "Non-descending ChunkStep dependency: " + requiredStatus
                            + " -> " + status);
                }
                node.dependencies.add(build(x, z, requiredStatus));
            }
        }
        return node;
    }

    private void prepareAndStart() {
        for (Node node : nodes.values()) {
            if (node.emptyRoot) {
                node.completed = true;
            }
        }
        for (Node node : nodes.values()) {
            if (node.emptyRoot) continue;
            int unresolved = 0;
            for (Node dependency : node.dependencies) {
                if (dependency.completed) continue;
                unresolved++;
                dependency.successors.add(node);
            }
            node.remainingDependencies.set(unresolved);
        }

        if (root.completed) {
            successful = true;
            terminal.complete(null);
            return;
        }
        for (Node node : nodes.values()) {
            if (!node.emptyRoot && node.remainingDependencies.get() == 0) {
                schedule(node);
            }
        }
    }

    private void schedule(Node node) {
        if (cancelled.get() || !node.scheduled.compareAndSet(false, true)) return;
        executor.aerogel$submitGenerationNode(node.holder, () -> execute(node));
    }

    private void execute(Node node) {
        if (cancelled.get()) return;
        CompletableFuture<ChunkResult<ChunkAccess>> future;
        try {
            future = ((GenerationChunkHolderInvoker) node.holder)
                .aerogel$applyStep(node.step, chunkMap, cache);
        } catch (Throwable failure) {
            failGraph();
            throw failure;
        }
        future.whenComplete((result, failure) -> {
            if (failure != null || result == null || !result.isSuccess()) {
                failGraph();
                return;
            }
            complete(node);
        });
    }

    private void complete(Node node) {
        if (cancelled.get() || !node.done.compareAndSet(false, true)) return;
        node.completed = true;
        if (node == root) {
            successful = true;
            terminal.complete(null);
            return;
        }
        for (Node successor : node.successors) {
            if (successor.remainingDependencies.decrementAndGet() == 0) {
                schedule(successor);
            }
        }
    }

    private void failGraph() {
        if (!cancelled.compareAndSet(false, true)) return;
        cancellationAction.run();
        // ChunkMap resumes tasks with thenRun(), so the terminal wake-up must
        // complete normally even when the holder result reports cancellation.
        terminal.complete(null);
    }

    private record NodeKey(int x, int z, ChunkStatus status) { }

    private static final class Node {
        private final GenerationChunkHolder holder;
        private final ChunkStatus status;
        private final List<Node> dependencies = new ArrayList<>();
        private final List<Node> successors = new ArrayList<>();
        private final AtomicInteger remainingDependencies = new AtomicInteger();
        private final AtomicBoolean scheduled = new AtomicBoolean();
        private final AtomicBoolean done = new AtomicBoolean();
        private ChunkStep step;
        private boolean emptyRoot;
        private volatile boolean completed;

        private Node(GenerationChunkHolder holder, ChunkStatus status) {
            this.holder = holder;
            this.status = status;
        }
    }
}
