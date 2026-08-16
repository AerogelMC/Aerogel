package dev.aerogel.api.recipe;

import dev.aerogel.api.Registration;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.*;

import java.util.Collection;
import java.util.Optional;

public interface RecipeService {
    default Registration register(String path, Recipe<?> recipe) {
        return register(Identifier.fromNamespaceAndPath(pluginNamespace(), path), recipe);
    }
    Registration register(Identifier id, Recipe<?> recipe);
    Optional<RecipeHolder<?>> find(Identifier id);
    Collection<RecipeHolder<?>> all();
    <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> find(
        RecipeType<T> type, I input, net.minecraft.world.level.Level level);
    ResourceKey<Recipe<?>> key(Identifier id);
    /** Namespace automatically used by the String-path overload. */
    String pluginNamespace();
}
