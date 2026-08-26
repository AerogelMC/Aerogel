package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.NativeTickCoordinator;
import dev.aerogel.loader.context.NeighborUpdateContinuation;
import dev.aerogel.loader.context.ScheduledTickQueryScope;
import dev.aerogel.loader.runtime.AerogelRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Routes the actual target of a cross-chunk neighbor update to its owning Context. */
@Mixin(targets = {
    "net.minecraft.world.level.redstone.CollectingNeighborUpdater$SimpleNeighborUpdate",
    "net.minecraft.world.level.redstone.CollectingNeighborUpdater$FullNeighborUpdate",
    "net.minecraft.world.level.redstone.CollectingNeighborUpdater$MultiNeighborUpdate"
})
abstract class CollectingNeighborUpdaterUpdateMixin {
    @Redirect(
        method = "runNext(Lnet/minecraft/world/level/Level;)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/redstone/"
            + "NeighborUpdater;executeUpdate(Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;"
            + "Lnet/minecraft/world/level/redstone/Orientation;Z)V")
    )
    private void aerogel$routeExactUpdate(
        Level level, BlockState state, BlockPos position, Block sourceBlock,
        Orientation orientation, boolean moved
    ) {
        if (!NativeTickCoordinator.isNativeWorker()
            || !(level instanceof ServerLevel serverLevel)
            || AerogelRuntime.isNeighborMutationThread(serverLevel, position)) {
            NeighborUpdater.executeUpdate(
                level, state, position, sourceBlock, orientation, moved);
            return;
        }
        Runnable update = () -> NeighborUpdater.executeUpdate(
            level, state, position, sourceBlock, orientation, moved);
        CollectingNeighborUpdater updater = NeighborUpdateContinuation.current();
        if (updater == null) {
            if (!AerogelRuntime.routeNeighborTask(
                serverLevel, position.immutable(), update)) update.run();
            return;
        }
        // Capture the immutable scheduled-tick query view while still inside the
        // source action. Publication is deliberately deferred until that action
        // unwinds, by which point its worker-local binding no longer exists.
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
