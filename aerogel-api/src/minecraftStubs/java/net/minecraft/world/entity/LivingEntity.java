package net.minecraft.world.entity;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;

public abstract class LivingEntity extends Entity {
    public Collection<MobEffectInstance> getActiveEffects() { return null; }
    public ItemEntity drop(ItemStack stack, boolean randomOffset, boolean includeThrowerName) { return null; }
}
