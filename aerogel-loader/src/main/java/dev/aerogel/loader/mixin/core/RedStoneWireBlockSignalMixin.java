package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.ContextWorkerLocal;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps vanilla's temporary "wire does not emit" calculation state local to
 * the execution that owns it.  RedStoneWireBlock is a global block singleton;
 * its vanilla boolean is only safe because vanilla evaluates redstone on one
 * thread.  Sharing it between independent Contexts makes one circuit observe
 * another circuit's temporary false value and creates phantom power changes.
 */
@Mixin(targets = "net.minecraft.world.level.block.RedStoneWireBlock")
abstract class RedStoneWireBlockSignalMixin {
    @Unique
    private final ContextWorkerLocal<Boolean> aerogel$shouldSignal =
        ContextWorkerLocal.withInitial(() -> Boolean.TRUE);

    @Redirect(
        method = "getBlockSignal",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/"
            + "RedStoneWireBlock;shouldSignal:Z", opcode = Opcodes.PUTFIELD)
    )
    private void aerogel$setLocalSignalState(
        @Coerce Object instance, boolean shouldSignal
    ) {
        aerogel$shouldSignal.set(shouldSignal);
    }

    @Redirect(
        method = {"getDirectSignal", "getSignal", "isSignalSource"},
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/"
            + "RedStoneWireBlock;shouldSignal:Z", opcode = Opcodes.GETFIELD)
    )
    private boolean aerogel$getLocalSignalState(@Coerce Object instance) {
        return aerogel$shouldSignal.get();
    }
}
