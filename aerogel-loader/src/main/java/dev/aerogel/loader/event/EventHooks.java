package dev.aerogel.loader.event;

import dev.aerogel.api.event.AerogelEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentHashMap;

public final class EventHooks {
    private static final ConcurrentHashMap<FieldKey, Field> FIELDS = new ConcurrentHashMap<>();

    private EventHooks() {
    }

    public static void post(AerogelEvent event) {
        AerogelEvents.post(event);
    }

    @SuppressWarnings("unchecked")
    public static <T> T cast(Object value) {
        return (T) value;
    }

    public static Object field(Object owner, String name) {
        try {
            Field field = FIELDS.computeIfAbsent(new FieldKey(owner.getClass(), name), key -> findField(key.type, key.name));
            return field.get(owner);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read vanilla field " + name, exception);
        }
    }

    public static void setField(Object owner, String name, Object value) {
        try {
            Field field = FIELDS.computeIfAbsent(new FieldKey(owner.getClass(), name), key -> findField(key.type, key.name));
            field.set(owner, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot write vanilla field " + name, exception);
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

    public static Object call(Object owner, String methodName) {
        return call(owner, methodName, new Object[0]);
    }

    public static Object call(Object owner, String methodName, Object... arguments) {
        try {
            Method method = findMethod(owner.getClass(), methodName, arguments);
            return method.invoke(owner, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot call vanilla method " + methodName, exception);
        }
    }

    public static Object construct(Object owner, String className, Object... arguments) {
        try {
            Class<?> type = Class.forName(className, true, owner.getClass().getClassLoader());
            for (Constructor<?> constructor : type.getConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length != arguments.length) continue;
                boolean compatible = true;
                for (int index = 0; index < parameters.length; index++) {
                    Object argument = arguments[index];
                    if (argument != null && !boxed(parameters[index]).isInstance(argument)) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) return constructor.newInstance(arguments);
            }
            throw new NoSuchMethodException(type.getName() + " constructor");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot construct vanilla class " + className, exception);
        }
    }

    public static void resyncBlock(Object player, Object level, Object position) {
        Object state = call(level, "getBlockState", position);
        call(level, "sendBlockUpdated", position, state, state, 3);
        call(level, "destroyBlockProgress", call(player, "getId"), position, -1);
    }

    public static boolean isInstance(Object value, String className) {
        if (value == null) return false;
        try {
            return Class.forName(className, false, value.getClass().getClassLoader()).isInstance(value);
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    public static Object eitherLeft(Object owner, String valueClass, String fieldName) {
        try {
            Object value = staticField(owner, valueClass, fieldName);
            Class<?> either = Class.forName(
                "com.mojang.datafixers.util.Either", true, owner.getClass().getClassLoader());
            return either.getMethod("left", Object.class).invoke(null, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot create vanilla Either.left result", exception);
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

    private static Method findMethod(Class<?> type, String name, Object[] arguments) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
                Class<?>[] parameters = method.getParameterTypes();
                boolean compatible = true;
                for (int index = 0; index < parameters.length; index++) {
                    Object argument = arguments[index];
                    if (argument != null && !boxed(parameters[index]).isInstance(argument)) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private record FieldKey(Class<?> type, String name) {
    }
}
