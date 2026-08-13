package dev.aerogel.loader.install;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aerogel.loader.util.Hashing;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;

public final class MinecraftInstaller {
    public static final URI VERSION_MANIFEST = URI.create(
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    );

    private final HttpClient httpClient;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public MinecraftInstaller() {
        this(HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build());
    }

    MinecraftInstaller(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Installation install(String version, Path serverJar, Path gameDirectory) throws IOException, InterruptedException {
        requireSupportedVersion(version);
        JsonObject manifest = getJson(VERSION_MANIFEST);
        JsonObject selected = findVersion(manifest.getAsJsonArray("versions"), version);
        URI metadataUri = requireOfficialUri(selected.get("url").getAsString());
        JsonObject metadata = getJson(metadataUri);
        JsonObject server = metadata.getAsJsonObject("downloads").getAsJsonObject("server");
        URI serverUri = requireOfficialUri(server.get("url").getAsString());
        String expectedSha1 = server.get("sha1").getAsString().toLowerCase(Locale.ROOT);
        long expectedSize = server.get("size").getAsLong();

        Files.createDirectories(serverJar.getParent());
        Path temporary = Files.createTempFile(serverJar.getParent(), "server-", ".download");
        boolean complete = false;
        try {
            download(serverUri, temporary);
            long actualSize = Files.size(temporary);
            String actualSha1 = Hashing.sha1(temporary);
            if (actualSize != expectedSize || !actualSha1.equals(expectedSha1)) {
                throw new IOException(
                    "Minecraft server integrity check failed: expected " + expectedSha1 + "/" + expectedSize
                        + ", got " + actualSha1 + "/" + actualSize
                );
            }
            moveReplacing(temporary, serverJar);
            complete = true;
        } finally {
            if (!complete) {
                Files.deleteIfExists(temporary);
            }
        }

        JsonObject receipt = new JsonObject();
        receipt.addProperty("version", version);
        receipt.addProperty("source", serverUri.toString());
        receipt.addProperty("sha1", expectedSha1);
        receipt.addProperty("size", expectedSize);
        receipt.addProperty("serverJar", serverJar.toString());
        Path receiptPath = gameDirectory.resolve(".aerogel").resolve("versions").resolve(version + ".json");
        Files.createDirectories(receiptPath.getParent());
        Files.writeString(receiptPath, gson.toJson(receipt) + System.lineSeparator(), StandardCharsets.UTF_8);
        return new Installation(version, serverJar, serverUri, expectedSha1, expectedSize);
    }

    public static void acceptEula(Path gameDirectory) throws IOException {
        Path eula = gameDirectory.resolve("eula.txt");
        if (Files.exists(eula)) {
            String current = Files.readString(eula, StandardCharsets.UTF_8);
            if (current.lines().anyMatch(line -> line.strip().equalsIgnoreCase("eula=true"))) {
                return;
            }
        }
        String content = "# Accepted explicitly through Aerogel --accept-minecraft-eula" + System.lineSeparator()
            + "# https://aka.ms/MinecraftEULA" + System.lineSeparator()
            + "eula=true" + System.lineSeparator();
        Files.writeString(eula, content, StandardCharsets.UTF_8);
    }

    public static void requireSupportedVersion(String version) {
        String[] pieces = version.split("\\.", 3);
        try {
            int major = Integer.parseInt(pieces[0]);
            int minor = pieces.length > 1 ? Integer.parseInt(pieces[1]) : 0;
            if (major < 26 || (major == 26 && minor < 2)) {
                throw new IllegalArgumentException("Aerogel supports Minecraft 26.2 and newer, got " + version);
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("A release version such as 26.2 is required, got " + version, exception);
        }
    }

    private JsonObject getJson(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "Aerogel-Loader/0.1")
            .GET()
            .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Official metadata request failed with HTTP " + response.statusCode() + ": " + uri);
        }
        try (InputStream input = response.body(); Reader reader = new java.io.InputStreamReader(input, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private void download(URI uri, Path destination) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMinutes(5))
            .header("User-Agent", "Aerogel-Loader/0.1")
            .GET()
            .build();
        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(destination));
        if (response.statusCode() != 200) {
            throw new IOException("Official server download failed with HTTP " + response.statusCode() + ": " + uri);
        }
    }

    private static JsonObject findVersion(JsonArray versions, String requested) {
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            if (requested.equals(version.get("id").getAsString()) && "release".equals(version.get("type").getAsString())) {
                return version;
            }
        }
        throw new IllegalArgumentException("Official release not found in Mojang manifest: " + requested);
    }

    private static URI requireOfficialUri(String raw) {
        URI uri = URI.create(raw);
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
            || !(host.equals("mojang.com") || host.endsWith(".mojang.com"))) {
            throw new IllegalArgumentException("Refusing non-Mojang download URL: " + uri);
        }
        return uri;
    }

    private static void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record Installation(String version, Path serverJar, URI source, String sha1, long size) {
    }
}
