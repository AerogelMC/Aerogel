package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.block.BlockPlaceEvent;
import dev.aerogel.api.event.block.BlockStateChangeEvent;
import dev.aerogel.loader.event.BlockChangeContext;
import dev.aerogel.loader.event.EventHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.item.BlockItem")
abstract class BlockItemMixin {
    @Shadow protected abstract boolean placeBlock(BlockPlaceContext context, BlockState state);
    @Shadow private BlockState updateBlockStateFromTag(
        BlockPos position, Level level, ItemStack item, BlockState state) {
        throw new AssertionError();
    }

    @Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)"
        + "Lnet/minecraft/world/InteractionResult;", at = @At("HEAD"), cancellable = true)
    private void aerogel$place(
        BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> callbackInfo
    ) {
        if (!EventHooks.hasListeners(BlockPlaceEvent.class)) return;
        BlockPlaceEvent event = new BlockPlaceEvent((BlockItem) (Object) this, context);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Redirect(
        method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)"
            + "Lnet/minecraft/world/InteractionResult;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BlockItem;placeBlock("
            + "Lnet/minecraft/world/item/context/BlockPlaceContext;"
            + "Lnet/minecraft/world/level/block/state/BlockState;)Z")
    )
    private boolean aerogel$placeBlockWithContext(
        BlockItem blockItem, BlockPlaceContext context, BlockState state
    ) {
        if (!EventHooks.hasListeners(BlockStateChangeEvent.class)) {
            return placeBlock(context, state);
        }
        Player player = context.getPlayer();
        BlockPos position = context.getClickedPos();
        return BlockChangeContext.call(
            BlockStateChangeEvent.Reason.PLAYER_PLACE, player, position,
            player == null ? null : player.position(), () -> placeBlock(context, state));
    }

    @Redirect(
        method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)"
            + "Lnet/minecraft/world/InteractionResult;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BlockItem;"
            + "updateBlockStateFromTag(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/level/block/state/BlockState;)"
            + "Lnet/minecraft/world/level/block/state/BlockState;")
    )
    private BlockState aerogel$applyPlacementStateWithContext(
        BlockItem blockItem, BlockPos position, Level level,
        ItemStack item, BlockState state, BlockPlaceContext originalContext
    ) {
        if (!EventHooks.hasListeners(BlockStateChangeEvent.class)) {
            return updateBlockStateFromTag(position, level, item, state);
        }
        Player player = originalContext.getPlayer();
        return BlockChangeContext.call(
            BlockStateChangeEvent.Reason.PLAYER_PLACE, player, position,
            player == null ? null : player.position(),
            () -> updateBlockStateFromTag(position, level, item, state));
    }
}
