package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.ChunkMapTrackingBridge;
import dev.aerogel.loader.internal.DistanceManagerBridge;
import dev.aerogel.loader.internal.ServerEntityBridge;
import dev.aerogel.loader.internal.ContextOwnedEntityTask;
import dev.aerogel.loader.internal.TrackedEntityBridge;
import dev.aerogel.loader.internal.GenerationNodeExecutorBridge;
import dev.aerogel.loader.runtime.AerogelRuntime;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ChunkTaskDispatcher;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import dev.aerogel.loader.context.NativeTickCoordinator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.ArrayList;
import dev.aerogel.loader.internal.LevelTicksBridge;

@Mixin(targets = "net.minecraft.server.level.ChunkMap")
abstract class ChunkMapMixin implements ChunkMapTrackingBridge, GenerationNodeExecutorBridge {
    private static final ThreadLocal<MoveSnapshot> AEROGEL_MOVE_SNAPSHOT =
        new ThreadLocal<>();
    private static final ObjectCollection<Object> AEROGEL_EMPTY_TRACKED_ENTITIES =
        new ObjectArrayList<>(java.util.List.of());

    @Shadow @Final private ServerLevel level;
    @Shadow @Final private Int2ObjectMap<Object> entityMap;
    @Shadow @Final private ChunkTaskDispatcher worldgenTaskDispatcher;
    @Shadow public abstract DistanceManager getDistanceManager();

    @Inject(method = "onFullChunkStatusChange", at = @At("RETURN"))
    private void aerogel$wakeScheduledTicks(
        ChunkPos position, @Coerce Object status,
        CallbackInfo callback
    ) {
        long key = position.pack();
        ((LevelTicksBridge) (Object) level.getBlockTicks())
            .aerogel$eligibilityChanged(key);
        ((LevelTicksBridge) (Object) level.getFluidTicks())
            .aerogel$eligibilityChanged(key);
    }

    @Inject(
        method = "tick()V",
        at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/ints/"
            + "Int2ObjectMap;values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;",
            ordinal = 0),
        cancellable = true
    )
    private void aerogel$tickPersistentTrackingIndex(CallbackInfo callback) {
        AerogelRuntime.tickTrackedEntities(level, level.players());
        callback.cancel();
    }

    @Inject(method = "addEntity", at = @At("RETURN"))
    private void aerogel$registerTrackedEntity(Entity entity, CallbackInfo callback) {
        Object tracked = entityMap.get(entity.getId());
        if (tracked instanceof TrackedEntityBridge bridge) {
            AerogelRuntime.registerTrackedEntity(level, entity, bridge);
        }
    }

    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void aerogel$unregisterTrackedEntity(Entity entity, CallbackInfo callback) {
        AerogelRuntime.unregisterTrackedEntity(entity);
    }

    @Invoker("setChunkUnsaved")
    protected abstract void aerogel$setChunkUnsaved(ChunkPos position);

    @Override
    public ServerLevel aerogel$level() {
        return level;
    }

    @Override
    public Object aerogel$trackedEntity(int entityId) {
        return entityMap.get(entityId);
    }

    @Override
    public void aerogel$submitGenerationNode(
        GenerationChunkHolder holder, Runnable task
    ) {
        worldgenTaskDispatcher.submit(
            task, holder.getPos().pack(), holder::getQueueLevel);
    }

    @Override
    public void aerogel$moveSnapshot(
        ServerPlayer player, SectionPos section, ChunkPos chunk
    ) {
        if (AEROGEL_MOVE_SNAPSHOT.get() != null) {
            throw new IllegalStateException("Nested player movement snapshot");
        }
        AEROGEL_MOVE_SNAPSHOT.set(new MoveSnapshot(player, section, chunk));
        try {
            ((ChunkMap) (Object) this).move(player);
        } finally {
            AEROGEL_MOVE_SNAPSHOT.remove();
        }
    }

    @Redirect(
        method = {
            "move(Lnet/minecraft/server/level/ServerPlayer;)V",
            "updatePlayerPos(Lnet/minecraft/server/level/ServerPlayer;)V"
        },
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;"
            + "of(Lnet/minecraft/world/level/entity/EntityAccess;)"
            + "Lnet/minecraft/core/SectionPos;")
    )
    private SectionPos aerogel$coherentMoveSection(EntityAccess entity) {
        MoveSnapshot snapshot = AEROGEL_MOVE_SNAPSHOT.get();
        return snapshot != null && snapshot.player() == entity
            ? snapshot.section()
            : SectionPos.of(entity);
    }

    @Redirect(
        method = "updateChunkTracking(Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;"
            + "chunkPosition()Lnet/minecraft/world/level/ChunkPos;")
    )
    private ChunkPos aerogel$coherentMoveChunk(ServerPlayer player) {
        MoveSnapshot snapshot = AEROGEL_MOVE_SNAPSHOT.get();
        return snapshot != null && snapshot.player() == player
            ? snapshot.chunk()
            : player.chunkPosition();
    }

    /**
     * Player visibility against every tracked entity is already distributed by
     * ContextServiceImpl using the same section-change snapshot. Keep only the
     * player-distance and chunk-view half of vanilla's move transaction here.
     */
    @Redirect(
        method = "move(Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/ints/"
            + "Int2ObjectMap;values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;")
    )
    private ObjectCollection<Object> aerogel$distributedPlayerTracking(
        Int2ObjectMap<Object> entities
    ) {
        return AEROGEL_MOVE_SNAPSHOT.get() == null
            ? entities.values()
            : AEROGEL_EMPTY_TRACKED_ENTITIES;
    }

    @Redirect(
        method = "forEachBlockTickingChunk(Ljava/util/function/Consumer;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap$DistanceManager;"
            + "forEachEntityTickingChunk(Lit/unimi/dsi/fastutil/longs/LongConsumer;)V")
    )
    private void aerogel$publishedTickingChunks(
        @Coerce Object manager, LongConsumer consumer
    ) {
        ((DistanceManagerBridge) manager)
            .aerogel$forEachPublishedEntityTickingChunk(consumer);
    }

    @Inject(method = "setChunkUnsaved(Lnet/minecraft/world/level/ChunkPos;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$commitUnsavedChunk(ChunkPos position, CallbackInfo callback) {
        if (!NativeTickCoordinator.isNativeWorker()) return;
        if (NativeTickCoordinator.deferGlobalCommit(
            () -> aerogel$setChunkUnsaved(position))) callback.cancel();
    }

    private record MoveSnapshot(
        ServerPlayer player, SectionPos section, ChunkPos chunk
    ) { }

}
