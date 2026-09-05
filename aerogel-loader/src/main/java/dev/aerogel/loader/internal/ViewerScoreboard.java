package dev.aerogel.loader.internal;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.scores.*;

import java.util.HashSet;
import java.util.Optional;
import java.util.function.Consumer;

/** Vanilla data model with delta publication to a single display owner. */
public final class ViewerScoreboard extends Scoreboard {
    private final Consumer<Packet<?>> output;
    private final Runnable guard;
    private volatile boolean open = true;

    public ViewerScoreboard(Consumer<Packet<?>> output) { this(output, () -> { }); }
    public ViewerScoreboard(Consumer<Packet<?>> output, Runnable guard) {
        this.output = output;
        this.guard = guard;
    }
    public void checkAccess() { guard.run(); }
    public boolean open() { return open; }
    public void closeAccess() { open = false; }

    @Override public void onObjectiveAdded(Objective objective) {
        output.accept(new ClientboundSetObjectivePacket(objective, 0));
    }
    @Override public void onObjectiveChanged(Objective objective) {
        output.accept(new ClientboundSetObjectivePacket(objective, 2));
    }
    @Override public void onObjectiveRemoved(Objective objective) {
        output.accept(new ClientboundSetObjectivePacket(objective, 1));
    }
    @Override public void setDisplayObjective(DisplaySlot slot, Objective objective) {
        super.setDisplayObjective(slot, objective);
        output.accept(new ClientboundSetDisplayObjectivePacket(slot, objective));
    }
    @Override protected void onScoreChanged(ScoreHolder holder, Objective objective, Score score) {
        output.accept(new ClientboundSetScorePacket(holder.getScoreboardName(), objective.getName(),
            score.value(), Optional.ofNullable(score.display()), Optional.ofNullable(score.numberFormat())));
    }
    @Override public void onPlayerRemoved(ScoreHolder holder) {
        output.accept(new ClientboundResetScorePacket(holder.getScoreboardName(), null));
    }
    @Override public void onPlayerScoreRemoved(ScoreHolder holder, Objective objective) {
        output.accept(new ClientboundResetScorePacket(holder.getScoreboardName(), objective.getName()));
    }
    @Override public void onTeamAdded(PlayerTeam team) {
        output.accept(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
    }
    @Override public void onTeamChanged(PlayerTeam team) {
        output.accept(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, false));
    }
    @Override public void onTeamRemoved(PlayerTeam team) {
        output.accept(ClientboundSetPlayerTeamPacket.createRemovePacket(team));
    }
    @Override public boolean addPlayerToTeam(String name, PlayerTeam team) {
        if (!super.addPlayerToTeam(name, team)) return false;
        output.accept(ClientboundSetPlayerTeamPacket.createPlayerPacket(
            team, name, ClientboundSetPlayerTeamPacket.Action.ADD));
        return true;
    }
    @Override public void removePlayerFromTeam(String name, PlayerTeam team) {
        super.removePlayerFromTeam(name, team);
        output.accept(ClientboundSetPlayerTeamPacket.createPlayerPacket(
            team, name, ClientboundSetPlayerTeamPacket.Action.REMOVE));
    }

    /** Definitions precede scores and display slots. No periodic full resend. */
    public static void snapshot(Scoreboard board, Consumer<Packet<?>> output) {
        var objectives = new HashSet<Objective>();
        if (board instanceof ViewerScoreboard) objectives.addAll(board.getObjectives());
        for (DisplaySlot slot : DisplaySlot.values()) {
            Objective objective = board.getDisplayObjective(slot);
            if (objective != null) objectives.add(objective);
        }
        for (Objective objective : objectives) {
            output.accept(new ClientboundSetObjectivePacket(objective, 0));
            for (PlayerScoreEntry score : board.listPlayerScores(objective)) {
                output.accept(new ClientboundSetScorePacket(score.owner(), objective.getName(),
                    score.value(), Optional.ofNullable(score.display()),
                    Optional.ofNullable(score.numberFormatOverride())));
            }
        }
        for (PlayerTeam team : board.getPlayerTeams()) {
            output.accept(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
        }
        for (DisplaySlot slot : DisplaySlot.values()) {
            output.accept(new ClientboundSetDisplayObjectivePacket(slot, board.getDisplayObjective(slot)));
        }
    }

    public static void clearDisplay(Scoreboard board, Consumer<Packet<?>> output) {
        for (DisplaySlot slot : DisplaySlot.values()) {
            output.accept(new ClientboundSetDisplayObjectivePacket(slot, null));
        }
        var objectives = new HashSet<Objective>();
        if (board instanceof ViewerScoreboard) objectives.addAll(board.getObjectives());
        for (DisplaySlot slot : DisplaySlot.values()) {
            Objective objective = board.getDisplayObjective(slot);
            if (objective != null) objectives.add(objective);
        }
        for (Objective objective : objectives) output.accept(new ClientboundSetObjectivePacket(objective, 1));
        for (PlayerTeam team : board.getPlayerTeams()) output.accept(ClientboundSetPlayerTeamPacket.createRemovePacket(team));
    }
}
