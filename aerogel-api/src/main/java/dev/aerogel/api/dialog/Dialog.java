package dev.aerogel.api.dialog;

import dev.aerogel.api.Registration;
import net.minecraft.server.level.ServerPlayer;

public interface Dialog extends Registration {
    net.minecraft.server.dialog.Dialog vanilla();
    void show(ServerPlayer vanillaPlayer);
}
