package dev.aerogel.gradle;

import dev.aerogel.api.AerogelPlugin;
import dev.aerogel.api.mixin.MixinDefinition;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.jetbrains.kotlin.gradle.dsl.JvmTarget;
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Locale;

public final class AerogelGradlePlugin implements Plugin<Project> {
    private static final String MIXIN_VERSION = "0.17.3+mixin.0.8.7";
    private static final String JETBRAINS_ANNOTATIONS_VERSION = "26.1.0";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply("org.jetbrains.kotlin.jvm");
        AerogelExtension extension = project.getExtensions().create(
            "aerogel", AerogelExtension.class, project.getObjects());
        extension.getMinecraft().convention("26.2");
        extension.getPlugin().getVersion().convention(project.provider(() -> project.getVersion().toString()));
        extension.getPlugin().getName().convention(extension.getPlugin().getId());
        extension.getPlugin().getEntrypoints().convention(java.util.List.of());
        extension.getPlugin().getMixins().convention(java.util.List.of());
        extension.getPlugin().getDepends().convention(java.util.Map.of());

        configureRepositories(project);
        configureJava(project);

        boolean ideaProject = project.file(".idea").isDirectory();
        TaskProvider<ConfigureAerogelIdea> idea = project.getTasks().register(
            "configureAerogelIdea", ConfigureAerogelIdea.class, task -> {
                task.setGroup("aerogel");
                task.setDescription("Registers Aerogel plugin and event entry points with IntelliJ IDEA.");
                task.getEntrypoints().set(extension.getPlugin().getEntrypoints());
                task.getProjectFile().set(
                    project.getLayout().getProjectDirectory().file(".idea/misc.xml"));
                task.onlyIf(ignored -> ideaProject);
            });

        TaskProvider<SetupAerogelDevelopment> setup = project.getTasks().register(
            "setupAerogelDevelopment", SetupAerogelDevelopment.class, task -> {
                task.setGroup("aerogel");
                task.setDescription("Downloads, verifies, and extracts the official Minecraft development classpath.");
                task.getMinecraftVersion().set(extension.getMinecraft());
                task.getDevelopmentPatchFingerprint().set(DevelopmentJarPatcher.fingerprint());
                task.getServerJar().set(extension.getMinecraftServerJar());
                task.getOutputDirectory().set(project.getLayout().dir(project.provider(() -> {
                    String version = safePath(extension.getMinecraft().get());
                    if (extension.getMinecraftServerJar().isPresent()) {
                        return project.getLayout().getBuildDirectory().get()
                            .dir("aerogel/minecraft/" + version).getAsFile();
                    }
                    return new File(project.getGradle().getGradleUserHomeDir(),
                        "caches/aerogel/minecraft/" + version);
                })));
            });
        setup.configure(task -> task.dependsOn(idea));

        TaskProvider<GenerateAerogelMetadata> metadata = project.getTasks().register(
            "generateAerogelPluginMetadata", GenerateAerogelMetadata.class, task -> {
                task.setGroup("aerogel");
                task.setDescription("Generates aerogel.plugin.json from the Aerogel Gradle DSL.");
                PluginMetadata plugin = extension.getPlugin();
                task.getPluginId().set(plugin.getId());
                task.getDisplayName().set(plugin.getName());
                task.getPluginVersion().set(plugin.getVersion());
                task.getMinecraftRequirement().set(extension.getMinecraft().map(version -> ">=" + version));
                task.getEntrypoints().set(plugin.getEntrypoints());
                task.getMixins().set(plugin.getMixins());
                task.getDepends().set(plugin.getDepends());
                task.getOutputDirectory().set(project.getLayout().getBuildDirectory()
                    .dir("generated/aerogel/resources"));
            });

        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        ConfigurableFileTree mixinScripts = project.fileTree("src/main/mixins", files ->
            files.include("**/*.mixin.kts"));
        TaskProvider<GenerateAerogelMixinSources> mixinSources = project.getTasks().register(
            "generateAerogelMixinSources", GenerateAerogelMixinSources.class, task -> {
                task.setGroup("aerogel");
                task.setDescription("Generates Kotlin compilation units from string-free Aerogel Mixin scripts.");
                task.getScripts().from(mixinScripts);
                task.getPluginId().set(extension.getPlugin().getId());
                task.getOutputDirectory().set(project.getLayout().getBuildDirectory()
                    .dir("generated/aerogel/mixin-kotlin"));
                task.getIndexFile().set(project.getLayout().getBuildDirectory()
                    .file("generated/aerogel/mixin-index.tsv"));
            });
        sourceSets.getByName("main").getJava().srcDir(
            mixinSources.flatMap(GenerateAerogelMixinSources::getOutputDirectory));

        TaskProvider<GenerateAerogelMixinConfig> mixinConfig = project.getTasks().register(
            "generateAerogelMixinConfig", GenerateAerogelMixinConfig.class, task -> {
                task.setGroup("aerogel");
                task.setDescription("Generates the standard Sponge Mixin configuration for Kotlin DSL Mixins.");
                task.dependsOn(mixinSources);
                task.getPluginId().set(extension.getPlugin().getId());
                task.getIndexFile().set(mixinSources.flatMap(GenerateAerogelMixinSources::getIndexFile));
                task.getOutputDirectory().set(project.getLayout().getBuildDirectory()
                    .dir("generated/aerogel/mixin-resources"));
            });
        sourceSets.getByName("main").getResources().srcDir(
            mixinConfig.flatMap(GenerateAerogelMixinConfig::getOutputDirectory));
        metadata.configure(task -> {
            task.dependsOn(mixinSources);
            task.getGeneratedMixinIndex().set(mixinSources.flatMap(GenerateAerogelMixinSources::getIndexFile));
            task.getGeneratedMixinConfiguration().set(extension.getPlugin().getId()
                .map(id -> id + ".generated.mixins.json"));
        });

        TaskProvider<GenerateAerogelMixinBytecode> mixinBytecode = project.getTasks().register(
            "compileAerogelMixins", GenerateAerogelMixinBytecode.class, task -> {
                task.setGroup("aerogel");
                task.setDescription("Compiles Kotlin Mixin definitions to standard Sponge Mixin bytecode.");
                task.dependsOn("compileKotlin", "compileJava");
                task.getIndexFile().set(mixinSources.flatMap(GenerateAerogelMixinSources::getIndexFile));
                task.getCompilationClasspath().from(
                    project.getLayout().getBuildDirectory().dir("classes/kotlin/main"),
                    project.getLayout().getBuildDirectory().dir("classes/java/main"),
                    project.getConfigurations().getByName("compileClasspath")
                );
                task.getOutputDirectory().set(project.getLayout().getBuildDirectory()
                    .dir("generated/aerogel/mixin-classes"));
            });
        sourceSets.getByName("main").getOutput().dir(
            java.util.Map.of("builtBy", mixinBytecode),
            mixinBytecode.flatMap(GenerateAerogelMixinBytecode::getOutputDirectory));

        sourceSets.getByName("main").getResources().srcDir(metadata.flatMap(GenerateAerogelMetadata::getOutputDirectory));
        project.getTasks().named("processResources").configure(task -> task.dependsOn(metadata, mixinConfig));

        project.afterEvaluate(ignored -> {
            ConfigurableFileTree minecraft = project.fileTree(
                setup.get().getOutputDirectory().get().dir("classpath"));
            minecraft.include("**/*.jar");
            minecraft.builtBy(setup);
            project.getDependencies().add("compileOnly", minecraft);
        });
        project.getDependencies().add("compileOnly", project.files(apiLocation()));
        project.getDependencies().add("compileOnly", project.files(mixinDslLocation()));
        if (project.getConfigurations().findByName("kotlinScriptDef") != null) {
            project.getDependencies().add("kotlinScriptDef", project.files(mixinDslLocation()));
        }
        project.getDependencies().add("compileOnly", "net.fabricmc:sponge-mixin:" + MIXIN_VERSION);
        project.getDependencies().add("compileOnly",
            "org.jetbrains:annotations:" + JETBRAINS_ANNOTATIONS_VERSION);

        project.getTasks().withType(JavaCompile.class).configureEach(task -> task.dependsOn(setup));
        project.getTasks().withType(KotlinJvmCompile.class).configureEach(task -> {
            task.dependsOn(setup, mixinSources);
            task.getCompilerOptions().getJvmTarget().set(JvmTarget.JVM_25);
            task.getCompilerOptions().getJavaParameters().set(true);
        });
        project.getTasks().named("classes").configure(task -> task.dependsOn(mixinBytecode));

        TaskProvider<Jar> jar = project.getTasks().named("jar", Jar.class);
        TaskProvider<ValidateAerogelPluginJar> validate = project.getTasks().register(
            "validateAerogelPluginJar", ValidateAerogelPluginJar.class, task -> {
                task.setGroup("verification");
                task.setDescription("Rejects Minecraft, Mixin, and Aerogel API classes bundled into the plugin JAR.");
                task.dependsOn(jar);
                task.getPluginJar().set(jar.flatMap(Jar::getArchiveFile));
            });
        project.getTasks().named("check").configure(task -> task.dependsOn(validate));
    }

    private static void configureRepositories(Project project) {
        project.getRepositories().mavenCentral();
        project.getRepositories().maven(repository -> {
            repository.setName("AerogelMixin");
            repository.setUrl("https://maven.fabricmc.net/");
            repository.content(content -> content.includeGroup("net.fabricmc"));
        });
    }

    private static void configureJava(Project project) {
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(25));
        java.withSourcesJar();
        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            task.getOptions().getRelease().set(25);
            task.getOptions().setEncoding("UTF-8");
            task.getOptions().getCompilerArgs().add("-Xlint:all");
        });
    }

    private static File apiLocation() {
        return codeLocation(AerogelPlugin.class, "Aerogel API");
    }

    private static File mixinDslLocation() {
        return codeLocation(MixinDefinition.class, "Aerogel Mixin DSL");
    }

    private static File codeLocation(Class<?> type, String label) {
        try {
            return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Cannot locate " + label + " used by the Gradle plugin", exception);
        }
    }

    private static String safePath(String version) {
        String safe = version.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) {
            throw new IllegalArgumentException("Unsafe Minecraft version path: " + version);
        }
        return safe;
    }
}
