package dev.aerogel.api.context;

/** Immutable measurements for one chunk context. */
public record ContextSnapshot(
    long epoch,
    String lifecycle,
    long submittedTasks,
    long completedTasks,
    long failedTasks,
    long staleTasks,
    long measuredTicks,
    long totalExecutionNanos,
    long recentExecutionNanos,
    long maximumExecutionNanos,
    int queuedTasks
) {
    /** Preserves the pre-26.2-9 source and binary constructor contract. */
    public ContextSnapshot(
        long epoch,
        String lifecycle,
        long submittedTasks,
        long completedTasks,
        long failedTasks,
        long staleTasks,
        long measuredTicks,
        long totalExecutionNanos,
        long maximumExecutionNanos,
        int queuedTasks
    ) {
        this(epoch, lifecycle, submittedTasks, completedTasks, failedTasks, staleTasks,
            measuredTicks, totalExecutionNanos, 0L, maximumExecutionNanos, queuedTasks);
    }

    public double averageExecutionMillis() {
        return measuredTicks == 0L
            ? 0.0D
            : totalExecutionNanos / (double) measuredTicks / 1_000_000.0D;
    }

    /** Work accumulated by the active local tick, or the last completed local tick. */
    public double recentExecutionMillis() {
        return recentExecutionNanos / 1_000_000.0D;
    }
}
