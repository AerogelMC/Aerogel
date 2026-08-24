package dev.aerogel.loader.context;

import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        List<Entity> snapshot = new ArrayList<>(entities);
        if (token == null) {
            enqueue(snapshot, action, null);
            return;
        }
        context.offerTickTask(token,
            tickState -> enqueue(snapshot, action, tickState), () -> { });
    }

    private void enqueue(
        List<Entity> entities, Consumer<Entity> action,
        ChunkContextImpl.TickState tickState
    ) {
        pending.add(new Request(partition(entities), action, tickState));
        if (active.compareAndSet(false, true)) scheduleNext();
    }

    /**
     * Keeps the common owner-only entities independent from the exceptional
     * entities whose swept box actually crosses a chunk face. Unioning every
     * entity footprint into one scope makes one boundary entity reserve adjacent
     * chunks for the entire owner batch and creates a dense reservation conflict
     * graph in mob-heavy worlds.
     */
    private List<ScopeGroup> partition(List<Entity> entities) {
        Map<ScopeKey, List<Entity>> byScope = new LinkedHashMap<>();
        for (Entity entity : entities) {
            long[] scope = ContextServiceImpl.entityTickScope(context, entity);
            Arrays.sort(scope);
            ScopeKey key = new ScopeKey(scope);
            byScope.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entity);
        }
        List<ScopeGroup> groups = new ArrayList<>(byScope.size());
        byScope.forEach((scope, grouped) ->
            groups.add(new ScopeGroup(scope.keys, List.copyOf(grouped))));
        return groups;
    }

    private void scheduleNext() {
        Request request = pending.poll();
        if (request == null) {
            active.set(false);
            if (!pending.isEmpty() && active.compareAndSet(false, true)) scheduleNext();
            return;
        }

        scheduleGroup(request);
    }

    private void scheduleGroup(Request request) {
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
        ScopeGroup group = request.groups.get(request.groupIndex);
        if (!context.submitNativePhase(NativePhase.ENTITY, group.scope,
            () -> NativeTickCoordinator.runNativeAfterGlobalCommit(
            group.entities, ownedAction, () -> {
                request.groupIndex++;
                if (request.groupIndex < request.groups.size()) {
                    scheduleGroup(request);
                } else {
                    context.completeTickTask(request.tickState);
                    scheduleNext();
                }
            }), rejected)) {
            rejected.run();
        }
    }

    private static final class Request {
        private final List<ScopeGroup> groups;
        private final Consumer<Entity> action;
        private final ChunkContextImpl.TickState tickState;
        private int groupIndex;

        private Request(
            List<ScopeGroup> groups, Consumer<Entity> action,
            ChunkContextImpl.TickState tickState
        ) {
            this.groups = groups;
            this.action = action;
            this.tickState = tickState;
        }
    }

    private record ScopeGroup(long[] scope, List<Entity> entities) { }

    private static final class ScopeKey {
        private final long[] keys;
        private final int hash;

        private ScopeKey(long[] keys) {
            this.keys = keys;
            this.hash = Arrays.hashCode(keys);
        }

        @Override public int hashCode() { return hash; }

        @Override
        public boolean equals(Object other) {
            return other instanceof ScopeKey key && Arrays.equals(keys, key.keys);
        }
    }
}
