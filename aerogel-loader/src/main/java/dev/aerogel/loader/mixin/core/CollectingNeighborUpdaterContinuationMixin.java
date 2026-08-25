package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.NeighborUpdateContinuation;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pauses, rather than drains past, a causal queue handed to another Context. */
@Mixin(targets = "net.minecraft.world.level.redstone.CollectingNeighborUpdater")
abstract class CollectingNeighborUpdaterContinuationMixin {
    private static final String RUN_NEXT =
        "Lnet/minecraft/world/level/redstone/CollectingNeighborUpdater$NeighborUpdates;"
            + "runNext(Lnet/minecraft/world/level/Level;)Z";

    @Inject(method = "runUpdates", at = @At(value = "INVOKE", target = RUN_NEXT))
    private void aerogel$enterCausalUpdate(CallbackInfo callback) {
        NeighborUpdateContinuation.enter((CollectingNeighborUpdater) (Object) this);
    }

    @Inject(
        method = "runUpdates",
        at = @At(value = "INVOKE", target = RUN_NEXT, shift = At.Shift.AFTER),
        cancellable = true
    )
    private void aerogel$pauseCausalUpdate(CallbackInfo callback) {
        CollectingNeighborUpdater updater = (CollectingNeighborUpdater) (Object) this;
        NeighborUpdateContinuation.leave(updater);
        if (NeighborUpdateContinuation.pauseAfterCompletedStep(updater)) callback.cancel();
    }

    @Inject(method = "runUpdates", at = @At("RETURN"))
    private void aerogel$leaveCausalUpdate(CallbackInfo callback) {
        CollectingNeighborUpdater updater = (CollectingNeighborUpdater) (Object) this;
        NeighborUpdateContinuation.leave(updater);
        NeighborUpdateContinuation.consumeSuspension(updater);
    }
}
