package dev.aerogel.loader.api;

import dev.aerogel.api.persistence.PersistentDataContainer;
import dev.aerogel.loader.internal.PersistentDataHolderBridge;
import dev.aerogel.loader.internal.PersistentDataViews;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

final class PersistentDataContainerTest {
    @Test void builtInValuesRoundTripWithoutAnExplicitTypeToken() {
        PersistentDataContainer data = container();
        UUID owner = UUID.randomUUID();

        data.set("byte", (byte) 1);
        data.set("short", (short) 2);
        data.set("level", 10);
        data.set("long", 20L);
        data.set("float", 1.5F);
        data.set("double", 2.5D);
        data.set("enabled", true);
        data.set("name", "Aerogel");
        data.set("owner", owner);
        data.set("bytes", new byte[] {1, 2});
        data.set("ints", new int[] {3, 4});
        data.set("longs", new long[] {5, 6});
        CompoundTag compound = new CompoundTag();
        compound.put("nested", net.minecraft.nbt.IntTag.valueOf(7));
        data.set("compound", compound);

        assertEquals((byte) 1, data.getByte("byte").orElseThrow());
        assertEquals((short) 2, data.getShort("short").orElseThrow());
        assertEquals(10, data.getInt("level", 0));
        assertEquals(20L, data.getLong("long", 0));
        assertEquals(1.5F, data.getFloat("float", 0));
        assertEquals(2.5D, data.getDouble("double", 0));
        assertTrue(data.getBoolean("enabled", false));
        assertEquals("Aerogel", data.getString("name", "missing"));
        assertEquals(owner, data.getUUID("owner").orElseThrow());
        assertArrayEquals(new byte[] {1, 2}, data.getByteArray("bytes").orElseThrow());
        assertArrayEquals(new int[] {3, 4}, data.getIntArray("ints").orElseThrow());
        assertArrayEquals(new long[] {5, 6}, data.getLongArray("longs").orElseThrow());
        assertTrue(data.getCompound("compound").orElseThrow().contains("nested"));
        assertEquals(4, data.getInt("missing", 4));
    }

    @Test void typeMismatchFailsInsteadOfSilentlyConvertingNbt() {
        PersistentDataContainer data = container();
        data.set("value", 7L);
        assertThrows(IllegalArgumentException.class, () -> data.getInt("value"));
    }

    @Test void namespacesRemainIndependent() {
        FakeHolder holder = new FakeHolder();
        PersistentDataViews.holder(holder).namespace("one").set("value", 1);
        PersistentDataViews.holder(holder).namespace("two").set("value", 2);
        assertEquals(Set.of("one", "two"), PersistentDataViews.holder(holder).namespaces());
    }

    private static PersistentDataContainer container() {
        return PersistentDataViews.holder(new FakeHolder()).namespace("test");
    }

    private static final class FakeHolder implements PersistentDataHolderBridge {
        private CompoundTag data = new CompoundTag();

        @Override public CompoundTag aerogel$persistentData(String pluginId) {
            return data.getCompoundOrEmpty(pluginId).copy();
        }
        @Override public void aerogel$editPersistentData(
            String pluginId, Consumer<CompoundTag> editor
        ) {
            CompoundTag value = data.getCompoundOrEmpty(pluginId).copy();
            editor.accept(value);
            if (value.isEmpty()) data.remove(pluginId); else data.put(pluginId, value);
        }
        @Override public CompoundTag aerogel$allPersistentData() { return data.copy(); }
        @Override public void aerogel$restorePersistentData(CompoundTag restored) {
            data = restored.copy();
        }
    }
}
