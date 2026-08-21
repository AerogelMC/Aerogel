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
    long maximumExecutionNanos,
    int queuedTasks
) {
    public double averageExecutionMillis() {
        return measuredTicks == 0L
            ? 0.0D
            : totalExecutionNanos / (double) measuredTicks / 1_000_000.0D;
    }
}
