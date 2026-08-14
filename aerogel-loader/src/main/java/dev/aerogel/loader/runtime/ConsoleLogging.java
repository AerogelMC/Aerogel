package dev.aerogel.loader.runtime;

import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class ConsoleLogging {
    private static final String CONFIGURATION_PROPERTY = "log4j.configurationFile";
    private static final String COLOR_PROPERTY = "aerogel.console.color";
    private static final String DISABLE_ANSI_PROPERTY = "aerogel.console.disableAnsi";
    private static final String CHARSET_PROPERTY = "aerogel.console.charset";

    private ConsoleLogging() {
    }

    static void configure(ClassLoader loader) {
        System.setProperty(CHARSET_PROPERTY, consoleCharset().name());
        if (System.getProperty(CONFIGURATION_PROPERTY) == null) {
            URL configuration = loader.getResource("aerogel-log4j2.xml");
            if (configuration != null) {
                System.setProperty(CONFIGURATION_PROPERTY, configuration.toExternalForm());
            }
        }
        System.setProperty(DISABLE_ANSI_PROPERTY, Boolean.toString(!colorEnabled()));
    }

    static Charset consoleCharset() {
        if (System.console() != null) {
            return System.console().charset();
        }
        String stdout = System.getProperty("stdout.encoding");
        if (stdout != null && Charset.isSupported(stdout)) {
            return Charset.forName(stdout);
        }
        return StandardCharsets.UTF_8;
    }

    static boolean ansiEnabled() {
        String disabled = System.getProperty(DISABLE_ANSI_PROPERTY);
        return disabled == null ? colorEnabled() : !Boolean.parseBoolean(disabled);
    }

    private static boolean colorEnabled() {
        String override = System.getProperty(COLOR_PROPERTY);
        if (override != null) return Boolean.parseBoolean(override);
        String noColor = System.getenv("NO_COLOR");
        if (noColor != null) return false;
        return System.console() != null && !"dumb".equalsIgnoreCase(System.getenv("TERM"));
    }
}
