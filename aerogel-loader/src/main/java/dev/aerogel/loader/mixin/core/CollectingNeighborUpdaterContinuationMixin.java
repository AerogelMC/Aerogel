package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.NeighborUpdateContinuation;
import dev.aerogel.loader.context.NeighborUpdaterAsyncBridge;
import dev.aerogel.loader.context.NeighborCausalGroup;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pauses, rather than drains past, a causal queue handed to another Context. */
@Mixin(targets = "net.minecraft.world.level.redstone.CollectingNeighborUpdater")
abstract class CollectingNeighborUpdaterContinuationMixin
    implements NeighborUpdaterAsyncBridge {
    @Unique private final java.util.concurrent.atomic.AtomicInteger
        aerogel$asyncNeighborContinuations = new java.util.concurrent.atomic.AtomicInteger();
    @Unique private final java.util.concurrent.atomic.AtomicReference<NeighborCausalGroup>
        aerogel$neighborCausalGroup = new java.util.concurrent.atomic.AtomicReference<>();

    @Override
    public void aerogel$beginAsyncNeighborContinuation() {
        aerogel$asyncNeighborContinuations.incrementAndGet();
    }

    @Override
    public void aerogel$endAsyncNeighborContinuation() {
        int remaining = aerogel$asyncNeighborContinuations.decrementAndGet();
        if (remaining < 0) {
            throw new IllegalStateException("Neighbor continuation completed twice");
        }
    }

    @Override
    public boolean aerogel$hasAsyncNeighborContinuation() {
        return aerogel$asyncNeighborContinuations.get() != 0;
    }

    @Override
    public void aerogel$neighborCausalGroup(NeighborCausalGroup group) {
        NeighborCausalGroup existing = aerogel$neighborCausalGroup.get();
        if (existing == null) {
            aerogel$neighborCausalGroup.compareAndSet(null, group);
            existing = aerogel$neighborCausalGroup.get();
        }
        if (existing.root() != group.root()) {
            throw new IllegalStateException("Neighbor updater changed causal group");
        }
    }

    @Inject(method = "shapeUpdate", at = @At("HEAD"), cancellable = true)
    private void aerogel$admitContendedShapeChain(
        Direction direction, BlockState neighborState, BlockPos position,
        BlockPos neighborPosition, int flags, int recursionLeft,
        CallbackInfo callback
    ) {
        CollectingNeighborUpdater updater = (CollectingNeighborUpdater) (Object) this;
        if (NeighborUpdateContinuation.deferChainAdmission(updater, position,
            () -> updater.shapeUpdate(direction, neighborState, position,
                neighborPosition, flags, recursionLeft))) callback.cancel();
    }

    @Inject(
        method = "neighborChanged(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/Block;"
            + "Lnet/minecraft/world/level/redstone/Orientation;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void aerogel$admitContendedSimpleChain(
        BlockPos position, Block block, Orientation orientation,
        CallbackInfo callback
    ) {
        CollectingNeighborUpdater updater = (CollectingNeighborUpdater) (Object) this;
        if (NeighborUpdateContinuation.deferChainAdmission(updater, position,
            () -> updater.neighborChanged(position, block, orientation))) callback.cancel();
    }

    @Inject(
        method = "neighborChanged(Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;"
            + "Lnet/minecraft/world/level/redstone/Orientation;Z)V",
        at = @At("HEAD"), cancellable = true
    )
    private void aerogel$admitContendedFullChain(
        BlockState state, BlockPos position, Block block,
        Orientation orientation, boolean moved, CallbackInfo callback
    ) {
        CollectingNeighborUpdater updater = (CollectingNeighborUpdater) (Object) this;
        if (NeighborUpdateContinuation.deferChainAdmission(updater, position,
            () -> updater.neighborChanged(state, position, block, orientation, moved))) {
            callback.cancel();
        }
    }

    @Inject(method = "updateNeighborsAtExceptFromFacing", at = @At("HEAD"), cancellable = true)
    private void aerogel$admitContendedMultiChain(
        BlockPos position, Block block, Direction skipped,
        Orientation orientation, CallbackInfo callback
    ) {
        CollectingNeighborUpdater updater = (CollectingNeighborUpdater) (Object) this;
        if (NeighborUpdateContinuation.deferChainAdmission(updater, position,
            () -> updater.updateNeighborsAtExceptFromFacing(
                position, block, skipped, orientation))) callback.cancel();
    }

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
    }
}
