package dev.aerogel.api.storage;

import java.time.Duration;
import java.util.Objects;

/** Immutable tuning options for a managed data file. */
public final class StorageOptions {
    private static final long DEFAULT_MAXIMUM_BYTES = 64L * 1024L * 1024L;
    private static final StorageOptions DEFAULTS = new StorageOptions(
        true, Duration.ofMillis(250), Duration.ofSeconds(5), DEFAULT_MAXIMUM_BYTES
    );
    private static final StorageOptions MANUAL = new StorageOptions(
        false, Duration.ZERO, Duration.ofSeconds(5), DEFAULT_MAXIMUM_BYTES
    );

    private final boolean automaticSaving;
    private final Duration saveDelay;
    private final Duration closeTimeout;
    private final long maximumBytes;

    private StorageOptions(
        boolean automaticSaving,
        Duration saveDelay,
        Duration closeTimeout,
        long maximumBytes
    ) {
        this.automaticSaving = automaticSaving;
        this.saveDelay = nonNegative(saveDelay, "saveDelay");
        this.closeTimeout = positive(closeTimeout, "closeTimeout");
        if (maximumBytes < 1) throw new IllegalArgumentException("maximumBytes must be positive");
        this.maximumBytes = maximumBytes;
    }

    public static StorageOptions defaults() {
        return DEFAULTS;
    }

    public static StorageOptions manual() {
        return MANUAL;
    }

    public boolean automaticSaving() {
        return automaticSaving;
    }

    public Duration saveDelay() {
        return saveDelay;
    }

    public Duration closeTimeout() {
        return closeTimeout;
    }

    public long maximumBytes() {
        return maximumBytes;
    }

    public StorageOptions withAutomaticSaving(boolean value) {
        return new StorageOptions(value, saveDelay, closeTimeout, maximumBytes);
    }

    public StorageOptions withSaveDelay(Duration value) {
        return new StorageOptions(automaticSaving, value, closeTimeout, maximumBytes);
    }

    public StorageOptions withCloseTimeout(Duration value) {
        return new StorageOptions(automaticSaving, saveDelay, value, maximumBytes);
    }

    public StorageOptions withMaximumBytes(long value) {
        return new StorageOptions(automaticSaving, saveDelay, closeTimeout, value);
    }

    private static Duration nonNegative(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative()) throw new IllegalArgumentException(name + " must not be negative");
        return value;
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
