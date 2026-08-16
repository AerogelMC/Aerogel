package dev.aerogel.loader.internal;

/** Direct command-tree mutation bridge installed into Brigadier command nodes. */
public interface CommandNodeBridge {
    void aerogel$removeChild(String name);
}
