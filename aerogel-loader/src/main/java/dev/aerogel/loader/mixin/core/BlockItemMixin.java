package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.block.BlockPlaceEvent;
import dev.aerogel.api.event.block.BlockStateChangeEvent;
import dev.aerogel.loader.event.BlockChangeContext;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.item.BlockItem")
abstract class BlockItemMixin {
    @Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)"
        + "Lnet/minecraft/world/InteractionResult;", at = @At("HEAD"), cancellable = true)
    private void aerogel$place(@Coerce Object context, CallbackInfoReturnable<Object> callbackInfo) {
        BlockPlaceEvent event = new BlockPlaceEvent(EventHooks.cast(this), EventHooks.cast(context));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(EventHooks.staticField(
                this, "net.minecraft.world.InteractionResult", "FAIL"));
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
        @Coerce Object blockItem, @Coerce Object context, @Coerce Object state
    ) {
        Object player = EventHooks.call(context, "getPlayer");
        Object position = EventHooks.call(context, "getClickedPos");
        Object location = player == null ? null : EventHooks.call(player, "position");
        return BlockChangeContext.call(
            BlockStateChangeEvent.Reason.PLAYER_PLACE, player, position, location,
            () -> (Boolean) EventHooks.call(blockItem, "placeBlock", context, state));
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
    @Coerce
    private Object aerogel$applyPlacementStateWithContext(
        @Coerce Object blockItem, @Coerce Object position, @Coerce Object level,
        @Coerce Object item, @Coerce Object state, @Coerce Object originalContext
    ) {
        Object player = EventHooks.call(originalContext, "getPlayer");
        Object location = player == null ? null : EventHooks.call(player, "position");
        return BlockChangeContext.call(
            BlockStateChangeEvent.Reason.PLAYER_PLACE, player, position, location,
            () -> EventHooks.call(blockItem, "updateBlockStateFromTag",
                position, level, item, state));
    }
}
