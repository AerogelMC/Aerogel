package dev.aerogel.api.menu;

import dev.aerogel.api.Registration;
import dev.aerogel.api.inventory.InventoryView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.function.Consumer;

public interface Menu extends Registration {
    int size();
    Menu item(int slot, ItemStack item);
    ItemStack item(int slot);
    Menu onClick(int slot, Consumer<MenuClick> handler);
    Menu onAnyClick(Consumer<MenuClick> handler);
    Menu allowPlayerInventory(boolean allow);
    InventoryView open(ServerPlayer player);
    void refresh();
    Collection<ServerPlayer> viewers();
}
