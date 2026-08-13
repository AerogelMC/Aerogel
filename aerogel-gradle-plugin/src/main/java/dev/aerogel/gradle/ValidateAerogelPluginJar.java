package dev.aerogel.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

public abstract class ValidateAerogelPluginJar extends DefaultTask {
    private static final String[] FORBIDDEN_PREFIXES = {
        "net/minecraft/",
        "dev/aerogel/api/",
        "org/spongepowered/asm/"
    };

    @InputFile
    public abstract RegularFileProperty getPluginJar();

    @TaskAction
    public void validateJar() {
        List<String> bundledClasses = new ArrayList<>();
        try (JarFile jar = new JarFile(getPluginJar().get().getAsFile(), false)) {
            int metadataFiles = 0;
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.equals("aerogel.plugin.json")) {
                    metadataFiles++;
                }
                for (String prefix : FORBIDDEN_PREFIXES) {
                    if (name.startsWith(prefix) && name.endsWith(".class")) {
                        bundledClasses.add(name);
                    }
                }
            }
            if (metadataFiles != 1) {
                throw new GradleException("Plugin JAR must contain exactly one aerogel.plugin.json, found "
                    + metadataFiles);
            }
        } catch (IOException exception) {
            throw new GradleException("Cannot inspect Aerogel plugin JAR", exception);
        }
        if (!bundledClasses.isEmpty()) {
            int shown = Math.min(8, bundledClasses.size());
            throw new GradleException("Plugin JAR bundles protected runtime classes; use compileOnly dependencies: "
                + String.join(", ", bundledClasses.subList(0, shown))
                + (bundledClasses.size() > shown ? " ..." : ""));
        }
        getLogger().lifecycle("[Aerogel] Plugin JAR validated: {}", getPluginJar().get().getAsFile());
    }
}
