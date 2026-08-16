package net.minecraft.network.syncher;

public class SynchedEntityData {
    public <T> T get(EntityDataAccessor<T> accessor) { return null; }

    public record DataValue<T>(int id, Object serializer, T value) {
        public static <T> DataValue<T> create(EntityDataAccessor<T> accessor, T value) {
            return null;
        }
    }
}
