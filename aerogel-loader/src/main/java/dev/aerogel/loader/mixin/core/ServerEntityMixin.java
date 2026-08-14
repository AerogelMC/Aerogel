package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.PlayerNameTagService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.ServerEntity")
abstract class ServerEntityMixin {
    @Shadow @Final private Entity entity;

    @Inject(method = "addPairing(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("RETURN"))
    private void aerogel$syncPlayerNameTag(ServerPlayer viewer, CallbackInfo callbackInfo) {
        if (entity instanceof ServerPlayer target) {
            PlayerNameTagService.paired(viewer, target);
        }
    }
}
