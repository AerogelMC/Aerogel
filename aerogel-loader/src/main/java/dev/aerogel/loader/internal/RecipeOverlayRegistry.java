package dev.aerogel.loader.internal;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeOverlayRegistry {
    private static final Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> OVERLAYS = new LinkedHashMap<>();
    private static List<RecipeHolder<?>> vanilla = List.of();
    private static RecipeManager manager;
    private static boolean rebuilding;

    private RecipeOverlayRegistry() { }

    public static synchronized void add(RecipeManager current, RecipeHolder<?> holder) {
        attach(current);
        if (OVERLAYS.putIfAbsent(holder.id(), holder) != null) {
            throw new IllegalStateException("A plugin recipe is already registered as " + holder.id().identifier());
        }
        rebuild();
    }

    public static synchronized void remove(RecipeManager current, ResourceKey<Recipe<?>> key) {
        if (manager != current || OVERLAYS.remove(key) == null) return;
        rebuild();
    }

    /** Called after a datapack recipe reload to replace the vanilla baseline without losing plugins. */
    public static synchronized void reloaded(RecipeManager current) {
        if (rebuilding) return;
        manager = current;
        vanilla = current.getRecipes().stream().filter(holder -> !OVERLAYS.containsKey(holder.id())).toList();
        rebuild();
    }

    private static void attach(RecipeManager current) {
        if (manager == current) return;
        manager = current;
        vanilla = List.copyOf(current.getRecipes());
        OVERLAYS.clear();
    }

    private static void rebuild() {
        if (manager == null) return;
        Collection<RecipeHolder<?>> combined = new ArrayList<>(vanilla.size() + OVERLAYS.size());
        vanilla.stream().filter(holder -> !OVERLAYS.containsKey(holder.id())).forEach(combined::add);
        combined.addAll(OVERLAYS.values());
        rebuilding = true;
        try {
            ((RecipeManagerBridge) manager).aerogel$replaceRecipes(combined);
        } finally {
            rebuilding = false;
        }
    }
}
