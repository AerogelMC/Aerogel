package net.minecraft.world.item.crafting;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.Collection;
import java.util.Optional;
import java.util.Map;

public class RecipeManager {
    public Collection<RecipeHolder<?>> getRecipes() { return null; }
    public Optional<RecipeHolder<?>> byKey(ResourceKey<Recipe<?>> key) { return Optional.empty(); }
    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(
        RecipeType<T> type, I input, Level level) { return Optional.empty(); }
    public void finalizeRecipeLoading(net.minecraft.world.flag.FeatureFlagSet flags) { }
    public Map<net.minecraft.resources.ResourceKey<RecipePropertySet>, RecipePropertySet>
        getSynchronizedItemProperties() { return null; }
    public SelectableRecipe.SingleInputSet<StonecutterRecipe> getSynchronizedStonecutterRecipes() { return null; }
}
