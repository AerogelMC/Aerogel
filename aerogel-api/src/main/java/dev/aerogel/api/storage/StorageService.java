package dev.aerogel.api.storage;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/** Opens plugin-owned files confined to the plugin data directory. */
public interface StorageService {
    default <T> DataFile<T> json(
        String relativePath,
        Class<T> type,
        Supplier<? extends T> defaultValue
    ) {
        return json(Path.of(relativePath), TypeRef.of(type), defaultValue, StorageOptions.defaults());
    }

    default <T> DataFile<T> json(
        Path path,
        Class<T> type,
        Supplier<? extends T> defaultValue
    ) {
        return json(path, TypeRef.of(type), defaultValue, StorageOptions.defaults());
    }

    default <T> DataFile<T> json(
        Path path,
        TypeRef<T> type,
        Supplier<? extends T> defaultValue
    ) {
        return json(path, type, defaultValue, StorageOptions.defaults());
    }

    default <T> DataFile<T> json(
        String relativePath,
        TypeRef<T> type,
        Supplier<? extends T> defaultValue
    ) {
        return json(Path.of(relativePath), type, defaultValue, StorageOptions.defaults());
    }

    default <T> DataFile<T> json(
        Path path,
        Class<T> type,
        Supplier<? extends T> defaultValue,
        StorageOptions options
    ) {
        return json(path, TypeRef.of(type), defaultValue, options);
    }

    <T> DataFile<T> json(
        Path path,
        TypeRef<T> type,
        Supplier<? extends T> defaultValue,
        StorageOptions options
    );

    /**
     * Stores a plugin data model as JSON with exact adapters for supported Minecraft values.
     * Loading starts when the live server and its registry access become available.
     */
    default <T> DataFile<T> minecraftJson(
        String relativePath,
        Class<T> type,
        Supplier<? extends T> defaultValue
    ) {
        return minecraftJson(
            Path.of(relativePath), TypeRef.of(type), defaultValue, StorageOptions.defaults());
    }

    default <T> DataFile<T> minecraftJson(
        Path path,
        Class<T> type,
        Supplier<? extends T> defaultValue
    ) {
        return minecraftJson(path, TypeRef.of(type), defaultValue, StorageOptions.defaults());
    }

    default <T> DataFile<T> minecraftJson(
        String relativePath,
        TypeRef<T> type,
        Supplier<? extends T> defaultValue
    ) {
        return minecraftJson(
            Path.of(relativePath), type, defaultValue, StorageOptions.defaults());
    }

    default <T> DataFile<T> minecraftJson(
        Path path,
        TypeRef<T> type,
        Supplier<? extends T> defaultValue
    ) {
        return minecraftJson(path, type, defaultValue, StorageOptions.defaults());
    }

    default <T> DataFile<T> minecraftJson(
        Path path,
        Class<T> type,
        Supplier<? extends T> defaultValue,
        StorageOptions options
    ) {
        return minecraftJson(path, TypeRef.of(type), defaultValue, options);
    }

    <T> DataFile<T> minecraftJson(
        Path path,
        TypeRef<T> type,
        Supplier<? extends T> defaultValue,
        StorageOptions options
    );

    /** Stores any value supported by a Mojang codec as registry-aware, human-readable JSON. */
    default <T> DataFile<T> codecJson(
        String relativePath,
        Codec<T> codec,
        Supplier<? extends T> defaultValue
    ) {
        return codecJson(Path.of(relativePath), codec, defaultValue, StorageOptions.defaults());
    }

    default <T> DataFile<T> codecJson(
        Path path,
        Codec<T> codec,
        Supplier<? extends T> defaultValue
    ) {
        return codecJson(path, codec, defaultValue, StorageOptions.defaults());
    }

    <T> DataFile<T> codecJson(
        Path path,
        Codec<T> codec,
        Supplier<? extends T> defaultValue,
        StorageOptions options
    );

    default DataFile<ItemStack> itemStack(
        String relativePath,
        Supplier<? extends ItemStack> defaultValue
    ) {
        return itemStack(Path.of(relativePath), defaultValue, StorageOptions.defaults());
    }

    default DataFile<ItemStack> itemStack(
        Path path,
        Supplier<? extends ItemStack> defaultValue
    ) {
        return itemStack(path, defaultValue, StorageOptions.defaults());
    }

    default DataFile<ItemStack> itemStack(
        Path path,
        Supplier<? extends ItemStack> defaultValue,
        StorageOptions options
    ) {
        return codecJson(path, ItemStack.OPTIONAL_CODEC, defaultValue, options);
    }

    /** A list codec that keeps empty stacks, so list indices can represent inventory slots. */
    default DataFile<List<ItemStack>> itemStacks(
        String relativePath,
        Supplier<? extends List<ItemStack>> defaultValue
    ) {
        return itemStacks(Path.of(relativePath), defaultValue, StorageOptions.defaults());
    }

    default DataFile<List<ItemStack>> itemStacks(
        Path path,
        Supplier<? extends List<ItemStack>> defaultValue
    ) {
        return itemStacks(path, defaultValue, StorageOptions.defaults());
    }

    default DataFile<List<ItemStack>> itemStacks(
        Path path,
        Supplier<? extends List<ItemStack>> defaultValue,
        StorageOptions options
    ) {
        return codecJson(path, ItemStack.OPTIONAL_CODEC.listOf(), defaultValue, options);
    }

    default DataFile<Component> component(
        String relativePath,
        Supplier<? extends Component> defaultValue
    ) {
        return component(Path.of(relativePath), defaultValue, StorageOptions.defaults());
    }

    default DataFile<Component> component(
        Path path,
        Supplier<? extends Component> defaultValue
    ) {
        return component(path, defaultValue, StorageOptions.defaults());
    }

    default DataFile<Component> component(
        Path path,
        Supplier<? extends Component> defaultValue,
        StorageOptions options
    ) {
        return codecJson(path, ComponentSerialization.CODEC, defaultValue, options);
    }

    default DataFile<CompoundTag> compoundTag(
        String relativePath,
        Supplier<? extends CompoundTag> defaultValue
    ) {
        return compoundTag(Path.of(relativePath), defaultValue, StorageOptions.defaults());
    }

    default DataFile<CompoundTag> compoundTag(
        Path path,
        Supplier<? extends CompoundTag> defaultValue
    ) {
        return compoundTag(path, defaultValue, StorageOptions.defaults());
    }

    default DataFile<CompoundTag> compoundTag(
        Path path,
        Supplier<? extends CompoundTag> defaultValue,
        StorageOptions options
    ) {
        return codecJson(path, CompoundTag.CODEC, defaultValue, options);
    }

    default DataFile<BlockState> blockState(
        String relativePath,
        Supplier<? extends BlockState> defaultValue
    ) {
        return blockState(Path.of(relativePath), defaultValue, StorageOptions.defaults());
    }

    default DataFile<BlockState> blockState(
        Path path,
        Supplier<? extends BlockState> defaultValue
    ) {
        return blockState(path, defaultValue, StorageOptions.defaults());
    }

    default DataFile<BlockState> blockState(
        Path path,
        Supplier<? extends BlockState> defaultValue,
        StorageOptions options
    ) {
        return codecJson(path, BlockState.CODEC, defaultValue, options);
    }

    default DataFile<DataComponentPatch> dataComponentPatch(
        String relativePath,
        Supplier<? extends DataComponentPatch> defaultValue
    ) {
        return dataComponentPatch(Path.of(relativePath), defaultValue, StorageOptions.defaults());
    }

    default DataFile<DataComponentPatch> dataComponentPatch(
        Path path,
        Supplier<? extends DataComponentPatch> defaultValue
    ) {
        return dataComponentPatch(path, defaultValue, StorageOptions.defaults());
    }

    default DataFile<DataComponentPatch> dataComponentPatch(
        Path path,
        Supplier<? extends DataComponentPatch> defaultValue,
        StorageOptions options
    ) {
        return codecJson(path, DataComponentPatch.CODEC, defaultValue, options);
    }

    default DataFile<GlobalPos> globalPosition(
        String relativePath,
        Supplier<? extends GlobalPos> defaultValue
    ) {
        return globalPosition(Path.of(relativePath), defaultValue, StorageOptions.defaults());
    }

    default DataFile<GlobalPos> globalPosition(
        Path path,
        Supplier<? extends GlobalPos> defaultValue
    ) {
        return globalPosition(path, defaultValue, StorageOptions.defaults());
    }

    default DataFile<GlobalPos> globalPosition(
        Path path,
        Supplier<? extends GlobalPos> defaultValue,
        StorageOptions options
    ) {
        return codecJson(path, GlobalPos.CODEC, defaultValue, options);
    }

    default DataFile<BlockPos> blockPosition(
        String relativePath,
        Supplier<? extends BlockPos> defaultValue
    ) {
        return blockPosition(Path.of(relativePath), defaultValue, StorageOptions.defaults());
    }

    default DataFile<BlockPos> blockPosition(
        Path path,
        Supplier<? extends BlockPos> defaultValue
    ) {
        return blockPosition(path, defaultValue, StorageOptions.defaults());
    }

    default DataFile<BlockPos> blockPosition(
        Path path,
        Supplier<? extends BlockPos> defaultValue,
        StorageOptions options
    ) {
        return codecJson(path, BlockPos.CODEC, defaultValue, options);
    }

    default DataFile<Identifier> identifier(
        String relativePath,
        Supplier<? extends Identifier> defaultValue
    ) {
        return identifier(Path.of(relativePath), defaultValue, StorageOptions.defaults());
    }

    default DataFile<Identifier> identifier(
        Path path,
        Supplier<? extends Identifier> defaultValue
    ) {
        return identifier(path, defaultValue, StorageOptions.defaults());
    }

    default DataFile<Identifier> identifier(
        Path path,
        Supplier<? extends Identifier> defaultValue,
        StorageOptions options
    ) {
        return codecJson(path, Identifier.CODEC, defaultValue, options);
    }

    default <T> DataFile<T> open(
        Path path,
        DataCodec<T> codec,
        Supplier<? extends T> defaultValue
    ) {
        return open(path, codec, defaultValue, StorageOptions.defaults());
    }

    default <T> DataFile<T> open(
        String relativePath,
        DataCodec<T> codec,
        Supplier<? extends T> defaultValue
    ) {
        return open(Path.of(relativePath), codec, defaultValue, StorageOptions.defaults());
    }

    <T> DataFile<T> open(
        Path path,
        DataCodec<T> codec,
        Supplier<? extends T> defaultValue,
        StorageOptions options
    );
}
