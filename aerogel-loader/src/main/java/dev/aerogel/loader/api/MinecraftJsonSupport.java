package dev.aerogel.loader.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import dev.aerogel.api.storage.DataCodec;
import dev.aerogel.api.storage.StorageException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;

/** Bridges Mojang's registry-aware codecs to managed UTF-8 JSON files. */
final class MinecraftJsonSupport {
    private static final Gson OUTPUT = new GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    private final PluginApiScope scope;
    private final Gson typedJson;

    MinecraftJsonSupport(PluginApiScope scope) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.typedJson = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .registerTypeAdapter(ItemStack.class,
                adapter(() -> ItemStack.OPTIONAL_CODEC, "ItemStack"))
            .registerTypeHierarchyAdapter(Component.class,
                adapter(() -> ComponentSerialization.CODEC, "Component"))
            .registerTypeAdapter(CompoundTag.class,
                adapter(() -> CompoundTag.CODEC, "CompoundTag"))
            .registerTypeAdapter(DataComponentPatch.class,
                adapter(() -> DataComponentPatch.CODEC, "DataComponentPatch"))
            .registerTypeAdapter(GlobalPos.class,
                adapter(() -> GlobalPos.CODEC, "GlobalPos"))
            .registerTypeAdapter(Identifier.class,
                adapter(() -> Identifier.CODEC, "Identifier"))
            .registerTypeHierarchyAdapter(BlockPos.class,
                adapter(() -> BlockPos.CODEC, "BlockPos"))
            .registerTypeHierarchyAdapter(BlockState.class,
                adapter(() -> BlockState.CODEC, "BlockState"))
            .create();
    }

    <T> DataCodec<T> codec(Codec<T> codec) {
        Objects.requireNonNull(codec, "codec");
        return new MojangCodec<>(codec, this::operations, "value");
    }

    <T> DataCodec<T> typed(Type type) {
        Objects.requireNonNull(type, "type");
        return new DataCodec<>() {
            @Override
            public byte[] encode(T value) {
                JsonElement json = typedJson.toJsonTree(value, type);
                return OUTPUT.toJson(json).getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public T decode(byte[] encoded) {
                JsonElement json = parse(encoded);
                @SuppressWarnings("unchecked")
                T value = (T) typedJson.fromJson(json, type);
                if (value == null) {
                    throw new StorageException(
                        "Minecraft JSON decoded to null for " + type.getTypeName());
                }
                return value;
            }
        };
    }

    private <T> CodecAdapter<T> adapter(Supplier<? extends Codec<T>> codec, String description) {
        return new CodecAdapter<>(codec, this::operations, description);
    }

    private DynamicOps<Tag> operations() {
        return scope.vanilla().registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }

    private static JsonElement parse(byte[] encoded) {
        try {
            return JsonParser.parseString(new String(encoded, StandardCharsets.UTF_8));
        } catch (JsonParseException exception) {
            throw new StorageException("Malformed Minecraft JSON", exception);
        }
    }

    private static final class MojangCodec<T> implements DataCodec<T> {
        private final Codec<T> codec;
        private final Supplier<? extends DynamicOps<Tag>> operations;
        private final String description;

        private MojangCodec(
            Codec<T> codec,
            Supplier<? extends DynamicOps<Tag>> operations,
            String description
        ) {
            this.codec = codec;
            this.operations = operations;
            this.description = description;
        }

        @Override
        public byte[] encode(T value) {
            JsonElement json = LosslessNbtJson.encode(
                encodeTree(codec, operations.get(), value, description));
            return OUTPUT.toJson(json).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public T decode(byte[] encoded) {
            return decodeTree(
                codec, operations.get(), LosslessNbtJson.decode(parse(encoded)), description);
        }
    }

    private static final class CodecAdapter<T>
        implements JsonSerializer<T>, JsonDeserializer<T> {
        private final Supplier<? extends Codec<T>> codec;
        private final Supplier<? extends DynamicOps<Tag>> operations;
        private final String description;

        private CodecAdapter(
            Supplier<? extends Codec<T>> codec,
            Supplier<? extends DynamicOps<Tag>> operations,
            String description
        ) {
            this.codec = codec;
            this.operations = operations;
            this.description = description;
        }

        @Override
        public JsonElement serialize(T value, Type type, JsonSerializationContext context) {
            if (value == null) return null;
            return LosslessNbtJson.encode(
                encodeTree(codec.get(), operations.get(), value, description));
        }

        @Override
        public T deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            return decodeTree(
                codec.get(), operations.get(), LosslessNbtJson.decode(json), description);
        }
    }

    private static <T> Tag encodeTree(
        Codec<T> codec,
        DynamicOps<Tag> operations,
        T value,
        String description
    ) {
        Objects.requireNonNull(value, description);
        return codec.encodeStart(operations, value).getOrThrow(message ->
            new StorageException("Could not encode " + description + ": " + message));
    }

    private static <T> T decodeTree(
        Codec<T> codec,
        DynamicOps<Tag> operations,
        Tag encoded,
        String description
    ) {
        if (encoded == null) {
            throw new StorageException("Stored " + description + " must not be null");
        }
        return codec.parse(operations, encoded).getOrThrow(message ->
            new StorageException("Could not decode " + description + ": " + message));
    }
}
