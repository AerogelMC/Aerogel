package dev.aerogel.loader.mixin.core;

import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PoiRecord.class)
public interface PoiRecordAccess {
    @Invoker("acquireTicket")
    boolean aerogel$acquireTicket();
}
