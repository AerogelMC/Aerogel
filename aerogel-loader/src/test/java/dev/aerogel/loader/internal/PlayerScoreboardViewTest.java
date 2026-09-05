package dev.aerogel.loader.internal;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

final class PlayerScoreboardViewTest {
    @Test
    void scoreboardFilteringIsPerViewerAndLeavesOrdinaryPacketsAlone() {
        ServerPlayer first = new ServerPlayer();
        ServerPlayer second = new ServerPlayer();
        View view = new View();
        var score = new ClientboundSetScorePacket("coins", "score", 1, Optional.empty(), Optional.empty());
        Packet<ClientGamePacketListener> ordinary = new Packet<>() { };
        PlayerScoreboardView.register(first, view);
        PlayerScoreboardView.visible(first, view);
        try {
            assertTrue(PlayerScoreboardView.suppress(first, score));
            assertFalse(PlayerScoreboardView.suppress(second, score));
            assertFalse(PlayerScoreboardView.suppress(first, ordinary));
            first.connection = new ServerGamePacketListenerImpl() {
                @Override public void send(Packet<? super ClientGamePacketListener> packet) {
                    assertFalse(PlayerScoreboardView.suppress(first, packet));
                    throw new IllegalStateException("transport failure");
                }
            };
            assertThrows(IllegalStateException.class, () -> PlayerScoreboardView.send(first, score));
            assertTrue(PlayerScoreboardView.suppress(first, score), "Send origin must clear even on failure");
        } finally {
            PlayerScoreboardView.disconnected(first);
        }
        assertFalse(PlayerScoreboardView.suppress(first, score));
    }

    @Test
    void removingHiddenBoardDoesNotRemoveAnotherPluginsVisibleBoard() {
        ServerPlayer player = new ServerPlayer();
        View hidden = new View();
        View visible = new View();
        PlayerScoreboardView.register(player, hidden);
        PlayerScoreboardView.register(player, visible);
        PlayerScoreboardView.visible(player, visible);
        try {
            PlayerScoreboardView.unregister(player, hidden);
            assertSame(visible, PlayerScoreboardView.visible(player));
        } finally {
            PlayerScoreboardView.disconnected(player);
        }
        assertEquals(0, hidden.disconnects);
        assertEquals(1, visible.disconnects);
    }

    @Test
    void respawnTransfersVisibleAndHiddenBoardsAndDisconnectClearsBoth() {
        ServerPlayer previous = new ServerPlayer();
        ServerPlayer replacement = new ServerPlayer();
        View first = new View();
        View second = new View();
        PlayerScoreboardView.register(previous, first);
        PlayerScoreboardView.register(previous, second);
        PlayerScoreboardView.visible(previous, first);
        try {
            PlayerScoreboardView.respawned(previous, replacement);
            assertNull(PlayerScoreboardView.visible(previous));
            assertSame(first, PlayerScoreboardView.visible(replacement));
            assertSame(replacement, first.replacement);
            assertSame(replacement, second.replacement);
            PlayerScoreboardView.disconnected(previous);
            assertEquals(0, first.disconnects);
        } finally {
            PlayerScoreboardView.disconnected(replacement);
        }
        assertNull(PlayerScoreboardView.visible(replacement));
        assertEquals(1, first.disconnects);
        assertEquals(1, second.disconnects);
        PlayerScoreboardView.disconnected(replacement);
        assertEquals(1, first.disconnects);
    }

    private static final class View implements PlayerScoreboardView.View {
        private int disconnects;
        private ServerPlayer replacement;
        @Override public void disconnected() { disconnects++; }
        @Override public void respawned(ServerPlayer player) { replacement = player; }
    }
}
