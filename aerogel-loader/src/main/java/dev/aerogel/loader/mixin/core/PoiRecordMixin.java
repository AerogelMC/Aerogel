package dev.aerogel.loader.mixin.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(PoiRecord.class)
abstract class PoiRecordMixin {
    @Shadow private int freeTickets;
    @Shadow @Final private Holder<PoiType> poiType;
    @Shadow @Final private Runnable setDirty;
    @Unique private AtomicInteger aerogel$freeTickets;

    @Inject(method = "<init>(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Holder;"
        + "ILjava/lang/Runnable;)V", at = @At("RETURN"))
    private void aerogel$initializeAtomicTickets(
        BlockPos position, Holder<PoiType> type, int tickets, Runnable dirty,
        CallbackInfo callback
    ) {
        aerogel$freeTickets = new AtomicInteger(tickets);
    }

    @Inject(method = "getFreeTickets", at = @At("HEAD"), cancellable = true)
    private void aerogel$getTickets(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(aerogel$freeTickets.get());
    }

    @Inject(method = "hasSpace", at = @At("HEAD"), cancellable = true)
    private void aerogel$hasAtomicSpace(CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(aerogel$freeTickets.get() > 0);
    }

    @Inject(method = "isOccupied", at = @At("HEAD"), cancellable = true)
    private void aerogel$isAtomicallyOccupied(CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(aerogel$freeTickets.get() != poiType.value().maxTickets());
    }

    @Inject(method = "acquireTicket", at = @At("HEAD"), cancellable = true)
    private void aerogel$acquireAtomicTicket(CallbackInfoReturnable<Boolean> callback) {
        while (true) {
            int current = aerogel$freeTickets.get();
            if (current <= 0) {
                callback.setReturnValue(false);
                return;
            }
            if (aerogel$freeTickets.compareAndSet(current, current - 1)) {
                setDirty.run();
                callback.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "releaseTicket", at = @At("HEAD"), cancellable = true)
    private void aerogel$releaseAtomicTicket(CallbackInfoReturnable<Boolean> callback) {
        int maximum = poiType.value().maxTickets();
        while (true) {
            int current = aerogel$freeTickets.get();
            if (current >= maximum) {
                callback.setReturnValue(false);
                return;
            }
            if (aerogel$freeTickets.compareAndSet(current, current + 1)) {
                setDirty.run();
                callback.setReturnValue(true);
                return;
            }
        }
    }

    @Redirect(method = "pack", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/entity/ai/village/poi/PoiRecord;freeTickets:I"))
    private int aerogel$packAtomicTickets(PoiRecord record) {
        return aerogel$freeTickets.get();
    }
}
