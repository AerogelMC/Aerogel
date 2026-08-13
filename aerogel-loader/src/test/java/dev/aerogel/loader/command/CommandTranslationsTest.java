package dev.aerogel.loader.command;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommandTranslationsTest {
    private static final String[] LANGUAGES = {
        "en_us", "ko_kr", "ja_jp", "zh_cn", "zh_tw", "de_de", "fr_fr", "es_es", "pt_br", "ru_ru"
    };

    @Test
    void allClientLanguagesHaveMatchingKeysAndArguments() {
        JsonObject english = resource("en_us");
        Set<String> keys = english.keySet();
        for (String language : LANGUAGES) {
            JsonObject translated = resource(language);
            assertEquals(keys, translated.keySet(), language);
            for (String key : keys) {
                assertEquals(placeholders(english.get(key).getAsString()),
                    placeholders(translated.get(key).getAsString()), language + ": " + key);
            }
        }
    }

    @Test
    void usesPlayerLanguageAndFallsBackToEnglish() {
        String key = "commands.aerogel.plugins.unknown";
        assertNotEquals(CommandTranslations.fallback("ko_kr", key, "fallback"),
            CommandTranslations.fallback("en_us", key, "fallback"));
        assertEquals("Unknown plugin: %s", CommandTranslations.fallback("unknown", key, "fallback"));
    }

    private static JsonObject resource(String language) {
        var input = CommandTranslationsTest.class.getResourceAsStream(
            "/assets/aerogel/lang/" + language + ".json");
        assertNotNull(input, language);
        try (input) {
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static int placeholders(String text) {
        int count = 0;
        for (int offset = 0; (offset = text.indexOf("%s", offset)) >= 0; offset += 2) {
            count++;
        }
        return count;
    }
}
