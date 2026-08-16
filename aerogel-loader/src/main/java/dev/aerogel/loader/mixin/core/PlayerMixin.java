package dev.aerogel.loader.mixin.core;

import com.mojang.authlib.GameProfile;
import dev.aerogel.api.event.player.PlayerFoodExhaustionEvent;
import dev.aerogel.loader.event.EventHooks;
import dev.aerogel.loader.internal.ServerPlayerDisplayNameBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

@Mixin(targets = "net.minecraft.world.entity.player.Player")
abstract class PlayerMixin {
    @Unique private boolean aerogel$foodExhaustionOverride;

    @Inject(method = "getGameProfile()Lcom/mojang/authlib/GameProfile;",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$packetProfile(CallbackInfoReturnable<GameProfile> callbackInfo) {
        if ((Object) this instanceof ServerPlayerDisplayNameBridge bridge) {
            GameProfile profile = bridge.aerogel$packetProfileOverride();
            if (profile != null) callbackInfo.setReturnValue(profile);
        }
    }

    @Inject(method = "getDisplayName()Lnet/minecraft/network/chat/Component;",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$displayName(CallbackInfoReturnable<Component> callbackInfo) {
        if ((Object) this instanceof ServerPlayerDisplayNameBridge bridge) {
            Component displayName = bridge.aerogel$displayNameOverride();
            if (displayName != null) callbackInfo.setReturnValue(displayName);
        }
    }

    @Inject(method = "causeFoodExhaustion(F)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$foodExhaustion(float amount, CallbackInfo callbackInfo) {
        if (aerogel$foodExhaustionOverride
            || !EventHooks.hasListeners(PlayerFoodExhaustionEvent.class)) return;
        PlayerFoodExhaustionEvent event = new PlayerFoodExhaustionEvent(
            (ServerPlayer) (Object) this, amount);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (Float.compare(event.amount(), amount) != 0) {
            aerogel$foodExhaustionOverride = true;
            try {
                ((ServerPlayer) (Object) this).causeFoodExhaustion(event.amount());
            } finally {
                aerogel$foodExhaustionOverride = false;
            }
            callbackInfo.cancel();
        }
    }
}
