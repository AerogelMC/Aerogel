package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.PlayerNameTagService;
import dev.aerogel.loader.internal.ServerEntityBridge;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.ServerEntity")
abstract class ServerEntityMixin implements ServerEntityBridge {
    @Shadow @Final private Entity entity;
    @Shadow @Final private ServerEntity.Synchronizer synchronizer;

    @Invoker("sendDirtyEntityData")
    protected abstract void aerogel$sendDirtyEntityData();

    @Override
    public Entity aerogel$entity() {
        return entity;
    }

    @Override
    public void aerogel$publishDirtyState() {
        aerogel$sendDirtyEntityData();
        if (entity.hurtMarked) {
            entity.hurtMarked = false;
            synchronizer.sendToTrackingPlayersAndSelf(
                new ClientboundSetEntityMotionPacket(
                    entity.getId(), entity.getDeltaMovement()));
        }
    }

    @Inject(method = "addPairing(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("RETURN"))
    private void aerogel$syncPlayerNameTag(ServerPlayer viewer, CallbackInfo callbackInfo) {
        if (entity instanceof ServerPlayer target) {
            PlayerNameTagService.paired(viewer, target);
        }
    }
}
