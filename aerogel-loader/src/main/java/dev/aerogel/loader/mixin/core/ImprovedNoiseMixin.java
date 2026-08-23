package dev.aerogel.loader.mixin.core;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.levelgen.synth.ImprovedNoise")
abstract class ImprovedNoiseMixin {
    @Shadow @Final private byte[] p;
    @Unique private int[] aerogel$permutation;


    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$expandPermutation(CallbackInfo callback) {
        int[] expanded = new int[256];
        for (int index = 0; index < expanded.length; index++) expanded[index] = p[index] & 255;
        aerogel$permutation = expanded;
    }

    /**
     * @author Aerogel
     * @reason Read an unsigned permutation directly instead of widening and masking a byte.
     */
    @Overwrite
    private int p(int index) {
        return aerogel$permutation[index & 255];
    }

}
