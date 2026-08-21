package dev.aerogel.loader.internal;

/** Stores the last spatially committed Context owner of an entity. */
public interface EntityContextOwnerBridge {
    Object aerogel$contextOwner();
    void aerogel$contextOwner(Object owner);
    boolean aerogel$compareAndSetContextOwner(Object expected, Object updated);
}
