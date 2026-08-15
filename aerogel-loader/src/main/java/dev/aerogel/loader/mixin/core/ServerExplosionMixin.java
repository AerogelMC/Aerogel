package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.block.BlockStateChangeEvent;
import dev.aerogel.loader.event.BlockChangeContext;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(targets = "net.minecraft.world.level.ServerExplosion")
abstract class ServerExplosionMixin {
    @Redirect(
        method = "explode()I",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerExplosion;"
            + "interactWithBlocks(Ljava/util/List;)V")
    )
    private void aerogel$explosionBlockChanges(
        @Coerce Object explosion, List<?> positions
    ) {
        aerogel$withExplosionContext(explosion, () ->
            EventHooks.call(explosion, "interactWithBlocks", positions));
    }

    @Redirect(
        method = "explode()I",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerExplosion;"
            + "createFire(Ljava/util/List;)V")
    )
    private void aerogel$explosionFireChanges(
        @Coerce Object explosion, List<?> positions
    ) {
        aerogel$withExplosionContext(explosion, () ->
            EventHooks.call(explosion, "createFire", positions));
    }

    private void aerogel$withExplosionContext(Object explosion, Runnable action) {
        Object source = EventHooks.field(explosion, "source");
        Object center = EventHooks.field(explosion, "center");
        BlockChangeContext.run(
            BlockStateChangeEvent.Reason.EXPLOSION, source, null, center, action);
    }
}
