package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.block.BlockStateChangeEvent;
import dev.aerogel.loader.event.BlockChangeContext;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
abstract class EndermanTakeBlockGoalMixin {
    @Redirect(
        method = "tick()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;"
            + "removeBlock(Lnet/minecraft/core/BlockPos;Z)Z")
    )
    private boolean aerogel$endermanTakesBlock(
        @Coerce Object level, @Coerce Object position, boolean moving
    ) {
        Object enderman = EventHooks.field(this, "enderman");
        return BlockChangeContext.call(
            BlockStateChangeEvent.Reason.ENTITY_ACTION, enderman, position,
            EventHooks.call(enderman, "position"),
            () -> (Boolean) EventHooks.call(level, "removeBlock", position, moving));
    }
}
