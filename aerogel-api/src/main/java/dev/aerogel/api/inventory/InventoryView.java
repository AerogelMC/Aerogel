package dev.aerogel.api.inventory;

import dev.aerogel.api.Registration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

public interface InventoryView extends Registration {
    ServerPlayer player();
    AbstractContainerMenu menu();
}
