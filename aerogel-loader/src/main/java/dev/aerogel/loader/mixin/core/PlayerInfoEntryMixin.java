package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.ServerPlayerDisplayNameBridge;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry")
abstract class PlayerInfoEntryMixin {
    @Shadow @Final @Mutable private boolean listed;

    @Inject(method = "<init>(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("RETURN"))
    private void aerogel$tabListVisibility(ServerPlayer player, CallbackInfo callbackInfo) {
        if (player instanceof ServerPlayerDisplayNameBridge bridge) {
            listed = !bridge.aerogel$tabListHidden();
        }
    }
}
