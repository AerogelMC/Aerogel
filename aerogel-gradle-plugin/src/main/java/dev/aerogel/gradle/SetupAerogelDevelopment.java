package dev.aerogel.gradle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public abstract class SetupAerogelDevelopment extends DefaultTask {
    private static final URI VERSION_MANIFEST = URI.create(
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Optional
    @InputFile
    public abstract RegularFileProperty getServerJar();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void setup() {
        String version = getMinecraftVersion().get();
        requireSupportedVersion(version);
        Path output = getOutputDirectory().get().getAsFile().toPath();
        Path bundler = getServerJar().isPresent()
            ? getServerJar().get().getAsFile().toPath()
            : output.resolve("downloads").resolve("server-" + version + ".jar");
        try {
            Files.createDirectories(output);
            if (!getServerJar().isPresent()) {
                installOfficialServer(version, bundler);
            } else {
                getLogger().lifecycle("[Aerogel] Using Minecraft server JAR: {}", bundler);
            }
            Path classpath = output.resolve("classpath");
            deleteTree(classpath);
            extractBundle(bundler, classpath);
            Files.writeString(output.resolve("ready.txt"),
                "minecraft=" + version + System.lineSeparator()
                    + "serverSha1=" + Hashing.sha1(bundler) + System.lineSeparator(),
                StandardCharsets.UTF_8);
            getLogger().lifecycle("[Aerogel] Minecraft {} development classpath is ready.", version);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new GradleException("Cannot prepare Minecraft " + version + " for Aerogel development", exception);
        }
    }

    private void installOfficialServer(String version, Path destination) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
        getLogger().lifecycle("[Aerogel] Checking official Minecraft metadata for {}...", version);
        JsonObject manifest = getJson(client, VERSION_MANIFEST);
        JsonObject selected = findVersion(manifest.getAsJsonArray("versions"), version);
        JsonObject metadata = getJson(client, requireOfficialUri(selected.get("url").getAsString()));
        JsonObject server = metadata.getAsJsonObject("downloads").getAsJsonObject("server");
        URI uri = requireOfficialUri(server.get("url").getAsString());
        String expectedSha1 = server.get("sha1").getAsString().toLowerCase(Locale.ROOT);
        long expectedSize = server.get("size").getAsLong();
        if (Files.isRegularFile(destination)
            && Files.size(destination) == expectedSize
            && Hashing.sha1(destination).equals(expectedSha1)) {
            getLogger().lifecycle("[Aerogel] Reusing verified Minecraft {} server JAR.", version);
            return;
        }
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), "server-", ".download");
        boolean complete = false;
        try {
            download(client, uri, temporary, expectedSize);
            long actualSize = Files.size(temporary);
            String actualSha1 = Hashing.sha1(temporary);
            if (actualSize != expectedSize || !actualSha1.equals(expectedSha1)) {
                throw new IOException("Minecraft server integrity check failed: expected "
                    + expectedSha1 + "/" + expectedSize + ", got " + actualSha1 + "/" + actualSize);
            }
            moveReplacing(temporary, destination);
            complete = true;
        } finally {
            if (!complete) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private JsonObject getJson(HttpClient client, URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "Aerogel-Gradle/26.2-1")
            .GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Official metadata request failed with HTTP " + response.statusCode() + ": " + uri);
        }
        try (InputStream input = response.body();
             Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private void download(HttpClient client, URI uri, Path destination, long expectedSize)
        throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMinutes(5))
            .header("User-Agent", "Aerogel-Gradle/26.2-1")
            .GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Official server download failed with HTTP " + response.statusCode() + ": " + uri);
        }
        try (InputStream input = response.body();
             OutputStream output = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            long transferred = 0;
            int lastPercent = -10;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                output.write(buffer, 0, read);
                transferred += read;
                int percent = expectedSize <= 0 ? 0 : (int) Math.min(100, transferred * 100 / expectedSize);
                if (percent >= lastPercent + 10 || transferred >= expectedSize) {
                    getLogger().lifecycle("[Aerogel] Downloading server.jar: {}% ({} / {})",
                        percent, formatBytes(transferred), formatBytes(expectedSize));
                    lastPercent = percent;
                }
            }
        }
    }

    private static void extractBundle(Path bundler, Path output) throws IOException {
        try (JarFile jar = new JarFile(bundler.toFile(), false)) {
            JarEntry versions = jar.getJarEntry("META-INF/versions.list");
            JarEntry libraries = jar.getJarEntry("META-INF/libraries.list");
            JarEntry mainClass = jar.getJarEntry("META-INF/main-class");
            if (versions == null || libraries == null || mainClass == null) {
                throw new IOException("Unsupported server JAR: Mojang server-bundler indexes are missing");
            }
            extractList(jar, versions, "META-INF/versions/", output.resolve("versions"));
            extractList(jar, libraries, "META-INF/libraries/", output.resolve("libraries"));
        }
    }

    private static void extractList(JarFile jar, JarEntry index, String prefix, Path root) throws IOException {
        Files.createDirectories(root);
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(jar.getInputStream(index), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\\t");
                if (parts.length != 3 || !parts[0].matches("[0-9a-fA-F]{64}")) {
                    throw new IOException("Malformed Mojang bundle index line: " + line);
                }
                String relative = parts[2].replace('\\', '/');
                Path destination = root.resolve(relative).normalize();
                if (!destination.startsWith(root.normalize()) || relative.startsWith("/")) {
                    throw new IOException("Unsafe path in Mojang bundle index: " + relative);
                }
                if (!Files.isRegularFile(destination)
                    || !Hashing.sha256(destination).equalsIgnoreCase(parts[0])) {
                    JarEntry artifact = jar.getJarEntry(prefix + relative);
                    if (artifact == null || artifact.isDirectory()) {
                        throw new IOException("Bundled artifact is missing: " + relative);
                    }
                    Files.createDirectories(destination.getParent());
                    Path temporary = Files.createTempFile(destination.getParent(), "artifact-", ".tmp");
                    boolean complete = false;
                    try (InputStream input = jar.getInputStream(artifact)) {
                        Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                        if (!Hashing.sha256(temporary).equalsIgnoreCase(parts[0])) {
                            throw new IOException("Bundled artifact failed SHA-256 verification: " + relative);
                        }
                        moveReplacing(temporary, destination);
                        complete = true;
                    } finally {
                        if (!complete) {
                            Files.deleteIfExists(temporary);
                        }
                    }
                }
            }
        }
    }

    private static JsonObject findVersion(JsonArray versions, String requested) {
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            if (requested.equals(version.get("id").getAsString())
                && "release".equals(version.get("type").getAsString())) {
                return version;
            }
        }
        throw new GradleException("Official Minecraft release not found: " + requested);
    }

    private static URI requireOfficialUri(String raw) {
        URI uri = URI.create(raw);
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
            || !(host.equals("mojang.com") || host.endsWith(".mojang.com"))) {
            throw new GradleException("Refusing non-Mojang download URL: " + uri);
        }
        return uri;
    }

    private static void requireSupportedVersion(String version) {
        String[] pieces = version.split("\\.", 3);
        try {
            int major = Integer.parseInt(pieces[0]);
            int minor = pieces.length > 1 ? Integer.parseInt(pieces[1]) : 0;
            if (major < 26 || (major == 26 && minor < 2)) {
                throw new GradleException("Aerogel supports Minecraft 26.2 and newer, got " + version);
            }
        } catch (NumberFormatException exception) {
            throw new GradleException("A release version such as 26.2 is required, got " + version, exception);
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KiB", "MiB", "GiB", "TiB"};
        double value = bytes;
        int unit = -1;
        do {
            value /= 1024.0;
            unit++;
        } while (value >= 1024 && unit < units.length - 1);
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    private static void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path target) throws IOException {
        if (!Files.exists(target)) {
            return;
        }
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
