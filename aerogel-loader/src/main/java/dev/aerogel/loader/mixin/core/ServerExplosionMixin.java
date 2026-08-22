package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.block.BlockStateChangeEvent;
import dev.aerogel.loader.event.BlockChangeContext;
import dev.aerogel.loader.event.EventHooks;
import dev.aerogel.loader.runtime.AerogelRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.ArrayList;

@Mixin(targets = "net.minecraft.world.level.ServerExplosion")
abstract class ServerExplosionMixin {
    @Shadow @Final private Entity source;
    @Shadow @Final private Vec3 center;
    @Shadow @Final private ServerLevel level;

    @Invoker("interactWithBlocks")
    protected abstract void aerogel$interactWithBlocks(List<BlockPos> positions);

    @Invoker("createFire")
    protected abstract void aerogel$createFire(List<BlockPos> positions);

    @Redirect(
        method = "explode()I",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerExplosion;"
            + "interactWithBlocks(Ljava/util/List;)V")
    )
    private void aerogel$explosionBlockChanges(
        ServerExplosion explosion, List<BlockPos> positions
    ) {
        List<BlockPos> targets = aerogel$immutablePositionCopy(positions);
        Runnable changes = () -> aerogel$withExplosionContext(
            () -> aerogel$interactWithBlocks(targets));
        if (!AerogelRuntime.routeBlockEffects(level, targets, changes)) changes.run();
    }

    @Redirect(
        method = "explode()I",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerExplosion;"
            + "createFire(Ljava/util/List;)V")
    )
    private void aerogel$explosionFireChanges(
        ServerExplosion explosion, List<BlockPos> positions
    ) {
        List<BlockPos> targets = aerogel$immutablePositionCopy(positions);
        Runnable changes = () -> aerogel$withExplosionContext(
            () -> aerogel$createFire(targets));
        if (!AerogelRuntime.routeBlockEffects(level, targets, changes)) changes.run();
    }

    private static List<BlockPos> aerogel$immutablePositionCopy(List<BlockPos> positions) {
        List<BlockPos> copy = new ArrayList<>(positions.size());
        for (BlockPos position : positions) copy.add(position.immutable());
        return copy;
    }

    private void aerogel$withExplosionContext(Runnable action) {
        if (!EventHooks.hasListeners(BlockStateChangeEvent.class)) {
            action.run();
            return;
        }
        BlockChangeContext.run(
            BlockStateChangeEvent.Reason.EXPLOSION, source, null, center, action);
    }
}
