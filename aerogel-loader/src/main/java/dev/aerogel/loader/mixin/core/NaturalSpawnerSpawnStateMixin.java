package dev.aerogel.loader.mixin.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.MobCategory;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import dev.aerogel.loader.internal.PreparedSpawnStateBridge;
import dev.aerogel.loader.context.NaturalSpawnWave;
import dev.aerogel.loader.context.NaturalSpawnReservation;

@Mixin(targets = "net.minecraft.world.level.NaturalSpawner$SpawnState")
abstract class NaturalSpawnerSpawnStateMixin implements PreparedSpawnStateBridge {
    @Shadow @Final private int spawnableChunkCount;
    @Shadow @Final private Object2IntOpenHashMap<MobCategory> mobCategoryCounts;
    @Shadow @Final private LocalMobCapCalculator localMobCapCalculator;

    @Unique
    private ThreadLocal<CheckedSpawn> aerogel$lastChecked;
    @Unique
    private AtomicIntegerArray aerogel$categoryCounts;
    @Unique
    private volatile CompletableFuture<NaturalSpawner.SpawnState> aerogel$preparedState;
    @Unique
    private volatile NaturalSpawner.SpawnState aerogel$preparedDelegate;
    @Unique
    private volatile CompletableFuture<List<MobCategory>> aerogel$preparedCategories;
    @Unique
    private volatile NaturalSpawnWave aerogel$spawnWave;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$captureInitialCounts(
        int spawnableChunks, Object2IntOpenHashMap<MobCategory> counts,
        PotentialCalculator potential, LocalMobCapCalculator localCaps,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback
    ) {
        aerogel$lastChecked = ThreadLocal.withInitial(CheckedSpawn::new);
        aerogel$categoryCounts = new AtomicIntegerArray(MobCategory.values().length);
        for (MobCategory category : MobCategory.values()) {
            aerogel$categoryCounts.set(
                category.ordinal(), counts.getInt(category));
        }
    }

    @Inject(method = "canSpawn", at = @At("HEAD"))
    private void aerogel$beginCheck(
        EntityType<?> type, BlockPos position, ChunkAccess chunk,
        CallbackInfoReturnable<Boolean> callback
    ) {
        CheckedSpawn checked = aerogel$lastChecked.get();
        checked.position = position;
        checked.type = type;
    }

    /** Linearizes vanilla's global and per-player cap check with its later add. */
    @Inject(method = "canSpawn", at = @At("RETURN"), cancellable = true)
    private void aerogel$reserveMobCap(
        EntityType<?> type, BlockPos position, ChunkAccess chunk,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (!callback.getReturnValueZ()) return;
        MobCategory category = type.getCategory();
        int globalLimit = category.getMaxInstancesPerChunk() * spawnableChunkCount
            / NaturalSpawnerAccessor.aerogel$magicNumber();
        if (!NaturalSpawnReservation.acquire(
            aerogel$categoryCounts, category, globalLimit,
            localMobCapCalculator, ChunkPos.containing(position))) {
            callback.setReturnValue(false);
        }
    }

    @Redirect(method = "canSpawn", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/level/NaturalSpawner$SpawnState;lastCheckedPos:"
            + "Lnet/minecraft/core/BlockPos;", opcode = Opcodes.PUTFIELD))
    private void aerogel$doNotPublishPosition(
        NaturalSpawner.SpawnState state, BlockPos position
    ) { }

    @Redirect(method = "canSpawn", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/level/NaturalSpawner$SpawnState;lastCheckedType:"
            + "Lnet/minecraft/world/entity/EntityType;", opcode = Opcodes.PUTFIELD))
    private void aerogel$doNotPublishType(
        NaturalSpawner.SpawnState state, EntityType<?> type
    ) { }

    @Redirect(method = "canSpawn", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/level/NaturalSpawner$SpawnState;lastCharge:D",
        opcode = Opcodes.PUTFIELD))
    private void aerogel$rememberCharge(
        NaturalSpawner.SpawnState state, double charge
    ) {
        aerogel$lastChecked.get().charge = charge;
    }

    @Redirect(method = "afterSpawn", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/level/NaturalSpawner$SpawnState;lastCheckedPos:"
            + "Lnet/minecraft/core/BlockPos;", opcode = Opcodes.GETFIELD))
    private BlockPos aerogel$checkedPosition(NaturalSpawner.SpawnState state) {
        return aerogel$lastChecked.get().position;
    }

    @Redirect(method = "afterSpawn", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/level/NaturalSpawner$SpawnState;lastCheckedType:"
            + "Lnet/minecraft/world/entity/EntityType;", opcode = Opcodes.GETFIELD))
    private EntityType<?> aerogel$checkedType(NaturalSpawner.SpawnState state) {
        return aerogel$lastChecked.get().type;
    }

    @Redirect(method = "afterSpawn", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/level/NaturalSpawner$SpawnState;lastCharge:D",
        opcode = Opcodes.GETFIELD))
    private double aerogel$checkedCharge(NaturalSpawner.SpawnState state) {
        return aerogel$lastChecked.get().charge;
    }

    @Redirect(method = "afterSpawn", at = @At(value = "INVOKE",
        target = "Lit/unimi/dsi/fastutil/objects/Object2IntOpenHashMap;"
            + "addTo(Ljava/lang/Object;I)I"))
    private int aerogel$atomicCategoryAdd(
        Object2IntOpenHashMap<MobCategory> counts, Object category, int increment
    ) {
        if (increment == 1 && NaturalSpawnReservation.commitGlobal(
            aerogel$categoryCounts, (MobCategory) category)) {
            return NaturalSpawnReservation.reservedPreviousCount();
        }
        return aerogel$categoryCounts.getAndAdd(((MobCategory) category).ordinal(), increment);
    }

    @Redirect(method = "afterSpawn", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/LocalMobCapCalculator;"
            + "addMob(Lnet/minecraft/world/level/ChunkPos;"
            + "Lnet/minecraft/world/entity/MobCategory;)V"))
    private void aerogel$commitLocalCategoryAdd(
        LocalMobCapCalculator calculator, ChunkPos chunk, MobCategory category
    ) {
        if (!NaturalSpawnReservation.commitLocal(calculator, chunk, category)) {
            calculator.addMob(chunk, category);
        }
    }

    @Redirect(method = "canSpawnForCategoryGlobal", at = @At(value = "INVOKE",
        target = "Lit/unimi/dsi/fastutil/objects/Object2IntOpenHashMap;"
            + "getInt(Ljava/lang/Object;)I"))
    private int aerogel$atomicCategoryCount(
        Object2IntOpenHashMap<MobCategory> counts, Object category
    ) {
        return aerogel$categoryCounts.get(((MobCategory) category).ordinal());
    }

    @Inject(method = "getMobCategoryCounts", at = @At("HEAD"), cancellable = true)
    private void aerogel$concurrentCountSnapshot(
        CallbackInfoReturnable<Object2IntMap<MobCategory>> callback
    ) {
        NaturalSpawner.SpawnState prepared = aerogel$preparedDelegate;
        if (prepared != null) {
            callback.setReturnValue(prepared.getMobCategoryCounts());
            return;
        }
        Object2IntOpenHashMap<MobCategory> snapshot = new Object2IntOpenHashMap<>();
        for (MobCategory category : MobCategory.values()) {
            snapshot.put(category, aerogel$categoryCounts.get(category.ordinal()));
        }
        callback.setReturnValue(Object2IntMaps.unmodifiable(snapshot));
    }

    @Override
    public void aerogel$preparedState(
        CompletableFuture<NaturalSpawner.SpawnState> state, NaturalSpawnWave wave
    ) {
        aerogel$preparedState = state;
        aerogel$spawnWave = wave;
        state.thenAccept(prepared -> aerogel$preparedDelegate = prepared);
    }

    @Override
    public NaturalSpawnWave aerogel$spawnWave() {
        return aerogel$spawnWave;
    }

    @Override
    public void aerogel$whenPrepared(
        List<MobCategory> gatedCategories,
        BiConsumer<NaturalSpawner.SpawnState, List<MobCategory>> action
    ) {
        CompletableFuture<NaturalSpawner.SpawnState> state = aerogel$preparedState;
        if (state == null) {
            action.accept((NaturalSpawner.SpawnState) (Object) this, gatedCategories);
            return;
        }
        NaturalSpawnWave wave = aerogel$spawnWave;
        if (wave != null && !wave.register()) {
            throw new IllegalStateException("Natural-spawn wave closed before preparation");
        }
        CompletableFuture<List<MobCategory>> categories = aerogel$preparedCategories;
        if (categories == null) {
            synchronized (this) {
                categories = aerogel$preparedCategories;
                if (categories == null) {
                    List<MobCategory> gate = List.copyOf(gatedCategories);
                    categories = state.thenApply(prepared -> {
                        EnumSet<MobCategory> globallyAllowed = EnumSet.noneOf(MobCategory.class);
                        globallyAllowed.addAll(NaturalSpawner.getFilteredSpawningCategories(
                            prepared, true, true));
                        return gate.stream().filter(globallyAllowed::contains).toList();
                    });
                    aerogel$preparedCategories = categories;
                }
            }
        }
        CompletableFuture<List<MobCategory>> exactCategories = categories;
        state.thenCombine(exactCategories, (prepared, exact) -> {
            action.accept(prepared, exact);
            return null;
        }).whenComplete((ignored, failure) -> {
            if (wave != null) wave.taskComplete();
        });
    }

    @Inject(method = "getSpawnableChunkCount", at = @At("HEAD"), cancellable = true)
    private void aerogel$preparedSpawnableChunkCount(
        CallbackInfoReturnable<Integer> callback
    ) {
        NaturalSpawner.SpawnState prepared = aerogel$preparedDelegate;
        if (prepared != null) callback.setReturnValue(prepared.getSpawnableChunkCount());
    }

    @Unique
    private static final class CheckedSpawn {
        private BlockPos position;
        private EntityType<?> type;
        private double charge;
    }
}
