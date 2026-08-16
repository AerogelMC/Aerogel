package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import net.minecraft.world.scores.PlayerTeam;

public class ClientboundSetPlayerTeamPacket implements Packet<ClientGamePacketListener> {
    public static ClientboundSetPlayerTeamPacket createAddOrModifyPacket(PlayerTeam team, boolean add) {
        return null;
    }

    public static ClientboundSetPlayerTeamPacket createRemovePacket(PlayerTeam team) {
        return null;
    }
    public static ClientboundSetPlayerTeamPacket createPlayerPacket(
        PlayerTeam team, String player, Action action
    ) { return null; }

    public enum Action { ADD, REMOVE }
}
