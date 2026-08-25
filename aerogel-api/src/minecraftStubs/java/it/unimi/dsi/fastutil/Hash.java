package it.unimi.dsi.fastutil;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public interface Hash {
    interface Strategy<K> {
        int hashCode(K object);
        boolean equals(K first, K second);
    }
}
