package dev.aerogel.api.storage;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

/** Captures a generic Java type for JSON storage, for example {@code new TypeRef<List<Entry>>() {}}. */
public abstract class TypeRef<T> {
    private final Type type;

    protected TypeRef() {
        Type parent = getClass().getGenericSuperclass();
        if (!(parent instanceof ParameterizedType parameterized)) {
            throw new IllegalStateException("TypeRef must be created with a type parameter");
        }
        type = parameterized.getActualTypeArguments()[0];
    }

    private TypeRef(Type type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <T> TypeRef<T> of(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return new TypeRef<>(type) { };
    }

    public final Type type() {
        return type;
    }

    @Override
    public final String toString() {
        return type.getTypeName();
    }
}
