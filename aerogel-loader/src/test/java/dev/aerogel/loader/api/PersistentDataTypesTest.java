package dev.aerogel.loader.api;

import dev.aerogel.api.persistence.PersistentDataContainer;
import dev.aerogel.api.persistence.PersistentDataType;
import dev.aerogel.api.persistence.PersistentDataTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class PersistentDataTypesTest {
    @Test void primitiveTypesRoundTripWithoutChangingTagKinds() {
        assertEquals(42, PersistentDataTypes.INTEGER.decode(PersistentDataTypes.INTEGER.encode(42)));
        assertEquals("Aerogel", PersistentDataTypes.STRING.decode(PersistentDataTypes.STRING.encode("Aerogel")));
        assertTrue(PersistentDataTypes.BOOLEAN.decode(PersistentDataTypes.BOOLEAN.encode(true)));
        assertArrayEquals(new long[] {1, 2, 3},
            PersistentDataTypes.LONG_ARRAY.decode(PersistentDataTypes.LONG_ARRAY.encode(new long[] {1, 2, 3})));
    }

    @Test void uuidRoundTripsExactly() {
        UUID value = UUID.randomUUID();
        assertEquals(value, PersistentDataTypes.UUID.decode(PersistentDataTypes.UUID.encode(value)));
    }

    @Test void compoundValuesAreDefensivelyCopied() {
        CompoundTag original = new CompoundTag();
        original.put("value", PersistentDataTypes.INTEGER.encode(7));
        CompoundTag decoded = PersistentDataTypes.COMPOUND.decode(PersistentDataTypes.COMPOUND.encode(original));
        decoded.remove("value");
        assertTrue(original.contains("value"));
    }

    @Test void builtInContainerOverloadsInferTypesWithoutRuntimeGuessing() {
        MemoryContainer data = new MemoryContainer();
        UUID owner = UUID.randomUUID();

        data.set("level", 10);
        data.set("name", "Aerogel");
        data.set("enabled", true);
        data.set("owner", owner);

        assertEquals(10, data.getInt("level", 0));
        assertEquals("Aerogel", data.getString("name", "missing"));
        assertTrue(data.getBoolean("enabled", false));
        assertEquals(owner, data.getUUID("owner").orElseThrow());
        assertEquals(4, data.getInt("missing", 4));
    }

    private static final class MemoryContainer implements PersistentDataContainer {
        private final CompoundTag values = new CompoundTag();

        @Override public <T> void set(String key, PersistentDataType<T> type, T value) {
            values.put(key, type.encode(value));
        }
        @Override public <T> Optional<T> get(String key, PersistentDataType<T> type) {
            Tag value = values.get(key);
            return value == null ? Optional.empty() : Optional.of(type.decode(value));
        }
        @Override public boolean contains(String key) { return values.contains(key); }
        @Override public void remove(String key) { values.remove(key); }
        @Override public Set<String> keys() { return Set.copyOf(values.keySet()); }
        @Override public void clear() { Set.copyOf(values.keySet()).forEach(values::remove); }
        @Override public CompoundTag snapshot() { return values.copy(); }
    }
}
