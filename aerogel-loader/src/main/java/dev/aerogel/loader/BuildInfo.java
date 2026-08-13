package dev.aerogel.loader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public record BuildInfo(String version, String minecraftVersion, String mixinVersion) {
    private static final BuildInfo CURRENT = load();

    public static BuildInfo current() {
        return CURRENT;
    }

    private static BuildInfo load() {
        Properties properties = new Properties();
        try (InputStream input = BuildInfo.class.getResourceAsStream("/aerogel-build.properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing aerogel-build.properties");
            }
            properties.load(input);
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
        return new BuildInfo(
            properties.getProperty("version"),
            properties.getProperty("minecraftVersion"),
            properties.getProperty("mixinVersion")
        );
    }
}
