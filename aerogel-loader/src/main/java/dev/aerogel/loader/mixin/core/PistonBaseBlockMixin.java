package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.block.PistonMoveEvent;
import dev.aerogel.api.event.block.BlockStateChangeEvent;
import dev.aerogel.loader.event.BlockChangeContext;
import dev.aerogel.loader.event.EventHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
        Level level, BlockPos position, Direction direction,
        boolean extending, CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (!EventHooks.hasListeners(PistonMoveEvent.class)) return;
        PistonMoveEvent event = new PistonMoveEvent(level, position, direction, extending);
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
        Level targetLevel, BlockPos changedPosition,
        BlockState state, int flags,
        Level level, BlockPos pistonPosition,
        Direction direction, boolean extending
    ) {
        if (!EventHooks.hasListeners(BlockStateChangeEvent.class)) {
            return targetLevel.setBlock(changedPosition, state, flags);
        }
        return BlockChangeContext.call(
            BlockStateChangeEvent.Reason.PISTON, null, pistonPosition, null,
            () -> targetLevel.setBlock(changedPosition, state, flags));
    }
}
