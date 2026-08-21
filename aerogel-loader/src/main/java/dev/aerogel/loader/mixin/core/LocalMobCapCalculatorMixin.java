package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.ConcurrentLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(targets = "net.minecraft.world.level.LocalMobCapCalculator")
abstract class LocalMobCapCalculatorMixin {
    @Shadow @Final @Mutable private Long2ObjectMap<Object> playersNearChunk;
    @Shadow @Final @Mutable private Map<Object, Object> playerMobCounts;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$useConcurrentIndexes(CallbackInfo callback) {
        playersNearChunk = new ConcurrentLong2ObjectMap<>();
        playerMobCounts = new ConcurrentHashMap<>();
    }
}
