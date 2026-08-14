package net.minecraft.world.entity;

import net.minecraft.world.effect.MobEffectInstance;
import java.util.Collection;

public abstract class LivingEntity extends Entity {
    public Collection<MobEffectInstance> getActiveEffects() { return null; }
}
