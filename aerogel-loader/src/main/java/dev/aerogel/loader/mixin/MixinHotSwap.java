package dev.aerogel.loader.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aerogel.loader.plugin.PluginDescriptor;

import java.io.InputStreamReader;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarFile;

/** Guarded bridge to Mixin's Java-agent hot swap support. */
public final class MixinHotSwap {
    private MixinHotSwap() {
    }

    public static Snapshot inspect(PluginDescriptor plugin) throws Exception {
        if (plugin.mixins().isEmpty()) return Snapshot.EMPTY;
        Set<String> classes = new LinkedHashSet<>();
        Map<String, String> configurations = new TreeMap<>();
        Map<String, String> classHashes = new TreeMap<>();
        try (JarFile jar = new JarFile(plugin.jar().toFile(), false)) {
            for (String path : plugin.mixins()) {
                var entry = jar.getJarEntry(path);
                if (entry == null) throw new IllegalStateException("Missing Mixin configuration " + path);
                byte[] bytes;
                try (var input = jar.getInputStream(entry)) {
                    bytes = input.readAllBytes();
                }
                JsonObject config = JsonParser.parseReader(
                    new InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8))
                    .getAsJsonObject();
                configurations.put(path, sha256(canonicalJson(config).getBytes(StandardCharsets.UTF_8)));
                String packageName = config.has("package") ? config.get("package").getAsString() : "";
                collect(config.get("mixins"), packageName, classes);
                collect(config.get("server"), packageName, classes);
            }
            for (String className : classes) {
                var entry = jar.getJarEntry(className.replace('.', '/') + ".class");
                if (entry == null) {
                    throw new IllegalStateException("Missing Mixin class " + className);
                }
                try (var input = jar.getInputStream(entry)) {
                    classHashes.put(className, sha256(input.readAllBytes()));
                }
            }
        }
        return new Snapshot(Set.copyOf(classes), Map.copyOf(configurations), Map.copyOf(classHashes));
    }

    public static Snapshot reload(PluginDescriptor updatedPlugin, Snapshot loadedState) throws Exception {
        Snapshot updatedState = inspect(updatedPlugin);
        if (!loadedState.configurations().equals(updatedState.configurations())) {
            throw new IllegalStateException("Mixin configuration changed; restart required");
        }
        if (!loadedState.classes().equals(updatedState.classes())) {
            throw new IllegalStateException("Mixin class list changed; restart required");
        }
        if (updatedState.classes().isEmpty()) return updatedState;
        if (loadedState.classHashes().equals(updatedState.classHashes())) return updatedState;

        Instrumentation instrumentation = instrumentation();
        Map<String, Class<?>> fakeMixins = new java.util.HashMap<>();
        for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
            if (loaded.getClassLoader() != null
                && loaded.getClassLoader().getClass().getName()
                    .equals("org.spongepowered.tools.agent.MixinAgentClassLoader")) {
                fakeMixins.put(loaded.getName(), loaded);
            }
        }

        List<ClassDefinition> definitions = new ArrayList<>();
        try (JarFile jar = new JarFile(updatedPlugin.jar().toFile(), false)) {
            for (String className : updatedState.classes()) {
                Class<?> fakeClass = fakeMixins.get(className);
                if (fakeClass == null) {
                    throw new IllegalStateException("Mixin " + className + " was not registered for hot swap");
                }
                var entry = jar.getJarEntry(className.replace('.', '/') + ".class");
                try (var input = jar.getInputStream(entry)) {
                    definitions.add(new ClassDefinition(fakeClass, input.readAllBytes()));
                }
            }
        }
        instrumentation.redefineClasses(definitions.toArray(ClassDefinition[]::new));
        return updatedState;
    }

    private static Instrumentation instrumentation() throws ReflectiveOperationException {
        Class<?> agent = Class.forName("org.spongepowered.tools.agent.MixinAgent");
        Field field = agent.getDeclaredField("instrumentation");
        field.setAccessible(true);
        Instrumentation instrumentation = (Instrumentation) field.get(null);
        if (instrumentation == null || !instrumentation.isRedefineClassesSupported()) {
            throw new IllegalStateException("Mixin hot-swap agent is not active; restart through the Aerogel launcher");
        }
        return instrumentation;
    }

    private static void collect(JsonElement element, String packageName, Set<String> classes) {
        if (element == null || element.isJsonNull()) return;
        for (JsonElement value : element.getAsJsonArray()) {
            String name = value.getAsString();
            classes.add(packageName.isBlank() ? name : packageName + '.' + name);
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String canonicalJson(JsonElement element) {
        if (element.isJsonObject()) {
            StringBuilder result = new StringBuilder("{");
            boolean first = true;
            for (String key : new java.util.TreeSet<>(element.getAsJsonObject().keySet())) {
                if (!first) result.append(',');
                first = false;
                result.append(new com.google.gson.JsonPrimitive(key));
                result.append(':').append(canonicalJson(element.getAsJsonObject().get(key)));
            }
            return result.append('}').toString();
        }
        if (element.isJsonArray()) {
            StringBuilder result = new StringBuilder("[");
            boolean first = true;
            for (JsonElement value : element.getAsJsonArray()) {
                if (!first) result.append(',');
                first = false;
                result.append(canonicalJson(value));
            }
            return result.append(']').toString();
        }
        return element.toString();
    }

    public record Snapshot(
        Set<String> classes,
        Map<String, String> configurations,
        Map<String, String> classHashes
    ) {
        private static final Snapshot EMPTY = new Snapshot(Set.of(), Map.of(), Map.of());

        public static Snapshot empty() {
            return EMPTY;
        }
    }
}
