package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.PluginContext;
import dev.aerogel.api.Registration;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Objects;

@Mixin(targets = "net.minecraft.world.item.crafting.RecipeHolder")
abstract class RecipeHolderMixin {
    @Shadow public abstract ResourceKey<Recipe<?>> id();
    @Shadow public abstract Recipe<?> value();

    /** Registers this keyed recipe as a resource owned by the supplied plugin. */
    @Unique
    public Registration register(PluginContext plugin) {
        Objects.requireNonNull(plugin, "plugin");
        RecipeHolder<?> self = (RecipeHolder<?>) (Object) this;
        return plugin.recipes().register(self.id().identifier(), self.value());
    }
}
