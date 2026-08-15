package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.block.PistonMoveEvent;
import dev.aerogel.api.event.block.BlockStateChangeEvent;
import dev.aerogel.loader.event.BlockChangeContext;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.block.piston.PistonBaseBlock")
abstract class PistonBaseBlockMixin {
    @Inject(
        method = "moveBlocks(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/core/Direction;Z)Z",
        at = @At("HEAD"), cancellable = true
    )
    private void aerogel$pistonMove(
        @Coerce Object level, @Coerce Object position, @Coerce Object direction,
        boolean extending, CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        PistonMoveEvent event = new PistonMoveEvent(
            EventHooks.cast(level), EventHooks.cast(position), EventHooks.cast(direction), extending);
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.setReturnValue(false);
    }

    @Redirect(
        method = "moveBlocks(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/core/Direction;Z)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock("
            + "Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;I)Z")
    )
    private boolean aerogel$pistonBlockChange(
        @Coerce Object targetLevel, @Coerce Object changedPosition,
        @Coerce Object state, int flags,
        @Coerce Object level, @Coerce Object pistonPosition,
        @Coerce Object direction, boolean extending
    ) {
        return BlockChangeContext.call(
            BlockStateChangeEvent.Reason.PISTON, null, pistonPosition, null,
            () -> (Boolean) EventHooks.call(
                targetLevel, "setBlock", changedPosition, state, flags));
    }
}
