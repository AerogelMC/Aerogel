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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.TeamColor;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class ServerPlayer extends Player implements net.minecraft.world.waypoints.WaypointTransmitter {
    public ServerGamePacketListenerImpl connection;
    public AbstractContainerMenu containerMenu;
    @Override public ServerLevel level() { return null; }
    public boolean isSpectator() { return false; }
    public net.minecraft.world.level.GameType gameMode() { return null; }
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
    public ServerPlayer respawn() { return null; }
    public ServerPlayer respawn(boolean keepEverything) { return null; }
    public boolean isTransmittingWaypoint() { return false; }
    @Override
    public java.util.Optional<net.minecraft.world.waypoints.WaypointTransmitter.Connection>
        makeWaypointConnectionWith(ServerPlayer player) {
        return java.util.Optional.empty();
    }

    /** Shows a client-side block without changing the server level. */
    public void setBlock(BlockPos position, BlockState state) { }
    /** Shows several client-side blocks without changing the server level. */
    public void setBlocks(Map<BlockPos, BlockState> blocks) { }
    /** Restores the real server block for this player. */
    public void resetBlock(BlockPos position) { }
    /** Restores the real server blocks for this player. */
    public void resetBlocks(Collection<BlockPos> positions) { }
    public void setBlockEntity(BlockEntity blockEntity) { }
    public void setBlockBreakProgress(BlockPos position, int progress) { }
    public void clearBlockBreakProgress(BlockPos position) { }
    public void playBlockEvent(BlockPos position, Block block, int type, int data) { }

    /** Controls whether this player tracks and renders an entity. */
    public void setVisible(Entity entity, boolean visible) { }
    public boolean isVisible(Entity entity) { return false; }
    public void setGlowing(Entity entity, boolean glowing) { }
    public void resetGlowing(Entity entity) { }
    public void setGlowColorOverride(Entity entity, TeamColor color) { }
    public void resetGlowColorOverride(Entity entity) { }
    public void setInvisible(Entity entity, boolean invisible) { }
    public void resetInvisible(Entity entity) { }
    public void setOnFire(Entity entity, boolean onFire) { }
    public void resetOnFire(Entity entity) { }
    public void setEquipment(LivingEntity entity, EquipmentSlot slot, ItemStack item) { }
    public void resetEquipment(LivingEntity entity, EquipmentSlot slot) { }
    public void setEntityVelocity(Entity entity, Vec3 velocity) { }
    public void setEntityPosition(Entity entity, double x, double y, double z,
                                  float yaw, float pitch) { }
    public void setEntityHeadRotation(Entity entity, float yaw) { }
    public void playHandSwing(Entity entity, net.minecraft.world.InteractionHand hand) { }
    public void playCriticalHit(Entity entity, boolean magic) { }
    public void playWakeUp(Entity entity) { }
    public void playEntityEvent(Entity entity, byte eventId) { }
    public void setEntityLeash(Entity entity, Entity holder) { }
    public void setCameraView(Entity entity) { }
    public void resetCameraView() { }

    public void spawnParticle(ParticleOptions particle, double x, double y, double z,
                              int count, double offsetX, double offsetY, double offsetZ,
                              double speed) { }
    public void playSound(Holder<SoundEvent> sound, SoundSource source,
                          double x, double y, double z, float volume, float pitch) { }
    public void stopSound(Identifier sound, SoundSource source) { }
    public void stopSounds() { }
    public void setExperienceBar(float progress, int level, int totalExperience) { }
    public void resetExperienceBar() { }
    public void setHealthBar(float health, int food, float saturation) { }
    public void resetHealthBar() { }
    public void setWeather(float rainLevel, float thunderLevel) { }
    public void resetWeather() { }
    public void setWorldBorder(WorldBorder border) { }
    public void resetWorldBorder() { }
    /** Removes every persistent per-view override and restores server truth. */
    public void clearViewOverrides() { }

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
