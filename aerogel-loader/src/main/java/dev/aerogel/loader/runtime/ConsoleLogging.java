package dev.aerogel.loader.runtime;

import java.net.URL;

final class ConsoleLogging {
    private static final String CONFIGURATION_PROPERTY = "log4j.configurationFile";
    private static final String COLOR_PROPERTY = "aerogel.console.color";
    private static final String DISABLE_ANSI_PROPERTY = "aerogel.console.disableAnsi";

    private ConsoleLogging() {
    }

    static void configure(ClassLoader loader) {
        if (System.getProperty(CONFIGURATION_PROPERTY) == null) {
            URL configuration = loader.getResource("aerogel-log4j2.xml");
            if (configuration != null) {
                System.setProperty(CONFIGURATION_PROPERTY, configuration.toExternalForm());
            }
        }
        System.setProperty(DISABLE_ANSI_PROPERTY, Boolean.toString(!colorEnabled()));
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
