package dev.aerogel.loader.internal;

import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.server.level.ServerLevel;

public interface EntityLoadStatusBridge {
    void aerogel$loadStatusListener(LongConsumer listener);
    void aerogel$level(ServerLevel level);
}
