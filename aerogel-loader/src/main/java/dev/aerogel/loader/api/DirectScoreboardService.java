package dev.aerogel.loader.api;

import dev.aerogel.api.scoreboard.DisplaySlot;
import dev.aerogel.api.scoreboard.Objective;
import dev.aerogel.api.scoreboard.ObjectiveRenderType;
import dev.aerogel.api.scoreboard.Scoreboard;
import dev.aerogel.api.scoreboard.ScoreboardService;
import dev.aerogel.api.scoreboard.Team;
import dev.aerogel.api.scoreboard.PlayerScoreboard;
import dev.aerogel.loader.internal.PlayerScoreboardView;
import dev.aerogel.loader.internal.ViewerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import java.util.Objects;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

final class DirectScoreboardService implements ScoreboardService {
    private final PluginApiScope scope;
    DirectScoreboardService(PluginApiScope scope) { this.scope = scope; }

    @Override public Scoreboard main() {
        return new Board(scope.vanilla().getScoreboard());
    }

    @Override public PlayerScoreboard create(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        if (!scope.vanilla().isSameThread()) {
            throw new IllegalStateException("Use the server scheduler for scoreboard access");
        }
        if (player.level().getServer() != scope.vanilla() || player.isRemoved()) {
            throw new IllegalArgumentException("Player must be connected to this server");
        }
        return scope.own(new PersonalBoard(new Display(player)));
    }

    private static void checkBoard(net.minecraft.world.scores.Scoreboard scoreboard) {
        if (scoreboard instanceof ViewerScoreboard personal) personal.checkAccess();
    }

    private class Board implements Scoreboard {
        private final net.minecraft.world.scores.Scoreboard scoreboard;
        private Board(net.minecraft.world.scores.Scoreboard scoreboard) { this.scoreboard = scoreboard; }
        @Override public net.minecraft.world.scores.Scoreboard vanilla() {
            checkBoard(scoreboard);
            return scoreboard;
        }

        @Override public Objective objective(String name, Component displayName) {
            checkBoard(scoreboard);
            net.minecraft.world.scores.Objective found = scoreboard.getObjective(name);
            if (found != null) return new ObjectiveImpl(scoreboard, found, false);
            net.minecraft.world.scores.Objective created = scoreboard.addObjective(
                name, ObjectiveCriteria.DUMMY, displayName,
                ObjectiveCriteria.RenderType.INTEGER, false, null);
            ObjectiveImpl wrapper = new ObjectiveImpl(scoreboard, created, true);
            return scoreboard instanceof ViewerScoreboard ? wrapper : scope.own(wrapper);
        }

        @Override public Optional<Objective> findObjective(String name) {
            checkBoard(scoreboard);
            net.minecraft.world.scores.Objective found = scoreboard.getObjective(name);
            return found == null ? Optional.empty() : Optional.of(new ObjectiveImpl(scoreboard, found, false));
        }
        @Override public Collection<Objective> objectives() {
            checkBoard(scoreboard);
            Collection<net.minecraft.world.scores.Objective> values = scoreboard.getObjectives();
            return values.stream().map(value -> (Objective) new ObjectiveImpl(scoreboard, value, false)).toList();
        }
        @Override public Team team(String name) {
            checkBoard(scoreboard);
            PlayerTeam found = scoreboard.getPlayerTeam(name);
            if (found != null) return new TeamImpl(scoreboard, found, false);
            TeamImpl wrapper = new TeamImpl(scoreboard, scoreboard.addPlayerTeam(name), true);
            return scoreboard instanceof ViewerScoreboard ? wrapper : scope.own(wrapper);
        }
        @Override public Optional<Team> findTeam(String name) {
            checkBoard(scoreboard);
            PlayerTeam found = scoreboard.getPlayerTeam(name);
            return found == null ? Optional.empty() : Optional.of(new TeamImpl(scoreboard, found, false));
        }
        @Override public Collection<Team> teams() {
            checkBoard(scoreboard);
            Collection<PlayerTeam> values = scoreboard.getPlayerTeams();
            return values.stream().map(value -> (Team) new TeamImpl(scoreboard, value, false)).toList();
        }
    }

    private final class ObjectiveImpl implements Objective {
        private final net.minecraft.world.scores.Scoreboard scoreboard;
        private final net.minecraft.world.scores.Objective objective;
        private final boolean owned;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private ObjectiveImpl(net.minecraft.world.scores.Scoreboard scoreboard,
                              net.minecraft.world.scores.Objective objective, boolean owned) {
            this.scoreboard = scoreboard; this.objective = objective; this.owned = owned;
        }
        @Override public String name() { return objective.getName(); }
        @Override public net.minecraft.world.scores.Objective vanilla() {
            return objective;
        }
        @Override public Objective displayName(Component value) {
            check(); objective.setDisplayName(value); return this;
        }
        @Override public Objective renderType(ObjectiveRenderType value) {
            check(); objective.setRenderType(ObjectiveCriteria.RenderType.valueOf(value.name())); return this;
        }
        @Override public Objective display(DisplaySlot slot) {
            check(); scoreboard.setDisplayObjective(
                net.minecraft.world.scores.DisplaySlot.valueOf(slot.name()), objective); return this;
        }
        @Override public Objective score(String holder, int value) {
            check(); access(holder).set(value); return this;
        }
        @Override public int score(String holder) { check(); return access(holder).get(); }
        @Override public Objective reset(String holder) {
            check(); scoreboard.resetSinglePlayerScore(holder(holder), objective); return this;
        }
        private ScoreAccess access(String holder) { return scoreboard.getOrCreatePlayerScore(holder(holder), objective); }
        private ScoreHolder holder(String value) { return ScoreHolder.forNameOnly(value); }
        @Override public boolean active() {
            return active.get() && (!(scoreboard instanceof ViewerScoreboard personal) || personal.open());
        }
        @Override public void close() {
            if (!active()) return;
            checkBoard(scoreboard);
            if (active.compareAndSet(true, false) && owned) scoreboard.removeObjective(objective);
        }
        private void check() { checkBoard(scoreboard); if (!active()) throw new IllegalStateException("Objective is closed: " + name()); }
    }

    private final class TeamImpl implements Team {
        private final net.minecraft.world.scores.Scoreboard scoreboard;
        private final PlayerTeam team;
        private final boolean owned;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private TeamImpl(net.minecraft.world.scores.Scoreboard scoreboard, PlayerTeam team, boolean owned) {
            this.scoreboard = scoreboard; this.team = team; this.owned = owned;
        }
        @Override public String name() { return team.getName(); }
        @Override public PlayerTeam vanilla() { return team; }
        @Override public Team displayName(Component value) { check(); team.setDisplayName(value); return this; }
        @Override public Team prefix(Component value) { check(); team.setPlayerPrefix(value); return this; }
        @Override public Team suffix(Component value) { check(); team.setPlayerSuffix(value); return this; }
        @Override public Team friendlyFire(boolean value) { check(); team.setAllowFriendlyFire(value); return this; }
        @Override public Team seeFriendlyInvisible(boolean value) { check(); team.setSeeFriendlyInvisibles(value); return this; }
        @Override public Team add(String holder) { check(); scoreboard.addPlayerToTeam(holder, team); return this; }
        @Override public Team remove(String holder) { check(); scoreboard.removePlayerFromTeam(holder, team); return this; }
        @Override public Collection<String> members() {
            check(); return ListCopy.copy(team.getPlayers());
        }
        @Override public boolean active() {
            return active.get() && (!(scoreboard instanceof ViewerScoreboard personal) || personal.open());
        }
        @Override public void close() {
            if (!active()) return;
            checkBoard(scoreboard);
            if (active.compareAndSet(true, false) && owned) scoreboard.removePlayerTeam(team);
        }
        private void check() { checkBoard(scoreboard); if (!active()) throw new IllegalStateException("Team is closed: " + name()); }
    }

    private final class PersonalBoard extends Board implements PlayerScoreboard {
        private final Display display;
        private PersonalBoard(Display display) { super(display.board); this.display = display; }
        @Override public ServerPlayer player() { display.check(); return display.player; }
        @Override public PlayerScoreboard show() { display.show(); return this; }
        @Override public PlayerScoreboard hide() { display.check(); display.hide(true); return this; }
        @Override public boolean visible() { return display.visible(); }
        @Override public boolean active() { return display.active.get(); }
        @Override public void close() { display.close(); }
    }

    private final class Display implements PlayerScoreboardView.View {
        private volatile ServerPlayer player;
        private final net.minecraft.server.MinecraftServer server = scope.vanilla();
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final ViewerScoreboard board;

        private Display(ServerPlayer player) {
            this.player = player;
            board = new ViewerScoreboard(packet -> {
                if (visible()) PlayerScoreboardView.send(this.player, packet);
            }, this::check);
            PlayerScoreboardView.register(player, this);
        }
        private void check() {
            if (!server.isSameThread()) throw new IllegalStateException("Use the server scheduler for scoreboard access");
            if (!active.get()) throw new IllegalStateException("Player scoreboard is closed");
        }
        private boolean visible() {
            ServerPlayer current = player;
            return current != null && PlayerScoreboardView.visible(current) == this;
        }
        private void show() {
            check();
            if (visible()) return;
            var previous = PlayerScoreboardView.visible(player);
            if (previous instanceof DirectScoreboardService.Display display) display.hide(false);
            else ViewerScoreboard.clearDisplay(server.getScoreboard(), packet -> PlayerScoreboardView.send(player, packet));
            PlayerScoreboardView.visible(player, this);
            ViewerScoreboard.snapshot(board, packet -> PlayerScoreboardView.send(player, packet));
        }
        private void hide(boolean restore) {
            if (!visible()) return;
            ViewerScoreboard.clearDisplay(board, packet -> PlayerScoreboardView.send(player, packet));
            PlayerScoreboardView.visible(player, null);
            if (restore) ViewerScoreboard.snapshot(server.getScoreboard(), packet -> PlayerScoreboardView.send(player, packet));
        }
        private void close() {
            if (!active.compareAndSet(true, false)) return;
            board.closeAccess();
            Runnable cleanup = () -> {
                if (player == null) return;
                hide(true);
                PlayerScoreboardView.unregister(player, this);
                player = null;
            };
            if (server.isSameThread()) cleanup.run(); else server.execute(cleanup);
        }
        @Override public void disconnected() { active.set(false); board.closeAccess(); player = null; }
        @Override public void respawned(ServerPlayer replacement) { player = replacement; }
    }

    private static final class ListCopy {
        private static <T> Collection<T> copy(Collection<T> values) { return java.util.List.copyOf(values); }
    }
}
