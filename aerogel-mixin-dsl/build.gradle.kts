plugins {
    kotlin("jvm") version "2.4.10"
    `java-library`
    `maven-publish`
}

val mixinVersion: String by project

dependencies {
    api(kotlin("stdlib"))
    implementation(kotlin("scripting-common"))
    implementation(kotlin("scripting-jvm"))
    compileOnly("net.fabricmc:sponge-mixin:$mixinVersion")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        javaParameters.set(true)
    }
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "aerogel-mixin-dsl"
        }
    }
    repositories {
        maven {
            name = "AerogelBuild"
            url = providers.gradleProperty("aerogelMavenRepository")
                .map { uri(it) }
                .orElse(rootProject.layout.buildDirectory.dir("aerogel-maven").map { it.asFile.toURI() })
                .get()
        }
    }
}
