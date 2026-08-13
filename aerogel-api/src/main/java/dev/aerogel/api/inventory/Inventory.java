package dev.aerogel.api.inventory;

import dev.aerogel.api.Registration;

import java.util.Collection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public interface Inventory extends Registration {
    int size();
    Container vanilla();
    ItemStack item(int slot);
    void item(int slot, ItemStack itemStack);
    void clear();
    InventoryView open(ServerPlayer vanillaPlayer);
    Collection<ServerPlayer> viewers();
}
