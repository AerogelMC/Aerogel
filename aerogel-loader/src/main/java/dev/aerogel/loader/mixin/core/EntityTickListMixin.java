package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.ConcurrentInt2ObjectMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Makes vanilla ticking membership safe to publish in its owning Chunk Context. */
@Mixin(EntityTickList.class)
abstract class EntityTickListMixin {
    @Unique private final ConcurrentInt2ObjectMap<Entity> aerogel$entities =
        new ConcurrentInt2ObjectMap<>();
    @Unique private final AtomicBoolean aerogel$iterating = new AtomicBoolean();

    /** @author Aerogel @reason Context-owned concurrent membership publication. */
    @Overwrite
    public void add(Entity entity) {
        aerogel$entities.put(entity.getId(), entity);
    }

    /** @author Aerogel @reason Context-owned concurrent membership publication. */
    @Overwrite
    public void remove(Entity entity) {
        aerogel$entities.remove(entity.getId());
    }

    /** @author Aerogel @reason Concurrent point lookup against published membership. */
    @Overwrite
    public boolean contains(Entity entity) {
        return aerogel$entities.containsKey(entity.getId());
    }

    /**
     * @author Aerogel
     * @reason Preserve vanilla's one-iteration rule and copy-on-write observation
     * semantics using the map's immutable membership image.
     */
    @Overwrite
    public void forEach(Consumer<Entity> action) {
        if (!aerogel$iterating.compareAndSet(false, true)) {
            throw new UnsupportedOperationException("Only one concurrent iteration supported");
        }
        try {
            aerogel$entities.values().forEach(action);
        } finally {
            aerogel$iterating.set(false);
        }
    }
}
