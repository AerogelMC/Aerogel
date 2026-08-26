package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.NativeTickCoordinator;
import dev.aerogel.loader.context.ConcurrentIngress;
import dev.aerogel.loader.context.ScheduledTickQueryScope;
import dev.aerogel.loader.internal.LevelTicksBridge;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.level.ChunkPos;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiConsumer;
import java.util.function.LongPredicate;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Queue;
import net.minecraft.core.BlockPos;

@Mixin(targets = "net.minecraft.world.ticks.LevelTicks")
abstract class LevelTicksMixin<T> implements LevelTicksBridge {
    @Shadow public abstract void schedule(ScheduledTick<T> tick);
    @Shadow @Final private LongPredicate tickCheck;
    @Shadow @Final private Long2ObjectMap<LevelChunkTicks<T>> allContainers;
    @Shadow @Final private Long2LongMap nextTickForContainer;
    @Shadow @Final private Queue<LevelChunkTicks<T>> containersToTick;
    @Shadow @Final private Queue<ScheduledTick<T>> toRunThisTick;
    @Unique private TreeMap<Long, LongOpenHashSet> aerogel$dueByTime;
    @Unique private Long2LongOpenHashMap aerogel$indexedDue;
    @Unique private Long2LongOpenHashMap aerogel$inactiveDue;
    @Unique private ConcurrentIngress<Long> aerogel$eligibilityChanges;
    @Unique private ScheduledTickQueryScope.Snapshot aerogel$querySnapshot;
    @Unique private int aerogel$dispatchOrder;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$initializeIngress(
        LongPredicate tickCheck, CallbackInfo callback
    ) {
        aerogel$dueByTime = new TreeMap<>();
        aerogel$indexedDue = new Long2LongOpenHashMap();
        aerogel$inactiveDue = new Long2LongOpenHashMap();
        aerogel$eligibilityChanges = new ConcurrentIngress<>();
    }

    @Inject(method = "schedule(Lnet/minecraft/world/ticks/ScheduledTick;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$commitScheduledTick(ScheduledTick<T> tick, CallbackInfo callback) {
        if (!NativeTickCoordinator.isNativeWorker()) return;
        /*
         * A schedule is an owner mutation, not next-tick ingress. Publishing it
         * with the native transaction keeps it ahead of the same Context's unload
         * fence. One attachment batches every schedule produced by this LevelTicks
         * instance in the transaction, so this adds neither one global queue node
         * nor one wake-up per scheduled tick.
         */
        ArrayList<ScheduledTick<T>> batch = NativeTickCoordinator.nativeAttachment(
            this, () -> {
                ArrayList<ScheduledTick<T>> created = new ArrayList<>();
                if (!NativeTickCoordinator.deferGlobalCommit(() -> {
                    for (ScheduledTick<T> scheduled : created) schedule(scheduled);
                })) {
                    throw new IllegalStateException(
                        "Scheduled tick publication escaped its native transaction");
                }
                return created;
            });
        if (batch == null) {
            throw new IllegalStateException(
                "Scheduled tick publication has no native transaction");
        }
        batch.add(tick);
        callback.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void aerogel$mergeOwnedSchedules(
        long gameTime, int maximumTicks, BiConsumer<BlockPos, T> ticker,
        CallbackInfo callback
    ) {
        aerogel$eligibilityChanges.drain(this::aerogel$recheckEligibility);
    }

    @Inject(method = "runCollectedTicks", at = @At("HEAD"))
    private void aerogel$indexCollectedTickOrder(
        BiConsumer<BlockPos, T> ticker, CallbackInfo callback
    ) {
        aerogel$querySnapshot = ScheduledTickQueryScope.snapshot(toRunThisTick);
        aerogel$dispatchOrder = 0;
    }

    @SuppressWarnings("unchecked")
    @Redirect(
        method = "runCollectedTicks",
        at = @At(value = "INVOKE", target = "Ljava/util/function/BiConsumer;"
            + "accept(Ljava/lang/Object;Ljava/lang/Object;)V")
    )
    private void aerogel$publishCollectedTickView(
        BiConsumer<BlockPos, T> ticker, Object position, Object type
    ) {
        int order = aerogel$dispatchOrder++;
        ScheduledTickQueryScope.Snapshot snapshot = aerogel$querySnapshot;
        ScheduledTickQueryScope.run((LevelTicks<T>) (Object) this, snapshot, order,
            () -> ticker.accept((BlockPos) position, (T) type));
    }

    @Inject(method = "runCollectedTicks", at = @At("RETURN"))
    private void aerogel$releaseCollectedTickOrder(
        BiConsumer<BlockPos, T> ticker, CallbackInfo callback
    ) {
        aerogel$querySnapshot = null;
    }

    @Inject(method = "willTickThisTick", at = @At("HEAD"), cancellable = true)
    private void aerogel$queryRoutedTickView(
        BlockPos position, T type, CallbackInfoReturnable<Boolean> callback
    ) {
        Boolean result = ScheduledTickQueryScope.willTick(this, position, type);
        if (result != null) callback.setReturnValue(result);
    }

    @Inject(method = "addContainer", at = @At("RETURN"))
    private void aerogel$indexAddedContainer(
        ChunkPos position, LevelChunkTicks<T> container, CallbackInfo callback
    ) {
        ScheduledTick<T> next = container.peek();
        if (next != null) aerogel$indexDue(position.pack(), next.triggerTick());
    }

    @Inject(method = "updateContainerScheduling", at = @At("RETURN"))
    private void aerogel$indexUpdatedContainer(
        ScheduledTick<T> tick, CallbackInfo callback
    ) {
        aerogel$indexDue(ChunkPos.pack(tick.pos()), tick.triggerTick());
    }

    /**
     * Vanilla scans every scheduled chunk on every server tick. The indexed form
     * performs the same validation and preserves the vanilla drain queue ordering,
     * but visits only containers whose exact next trigger time has arrived.
     */
    @Inject(method = "sortContainersToTick", at = @At("HEAD"), cancellable = true)
    private void aerogel$selectDueContainers(long gameTime, CallbackInfo callback) {
        while (!aerogel$dueByTime.isEmpty()
            && aerogel$dueByTime.firstKey() <= gameTime) {
            var due = aerogel$dueByTime.pollFirstEntry();
            long triggerTick = due.getKey();
            LongIterator chunks = due.getValue().iterator();
            while (chunks.hasNext()) {
                long chunkKey = chunks.nextLong();
                if (!aerogel$indexedDue.containsKey(chunkKey)
                    || aerogel$indexedDue.get(chunkKey) != triggerTick) continue;
                aerogel$indexedDue.remove(chunkKey);

                if (!nextTickForContainer.containsKey(chunkKey)) continue;
                long recorded = nextTickForContainer.get(chunkKey);
                if (recorded != triggerTick) {
                    aerogel$indexDue(chunkKey, recorded);
                    continue;
                }

                LevelChunkTicks<T> container = allContainers.get(chunkKey);
                if (container == null) {
                    nextTickForContainer.remove(chunkKey);
                    continue;
                }
                ScheduledTick<T> next = container.peek();
                if (next == null) {
                    nextTickForContainer.remove(chunkKey);
                    continue;
                }
                long actualTrigger = next.triggerTick();
                if (actualTrigger > gameTime) {
                    nextTickForContainer.put(chunkKey, actualTrigger);
                    aerogel$indexDue(chunkKey, actualTrigger);
                    continue;
                }
                if (!tickCheck.test(chunkKey)) {
                    aerogel$inactiveDue.put(chunkKey, triggerTick);
                    continue;
                }
                nextTickForContainer.remove(chunkKey);
                containersToTick.add(container);
            }
        }
        callback.cancel();
    }

    @Inject(method = "removeContainer", at = @At("RETURN"))
    private void aerogel$removeContainerIndex(ChunkPos position, CallbackInfo callback) {
        long chunkKey = position.pack();
        aerogel$inactiveDue.remove(chunkKey);
        if (!aerogel$indexedDue.containsKey(chunkKey)) return;
        long triggerTick = aerogel$indexedDue.remove(chunkKey);
        LongOpenHashSet bucket = aerogel$dueByTime.get(triggerTick);
        if (bucket == null) return;
        bucket.remove(chunkKey);
        if (bucket.isEmpty()) aerogel$dueByTime.remove(triggerTick);
    }

    @Override
    public void aerogel$eligibilityChanged(long chunkKey) {
        aerogel$eligibilityChanges.offer(Long.valueOf(chunkKey));
    }

    @Unique
    private void aerogel$recheckEligibility(Long boxedKey) {
        long chunkKey = boxedKey.longValue();
        if (!aerogel$inactiveDue.containsKey(chunkKey)) return;
        long triggerTick = aerogel$inactiveDue.remove(chunkKey);
        if (nextTickForContainer.containsKey(chunkKey)) {
            aerogel$indexDue(chunkKey, triggerTick);
        }
    }

    @Unique
    private void aerogel$indexDue(long chunkKey, long triggerTick) {
        aerogel$inactiveDue.remove(chunkKey);
        if (aerogel$indexedDue.containsKey(chunkKey)) {
            long previous = aerogel$indexedDue.get(chunkKey);
            if (previous == triggerTick) return;
            LongOpenHashSet previousBucket = aerogel$dueByTime.get(previous);
            if (previousBucket != null) {
                previousBucket.remove(chunkKey);
                if (previousBucket.isEmpty()) aerogel$dueByTime.remove(previous);
            }
        }
        aerogel$indexedDue.put(chunkKey, triggerTick);
        aerogel$dueByTime.computeIfAbsent(
            triggerTick, ignored -> new LongOpenHashSet()).add(chunkKey);
    }

}
