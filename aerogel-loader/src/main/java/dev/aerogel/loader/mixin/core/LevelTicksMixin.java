package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.NativeTickCoordinator;
import dev.aerogel.loader.context.ConcurrentIngress;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;

@Mixin(targets = "net.minecraft.world.ticks.LevelTicks")
abstract class LevelTicksMixin<T> {
    @Shadow public abstract void schedule(ScheduledTick<T> tick);
    @Unique private ConcurrentIngress<ScheduledTick<T>> aerogel$scheduledTicks;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$initializeIngress(
        LongPredicate tickCheck, CallbackInfo callback
    ) {
        aerogel$scheduledTicks = new ConcurrentIngress<>();
    }

    @Inject(method = "schedule(Lnet/minecraft/world/ticks/ScheduledTick;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$commitScheduledTick(ScheduledTick<T> tick, CallbackInfo callback) {
        if (!NativeTickCoordinator.isNativeWorker()) return;
        aerogel$scheduledTicks.offer(tick);
        callback.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void aerogel$mergeOwnedSchedules(
        long gameTime, int maximumTicks, BiConsumer<BlockPos, T> ticker,
        CallbackInfo callback
    ) {
        aerogel$scheduledTicks.drain(this::schedule);
    }
}
