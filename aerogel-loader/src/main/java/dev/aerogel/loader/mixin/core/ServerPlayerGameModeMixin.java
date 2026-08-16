package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.block.BlockBreakAttemptEvent;
import dev.aerogel.api.event.block.BlockBreakEvent;
import dev.aerogel.api.event.block.BlockBrokenEvent;
import dev.aerogel.api.event.block.BlockMiningAbortEvent;
import dev.aerogel.api.event.block.BlockMiningProgressEvent;
import dev.aerogel.api.event.block.BlockMiningStartEvent;
import dev.aerogel.api.event.block.BlockMiningStopEvent;
import dev.aerogel.api.event.block.BlockStateChangeEvent;
import dev.aerogel.api.event.player.PlayerGameModeChangeEvent;
import dev.aerogel.api.event.player.PlayerInteractEvent;
import dev.aerogel.loader.event.EventHooks;
import dev.aerogel.loader.event.BlockChangeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@Mixin(targets = "net.minecraft.server.level.ServerPlayerGameMode")
abstract class ServerPlayerGameModeMixin {
    @Shadow @Final protected ServerPlayer player;
    @Shadow protected ServerLevel level;
    @Shadow public abstract GameType getGameModeForPlayer();
    @Shadow public abstract boolean changeGameModeForPlayer(GameType gameMode);
    @Unique private BlockState aerogel$breakingState;
    @Unique private boolean aerogel$gameModeOverride;

    @Inject(
        method = "handleBlockBreakAction(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;"
            + "Lnet/minecraft/core/Direction;II)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void aerogel$onBlockAction(
        BlockPos position,
        ServerboundPlayerActionPacket.Action action,
        Direction direction,
        int maxBuildHeight,
        int sequence,
        CallbackInfo callbackInfo
    ) {
        if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            boolean listenInteraction = EventHooks.hasListeners(PlayerInteractEvent.class);
            boolean listenAttempt = EventHooks.hasListeners(BlockBreakAttemptEvent.class);
            if (!listenInteraction && !listenAttempt) return;
            BlockState state = listenAttempt ? level.getBlockState(position) : null;
            boolean cancelled = false;
            if (listenInteraction) {
                PlayerInteractEvent interaction = PlayerInteractEvent.block(
                    player, PlayerInteractEvent.Action.LEFT_CLICK,
                    InteractionHand.MAIN_HAND, position, direction,
                    null);
                EventHooks.post(interaction);
                cancelled = interaction.isCancelled();
            }
            if (listenAttempt) {
                BlockBreakAttemptEvent attempt = new BlockBreakAttemptEvent(
                    player, level, position, state, direction, sequence);
                EventHooks.post(attempt);
                cancelled |= attempt.isCancelled();
            }
            if (cancelled) {
                EventHooks.resyncBlock(player, level, position);
                callbackInfo.cancel();
            }
        } else if (action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
            if (!EventHooks.hasListeners(BlockMiningStopEvent.class)) return;
            BlockMiningStopEvent event = new BlockMiningStopEvent(
                player, level, position, level.getBlockState(position), direction, sequence);
            EventHooks.post(event);
            if (event.isCancelled()) callbackInfo.cancel();
        } else if (action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
            if (!EventHooks.hasListeners(BlockMiningAbortEvent.class)) return;
            BlockMiningAbortEvent event = new BlockMiningAbortEvent(
                player, level, position, level.getBlockState(position), direction, sequence);
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
        BlockPos position,
        ServerboundPlayerActionPacket.Action action,
        Direction direction,
        int maxBuildHeight,
        int sequence,
        CallbackInfo callbackInfo
    ) {
        if (!EventHooks.hasListeners(BlockMiningStartEvent.class)) return;
        BlockMiningStartEvent event = new BlockMiningStartEvent(
            player, level, position, level.getBlockState(position), direction, sequence);
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
        BlockState state,
        BlockPos position,
        int startedAtTick,
        CallbackInfoReturnable<Float> callbackInfo
    ) {
        if (!EventHooks.hasListeners(BlockMiningProgressEvent.class)) return;
        float progress = callbackInfo.getReturnValueF();
        BlockMiningProgressEvent event = new BlockMiningProgressEvent(
            player, level, position, state, progress, (int) (progress * 10.0F));
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
        BlockPos position,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        boolean listenBefore = EventHooks.hasListeners(BlockBreakEvent.class);
        boolean listenAfter = EventHooks.hasListeners(BlockBrokenEvent.class);
        if (!listenBefore && !listenAfter) return;
        aerogel$breakingState = level.getBlockState(position);
        if (!listenBefore) return;
        BlockBreakEvent event = new BlockBreakEvent(
            player, level, position, aerogel$breakingState);
        EventHooks.post(event);
        if (event.isCancelled()) {
            aerogel$breakingState = null;
            callbackInfo.setReturnValue(false);
        }
    }

    @Redirect(
        method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;"
            + "removeBlock(Lnet/minecraft/core/BlockPos;Z)Z")
    )
    private boolean aerogel$removePlayerBrokenBlock(
        ServerLevel level, BlockPos position, boolean moving
    ) {
        if (!EventHooks.hasListeners(BlockStateChangeEvent.class)) {
            return level.removeBlock(position, moving);
        }
        return BlockChangeContext.call(
            BlockStateChangeEvent.Reason.PLAYER_BREAK, player, position, player.position(),
            () -> level.removeBlock(position, moving));
    }

    @Redirect(
        method = "useItemOn(Lnet/minecraft/server/level/ServerPlayer;"
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/InteractionHand;"
            + "Lnet/minecraft/world/phys/BlockHitResult;)"
            + "Lnet/minecraft/world/InteractionResult;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/"
            + "BlockState;useItemOn(Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)"
            + "Lnet/minecraft/world/InteractionResult;")
    )
    private InteractionResult aerogel$playerUsesItemOnBlock(
        BlockState state, ItemStack item, Level level,
        Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        return aerogel$playerInteraction(player, hitResult.getBlockPos(),
            () -> state.useItemOn(item, level, player, hand, hitResult));
    }

    @Redirect(
        method = "useItemOn(Lnet/minecraft/server/level/ServerPlayer;"
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/InteractionHand;"
            + "Lnet/minecraft/world/phys/BlockHitResult;)"
            + "Lnet/minecraft/world/InteractionResult;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/"
            + "BlockState;useWithoutItem(Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/world/phys/BlockHitResult;)"
            + "Lnet/minecraft/world/InteractionResult;")
    )
    private InteractionResult aerogel$playerUsesBlock(
        BlockState state, Level level, Player player, BlockHitResult hitResult
    ) {
        return aerogel$playerInteraction(player, hitResult.getBlockPos(),
            () -> state.useWithoutItem(level, player, hitResult));
    }

    @Redirect(
        method = "useItemOn(Lnet/minecraft/server/level/ServerPlayer;"
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/InteractionHand;"
            + "Lnet/minecraft/world/phys/BlockHitResult;)"
            + "Lnet/minecraft/world/InteractionResult;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;"
            + "useOn(Lnet/minecraft/world/item/context/UseOnContext;)"
            + "Lnet/minecraft/world/InteractionResult;")
    )
    private InteractionResult aerogel$playerUsesStackOnBlock(
        ItemStack item, UseOnContext context
    ) {
        return aerogel$playerInteraction(context.getPlayer(), context.getClickedPos(),
            () -> item.useOn(context));
    }

    @Unique
    private InteractionResult aerogel$playerInteraction(
        Player player, BlockPos position, Supplier<InteractionResult> action
    ) {
        if (!EventHooks.hasListeners(BlockStateChangeEvent.class)) return action.get();
        return BlockChangeContext.call(
            BlockStateChangeEvent.Reason.PLAYER_INTERACTION,
            player, position, player == null ? null : player.position(), action);
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
        BlockPos position,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        BlockState previousState = aerogel$breakingState;
        aerogel$breakingState = null;
        if (previousState != null && EventHooks.hasListeners(BlockBrokenEvent.class)) {
            EventHooks.post(new BlockBrokenEvent(
                player, level, position, previousState));
        }
    }

    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z", at = @At("RETURN"))
    private void aerogel$clearBlockBreakState(
        BlockPos position,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        aerogel$breakingState = null;
    }

    @Inject(
        method = "changeGameModeForPlayer(Lnet/minecraft/world/level/GameType;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void aerogel$beforeGameModeChange(
        GameType gameMode,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (aerogel$gameModeOverride) return;
        GameType previousGameMode = getGameModeForPlayer();
        if (previousGameMode == gameMode) return;
        if (!EventHooks.hasListeners(PlayerGameModeChangeEvent.class)) return;
        PlayerGameModeChangeEvent event = new PlayerGameModeChangeEvent(
            player, previousGameMode, gameMode);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        } else if (event.gameMode() != gameMode) {
            aerogel$gameModeOverride = true;
            try {
                callbackInfo.setReturnValue(changeGameModeForPlayer(event.gameMode()));
            } finally {
                aerogel$gameModeOverride = false;
            }
        }
    }
}
