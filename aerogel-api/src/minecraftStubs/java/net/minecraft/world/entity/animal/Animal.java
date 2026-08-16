package net.minecraft.world.entity.animal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
public abstract class Animal extends LivingEntity {
    public void spawnChildFromBreeding(ServerLevel level, Animal partner) { }
}
