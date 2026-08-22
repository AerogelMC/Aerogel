package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.NaturalSpawnReservation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Releases a reserved cap slot when a candidate fails after its spawn predicate. */
@Mixin(targets = "net.minecraft.world.level.NaturalSpawner")
abstract class NaturalSpawnerMixin {
    @Inject(
        method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;"
            + "Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;"
            + "Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;"
            + "Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/"
            + "NaturalSpawner$SpawnPredicate;test(Lnet/minecraft/world/entity/EntityType;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/ChunkAccess;)Z",
            shift = At.Shift.BEFORE)
    )
    private static void aerogel$releasePreviousFailedCandidate(CallbackInfo callback) {
        NaturalSpawnReservation.releaseCurrent();
    }

    @Inject(
        method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;"
            + "Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;"
            + "Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;"
            + "Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
        at = @At("RETURN")
    )
    private static void aerogel$releaseFailedCandidateAtReturn(CallbackInfo callback) {
        NaturalSpawnReservation.releaseCurrent();
    }
}
