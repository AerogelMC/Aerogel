package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.block.BlockStateChangeEvent;
import dev.aerogel.loader.event.BlockChangeContext;
import dev.aerogel.loader.event.EventHooks;
import dev.aerogel.loader.context.ContextRandomRouting;
import dev.aerogel.loader.context.ContextDispatchingRandomSource;
import dev.aerogel.loader.context.ContextNeighborRouting;
import dev.aerogel.loader.context.LevelNeighborUpdaterBridge;
import dev.aerogel.loader.context.NativeTickCoordinator;
import dev.aerogel.loader.runtime.AerogelRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(targets = "net.minecraft.world.level.Level")
abstract class LevelMixin implements LevelNeighborUpdaterBridge {
    @Shadow @Final @Mutable protected List<TickingBlockEntity> blockEntityTickers;
    @Shadow @Final protected CollectingNeighborUpdater neighborUpdater;
    @Shadow @Final @Mutable protected RandomSource random;
    @Shadow public abstract BlockState getBlockState(BlockPos position);
    @Shadow public abstract boolean isInValidBounds(BlockPos position);
    @Shadow public abstract LevelChunk getChunkAt(BlockPos position);
    @Shadow public abstract TickRateManager tickRateManager();
    @Shadow public abstract boolean shouldTickBlocksAt(BlockPos position);
    @Shadow public abstract boolean setBlock(
        BlockPos position, BlockState state, int flags, int recursionLeft);

    @Override
    public CollectingNeighborUpdater aerogel$neighborUpdater() {
        return neighborUpdater;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$installContextRandomFacade(CallbackInfo callback) {
        if (!(random instanceof ContextDispatchingRandomSource)) {
            random = new ContextDispatchingRandomSource((Level) (Object) this, random);
        }
        blockEntityTickers = new CopyOnWriteArrayList<>(blockEntityTickers);
    }

    @Inject(method = "tickBlockEntities()V", at = @At("HEAD"), cancellable = true)
    private void aerogel$parallelOwnedBlockEntities(CallbackInfo callback) {
        if (!((Object) this instanceof ServerLevel level)) return;
        AerogelRuntime.tickBlockEntities(
            level, blockEntityTickers, tickRateManager().runsNormally());
        callback.cancel();
    }

    @Inject(method = "getRandom()Lnet/minecraft/util/RandomSource;",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$contextRandom(CallbackInfoReturnable<RandomSource> callbackInfo) {
        RandomSource owned = ContextRandomRouting.current((Level) (Object) this);
        if (owned != null) callbackInfo.setReturnValue(owned);
    }

    /**
     * Vanilla rejects every server-side block-entity lookup away from the construction
     * thread. A context worker owning the exact block position is the replacement
     * mutation thread, so it must read that owning chunk directly.
     */
    @Inject(
        method = "getBlockEntity(Lnet/minecraft/core/BlockPos;)"
            + "Lnet/minecraft/world/level/block/entity/BlockEntity;",
        at = @At("HEAD"), cancellable = true
    )
    private void aerogel$getOwnedBlockEntity(
        BlockPos position, CallbackInfoReturnable<BlockEntity> callbackInfo
    ) {
        if (!NativeTickCoordinator.isNativeWorker()
            || !((Object) this instanceof ServerLevel level)
            || !isInValidBounds(position)
            || !AerogelRuntime.isBlockMutationThread(level, position)) return;
        callbackInfo.setReturnValue(getChunkAt(position).getBlockEntity(
            position, LevelChunk.EntityCreationType.IMMEDIATE));
    }

    @Redirect(
        method = "neighborShapeChanged(Lnet/minecraft/core/Direction;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;II)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;"
            + "neighborUpdater:Lnet/minecraft/world/level/redstone/CollectingNeighborUpdater;")
    )
    private CollectingNeighborUpdater aerogel$contextNeighborUpdater(
        Level ignored, Direction direction, BlockPos position,
        BlockPos neighborPosition, BlockState neighborState, int flags, int recursionLeft
    ) {
        return ContextNeighborRouting.current(
            (Level) (Object) this, neighborUpdater, position);
    }

    @Inject(
        method = "updateNeighbourForOutputSignal(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/Block;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void aerogel$routeOutputSignalUpdate(
        BlockPos position, Block sourceBlock, CallbackInfo callback
    ) {
        if ((Object) this instanceof ServerLevel level
            && AerogelRuntime.routeOutputSignalUpdate(
                level, position.immutable(), sourceBlock)) {
            callback.cancel();
        }
    }


    @Unique
    private static final class AerogelBlockStateOverride {
        boolean pending;
        Object position;
        Object state;
        int flags;
        int recursion;
    }

    @Unique
    private static final ThreadLocal<AerogelBlockStateOverride> AEROGEL$BLOCK_STATE_OVERRIDE =
        ThreadLocal.withInitial(AerogelBlockStateOverride::new);
    @Unique
    private static final AtomicInteger AEROGEL$PENDING_BLOCK_STATE_OVERRIDES =
        new AtomicInteger();

    @Inject(
        method = "setBlock(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"), cancellable = true
    )
    private void aerogel$blockStateChange(
        BlockPos position, BlockState state, int flags, int recursionLeft,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (NativeTickCoordinator.isNativeWorker()) {
            if ((Object) this instanceof ServerLevel level
                && AerogelRuntime.isBlockMutationThread(level, position)) return;
            if ((Object) this instanceof ServerLevel level) {
                BlockPos target = position.immutable();
                if (AerogelRuntime.routeBlockTask(level, target,
                    () -> level.setBlock(target, state, flags, recursionLeft))) {
                    callbackInfo.setReturnValue(true);
                    return;
                }
            }
            throw new IllegalStateException(
                "Block mutation escaped its exact owning Context at " + position);
        }
        if (AEROGEL$PENDING_BLOCK_STATE_OVERRIDES.get() != 0
            && aerogel$consumeBlockStateOverride(
                position, state, flags, recursionLeft)) return;
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
            AerogelBlockStateOverride override = AEROGEL$BLOCK_STATE_OVERRIDE.get();
            override.pending = true;
            override.position = position;
            override.state = event.state();
            override.flags = event.flags();
            override.recursion = event.recursionLeft();
            AEROGEL$PENDING_BLOCK_STATE_OVERRIDES.incrementAndGet();
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
        AerogelBlockStateOverride override = AEROGEL$BLOCK_STATE_OVERRIDE.get();
        if (!override.pending
            || !Objects.equals(override.position, position)
            || !Objects.equals(override.state, state)
            || override.flags != flags
            || override.recursion != recursionLeft) {
            return false;
        }
        aerogel$clearBlockStateOverride();
        return true;
    }

    @Unique
    private void aerogel$clearBlockStateOverride() {
        AerogelBlockStateOverride override = AEROGEL$BLOCK_STATE_OVERRIDE.get();
        if (!override.pending) return;
        override.pending = false;
        override.position = null;
        override.state = null;
        AEROGEL$PENDING_BLOCK_STATE_OVERRIDES.decrementAndGet();
    }

}
