package dev.aerogel.api.scheduler;

public interface Scheduler {
    ScheduledTask run(Runnable action);
    ScheduledTask later(long delayTicks, Runnable action);
    ScheduledTask repeat(long initialDelayTicks, long periodTicks, Runnable action);
    ScheduledTask async(Runnable action);
    ScheduledTask asyncLater(long delayTicks, Runnable action);
}
