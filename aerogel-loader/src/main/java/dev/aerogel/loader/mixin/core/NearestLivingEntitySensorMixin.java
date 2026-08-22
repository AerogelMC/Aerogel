package dev.aerogel.loader.mixin.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;

/** Keeps nearest-entity ordering stable while independent owners continue moving. */
@Mixin(targets = "net.minecraft.world.entity.ai.sensing.NearestLivingEntitySensor")
abstract class NearestLivingEntitySensorMixin {
    @Redirect(
        method = "doTick(Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/entity/LivingEntity;)V",
        at = @At(value = "INVOKE", target = "Ljava/util/List;sort("
            + "Ljava/util/Comparator;)V")
    )
    private void aerogel$sortByPublishedPositions(
        List<LivingEntity> entities,
        Comparator<? super LivingEntity> vanillaComparator,
        ServerLevel level,
        LivingEntity observer
    ) {
        Vec3 origin = observer.position();
        IdentityHashMap<LivingEntity, Double> distances =
            new IdentityHashMap<>(entities.size());
        for (LivingEntity entity : entities) {
            Vec3 position = entity.position();
            double x = position.x - origin.x;
            double y = position.y - origin.y;
            double z = position.z - origin.z;
            distances.put(entity, x * x + y * y + z * z);
        }
        entities.sort((left, right) -> Double.compare(
            distances.get(left), distances.get(right)));
    }
}
