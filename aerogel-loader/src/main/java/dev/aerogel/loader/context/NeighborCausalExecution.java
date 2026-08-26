package dev.aerogel.loader.context;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;

/** Binds a native owner action to the neighbor causal group it creates or resumes. */
final class NeighborCausalExecution {
    private static final ContextWorkerLocal<State> CURRENT = ContextWorkerLocal.create();

    private NeighborCausalExecution() { }

    static void enter(NeighborCausalGroup admitted) {
        if (CURRENT.get() != null) throw new IllegalStateException("Nested causal execution");
        CURRENT.set(new State(admitted));
    }

    static NeighborCausalGroup current() {
        State state = CURRENT.get();
        return state == null ? null : state.group;
    }

    static NeighborCausalGroup attachCurrent() {
        State state = CURRENT.get();
        if (state == null) throw new IllegalStateException("No Context action owns this chain");
        if (state.group == null) state.group = NeighborCausalGroup.startCurrent();
        return state.group;
    }

    static CollectingNeighborUpdater currentUpdater(
        Level level, int maximumChainedUpdates
    ) {
        State state = CURRENT.get();
        if (state == null) throw new IllegalStateException("No Context action owns this chain");
        if (state.updater != null) return state.updater;
        NeighborCausalGroup group = attachCurrent();
        CollectingNeighborUpdater candidate =
            new CollectingNeighborUpdater(level, maximumChainedUpdates);
        NeighborUpdateContinuation.attach(candidate, group);
        CollectingNeighborUpdater canonical = group.canonicalUpdater(candidate);
        NeighborUpdateContinuation.attach(canonical, group);
        state.updater = canonical;
        state.requiresAdmission = canonical != candidate;
        return canonical;
    }

    static boolean shouldDeferChainAdmission() {
        State state = CURRENT.get();
        return state != null && !state.admitted && state.requiresAdmission;
    }

    static void leave() {
        State state = CURRENT.get();
        CURRENT.remove();
        if (state != null && state.group != null) state.group.actionCompleted();
    }

    private static final class State {
        private NeighborCausalGroup group;
        private final boolean admitted;
        private CollectingNeighborUpdater updater;
        private boolean requiresAdmission;

        private State(NeighborCausalGroup group) {
            this.group = group;
            this.admitted = group != null;
        }
    }
}
