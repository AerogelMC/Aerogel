package dev.aerogel.gradle;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.file.RegularFileProperty;

import javax.inject.Inject;

public abstract class AerogelExtension {
    private final PluginMetadata plugin;

    @Inject
    public AerogelExtension(ObjectFactory objects) {
        plugin = objects.newInstance(PluginMetadata.class);
    }

    public abstract Property<String> getMinecraft();

    /** Optional existing official Mojang bundler JAR. The default downloads and verifies it. */
    public abstract RegularFileProperty getMinecraftServerJar();

    public PluginMetadata getPlugin() {
        return plugin;
    }

    public void plugin(Action<? super PluginMetadata> action) {
        action.execute(plugin);
    }
}
