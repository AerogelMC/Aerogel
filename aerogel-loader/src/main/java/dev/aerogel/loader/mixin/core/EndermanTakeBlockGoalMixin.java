package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.block.BlockStateChangeEvent;
import dev.aerogel.loader.event.BlockChangeContext;
import dev.aerogel.loader.event.EventHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
abstract class EndermanTakeBlockGoalMixin {
    @Shadow @Final private EnderMan enderman;
    @Redirect(
        method = "tick()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;"
            + "removeBlock(Lnet/minecraft/core/BlockPos;Z)Z")
    )
    private boolean aerogel$endermanTakesBlock(
        Level level, BlockPos position, boolean moving
    ) {
        if (!EventHooks.hasListeners(BlockStateChangeEvent.class)) {
            return level.removeBlock(position, moving);
        }
        return BlockChangeContext.call(
            BlockStateChangeEvent.Reason.ENTITY_ACTION, enderman, position,
            enderman.position(), () -> level.removeBlock(position, moving));
    }
}
