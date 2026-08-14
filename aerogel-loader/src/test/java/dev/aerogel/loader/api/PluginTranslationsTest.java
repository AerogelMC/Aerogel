package dev.aerogel.loader.api;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginTranslationsTest {
    @Test
    void resolvesRequestedLanguageThenEnglishThenKey() {
        ClassLoader resources = new MemoryResources(Map.of(
            "assets/test_plugin/lang/en_us.json", "{\"test.hello\":\"Hello %s\",\"test.only_en\":\"English\"}",
            "assets/test_plugin/lang/ko_kr.json", "{\"test.hello\":\"안녕하세요 %s\"}"
        ));
        PluginTranslations translations = new PluginTranslations(
            "test_plugin", resources, Logger.getAnonymousLogger());

        assertEquals("안녕하세요 %s", translations.text("ko-KR", "test.hello"));
        assertEquals("English", translations.text("ko_kr", "test.only_en"));
        assertEquals("test.missing", translations.text("ko_kr", "test.missing"));
    }

    private static final class MemoryResources extends ClassLoader {
        private final Map<String, byte[]> resources;

        private MemoryResources(Map<String, String> resources) {
            super(null);
            this.resources = resources.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> entry.getValue().getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            byte[] value = resources.get(name);
            return value == null ? null : new ByteArrayInputStream(value);
        }
    }
}
