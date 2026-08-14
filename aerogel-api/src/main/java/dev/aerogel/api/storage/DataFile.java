package dev.aerogel.api.storage;

import dev.aerogel.api.Registration;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** A typed, in-memory value backed by one asynchronously managed file. */
public interface DataFile<T> extends Registration {
    /** Absolute normalized path of the managed file. */
    Path path();

    /** The single asynchronous initial load. Missing files use the supplied default value. */
    CompletableFuture<T> load();

    boolean loaded();

    /** Returns the live in-memory value after {@link #load()} completes successfully. */
    T value();

    /** Replaces the value and schedules an automatic save when enabled. */
    void set(T value);

    /** Atomically replaces the value using an immutable-style update. */
    T update(UnaryOperator<T> updater);

    /** Mutates the value under the file lock and marks it dirty even if the editor throws. */
    void edit(Consumer<T> editor);

    boolean dirty();

    /** Most recent load or save failure, cleared after a successful save. */
    Optional<Throwable> lastFailure();

    /** User-facing alias for {@link #flush()}. */
    default CompletableFuture<Void> save() {
        return flush();
    }

    /** Immediately persists every change visible when this method is called. */
    CompletableFuture<Void> flush();
}
