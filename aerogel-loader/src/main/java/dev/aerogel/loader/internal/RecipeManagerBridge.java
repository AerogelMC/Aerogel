package dev.aerogel.loader.internal;

import net.minecraft.world.item.crafting.RecipeHolder;
import java.util.Collection;

public interface RecipeManagerBridge {
    void aerogel$replaceRecipes(Collection<RecipeHolder<?>> recipes);
}
