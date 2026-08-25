package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.BlockInteractionScope;
import dev.aerogel.loader.internal.MenuContextBridge;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.inventory.AbstractContainerMenu")
abstract class AbstractContainerMenuContextMixin implements MenuContextBridge {
    @Unique private BlockInteractionScope.Binding aerogel$blockInteraction;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$captureBlockInteraction(
        MenuType<?> type, int containerId, CallbackInfo callback
    ) {
        aerogel$blockInteraction = BlockInteractionScope.current();
    }

    @Override
    public BlockInteractionScope.Binding aerogel$blockInteraction() {
        return aerogel$blockInteraction;
    }
}
