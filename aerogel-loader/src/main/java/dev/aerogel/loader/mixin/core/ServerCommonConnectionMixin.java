package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.ServerCommonConnectionBridge;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.server.network.ServerCommonPacketListenerImpl")
interface ServerCommonConnectionMixin extends ServerCommonConnectionBridge {
    @Override
    @Accessor("connection")
    Connection aerogel$connection();
}
