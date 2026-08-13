package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.block.BlockBreakEvent;
import dev.aerogel.api.event.player.PlayerGameModeChangeEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.server.level.ServerPlayerGameMode")
abstract class ServerPlayerGameModeMixin {
    @Inject(
        method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void aerogel$beforeBlockBreak(
        @Coerce Object position,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        BlockBreakEvent event = new BlockBreakEvent(
            EventHooks.field(this, "player"), EventHooks.field(this, "level"), position);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(
        method = "changeGameModeForPlayer(Lnet/minecraft/world/level/GameType;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void aerogel$beforeGameModeChange(
        @Coerce Object gameMode,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        PlayerGameModeChangeEvent event = new PlayerGameModeChangeEvent(
            EventHooks.field(this, "player"), gameMode);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        }
    }
}
