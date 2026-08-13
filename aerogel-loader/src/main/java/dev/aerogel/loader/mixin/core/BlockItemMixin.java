package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.block.BlockPlaceEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.item.BlockItem")
abstract class BlockItemMixin {
    @Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)"
        + "Lnet/minecraft/world/InteractionResult;", at = @At("HEAD"), cancellable = true)
    private void aerogel$place(@Coerce Object context, CallbackInfoReturnable<Object> callbackInfo) {
        BlockPlaceEvent event = new BlockPlaceEvent(this, context);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(EventHooks.staticField(
                this, "net.minecraft.world.InteractionResult", "FAIL"));
        }
    }
}
