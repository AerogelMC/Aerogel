package dev.aerogel.api.storage;

/** A load, encoding, decoding, path, or persistence failure in managed storage. */
public final class StorageException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
