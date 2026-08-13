plugins {
    base
}

allprojects {
    group = property("group") as String
    version = property("version") as String

    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        mavenCentral()
    }
}

subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(25))
            withSourcesJar()
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release.set(25)
            options.encoding = "UTF-8"
            options.compilerArgs.add("-Xlint:all")
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}

tasks.register<Copy>("collectExamplePlugin") {
    dependsOn(":example-plugin:jar")
    from(project(":example-plugin").tasks.named("jar"))
    into(layout.buildDirectory.dir("example"))
}
