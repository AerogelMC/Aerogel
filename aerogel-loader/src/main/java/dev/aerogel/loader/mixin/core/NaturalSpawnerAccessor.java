package dev.aerogel.loader.mixin.core;

import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accesses vanilla's exact 17x17 natural-spawn normalization constant. */
@Mixin(targets = "net.minecraft.world.level.NaturalSpawner")
interface NaturalSpawnerAccessor {
    @Accessor("MAGIC_NUMBER")
    static int aerogel$magicNumber() {
        throw new AssertionError();
    }
}
