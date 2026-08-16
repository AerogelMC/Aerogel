package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.block.BlockStateChangeEvent;
import dev.aerogel.loader.event.BlockChangeContext;
import dev.aerogel.loader.event.EventHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(targets = "net.minecraft.world.level.Level")
abstract class LevelMixin {
    @Shadow public abstract BlockState getBlockState(BlockPos position);
    @Shadow public abstract boolean setBlock(
        BlockPos position, BlockState state, int flags, int recursionLeft);
    @Unique private boolean aerogel$blockStateOverridePending;
    @Unique private Object aerogel$blockStateOverridePosition;
    @Unique private Object aerogel$blockStateOverrideState;
    @Unique private int aerogel$blockStateOverrideFlags;
    @Unique private int aerogel$blockStateOverrideRecursion;

    @Inject(
        method = "setBlock(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"), cancellable = true
    )
    private void aerogel$blockStateChange(
        BlockPos position, BlockState state, int flags, int recursionLeft,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (aerogel$consumeBlockStateOverride(position, state, flags, recursionLeft)) return;
        if (!EventHooks.hasListeners(BlockStateChangeEvent.class)) return;
        BlockState previousState = getBlockState(position);
        if (Objects.equals(previousState, state)) return;
        BlockChangeContext.Context context = BlockChangeContext.current();
        BlockStateChangeEvent event = new BlockStateChangeEvent(
            (Level) (Object) this, position, previousState, state,
            flags, recursionLeft, context.reason(), context.sourceEntity(),
            context.sourcePosition(), context.sourceLocation());
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        } else if (event.state() != state || event.flags() != flags
            || event.recursionLeft() != recursionLeft) {
            aerogel$blockStateOverridePending = true;
            aerogel$blockStateOverridePosition = position;
            aerogel$blockStateOverrideState = event.state();
            aerogel$blockStateOverrideFlags = event.flags();
            aerogel$blockStateOverrideRecursion = event.recursionLeft();
            try {
                callbackInfo.setReturnValue(setBlock(
                    position, event.state(), event.flags(), event.recursionLeft()));
            } finally {
                aerogel$clearBlockStateOverride();
            }
        }
    }

    @Unique
    private boolean aerogel$consumeBlockStateOverride(
        Object position, Object state, int flags, int recursionLeft
    ) {
        if (!aerogel$blockStateOverridePending
            || !Objects.equals(aerogel$blockStateOverridePosition, position)
            || !Objects.equals(aerogel$blockStateOverrideState, state)
            || aerogel$blockStateOverrideFlags != flags
            || aerogel$blockStateOverrideRecursion != recursionLeft) {
            return false;
        }
        aerogel$clearBlockStateOverride();
        return true;
    }

    @Unique
    private void aerogel$clearBlockStateOverride() {
        aerogel$blockStateOverridePending = false;
        aerogel$blockStateOverridePosition = null;
        aerogel$blockStateOverrideState = null;
    }
}
