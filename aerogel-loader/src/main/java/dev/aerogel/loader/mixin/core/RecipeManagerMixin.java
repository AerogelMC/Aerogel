package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.RecipeManagerBridge;
import dev.aerogel.loader.internal.RecipeOverlayRegistry;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(targets = "net.minecraft.world.item.crafting.RecipeManager")
abstract class RecipeManagerMixin implements RecipeManagerBridge {
    @Shadow private RecipeMap recipes;

    @Override @Unique
    public void aerogel$replaceRecipes(Collection<RecipeHolder<?>> values) {
        recipes = RecipeMap.create(values);
    }

    @Inject(method = "apply(Lnet/minecraft/world/item/crafting/RecipeMap;"
        + "Lnet/minecraft/server/packs/resources/ResourceManager;"
        + "Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("RETURN"))
    private void aerogel$reload(
        RecipeMap prepared, @Coerce Object resources, @Coerce Object profiler, CallbackInfo ci
    ) {
        RecipeOverlayRegistry.reloaded((RecipeManager) (Object) this);
    }
}
