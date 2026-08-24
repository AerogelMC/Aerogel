package dev.aerogel.loader.context;

import org.junit.jupiter.api.Test;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.aerogel.loader.internal.EntityContextOwnerBridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ContextSchedulerTest {
    @Test
    void blockMutationScopeContainsOnlyTheActualTargetChunk() {
        assertScope(new long[] { ChunkPos.pack(2, 3) },
            new BlockPos(2 * 16 + 8, 64, 3 * 16 + 8));
        assertScope(new long[] { ChunkPos.pack(2, 3) },
            new BlockPos(2 * 16, 64, 3 * 16 + 8));
        assertScope(new long[] { ChunkPos.pack(2, 3) },
            new BlockPos(2 * 16, 64, 3 * 16));
    }

    @Test
    void blockEffectScopeIsDerivedFromTheActualTargetPositions() {
        long[] actual = ContextServiceImpl.blockEffectScope(List.of(
            new BlockPos(8, 64, 8),
            new BlockPos(31, 64, 8)));
        long[] expected = {
            ChunkPos.pack(0, 0), ChunkPos.pack(1, 0)
        };
        Arrays.sort(actual);
        Arrays.sort(expected);
        assertArrayEquals(expected, actual);
    }

    @Test
    void gameEventScopeMatchesVanillaNotificationRadiusAtChunkBorders() {
        long[] actual = ContextServiceImpl.gameEventScope(15, -1, 1);
        long[] expected = {
            ChunkPos.pack(0, -1), ChunkPos.pack(0, 0),
            ChunkPos.pack(1, -1), ChunkPos.pack(1, 0)
        };
        Arrays.sort(actual);
        Arrays.sort(expected);
        assertArrayEquals(expected, actual);

        assertArrayEquals(
            new long[] { ChunkPos.pack(-2, 3) },
            ContextServiceImpl.gameEventScope(-24, 55, 0));
    }

    @Test
    void entityTickScopeComesFromItsActualSweptBlockFootprint() {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(1)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl owner = world.context(-19, 17);
            Entity entity = new OwnedEntity(owner) {
                @Override public AABB getBoundingBox() {
                    return new AABB(-294.5D, -19.0D, 271.8D, -293.5D, -17.0D, 272.8D);
                }
                @Override public Vec3 getDeltaMovement() {
                    return new Vec3(0.0D, 0.0D, 0.0D);
                }
                @Override public BlockPos getOnPosLegacy() {
                    throw new AssertionError("scope calculation must not read world state");
                }
                @Override public BlockPos getOnPos() {
                    throw new AssertionError("scope calculation must not read world state");
                }
            };
            long[] actual = ContextServiceImpl.entityTickScope(owner, entity);
            long[] expected = { ChunkPos.pack(-19, 16), ChunkPos.pack(-19, 17) };
            Arrays.sort(actual);
            Arrays.sort(expected);
            assertArrayEquals(expected, actual);
        }
    }

    private static void assertScope(long[] expected, BlockPos position) {
        long[] actual = ContextServiceImpl.blockMutationScope(position);
        Arrays.sort(expected);
        Arrays.sort(actual);
        assertArrayEquals(expected, actual);
    }

    @Test
    void nativeEntityLanesDoNotCreateAGlobalTickBarrier() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(2)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl slowContext = world.context(0, 0);
            ChunkContextImpl fastContext = world.context(1, 0);
            NativeEntityLane slow = slowContext.entityLane();
            NativeEntityLane fast = fastContext.entityLane();
            OwnedEntity slowEntity = new OwnedEntity(slowContext);
            OwnedEntity fastEntity = new OwnedEntity(fastContext);
            CountDownLatch slowStarted = new CountDownLatch(1);
            CountDownLatch releaseSlow = new CountDownLatch(1);
            CountDownLatch fastFinished = new CountDownLatch(1);

            slow.offer(List.of(slowEntity), ignored -> {
                slowStarted.countDown();
                await(releaseSlow);
            });
            assertTrue(slowStarted.await(2, TimeUnit.SECONDS));
            fast.offer(List.of(fastEntity), ignored -> fastFinished.countDown());

            assertTrue(fastFinished.await(1, TimeUnit.SECONDS));
            releaseSlow.countDown();
        }
    }

    @Test
    void overloadedContextKeepsOnlyTheLatestUnstartedTick() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(1)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl context = world.context(0, 0);
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch latestFinished = new CountDownLatch(1);
            AtomicInteger executed = new AtomicInteger();
            AtomicInteger superseded = new AtomicInteger();

            NativeTickToken first = new NativeTickToken(1L);
            offerTickTask(context, first, () -> {
                executed.incrementAndGet();
                firstStarted.countDown();
                await(releaseFirst);
            }, superseded::incrementAndGet);
            first.seal();
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

            for (long tick = 2L; tick <= 1_000L; tick++) {
                NativeTickToken token = new NativeTickToken(tick);
                boolean latest = tick == 1_000L;
                offerTickTask(context, token, () -> {
                    executed.incrementAndGet();
                    if (latest) latestFinished.countDown();
                }, superseded::incrementAndGet);
                token.seal();
            }

            assertTrue(context.snapshot().queuedTasks() <= 1,
                "future tick requests must not become Context mailbox entries");
            releaseFirst.countDown();
            assertTrue(latestFinished.await(2, TimeUnit.SECONDS));
            assertEquals(2, executed.get());
            assertEquals(998, superseded.get());
            context.submit(0, () -> { }).get(2, TimeUnit.SECONDS);
            assertEquals(0, context.snapshot().queuedTasks());
        }
    }

    @Test
    void entityBatchFinishesBeforeItsGlobalIndexCommit() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(2)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl context = world.context(0, 0);
            NativeEntityLane lane = context.entityLane();
            List<Entity> entities = List.of(
                new OwnedEntity(context), new OwnedEntity(context), new OwnedEntity(context));
            CountDownLatch ticked = new CountDownLatch(entities.size());
            AtomicInteger committed = new AtomicInteger();

            lane.offer(entities, ignored -> {
                NativeTickCoordinator.deferGlobalCommit(committed::incrementAndGet);
                ticked.countDown();
            });

            assertTrue(ticked.await(2, TimeUnit.SECONDS),
                "all same-chunk entities must tick without waiting for the server commit pump");
            context.submit(0, () -> { }).get(2, TimeUnit.SECONDS);
            assertEquals(0, committed.get());
            NativeTickCoordinator.pumpMainThread();
            assertEquals(entities.size(), committed.get());
        }
    }

    @Test
    void contextCommitPublishesInsideTheExactOwnerTransaction() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(1)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl context = world.context(0, 0);
            AtomicInteger contextCommit = new AtomicInteger();
            AtomicInteger serverCommit = new AtomicInteger();

            context.submit(0, () -> NativeTickCoordinator.runNative(
                List.of(1), ignored -> {
                    assertTrue(NativeTickCoordinator.deferCommit(
                        CommitScope.CONTEXT, contextCommit::incrementAndGet));
                    assertTrue(NativeTickCoordinator.deferCommit(
                        CommitScope.SERVER, serverCommit::incrementAndGet));
                }, () -> { })).get(2, TimeUnit.SECONDS);

            assertEquals(1, contextCommit.get(),
                "the Context publication must finish before its owner task completes");
            assertEquals(0, serverCommit.get(),
                "a true server publication must still wait for the server boundary");
            NativeTickCoordinator.pumpMainThread();
            assertEquals(1, serverCommit.get());
        }
    }

    @Test
    void failedNativeCompletionReturnsItsOutstandingPermit() throws Exception {
        java.lang.reflect.Field outstandingField =
            NativeTickCoordinator.class.getDeclaredField("OUTSTANDING");
        outstandingField.setAccessible(true);
        AtomicInteger outstanding = (AtomicInteger) outstandingField.get(null);
        int before = outstanding.get();
        AtomicInteger laneCompletions = new AtomicInteger();

        NativeTickCoordinator.taskSubmitted();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> NativeTickCoordinator.runNative(List.of(1), ignored ->
                NativeTickCoordinator.deferNativeCompletion(() -> {
                    throw new IllegalStateException("failed publication");
                }), laneCompletions::incrementAndGet));

        assertEquals("failed publication", failure.getMessage());
        assertEquals(1, laneCompletions.get(),
            "the failed owner transaction must still release its lane");
        assertEquals(before, outstanding.get(),
            "the failed owner transaction must not strand shutdown");
    }

    @Test
    void slowContextCommitDoesNotBlockAnUnrelatedChunk() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(2)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl slow = world.context(0, 0);
            ChunkContextImpl fast = world.context(1, 0);
            CountDownLatch slowStarted = new CountDownLatch(1);
            CountDownLatch releaseSlow = new CountDownLatch(1);
            CountDownLatch fastFinished = new CountDownLatch(1);

            slow.submit(0, () -> NativeTickCoordinator.runNative(
                List.of(1), ignored -> NativeTickCoordinator.deferCommit(
                    CommitScope.CONTEXT, () -> {
                        slowStarted.countDown();
                        await(releaseSlow);
                    }), () -> { }));
            assertTrue(slowStarted.await(2, TimeUnit.SECONDS));

            fast.submit(0, () -> NativeTickCoordinator.runNative(
                List.of(1), ignored -> NativeTickCoordinator.deferCommit(
                    CommitScope.CONTEXT, fastFinished::countDown), () -> { }));

            assertTrue(fastFinished.await(1, TimeUnit.SECONDS));
            releaseSlow.countDown();
        }
    }

    @Test
    void worldCommitLanesSerializeOneWorldButNotDifferentWorlds() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(3)) {
            WorldContextImpl firstWorld = new WorldContextImpl(scheduler, null);
            WorldContextImpl secondWorld = new WorldContextImpl(scheduler, null);
            ChunkContextImpl first = firstWorld.context(0, 0);
            ChunkContextImpl sameWorld = firstWorld.context(1, 0);
            ChunkContextImpl otherWorld = secondWorld.context(0, 0);
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch sameWorldFinished = new CountDownLatch(1);
            CountDownLatch otherWorldFinished = new CountDownLatch(1);

            first.submit(0, () -> NativeTickCoordinator.runNative(
                List.of(1), ignored -> NativeTickCoordinator.deferCommit(
                    CommitScope.WORLD, () -> {
                        firstStarted.countDown();
                        await(releaseFirst);
                    }), () -> { })).get(2, TimeUnit.SECONDS);
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

            sameWorld.submit(0, () -> NativeTickCoordinator.runNative(
                List.of(1), ignored -> NativeTickCoordinator.deferCommit(
                    CommitScope.WORLD, sameWorldFinished::countDown), () -> { }));
            otherWorld.submit(0, () -> NativeTickCoordinator.runNative(
                List.of(1), ignored -> NativeTickCoordinator.deferCommit(
                    CommitScope.WORLD, otherWorldFinished::countDown), () -> { }));

            assertFalse(sameWorldFinished.await(50, TimeUnit.MILLISECONDS),
                "one world's owner sequence must remain serial");
            assertTrue(otherWorldFinished.await(1, TimeUnit.SECONDS),
                "another world's lane must remain independent");
            releaseFirst.countDown();
            assertTrue(sameWorldFinished.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void nextEntityBatchWaitsForPreviousGlobalIndexCommit() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(2)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl context = world.context(0, 0);
            NativeEntityLane lane = context.entityLane();
            Entity first = new OwnedEntity(context);
            Entity second = new OwnedEntity(context);
            CountDownLatch firstTicked = new CountDownLatch(1);
            CountDownLatch secondTicked = new CountDownLatch(1);
            AtomicBoolean published = new AtomicBoolean();

            lane.offer(List.of(first), ignored -> {
                NativeTickCoordinator.deferGlobalCommit(() -> published.set(true));
                firstTicked.countDown();
            });
            lane.offer(List.of(second), ignored -> {
                assertTrue(published.get(),
                    "the preceding owner transaction must be published first");
                secondTicked.countDown();
            });

            assertTrue(firstTicked.await(2, TimeUnit.SECONDS));
            assertFalse(secondTicked.await(50, TimeUnit.MILLISECONDS));
            NativeTickCoordinator.pumpMainThread();
            assertTrue(secondTicked.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void nativeAttachmentsCoalesceOneBatchPerOwnedTransaction() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(1)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl context = world.context(0, 0);
            List<Entity> entities = List.of(
                new OwnedEntity(context), new OwnedEntity(context), new OwnedEntity(context));
            Object key = new Object();
            AtomicInteger creations = new AtomicInteger();
            AtomicReference<List<Integer>> observed = new AtomicReference<>();
            CountDownLatch ticked = new CountDownLatch(entities.size());

            context.entityLane().offer(entities, ignored -> {
                List<Integer> attachment = NativeTickCoordinator.nativeAttachment(
                    key, () -> {
                        creations.incrementAndGet();
                        List<Integer> created = new ArrayList<>();
                        observed.set(created);
                        return created;
                    });
                attachment.add(attachment.size());
                ticked.countDown();
            });

            assertTrue(ticked.await(2, TimeUnit.SECONDS));
            context.submit(0, () -> { }).get(2, TimeUnit.SECONDS);
            assertEquals(1, creations.get());
            assertEquals(List.of(0, 1, 2), observed.get());
        }
    }

    @Test
    void reusedNativeFrameDoesNotLeakAttachmentsOrCommits() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(1)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl context = world.context(0, 0);
            Object key = new Object();
            AtomicInteger attachmentCreations = new AtomicInteger();
            AtomicInteger committed = new AtomicInteger();

            for (int value : List.of(1, 10)) {
                context.submit(0, () -> NativeTickCoordinator.runNative(
                    List.of(value), ignored -> {
                        List<Integer> attachment = NativeTickCoordinator.nativeAttachment(
                            key, () -> {
                                attachmentCreations.incrementAndGet();
                                return new ArrayList<>();
                            });
                        assertTrue(attachment.isEmpty(),
                            "attachments must be transaction-local");
                        attachment.add(value);
                        NativeTickCoordinator.deferGlobalCommit(
                            () -> committed.addAndGet(value));
                    }, () -> { })).get(2, TimeUnit.SECONDS);
            }

            assertEquals(2, attachmentCreations.get());
            assertEquals(0, committed.get());
            NativeTickCoordinator.pumpMainThread();
            assertEquals(11, committed.get());
        }
    }

    @Test
    void globalCommitPumpStopsAtItsLinearizedGenerationBoundary() {
        List<Integer> committed = new ArrayList<>();
        NativeTickCoordinator.submitMainThread(() -> {
            committed.add(1);
            NativeTickCoordinator.submitMainThread(() -> committed.add(2));
        });

        NativeTickCoordinator.pumpMainThread();
        assertEquals(List.of(1), committed,
            "a producer running during a drain must publish into the next generation");

        NativeTickCoordinator.pumpMainThread();
        assertEquals(List.of(1, 2), committed);
    }

    @Test
    void reentrantGlobalCommitPumpCannotCrossTheOuterGenerationBoundary() {
        List<Integer> committed = new ArrayList<>();
        NativeTickCoordinator.submitMainThread(() -> {
            committed.add(1);
            NativeTickCoordinator.submitMainThread(() -> committed.add(2));
            NativeTickCoordinator.pumpMainThread();
            committed.add(3);
        });

        NativeTickCoordinator.pumpMainThread();
        assertEquals(List.of(1, 3), committed,
            "a nested distance-manager pump must not consume the next generation");

        NativeTickCoordinator.pumpMainThread();
        assertEquals(List.of(1, 3, 2), committed);
    }

    @Test
    void entityLaneSubmitsOneOwnedTaskForOneChunkTickBatch() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(1)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl context = world.context(0, 0);
            List<Entity> entities = List.of(
                new OwnedEntity(context), new OwnedEntity(context), new OwnedEntity(context));
            CountDownLatch ticked = new CountDownLatch(entities.size());

            context.entityLane().offer(entities, ignored -> ticked.countDown());

            assertTrue(ticked.await(2, TimeUnit.SECONDS));
            assertEquals(1L, context.snapshot().submittedTasks());
        }
    }

    @Test
    void failedEntityDoesNotPreventTheRestOfItsChunkBatchFromTicking() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(1)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl context = world.context(0, 0);
            List<Entity> entities = List.of(
                new OwnedEntity(context), new OwnedEntity(context), new OwnedEntity(context));
            CountDownLatch attempted = new CountDownLatch(entities.size());
            AtomicInteger index = new AtomicInteger();

            context.entityLane().offer(entities, ignored -> {
                attempted.countDown();
                if (index.getAndIncrement() == 0) throw new IllegalStateException("expected");
            });

            assertTrue(attempted.await(2, TimeUnit.SECONDS));
            context.submit(0, () -> { }).get(2, TimeUnit.SECONDS);
            assertEquals(1L, context.snapshot().failedTasks());
        }
    }

    @Test
    void nativeChunkLanesDoNotCreateAGlobalTickBarrier() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(2)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            NativeChunkLane slow = world.context(0, 0).chunkLane();
            NativeChunkLane fast = world.context(1, 0).chunkLane();
            CountDownLatch slowStarted = new CountDownLatch(1);
            CountDownLatch releaseSlow = new CountDownLatch(1);
            CountDownLatch fastFinished = new CountDownLatch(1);

            slow.offer(new LevelChunk(), ignored -> {
                slowStarted.countDown();
                await(releaseSlow);
            });
            assertTrue(slowStarted.await(2, TimeUnit.SECONDS));
            fast.offer(new LevelChunk(), ignored -> fastFinished.countDown());

            assertTrue(fastFinished.await(1, TimeUnit.SECONDS));
            releaseSlow.countDown();
        }
    }

    @Test
    void nativeBlockEntityLanesDoNotCreateAGlobalTickBarrier() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(2)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            NativeBlockEntityLane slow = world.context(0, 0).blockEntityLane();
            NativeBlockEntityLane fast = world.context(1, 0).blockEntityLane();
            CountDownLatch slowStarted = new CountDownLatch(1);
            CountDownLatch releaseSlow = new CountDownLatch(1);
            CountDownLatch fastFinished = new CountDownLatch(1);

            slow.offer(List.of(new TestBlockEntity(0, 0)), ignored -> {
                slowStarted.countDown();
                await(releaseSlow);
            });
            assertTrue(slowStarted.await(2, TimeUnit.SECONDS));
            fast.offer(List.of(new TestBlockEntity(16, 0)), ignored ->
                fastFinished.countDown());

            assertTrue(fastFinished.await(1, TimeUnit.SECONDS));
            releaseSlow.countDown();
        }
    }

    @Test
    void nativeBlockEntityLaneDoesNotDropQueuedTicks() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(1)) {
            NativeBlockEntityLane lane = new WorldContextImpl(scheduler, null)
                .context(0, 0).blockEntityLane();
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch secondFinished = new CountDownLatch(1);
            AtomicInteger executions = new AtomicInteger();

            lane.offer(List.of(new TestBlockEntity(0, 0)), ignored -> {
                executions.incrementAndGet();
                firstStarted.countDown();
                await(releaseFirst);
            });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            lane.offer(List.of(new TestBlockEntity(0, 0)), ignored -> {
                executions.incrementAndGet();
                secondFinished.countDown();
            });
            releaseFirst.countDown();

            assertTrue(secondFinished.await(2, TimeUnit.SECONDS));
            assertEquals(2, executions.get());
        }
    }

    private record TestBlockEntity(BlockPos position) implements TickingBlockEntity {
        private TestBlockEntity(int x, int z) {
            this(new BlockPos(x, 64, z));
        }

        @Override public void tick() { }
        @Override public boolean isRemoved() { return false; }
        @Override public BlockPos getPos() { return position; }
        @Override public String getType() { return "test"; }
    }

    @Test
    void externalEntityWorkUsesTheSameOwnerMailbox() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(2)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl context = world.context(0, 0);
            OwnedEntity entity = new OwnedEntity(context);
            CountDownLatch entityTickStarted = new CountDownLatch(1);
            CountDownLatch releaseEntityTick = new CountDownLatch(1);
            CountDownLatch externalFinished = new CountDownLatch(1);
            AtomicBoolean ranInOwner = new AtomicBoolean();

            context.entityLane().offer(List.of(entity), ignored -> {
                entityTickStarted.countDown();
                await(releaseEntityTick);
            });
            assertTrue(entityTickStarted.await(2, TimeUnit.SECONDS));

            assertTrue(scheduler.routeEntityTask(entity, () -> {
                ranInOwner.set(context.current());
                externalFinished.countDown();
            }));
            assertFalse(externalFinished.await(50, TimeUnit.MILLISECONDS));

            releaseEntityTick.countDown();
            assertTrue(externalFinished.await(2, TimeUnit.SECONDS));
            assertTrue(ranInOwner.get());
        }
    }

    private static class OwnedEntity extends Entity
        implements EntityContextOwnerBridge {
        private volatile Object owner;

        private OwnedEntity(Object owner) {
            this.owner = owner;
        }

        @Override public Object aerogel$contextOwner() { return owner; }
        @Override public void aerogel$contextOwner(Object owner) { this.owner = owner; }
        @Override public synchronized boolean aerogel$compareAndSetContextOwner(
            Object expected, Object updated
        ) {
            if (owner != expected) return false;
            owner = updated;
            return true;
        }
    }

    @Test
    void slowChunkDoesNotDelayIndependentChunk() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(2)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl slow = world.context(0, 0);
            ChunkContextImpl fast = world.context(8, 8);
            CountDownLatch slowStarted = new CountDownLatch(1);
            CountDownLatch releaseSlow = new CountDownLatch(1);

            CompletableFuture<Void> slowResult = slow.submit(0, () -> {
                slowStarted.countDown();
                await(releaseSlow);
            });
            assertTrue(slowStarted.await(2, TimeUnit.SECONDS));

            CompletableFuture<Void> fastResult = fast.submit(0, () -> { });
            fastResult.get(1, TimeUnit.SECONDS);
            assertFalse(slowResult.isDone());

            releaseSlow.countDown();
            slowResult.get(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void oneContextAlwaysHasOneConsumer() {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(4)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl context = world.context(0, 0);
            AtomicInteger active = new AtomicInteger();
            AtomicInteger maximum = new AtomicInteger();
            List<CompletableFuture<Void>> results = new ArrayList<>();

            for (int index = 0; index < 1_000; index++) {
                results.add(context.submit(0, () -> {
                    int current = active.incrementAndGet();
                    maximum.accumulateAndGet(current, Math::max);
                    Thread.onSpinWait();
                    active.decrementAndGet();
                }));
            }
            CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).join();

            assertEquals(1, maximum.get());
            assertEquals(1_000L, context.snapshot().completedTasks());
        }
    }

    @Test
    void chunkMsptAggregatesAllContextWorkByMinecraftTick() {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(1)) {
            ChunkContextImpl context = new WorldContextImpl(scheduler, null).context(0, 0);

            NativeTickCoordinator.beginServerTick();
            CompletableFuture.allOf(
                context.submit(0, Thread::onSpinWait),
                context.submit(0, Thread::onSpinWait)
            ).join();
            var first = context.snapshot();
            assertEquals(1L, first.measuredTicks());
            assertEquals(first.totalExecutionNanos(), first.maximumExecutionNanos());
            assertEquals(first.totalExecutionNanos() / 1_000_000.0D,
                first.averageExecutionMillis(), 0.0D);
            assertEquals(first.totalExecutionNanos(), first.recentExecutionNanos());

            NativeTickCoordinator.beginServerTick();
            context.submit(0, Thread::onSpinWait).join();
            var second = context.snapshot();
            assertEquals(2L, second.measuredTicks());
            assertEquals(second.totalExecutionNanos() / 2_000_000.0D,
                second.averageExecutionMillis(), 0.0D);
            assertEquals(
                second.totalExecutionNanos() - first.totalExecutionNanos(),
                second.recentExecutionNanos());
        }
    }

    @Test
    void neighborhoodReservationExcludesAdjacentMutation() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(3)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl center = world.context(0, 0);
            ChunkContextImpl adjacent = world.context(1, 0);
            CountDownLatch neighborhoodStarted = new CountDownLatch(1);
            CountDownLatch releaseNeighborhood = new CountDownLatch(1);
            AtomicBoolean adjacentRan = new AtomicBoolean();

            CompletableFuture<Void> reservation = center.submit(1, () -> {
                center.assertCurrent();
                adjacent.assertCurrent();
                neighborhoodStarted.countDown();
                await(releaseNeighborhood);
            });
            assertTrue(neighborhoodStarted.await(2, TimeUnit.SECONDS));
            CompletableFuture<Void> adjacentResult = adjacent.submit(0,
                () -> adjacentRan.set(true));

            assertFalse(adjacentResult.isDone());
            assertFalse(adjacentRan.get());
            releaseNeighborhood.countDown();
            CompletableFuture.allOf(reservation, adjacentResult).get(2, TimeUnit.SECONDS);
            assertTrue(adjacentRan.get());
        }
    }

    @Test
    void releasingNeighborhoodOwnershipFinishesAnOtherwiseIdleTick() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(2)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl primary = world.context(0, 0);
            ChunkContextImpl secondary = world.context(1, 0);
            CountDownLatch neighborhoodStarted = new CountDownLatch(1);
            CountDownLatch releaseNeighborhood = new CountDownLatch(1);
            CountDownLatch nextTickAdmitted = new CountDownLatch(1);

            CompletableFuture<Void> reservation = primary.submit(
                new long[] { primary.key(), secondary.key() }, () -> {
                    neighborhoodStarted.countDown();
                    await(releaseNeighborhood);
                });
            assertTrue(neighborhoodStarted.await(2, TimeUnit.SECONDS));

            NativeTickToken blocked = new NativeTickToken(1L);
            secondary.offerTickTask(blocked,
                secondary::completeTickTask, () -> { });
            blocked.seal();

            NativeTickToken pending = new NativeTickToken(2L);
            secondary.offerTickTask(pending, state -> {
                nextTickAdmitted.countDown();
                secondary.completeTickTask(state);
            }, () -> { });
            pending.seal();

            releaseNeighborhood.countDown();
            reservation.get(2, TimeUnit.SECONDS);
            assertTrue(nextTickAdmitted.await(2, TimeUnit.SECONDS),
                "releasing the final ownership blocker must recheck tick completion");
        }
    }

    @Test
    void registeringAContextAfterTokenSealStillClosesItsInput() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(1)) {
            ChunkContextImpl context =
                new WorldContextImpl(scheduler, null).context(0, 0);
            CountDownLatch nextTickAdmitted = new CountDownLatch(1);

            NativeTickToken alreadySealed = new NativeTickToken(1L);
            alreadySealed.seal();
            context.offerTickTask(alreadySealed,
                context::completeTickTask, () -> { });

            NativeTickToken next = new NativeTickToken(2L);
            context.offerTickTask(next, state -> {
                nextTickAdmitted.countDown();
                context.completeTickTask(state);
            }, () -> { });
            next.seal();

            assertTrue(nextTickAdmitted.await(2, TimeUnit.SECONDS),
                "a late registration must observe an already-closed token");
        }
    }

    @Test
    void sealingATickDoesNotSettleItsContextsOnTheProducerThread() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(1)) {
            ChunkContextImpl context =
                new WorldContextImpl(scheduler, null).context(0, 0);
            NativeTickToken active = new NativeTickToken(1L);
            NativeTickToken pending = new NativeTickToken(2L);
            CountDownLatch pendingActivated = new CountDownLatch(1);
            AtomicReference<Thread> settlementThread = new AtomicReference<>();

            context.offerTickTask(active, context::completeTickTask, () -> { });
            context.offerTickTask(pending, state -> {
                settlementThread.set(Thread.currentThread());
                context.completeTickTask(state);
                pendingActivated.countDown();
            }, () -> { });
            pending.seal();

            Thread producerThread = Thread.currentThread();
            active.seal();

            assertTrue(pendingActivated.await(2, TimeUnit.SECONDS));
            assertTrue(settlementThread.get() != producerThread,
                "the producer must publish token closure without scanning Contexts");
        }
    }

    @Test
    void opposingNeighborhoodRequestsCompleteWithoutDeadlock() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(4)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            CompletableFuture<Void> left = world.context(0, 0).submit(1, () -> { });
            CompletableFuture<Void> right = world.context(1, 0).submit(1, () -> { });

            CompletableFuture.allOf(left, right).get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void exactScopeReservationCannotBeStarvedByLaterSingleContextTicks() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(4)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl primary = world.context(0, 0);
            ChunkContextImpl occupied = world.context(1, 0);
            CountDownLatch occupiedStarted = new CountDownLatch(1);
            CountDownLatch releaseOccupied = new CountDownLatch(1);
            AtomicBoolean scopeRan = new AtomicBoolean();
            AtomicInteger localsBeforeScope = new AtomicInteger();

            CompletableFuture<Void> blocker = occupied.submit(0, () -> {
                occupiedStarted.countDown();
                await(releaseOccupied);
            });
            assertTrue(occupiedStarted.await(2, TimeUnit.SECONDS));

            CompletableFuture<Void> scoped = primary.submit(
                new long[] { primary.key(), occupied.key() },
                () -> scopeRan.set(true));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!occupied.reservedFor(primary) && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertTrue(occupied.reservedFor(primary));

            List<CompletableFuture<Void>> localTicks = new ArrayList<>();
            for (int index = 0; index < 100; index++) {
                localTicks.add(occupied.submit(0, () -> {
                    if (!scopeRan.get()) localsBeforeScope.incrementAndGet();
                }));
            }

            releaseOccupied.countDown();
            CompletableFuture.allOf(blocker, scoped).get(2, TimeUnit.SECONDS);
            CompletableFuture.allOf(localTicks.toArray(CompletableFuture[]::new))
                .get(2, TimeUnit.SECONDS);
            assertEquals(0, localsBeforeScope.get());
        }
    }

    @Test
    void exactScopeBlocksOnlyDeclaredContexts() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(4)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl primary = world.context(0, 0);
            ChunkContextImpl declared = world.context(100, 100);
            ChunkContextImpl undeclared = world.context(1, 0);
            CountDownLatch scopeStarted = new CountDownLatch(1);
            CountDownLatch releaseScope = new CountDownLatch(1);

            CompletableFuture<Void> scoped = primary.submit(
                new long[] { ChunkPos.pack(0, 0), ChunkPos.pack(100, 100) }, () -> {
                    primary.assertCurrent();
                    declared.assertCurrent();
                    assertThrows(IllegalStateException.class, undeclared::assertCurrent);
                    scopeStarted.countDown();
                    await(releaseScope);
                });
            assertTrue(scopeStarted.await(2, TimeUnit.SECONDS));

            CompletableFuture<Void> independent = undeclared.submit(0, () -> { });
            independent.get(1, TimeUnit.SECONDS);
            CompletableFuture<Void> conflicting = declared.submit(0, () -> { });
            assertFalse(conflicting.isDone());

            releaseScope.countDown();
            CompletableFuture.allOf(scoped, conflicting).get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void unloadRejectsQueuedWorkFromTheOldEpoch() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(2)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl context = world.context(0, 0);
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CompletableFuture<Void> running = context.submit(0, () -> {
                started.countDown();
                await(release);
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            CompletableFuture<Void> stale = context.submit(0, () -> { });

            context.deactivate();
            release.countDown();
            running.get(1, TimeUnit.SECONDS);

            assertThrows(Exception.class, () -> stale.get(1, TimeUnit.SECONDS));
            assertEquals("CLOSED", context.snapshot().lifecycle());
            assertEquals(1L, context.snapshot().staleTasks());
        }
    }

    @Test
    void interactiveWorkKeepsFifoOrderAndRunsBeforeTickBacklog() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(1)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl context = world.context(0, 0);
            CountDownLatch running = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch finished = new CountDownLatch(3);
            List<String> order = java.util.Collections.synchronizedList(
                new ArrayList<>());

            assertTrue(context.submitNative(() -> {
                running.countDown();
                await(release);
            }, () -> { }));
            assertTrue(running.await(2, TimeUnit.SECONDS));
            assertTrue(context.submitNative(() -> {
                order.add("tick");
                finished.countDown();
            }, () -> { }));
            assertTrue(context.submitInteractiveNative(() -> {
                assertTrue(ContextThreadState.current().interactive());
                order.add("first");
                finished.countDown();
            }, () -> { }));
            assertTrue(context.submitInteractiveNative(() -> {
                assertTrue(ContextThreadState.current().interactive());
                order.add("second");
                finished.countDown();
            }, () -> { }));

            release.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("first", "second", "tick"), order);
        }
    }

    @Test
    void concurrentDeactivationSettlesEveryNativeSubmission() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(4);
             ExecutorService submitters = Executors.newFixedThreadPool(4)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl context = world.context(0, 0);
            int submissionsPerThread = 2_000;
            int total = 4 * submissionsPerThread;
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch submitted = new CountDownLatch(4);
            CountDownLatch settled = new CountDownLatch(total);
            AtomicInteger attemptsStarted = new AtomicInteger();

            for (int thread = 0; thread < 4; thread++) {
                submitters.execute(() -> {
                    await(start);
                    for (int index = 0; index < submissionsPerThread; index++) {
                        attemptsStarted.incrementAndGet();
                        Runnable completion = settled::countDown;
                        if (!context.submitNative(completion, completion)) {
                            completion.run();
                        }
                    }
                    submitted.countDown();
                });
            }

            start.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (attemptsStarted.get() < 1_000 && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertTrue(attemptsStarted.get() >= 1_000);
            context.deactivate();
            assertTrue(submitted.await(2, TimeUnit.SECONDS));
            assertTrue(settled.await(2, TimeUnit.SECONDS),
                "deactivation must not strand a task after its ACTIVE check");
            assertEquals(0, context.snapshot().queuedTasks());
        }
    }

    @Test
    void orderedDrainRunsAcceptedWorkBeforeUnloadCommit() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(2)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl context = world.context(0, 0);
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger order = new AtomicInteger();
            CompletableFuture<Void> running = context.submit(0, () -> {
                started.countDown();
                await(release);
                assertEquals(1, order.incrementAndGet());
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            CompletableFuture<Void> accepted = context.submit(0,
                () -> assertEquals(2, order.incrementAndGet()));
            AtomicBoolean committed = new AtomicBoolean();

            assertTrue(context.drainThen(() -> {
                assertEquals(3, order.incrementAndGet());
                committed.set(true);
            }));
            assertThrows(java.util.concurrent.RejectedExecutionException.class,
                () -> context.execute(() -> { }));
            release.countDown();
            CompletableFuture.allOf(running, accepted).get(2, TimeUnit.SECONDS);

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while ((!context.closed() || !committed.get())
                && System.nanoTime() < deadline) {
                NativeTickCoordinator.pumpMainThread();
                Thread.onSpinWait();
            }
            assertTrue(context.closed());
            assertTrue(committed.get());
            assertEquals(3, order.get());
        }
    }


    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static void offerTickTask(
        ChunkContextImpl context,
        NativeTickToken token,
        Runnable action,
        Runnable superseded
    ) {
        context.offerTickTask(token, state -> {
            Runnable complete = () -> context.completeTickTask(state);
            if (!context.submitNative(() -> {
                try {
                    action.run();
                } finally {
                    complete.run();
                }
            }, complete)) {
                complete.run();
            }
        }, superseded);
    }
}
