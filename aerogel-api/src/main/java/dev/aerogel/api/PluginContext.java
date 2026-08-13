package dev.aerogel.api;

import java.nio.file.Path;
import java.util.logging.Logger;

/** Stable context passed to a plugin's pre-launch entry point. */
public interface PluginContext {
    String pluginId();

    String pluginVersion();

    Path serverDirectory();

    Path dataDirectory();

    Logger logger();
}
