package net.minecraft.world;

public interface MenuProvider {
    net.minecraft.network.chat.Component getDisplayName();
    net.minecraft.world.inventory.AbstractContainerMenu createMenu(
        int containerId, net.minecraft.world.entity.player.Inventory inventory,
        net.minecraft.world.entity.player.Player player
    );
}
