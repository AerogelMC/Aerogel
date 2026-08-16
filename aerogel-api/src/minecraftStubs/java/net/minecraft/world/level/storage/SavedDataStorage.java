package net.minecraft.world.level.storage;

import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class SavedDataStorage implements AutoCloseable {
    public <T extends SavedData> T computeIfAbsent(SavedDataType<T> type) { return null; }
    @Override public void close() { }
}
