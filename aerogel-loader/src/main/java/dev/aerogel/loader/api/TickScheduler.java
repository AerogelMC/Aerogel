package dev.aerogel.loader.api;

import dev.aerogel.api.scheduler.ScheduledTask;
import dev.aerogel.api.scheduler.Scheduler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import dev.aerogel.loader.plugin.PluginFailures;

final class TickScheduler implements Scheduler, AutoCloseable {
    private static final ScheduledExecutorService ASYNC = Executors.newScheduledThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() / 2), runnable -> {
            Thread thread = new Thread(runnable, "Aerogel async task");
            thread.setDaemon(true);
            return thread;
        });

    private final PluginApiScope scope;
    private final Set<Task> tasks = ConcurrentHashMap.newKeySet();
    private volatile long currentTick;

    TickScheduler(PluginApiScope scope) { this.scope = scope; }

    @Override public ScheduledTask run(Runnable action) { return later(0, action); }
    @Override public ScheduledTask later(long delayTicks, Runnable action) {
        requireTicks(delayTicks, true);
        return add(new Task(false, currentTick + delayTicks, 0, action));
    }
    @Override public ScheduledTask repeat(long initialDelayTicks, long periodTicks, Runnable action) {
        requireTicks(initialDelayTicks, true);
        requireTicks(periodTicks, false);
        return add(new Task(false, currentTick + initialDelayTicks, periodTicks, action));
    }
    @Override public ScheduledTask async(Runnable action) { return asyncLater(0, action); }
    @Override public ScheduledTask asyncLater(long delayTicks, Runnable action) {
        requireTicks(delayTicks, true);
        Task task = add(new Task(true, -1, 0, action));
        task.future = ASYNC.schedule(task::execute, delayTicks * 50, TimeUnit.MILLISECONDS);
        return task;
    }

    void tick(long tick) {
        currentTick = tick;
        for (Task task : tasks.toArray(Task[]::new)) {
            if (!task.asynchronous && task.active() && tick >= task.nextTick) task.execute();
        }
    }

    private Task add(Task task) { tasks.add(task); return scope.own(task); }
    private static void requireTicks(long ticks, boolean zeroAllowed) {
        if (ticks < 0 || (!zeroAllowed && ticks == 0)) throw new IllegalArgumentException("Invalid tick delay: " + ticks);
    }

    @Override public void close() { for (Task task : tasks.toArray(Task[]::new)) task.close(); }

    private final class Task implements ScheduledTask {
        private final boolean asynchronous;
        private final long period;
        private final Runnable action;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private volatile long nextTick;
        private volatile java.util.concurrent.Future<?> future;

        private Task(boolean asynchronous, long nextTick, long period, Runnable action) {
            this.asynchronous = asynchronous;
            this.nextTick = nextTick;
            this.period = period;
            this.action = java.util.Objects.requireNonNull(action, "action");
        }

        private void execute() {
            if (!active.get()) return;
            try { action.run(); }
            catch (Throwable throwable) {
                PluginFailures.rethrowFatal(throwable);
                scope.logger().log(Level.SEVERE, "Scheduled task failed", throwable);
            }
            if (period > 0 && active.get()) nextTick = currentTick + period;
            else close();
        }

        @Override public boolean active() { return active.get(); }
        @Override public boolean asynchronous() { return asynchronous; }
        @Override public long nextTick() { return nextTick; }
        @Override public void close() {
            if (!active.compareAndSet(true, false)) return;
            tasks.remove(this);
            if (future != null) future.cancel(false);
        }
    }
}
