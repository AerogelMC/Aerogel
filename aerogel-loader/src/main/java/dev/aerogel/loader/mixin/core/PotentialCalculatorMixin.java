package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.ConcurrentAppendList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "net.minecraft.world.level.PotentialCalculator")
abstract class PotentialCalculatorMixin {
    @Shadow @Final @Mutable private List<Object> charges;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$useConcurrentCharges(CallbackInfo callback) {
        charges = new ConcurrentAppendList<>();
    }
}
