package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.TrackedEntityBridge;
import dev.aerogel.loader.internal.PlayerViewService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
abstract class TrackedEntityMixin implements TrackedEntityBridge {
    @Shadow @Final private Set<?> seenBy;
    @Shadow @Final private Entity entity;
    @Shadow public abstract void removePlayer(ServerPlayer player);
    @Shadow public abstract void updatePlayer(ServerPlayer player);

    @Override
    public boolean aerogel$isSeenBy(Object connection) {
        return seenBy.contains(connection);
    }

    @Override
    public void aerogel$removePlayer(ServerPlayer player) {
        removePlayer(player);
    }

    @Override
    public void aerogel$updatePlayer(ServerPlayer player) {
        updatePlayer(player);
    }

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void aerogel$keepHidden(ServerPlayer player, CallbackInfo callbackInfo) {
        if (PlayerViewService.isHidden(player, entity)) callbackInfo.cancel();
    }
}
