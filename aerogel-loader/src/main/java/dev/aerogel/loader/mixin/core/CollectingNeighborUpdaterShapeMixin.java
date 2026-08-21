package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.NativeTickCoordinator;
import dev.aerogel.loader.runtime.AerogelRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.NeighborUpdater;
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
        Runnable update = () -> NeighborUpdater.executeShapeUpdate(
            level, direction, position, neighborPosition,
            neighborState, flags, recursionLeft);
        if (!(level instanceof ServerLevel serverLevel)
            || !NativeTickCoordinator.isNativeWorker()
            || AerogelRuntime.isBlockMutationThread(serverLevel, position)
            || !AerogelRuntime.routeBlockTask(serverLevel, position.immutable(), update)) {
            update.run();
        }
    }
}
