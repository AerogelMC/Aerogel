package net.minecraft.world.entity;
public abstract class Mob extends LivingEntity {
    public LivingEntity getTarget() { return null; }
    public void setTarget(LivingEntity target) { }
    public net.minecraft.world.entity.ai.navigation.PathNavigation getNavigation() { return null; }
}
