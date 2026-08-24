package net.minecraft.world.entity;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import java.util.Collection;
import net.minecraft.world.entity.ai.attributes.Attribute;

public abstract class LivingEntity extends Entity {
    public double getAttributeValue(Holder<Attribute> attribute) { return 0.0D; }
    public Collection<MobEffectInstance> getActiveEffects() { return null; }
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    public void heal(float amount) { }
    public boolean addEffect(MobEffectInstance effect, Entity source) { return false; }
    public boolean removeEffect(Holder<MobEffect> effect) { return false; }
    public ItemStack getItemBySlot(EquipmentSlot slot) { return null; }
    public void setItemSlot(EquipmentSlot slot, ItemStack item) { }
    public ItemStack getItemInHand(InteractionHand hand) { return null; }
    public boolean isUsingItem() { return false; }
    public float getHealth() { return 0; }
    public void setHealth(float health) { }
    public float getAbsorptionAmount() { return 0; }
    public void setAbsorptionAmount(float amount) { }
    public void knockback(double strength, double directionX, double directionZ,
                          DamageSource source, float verticalStrength, boolean limitVertical) { }
    public boolean randomTeleport(double x, double y, double z, boolean showParticles) {
        return false;
    }
    public boolean isSprinting() { return false; }
    public void setSprinting(boolean sprinting) { }
    public InteractionHand getUsedItemHand() { return null; }
    public ItemStack getUseItem() { return null; }
    public int getUseItemRemainingTicks() { return 0; }
    public void stopUsingItem() { }
}
