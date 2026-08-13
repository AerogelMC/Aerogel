package dev.aerogel.api.event;

/** A listener registration that can be removed before its plugin unloads. */
public interface EventRegistration extends AutoCloseable {
    boolean active();

    @Override
    void close();
}
