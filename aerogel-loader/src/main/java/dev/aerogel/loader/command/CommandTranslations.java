package dev.aerogel.loader.command;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CommandTranslations {
    private static final Gson GSON = new Gson();
    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();
    private static final String[] SUPPORTED = {
        "en_us", "ko_kr", "ja_jp", "zh_cn", "zh_tw", "de_de", "fr_fr", "es_es", "pt_br", "ru_ru"
    };

    private CommandTranslations() {
    }

    public static String fallback(String language, String key, String englishFallback) {
        String normalized = normalize(language);
        return CACHE.computeIfAbsent(normalized, CommandTranslations::load).getOrDefault(key, englishFallback);
    }

    private static String normalize(String language) {
        if (language == null) {
            return "en_us";
        }
        String normalized = language.toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        for (String supported : SUPPORTED) {
            if (supported.equals(normalized)) {
                return supported;
            }
        }
        return "en_us";
    }

    private static Map<String, String> load(String language) {
        String resource = "/assets/aerogel/lang/" + language + ".json";
        try (InputStream input = CommandTranslations.class.getResourceAsStream(resource)) {
            if (input == null) {
                return Map.of();
            }
            JsonObject json = GSON.fromJson(
                new InputStreamReader(input, StandardCharsets.UTF_8), JsonObject.class);
            Map<String, String> values = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                values.put(entry.getKey(), entry.getValue().getAsString());
            }
            return Map.copyOf(values);
        } catch (IOException | RuntimeException ignored) {
            return Map.of();
        }
    }
}
