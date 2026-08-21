package dev.aerogel.loader.context;

import dev.aerogel.loader.internal.EntityContextOwnerBridge;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ContextualEntityCallbackTest {
    @Test
    void staleMoveDoesNotRunAfterEntityRemoval() {
        TestEntity entity = new TestEntity();
        entity.removed = true;
        AtomicInteger moves = new AtomicInteger();
        ContextualEntityCallback callback = new ContextualEntityCallback(
            entity, delegate(moves));

        callback.onMove();

        assertEquals(0, moves.get());
        assertEquals(null, entity.owner);
    }

    @Test
    void liveMoveStillReachesTheEntityIndex() {
        TestEntity entity = new TestEntity();
        AtomicInteger moves = new AtomicInteger();
        ContextualEntityCallback callback = new ContextualEntityCallback(
            entity, delegate(moves));

        callback.onMove();

        assertEquals(1, moves.get());
    }

    @Test
    void movementInsideTheSameChunkKeepsItsOwner() {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(1)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl owner = world.context(2, 3);
            TestEntity entity = new TestEntity(owner, new ChunkPos(2, 3));
            ContextualEntityCallback callback = new ContextualEntityCallback(
                entity, delegate(new AtomicInteger()));

            callback.onMove();

            assertEquals(owner, entity.owner);
        }
    }

    @Test
    void boundaryMovementReleasesAfterEarlierOwnerWork() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(1)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl owner = world.context(2, 3);
            TestEntity entity = new TestEntity(owner, new ChunkPos(3, 3));
            ContextualEntityCallback callback = new ContextualEntityCallback(
                entity, delegate(new AtomicInteger()));

            callback.onMove();
            owner.submit(0, () -> { }).get();

            assertEquals(null, entity.owner);
        }
    }

    private static EntityInLevelCallback delegate(AtomicInteger moves) {
        return new EntityInLevelCallback() {
            @Override public void onMove() { moves.incrementAndGet(); }
            @Override public void onRemove(Entity.RemovalReason reason) { }
        };
    }

    private static final class TestEntity extends Entity
        implements EntityContextOwnerBridge {
        private boolean removed;
        private Object owner = new Object();
        private ChunkPos position;

        private TestEntity() {
        }

        private TestEntity(Object owner, ChunkPos position) {
            this.owner = owner;
            this.position = position;
        }

        @Override public boolean isRemoved() { return removed; }
        @Override public ChunkPos chunkPosition() { return position; }
        @Override public Object aerogel$contextOwner() { return owner; }
        @Override public void aerogel$contextOwner(Object owner) { this.owner = owner; }
        @Override public boolean aerogel$compareAndSetContextOwner(
            Object expected, Object updated
        ) {
            if (owner != expected) return false;
            owner = updated;
            return true;
        }
    }
}
