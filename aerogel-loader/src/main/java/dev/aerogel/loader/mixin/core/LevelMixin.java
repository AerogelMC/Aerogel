package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.block.BlockStateChangeEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.Level")
abstract class LevelMixin {
    @Unique private boolean aerogel$blockStateOverride;

    @Inject(
        method = "setBlock(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"), cancellable = true
    )
    private void aerogel$blockStateChange(
        @Coerce Object position, @Coerce Object state, int flags, int recursionLeft,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (aerogel$blockStateOverride) return;
        BlockStateChangeEvent event = new BlockStateChangeEvent(
            EventHooks.cast(this), EventHooks.cast(position),
            EventHooks.cast(EventHooks.call(this, "getBlockState", position)),
            EventHooks.cast(state), flags, recursionLeft);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        } else if (event.state() != state || event.flags() != flags
            || event.recursionLeft() != recursionLeft) {
            aerogel$blockStateOverride = true;
            try {
                callbackInfo.setReturnValue((Boolean) EventHooks.call(this, "setBlock",
                    position, event.state(), event.flags(), event.recursionLeft()));
            } finally {
                aerogel$blockStateOverride = false;
            }
        }
    }
}
