package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.NativeTickCoordinator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Makes POI selection linearizable without a global main-thread owner. */
@Mixin(targets = "net.minecraft.world.entity.ai.village.poi.PoiManager")
abstract class PoiManagerMixin {
    @Shadow public abstract Stream<net.minecraft.world.entity.ai.village.poi.PoiRecord>
        getInRange(Predicate<Holder<PoiType>> type, BlockPos position, int radius,
            PoiManager.Occupancy occupancy);
    @Shadow protected abstract void setDirty(long sectionKey);
    @Invoker("isVillageCenter") protected abstract boolean aerogel$isVillageCenter(long sectionKey);

    @Inject(method = "take", at = @At("HEAD"), cancellable = true)
    private void aerogel$acquireFirstAvailablePoi(
        Predicate<Holder<PoiType>> type,
        BiPredicate<Holder<PoiType>, BlockPos> positionFilter,
        BlockPos position,
        int radius,
        CallbackInfoReturnable<Optional<BlockPos>> callback
    ) {
        Iterator<net.minecraft.world.entity.ai.village.poi.PoiRecord> candidates =
            getInRange(type, position, radius, PoiManager.Occupancy.HAS_SPACE)
                .filter(record -> positionFilter.test(record.getPoiType(), record.getPos()))
                .iterator();
        while (candidates.hasNext()) {
            net.minecraft.world.entity.ai.village.poi.PoiRecord record = candidates.next();
            if (((PoiRecordAccess) (Object) record).aerogel$acquireTicket()) {
                callback.setReturnValue(Optional.of(record.getPos()));
                return;
            }
        }
        callback.setReturnValue(Optional.empty());
    }

    @Inject(method = "setDirty", at = @At("HEAD"), cancellable = true)
    private void aerogel$commitDistanceAndPersistenceWithoutWaiting(
        long sectionKey, org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback
    ) {
        if (!NativeTickCoordinator.isNativeWorker()) return;
        if (NativeTickCoordinator.deferGlobalCommit(() -> setDirty(sectionKey))) {
            callback.cancel();
        }
    }

    @Inject(method = "sectionsToVillage", at = @At("HEAD"), cancellable = true)
    private void aerogel$computeExactPublishedVillageDistance(
        net.minecraft.core.SectionPos origin,
        CallbackInfoReturnable<Integer> callback
    ) {
        if (!NativeTickCoordinator.isNativeWorker()) return;
        long originKey = origin.asLong();
        for (int distance = 0; distance <= PoiManager.MAX_VILLAGE_DISTANCE; distance++) {
            for (int x = -distance; x <= distance; x++) {
                for (int y = -distance; y <= distance; y++) {
                    for (int z = -distance; z <= distance; z++) {
                        if (Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z))
                            != distance) continue;
                        long sectionKey = net.minecraft.core.SectionPos.offset(
                            originKey, x, y, z);
                        if (aerogel$isVillageCenter(sectionKey)) {
                            callback.setReturnValue(distance);
                            return;
                        }
                    }
                }
            }
        }
        callback.setReturnValue(PoiManager.MAX_VILLAGE_DISTANCE + 1);
    }
}
