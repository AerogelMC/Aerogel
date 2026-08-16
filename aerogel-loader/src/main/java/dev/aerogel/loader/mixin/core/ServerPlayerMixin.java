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
import dev.aerogel.loader.internal.PlayerViewService;
import dev.aerogel.loader.internal.RespawnGameListenerBridge;
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
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.TeamColor;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import java.util.Set;
import java.util.Collection;
import java.util.Map;

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
        PlayerViewService.transfer(previous, self());
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

    @Unique
    public ServerPlayer respawn() {
        return respawn(false);
    }

    @Unique
    public ServerPlayer respawn(boolean keepEverything) {
        ServerPlayer current = self();
        if (!server.isSameThread()) {
            throw new IllegalStateException("Player respawn must run on the Minecraft server thread");
        }
        if (connection.player != current) {
            throw new IllegalStateException("Cannot respawn a stale ServerPlayer instance");
        }
        ServerPlayer replacement = server.getPlayerList().respawn(
            current, keepEverything, Entity.RemovalReason.KILLED);
        connection.player = replacement;
        connection.resetPosition();
        ((RespawnGameListenerBridge) connection).aerogel$restartClientLoadTimerAfterRespawn();
        return replacement;
    }

    @Unique
    public void setBlock(BlockPos position, BlockState state) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
        PlayerViewService.rememberBlock(self(), position);
        connection.send(new ClientboundBlockUpdatePacket(position, state));
    }

    @Unique
    public void setBlocks(Map<BlockPos, BlockState> blocks) {
        Objects.requireNonNull(blocks, "blocks");
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            setBlock(entry.getKey(), entry.getValue());
        }
    }

    @Unique
    public void resetBlock(BlockPos position) {
        Objects.requireNonNull(position, "position");
        PlayerViewService.forgetBlock(self(), position);
        connection.send(new ClientboundBlockUpdatePacket(
            position, self().level().getBlockState(position)));
    }

    @Unique
    public void resetBlocks(Collection<BlockPos> positions) {
        Objects.requireNonNull(positions, "positions");
        for (BlockPos position : positions) resetBlock(position);
    }

    @Unique
    public void setBlockEntity(BlockEntity blockEntity) {
        Objects.requireNonNull(blockEntity, "blockEntity");
        PlayerViewService.rememberBlock(self(), blockEntity.getBlockPos());
        connection.send(ClientboundBlockEntityDataPacket.create(blockEntity));
    }

    @Unique
    public void setBlockBreakProgress(BlockPos position, int progress) {
        Objects.requireNonNull(position, "position");
        if (progress < 0 || progress > 9) {
            throw new IllegalArgumentException("progress must be between 0 and 9");
        }
        PlayerViewService.rememberBreak(self(), position);
        connection.send(new ClientboundBlockDestructionPacket(
            PlayerViewService.breakId(position), position, progress));
    }

    @Unique
    public void clearBlockBreakProgress(BlockPos position) {
        Objects.requireNonNull(position, "position");
        PlayerViewService.forgetBreak(self(), position);
        connection.send(new ClientboundBlockDestructionPacket(
            PlayerViewService.breakId(position), position, -1));
    }

    @Unique
    public void playBlockEvent(BlockPos position, Block block, int type, int data) {
        connection.send(new ClientboundBlockEventPacket(
            Objects.requireNonNull(position, "position"),
            Objects.requireNonNull(block, "block"), type, data));
    }

    @Unique
    public void setVisible(Entity entity, boolean visible) {
        PlayerViewService.setVisible(self(), entity, visible);
    }

    @Unique
    public boolean isVisible(Entity entity) {
        return PlayerViewService.isVisible(self(), Objects.requireNonNull(entity, "entity"));
    }

    @Unique
    public void setGlowing(Entity entity, boolean glowing) {
        PlayerViewService.setGlowing(self(), entity, glowing);
    }

    @Unique
    public void resetGlowing(Entity entity) {
        PlayerViewService.resetGlowing(self(), entity);
    }

    @Unique
    public void setGlowColorOverride(Entity entity, TeamColor color) {
        PlayerViewService.setGlowColorOverride(self(), entity, color);
    }

    @Unique
    public void resetGlowColorOverride(Entity entity) {
        PlayerViewService.resetGlowColorOverride(self(), entity);
    }

    @Unique
    public void setInvisible(Entity entity, boolean invisible) {
        PlayerViewService.setInvisible(self(), entity, invisible);
    }

    @Unique
    public void resetInvisible(Entity entity) {
        PlayerViewService.resetInvisible(self(), entity);
    }

    @Unique
    public void setOnFire(Entity entity, boolean onFire) {
        PlayerViewService.setOnFire(self(), entity, onFire);
    }

    @Unique
    public void resetOnFire(Entity entity) {
        PlayerViewService.resetOnFire(self(), entity);
    }

    @Unique
    public void setEquipment(LivingEntity entity, EquipmentSlot slot, ItemStack item) {
        PlayerViewService.setEquipment(self(), entity, slot, item);
    }

    @Unique
    public void resetEquipment(LivingEntity entity, EquipmentSlot slot) {
        PlayerViewService.resetEquipment(self(), entity, slot);
    }

    @Unique
    public void setEntityVelocity(Entity entity, Vec3 velocity) {
        Objects.requireNonNull(entity, "entity");
        connection.send(new ClientboundSetEntityMotionPacket(
            entity.getId(), Objects.requireNonNull(velocity, "velocity")));
    }

    @Unique
    public void setEntityPosition(
        Entity entity, double x, double y, double z, float yaw, float pitch
    ) {
        Objects.requireNonNull(entity, "entity");
        connection.send(new ClientboundEntityPositionSyncPacket(
            entity.getId(), new PositionMoveRotation(
                new Vec3(x, y, z), entity.getDeltaMovement(), yaw, pitch), entity.onGround()));
    }

    @Unique
    public void setEntityHeadRotation(Entity entity, float yaw) {
        Objects.requireNonNull(entity, "entity");
        byte rotation = (byte) Math.floor(yaw * 256.0F / 360.0F);
        connection.send(new ClientboundRotateHeadPacket(entity, rotation));
    }

    @Unique
    public void playHandSwing(Entity entity, InteractionHand hand) {
        Objects.requireNonNull(entity, "entity");
        int animation = Objects.requireNonNull(hand, "hand") == InteractionHand.MAIN_HAND ? 0 : 3;
        connection.send(new ClientboundAnimatePacket(entity, animation));
    }

    @Unique
    public void playCriticalHit(Entity entity, boolean magic) {
        connection.send(new ClientboundAnimatePacket(
            Objects.requireNonNull(entity, "entity"), magic ? 5 : 4));
    }

    @Unique
    public void playWakeUp(Entity entity) {
        connection.send(new ClientboundAnimatePacket(
            Objects.requireNonNull(entity, "entity"), 2));
    }

    @Unique
    public void playEntityEvent(Entity entity, byte eventId) {
        connection.send(new ClientboundEntityEventPacket(
            Objects.requireNonNull(entity, "entity"), eventId));
    }

    @Unique
    public void setEntityLeash(Entity entity, Entity holder) {
        connection.send(new ClientboundSetEntityLinkPacket(
            Objects.requireNonNull(entity, "entity"), holder));
    }

    @Unique
    public void setCameraView(Entity entity) {
        connection.send(new ClientboundSetCameraPacket(
            Objects.requireNonNull(entity, "entity")));
    }

    @Unique
    public void resetCameraView() {
        connection.send(new ClientboundSetCameraPacket(self()));
    }

    @Unique
    public void spawnParticle(
        ParticleOptions particle, double x, double y, double z, int count,
        double offsetX, double offsetY, double offsetZ, double speed
    ) {
        if (count < 0) throw new IllegalArgumentException("count must not be negative");
        connection.send(new ClientboundLevelParticlesPacket(
            Objects.requireNonNull(particle, "particle"), true, true, x, y, z,
            (float) offsetX, (float) offsetY, (float) offsetZ, (float) speed, count));
    }

    @Unique
    public void playSound(
        Holder<SoundEvent> sound, SoundSource source,
        double x, double y, double z, float volume, float pitch
    ) {
        if (volume < 0.0F) throw new IllegalArgumentException("volume must not be negative");
        if (pitch < 0.0F) throw new IllegalArgumentException("pitch must not be negative");
        connection.send(new ClientboundSoundPacket(
            Objects.requireNonNull(sound, "sound"), Objects.requireNonNull(source, "source"),
            x, y, z, volume, pitch, java.util.concurrent.ThreadLocalRandom.current().nextLong()));
    }

    @Unique
    public void stopSound(Identifier sound, SoundSource source) {
        connection.send(new ClientboundStopSoundPacket(
            Objects.requireNonNull(sound, "sound"), Objects.requireNonNull(source, "source")));
    }

    @Unique
    public void stopSounds() {
        connection.send(new ClientboundStopSoundPacket(null, null));
    }

    @Unique
    public void setExperienceBar(float progress, int level, int totalExperience) {
        if (progress < 0.0F || progress > 1.0F) {
            throw new IllegalArgumentException("progress must be between 0 and 1");
        }
        if (level < 0 || totalExperience < 0) {
            throw new IllegalArgumentException("experience values must not be negative");
        }
        connection.send(new ClientboundSetExperiencePacket(progress, totalExperience, level));
    }

    @Unique
    public void resetExperienceBar() {
        connection.send(new ClientboundSetExperiencePacket(
            self().experienceProgress, self().totalExperience, self().experienceLevel));
    }

    @Unique
    public void setHealthBar(float health, int food, float saturation) {
        if (health < 0.0F || food < 0 || food > 20 || saturation < 0.0F) {
            throw new IllegalArgumentException("invalid health bar values");
        }
        connection.send(new ClientboundSetHealthPacket(health, food, saturation));
    }

    @Unique
    public void resetHealthBar() {
        connection.send(new ClientboundSetHealthPacket(
            self().getHealth(), self().getFoodData().getFoodLevel(),
            self().getFoodData().getSaturationLevel()));
    }

    @Unique
    public void setWeather(float rainLevel, float thunderLevel) {
        if (rainLevel < 0.0F || rainLevel > 1.0F
            || thunderLevel < 0.0F || thunderLevel > 1.0F) {
            throw new IllegalArgumentException("weather levels must be between 0 and 1");
        }
        PlayerViewService.sendWeather(self(), rainLevel, thunderLevel);
    }

    @Unique
    public void resetWeather() {
        ServerLevel level = self().level();
        PlayerViewService.sendWeather(
            self(), level.getRainLevel(1.0F), level.getThunderLevel(1.0F));
    }

    @Unique
    public void setWorldBorder(WorldBorder border) {
        connection.send(new ClientboundInitializeBorderPacket(
            Objects.requireNonNull(border, "border")));
    }

    @Unique
    public void resetWorldBorder() {
        connection.send(new ClientboundInitializeBorderPacket(self().level().getWorldBorder()));
    }

    @Unique
    public void clearViewOverrides() {
        PlayerViewService.clear(self());
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
