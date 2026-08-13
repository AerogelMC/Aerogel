package dev.aerogel.loader.mixin;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AerogelPropertyService implements IGlobalPropertyService {
    private static final Map<String, Object> PROPERTIES = new ConcurrentHashMap<>();

    @Override
    public IPropertyKey resolveKey(String name) {
        return new Key(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key) {
        return (T) PROPERTIES.get(key.toString());
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        if (value == null) {
            PROPERTIES.remove(key.toString());
        } else {
            PROPERTIES.put(key.toString(), value);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        return (T) PROPERTIES.getOrDefault(key.toString(), defaultValue);
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        Object value = PROPERTIES.get(key.toString());
        return value == null ? defaultValue : value.toString();
    }

    private record Key(String name) implements IPropertyKey {
        @Override
        public String toString() {
            return name;
        }
    }
}
