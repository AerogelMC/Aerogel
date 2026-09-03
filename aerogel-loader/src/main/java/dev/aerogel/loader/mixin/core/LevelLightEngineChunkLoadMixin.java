package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.worldgen.ChunkLoadAssemblyScope;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves loaded light data to the keyed light owner without blocking assembly. */
@Mixin(targets = "net.minecraft.world.level.lighting.LevelLightEngine")
abstract class LevelLightEngineChunkLoadMixin {
    @Inject(method = "retainData", at = @At("HEAD"), cancellable = true)
    private void aerogel$deferLoadedLightRetention(
        ChunkPos position, boolean retain, CallbackInfo callback
    ) {
        LevelLightEngine self = (LevelLightEngine) (Object) this;
        if (ChunkLoadAssemblyScope.deferLight(() -> self.retainData(position, retain))) {
            callback.cancel();
        }
    }

    @Inject(method = "queueSectionData", at = @At("HEAD"), cancellable = true)
    private void aerogel$deferLoadedSectionLight(
        LightLayer layer, SectionPos section, DataLayer data, CallbackInfo callback
    ) {
        LevelLightEngine self = (LevelLightEngine) (Object) this;
        if (ChunkLoadAssemblyScope.deferLight(
            () -> self.queueSectionData(layer, section, data))) {
            callback.cancel();
        }
    }
}
