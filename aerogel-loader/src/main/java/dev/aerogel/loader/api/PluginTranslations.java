package dev.aerogel.loader.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.aerogel.api.translation.TranslationService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

final class PluginTranslations implements TranslationService {
    private static final Gson GSON = new Gson();
    private final String resourceRoot;
    private final ClassLoader resourceLoader;
    private final Logger logger;
    private final Map<String, Map<String, String>> languages = new ConcurrentHashMap<>();

    PluginTranslations(String pluginId, ClassLoader resourceLoader, Logger logger) {
        this.resourceRoot = "assets/" + pluginId + "/lang/";
        this.resourceLoader = resourceLoader;
        this.logger = logger;
    }

    @Override
    public Component component(String key, Object... arguments) {
        return componentForLocale("en_us", key, arguments);
    }

    @Override
    public Component componentFor(ServerPlayer recipient, String key, Object... arguments) {
        if (recipient == null) throw new NullPointerException("recipient");
        return componentForLocale(playerLanguage(recipient), key, arguments);
    }

    @Override
    public Component componentForLocale(String language, String key, Object... arguments) {
        if (key == null) throw new NullPointerException("key");
        String fallback = text(language, key);
        return Component.translatableWithFallback(key, fallback, arguments);
    }

    @Override
    public String text(String language, String key) {
        if (key == null) throw new NullPointerException("key");
        String normalized = normalize(language);
        String translated = load(normalized).get(key);
        if (translated != null) return translated;
        if (!normalized.equals("en_us")) {
            translated = load("en_us").get(key);
            if (translated != null) return translated;
        }
        return key;
    }

    private Map<String, String> load(String language) {
        return languages.computeIfAbsent(language, this::read);
    }

    private Map<String, String> read(String language) {
        String resource = resourceRoot + language + ".json";
        try (InputStream input = resourceLoader.getResourceAsStream(resource)) {
            if (input == null) return Map.of();
            JsonObject json = GSON.fromJson(
                new InputStreamReader(input, StandardCharsets.UTF_8), JsonObject.class);
            if (json == null) return Map.of();
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                if (entry.getValue().isJsonPrimitive()
                    && entry.getValue().getAsJsonPrimitive().isString()) {
                    values.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            return Map.copyOf(values);
        } catch (IOException | RuntimeException exception) {
            logger.log(Level.WARNING, "Could not load translation resource " + resource, exception);
            return Map.of();
        }
    }

    private static String playerLanguage(ServerPlayer player) {
        String language = player.clientInformation().language();
        return language == null ? "en_us" : language;
    }

    private static String normalize(String language) {
        if (language == null || language.isBlank()) return "en_us";
        return language.toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
