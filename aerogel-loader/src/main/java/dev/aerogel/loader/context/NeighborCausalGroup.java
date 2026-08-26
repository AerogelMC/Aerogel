package dev.aerogel.loader.context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;

/**
 * Lock-free ownership group for one or more neighbor chains that actually meet.
 * Independent groups run concurrently. Once their footprints touch, union-find
 * gives the combined group one actor without introducing a world-wide lane.
 */
public final class NeighborCausalGroup {
    private final long originKey;
    private final long originSequence;
    private final AtomicReference<NeighborCausalGroup> parent =
        new AtomicReference<>(this);
    private final AtomicReference<CollectingNeighborUpdater> updater =
        new AtomicReference<>();
    private final ConcurrentLinkedQueue<NeighborCausalGroup> children =
        new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<CausalJob> jobs = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ChunkContextImpl> claims =
        new ConcurrentLinkedQueue<>();
    /**
     * Ordinary Context work that was submitted after this causal chain claimed
     * one of its scope keys.  These are not causal jobs: they retain their
     * original mailbox phase and accounting and are only republished after the
     * complete vanilla neighbor chain has released its claims.
     */
    private final ConcurrentLinkedQueue<Runnable> successors =
        new ConcurrentLinkedQueue<>();
    /** This node's admitted action; merged roots may temporarily have several. */
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicBoolean contended = new AtomicBoolean();
    private final AtomicBoolean scheduling = new AtomicBoolean();
    private final AtomicBoolean completed = new AtomicBoolean();

    private NeighborCausalGroup(long originKey, long originSequence) {
        this.originKey = originKey;
        this.originSequence = originSequence;
    }

    static NeighborCausalGroup startCurrent() {
        ContextThreadState.AccessScope scope = ContextThreadState.current();
        if (scope == null) throw new IllegalStateException("Neighbor chain has no Context owner");
        NeighborCausalGroup group = new NeighborCausalGroup(
            scope.primary().canonicalKey(),
            scope.primary().nextNeighborCausalSequence());
        group.claim(scope.primary());
        if (scope.ownedKeys() != null) {
            for (long key : scope.ownedKeys().toLongArray()) {
                group.claim(scope.primary().world().context(
                    net.minecraft.world.level.ChunkPos.getX(key),
                    net.minecraft.world.level.ChunkPos.getZ(key)));
            }
        }
        return group;
    }

    public NeighborCausalGroup root() {
        NeighborCausalGroup observed = parent.get();
        if (observed == this) return this;
        NeighborCausalGroup root = observed.root();
        parent.compareAndSet(observed, root);
        return root;
    }

    boolean isCompleted() {
        return root().completed.get();
    }

    NeighborCausalGroup claim(ChunkContextImpl context) {
        while (true) {
            NeighborCausalGroup root = root();
            if (root.completed.get()) return root;
            NeighborCausalGroup existing = context.neighborCausalClaim();
            if (existing == null || existing.isCompleted()) {
                if (!context.claimNeighborCausal(existing, this)) continue;
                claims.add(context);
                if (root().completed.get()) {
                    context.releaseNeighborCausal(this);
                    continue;
                }
                return root();
            }
            NeighborCausalGroup other = existing.root();
            if (other == root) return root;
            return union(root, other);
        }
    }

    private static NeighborCausalGroup union(
        NeighborCausalGroup first, NeighborCausalGroup second
    ) {
        while (true) {
            first = first.root();
            second = second.root();
            if (first == second) return first;
            NeighborCausalGroup winner = precedes(first, second) ? first : second;
            NeighborCausalGroup loser = winner == first ? second : first;
            // Both chains may already own different Contexts. Mark the collision
            // before publishing the union edge so the next chain admission waits
            // for the combined actor without pausing unrelated owner work.
            first.contended.set(true);
            second.contended.set(true);
            // Publish membership before the parent edge. A scheduler can only
            // consider the child after root() observes that edge.
            winner.children.add(loser);
            if (loser.parent.compareAndSet(loser, winner)) {
                winner.trySchedule();
                return winner;
            }
            winner.children.remove(loser);
        }
    }

    boolean contended() {
        return contended.get() || root().contended.get();
    }

    CollectingNeighborUpdater canonicalUpdater(CollectingNeighborUpdater candidate) {
        NeighborCausalGroup root = root();
        CollectingNeighborUpdater current = root.updater.get();
        if (current != null) return current;
        return root.updater.compareAndSet(null, candidate)
            ? candidate : root.updater.get();
    }

    private static boolean precedes(
        NeighborCausalGroup first, NeighborCausalGroup second
    ) {
        int keyOrder = Long.compareUnsigned(first.originKey, second.originKey);
        return keyOrder < 0 || keyOrder == 0
            && Long.compareUnsigned(first.originSequence, second.originSequence) < 0;
    }

    void enqueue(ChunkContextImpl owner, ContextTask task) {
        NeighborCausalGroup root = root();
        if (root.completed.get()) {
            owner.resumeCausalTask(task.withCausalGroup(null));
            return;
        }
        claimScope(owner.world(), task.scopeKeys());
        CausalJob job = new CausalJob(owner, task.withCausalGroup(this));
        jobs.add(job);
        root().trySchedule();
        /*
         * Close completion-after-check/before-publication. The completing actor
         * may have scanned an empty tree and published completed immediately
         * before this job became visible. In that case no causal claim remains,
         * so atomically reclaim this still-unselected job and publish it as
         * ordinary Context work. If pollJob already selected it, remove fails and
         * the actor owns the only completion path.
         */
        if (root().completed.get() && jobs.remove(job)) {
            owner.resumeCausalTask(task.withCausalGroup(null));
        }
    }

    private void claimScope(WorldContextImpl world, long[] scopeKeys) {
        for (long key : scopeKeys) {
            claim(world.context(
                net.minecraft.world.level.ChunkPos.getX(key),
                net.minecraft.world.level.ChunkPos.getZ(key)));
        }
    }

    void actionCompleted() {
        if (!active.compareAndSet(true, false)) {
            throw new IllegalStateException("Neighbor causal action completed twice");
        }
        root().trySchedule();
    }

    private void trySchedule() {
        NeighborCausalGroup root = root();
        if (root != this) {
            root.trySchedule();
            return;
        }
        if (!scheduling.compareAndSet(false, true)) return;
        try {
            if (completed.get()) return;
            while (true) {
                if (hasActive(this)) return;
                NodeJob selected = pollJob(this);
                if (selected == null) {
                    if (!completed.compareAndSet(false, true)) return;
                    releaseClaims(this);
                    releaseSuccessors(this);
                    return;
                }
                if (!selected.node.active.compareAndSet(false, true)) continue;
                selected.job.owner.resumeCausalTask(
                    selected.job.task.withCausalGroup(selected.node));
                return;
            }
        } finally {
            scheduling.set(false);
            // Close enqueue/merge-after-scan without spinning or a timer.
            if (!completed.get() && !hasActive(this)) trySchedule();
        }
    }

    private static boolean hasActive(NeighborCausalGroup node) {
        if (node.active.get()) return true;
        for (NeighborCausalGroup child : node.children) {
            if (child.root() == node.root() && hasActive(child)) return true;
        }
        return false;
    }

    private static NodeJob pollJob(NeighborCausalGroup node) {
        CausalJob own = node.jobs.poll();
        if (own != null) return new NodeJob(node, own);
        for (NeighborCausalGroup child : node.children) {
            if (child.root() != node.root()) continue;
            NodeJob selected = pollJob(child);
            if (selected != null) return selected;
        }
        return null;
    }

    private static void releaseClaims(NeighborCausalGroup node) {
        List<NeighborCausalGroup> members = new ArrayList<>();
        collectMembers(node, members);
        for (NeighborCausalGroup member : members) {
            ChunkContextImpl context;
            while ((context = member.claims.poll()) != null) {
                context.releaseNeighborCausal(member);
            }
        }
    }

    private static void collectMembers(
        NeighborCausalGroup node, List<NeighborCausalGroup> members
    ) {
        members.add(node);
        for (NeighborCausalGroup child : node.children) {
            if (child.root() == node.root()) collectMembers(child, members);
        }
    }

    /**
     * Publishes {@code successor} exactly once after the complete unioned causal
     * group.  A completed group needs no dependency.  The post-publication check
     * closes completion racing queue insertion without polling or a lock.
     */
    boolean awaitCompletion(Runnable successor) {
        NeighborCausalGroup root = root();
        if (root.completed.get()) return false;
        root.successors.add(successor);
        if (root.completed.get() && root.successors.remove(successor)) {
            successor.run();
        }
        return true;
    }

    private static void releaseSuccessors(NeighborCausalGroup node) {
        List<NeighborCausalGroup> members = new ArrayList<>();
        collectMembers(node, members);
        for (NeighborCausalGroup member : members) {
            Runnable successor;
            while ((successor = member.successors.poll()) != null) successor.run();
        }
    }

    private record CausalJob(ChunkContextImpl owner, ContextTask task) { }
    private record NodeJob(NeighborCausalGroup node, CausalJob job) { }

}
