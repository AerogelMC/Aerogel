package dev.aerogel.api.world;

import java.util.Collection;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

public interface WorldService {
    Collection<World> loaded();
    Optional<World> find(String identifier);
    World overworld();
    World wrap(ServerLevel vanillaServerLevel);
}
