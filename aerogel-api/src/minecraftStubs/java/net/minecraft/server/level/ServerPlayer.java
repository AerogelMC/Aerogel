package net.minecraft.server.level;

import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.item.ItemStack;
import java.util.function.Predicate;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class ServerPlayer extends Player {
    public void sendTitle(Component title) { }
    public void sendTitle(Component title, Component subtitle,
                          int fadeInTicks, int stayTicks, int fadeOutTicks) { }
    public void clearTitle() { }
    public void clearTitle(boolean resetTimes) { }
    public void kick(Component reason) { }
    public void sendPacket(Packet<?> packet) { }
    public boolean giveItem(ItemStack stack) { return false; }
    public int removeItems(Predicate<ItemStack> filter, int maximum) { return 0; }
    public void clearInventory() { }

    public void sendSystemMessage(Component message) { }
    public void sendOverlayMessage(Component message) { }
    public void giveExperiencePoints(int points) { }
    public void giveExperienceLevels(int levels) { }
}
