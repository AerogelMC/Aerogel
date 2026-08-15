package dev.aerogel.gradle;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public abstract class GenerateAerogelMetadata extends DefaultTask {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]{1,63}");

    @Input public abstract Property<String> getPluginId();
    @Input public abstract Property<String> getDisplayName();
    @Input public abstract Property<String> getPluginVersion();
    @Input public abstract Property<String> getMinecraftRequirement();
    @Input public abstract ListProperty<String> getEntrypoints();
    @Input public abstract ListProperty<String> getMixins();
    @Input public abstract Property<String> getGeneratedMixinConfiguration();
    @InputFile public abstract RegularFileProperty getGeneratedMixinIndex();
    @Input public abstract MapProperty<String, String> getDepends();
    @OutputDirectory public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() {
        String id = required(getPluginId(), "aerogel.plugin.id");
        if (!ID.matcher(id).matches()) {
            throw new GradleException("Invalid Aerogel plugin id '" + id
                + "'; use 2-64 lowercase letters, digits, '_' or '-'");
        }
        String name = required(getDisplayName(), "aerogel.plugin.name");
        String version = required(getPluginVersion(), "aerogel.plugin.version");
        String minecraft = required(getMinecraftRequirement(), "aerogel.minecraft");
        validateStrings(getEntrypoints().get(), "entrypoint");
        List<String> mixinConfigurations = new ArrayList<>(getMixins().get());
        try {
            if (Files.size(getGeneratedMixinIndex().get().getAsFile().toPath()) > 0) {
                mixinConfigurations.add(required(
                    getGeneratedMixinConfiguration(), "generated Mixin configuration"));
            }
        } catch (IOException exception) {
            throw new GradleException("Cannot inspect generated Aerogel Mixin index", exception);
        }
        validateStrings(mixinConfigurations, "mixin configuration");

        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", 1);
        json.addProperty("id", id);
        json.addProperty("version", version);
        json.addProperty("name", name);
        json.addProperty("minecraft", minecraft);
        JsonArray entrypoints = new JsonArray();
        getEntrypoints().get().forEach(entrypoints::add);
        if (!entrypoints.isEmpty()) {
            json.add("entrypoints", entrypoints);
        }
        JsonArray mixins = new JsonArray();
        mixinConfigurations.forEach(mixins::add);
        if (!mixins.isEmpty()) {
            json.add("mixins", mixins);
        }
        JsonObject depends = new JsonObject();
        for (Map.Entry<String, String> dependency : getDepends().get().entrySet()) {
            if (!ID.matcher(dependency.getKey()).matches() || dependency.getValue().isBlank()) {
                throw new GradleException("Invalid Aerogel plugin dependency: " + dependency);
            }
            depends.addProperty(dependency.getKey(), dependency.getValue());
        }
        json.add("depends", depends);

        Path output = getOutputDirectory().get().file("aerogel.plugin.json").getAsFile().toPath();
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output,
                new GsonBuilder().setPrettyPrinting().create().toJson(json) + System.lineSeparator(),
                StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new GradleException("Cannot write " + output, exception);
        }
    }

    private static String required(Property<String> property, String path) {
        if (!property.isPresent() || property.get().isBlank() || "unspecified".equals(property.get())) {
            throw new GradleException("Missing required " + path);
        }
        return property.get().strip();
    }

    private static void validateStrings(Iterable<String> values, String label) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new GradleException("Aerogel " + label + " must not be blank");
            }
        }
    }
}
