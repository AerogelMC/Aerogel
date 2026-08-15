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

val publishAerogelGradleRepository by tasks.registering {
    group = "distribution"
    description = "Publishes the Aerogel API and Gradle plugin marker to a local Maven repository."
    dependsOn(":aerogel-api:publishMavenJavaPublicationToAerogelBuildRepository")
    dependsOn(":aerogel-mixin-dsl:publishMavenJavaPublicationToAerogelBuildRepository")
    dependsOn(":aerogel-gradle-plugin:publishAllPublicationsToAerogelBuildRepository")
}

tasks.register<Zip>("aerogelGradleRepositoryZip") {
    group = "distribution"
    description = "Packages a directly usable Maven repository for Aerogel plugin development."
    dependsOn(publishAerogelGradleRepository)
    archiveFileName.set("aerogel-gradle-plugin-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(layout.buildDirectory.dir("aerogel-maven"))
    from("docs/GRADLE_PLUGIN.md") {
        rename { "README.md" }
    }
}
