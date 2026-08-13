package dev.aerogel.api;

/**
 * A handle owned by its plugin and released automatically when that plugin unloads.
 * Call {@link #close()} only when it needs to be released earlier; closing is idempotent.
 */
public interface Registration {
    boolean active();

    void close();
}
