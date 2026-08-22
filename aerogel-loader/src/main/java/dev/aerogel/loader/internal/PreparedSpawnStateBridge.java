package dev.aerogel.loader.internal;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/** Asynchronous, exact-state handoff for one vanilla natural-spawn pass. */
public interface PreparedSpawnStateBridge {
    void aerogel$preparedState(CompletableFuture<NaturalSpawner.SpawnState> state);

    void aerogel$whenPrepared(
        List<MobCategory> gatedCategories,
        BiConsumer<NaturalSpawner.SpawnState, List<MobCategory>> action);
}
