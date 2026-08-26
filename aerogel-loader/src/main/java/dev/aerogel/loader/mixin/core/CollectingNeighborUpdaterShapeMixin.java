package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.NativeTickCoordinator;
import dev.aerogel.loader.context.NeighborUpdateContinuation;
import dev.aerogel.loader.context.ScheduledTickQueryScope;
import dev.aerogel.loader.runtime.AerogelRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Routes the actual target of a cross-chunk shape update to its owning Context. */
@Mixin(targets = "net.minecraft.world.level.redstone.CollectingNeighborUpdater$ShapeUpdate")
abstract class CollectingNeighborUpdaterShapeMixin {
    @Redirect(
        method = "runNext(Lnet/minecraft/world/level/Level;)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/redstone/"
            + "NeighborUpdater;executeShapeUpdate(Lnet/minecraft/world/level/LevelAccessor;"
            + "Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;II)V")
    )
    private void aerogel$routeExactShapeUpdate(
        LevelAccessor level, Direction direction, BlockPos position,
        BlockPos neighborPosition, BlockState neighborState, int flags, int recursionLeft
    ) {
        if (!NativeTickCoordinator.isNativeWorker()
            || !(level instanceof ServerLevel serverLevel)
            || AerogelRuntime.isNeighborMutationThread(serverLevel, position)) {
            NeighborUpdater.executeShapeUpdate(
                level, direction, position, neighborPosition,
                neighborState, flags, recursionLeft);
            return;
        }
        Runnable update = () -> NeighborUpdater.executeShapeUpdate(
            level, direction, position, neighborPosition,
            neighborState, flags, recursionLeft);
        CollectingNeighborUpdater updater = NeighborUpdateContinuation.current();
        if (updater == null) {
            if (!AerogelRuntime.routeNeighborTask(
                serverLevel, position.immutable(), update)) update.run();
            return;
        }
        // Capture the immutable scheduled-tick query view before the source action
        // unwinds; only publication of this already-bound continuation is deferred.
        Runnable continuation = ScheduledTickQueryScope.propagate(
            NeighborUpdateContinuation.resumeAfter(updater, update));
        Runnable rejected = () ->
            NeighborUpdateContinuation.cancelSuspended(updater);
        NeighborUpdateContinuation.suspend(updater, () -> {
            if (!AerogelRuntime.routeNeighborTask(
                serverLevel, position.immutable(), continuation, rejected)) rejected.run();
        });
    }
}
