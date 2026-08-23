package dev.aerogel.loader.mixin.core;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidState.class)
abstract class FluidStateMixin {
    @Shadow public abstract Fluid getType();

    @Unique private int aerogel$amount;
    @Unique private boolean aerogel$empty;
    @Unique private boolean aerogel$source;
    @Unique private float aerogel$ownHeight;
    @Unique private boolean aerogel$randomlyTicking;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$initializeCaches(CallbackInfo callback) {
        FluidState state = (FluidState) (Object) this;
        Fluid type = getType();
        aerogel$amount = type.getAmount(state);
        aerogel$empty = type.isEmpty();
        aerogel$source = type.isSource(state);
        aerogel$ownHeight = type.getOwnHeight(state);
        aerogel$randomlyTicking = type.isRandomlyTicking();
    }

    /** @author Spottedleaf, Aerogel @reason Fluid states are immutable; cache derived values. */
    @Overwrite public int getAmount() { return aerogel$amount; }

    /** @author Spottedleaf, Aerogel @reason Fluid states are immutable; cache derived values. */
    @Overwrite public boolean isEmpty() { return aerogel$empty; }

    /** @author Spottedleaf, Aerogel @reason Fluid states are immutable; cache derived values. */
    @Overwrite public boolean isSource() { return aerogel$source; }

    /** @author Spottedleaf, Aerogel @reason Fluid states are immutable; cache derived values. */
    @Overwrite public boolean isSourceOfType(Fluid fluid) {
        return aerogel$source && getType() == fluid;
    }

    /** @author Spottedleaf, Aerogel @reason Fluid states are immutable; cache derived values. */
    @Overwrite public float getOwnHeight() { return aerogel$ownHeight; }

    /** @author Spottedleaf, Aerogel @reason Fluid states are immutable; cache derived values. */
    @Overwrite public boolean isRandomlyTicking() { return aerogel$randomlyTicking; }
}
