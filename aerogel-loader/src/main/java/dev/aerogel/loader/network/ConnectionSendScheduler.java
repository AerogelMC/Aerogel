package dev.aerogel.loader.network;

import io.netty.channel.EventLoop;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Per-connection, pre-Netty priority lane.
 *
 * <p>Producers only publish into an MPSC inbox. The connection's event loop is
 * the sole consumer of the segment queues, so prioritisation needs no lock.
 * Exactly one send is executed per event-loop turn: queued interactive traffic
 * can pass bulk chunk traffic without a large drain monopolising Netty itself.
 * Barriers split the queue into ordered segments and can never be crossed.
 * After the apparent last send, ownership is retained through one event-loop
 * turn. A producer racing that turn reuses the existing wakeup instead of
 * waking the selector again; no packet is combined with another packet.</p>
 */
public final class ConnectionSendScheduler {
    private final Executor eventLoop;
    private final BooleanSupplier inEventLoop;
    private final String identity;
    private final boolean offloadExternalWakeup;
    private final ConcurrentLinkedQueue<Submission> inbox =
        new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    // Accessed only by the connection's event-loop thread.
    private final ArrayDeque<PendingSegment> pending = new ArrayDeque<>();
    private boolean waitingForCompletion;
    private boolean draining;

    public ConnectionSendScheduler(EventLoop eventLoop) {
        this(eventLoop, eventLoop::inEventLoop, "unknown", true);
    }

    public ConnectionSendScheduler(EventLoop eventLoop, String identity) {
        this(eventLoop, eventLoop::inEventLoop, identity, true);
    }

    ConnectionSendScheduler(Executor eventLoop, BooleanSupplier inEventLoop) {
        this(eventLoop, inEventLoop, "test", false);
    }

    ConnectionSendScheduler(
        Executor eventLoop, BooleanSupplier inEventLoop, String identity
    ) {
        this(eventLoop, inEventLoop, identity, false);
    }

    private ConnectionSendScheduler(
        Executor eventLoop, BooleanSupplier inEventLoop, String identity,
        boolean offloadExternalWakeup
    ) {
        this.eventLoop = Objects.requireNonNull(eventLoop, "eventLoop");
        this.inEventLoop = Objects.requireNonNull(inEventLoop, "inEventLoop");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.offloadExternalWakeup = offloadExternalWakeup;
    }

    public void submit(PacketPriority priority, Runnable action) {
        submit(priority, action, false);
    }

    /**
     * Publishes a send whose completion callback is a causal protocol boundary.
     * The next send is not invoked until {@link #complete()} is called from that
     * callback. Callers must use this only for terminal protocol transitions,
     * not ordinary write-completion listeners. This preserves vanilla's
     * pipeline-transition ordering without allowing a bulk write callback to
     * block later interactive traffic.
     */
    public void submit(
        PacketPriority priority, Runnable action, boolean awaitCompletion
    ) {
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(action, "action");
        if (closed.get()) return;

        Submission submission = new Submission(priority, action, awaitCompletion);
        inbox.add(submission);
        // close() publishes the terminal state before clearing the inbox. A
        // producer can race that clear after its first check, so remove the
        // exact late publication instead of retaining a dead connection's
        // packet graph indefinitely.
        if (closed.get()) {
            inbox.remove(submission);
            return;
        }
        if (!scheduled.compareAndSet(false, true)) return;
        try {
            if (inEventLoop.getAsBoolean()) {
                drainTurn();
            } else {
                executeExternal(this::drainTurn);
            }
        } catch (Throwable failure) {
            scheduled.set(false);
            throw failure;
        }
    }

    /**
     * Permanently closes this connection lane.
     *
     * <p>Netty invokes this from the channel close future. The terminal flag is
     * visible to every producer immediately; only the event-loop owner touches
     * the segmented consumer queues. Pending sends are deliberately discarded:
     * invoking their write listeners against a closed channel would cause
     * Minecraft's failure listener to enqueue a fallback packet onto the same
     * closed channel, producing a second avoidable failure for every send.</p>
     */
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        inbox.clear();
        if (inEventLoop.getAsBoolean()) {
            discardPending();
            return;
        }
        try {
            executeExternal(this::discardPending);
        } catch (Throwable ignored) {
            // The event loop itself may already be terminated. The terminal
            // flag and cleared MPSC inbox are sufficient; the connection owns
            // the remaining event-loop-only objects and can now be collected.
        }
    }

    private void drainTurn() {
        if (closed.get()) {
            discardPending();
            return;
        }
        draining = true;
        try {
            importSubmissions();
            Submission submission = pollNext();
            if (submission != null) {
                waitingForCompletion = submission.awaitCompletion;
                try {
                    OutboundPacketPriority.run(submission.priority, submission.action);
                } catch (Throwable failure) {
                    waitingForCompletion = false;
                    throw failure;
                }
            }
        } finally {
            draining = false;
            if (closed.get()) {
                discardPending();
            } else {
                scheduleContinuation();
            }
        }
    }

    /** Completes the causal send currently owning this connection lane. */
    public void complete() {
        if (closed.get()) return;
        if (!inEventLoop.getAsBoolean()) {
            executeExternal(this::complete);
            return;
        }
        if (!waitingForCompletion) return;
        waitingForCompletion = false;
        // A completed ChannelFuture may invoke its listener inline from the
        // send action. drainTurn's finally block owns continuation in that
        // case, avoiding two event-loop tasks for the same lane transition.
        if (!draining) scheduleContinuation();
    }

    private void scheduleContinuation() {
        if (closed.get()) {
            discardPending();
            return;
        }
        if (waitingForCompletion) return;
        importSubmissions();
        if (!pending.isEmpty()) {
            eventLoop.execute(this::drainTurn);
            return;
        }

        // Do not release ownership in the same turn that consumed the apparent
        // tail. Server/context producers commonly publish the next packet while
        // Netty is finishing this turn. An event-loop confirmation absorbs that
        // race without a timer, a packet batch, or an external selector wakeup.
        eventLoop.execute(this::confirmIdle);
    }

    private void confirmIdle() {
        if (closed.get()) {
            discardPending();
            return;
        }
        importSubmissions();
        if (!pending.isEmpty()) {
            drainTurn();
            return;
        }
        scheduled.set(false);
        if (inbox.isEmpty() || !scheduled.compareAndSet(false, true)) return;
        // We are already in the connection's event loop. If publication raced
        // the release above, claim and process it without scheduling a wakeup.
        drainTurn();
    }

    private void importSubmissions() {
        if (closed.get()) {
            inbox.clear();
            return;
        }
        Submission submission;
        while ((submission = inbox.poll()) != null) {
            PendingSegment segment = tailSegment();
            if (submission.priority == PacketPriority.BARRIER) {
                segment.barrier = submission;
                pending.addLast(new PendingSegment());
            } else if (submission.priority == PacketPriority.BULK) {
                segment.bulk.addLast(submission);
            } else {
                segment.interactive.addLast(submission);
            }
        }
    }

    private void executeExternal(Runnable task) {
        if (!offloadExternalWakeup) {
            eventLoop.execute(task);
            return;
        }
        NettySelectorWakeupLane.execute(() -> {
            try {
                eventLoop.execute(task);
            } catch (Throwable failure) {
                closed.set(true);
                inbox.clear();
                scheduled.set(false);
            }
        });
    }

    private void discardPending() {
        inbox.clear();
        pending.clear();
        waitingForCompletion = false;
        scheduled.set(false);
    }

    private Submission pollNext() {
        while (true) {
            PendingSegment segment = pending.peekFirst();
            if (segment == null) return null;
            Submission submission = segment.interactive.pollFirst();
            if (submission != null) {
                removeHeadIfDrained(segment);
                return submission;
            }
            submission = segment.bulk.pollFirst();
            if (submission != null) {
                removeHeadIfDrained(segment);
                return submission;
            }
            submission = segment.barrier;
            pending.removeFirst();
            if (submission != null) return submission;
        }
    }

    private void removeHeadIfDrained(PendingSegment segment) {
        if (segment.interactive.isEmpty() && segment.bulk.isEmpty()
            && segment.barrier == null) {
            pending.removeFirst();
        }
    }

    private PendingSegment tailSegment() {
        PendingSegment segment = pending.peekLast();
        if (segment != null) return segment;
        segment = new PendingSegment();
        pending.addLast(segment);
        return segment;
    }

    private record Submission(
        PacketPriority priority, Runnable action, boolean awaitCompletion
    ) {
    }

    private static final class PendingSegment {
        private final ArrayDeque<Submission> interactive = new ArrayDeque<>();
        private final ArrayDeque<Submission> bulk = new ArrayDeque<>();
        private Submission barrier;
    }
}
