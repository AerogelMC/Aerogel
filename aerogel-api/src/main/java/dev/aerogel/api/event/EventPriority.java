package dev.aerogel.api.event;

/** Listener order. MONITOR runs last and must not change cancellation state. */
public enum EventPriority {
    EARLY,
    NORMAL,
    LATE,
    MONITOR
}
