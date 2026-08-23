package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.TrackedEntityBridge;
import dev.aerogel.loader.internal.ServerEntityBridge;
import dev.aerogel.loader.internal.PlayerViewService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.List;
import java.util.Objects;
import dev.aerogel.loader.internal.DistanceManagerBridge;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
abstract class TrackedEntityMixin implements TrackedEntityBridge {
    @Shadow @Final private Set<?> seenBy;
    @Shadow @Final private Entity entity;
    @Shadow @Final private ServerEntity serverEntity;
    @Shadow private SectionPos lastSectionPos;
    @Shadow public abstract void removePlayer(ServerPlayer player);
    @Shadow public abstract void updatePlayer(ServerPlayer player);
    @Shadow public abstract void updatePlayers(List<ServerPlayer> players);

    @Override public Entity aerogel$entity() { return entity; }

    @Override
    public boolean aerogel$sectionChanged() {
        return !Objects.equals(lastSectionPos, SectionPos.of(entity));
    }

    @Override
    public void aerogel$tickTracking(
        List<ServerPlayer> players, boolean entityTicking
    ) {
        SectionPos current = SectionPos.of(entity);
        boolean moved = !Objects.equals(lastSectionPos, current);
        if (moved) {
            updatePlayers(players);
            lastSectionPos = current;
        }
        if (moved || entity.needsSync || entityTicking) {
            serverEntity.sendChanges();
        }
    }

    @Override
    public void aerogel$updatePlayers(List<ServerPlayer> players) {
        updatePlayers(players);
    }

    @Override
    public boolean aerogel$isSeenBy(Object connection) {
        return seenBy.contains(connection);
    }

    @Override
    public void aerogel$removePlayer(ServerPlayer player) {
        removePlayer(player);
    }

    @Override
    public void aerogel$updatePlayer(ServerPlayer player) {
        updatePlayer(player);
    }

    @Override
    public void aerogel$publishDirtyState() {
        ((ServerEntityBridge) serverEntity).aerogel$publishDirtyState();
    }

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void aerogel$keepHidden(ServerPlayer player, CallbackInfo callbackInfo) {
        if (PlayerViewService.isHidden(player, entity)) callbackInfo.cancel();
    }
}
