package net.minecraft.core;

public interface RegistryAccess extends HolderLookup.Provider {
    interface Frozen extends RegistryAccess {
    }
}
