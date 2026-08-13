package dev.aerogel.loader.api;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class Reflect {
    private static final Map<TypeKey, Class<?>> TYPES = new ConcurrentHashMap<>();

    private Reflect() {}

    static Class<?> type(ClassLoader loader, String name) {
        try {
            return TYPES.computeIfAbsent(new TypeKey(loader, name), ignored -> {
                try { return Class.forName(name, true, loader); }
                catch (ClassNotFoundException exception) { throw new TypeFailure(exception); }
            });
        } catch (TypeFailure failure) {
            throw new IllegalStateException("Vanilla class not found: " + name, failure.getCause());
        }
    }

    static Object invoke(Object target, String name, Object... arguments) {
        Method method = method(target.getClass(), name, false, arguments);
        try { return method.invoke(target, arguments); }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot invoke " + target.getClass().getName() + "." + name, unwrap(exception));
        }
    }

    static Object invokeStatic(Class<?> type, String name, Object... arguments) {
        Method method = method(type, name, true, arguments);
        try { return method.invoke(null, arguments); }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot invoke " + type.getName() + "." + name, unwrap(exception));
        }
    }

    static Object construct(Class<?> type, Object... arguments) {
        for (Constructor<?> constructor : type.getConstructors()) {
            if (matches(constructor.getParameterTypes(), arguments)) {
                try { return constructor.newInstance(arguments); }
                catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Cannot construct " + type.getName(), unwrap(exception));
                }
            }
        }
        throw new IllegalStateException("Compatible constructor not found: " + type.getName());
    }

    static Object field(Object owner, String name) {
        Field field = findField(owner.getClass(), name);
        try { return field.get(owner); }
        catch (IllegalAccessException exception) { throw new IllegalStateException("Cannot read " + name, exception); }
    }

    static Object staticField(Class<?> type, String name) {
        Field field = findField(type, name);
        try { return field.get(null); }
        catch (IllegalAccessException exception) { throw new IllegalStateException("Cannot read " + name, exception); }
    }

    static void removeNamedChild(Object root, String name) {
        for (Class<?> current = root.getClass(); current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(root);
                    if (value instanceof Map<?, ?> map) map.remove(name);
                } catch (IllegalAccessException exception) {
                    throw new IllegalStateException("Cannot update Brigadier command tree", exception);
                }
            }
        }
    }

    private static Method method(Class<?> type, String name, boolean requireStatic, Object[] arguments) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)
                && (!requireStatic || Modifier.isStatic(method.getModifiers()))
                && matches(method.getParameterTypes(), arguments)) {
                method.trySetAccessible();
                return method;
            }
        }
        throw new IllegalStateException("Compatible method not found: " + type.getName() + "." + name);
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new IllegalStateException("Field not found: " + type.getName() + "." + name);
    }

    private static boolean matches(Class<?>[] parameters, Object[] arguments) {
        if (parameters.length != arguments.length) return false;
        for (int index = 0; index < parameters.length; index++) {
            Object argument = arguments[index];
            if (argument == null) {
                if (parameters[index].isPrimitive()) return false;
                continue;
            }
            Class<?> parameter = wrap(parameters[index]);
            if (!parameter.isAssignableFrom(argument.getClass())) return false;
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return Void.class;
    }

    private static Throwable unwrap(ReflectiveOperationException exception) {
        return exception.getCause() == null ? exception : exception.getCause();
    }

    private static final class TypeFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private TypeFailure(Throwable cause) { super(cause); }
    }

    private record TypeKey(ClassLoader loader, String name) {}
}
