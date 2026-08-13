package dev.aerogel.api.event;

/**
 * A listener owned by its plugin and removed automatically when that plugin unloads.
 * Call {@link #close()} only to remove it earlier.
 */
public interface EventRegistration {
    boolean active();

    void close();
}
