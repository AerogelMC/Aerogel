package net.minecraft.network.protocol.game;
import net.minecraft.network.protocol.Packet;
public class ServerboundRecipeBookChangeSettingsPacket implements Packet<Object> {
    public net.minecraft.world.inventory.RecipeBookType getBookType() { return null; }
    public boolean isOpen() { return false; }
    public boolean isFiltering() { return false; }
}
