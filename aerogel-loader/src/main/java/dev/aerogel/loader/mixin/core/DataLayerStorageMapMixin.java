package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.SegmentedLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents light snapshot clones from becoming G1 humongous allocations. */
@Mixin(targets = "net.minecraft.world.level.lighting.DataLayerStorageMap")
abstract class DataLayerStorageMapMixin {
    @Shadow @Final @Mutable
    protected Long2ObjectOpenHashMap<Object> map;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$segmentSnapshotBacking(CallbackInfo callback) {
        map = SegmentedLong2ObjectMap.wrap(map);
    }
}
