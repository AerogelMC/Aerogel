package dev.aerogel.loader.context;

/** The smallest mutable owner that can correctly publish a native side effect. */
public enum CommitScope {
    /** The exact single- or multi-chunk ownership lease of the current transaction. */
    CONTEXT,
    /** One dimension. Different dimensions may publish concurrently. */
    WORLD,
    /** Truly server-wide state that must remain on the Minecraft server thread. */
    SERVER
}
