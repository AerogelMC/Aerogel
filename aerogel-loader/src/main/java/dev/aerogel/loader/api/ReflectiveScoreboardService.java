package dev.aerogel.loader.api;

import dev.aerogel.api.scoreboard.DisplaySlot;
import dev.aerogel.api.scoreboard.Objective;
import dev.aerogel.api.scoreboard.ObjectiveRenderType;
import dev.aerogel.api.scoreboard.Scoreboard;
import dev.aerogel.api.scoreboard.ScoreboardService;
import dev.aerogel.api.scoreboard.Team;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

final class ReflectiveScoreboardService implements ScoreboardService {
    private final PluginApiScope scope;
    ReflectiveScoreboardService(PluginApiScope scope) { this.scope = scope; }

    @Override public Scoreboard main() {
        return new Board(Reflect.invoke(scope.serverHandle(), "getScoreboard"));
    }

    private final class Board implements Scoreboard {
        private final Object scoreboard;
        private Board(Object scoreboard) { this.scoreboard = scoreboard; }
        @Override public net.minecraft.world.scores.Scoreboard vanilla() {
            return (net.minecraft.world.scores.Scoreboard) scoreboard;
        }

        @Override public Objective objective(String name, Component displayName) {
            Object found = Reflect.invoke(scoreboard, "getObjective", name);
            if (found != null) return new ObjectiveImpl(scoreboard, found, false);
            ClassLoader loader = scope.loader();
            Object dummy = Reflect.staticField(Reflect.type(loader,
                "net.minecraft.world.scores.criteria.ObjectiveCriteria"), "DUMMY");
            Object integer = Reflect.staticField(Reflect.type(loader,
                "net.minecraft.world.scores.criteria.ObjectiveCriteria$RenderType"), "INTEGER");
            Object created = Reflect.invoke(scoreboard, "addObjective", name, dummy,
                displayName, integer, false, null);
            return scope.own(new ObjectiveImpl(scoreboard, created, true));
        }

        @Override public Optional<Objective> findObjective(String name) {
            Object found = Reflect.invoke(scoreboard, "getObjective", name);
            return found == null ? Optional.empty() : Optional.of(new ObjectiveImpl(scoreboard, found, false));
        }
        @Override public Collection<Objective> objectives() {
            Collection<?> values = (Collection<?>) Reflect.invoke(scoreboard, "getObjectives");
            return values.stream().map(value -> (Objective) new ObjectiveImpl(scoreboard, value, false)).toList();
        }
        @Override public Team team(String name) {
            Object found = Reflect.invoke(scoreboard, "getPlayerTeam", name);
            if (found != null) return new TeamImpl(scoreboard, found, false);
            return scope.own(new TeamImpl(scoreboard, Reflect.invoke(scoreboard, "addPlayerTeam", name), true));
        }
        @Override public Optional<Team> findTeam(String name) {
            Object found = Reflect.invoke(scoreboard, "getPlayerTeam", name);
            return found == null ? Optional.empty() : Optional.of(new TeamImpl(scoreboard, found, false));
        }
        @Override public Collection<Team> teams() {
            Collection<?> values = (Collection<?>) Reflect.invoke(scoreboard, "getPlayerTeams");
            return values.stream().map(value -> (Team) new TeamImpl(scoreboard, value, false)).toList();
        }
    }

    private final class ObjectiveImpl implements Objective {
        private final Object scoreboard;
        private final Object objective;
        private final boolean owned;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private ObjectiveImpl(Object scoreboard, Object objective, boolean owned) {
            this.scoreboard = scoreboard; this.objective = objective; this.owned = owned;
        }
        @Override public String name() { return String.valueOf(Reflect.invoke(objective, "getName")); }
        @Override public net.minecraft.world.scores.Objective vanilla() {
            return (net.minecraft.world.scores.Objective) objective;
        }
        @Override public Objective displayName(Component value) {
            check(); Reflect.invoke(objective, "setDisplayName", value); return this;
        }
        @Override public Objective renderType(ObjectiveRenderType value) {
            check(); Object type = Reflect.staticField(Reflect.type(scope.loader(),
                "net.minecraft.world.scores.criteria.ObjectiveCriteria$RenderType"), value.name());
            Reflect.invoke(objective, "setRenderType", type); return this;
        }
        @Override public Objective display(DisplaySlot slot) {
            check(); Object target = Reflect.staticField(Reflect.type(scope.loader(),
                "net.minecraft.world.scores.DisplaySlot"), slot.name());
            Reflect.invoke(scoreboard, "setDisplayObjective", target, objective); return this;
        }
        @Override public Objective score(String holder, int value) {
            check(); Reflect.invoke(access(holder), "set", value); return this;
        }
        @Override public int score(String holder) { check(); return ((Number) Reflect.invoke(access(holder), "get")).intValue(); }
        @Override public Objective reset(String holder) {
            check(); Reflect.invoke(scoreboard, "resetSinglePlayerScore", holder(holder), objective); return this;
        }
        private Object access(String holder) { return Reflect.invoke(scoreboard, "getOrCreatePlayerScore", holder(holder), objective); }
        private Object holder(String value) { return Reflect.invokeStatic(Reflect.type(scope.loader(),
            "net.minecraft.world.scores.ScoreHolder"), "forNameOnly", value); }
        @Override public boolean active() { return active.get(); }
        @Override public void close() {
            if (active.compareAndSet(true, false) && owned) Reflect.invoke(scoreboard, "removeObjective", objective);
        }
        private void check() { if (!active()) throw new IllegalStateException("Objective is closed: " + name()); }
    }

    private final class TeamImpl implements Team {
        private final Object scoreboard;
        private final Object team;
        private final boolean owned;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private TeamImpl(Object scoreboard, Object team, boolean owned) {
            this.scoreboard = scoreboard; this.team = team; this.owned = owned;
        }
        @Override public String name() { return String.valueOf(Reflect.invoke(team, "getName")); }
        @Override public PlayerTeam vanilla() { return (PlayerTeam) team; }
        @Override public Team displayName(Component value) { set("setDisplayName", value); return this; }
        @Override public Team prefix(Component value) { set("setPlayerPrefix", value); return this; }
        @Override public Team suffix(Component value) { set("setPlayerSuffix", value); return this; }
        private void set(String method, Component value) {
            check(); Reflect.invoke(team, method, value);
        }
        @Override public Team friendlyFire(boolean value) { check(); Reflect.invoke(team, "setAllowFriendlyFire", value); return this; }
        @Override public Team seeFriendlyInvisible(boolean value) { check(); Reflect.invoke(team, "setSeeFriendlyInvisibles", value); return this; }
        @Override public Team add(String holder) { check(); Reflect.invoke(scoreboard, "addPlayerToTeam", holder, team); return this; }
        @Override public Team remove(String holder) { check(); Reflect.invoke(scoreboard, "removePlayerFromTeam", holder, team); return this; }
        @Override @SuppressWarnings("unchecked") public Collection<String> members() {
            check(); return ListCopy.copy((Collection<String>) Reflect.invoke(team, "getPlayers"));
        }
        @Override public boolean active() { return active.get(); }
        @Override public void close() {
            if (active.compareAndSet(true, false) && owned) Reflect.invoke(scoreboard, "removePlayerTeam", team);
        }
        private void check() { if (!active()) throw new IllegalStateException("Team is closed: " + name()); }
    }

    private static final class ListCopy {
        private static <T> Collection<T> copy(Collection<T> values) { return java.util.List.copyOf(values); }
    }
}
