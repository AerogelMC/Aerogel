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
 * Barriers split the queue into ordered segments and can never be crossed.</p>
 */
public final class ConnectionSendScheduler {
    private final Executor eventLoop;
    private final BooleanSupplier inEventLoop;
    private final ConcurrentLinkedQueue<Submission> inbox =
        new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();

    // Accessed only by the connection's event-loop thread.
    private final ArrayDeque<PendingSegment> pending = new ArrayDeque<>();

    public ConnectionSendScheduler(EventLoop eventLoop) {
        this(eventLoop, eventLoop::inEventLoop);
    }

    ConnectionSendScheduler(Executor eventLoop, BooleanSupplier inEventLoop) {
        this.eventLoop = Objects.requireNonNull(eventLoop, "eventLoop");
        this.inEventLoop = Objects.requireNonNull(inEventLoop, "inEventLoop");
    }

    public void submit(PacketPriority priority, Runnable action) {
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(action, "action");
        inbox.add(new Submission(priority, action));
        if (!scheduled.compareAndSet(false, true)) return;
        try {
            if (inEventLoop.getAsBoolean()) {
                drainTurn();
            } else {
                eventLoop.execute(this::drainTurn);
            }
        } catch (Throwable failure) {
            scheduled.set(false);
            throw failure;
        }
    }

    private void drainTurn() {
        try {
            importSubmissions();
            Submission submission = pollNext();
            if (submission != null) {
                OutboundPacketPriority.run(submission.priority, submission.action);
            }
        } finally {
            scheduleContinuation();
        }
    }

    private void scheduleContinuation() {
        importSubmissions();
        if (!pending.isEmpty()) {
            eventLoop.execute(this::drainTurn);
            return;
        }

        scheduled.set(false);
        if (inbox.isEmpty() || !scheduled.compareAndSet(false, true)) return;
        eventLoop.execute(this::drainTurn);
    }

    private void importSubmissions() {
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

    private record Submission(PacketPriority priority, Runnable action) {
    }

    private static final class PendingSegment {
        private final ArrayDeque<Submission> interactive = new ArrayDeque<>();
        private final ArrayDeque<Submission> bulk = new ArrayDeque<>();
        private Submission barrier;
    }
}
