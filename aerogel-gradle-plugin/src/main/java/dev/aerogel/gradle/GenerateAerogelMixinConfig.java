package dev.aerogel.gradle;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Generates the standard Sponge Mixin configuration for compiled Kotlin DSL scripts. */
public abstract class GenerateAerogelMixinConfig extends DefaultTask {
    @Input public abstract Property<String> getPluginId();
    @InputFile public abstract RegularFileProperty getIndexFile();
    @OutputDirectory public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() {
        String pluginId = getPluginId().get();
        Path directory = getOutputDirectory().get().getAsFile().toPath();
        JsonObject json = new JsonObject();
        json.addProperty("required", true);
        json.addProperty("minVersion", "0.8");
        json.addProperty("package", GenerateAerogelMixinSources.generatedPackage(pluginId));
        json.addProperty("compatibilityLevel", "JAVA_25");
        JsonArray mixins = new JsonArray();
        try {
            clean(directory);
            for (String line : Files.readAllLines(getIndexFile().get().getAsFile().toPath(), StandardCharsets.UTF_8)) {
                if (!line.isBlank()) mixins.add(line.substring(0, line.indexOf('\t')));
            }
            if (mixins.isEmpty()) return;
            json.add("mixins", mixins);
            JsonObject injectors = new JsonObject();
            injectors.addProperty("defaultRequire", 1);
            json.add("injectors", injectors);
            Path output = getOutputDirectory().get().file(pluginId + ".generated.mixins.json")
                .getAsFile().toPath();
            Files.createDirectories(output.getParent());
            Files.writeString(output,
                new GsonBuilder().setPrettyPrinting().create().toJson(json) + System.lineSeparator(),
                StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException exception) {
            throw new GradleException("Cannot generate Aerogel Mixin configuration", exception);
        }
    }

    private static void clean(Path output) throws IOException {
        if (!Files.isDirectory(output)) return;
        try (var paths = Files.walk(output)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(output)) Files.deleteIfExists(path);
            }
        }
    }
}
