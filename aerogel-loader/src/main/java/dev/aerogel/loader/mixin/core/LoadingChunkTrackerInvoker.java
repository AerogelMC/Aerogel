package dev.aerogel.loader.mixin.core;

import net.minecraft.server.level.LoadingChunkTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.server.level.LoadingChunkTracker")
public interface LoadingChunkTrackerInvoker {
    @Invoker("setLevel")
    void aerogel$setLevel(long chunkKey, int level);
}
