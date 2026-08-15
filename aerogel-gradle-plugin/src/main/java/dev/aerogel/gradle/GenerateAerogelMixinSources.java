package dev.aerogel.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts the script-shaped Kotlin DSL into ordinary generated Kotlin compilation units. */
public abstract class GenerateAerogelMixinSources extends DefaultTask {
    private static final Pattern IMPORT = Pattern.compile("(?m)^[\\t ]*import[\\t ]+[^\\r\\n]+[\\r\\n]*");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^[\\t ]*package(?:[\\t ]|$)");
    private static final Pattern NAME = Pattern.compile("[A-Z][A-Za-z0-9_]*");

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getScripts();

    @Input
    public abstract Property<String> getPluginId();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @OutputFile
    public abstract RegularFileProperty getIndexFile();

    @TaskAction
    public void generate() {
        String mixinPackage = generatedPackage(getPluginId().get());
        String definitionPackage = definitionPackage(getPluginId().get());
        Path output = getOutputDirectory().get().getAsFile().toPath();
        Path index = getIndexFile().get().getAsFile().toPath();
        try {
            cleanGeneratedFiles(output);
            Files.createDirectories(output);
            Files.createDirectories(index.getParent());

            List<Path> scripts = getScripts().getFiles().stream()
                .map(file -> file.toPath().toAbsolutePath().normalize())
                .sorted(Comparator.comparing(Path::toString))
                .toList();
            List<String> entries = new ArrayList<>();
            Set<String> names = new HashSet<>();
            for (Path script : scripts) {
                String file = script.getFileName().toString();
                if (!file.endsWith(".mixin.kts")) continue;
                String mixinName = file.substring(0, file.length() - ".mixin.kts".length());
                if (!NAME.matcher(mixinName).matches()) {
                    throw new GradleException("Invalid Aerogel Mixin script name '" + file
                        + "'; use an upper-camel Kotlin identifier such as ServerTick.mixin.kts");
                }
                if (!names.add(mixinName)) {
                    throw new GradleException("Duplicate Aerogel Mixin script name: " + mixinName);
                }
                writeSource(script, output, definitionPackage, mixinName);
                entries.add(mixinName + "\t" + definitionPackage + "." + mixinName
                    + "AerogelDefinition\t" + mixinPackage);
            }
            Files.writeString(index, String.join(System.lineSeparator(), entries)
                + (entries.isEmpty() ? "" : System.lineSeparator()), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new GradleException("Cannot generate Aerogel Kotlin Mixin sources", exception);
        }
    }

    private static void writeSource(
        Path script,
        Path output,
        String generatedPackage,
        String mixinName
    ) throws IOException {
        String source = Files.readString(script, StandardCharsets.UTF_8);
        if (!source.isEmpty() && source.charAt(0) == '\ufeff') source = source.substring(1);
        if (PACKAGE.matcher(source).find()) {
            throw new GradleException(script + " must not declare a package; its generated package is managed by Aerogel");
        }

        StringBuilder imports = new StringBuilder();
        Matcher matcher = IMPORT.matcher(source);
        while (matcher.find()) imports.append(matcher.group());
        String expression = matcher.replaceAll("").strip();
        if (expression.isEmpty()) {
            throw new GradleException(script + " does not contain a mixin<T> declaration");
        }

        String generated = "package " + generatedPackage + "\n\n"
            + imports + "\n"
            + "import dev.aerogel.api.mixin.MixinDefinition\n\n"
            + "internal object " + mixinName + "AerogelDefinition {\n"
            + "    @JvmField\n"
            + "    val definition: MixinDefinition<*> = dev.aerogel.api.mixin.withMixinIdentity(\""
            + generatedPackage + "." + mixinName + "\") {\n"
            + indent(expression, 8) + "\n"
            + "    }\n"
            + "}\n";
        Files.writeString(output.resolve(mixinName + "AerogelDefinition.kt"), generated, StandardCharsets.UTF_8);
    }

    private static String indent(String value, int spaces) {
        String prefix = " ".repeat(spaces);
        return prefix + value.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\n" + prefix);
    }

    static String generatedPackage(String pluginId) {
        return "dev.aerogel.generated." + pluginId.replace('-', '_') + ".mixin";
    }

    private static String definitionPackage(String pluginId) {
        return "dev.aerogel.generated." + pluginId.replace('-', '_') + ".mixinimpl";
    }

    private static void cleanGeneratedFiles(Path output) throws IOException {
        if (!Files.isDirectory(output)) return;
        try (var paths = Files.walk(output)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(output)) Files.deleteIfExists(path);
            }
        }
    }
}
