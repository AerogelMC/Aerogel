package net.minecraft.network.protocol.common.custom;
import net.minecraft.resources.Identifier;
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();
    record Type<T extends CustomPacketPayload>(Identifier id) { }
}
