package dev.aerogel.loader.internal;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Keeps a player's client-rendered overhead name in sync with Aerogel's display name. */
public final class PlayerNameTagService {
    private static final Map<ServerPlayer, Set<UUID>> INSTALLED = new java.util.WeakHashMap<>();
    private static final Map<UUID, Integer> ALIASES = new HashMap<>();
    private static int nextAlias;

    private PlayerNameTagService() {
    }

    public static void refresh(ServerPlayer target) {
        ServerPlayerDisplayNameBridge bridge = bridge(target);
        Component displayName = bridge.aerogel$displayNameOverride();
        boolean hidden = bridge.aerogel$nameTagHidden();
        for (ServerPlayer viewer : trackingPlayers(target)) {
            if (displayName == null && !hidden) {
                restore(viewer, target);
            } else if (installed(viewer, target)) {
                modifyTeam(viewer, target, visibleName(displayName), hidden);
            } else {
                install(viewer, target, visibleName(displayName), hidden);
            }
        }
    }

    public static void paired(ServerPlayer viewer, ServerPlayer target) {
        if (viewer == target) return;
        ServerPlayerDisplayNameBridge bridge = bridge(target);
        Component displayName = bridge.aerogel$displayNameOverride();
        boolean hidden = bridge.aerogel$nameTagHidden();
        if (displayName != null || hidden) {
            install(viewer, target, visibleName(displayName), hidden);
        }
    }

    public static void playerRemoved(ServerPlayer player) {
        INSTALLED.remove(player);
        for (Map.Entry<ServerPlayer, Set<UUID>> entry : List.copyOf(INSTALLED.entrySet())) {
            if (entry.getValue().remove(player.getUUID())) {
                entry.getKey().connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(
                    syntheticTeam(player, Component.empty(), false)));
            }
        }
    }

    private static void install(
        ServerPlayer viewer, ServerPlayer target, Component displayName, boolean hidden
    ) {
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        packets.add(new ClientboundRemoveEntitiesPacket(target.getId()));
        packets.add(new ClientboundPlayerInfoRemovePacket(List.of(target.getUUID())));
        packets.add(fakePlayerInfo(target));

        ServerEntity pairing = new ServerEntity(
            target.level(), target, 1, true, NoopServerEntitySynchronizer.INSTANCE);
        pairing.sendPairingData(viewer, packets::add);
        target.getActiveEffects().forEach(effect ->
            packets.add(new ClientboundUpdateMobEffectPacket(target.getId(), effect, false)));

        boolean knownTeam = installed(viewer, target);
        PlayerTeam team = syntheticTeam(target, displayName, hidden);
        packets.add(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, !knownTeam));
        packets.add(new ClientboundPlayerInfoRemovePacket(List.of(target.getUUID())));
        packets.add(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(target)));
        viewer.connection.send(new ClientboundBundlePacket(packets));
        INSTALLED.computeIfAbsent(viewer, ignored -> new HashSet<>()).add(target.getUUID());
    }

    private static void restore(ServerPlayer viewer, ServerPlayer target) {
        Set<UUID> targets = INSTALLED.get(viewer);
        if (targets == null || !targets.remove(target.getUUID())) return;

        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        packets.add(new ClientboundRemoveEntitiesPacket(target.getId()));
        packets.add(new ClientboundPlayerInfoRemovePacket(List.of(target.getUUID())));
        packets.add(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(target)));
        ServerEntity pairing = new ServerEntity(
            target.level(), target, 1, true, NoopServerEntitySynchronizer.INSTANCE);
        pairing.sendPairingData(viewer, packets::add);
        target.getActiveEffects().forEach(effect ->
            packets.add(new ClientboundUpdateMobEffectPacket(target.getId(), effect, false)));
        packets.add(ClientboundSetPlayerTeamPacket.createRemovePacket(
            syntheticTeam(target, Component.empty(), false)));
        viewer.connection.send(new ClientboundBundlePacket(packets));
    }

    private static void modifyTeam(
        ServerPlayer viewer, ServerPlayer target, Component displayName, boolean hidden
    ) {
        viewer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(
            syntheticTeam(target, displayName, hidden), false));
    }

    private static ClientboundPlayerInfoUpdatePacket fakePlayerInfo(ServerPlayer target) {
        ServerPlayerDisplayNameBridge bridge = bridge(target);
        GameProfile real = target.getGameProfile();
        GameProfile fake = new GameProfile(real.id(), alias(target), real.properties());
        bridge.aerogel$packetProfileOverride(fake);
        try {
            return new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, target);
        } finally {
            bridge.aerogel$packetProfileOverride(null);
        }
    }

    private static PlayerTeam syntheticTeam(
        ServerPlayer target, Component displayName, boolean hidden
    ) {
        Scoreboard scoreboard = new Scoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam(teamName(target));
        team.setPlayerPrefix(displayName);
        team.setPlayerSuffix(Component.empty());
        team.setNameTagVisibility(hidden ? Team.Visibility.NEVER : Team.Visibility.ALWAYS);
        PlayerTeam original = target.getTeam();
        if (original != null) {
            team.setAllowFriendlyFire(original.isAllowFriendlyFire());
            team.setSeeFriendlyInvisibles(original.canSeeFriendlyInvisibles());
            team.setCollisionRule(original.getCollisionRule());
            team.setDeathMessageVisibility(original.getDeathMessageVisibility());
        }
        scoreboard.addPlayerToTeam(alias(target), team);
        return team;
    }

    private static Component visibleName(Component displayName) {
        return displayName == null ? Component.empty() : displayName;
    }

    private static List<ServerPlayer> trackingPlayers(ServerPlayer target) {
        Object entityMap = dev.aerogel.loader.event.EventHooks.field(
            target.level().getChunkSource().chunkMap, "entityMap");
        Object tracked = dev.aerogel.loader.event.EventHooks.intMapGet(entityMap, target.getId());
        if (tracked == null) return List.of();
        Set<?> seenBy = (Set<?>) dev.aerogel.loader.event.EventHooks.field(tracked, "seenBy");
        return target.level().getServer().getPlayerList().getPlayers().stream()
            .filter(viewer -> viewer != target && seenBy.contains(viewer.connection))
            .toList();
    }

    private static boolean installed(ServerPlayer viewer, ServerPlayer target) {
        Set<UUID> targets = INSTALLED.get(viewer);
        return targets != null && targets.contains(target.getUUID());
    }

    private static String teamName(ServerPlayer target) {
        return "agnt" + target.getUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String alias(ServerPlayer target) {
        int value = ALIASES.computeIfAbsent(target.getUUID(), ignored -> nextAlias++);
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder alias = new StringBuilder(16);
        for (int shift = 28; shift >= 0; shift -= 4) {
            alias.append('\u00a7').append(hex[(value >>> shift) & 0xf]);
        }
        return alias.toString();
    }

    private static ServerPlayerDisplayNameBridge bridge(ServerPlayer player) {
        return (ServerPlayerDisplayNameBridge) player;
    }
}
