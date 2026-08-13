package dev.aerogel.loader.api;

import dev.aerogel.api.world.Position;
import dev.aerogel.api.world.Weather;
import dev.aerogel.api.world.World;
import dev.aerogel.api.world.WorldService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class ReflectiveWorldService implements WorldService {
    private final PluginApiScope scope;
    ReflectiveWorldService(PluginApiScope scope) { this.scope = scope; }

    @Override public Collection<World> loaded() {
        Iterable<?> levels = (Iterable<?>) Reflect.invoke(scope.serverHandle(), "getAllLevels");
        List<World> result = new ArrayList<>();
        for (Object level : levels) result.add(new WorldImpl(level));
        return List.copyOf(result);
    }

    @Override public Optional<World> find(String identifier) {
        return loaded().stream().filter(world -> world.identifier().equals(identifier)).findFirst();
    }

    @Override public World overworld() { return new WorldImpl(Reflect.invoke(scope.serverHandle(), "overworld")); }

    @Override public World wrap(ServerLevel vanillaServerLevel) {
        return new WorldImpl(java.util.Objects.requireNonNull(vanillaServerLevel, "vanillaServerLevel"));
    }

    private final class WorldImpl implements World {
        private final Object level;
        private WorldImpl(Object level) { this.level = level; }
        @Override public String identifier() {
            Object key = Reflect.invoke(level, "dimension");
            return String.valueOf(Reflect.invoke(key, "identifier"));
        }
        @Override public ServerLevel vanilla() { return (ServerLevel) level; }
        @Override public long gameTime() { return ((Number) Reflect.invoke(level, "getGameTime")).longValue(); }
        @Override public long dayTime() { return ((Number) Reflect.invoke(level, "getDayTime")).longValue(); }
        @Override public World dayTime(long value) { Reflect.invoke(level, "setDayTime", value); return this; }
        @Override public World weather(Weather value, int durationTicks) {
            if (durationTicks < 0) throw new IllegalArgumentException("durationTicks must not be negative");
            boolean rain = value != Weather.CLEAR;
            boolean thunder = value == Weather.THUNDER;
            Object weather = Reflect.invoke(level, "getWeatherData");
            Reflect.invoke(weather, "setClearWeatherTime", value == Weather.CLEAR ? durationTicks : 0);
            Reflect.invoke(weather, "setRainTime", rain ? durationTicks : 0);
            Reflect.invoke(weather, "setThunderTime", thunder ? durationTicks : 0);
            Reflect.invoke(weather, "setRaining", rain);
            Reflect.invoke(weather, "setThundering", thunder);
            return this;
        }
        @Override public BlockState block(int x, int y, int z) {
            return (BlockState) Reflect.invoke(level, "getBlockState", blockPos(x, y, z));
        }
        @Override public boolean block(int x, int y, int z, BlockState state, int flags) {
            return (Boolean) Reflect.invoke(level, "setBlock", blockPos(x, y, z), state, flags);
        }
        @Override public boolean spawn(Entity entity) { return (Boolean) Reflect.invoke(level, "addFreshEntity", entity); }
        @Override public void teleport(ServerPlayer player, Position position) {
            Reflect.invoke(player, "teleportTo", level, position.x(), position.y(), position.z(), Set.of(),
                position.yaw(), position.pitch(), true);
        }
        private Object blockPos(int x, int y, int z) {
            return Reflect.construct(Reflect.type(scope.loader(), "net.minecraft.core.BlockPos"), x, y, z);
        }
        @Override public boolean equals(Object other) {
            return other instanceof WorldImpl world && world.level == level;
        }
        @Override public int hashCode() { return System.identityHashCode(level); }
        @Override public String toString() { return "World[" + identifier() + "]"; }
    }
}
