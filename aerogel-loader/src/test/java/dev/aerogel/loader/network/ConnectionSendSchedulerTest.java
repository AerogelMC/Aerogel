package dev.aerogel.loader.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConnectionSendSchedulerTest {
    @Test
    void inlineCompletionSchedulesExactlyOneContinuation() {
        TestEventLoop eventLoop = new TestEventLoop();
        ConnectionSendScheduler scheduler =
            new ConnectionSendScheduler(eventLoop, eventLoop::inEventLoop);
        List<Integer> order = new ArrayList<>();

        eventLoop.runInEventLoop(() -> scheduler.submit(
            PacketPriority.INTERACTIVE,
            () -> {
                order.add(1);
                scheduler.complete();
            },
            true));

        assertEquals(List.of(1), order);
        assertEquals(1, eventLoop.queuedTasks());
        eventLoop.runAll();
        assertEquals(List.of(1), order);
    }

    @Test
    void completionBarrierWaitsForCausalCallback() {
        TestEventLoop eventLoop = new TestEventLoop();
        ConnectionSendScheduler scheduler =
            new ConnectionSendScheduler(eventLoop, eventLoop::inEventLoop);
        List<Integer> order = new ArrayList<>();

        scheduler.submit(PacketPriority.INTERACTIVE, () -> order.add(1), true);
        scheduler.submit(PacketPriority.BARRIER, () -> order.add(2));

        eventLoop.runOne();
        assertEquals(List.of(1), order);
        eventLoop.runAll();
        assertEquals(List.of(1), order);

        eventLoop.runInEventLoop(scheduler::complete);
        eventLoop.runAll();
        assertEquals(List.of(1, 2), order);
    }

    @Test
    void interactiveSendPassesQueuedBulkBeforeEnteringNetty() {
        TestEventLoop eventLoop = new TestEventLoop();
        ConnectionSendScheduler scheduler =
            new ConnectionSendScheduler(eventLoop, eventLoop::inEventLoop);
        List<Integer> order = new ArrayList<>();

        scheduler.submit(PacketPriority.BULK, () -> order.add(1));
        scheduler.submit(PacketPriority.BULK, () -> order.add(2));
        scheduler.submit(PacketPriority.INTERACTIVE, () -> order.add(3));

        assertEquals(1, eventLoop.queuedTasks());
        eventLoop.runAll();
        assertEquals(List.of(3, 1, 2), order);
    }

    @Test
    void barrierPreservesProtocolOrder() {
        TestEventLoop eventLoop = new TestEventLoop();
        ConnectionSendScheduler scheduler =
            new ConnectionSendScheduler(eventLoop, eventLoop::inEventLoop);
        List<Integer> order = new ArrayList<>();

        scheduler.submit(PacketPriority.BULK, () -> order.add(1));
        scheduler.submit(PacketPriority.BARRIER, () -> order.add(2));
        scheduler.submit(PacketPriority.INTERACTIVE, () -> order.add(3));

        eventLoop.runAll();
        assertEquals(List.of(1, 2, 3), order);
    }

    @Test
    void oneSendRunsPerEventLoopTurn() {
        TestEventLoop eventLoop = new TestEventLoop();
        ConnectionSendScheduler scheduler =
            new ConnectionSendScheduler(eventLoop, eventLoop::inEventLoop);
        List<Integer> order = new ArrayList<>();

        scheduler.submit(PacketPriority.BULK, () -> order.add(1));
        scheduler.submit(PacketPriority.BULK, () -> order.add(2));

        eventLoop.runOne();
        assertEquals(List.of(1), order);
        assertEquals(1, eventLoop.queuedTasks());
        eventLoop.runOne();
        assertEquals(List.of(1, 2), order);
    }

    @Test
    void idleEventLoopCanSendImmediatelyWithoutAnExtraTurn() {
        TestEventLoop eventLoop = new TestEventLoop();
        ConnectionSendScheduler scheduler =
            new ConnectionSendScheduler(eventLoop, eventLoop::inEventLoop);
        List<Integer> order = new ArrayList<>();

        eventLoop.runInEventLoop(() ->
            scheduler.submit(PacketPriority.INTERACTIVE, () -> order.add(1)));

        assertEquals(List.of(1), order);
        // The queued task only confirms that no concurrent producer arrived;
        // the packet itself was still sent synchronously in this turn.
        assertEquals(1, eventLoop.queuedTasks());
        eventLoop.runAll();
        assertEquals(List.of(1), order);
    }

    @Test
    void submissionBeforeIdleConfirmationReusesExistingWakeup() {
        TestEventLoop eventLoop = new TestEventLoop();
        ConnectionSendScheduler scheduler =
            new ConnectionSendScheduler(eventLoop, eventLoop::inEventLoop);
        List<Integer> order = new ArrayList<>();

        scheduler.submit(PacketPriority.BULK, () -> order.add(1));
        assertEquals(1, eventLoop.queuedTasks());
        eventLoop.runOne();
        assertEquals(List.of(1), order);
        assertEquals(1, eventLoop.queuedTasks());

        scheduler.submit(PacketPriority.INTERACTIVE, () -> order.add(2));
        // Still just the idle-confirmation turn: the second producer did not
        // enqueue another cross-thread event-loop wakeup.
        assertEquals(1, eventLoop.queuedTasks());
        eventLoop.runOne();
        assertEquals(List.of(1, 2), order);
    }

    private static final class TestEventLoop implements java.util.concurrent.Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private boolean running;

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        private boolean inEventLoop() {
            return running;
        }

        private int queuedTasks() {
            return tasks.size();
        }

        private void runOne() {
            Runnable task = tasks.removeFirst();
            runInEventLoop(task);
        }

        private void runAll() {
            while (!tasks.isEmpty()) runOne();
        }

        private void runInEventLoop(Runnable action) {
            boolean previous = running;
            running = true;
            try {
                action.run();
            } finally {
                running = previous;
            }
        }
    }
}
