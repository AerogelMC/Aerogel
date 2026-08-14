package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.block.BlockBreakAttemptEvent;
import dev.aerogel.api.event.block.BlockBreakEvent;
import dev.aerogel.api.event.block.BlockBrokenEvent;
import dev.aerogel.api.event.block.BlockMiningAbortEvent;
import dev.aerogel.api.event.block.BlockMiningProgressEvent;
import dev.aerogel.api.event.block.BlockMiningStartEvent;
import dev.aerogel.api.event.block.BlockMiningStopEvent;
import dev.aerogel.api.event.player.PlayerGameModeChangeEvent;
import dev.aerogel.api.event.player.PlayerInteractEvent;
import dev.aerogel.loader.event.EventHooks;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.server.level.ServerPlayerGameMode")
abstract class ServerPlayerGameModeMixin {
    @Unique private Object aerogel$breakingState;

    @Inject(
        method = "handleBlockBreakAction(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;"
            + "Lnet/minecraft/core/Direction;II)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void aerogel$onBlockAction(
        @Coerce Object position,
        @Coerce Object action,
        @Coerce Object direction,
        int maxBuildHeight,
        int sequence,
        CallbackInfo callbackInfo
    ) {
        Object player = EventHooks.field(this, "player");
        Object level = EventHooks.field(this, "level");
        Object state = EventHooks.call(level, "getBlockState", position);
        if (aerogel$isAction(action, "START_DESTROY_BLOCK")) {
            PlayerInteractEvent interaction = PlayerInteractEvent.block(
                EventHooks.cast(player), PlayerInteractEvent.Action.LEFT_CLICK,
                InteractionHand.MAIN_HAND, EventHooks.cast(position), EventHooks.cast(direction),
                null);
            EventHooks.post(interaction);

            BlockBreakAttemptEvent attempt = new BlockBreakAttemptEvent(
                EventHooks.cast(player), EventHooks.cast(level), EventHooks.cast(position),
                EventHooks.cast(state), EventHooks.cast(direction), sequence);
            EventHooks.post(attempt);
            if (interaction.isCancelled() || attempt.isCancelled()) {
                EventHooks.resyncBlock(player, level, position);
                callbackInfo.cancel();
            }
        } else if (aerogel$isAction(action, "STOP_DESTROY_BLOCK")) {
            BlockMiningStopEvent event = new BlockMiningStopEvent(
                EventHooks.cast(player), EventHooks.cast(level), EventHooks.cast(position),
                EventHooks.cast(state), EventHooks.cast(direction), sequence);
            EventHooks.post(event);
            if (event.isCancelled()) callbackInfo.cancel();
        } else if (aerogel$isAction(action, "ABORT_DESTROY_BLOCK")) {
            BlockMiningAbortEvent event = new BlockMiningAbortEvent(
                EventHooks.cast(player), EventHooks.cast(level), EventHooks.cast(position),
                EventHooks.cast(state), EventHooks.cast(direction), sequence);
            EventHooks.post(event);
            if (event.isCancelled()) callbackInfo.cancel();
        }
    }

    @Inject(
        method = "handleBlockBreakAction(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;"
            + "Lnet/minecraft/core/Direction;II)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;onHitBlock("
                + "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;"
                + "Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;"
                + "Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/phys/Vec3;"
                + "Lnet/minecraft/world/level/block/state/BlockState;Ljava/util/function/Consumer;)V"
        ),
        cancellable = true
    )
    private void aerogel$beforeMiningStarts(
        @Coerce Object position,
        @Coerce Object action,
        @Coerce Object direction,
        int maxBuildHeight,
        int sequence,
        CallbackInfo callbackInfo
    ) {
        Object player = EventHooks.field(this, "player");
        Object level = EventHooks.field(this, "level");
        BlockMiningStartEvent event = new BlockMiningStartEvent(
            EventHooks.cast(player), EventHooks.cast(level), EventHooks.cast(position),
            EventHooks.cast(EventHooks.call(level, "getBlockState", position)),
            EventHooks.cast(direction), sequence);
        EventHooks.post(event);
        if (event.isCancelled()) {
            EventHooks.resyncBlock(player, level, position);
            callbackInfo.cancel();
        }
    }

    @Inject(
        method = "incrementDestroyProgress(Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/core/BlockPos;I)F",
        at = @At("RETURN")
    )
    private void aerogel$afterMiningProgress(
        @Coerce Object state,
        @Coerce Object position,
        int startedAtTick,
        CallbackInfoReturnable<Float> callbackInfo
    ) {
        float progress = callbackInfo.getReturnValueF();
        Object player = EventHooks.field(this, "player");
        Object level = EventHooks.field(this, "level");
        BlockMiningProgressEvent event = new BlockMiningProgressEvent(
            EventHooks.cast(player), EventHooks.cast(level), EventHooks.cast(position),
            EventHooks.cast(state), progress, (int) (progress * 10.0F));
        EventHooks.post(event);
        if (event.isCancelled()) {
            EventHooks.resyncBlock(player, level, position);
            callbackInfo.setReturnValue(0.0F);
        }
    }

    @Inject(
        method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;playerWillDestroy("
                + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                + "Lnet/minecraft/world/level/block/state/BlockState;"
                + "Lnet/minecraft/world/entity/player/Player;)"
                + "Lnet/minecraft/world/level/block/state/BlockState;"
        ),
        cancellable = true
    )
    private void aerogel$beforeBlockBreak(
        @Coerce Object position,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        Object player = EventHooks.field(this, "player");
        Object level = EventHooks.field(this, "level");
        aerogel$breakingState = EventHooks.call(level, "getBlockState", position);
        BlockBreakEvent event = new BlockBreakEvent(
            EventHooks.cast(player), EventHooks.cast(level), EventHooks.cast(position),
            EventHooks.cast(aerogel$breakingState));
        EventHooks.post(event);
        if (event.isCancelled()) {
            aerogel$breakingState = null;
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(
        method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;destroy("
                + "Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;"
                + "Lnet/minecraft/world/level/block/state/BlockState;)V"
        )
    )
    private void aerogel$afterBlockWasRemoved(
        @Coerce Object position,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        Object previousState = aerogel$breakingState;
        aerogel$breakingState = null;
        if (previousState != null) {
            EventHooks.post(new BlockBrokenEvent(
                EventHooks.cast(EventHooks.field(this, "player")),
                EventHooks.cast(EventHooks.field(this, "level")), EventHooks.cast(position),
                EventHooks.cast(previousState)));
        }
    }

    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z", at = @At("RETURN"))
    private void aerogel$clearBlockBreakState(
        @Coerce Object position,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        aerogel$breakingState = null;
    }

    @Unique
    private boolean aerogel$isAction(Object action, String fieldName) {
        return action == EventHooks.staticField(
            this,
            "net.minecraft.network.protocol.game.ServerboundPlayerActionPacket$Action",
            fieldName);
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
            EventHooks.cast(EventHooks.field(this, "player")), EventHooks.cast(gameMode));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        }
    }
}
