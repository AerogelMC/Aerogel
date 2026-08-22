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
        EntityContextOwnerBridge ownership = (EntityContextOwnerBridge) entity;
        Object expectedOwner = ownership.aerogel$contextOwner();
        if (NativeTickCoordinator.isNativeWorker()) {
            try {
                if (!entity.isRemoved()) delegate.onMove();
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
                if (!entity.isRemoved()) delegate.onMove();
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
}
