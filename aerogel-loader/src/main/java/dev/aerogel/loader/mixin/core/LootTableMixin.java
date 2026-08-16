package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.PluginContext;
import dev.aerogel.api.Registration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Objects;

@Mixin(targets = "net.minecraft.world.level.storage.loot.LootTable")
abstract class LootTableMixin {
    /** Registers this table under the supplied plugin's namespace. */
    @Unique
    public Registration register(PluginContext plugin, String path) {
        Objects.requireNonNull(plugin, "plugin");
        return plugin.loot().register(path, (LootTable) (Object) this);
    }

    /** Registers this table as a resource owned by the supplied plugin. */
    @Unique
    public Registration register(PluginContext plugin, Identifier id) {
        Objects.requireNonNull(plugin, "plugin");
        return plugin.loot().register(Objects.requireNonNull(id, "id"),
            (LootTable) (Object) this);
    }
}
