package dev.aerogel.loader.internal;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Persistent, per-viewer state used by the ServerPlayer view API. */
public final class PlayerViewService {
    private static final byte ON_FIRE = 0x01;
    private static final byte INVISIBLE = 0x20;
    private static final byte GLOWING = 0x40;
    private static final Map<ServerPlayer, ViewState> STATES = new WeakHashMap<>();

    private PlayerViewService() {
    }

    public static void setVisible(ServerPlayer viewer, Entity entity, boolean visible) {
        requireSameLevel(viewer, entity);
        ViewState state = state(viewer);
        if (visible) state.hidden.remove(entity.getUUID());
        else state.hidden.add(entity.getUUID());

        TrackedEntityBridge tracked = tracked(entity);
        if (tracked == null) return;
        if (visible) tracked.aerogel$updatePlayer(viewer);
        else tracked.aerogel$removePlayer(viewer);
    }

    public static boolean isVisible(ServerPlayer viewer, Entity entity) {
        synchronized (STATES) {
            ViewState state = STATES.get(viewer);
            return state == null || !state.hidden.contains(entity.getUUID());
        }
    }

    public static boolean isHidden(ServerPlayer viewer, Entity entity) {
        return !isVisible(viewer, entity);
    }

    public static void setGlowing(ServerPlayer viewer, Entity entity, boolean value) {
        setFlag(viewer, entity, GLOWING, value);
    }

    public static void resetGlowing(ServerPlayer viewer, Entity entity) {
        resetFlag(viewer, entity, GLOWING);
    }

    public static void setGlowColorOverride(
        ServerPlayer viewer, Entity entity, TeamColor color
    ) {
        requireSameLevel(viewer, entity);
        Objects.requireNonNull(color, "color");
        ViewState state = state(viewer);
        boolean first = !state.glowColors.containsKey(entity.getUUID());
        state.glowColors.put(entity.getUUID(), color);
        PlayerTeam team = glowTeam(viewer, entity, color);
        viewer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, first));
        if (first) {
            viewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                team, entity.getScoreboardName(), ClientboundSetPlayerTeamPacket.Action.ADD));
        }
    }

    public static void resetGlowColorOverride(ServerPlayer viewer, Entity entity) {
        requireSameLevel(viewer, entity);
        TeamColor color = state(viewer).glowColors.remove(entity.getUUID());
        if (color == null) return;
        restoreGlowTeam(viewer, entity, color);
    }

    public static void setInvisible(ServerPlayer viewer, Entity entity, boolean value) {
        setFlag(viewer, entity, INVISIBLE, value);
    }

    public static void resetInvisible(ServerPlayer viewer, Entity entity) {
        resetFlag(viewer, entity, INVISIBLE);
    }

    public static void setOnFire(ServerPlayer viewer, Entity entity, boolean value) {
        setFlag(viewer, entity, ON_FIRE, value);
    }

    public static void resetOnFire(ServerPlayer viewer, Entity entity) {
        resetFlag(viewer, entity, ON_FIRE);
    }

    public static void setEquipment(
        ServerPlayer viewer, LivingEntity entity, EquipmentSlot slot, ItemStack item
    ) {
        requireSameLevel(viewer, entity);
        Objects.requireNonNull(slot, "slot");
        ItemStack snapshot = Objects.requireNonNull(item, "item").copy();
        state(viewer).equipment
            .computeIfAbsent(entity.getUUID(), ignored -> new EnumMap<>(EquipmentSlot.class))
            .put(slot, snapshot);
        viewer.connection.send(new ClientboundSetEquipmentPacket(
            entity.getId(), List.of(Pair.of(slot, snapshot))));
    }

    public static void resetEquipment(
        ServerPlayer viewer, LivingEntity entity, EquipmentSlot slot
    ) {
        requireSameLevel(viewer, entity);
        Objects.requireNonNull(slot, "slot");
        ViewState state = state(viewer);
        EnumMap<EquipmentSlot, ItemStack> overrides = state.equipment.get(entity.getUUID());
        if (overrides != null) {
            overrides.remove(slot);
            if (overrides.isEmpty()) state.equipment.remove(entity.getUUID());
        }
        viewer.connection.send(new ClientboundSetEquipmentPacket(
            entity.getId(), List.of(Pair.of(slot, entity.getItemBySlot(slot).copy()))));
    }

    public static Packet<?> transform(ServerPlayer viewer, Packet<?> packet) {
        ViewState state;
        synchronized (STATES) {
            state = STATES.get(viewer);
        }
        if (state == null) return packet;

        if (packet instanceof ClientboundSetEntityDataPacket metadata) {
            return transformMetadata(viewer, state, metadata);
        }
        if (packet instanceof ClientboundSetEquipmentPacket equipment) {
            return transformEquipment(viewer, state, equipment);
        }
        if (packet instanceof ClientboundBundlePacket bundle) {
            return transformBundle(viewer, bundle);
        }
        return packet;
    }

    public static void rememberBlock(ServerPlayer viewer, BlockPos position) {
        state(viewer).blocks.add(Objects.requireNonNull(position, "position"));
    }

    public static void forgetBlock(ServerPlayer viewer, BlockPos position) {
        state(viewer).blocks.remove(Objects.requireNonNull(position, "position"));
    }

    public static void forgetBlocks(ServerPlayer viewer, Collection<BlockPos> positions) {
        state(viewer).blocks.removeAll(positions);
    }

    public static void rememberBreak(ServerPlayer viewer, BlockPos position) {
        state(viewer).breaks.add(Objects.requireNonNull(position, "position"));
    }

    public static void forgetBreak(ServerPlayer viewer, BlockPos position) {
        state(viewer).breaks.remove(Objects.requireNonNull(position, "position"));
    }

    public static void clear(ServerPlayer viewer) {
        ViewState state;
        synchronized (STATES) {
            state = STATES.remove(viewer);
        }
        if (state == null) return;

        ServerLevel level = viewer.level();
        for (BlockPos position : state.blocks) {
            viewer.connection.send(new ClientboundBlockUpdatePacket(
                position, level.getBlockState(position)));
        }
        for (BlockPos position : state.breaks) {
            viewer.connection.send(new ClientboundBlockDestructionPacket(
                breakId(position), position, -1));
        }
        for (UUID uuid : state.hidden) {
            Optional.ofNullable(level.getEntityInAnyDimension(uuid)).ifPresent(entity -> {
                TrackedEntityBridge tracked = tracked(entity);
                if (tracked != null) tracked.aerogel$updatePlayer(viewer);
            });
        }
        for (UUID uuid : state.flags.keySet()) {
            Optional.ofNullable(level.getEntityInAnyDimension(uuid))
                .ifPresent(entity -> sendFlags(viewer, entity, null));
        }
        for (Map.Entry<UUID, TeamColor> entry : state.glowColors.entrySet()) {
            Optional.ofNullable(level.getEntityInAnyDimension(entry.getKey())).ifPresent(entity ->
                restoreGlowTeam(viewer, entity, entry.getValue()));
        }
        for (Map.Entry<UUID, EnumMap<EquipmentSlot, ItemStack>> entry
            : state.equipment.entrySet()) {
            Optional.ofNullable(level.getEntityInAnyDimension(entry.getKey()))
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast).ifPresent(entity -> {
                    List<Pair<EquipmentSlot, ItemStack>> actual = new ArrayList<>();
                    for (EquipmentSlot slot : entry.getValue().keySet()) {
                        actual.add(Pair.of(slot, entity.getItemBySlot(slot).copy()));
                    }
                    viewer.connection.send(new ClientboundSetEquipmentPacket(
                        entity.getId(), actual));
                });
        }
        viewer.connection.send(new ClientboundSetCameraPacket(viewer));
        viewer.connection.send(new ClientboundSetExperiencePacket(
            viewer.experienceProgress, viewer.totalExperience, viewer.experienceLevel));
        viewer.connection.send(new ClientboundSetHealthPacket(
            viewer.getHealth(), viewer.getFoodData().getFoodLevel(),
            viewer.getFoodData().getSaturationLevel()));
        sendWeather(viewer, level.getRainLevel(1.0F), level.getThunderLevel(1.0F));
        viewer.connection.send(new ClientboundInitializeBorderPacket(level.getWorldBorder()));
    }

    /** Moves viewer-owned overrides to Minecraft's replacement player instance after respawn. */
    public static void transfer(ServerPlayer previous, ServerPlayer replacement) {
        synchronized (STATES) {
            ViewState state = STATES.remove(previous);
            if (state != null) STATES.put(replacement, state);
        }
    }

    public static int breakId(BlockPos position) {
        return position.hashCode() ^ 0x41E0_6E1;
    }

    public static void sendWeather(ServerPlayer viewer, float rain, float thunder) {
        viewer.connection.send(new ClientboundGameEventPacket(
            rain > 0.0F ? ClientboundGameEventPacket.START_RAINING
                : ClientboundGameEventPacket.STOP_RAINING, 0.0F));
        viewer.connection.send(new ClientboundGameEventPacket(
            ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rain));
        viewer.connection.send(new ClientboundGameEventPacket(
            ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunder));
    }

    private static void setFlag(ServerPlayer viewer, Entity entity, byte bit, boolean value) {
        requireSameLevel(viewer, entity);
        ViewState state = state(viewer);
        FlagOverride current = state.flags.getOrDefault(entity.getUUID(), FlagOverride.NONE);
        byte mask = (byte) (current.mask | bit);
        byte values = value ? (byte) (current.values | bit) : (byte) (current.values & ~bit);
        FlagOverride updated = new FlagOverride(mask, values);
        state.flags.put(entity.getUUID(), updated);
        sendFlags(viewer, entity, updated);
    }

    private static void resetFlag(ServerPlayer viewer, Entity entity, byte bit) {
        requireSameLevel(viewer, entity);
        ViewState state = state(viewer);
        FlagOverride current = state.flags.get(entity.getUUID());
        if (current == null) return;
        byte mask = (byte) (current.mask & ~bit);
        FlagOverride updated = new FlagOverride(mask, (byte) (current.values & mask));
        if (mask == 0) state.flags.remove(entity.getUUID());
        else state.flags.put(entity.getUUID(), updated);
        sendFlags(viewer, entity, mask == 0 ? null : updated);
    }

    private static void sendFlags(
        ServerPlayer viewer, Entity entity, FlagOverride override
    ) {
        EntityViewBridge bridge = bridge(entity);
        byte flags = override == null ? bridge.aerogel$sharedFlags()
            : override.apply(bridge.aerogel$sharedFlags());
        viewer.connection.send(new ClientboundSetEntityDataPacket(
            entity.getId(), List.of(bridge.aerogel$sharedFlagsValue(flags))));
    }

    private static Packet<?> transformMetadata(
        ServerPlayer viewer, ViewState state, ClientboundSetEntityDataPacket packet
    ) {
        Entity entity = viewer.level().getEntity(packet.id());
        if (entity == null) return packet;
        FlagOverride override = state.flags.get(entity.getUUID());
        if (override == null) return packet;

        boolean containsFlags = packet.packedItems().stream().anyMatch(value -> value.id() == 0);
        if (!containsFlags) return packet;
        EntityViewBridge bridge = bridge(entity);
        List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> values =
            new ArrayList<>(packet.packedItems().size());
        for (var value : packet.packedItems()) {
            values.add(value.id() == 0
                ? bridge.aerogel$sharedFlagsValue(override.apply(bridge.aerogel$sharedFlags()))
                : value);
        }
        return new ClientboundSetEntityDataPacket(packet.id(), values);
    }

    private static Packet<?> transformEquipment(
        ServerPlayer viewer, ViewState state, ClientboundSetEquipmentPacket packet
    ) {
        Entity entity = viewer.level().getEntity(packet.getEntity());
        if (entity == null) return packet;
        EnumMap<EquipmentSlot, ItemStack> overrides = state.equipment.get(entity.getUUID());
        if (overrides == null || overrides.isEmpty()) return packet;

        List<Pair<EquipmentSlot, ItemStack>> values = new ArrayList<>(packet.getSlots().size());
        for (Pair<EquipmentSlot, ItemStack> pair : packet.getSlots()) {
            ItemStack replacement = overrides.get(pair.getFirst());
            values.add(Pair.of(pair.getFirst(), replacement == null ? pair.getSecond() : replacement));
        }
        return new ClientboundSetEquipmentPacket(packet.getEntity(), values);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Packet<?> transformBundle(ServerPlayer viewer, ClientboundBundlePacket bundle) {
        List<Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> packets =
            new ArrayList<>();
        for (Object nested : bundle.subPackets()) {
            packets.add((Packet) transform(viewer, (Packet<?>) nested));
        }
        return new ClientboundBundlePacket(packets);
    }

    private static EntityViewBridge bridge(Entity entity) {
        if (!(entity instanceof EntityViewBridge bridge)) {
            throw new IllegalStateException("Aerogel entity view bridge is unavailable");
        }
        return bridge;
    }

    private static PlayerTeam glowTeam(
        ServerPlayer viewer, Entity entity, TeamColor color
    ) {
        PlayerTeam team = new PlayerTeam(
            viewer.level().getServer().getScoreboard(), glowTeamName(entity));
        PlayerTeam actual = entity.getTeam();
        if (actual != null) {
            team.setDisplayName(actual.getDisplayName());
            team.setPlayerPrefix(actual.getPlayerPrefix());
            team.setPlayerSuffix(actual.getPlayerSuffix());
            team.setNameTagVisibility(actual.getNameTagVisibility());
            team.setDeathMessageVisibility(actual.getDeathMessageVisibility());
            team.setCollisionRule(actual.getCollisionRule());
            team.setAllowFriendlyFire(actual.isAllowFriendlyFire());
            team.setSeeFriendlyInvisibles(actual.canSeeFriendlyInvisibles());
        }
        team.setColor(java.util.Optional.of(color));
        return team;
    }

    private static void restoreGlowTeam(
        ServerPlayer viewer, Entity entity, TeamColor color
    ) {
        viewer.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(
            glowTeam(viewer, entity, color)));
        PlayerTeam actual = entity.getTeam();
        if (actual != null) {
            viewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                actual, entity.getScoreboardName(), ClientboundSetPlayerTeamPacket.Action.ADD));
        }
    }

    private static String glowTeamName(Entity entity) {
        return "agv" + Integer.toUnsignedString(entity.getId(), 36);
    }

    private static TrackedEntityBridge tracked(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)
            || !(level.getChunkSource().chunkMap instanceof ChunkMapTrackingBridge chunkMap)) {
            return null;
        }
        Object tracked = chunkMap.aerogel$trackedEntity(entity.getId());
        return tracked instanceof TrackedEntityBridge bridge ? bridge : null;
    }

    private static void requireSameLevel(ServerPlayer viewer, Entity entity) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(entity, "entity");
        if (viewer.level() != entity.level()) {
            throw new IllegalArgumentException("Viewer and entity must be in the same level");
        }
    }

    private static ViewState state(ServerPlayer viewer) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(viewer, ignored -> new ViewState());
        }
    }

    private record FlagOverride(byte mask, byte values) {
        private static final FlagOverride NONE = new FlagOverride((byte) 0, (byte) 0);

        private byte apply(byte actual) {
            return (byte) ((actual & ~mask) | (values & mask));
        }
    }

    private static final class ViewState {
        private final Set<UUID> hidden = new HashSet<>();
        private final Map<UUID, FlagOverride> flags = new HashMap<>();
        private final Map<UUID, TeamColor> glowColors = new HashMap<>();
        private final Map<UUID, EnumMap<EquipmentSlot, ItemStack>> equipment = new HashMap<>();
        private final Set<BlockPos> blocks = new HashSet<>();
        private final Set<BlockPos> breaks = new HashSet<>();
    }
}
