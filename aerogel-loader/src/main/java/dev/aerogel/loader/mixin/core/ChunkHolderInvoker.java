package dev.aerogel.loader.mixin.core;

import java.util.concurrent.Executor;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.server.level.ChunkHolder")
public interface ChunkHolderInvoker {
    @Invoker("updateFutures")
    void aerogel$updateFutures(ChunkMap chunkMap, Executor mainThreadExecutor);
}
