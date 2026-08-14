package dev.aerogel.loader.mixin.core;

import com.mojang.authlib.GameProfile;
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
import org.spongepowered.asm.mixin.Mixin;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

@Mixin(targets = "net.minecraft.server.level.ServerPlayer")
abstract class ServerPlayerMixin implements ServerPlayerDisplayNameBridge {
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
        Object packet = EventHooks.construct(this,
            "net.minecraft.network.protocol.game.ClientboundTabListPacket", header, footer);
        EventHooks.call(EventHooks.field(this, "connection"), "send", packet);
    }

    @Unique
    private void aerogel$broadcastTabListName() {
        aerogel$broadcastPlayerInfo("UPDATE_DISPLAY_NAME");
    }

    @Unique
    private void aerogel$broadcastPlayerInfo(String actionName) {
        Object server = EventHooks.field(this, "server");
        if (server == null) return;
        Object action = EventHooks.staticField(this,
            "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action",
            actionName);
        Object packet = EventHooks.construct(this,
            "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket", action, this);
        Object players = EventHooks.call(EventHooks.call(server, "getPlayerList"), "getPlayers");
        if (!(players instanceof Iterable<?> iterable)) return;
        for (Object viewer : iterable) {
            EventHooks.call(EventHooks.field(viewer, "connection"), "send", packet);
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
        Object listener = EventHooks.field(this, "connection");
        EventHooks.call(listener, "send", EventHooks.construct(this,
            "net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket",
            fadeInTicks, stayTicks, fadeOutTicks));
        if (subtitle != null) {
            EventHooks.call(listener, "send", EventHooks.construct(this,
                "net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket", subtitle));
        }
        EventHooks.call(listener, "send", EventHooks.construct(this,
            "net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket", title));
    }

    @Unique
    public void clearTitle() {
        clearTitle(true);
    }

    @Unique
    public void clearTitle(boolean resetTimes) {
        Object packet = EventHooks.construct(this,
            "net.minecraft.network.protocol.game.ClientboundClearTitlesPacket", resetTimes);
        EventHooks.call(EventHooks.field(this, "connection"), "send", packet);
    }

    @Unique
    public void kick(Component reason) {
        EventHooks.call(EventHooks.field(this, "connection"), "disconnect",
            Objects.requireNonNull(reason, "reason"));
    }

    @Unique
    public void sendPacket(Packet<?> packet) {
        EventHooks.call(EventHooks.field(this, "connection"), "send",
            Objects.requireNonNull(packet, "packet"));
    }

    @Unique
    public boolean giveItem(ItemStack stack) {
        return (boolean) EventHooks.call(EventHooks.call(this, "getInventory"), "add",
            Objects.requireNonNull(stack, "stack"));
    }

    @Unique
    public int removeItems(Predicate<ItemStack> filter, int maximum) {
        Objects.requireNonNull(filter, "filter");
        if (maximum < 0) throw new IllegalArgumentException("maximum must not be negative");
        if (maximum == 0) return 0;
        return (int) EventHooks.call(EventHooks.call(this, "getInventory"),
            "clearOrCountMatchingItems", filter, maximum, null);
    }

    @Unique
    public void clearInventory() {
        EventHooks.call(EventHooks.call(this, "getInventory"), "clearContent");
    }

    @Inject(method = "startSleepInBed(Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$bedEnter(
        @Coerce Object position, CallbackInfoReturnable<Object> callbackInfo
    ) {
        PlayerBedEnterEvent event = new PlayerBedEnterEvent(
            EventHooks.cast(this), EventHooks.cast(position));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(EventHooks.eitherLeft(
                this, "net.minecraft.world.entity.player.Player$BedSleepingProblem", "OTHER_PROBLEM"));
        }
    }

    @Inject(method = "stopSleepInBed(ZZ)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$bedLeave(
        boolean resetSleepTimer, boolean updateSleepingPlayers, CallbackInfo callbackInfo
    ) {
        PlayerBedLeaveEvent event = new PlayerBedLeaveEvent(
            EventHooks.cast(this), resetSleepTimer, updateSleepingPlayers);
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.cancel();
    }

    @Inject(method = "giveExperiencePoints(I)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$experiencePoints(int amount, CallbackInfo callbackInfo) {
        PlayerExperienceChangeEvent event = new PlayerExperienceChangeEvent(
            EventHooks.cast(this), amount, PlayerExperienceChangeEvent.Unit.POINTS);
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.cancel();
    }

    @Inject(method = "giveExperienceLevels(I)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$experienceLevels(int amount, CallbackInfo callbackInfo) {
        PlayerExperienceChangeEvent event = new PlayerExperienceChangeEvent(
            EventHooks.cast(this), amount, PlayerExperienceChangeEvent.Unit.LEVELS);
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.cancel();
    }

    @Inject(method = "completeUsingItem()V", at = @At("HEAD"), cancellable = true)
    private void aerogel$itemConsume(CallbackInfo callbackInfo) {
        PlayerItemConsumeEvent event = new PlayerItemConsumeEvent(
            EventHooks.cast(this), EventHooks.cast(EventHooks.call(this, "getUsedItemHand")),
            EventHooks.cast(EventHooks.call(this, "getUseItem")));
        EventHooks.post(event);
        if (event.isCancelled()) {
            EventHooks.call(this, "stopUsingItem");
            callbackInfo.cancel();
        }
    }

    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$openInventory(
        @Coerce Object provider, CallbackInfoReturnable<OptionalInt> callbackInfo
    ) {
        InventoryOpenEvent event = new InventoryOpenEvent(
            EventHooks.cast(this), EventHooks.cast(provider));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(OptionalInt.empty());
        }
    }

    @Inject(method = "closeContainer()V", at = @At("HEAD"))
    private void aerogel$closeInventory(CallbackInfo callbackInfo) {
        EventHooks.post(new InventoryCloseEvent(EventHooks.cast(this)));
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)"
        + "Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void aerogel$dropItem(
        @Coerce Object itemStack, boolean randomThrow, boolean retainOwnership,
        CallbackInfoReturnable<Object> callbackInfo
    ) {
        PlayerDropItemEvent event = new PlayerDropItemEvent(
            EventHooks.cast(this), EventHooks.cast(itemStack), randomThrow, retainOwnership);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(null);
        }
    }

    @Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
    private void aerogel$playerDeath(@Coerce Object source, CallbackInfo callbackInfo) {
        EventHooks.post(new PlayerDeathEvent(EventHooks.cast(this), EventHooks.cast(source)));
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDD"
        + "Ljava/util/Set;FFZ)Z", at = @At("HEAD"), cancellable = true)
    private void aerogel$teleport(
        @Coerce Object level, double x, double y, double z, @Coerce Object relative,
        float yaw, float pitch, boolean dismount,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        PlayerTeleportEvent event = new PlayerTeleportEvent(
            EventHooks.cast(this), EventHooks.cast(level), x, y, z, yaw, pitch);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        }
    }
}
