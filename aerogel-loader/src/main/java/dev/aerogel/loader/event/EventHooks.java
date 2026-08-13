package dev.aerogel.loader.event;

import dev.aerogel.api.event.AerogelEvent;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

public final class EventHooks {
    private static final ConcurrentHashMap<FieldKey, Field> FIELDS = new ConcurrentHashMap<>();

    private EventHooks() {
    }

    public static void post(AerogelEvent event) {
        AerogelEvents.post(event);
    }

    public static Object field(Object owner, String name) {
        try {
            Field field = FIELDS.computeIfAbsent(new FieldKey(owner.getClass(), name), key -> findField(key.type, key.name));
            return field.get(owner);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read vanilla field " + name, exception);
        }
    }

    public static Object staticField(Object owner, String className, String fieldName) {
        try {
            Class<?> type = Class.forName(className, true, owner.getClass().getClassLoader());
            Field field = FIELDS.computeIfAbsent(new FieldKey(type, fieldName), key -> findField(key.type, key.name));
            return field.get(null);
        } catch (ClassNotFoundException | IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read vanilla static field " + className + "." + fieldName,
                exception);
        }
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Continue through the vanilla hierarchy.
            }
        }
        throw new IllegalStateException("Vanilla field not found: " + type.getName() + "." + name);
    }

    private record FieldKey(Class<?> type, String name) {
    }
}
