package dev.aerogel.loader.plugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public record PluginDescriptor(
    Path jar,
    String id,
    String version,
    String name,
    String minecraft,
    List<String> entrypoints,
    List<String> mixins,
    Map<String, String> dependencies
) {
    public static final String METADATA_PATH = "aerogel.plugin.json";
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]{1,63}");

    public static PluginDescriptor parse(Path jar, Reader reader) throws IOException {
        final JsonObject json;
        try {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("Invalid " + METADATA_PATH + " in " + jar, exception);
        }
        if (required(json, "schemaVersion", jar).getAsInt() != 1) {
            throw new IOException("Unsupported plugin metadata schema in " + jar);
        }
        String id = requiredString(json, "id", jar);
        if (!ID.matcher(id).matches()) {
            throw new IOException("Invalid plugin id '" + id + "' in " + jar);
        }
        String version = requiredString(json, "version", jar);
        String name = json.has("name") ? json.get("name").getAsString() : id;
        String minecraft = json.has("minecraft") ? json.get("minecraft").getAsString() : ">=26.2";
        List<String> entrypoints = strings(json.get("entrypoints"), "entrypoints", jar);
        List<String> mixins = strings(json.get("mixins"), "mixins", jar);
        Map<String, String> dependencies = new LinkedHashMap<>();
        if (json.has("depends")) {
            JsonObject depends = json.getAsJsonObject("depends");
            for (String dependency : depends.keySet()) {
                dependencies.put(dependency, depends.get(dependency).getAsString());
            }
        }
        return new PluginDescriptor(
            jar, id, version, name, minecraft, List.copyOf(entrypoints), List.copyOf(mixins), Map.copyOf(dependencies)
        );
    }

    private static List<String> strings(JsonElement element, String field, Path jar) throws IOException {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            result.add(element.getAsString());
        } else if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString() || item.getAsString().isBlank()) {
                    throw new IOException("'" + field + "' must contain only non-empty strings in " + jar);
                }
                result.add(item.getAsString());
            }
        } else {
            throw new IOException("'" + field + "' must be a string or array in " + jar);
        }
        return result;
    }

    private static JsonElement required(JsonObject json, String key, Path jar) throws IOException {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            throw new IOException("Missing '" + key + "' in " + jar);
        }
        return json.get(key);
    }

    private static String requiredString(JsonObject json, String key, Path jar) throws IOException {
        String value = required(json, key, jar).getAsString().strip();
        if (value.isEmpty()) {
            throw new IOException("Empty '" + key + "' in " + jar);
        }
        return value;
    }
}
