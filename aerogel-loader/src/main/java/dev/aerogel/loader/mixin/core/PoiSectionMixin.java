package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.ConcurrentShort2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(targets = "net.minecraft.world.entity.ai.village.poi.PoiSection")
abstract class PoiSectionMixin {
    @Shadow @Final @Mutable private Short2ObjectMap<PoiRecord> records;

    @Inject(method = "<init>(Ljava/lang/Runnable;ZLjava/util/List;)V", at = @At("RETURN"))
    private void aerogel$publishRecordsConcurrently(
        Runnable dirty, boolean valid, java.util.List<PoiRecord> initial, CallbackInfo callback
    ) {
        ConcurrentShort2ObjectMap<PoiRecord> published = new ConcurrentShort2ObjectMap<>();
        for (PoiRecord record : records.values()) {
            published.put(net.minecraft.core.SectionPos.sectionRelativePos(record.getPos()), record);
        }
        records = published;
    }

    @Inject(method = "getRecords", at = @At("HEAD"), cancellable = true)
    private void aerogel$queryPublishedRecords(
        Predicate<Holder<PoiType>> type,
        PoiManager.Occupancy occupancy,
        CallbackInfoReturnable<Stream<PoiRecord>> callback
    ) {
        callback.setReturnValue(records.values().stream()
            .filter(record -> type.test(record.getPoiType()))
            .filter(occupancy.getTest()));
    }
}
