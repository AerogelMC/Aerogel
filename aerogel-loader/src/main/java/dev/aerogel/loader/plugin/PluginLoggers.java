package dev.aerogel.loader.plugin;

import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

final class PluginLoggers {
    private PluginLoggers() {
    }

    static Logger create(String pluginId) {
        String name = "Aerogel/" + pluginId;
        Logger logger = Logger.getLogger(name);
        logger.setUseParentHandlers(false);
        for (Handler handler : logger.getHandlers()) logger.removeHandler(handler);
        logger.addHandler(new Slf4jHandler(LoggerFactory.getLogger(name)));
        logger.setLevel(Level.ALL);
        return logger;
    }

    private static final class Slf4jHandler extends Handler {
        private final org.slf4j.Logger logger;

        private Slf4jHandler(org.slf4j.Logger logger) {
            this.logger = logger;
            setLevel(Level.ALL);
        }

        @Override public void publish(LogRecord record) {
            if (!isLoggable(record)) return;
            String message = format(record);
            Throwable thrown = record.getThrown();
            int level = record.getLevel().intValue();
            if (level >= Level.SEVERE.intValue()) {
                if (thrown == null) logger.error(message); else logger.error(message, thrown);
            } else if (level >= Level.WARNING.intValue()) {
                if (thrown == null) logger.warn(message); else logger.warn(message, thrown);
            } else if (level >= Level.INFO.intValue()) {
                if (thrown == null) logger.info(message); else logger.info(message, thrown);
            } else if (level >= Level.FINE.intValue()) {
                if (thrown == null) logger.debug(message); else logger.debug(message, thrown);
            } else {
                if (thrown == null) logger.trace(message); else logger.trace(message, thrown);
            }
        }

        private static String format(LogRecord record) {
            String message = record.getMessage();
            Object[] parameters = record.getParameters();
            if (parameters == null || parameters.length == 0) return message;
            try {
                return MessageFormat.format(message, parameters);
            } catch (IllegalArgumentException ignored) {
                return message;
            }
        }

        @Override public void flush() {
        }

        @Override public void close() {
        }
    }
}
