package dev.aerogel.loader.mixin.core;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import dev.aerogel.api.event.inventory.InventoryCloseEvent;
import dev.aerogel.api.event.inventory.InventoryOpenEvent;
import dev.aerogel.api.event.item.PlayerDropItemEvent;
import dev.aerogel.api.event.player.PlayerDeathEvent;
import dev.aerogel.api.event.player.PlayerBedEnterEvent;
import dev.aerogel.api.event.player.PlayerBedLeaveEvent;
import dev.aerogel.api.event.player.PlayerExperienceChangeEvent;
import dev.aerogel.api.event.player.PlayerItemConsumeEvent;
import dev.aerogel.api.event.player.PlayerTeleportEvent;
import dev.aerogel.loader.event.EventHooks;
import dev.aerogel.loader.internal.ServerPlayerDisplayNameBridge;
import dev.aerogel.loader.internal.PlayerNameTagService;
import dev.aerogel.loader.internal.DeathDropCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import java.util.Set;

@Mixin(targets = "net.minecraft.server.level.ServerPlayer")
abstract class ServerPlayerMixin implements ServerPlayerDisplayNameBridge {
    @Shadow public ServerGamePacketListenerImpl connection;
    @Shadow @Final private MinecraftServer server;
    @Unique
    private Component aerogel$displayName;

    @Unique
    private Component aerogel$tabListName;

    @Unique
    private Component aerogel$tabListHeader;

    @Unique
    private Component aerogel$tabListFooter;

    @Unique
    private GameProfile aerogel$packetProfile;

    @Unique
    private boolean aerogel$tabListHidden;

    @Unique
    private boolean aerogel$nameTagHidden;

    @Unique private boolean aerogel$experienceOverride;
    @Unique private boolean aerogel$teleportOverride;
    @Unique private boolean aerogel$dropOverride;

    @Unique
    public void setDisplayName(Component displayName) {
        aerogel$displayName = Objects.requireNonNull(displayName, "displayName");
        aerogel$broadcastTabListName();
        PlayerNameTagService.refresh((ServerPlayer) (Object) this);
    }

    @Unique
    public void clearDisplayName() {
        aerogel$displayName = null;
        aerogel$broadcastTabListName();
        PlayerNameTagService.refresh((ServerPlayer) (Object) this);
    }

    @Override
    public Component aerogel$displayNameOverride() {
        return aerogel$displayName;
    }

    @Override
    public GameProfile aerogel$packetProfileOverride() {
        return aerogel$packetProfile;
    }

    @Override
    public void aerogel$packetProfileOverride(GameProfile profile) {
        aerogel$packetProfile = profile;
    }

    @Override
    public boolean aerogel$tabListHidden() {
        return aerogel$tabListHidden;
    }

    @Override
    public boolean aerogel$nameTagHidden() {
        return aerogel$nameTagHidden;
    }

    @Inject(method = "restoreFrom(Lnet/minecraft/server/level/ServerPlayer;Z)V", at = @At("HEAD"))
    private void aerogel$restoreDisplayState(
        ServerPlayer previous, boolean keepEverything, CallbackInfo callbackInfo
    ) {
        ServerPlayerMixin source = (ServerPlayerMixin) (Object) previous;
        aerogel$displayName = source.aerogel$displayName;
        aerogel$tabListName = source.aerogel$tabListName;
        aerogel$tabListHeader = source.aerogel$tabListHeader;
        aerogel$tabListFooter = source.aerogel$tabListFooter;
        aerogel$tabListHidden = source.aerogel$tabListHidden;
        aerogel$nameTagHidden = source.aerogel$nameTagHidden;
    }

    @Unique
    public void setTabListName(Component name) {
        aerogel$tabListName = Objects.requireNonNull(name, "name");
        aerogel$broadcastTabListName();
    }

    @Unique
    public void clearTabListName() {
        aerogel$tabListName = null;
        aerogel$broadcastTabListName();
    }

    @Unique
    public void setTabListHidden(boolean hidden) {
        if (aerogel$tabListHidden == hidden) return;
        aerogel$tabListHidden = hidden;
        aerogel$broadcastPlayerInfo("UPDATE_LISTED");
    }

    @Unique
    public boolean isTabListHidden() {
        return aerogel$tabListHidden;
    }

    @Unique
    public void setNameTagHidden(boolean hidden) {
        if (aerogel$nameTagHidden == hidden) return;
        aerogel$nameTagHidden = hidden;
        PlayerNameTagService.refresh((ServerPlayer) (Object) this);
    }

    @Unique
    public boolean isNameTagHidden() {
        return aerogel$nameTagHidden;
    }

    @Unique
    public void setTabListHeader(Component header) {
        aerogel$tabListHeader = Objects.requireNonNull(header, "header");
        aerogel$sendTabListHeaderFooter();
    }

    @Unique
    public void setTabListFooter(Component footer) {
        aerogel$tabListFooter = Objects.requireNonNull(footer, "footer");
        aerogel$sendTabListHeaderFooter();
    }

    @Unique
    public void setTabListHeaderFooter(Component header, Component footer) {
        aerogel$tabListHeader = Objects.requireNonNull(header, "header");
        aerogel$tabListFooter = Objects.requireNonNull(footer, "footer");
        aerogel$sendTabListHeaderFooter();
    }

    @Unique
    public void clearTabListHeaderFooter() {
        aerogel$tabListHeader = null;
        aerogel$tabListFooter = null;
        aerogel$sendTabListHeaderFooter();
    }

    @Unique
    private void aerogel$sendTabListHeaderFooter() {
        Component header = aerogel$tabListHeader == null ? Component.empty() : aerogel$tabListHeader;
        Component footer = aerogel$tabListFooter == null ? Component.empty() : aerogel$tabListFooter;
        connection.send(new ClientboundTabListPacket(header, footer));
    }

    @Unique
    private void aerogel$broadcastTabListName() {
        aerogel$broadcastPlayerInfo("UPDATE_DISPLAY_NAME");
    }

    @Unique
    private void aerogel$broadcastPlayerInfo(String actionName) {
        if (server == null) return;
        ClientboundPlayerInfoUpdatePacket.Action action =
            ClientboundPlayerInfoUpdatePacket.Action.valueOf(actionName);
        ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
            action, self());
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            viewer.connection.send(packet);
        }
    }

    @Inject(method = "getTabListDisplayName()Lnet/minecraft/network/chat/Component;",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$tabListDisplayName(CallbackInfoReturnable<Component> callbackInfo) {
        Component displayName = aerogel$tabListName != null ? aerogel$tabListName : aerogel$displayName;
        if (displayName != null) callbackInfo.setReturnValue(displayName);
    }

    @Unique
    public void sendTitle(Component title) {
        sendTitle(title, null, 10, 70, 20);
    }

    @Unique
    public void sendTitle(
        Component title, Component subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks
    ) {
        Objects.requireNonNull(title, "title");
        if (fadeInTicks < 0 || stayTicks < 0 || fadeOutTicks < 0) {
            throw new IllegalArgumentException("title times must not be negative");
        }
        connection.send(new ClientboundSetTitlesAnimationPacket(
            fadeInTicks, stayTicks, fadeOutTicks));
        if (subtitle != null) {
            connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
        connection.send(new ClientboundSetTitleTextPacket(title));
    }

    @Unique
    public void clearTitle() {
        clearTitle(true);
    }

    @Unique
    public void clearTitle(boolean resetTimes) {
        connection.send(new ClientboundClearTitlesPacket(resetTimes));
    }

    @Unique
    public void kick(Component reason) {
        connection.disconnect(Objects.requireNonNull(reason, "reason"));
    }

    @Unique
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void sendPacket(Packet<?> packet) {
        connection.send((Packet) Objects.requireNonNull(packet, "packet"));
    }

    @Unique
    public boolean giveItem(ItemStack stack) {
        return self().getInventory().add(Objects.requireNonNull(stack, "stack"));
    }

    @Unique
    public int removeItems(Predicate<ItemStack> filter, int maximum) {
        Objects.requireNonNull(filter, "filter");
        if (maximum < 0) throw new IllegalArgumentException("maximum must not be negative");
        if (maximum == 0) return 0;
        return self().getInventory().clearOrCountMatchingItems(filter, maximum, null);
    }

    @Unique
    public void clearInventory() {
        self().getInventory().clearContent();
    }

    @Inject(method = "startSleepInBed(Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$bedEnter(
        BlockPos position,
        CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> callbackInfo
    ) {
        if (!EventHooks.hasListeners(PlayerBedEnterEvent.class)) return;
        PlayerBedEnterEvent event = new PlayerBedEnterEvent(
            self(), position);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(Either.left(Player.BedSleepingProblem.OTHER_PROBLEM));
        }
    }

    @Inject(method = "stopSleepInBed(ZZ)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$bedLeave(
        boolean resetSleepTimer, boolean updateSleepingPlayers, CallbackInfo callbackInfo
    ) {
        if (!EventHooks.hasListeners(PlayerBedLeaveEvent.class)) return;
        PlayerBedLeaveEvent event = new PlayerBedLeaveEvent(
            self(), resetSleepTimer, updateSleepingPlayers);
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.cancel();
    }

    @Inject(method = "giveExperiencePoints(I)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$experiencePoints(int amount, CallbackInfo callbackInfo) {
        if (aerogel$experienceOverride
            || !EventHooks.hasListeners(PlayerExperienceChangeEvent.class)) return;
        PlayerExperienceChangeEvent event = new PlayerExperienceChangeEvent(
            self(), amount, PlayerExperienceChangeEvent.Unit.POINTS);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.amount() != amount) {
            aerogel$experienceOverride = true;
            try {
                self().giveExperiencePoints(event.amount());
            } finally {
                aerogel$experienceOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "giveExperienceLevels(I)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$experienceLevels(int amount, CallbackInfo callbackInfo) {
        if (aerogel$experienceOverride
            || !EventHooks.hasListeners(PlayerExperienceChangeEvent.class)) return;
        PlayerExperienceChangeEvent event = new PlayerExperienceChangeEvent(
            self(), amount, PlayerExperienceChangeEvent.Unit.LEVELS);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.amount() != amount) {
            aerogel$experienceOverride = true;
            try {
                self().giveExperienceLevels(event.amount());
            } finally {
                aerogel$experienceOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "completeUsingItem()V", at = @At("HEAD"), cancellable = true)
    private void aerogel$itemConsume(CallbackInfo callbackInfo) {
        if (!EventHooks.hasListeners(PlayerItemConsumeEvent.class)) return;
        PlayerItemConsumeEvent event = new PlayerItemConsumeEvent(
            self(), self().getUsedItemHand(), self().getUseItem());
        EventHooks.post(event);
        if (event.isCancelled()) {
            self().stopUsingItem();
            callbackInfo.cancel();
        }
    }

    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$openInventory(
        MenuProvider provider, CallbackInfoReturnable<OptionalInt> callbackInfo
    ) {
        if (!EventHooks.hasListeners(InventoryOpenEvent.class)) return;
        InventoryOpenEvent event = new InventoryOpenEvent(
            self(), provider);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(OptionalInt.empty());
        }
    }

    @Inject(method = "closeContainer()V", at = @At("HEAD"))
    private void aerogel$closeInventory(CallbackInfo callbackInfo) {
        if (EventHooks.hasListeners(InventoryCloseEvent.class)) {
            EventHooks.post(new InventoryCloseEvent(self()));
        }
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)"
        + "Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void aerogel$dropItem(
        ItemStack itemStack, boolean randomThrow, boolean retainOwnership,
        CallbackInfoReturnable<ItemEntity> callbackInfo
    ) {
        if (DeathDropCapture.isHandling((ServerPlayer) (Object) this) || aerogel$dropOverride
            || !EventHooks.hasListeners(PlayerDropItemEvent.class)) return;
        PlayerDropItemEvent event = new PlayerDropItemEvent(
            self(), itemStack, randomThrow, retainOwnership);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(null);
        } else if (event.itemStack() != itemStack
            || event.randomThrow() != randomThrow
            || event.retainOwnership() != retainOwnership) {
            aerogel$dropOverride = true;
            try {
                callbackInfo.setReturnValue(self().drop(event.itemStack(),
                    event.randomThrow(), event.retainOwnership()));
            } finally {
                aerogel$dropOverride = false;
            }
        }
    }

    @Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
    private void aerogel$playerDeath(DamageSource source, CallbackInfo callbackInfo) {
        if (EventHooks.hasListeners(PlayerDeathEvent.class)) {
            EventHooks.post(new PlayerDeathEvent(self(), source));
        }
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDD"
        + "Ljava/util/Set;FFZ)Z", at = @At("HEAD"), cancellable = true)
    private void aerogel$teleport(
        ServerLevel level, double x, double y, double z, Set<Relative> relative,
        float yaw, float pitch, boolean dismount,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (aerogel$teleportOverride || !EventHooks.hasListeners(PlayerTeleportEvent.class)) return;
        PlayerTeleportEvent event = new PlayerTeleportEvent(
            self(), level, x, y, z, yaw, pitch);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        } else if (event.destinationLevel() != level
            || Double.compare(event.x(), x) != 0
            || Double.compare(event.y(), y) != 0
            || Double.compare(event.z(), z) != 0
            || Float.compare(event.yaw(), yaw) != 0
            || Float.compare(event.pitch(), pitch) != 0) {
            aerogel$teleportOverride = true;
            try {
                callbackInfo.setReturnValue(self().teleportTo(
                    event.destinationLevel(), event.x(), event.y(), event.z(), relative,
                    event.yaw(), event.pitch(), dismount));
            } finally {
                aerogel$teleportOverride = false;
            }
        }
    }

    @Unique
    private ServerPlayer self() {
        return (ServerPlayer) (Object) this;
    }
}
