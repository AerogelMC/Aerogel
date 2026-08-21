package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.persistence.PersistentDataView;
import dev.aerogel.loader.internal.PersistentDataHolderBridge;
import dev.aerogel.loader.internal.PersistentDataViews;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import java.util.function.Consumer;

/** Stores Aerogel data in the block entity's own vanilla NBT payload. */
@Mixin(targets = "net.minecraft.world.level.block.entity.BlockEntity")
abstract class BlockEntityMixin implements PersistentDataHolderBridge {
    @Unique private volatile boolean aerogel$removed;
    @Unique private CompoundTag aerogel$persistentData = new CompoundTag();
    @Unique private PersistentDataView aerogel$dataView;

    @Inject(method = "setRemoved()V", at = @At("TAIL"))
    private void aerogel$publishRemoved(CallbackInfo callbackInfo) {
        aerogel$removed = true;
    }

    @Inject(method = "clearRemoved()V", at = @At("TAIL"))
    private void aerogel$publishActive(CallbackInfo callbackInfo) {
        aerogel$removed = false;
    }

    @Inject(method = "isRemoved()Z", at = @At("HEAD"), cancellable = true)
    private void aerogel$readPublishedRemoval(
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        callbackInfo.setReturnValue(aerogel$removed);
    }

    @Unique
    public synchronized PersistentDataView data() {
        if (aerogel$dataView == null) aerogel$dataView = PersistentDataViews.holder(this);
        return aerogel$dataView;
    }

    @Override
    public synchronized CompoundTag aerogel$persistentData(String pluginId) {
        Objects.requireNonNull(pluginId, "pluginId");
        return aerogel$persistentData.getCompoundOrEmpty(pluginId).copy();
    }

    @Override
    public synchronized void aerogel$editPersistentData(
        String pluginId, Consumer<CompoundTag> editor
    ) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(editor, "editor");
        CompoundTag value = aerogel$persistentData.getCompoundOrEmpty(pluginId).copy();
        editor.accept(value);
        if (value.isEmpty()) aerogel$persistentData.remove(pluginId);
        else aerogel$persistentData.put(pluginId, value);
    }

    @Override
    public synchronized CompoundTag aerogel$allPersistentData() {
        return aerogel$persistentData.copy();
    }

    @Override
    public synchronized void aerogel$restorePersistentData(CompoundTag data) {
        aerogel$persistentData = Objects.requireNonNull(data, "data").copy();
    }

    @Inject(
        method = {
            "saveWithoutMetadata(Lnet/minecraft/world/level/storage/ValueOutput;)V",
            "saveCustomOnly(Lnet/minecraft/world/level/storage/ValueOutput;)V"
        },
        at = @At("TAIL")
    )
    private void aerogel$savePersistentData(ValueOutput output, CallbackInfo callbackInfo) {
        CompoundTag data = aerogel$allPersistentData();
        if (!data.isEmpty()) output.store("AerogelPersistentData", CompoundTag.CODEC, data);
    }

    @Inject(
        method = {
            "loadWithComponents(Lnet/minecraft/world/level/storage/ValueInput;)V",
            "loadCustomOnly(Lnet/minecraft/world/level/storage/ValueInput;)V"
        },
        at = @At("TAIL")
    )
    private void aerogel$loadPersistentData(ValueInput input, CallbackInfo callbackInfo) {
        aerogel$restorePersistentData(
            input.read("AerogelPersistentData", CompoundTag.CODEC).orElseGet(CompoundTag::new));
    }
}
