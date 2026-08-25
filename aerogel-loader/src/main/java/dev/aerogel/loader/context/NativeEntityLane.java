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
            () -> runValidatedGroup(request, group, ownedAction), rejected)) {
            rejected.run();
        }
    }

    /**
     * Entity-owned state can add an exact Context dependency after the producer
     * partitioned this tick. Opening a block menu is one example: the already
     * queued player tick must also own that menu's block Context before calling
     * stillValid. Re-evaluate at admission, while the entity owner is reserved.
     * If the required scope grew, replace this group and enqueue it behind all
     * entity mutations that were already ordered ahead of it. Revalidation at
     * every admission makes this converge on the latest state without a retry
     * count, distance rule, global lock, or partially executing an entity tick.
     */
    private void runValidatedGroup(
        Request request, ScopeGroup admitted, Consumer<Entity> ownedAction
    ) {
        boolean covered = true;
        for (Entity entity : admitted.entities) {
            if (!ContextServiceImpl.entityTickScopeCovered(
                context, entity, admitted.scope)) {
                covered = false;
                break;
            }
        }
        if (!covered) {
            List<ScopeGroup> current = partition(admitted.entities);
            request.groups.remove(request.groupIndex);
            request.groups.addAll(request.groupIndex, current);
            try {
                scheduleGroup(request);
            } finally {
                // This admission never enters runNative: its replacement owns a
                // fresh permit, so the superseded admission must return its own.
                NativeTickCoordinator.taskSuperseded();
            }
            return;
        }
        NativeTickCoordinator.runNativeAfterGlobalCommit(
            admitted.entities, ownedAction, () -> completeGroup(request));
    }

    private void completeGroup(Request request) {
        request.groupIndex++;
        if (request.groupIndex < request.groups.size()) {
            scheduleGroup(request);
        } else {
            context.completeTickTask(request.tickState);
            scheduleNext();
        }
    }

    private static final class Request {
        private final ArrayList<ScopeGroup> groups;
        private final Consumer<Entity> action;
        private final ChunkContextImpl.TickState tickState;
        private int groupIndex;

        private Request(
            List<ScopeGroup> groups, Consumer<Entity> action,
            ChunkContextImpl.TickState tickState
        ) {
            this.groups = new ArrayList<>(groups);
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
