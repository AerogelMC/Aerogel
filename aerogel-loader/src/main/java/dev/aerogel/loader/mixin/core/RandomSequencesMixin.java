package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.ConcurrentSnapshotMap;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.RandomSequences")
abstract class RandomSequencesMixin {
    @Shadow @Final @Mutable private Map<Object, Object> sequences;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$publishImmutableSequenceGenerations(CallbackInfo callbackInfo) {
        sequences = new ConcurrentSnapshotMap<>(sequences);
    }
}
