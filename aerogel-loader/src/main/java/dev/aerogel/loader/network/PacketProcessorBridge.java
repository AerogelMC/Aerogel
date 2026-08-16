package dev.aerogel.loader.network;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Internal bridge added to Minecraft's packet processor by Mixin. */
public interface PacketProcessorBridge {
    void aerogel$configureIdlePump(BooleanSupplier idle, Consumer<Runnable> executor);

    void aerogel$requestIdlePump();
}
