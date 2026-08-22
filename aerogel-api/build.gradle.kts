plugins {
    `java-library`
    `maven-publish`
}

val minecraftStubs by sourceSets.creating
val nettyVersion: String by project

dependencies {
    compileOnly(minecraftStubs.output)
    add(minecraftStubs.compileOnlyConfigurationName,
        "io.netty:netty-buffer:$nettyVersion")
    add(minecraftStubs.compileOnlyConfigurationName,
        "io.netty:netty-codec-base:$nettyVersion")
    add(minecraftStubs.compileOnlyConfigurationName,
        "io.netty:netty-transport:$nettyVersion")
}

tasks.compileJava {
    dependsOn(minecraftStubs.classesTaskName)
}

tasks.jar {
    archiveBaseName.set("aerogel-api")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "aerogel-api"
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
