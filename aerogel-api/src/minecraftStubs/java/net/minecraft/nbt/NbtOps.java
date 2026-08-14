package net.minecraft.nbt;

import com.mojang.serialization.DynamicOps;

public final class NbtOps implements DynamicOps<Tag> {
    public static final NbtOps INSTANCE = new NbtOps();

    private NbtOps() {
    }
}
