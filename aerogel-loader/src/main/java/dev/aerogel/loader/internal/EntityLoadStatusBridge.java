package dev.aerogel.loader.internal;

import it.unimi.dsi.fastutil.longs.LongConsumer;

public interface EntityLoadStatusBridge {
    void aerogel$loadStatusListener(LongConsumer listener);
}
