package dev.aerogel.loader.event;

import dev.aerogel.api.event.entity.EntityDeathEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityDeathEventTest {
    @Test
    void exposesMutableVanillaResult() {
        LivingEntity entity = new LivingEntity() { };
        DamageSource source = new DamageSource();
        ItemStack first = new ItemStack();
        ItemStack second = new ItemStack();
        EntityDeathEvent event = new EntityDeathEvent(entity, source, List.of(first), 7);

        event.drops().clear();
        event.addDrop(second);
        event.setDroppedExperience(25);

        assertSame(entity, event.entity());
        assertSame(source, event.damageSource());
        assertEquals(List.of(second), event.drops());
        assertEquals(25, event.droppedExperience());
    }

    @Test
    void rejectsInvalidOutcomeValues() {
        EntityDeathEvent event = new EntityDeathEvent(
            new LivingEntity() { }, new DamageSource(), List.of(), 0);

        assertThrows(NullPointerException.class, () -> event.addDrop(null));
        assertThrows(IllegalArgumentException.class, () -> event.setDroppedExperience(-1));
    }
}
