package dev.aerogel.gradle;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

public abstract class PluginMetadata {
    public abstract Property<String> getId();

    public abstract Property<String> getName();

    public abstract Property<String> getVersion();

    public abstract ListProperty<String> getEntrypoints();

    public abstract ListProperty<String> getMixins();

    public abstract MapProperty<String, String> getDepends();

    public void entrypoint(String className) {
        getEntrypoints().add(className);
    }

    public void mixin(String configuration) {
        getMixins().add(configuration);
    }

    public void dependsOn(String pluginId, String versionRange) {
        getDepends().put(pluginId, versionRange);
    }
}
