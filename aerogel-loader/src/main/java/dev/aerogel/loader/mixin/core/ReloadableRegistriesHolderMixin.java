package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.LootOverlayRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.server.ReloadableServerRegistries$Holder")
abstract class ReloadableRegistriesHolderMixin {
    @Inject(method = "getLootTable(Lnet/minecraft/resources/ResourceKey;)"
        + "Lnet/minecraft/world/level/storage/loot/LootTable;", at = @At("HEAD"), cancellable = true)
    private void aerogel$pluginLootTable(
        ResourceKey<LootTable> key, CallbackInfoReturnable<LootTable> callback
    ) {
        LootOverlayRegistry.find(key).ifPresent(callback::setReturnValue);
    }
}
