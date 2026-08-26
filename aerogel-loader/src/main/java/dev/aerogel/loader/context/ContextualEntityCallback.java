package dev.aerogel.loader.context;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import dev.aerogel.loader.internal.EntityContextOwnerBridge;

import java.util.Objects;

/** Publishes the entity-section index in its owning Context. */
public final class ContextualEntityCallback implements EntityInLevelCallback {
    private final EntityInLevelCallback delegate;
    private final Entity entity;

    public ContextualEntityCallback(Entity entity, EntityInLevelCallback delegate) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void onMove() {
        /*
         * Movement is not necessarily initiated by the entity's own tick. A
         * collision, vehicle, or another entity can move this entity while that
         * caller owns a different Context. Mutating PersistentEntitySectionManager
         * from that foreign scope races the entity's own lane: one callback can
         * replace currentSection after another callback compared currentSectionKey,
         * producing the vanilla "wasn't found in section" warning and a corrupt
         * spatial index.
         *
         * routeEntityTask validates the exact current owner plus the entity's
         * swept chunk footprint. It returns false when this transaction already
         * owns that scope, so the normal owner-tick path stays allocation-free.
         * A foreign mutation is appended to the entity owner instead of taking a
         * lock or falling back to a global queue.
         */
        if (NativeTickCoordinator.isNativeWorker()) {
            ContextThreadState.AccessScope scope = ContextThreadState.current();
            if (scope.primary().scheduler().routeEntityTask(entity, this::onMove)) return;
        }

        EntityContextOwnerBridge ownership = (EntityContextOwnerBridge) entity;
        Object expectedOwner = ownership.aerogel$contextOwner();
        if (NativeTickCoordinator.isNativeWorker()) {
            try {
                if (!entity.isRemoved()) {
                    delegate.onMove();
                    recordBoundaryMove(expectedOwner, entity.chunkPosition());
                }
            } finally {
                if (!stillOwnsPosition(expectedOwner, entity.chunkPosition())) {
                    Runnable release = () -> ownership.aerogel$compareAndSetContextOwner(
                        expectedOwner, null);
                    if (!NativeTickCoordinator.deferNativeCompletion(release)) release.run();
                }
            }
            return;
        }
        Runnable commit = () -> {
            try {
                if (!entity.isRemoved()) {
                    delegate.onMove();
                    recordBoundaryMove(expectedOwner, entity.chunkPosition());
                }
            } finally {
                if (!stillOwnsPosition(expectedOwner, entity.chunkPosition())) {
                    releaseAfterOwnedWork(ownership, expectedOwner);
                }
            }
        };
        commit.run();
    }

    @Override
    public void onRemove(Entity.RemovalReason reason) {
        try {
            delegate.onRemove(reason);
        } finally {
            ((EntityContextOwnerBridge) entity).aerogel$contextOwner(null);
        }
    }

    private static void releaseAfterOwnedWork(
        EntityContextOwnerBridge ownership, Object expectedOwner
    ) {
        Runnable release = () -> ownership.aerogel$compareAndSetContextOwner(
            expectedOwner, null);
        if (expectedOwner instanceof ChunkContextImpl context && context.active()) {
            if (!context.submitNative(release, release)) release.run();
        } else {
            release.run();
        }
    }

    private static boolean stillOwnsPosition(Object owner, ChunkPos position) {
        return owner instanceof ChunkContextImpl context
            && context.chunkX() == position.x() && context.chunkZ() == position.z();
    }

    private static void recordBoundaryMove(Object owner, ChunkPos position) {
        if (!(owner instanceof ChunkContextImpl context)
            || context.chunkX() == position.x() && context.chunkZ() == position.z()) return;
        context.scheduler().entityMovedAcrossChunks(
            context.world(), context.key(), position.pack());
    }
}
