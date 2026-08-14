package dev.aerogel.api.storage;

/** Encodes and decodes one stored value. Implementations must be thread-safe. */
public interface DataCodec<T> {
    byte[] encode(T value) throws Exception;

    T decode(byte[] encoded) throws Exception;
}
