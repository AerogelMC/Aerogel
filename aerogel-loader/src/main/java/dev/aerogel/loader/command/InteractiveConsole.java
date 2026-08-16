package dev.aerogel.loader.command;

import org.jline.reader.Candidate;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.nio.file.Path;
import net.minecraft.server.dedicated.DedicatedServer;

public final class InteractiveConsole {
    private static volatile Terminal activeTerminal;
    private static volatile Thread activeThread;
    private static volatile boolean closingForRestart;

    private InteractiveConsole() {
    }

    /** Returns false when input is redirected or JLine cannot own the terminal, allowing vanilla input fallback. */
    public static boolean run(DedicatedServer server) {
        if (System.console() == null) {
            return false;
        }
        try (Terminal terminal = TerminalBuilder.builder().system(true).nativeSignals(true).build()) {
            closingForRestart = false;
            activeTerminal = terminal;
            activeThread = Thread.currentThread();
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
            while (!server.isStopped() && server.isRunning()) {
                try {
                    String line = reader.readLine("> ");
                    if (!line.isBlank()) {
                        server.handleConsoleInput(line, server.createCommandSourceStack());
                    }
                } catch (UserInterruptException ignored) {
                    // Ctrl-C clears the current line without stopping the server.
                } catch (EndOfFileException endOfInput) {
                    break;
                } catch (IllegalStateException terminalClosed) {
                    if (closingForRestart) {
                        break;
                    }
                    throw terminalClosed;
                }
            }
            return true;
        } catch (java.io.IOException exception) {
            if (closingForRestart) {
                return true;
            }
            System.err.println("[Aerogel] Interactive console unavailable; using vanilla input: "
                + exception.getMessage());
            return false;
        } catch (IllegalStateException exception) {
            if (closingForRestart) {
                return true;
            }
            throw exception;
        } finally {
            activeTerminal = null;
            activeThread = null;
        }
    }

    public static void stop() {
        closingForRestart = true;
        Thread thread = activeThread;
        if (thread != null) {
            thread.interrupt();
        }
        Terminal terminal = activeTerminal;
        if (terminal != null) {
            try {
                terminal.close();
            } catch (java.io.IOException ignored) {
                // The console may already be closing as part of normal shutdown.
            }
        }
    }

}
