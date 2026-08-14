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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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
        if (!stack.isEmpty()) context.drops.add(new CapturedDrop(item, stack));
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
            List<ItemStack> calculatedDrops = context.drops.stream()
                .map(CapturedDrop::stack)
                .toList();
            EntityDeathEvent event = new EntityDeathEvent(
                entity, damageSource, calculatedDrops, context.experience);
            EventHooks.post(event);
            Map<ItemStack, Deque<ItemEntity>> capturedByStack = new IdentityHashMap<>();
            for (CapturedDrop captured : context.drops) {
                capturedByStack.computeIfAbsent(captured.stack(), ignored -> new ArrayDeque<>())
                    .addLast(captured.entity());
            }
            // Snapshot before spawning: spawning can call plugin listeners that mutate the
            // original list, and a directly edited live list may contain an invalid null.
            for (ItemStack drop : new ArrayList<>(event.drops())) {
                if (drop == null || drop.isEmpty()) continue;
                Deque<ItemEntity> originals = capturedByStack.get(drop);
                ItemEntity original = originals == null ? null : originals.pollFirst();
                if (original != null) level.addFreshEntity(original);
                else entity.spawnAtLocation(level, drop.copy());
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
        private final List<CapturedDrop> drops = new ArrayList<>();
        private int experience;
        private boolean finalizing;

        private Context(LivingEntity entity, DamageSource damageSource) {
            this.entity = entity;
            this.damageSource = damageSource;
        }
    }

    private record CapturedDrop(ItemEntity entity, ItemStack stack) {
    }
}
