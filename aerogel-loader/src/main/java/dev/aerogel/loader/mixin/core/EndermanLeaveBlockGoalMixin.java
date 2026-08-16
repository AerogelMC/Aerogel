package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.block.BlockStateChangeEvent;
import dev.aerogel.loader.event.BlockChangeContext;
import dev.aerogel.loader.event.EventHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal")
abstract class EndermanLeaveBlockGoalMixin {
    @Shadow @Final private EnderMan enderman;
    @Redirect(
        method = "tick()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock("
            + "Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;I)Z")
    )
    private boolean aerogel$endermanPlacesBlock(
        Level level, BlockPos position, BlockState state, int flags
    ) {
        if (!EventHooks.hasListeners(BlockStateChangeEvent.class)) {
            return level.setBlock(position, state, flags);
        }
        return BlockChangeContext.call(
            BlockStateChangeEvent.Reason.ENTITY_ACTION, enderman, position,
            enderman.position(), () -> level.setBlock(position, state, flags));
    }
}
