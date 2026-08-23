package dev.aerogel.loader.restart;

import dev.aerogel.loader.command.CommandTranslations;
import dev.aerogel.loader.command.InteractiveConsole;
import dev.aerogel.loader.event.EventHooks;
import dev.aerogel.loader.internal.RestartGameListenerBridge;
import dev.aerogel.loader.internal.ServerCommonConnectionBridge;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.apache.logging.log4j.LogManager;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

/** Coordinates a full JVM restart while the retiring JVM temporarily holds player connections. */
public final class RestartCoordinator {
    private static final String STARTING_KEY = "commands.aerogel.restart.starting";
    private static final String COMPLETE_KEY = "commands.aerogel.restart.complete";
    private static final String FAILED_KEY = "commands.aerogel.restart.failed";
    private static final String STARTING_FALLBACK =
        "Server restart started. Your connection will be restored automatically.";
    private static final String COMPLETE_FALLBACK = "Server restart completed in %s seconds.";
    private static final String FAILED_FALLBACK = "Server restart failed. Please reconnect later.";
    private static final long KEEP_ALIVE_INTERVAL_MILLIS = 5_000L;
    private static final long HANDOFF_GRACE_MILLIS = 1_500L;
    private static final long MAX_HOLD_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final long TRANSFER_ACCEPT_WINDOW_MILLIS = Duration.ofMinutes(2).toMillis();
    private static final AtomicBoolean REQUESTED = new AtomicBoolean();
    private static final Path SESSION_DIRECTORY = sessionDirectory();
    private static final int GENERATION = Integer.getInteger("aerogel.restartGeneration", 0);
    private static final Set<UUID> RETURNING_PLAYERS = new HashSet<>();
    private static final Set<String> RETURNING_ADDRESSES = new HashSet<>();
    private static final Set<Object> FROZEN_CONNECTIONS =
        ConcurrentHashMap.newKeySet();
    private static final Set<Object> FROZEN_LISTENERS =
        ConcurrentHashMap.newKeySet();
    private static volatile RestartState state;
    private static volatile String completedSeconds;
    private static volatile long readyAt;

    private RestartCoordinator() {
    }

    public static boolean available() {
        return SESSION_DIRECTORY != null;
    }

    public static boolean requested() {
        return REQUESTED.get();
    }

    public static boolean request(Object serverObject) {
        MinecraftServer server = (MinecraftServer) serverObject;
        if (!available() || !REQUESTED.compareAndSet(false, true)) {
            return false;
        }

        List<HeldConnection> held = new ArrayList<>();
        try {
            long startedAt = System.currentTimeMillis();
            List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
            held = new ArrayList<>(players.size());

            for (ServerPlayer player : players) {
                send(player, STARTING_KEY, STARTING_FALLBACK);
                ServerGamePacketListenerImpl listener = player.connection;
                Connection connection = ((ServerCommonConnectionBridge) listener).aerogel$connection();
                UUID uuid = player.getUUID();
                String language = language(player);
                held.add(new HeldConnection(connection, listener, RestartAddressRegistry.address(connection), uuid,
                    language, remoteHost(connection)));
            }

            // Freeze every client before removing the first player. Otherwise that first removal would still be
            // visible to the other clients which have not entered the holding state yet.
            for (HeldConnection connection : held) {
                FROZEN_CONNECTIONS.add(connection.connection());
            }
            for (HeldConnection connection : held) {
                ((RestartGameListenerBridge) connection.originalListener())
                    .aerogel$removePlayerFromWorld();
                installHoldingListener(connection);
            }

            state = new RestartState(startedAt, List.copyOf(held));
            System.out.printf("[Aerogel] Restarting server; holding %d player connection(s).%n", held.size());
            InteractiveConsole.stop();
            server.halt(false);
            return true;
        } catch (RuntimeException exception) {
            for (HeldConnection connection : held) {
                FROZEN_CONNECTIONS.remove(connection.connection());
            }
            REQUESTED.set(false);
            state = null;
            System.err.println("[Aerogel] Could not prepare server restart: " + exception.getMessage());
            exception.printStackTrace(System.err);
            return false;
        }
    }

    public static void serverStopping() {
        RestartState current = state;
        if (current == null || !current.holderStarted().compareAndSet(false, true)) {
            return;
        }
        InteractiveConsole.stop();
        Thread holder = new Thread(() -> holdConnections(current), "Aerogel restart connection holder");
        holder.setDaemon(false);
        holder.start();
    }

    public static void serverStopped() {
        RestartState current = state;
        if (current == null) {
            return;
        }
        try {
            shutdownLogging();
            Properties request = new Properties();
            request.setProperty("startedAt", Long.toString(current.startedAt()));
            request.setProperty("players", current.connections().stream()
                .map(connection -> connection.uuid().toString())
                .reduce((left, right) -> left + "," + right)
                .orElse(""));
            request.setProperty("addresses", current.connections().stream()
                .map(HeldConnection::remoteHost)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse(""));
            writeProperties(requestFile(GENERATION), request);
            current.serverStopped().set(true);
        } catch (Exception exception) {
            current.failureMessage = exception.getMessage();
            current.serverStopped().set(true);
        }
    }

    public static void serverReady() {
        if (!available()) {
            return;
        }
        try {
            if (GENERATION > 0) {
                Properties request = readProperties(requestFile(GENERATION - 1));
                long startedAt = Long.parseLong(request.getProperty("startedAt"));
                completedSeconds = String.format(Locale.ROOT, "%.2f",
                    (System.currentTimeMillis() - startedAt) / 1_000.0D);
                readyAt = System.currentTimeMillis();
                System.out.printf("[Aerogel] Server restart completed in %s seconds.%n", completedSeconds);
                synchronized (RETURNING_PLAYERS) {
                    RETURNING_PLAYERS.clear();
                    String players = request.getProperty("players", "");
                    if (!players.isBlank()) {
                        for (String player : players.split(",")) {
                            RETURNING_PLAYERS.add(UUID.fromString(player));
                        }
                    }
                    RETURNING_ADDRESSES.clear();
                    String addresses = request.getProperty("addresses", "");
                    if (!addresses.isBlank()) {
                        for (String address : addresses.split(",")) {
                            RETURNING_ADDRESSES.add(address);
                        }
                    }
                }
            }
            writeString(readyFile(GENERATION), Long.toString(System.currentTimeMillis()));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot signal that the restarted server is ready", exception);
        }
    }

    public static void playerJoined(Object playerObject) {
        ServerPlayer player = (ServerPlayer) playerObject;
        String elapsed = completedSeconds;
        if (elapsed == null) {
            return;
        }
        UUID uuid = player.getUUID();
        synchronized (RETURNING_PLAYERS) {
            if (!RETURNING_PLAYERS.remove(uuid)) {
                return;
            }
        }
        send(player, COMPLETE_KEY, COMPLETE_FALLBACK, elapsed);
    }

    public static boolean acceptsRestartTransfer(Object connectionObject) {
        Connection connection = (Connection) connectionObject;
        long started = readyAt;
        if (GENERATION <= 0 || started == 0L
            || System.currentTimeMillis() - started > TRANSFER_ACCEPT_WINDOW_MILLIS) {
            return false;
        }
        String remote = remoteHost(connection);
        synchronized (RETURNING_PLAYERS) {
            return !RETURNING_PLAYERS.isEmpty()
                && (RETURNING_ADDRESSES.contains(remote) || RETURNING_ADDRESSES.contains("*"));
        }
    }

    public static boolean isReturningPlayer(Object playerObject) {
        ServerPlayer player = (ServerPlayer) playerObject;
        if (completedSeconds == null) {
            return false;
        }
        UUID uuid = player.getUUID();
        synchronized (RETURNING_PLAYERS) {
            return RETURNING_PLAYERS.contains(uuid);
        }
    }

    public static boolean suppressOutgoing(Object connection, Object packet) {
        if (!FROZEN_CONNECTIONS.contains(connection)) {
            return false;
        }
        String type = packet.getClass().getName();
        return !type.equals("net.minecraft.network.protocol.common.ClientboundKeepAlivePacket")
            && !type.equals("net.minecraft.network.protocol.common.ClientboundTransferPacket")
            && !type.equals("net.minecraft.network.protocol.common.ClientboundDisconnectPacket")
            && !type.equals("net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket");
    }

    public static boolean suppressInbound(Object listener) {
        return FROZEN_LISTENERS.contains(listener);
    }

    public static boolean suppressListenerTick(Object connection) {
        return FROZEN_CONNECTIONS.contains(connection);
    }

    private static void holdConnections(RestartState current) {
        long lastKeepAlive = 0L;
        long deadline = current.startedAt() + MAX_HOLD_MILLIS;
        try {
            while (!current.serverStopped().get()) {
                tickConnections(current.connections());
                sleep(50L);
            }
            if (current.failureMessage != null) {
                failConnections(current.connections());
                return;
            }

            Path release = releaseFile(GENERATION);
            Path failed = failedFile(GENERATION);
            while (!Files.isRegularFile(release) && !Files.isRegularFile(failed)
                && System.currentTimeMillis() < deadline) {
                tickConnections(current.connections());
                long now = System.currentTimeMillis();
                if (now - lastKeepAlive >= KEEP_ALIVE_INTERVAL_MILLIS) {
                    sendKeepAlive(current.connections(), now);
                    lastKeepAlive = now;
                }
                sleep(50L);
            }

            if (!Files.isRegularFile(release)) {
                failConnections(current.connections());
                return;
            }
            transferConnections(current.connections());
            long graceDeadline = System.currentTimeMillis() + HANDOFF_GRACE_MILLIS;
            while (System.currentTimeMillis() < graceDeadline) {
                tickConnections(current.connections());
                sleep(25L);
            }
        } catch (Throwable exception) {
            System.err.println("[Aerogel] Player handoff failed: " + exception.getMessage());
            failConnections(current.connections());
        } finally {
            System.exit(0);
        }
    }

    private static void installHoldingListener(HeldConnection held) {
        FROZEN_LISTENERS.add(held.originalListener());
    }

    private static void tickConnections(List<HeldConnection> connections) {
        for (HeldConnection held : connections) {
            if (held.connection().isConnected()) {
                held.connection().tick();
            }
        }
    }

    private static void sendKeepAlive(List<HeldConnection> connections, long id) {
        for (HeldConnection held : connections) {
            try {
                held.connection().send(new ClientboundKeepAlivePacket(id));
            } catch (RuntimeException exception) {
                System.err.println("[Aerogel] Could not keep a player connection alive: " + exception.getMessage());
            }
        }
    }

    private static void transferConnections(List<HeldConnection> connections) {
        for (HeldConnection held : connections) {
            if (held.address() == null) {
                disconnect(held, FAILED_KEY, FAILED_FALLBACK);
                continue;
            }
            held.connection().send(new ClientboundTransferPacket(
                held.address().host(), held.address().port()));
        }
    }

    private static void failConnections(List<HeldConnection> connections) {
        for (HeldConnection held : connections) {
            disconnect(held, FAILED_KEY, FAILED_FALLBACK);
        }
    }

    private static void disconnect(HeldConnection held, String key, String fallback) {
        try {
            held.connection().disconnect(component(held.language(), key, fallback));
        } catch (RuntimeException ignored) {
            // A connection that already left needs no further cleanup.
        }
    }

    private static void send(ServerPlayer player, String key, String fallback, Object... arguments) {
        player.sendSystemMessage(component(language(player), key, fallback, arguments));
    }

    private static Component component(
        String language, String key, String fallback, Object... arguments
    ) {
        String localized = CommandTranslations.fallback(language, key, fallback);
        return Component.translatableWithFallback(key, localized, arguments);
    }

    private static String language(ServerPlayer player) {
        String language = player.clientInformation().language();
        return language == null ? "en_us" : language;
    }

    private static String remoteHost(Connection connection) {
        try {
            SocketAddress address = connection.getRemoteAddress();
            if (address instanceof InetSocketAddress internet) {
                return internet.getAddress() == null
                    ? internet.getHostString()
                    : internet.getAddress().getHostAddress();
            }
            return address == null ? "*" : address.toString();
        } catch (RuntimeException ignored) {
            return "*";
        }
    }

    private static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void shutdownLogging() {
        try {
            LogManager.shutdown();
        } catch (RuntimeException exception) {
            System.err.println("[Aerogel] Could not release the old log files before restart: "
                + exception.getMessage());
        }
    }

    private static Path sessionDirectory() {
        String value = System.getProperty("aerogel.restartSession");
        return value == null || value.isBlank() ? null : Path.of(value).toAbsolutePath().normalize();
    }

    private static Path requestFile(int generation) {
        return SESSION_DIRECTORY.resolve("request-" + generation + ".properties");
    }

    private static Path readyFile(int generation) {
        return SESSION_DIRECTORY.resolve("ready-" + generation);
    }

    private static Path releaseFile(int generation) {
        return SESSION_DIRECTORY.resolve("release-" + generation);
    }

    private static Path failedFile(int generation) {
        return SESSION_DIRECTORY.resolve("failed-" + generation);
    }

    private static void writeString(Path target, String value) throws java.io.IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, value, StandardCharsets.UTF_8);
        moveAtomically(temporary, target);
    }

    private static void writeProperties(Path target, Properties properties) throws java.io.IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (java.io.Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            properties.store(writer, "Aerogel restart handoff");
        }
        moveAtomically(temporary, target);
    }

    private static Properties readProperties(Path target) throws java.io.IOException {
        Properties properties = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static void moveAtomically(Path source, Path target) throws java.io.IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record HeldConnection(
        Connection connection,
        ServerGamePacketListenerImpl originalListener,
        RestartAddressRegistry.Address address,
        UUID uuid,
        String language,
        String remoteHost
    ) {
    }

    private static final class RestartState {
        private final long startedAt;
        private final List<HeldConnection> connections;
        private final AtomicBoolean holderStarted = new AtomicBoolean();
        private final AtomicBoolean serverStopped = new AtomicBoolean();
        private volatile String failureMessage;

        private RestartState(long startedAt, List<HeldConnection> connections) {
            this.startedAt = startedAt;
            this.connections = connections;
        }

        private long startedAt() {
            return startedAt;
        }

        private List<HeldConnection> connections() {
            return connections;
        }

        private AtomicBoolean holderStarted() {
            return holderStarted;
        }

        private AtomicBoolean serverStopped() {
            return serverStopped;
        }
    }
}
