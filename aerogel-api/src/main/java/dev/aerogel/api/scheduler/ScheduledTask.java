package dev.aerogel.api.scheduler;

import dev.aerogel.api.Registration;

public interface ScheduledTask extends Registration {
    boolean asynchronous();
    long nextTick();
}
