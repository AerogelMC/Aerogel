# Aerogel Gradle Plugin

The Aerogel Gradle plugin prepares the official Minecraft server class path, adds Aerogel API and Mixin as compile-only dependencies, configures Kotlin 2.4.10, and generates plugin metadata.

## Public repository

Aerogel publishes the Gradle plugin, plugin marker, API, and Mixin DSL to a public Maven
repository. No account or access token is required:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven("https://raw.githubusercontent.com/AerogelMC/Aerogel/maven/")
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Then apply `id("dev.aerogel.plugin") version "26.2-5"` normally. The `maven` branch is
updated automatically after changes reach `main` and retains previously published versions.

## Local repository package

For offline development or a pinned local mirror, extract `aerogel-gradle-plugin-26.2-5.zip` and add that directory to `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        maven { url = uri("path/to/extracted/aerogel-gradle-plugin-26.2-5") }
        mavenCentral()
        gradlePluginPortal()
    }
}
```

The extracted directory is a Maven repository containing the plugin marker, implementation, sources, and Aerogel API.

## Project setup

```kotlin
plugins {
    id("dev.aerogel.plugin") version "26.2-5"
}

group = "com.example"
version = "1.0.0"

aerogel {
    minecraft.set("26.2")

    plugin {
        id.set("example_plugin")
        name.set("Example Plugin")
        entrypoint("com.example.ExamplePlugin")
        mixin("example.mixins.json")
        dependsOn("shared_api", ">=1.0.0")
    }
}
```

`version` is read from the Gradle project. `entrypoint` and `mixin` are optional. A plugin containing only automatically discovered `@EventHandler` methods can omit both.

The project must use JDK 25. The plugin configures Java release 25 automatically.

## What happens during a build

`setupAerogelDevelopment` downloads the official server JAR from Mojang, verifies its published SHA-1, extracts the bundler class path, and verifies each embedded artifact against Mojang's SHA-256 index. The files remain in the Gradle user cache and are attached only to `compileOnly`.

If an official server JAR already exists locally, it can be selected without another download:

```kotlin
aerogel {
    minecraftServerJar.set(layout.projectDirectory.file("server.jar"))
}
```

The selected JAR must use Mojang's server-bundler format. Its embedded artifacts are still checked against the bundle index.

`generateAerogelPluginMetadata` writes `aerogel.plugin.json` from the DSL. Do not keep another copy in `src/main/resources`.

String-free Kotlin Mixins belong in `src/main/mixins/*.mixin.kts`. The plugin compiles them into standard Mixin bytecode and generates their Mixin JSON automatically; do not add generated configurations to `plugin.mixins`. See the [Mixin guide](MIXINS.md).

`validateAerogelPluginJar` runs as part of `check` and rejects plugin JARs containing Minecraft, Sponge Mixin, or Aerogel API classes. Those components must remain compile-only.

```shell
./gradlew build
```

The resulting plugin JAR is in `build/libs`.

## IDE use

Run the following once after importing or after changing the Minecraft version:

```shell
./gradlew setupAerogelDevelopment
```

Refresh the Gradle project in the IDE. Vanilla types can then be imported directly:

```java
@EventHandler
private void onJoin(PlayerJoinEvent event) {
    ServerPlayer player = event.player();
    player.sendSystemMessage(Component.literal("Hello"));
}
```

For IntelliJ IDEA projects, setup also merges Aerogel entry points into
`.idea/misc.xml`. Classes declared with `plugin.entrypoint(...)` and methods
annotated with `@EventHandler` are then treated as reflectively used instead of
being reported as unused. Run `./gradlew configureAerogelIdea` to refresh only
this IDE metadata.

The IDE may display decompiled Minecraft classes for navigation. Minecraft code is never copied into the built plugin JAR.
