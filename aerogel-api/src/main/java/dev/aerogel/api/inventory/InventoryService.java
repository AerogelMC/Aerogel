package dev.aerogel.api.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;

public interface InventoryService {
    /** Creates a chest inventory with one to six rows. */
    Inventory create(int rows, Component title);

    /** Wraps a live vanilla Container without copying it. */
    Inventory wrap(Container container, Component title);
}
