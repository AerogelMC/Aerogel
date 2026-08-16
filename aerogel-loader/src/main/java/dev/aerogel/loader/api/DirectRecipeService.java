package dev.aerogel.loader.api;

import dev.aerogel.api.Registration;
import dev.aerogel.api.recipe.RecipeService;
import dev.aerogel.loader.internal.RecipeOverlayRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.*;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

final class DirectRecipeService implements RecipeService {
    private final PluginApiScope scope;
    DirectRecipeService(PluginApiScope scope) { this.scope = scope; }

    @Override public Registration register(Identifier id, Recipe<?> recipe) {
        requireOwned(id);
        ResourceKey<Recipe<?>> key = key(id);
        RecipeManager manager = scope.vanilla().getRecipeManager();
        RecipeOverlayRegistry.add(manager, new RecipeHolder<>(key, Objects.requireNonNull(recipe, "recipe")));
        finalizeRecipes(manager);
        return scope.own(new Registration() {
            private final AtomicBoolean active = new AtomicBoolean(true);
            @Override public boolean active() { return active.get(); }
            @Override public void close() {
                if (active.compareAndSet(true, false)) {
                    RecipeOverlayRegistry.remove(manager, key);
                    finalizeRecipes(manager);
                }
            }
        });
    }
    @Override public Optional<RecipeHolder<?>> find(Identifier id) {
        return scope.vanilla().getRecipeManager().byKey(key(id));
    }
    @Override public Collection<RecipeHolder<?>> all() {
        return ListCopy.copy(scope.vanilla().getRecipeManager().getRecipes());
    }
    @Override public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> find(
        RecipeType<T> type, I input, net.minecraft.world.level.Level level
    ) {
        return scope.vanilla().getRecipeManager().getRecipeFor(type, input, level);
    }
    @Override public ResourceKey<Recipe<?>> key(Identifier id) { return ResourceKey.create(Registries.RECIPE, id); }
    @Override public String pluginNamespace() { return scope.pluginId(); }

    private void finalizeRecipes(RecipeManager manager) {
        manager.finalizeRecipeLoading(scope.vanilla().getWorldData().enabledFeatures());
        ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(
            manager.getSynchronizedItemProperties(), manager.getSynchronizedStonecutterRecipes());
        scope.vanilla().getPlayerList().getPlayers().forEach(player -> player.sendPacket(packet));
    }

    private void requireOwned(Identifier id) {
        Objects.requireNonNull(id, "id");
        if (!scope.pluginId().equals(id.getNamespace())) {
            throw new IllegalArgumentException("Plugin recipes must use namespace " + scope.pluginId() + ": " + id);
        }
    }

    private static final class ListCopy {
        static <T> Collection<T> copy(Collection<T> source) { return java.util.List.copyOf(source); }
    }
}
