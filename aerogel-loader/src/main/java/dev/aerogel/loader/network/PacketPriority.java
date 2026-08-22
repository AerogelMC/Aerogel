package dev.aerogel.loader.network;

/** Ordering class used by one connection's non-preemptive compression lane. */
public enum PacketPriority {
    /** Latency-sensitive traffic that may pass queued bulk traffic. */
    INTERACTIVE,
    /** Chunk data and information whose order is tied to chunk publication. */
    BULK,
    /** Protocol transitions and explicit bundles that nothing may cross. */
    BARRIER
}
