package dev.aerogel.loader.command;

import org.jline.reader.Candidate;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;

public final class InteractiveConsole {
    private InteractiveConsole() {
    }

    /** Returns false when input is redirected or JLine cannot own the terminal, allowing vanilla input fallback. */
    public static boolean run(Object consoleThread) {
        if (System.console() == null) {
            return false;
        }
        final Object server;
        try {
            Field owner = consoleThread.getClass().getDeclaredField("this$0");
            owner.setAccessible(true);
            server = owner.get(consoleThread);
        } catch (ReflectiveOperationException exception) {
            System.err.println("[Aerogel] Cannot attach interactive console: " + exception.getMessage());
            return false;
        }

        try (Terminal terminal = TerminalBuilder.builder().system(true).nativeSignals(true).build()) {
            LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer((ignored, line, candidates) -> {
                    for (String suggestion : PluginsCommand.complete(line.line(), line.cursor())) {
                        candidates.add(new Candidate(suggestion));
                    }
                })
                .option(LineReader.Option.AUTO_LIST, true)
                .build();
            reader.setVariable(LineReader.HISTORY_FILE,
                Path.of(System.getProperty("user.dir")).resolve(".aerogel").resolve("console-history"));
            Method sourceFactory = server.getClass().getMethod("createCommandSourceStack");
            Method submit = findSubmitMethod(server.getClass());
            while (!(boolean) server.getClass().getMethod("isStopped").invoke(server)
                && (boolean) server.getClass().getMethod("isRunning").invoke(server)) {
                try {
                    String line = reader.readLine("> ");
                    if (!line.isBlank()) {
                        submit.invoke(server, line, sourceFactory.invoke(server));
                    }
                } catch (UserInterruptException ignored) {
                    // Ctrl-C clears the current line without stopping the server.
                } catch (EndOfFileException endOfInput) {
                    break;
                }
            }
            return true;
        } catch (ReflectiveOperationException | java.io.IOException exception) {
            System.err.println("[Aerogel] Interactive console unavailable; using vanilla input: "
                + exception.getMessage());
            return false;
        }
    }

    private static Method findSubmitMethod(Class<?> serverType) throws NoSuchMethodException {
        for (Method method : serverType.getMethods()) {
            if (method.getName().equals("handleConsoleInput") && method.getParameterCount() == 2
                && method.getParameterTypes()[0] == String.class) {
                return method;
            }
        }
        throw new NoSuchMethodException(serverType.getName() + ".handleConsoleInput");
    }
}
