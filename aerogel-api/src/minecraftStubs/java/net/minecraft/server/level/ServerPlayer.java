package net.minecraft.server.level;

import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import java.util.Set;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class ServerPlayer extends Player {
    public ServerGamePacketListenerImpl connection;
    public AbstractContainerMenu containerMenu;
    @Override public ServerLevel level() { return null; }
    public Component getDisplayName() { return null; }
    public void setDisplayName(Component displayName) { }
    public void clearDisplayName() { }
    public void setTabListName(Component name) { }
    public void clearTabListName() { }
    public void setTabListHidden(boolean hidden) { }
    public boolean isTabListHidden() { return false; }
    public void setNameTagHidden(boolean hidden) { }
    public boolean isNameTagHidden() { return false; }
    public void setTabListHeader(Component header) { }
    public void setTabListFooter(Component footer) { }
    public void setTabListHeaderFooter(Component header, Component footer) { }
    public void clearTabListHeaderFooter() { }
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
    public Abilities getAbilities() { return null; }
    public void onUpdateAbilities() { }
    public ItemStack getMainHandItem() { return null; }
    public ItemStack getOffhandItem() { return null; }
    public ClientInformation clientInformation() { return null; }
    public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider) {
        return java.util.OptionalInt.empty();
    }
    public void closeContainer() { }
    public void openDialog(net.minecraft.core.Holder<net.minecraft.server.dialog.Dialog> dialog) { }
    public Inventory getInventory() { return null; }
    public ItemEntity drop(ItemStack stack, boolean randomThrow, boolean retainOwnership) { return null; }
}
