package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.AerogelEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Fired after vanilla calculates death loot and before any captured drops or experience spawn. */
public final class EntityDeathEvent implements AerogelEvent {
    private final LivingEntity entity;
    private final DamageSource damageSource;
    private final List<ItemStack> drops;
    private int droppedExperience;

    public EntityDeathEvent(
        LivingEntity entity, DamageSource damageSource,
        Collection<? extends ItemStack> drops, int droppedExperience
    ) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.damageSource = Objects.requireNonNull(damageSource, "damageSource");
        this.drops = new ArrayList<>();
        setDrops(drops);
        setDroppedExperience(droppedExperience);
    }

    public LivingEntity entity() { return entity; }
    public DamageSource damageSource() { return damageSource; }

    /** Mutable list that will be spawned after all listeners return. */
    public List<ItemStack> drops() { return drops; }

    public void setDrops(Collection<? extends ItemStack> drops) {
        Objects.requireNonNull(drops, "drops");
        this.drops.clear();
        for (ItemStack drop : drops) {
            this.drops.add(Objects.requireNonNull(drop, "drop"));
        }
    }

    public void addDrop(ItemStack drop) {
        drops.add(Objects.requireNonNull(drop, "drop"));
    }

    public void clearDrops() {
        drops.clear();
    }

    public int droppedExperience() { return droppedExperience; }

    public void setDroppedExperience(int droppedExperience) {
        if (droppedExperience < 0) {
            throw new IllegalArgumentException("droppedExperience must not be negative");
        }
        this.droppedExperience = droppedExperience;
    }
}
