package dev.aerogel.loader.internal;

import dev.aerogel.api.event.entity.EntityDeathEvent;
import dev.aerogel.loader.event.EventHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Captures vanilla death output until plugins have had a chance to edit it. */
public final class DeathDropCapture {
    private static final ThreadLocal<Deque<Context>> CONTEXTS =
        ThreadLocal.withInitial(ArrayDeque::new);

    private DeathDropCapture() {
    }

    public static void begin(LivingEntity entity, DamageSource damageSource) {
        CONTEXTS.get().push(new Context(entity, damageSource));
    }

    public static boolean capture(Entity entity) {
        Context context = current();
        if (context == null || context.finalizing || !(entity instanceof ItemEntity item)) {
            return false;
        }
        ItemStack stack = item.getItem();
        if (!stack.isEmpty()) context.drops.add(stack.copy());
        return true;
    }

    public static boolean isHandling(Entity entity) {
        Context context = current();
        return context != null && context.entity == entity;
    }

    public static boolean captureExperience(int amount) {
        Context context = current();
        if (context == null || context.finalizing) return false;
        context.experience += Math.max(0, amount);
        return true;
    }

    public static void complete(
        ServerLevel level, LivingEntity entity, DamageSource damageSource
    ) {
        Deque<Context> contexts = CONTEXTS.get();
        Context context = contexts.peek();
        if (context == null || context.entity != entity) {
            throw new IllegalStateException("Mismatched death-drop capture");
        }
        context.finalizing = true;
        try {
            EntityDeathEvent event = new EntityDeathEvent(
                entity, damageSource, context.drops, context.experience);
            EventHooks.post(event);
            // Snapshot before spawning: spawning can call plugin listeners that mutate the
            // original list, and a directly edited live list may contain an invalid null.
            for (ItemStack drop : new ArrayList<>(event.drops())) {
                if (drop != null && !drop.isEmpty()) entity.drop(drop.copy(), false, false);
            }
            if (event.droppedExperience() > 0) {
                ExperienceOrb.award(level, entity.position(), event.droppedExperience());
            }
        } finally {
            contexts.pop();
            if (contexts.isEmpty()) CONTEXTS.remove();
        }
    }

    private static Context current() {
        Deque<Context> contexts = CONTEXTS.get();
        return contexts.peek();
    }

    private static final class Context {
        private final LivingEntity entity;
        @SuppressWarnings("unused")
        private final DamageSource damageSource;
        private final List<ItemStack> drops = new ArrayList<>();
        private int experience;
        private boolean finalizing;

        private Context(LivingEntity entity, DamageSource damageSource) {
            this.entity = entity;
            this.damageSource = damageSource;
        }
    }
}
