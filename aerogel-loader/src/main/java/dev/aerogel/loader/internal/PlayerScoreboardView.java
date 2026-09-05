package dev.aerogel.loader.internal;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.ConcurrentHashMap;

/** Connection filtering only; board mutations and lifecycle are server-owned. */
public final class PlayerScoreboardView {
    public interface View {
        void disconnected();
        void respawned(ServerPlayer replacement);
    }
    private static final ConcurrentHashMap<ServerPlayer, View> VISIBLE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ServerPlayer, java.util.Set<View>> OWNED = new ConcurrentHashMap<>();
    private static final ThreadLocal<ServerPlayer> SENDING = new ThreadLocal<>();
    private PlayerScoreboardView() { }

    public static void register(ServerPlayer player, View view) {
        OWNED.computeIfAbsent(player, ignored -> ConcurrentHashMap.newKeySet()).add(view);
    }
    public static void unregister(ServerPlayer player, View view) {
        var views = OWNED.get(player);
        if (views != null) {
            views.remove(view);
            if (views.isEmpty()) OWNED.remove(player, views);
        }
        VISIBLE.remove(player, view);
    }
    public static View visible(ServerPlayer player) { return VISIBLE.get(player); }
    public static void visible(ServerPlayer player, View view) {
        if (view == null) VISIBLE.remove(player); else VISIBLE.put(player, view);
    }
    public static boolean suppress(ServerPlayer player, Packet<?> packet) {
        if (SENDING.get() == player || !VISIBLE.containsKey(player)) return false;
        return packet instanceof ClientboundSetObjectivePacket
            || packet instanceof ClientboundSetDisplayObjectivePacket
            || packet instanceof ClientboundSetScorePacket
            || packet instanceof ClientboundResetScorePacket
            || packet instanceof ClientboundSetPlayerTeamPacket;
    }
    public static Packet<?> filterBundle(ServerPlayer player, Packet<?> packet) {
        if (!(packet instanceof ClientboundBundlePacket bundle)
            || SENDING.get() == player || !VISIBLE.containsKey(player)) return packet;
        var retained = new java.util.ArrayList<Packet<? super ClientGamePacketListener>>();
        boolean changed = false;
        for (Packet<? super ClientGamePacketListener> child : bundle.subPackets()) {
            if (suppress(player, child)) { changed = true; continue; }
            Packet<?> filtered = filterBundle(player, child);
            changed |= filtered != child;
            @SuppressWarnings("unchecked")
            Packet<? super ClientGamePacketListener> typed = (Packet<? super ClientGamePacketListener>) filtered;
            retained.add(typed);
        }
        return changed ? new ClientboundBundlePacket(retained) : packet;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void send(ServerPlayer player, Packet<?> packet) {
        ServerPlayer previous = SENDING.get();
        SENDING.set(player);
        try { player.connection.send((Packet) packet); }
        finally {
            if (previous == null) SENDING.remove(); else SENDING.set(previous);
        }
    }
    public static void disconnected(ServerPlayer player) {
        VISIBLE.remove(player);
        var views = OWNED.remove(player);
        if (views != null) for (View view : views) view.disconnected();
    }
    public static void respawned(ServerPlayer previous, ServerPlayer replacement) {
        if (previous == replacement) return;
        View visible = VISIBLE.remove(previous);
        var views = OWNED.remove(previous);
        if (views != null) {
            OWNED.put(replacement, views);
            for (View view : views) view.respawned(replacement);
        }
        if (visible != null) VISIBLE.put(replacement, visible);
    }
}
