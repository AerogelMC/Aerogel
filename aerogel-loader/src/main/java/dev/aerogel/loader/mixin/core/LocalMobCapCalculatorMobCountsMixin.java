package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.LocalMobCapReservation;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicIntegerArray;

@Mixin(targets = "net.minecraft.world.level.LocalMobCapCalculator$MobCounts")
abstract class LocalMobCapCalculatorMobCountsMixin {
    @Unique
    private AtomicIntegerArray aerogel$counts;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$initializeCounts(CallbackInfo callback) {
        aerogel$counts = new AtomicIntegerArray(MobCategory.values().length);
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void aerogel$atomicAdd(MobCategory category, CallbackInfo callback) {
        if (!LocalMobCapReservation.captureIncrement(aerogel$counts, category)) {
            aerogel$counts.incrementAndGet(category.ordinal());
        }
        callback.cancel();
    }

    @Inject(method = "canSpawn", at = @At("HEAD"), cancellable = true)
    private void aerogel$atomicCanSpawn(
        MobCategory category, CallbackInfoReturnable<Boolean> callback
    ) {
        callback.setReturnValue(
            aerogel$counts.get(category.ordinal()) < category.getMaxInstancesPerChunk());
    }
}
