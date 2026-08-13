plugins {
    application
    `java-library`
}

evaluationDependsOn(":example-plugin")

val mixinVersion: String by project
val asmVersion: String by project
val gsonVersion: String by project
val jlineVersion: String by project
val junitVersion: String by project

dependencies {
    implementation(project(":aerogel-api"))
    implementation("net.fabricmc:sponge-mixin:$mixinVersion")
    implementation("org.ow2.asm:asm:$asmVersion")
    implementation("org.ow2.asm:asm-tree:$asmVersion")
    implementation("org.ow2.asm:asm-util:$asmVersion")
    implementation("com.google.code.gson:gson:$gsonVersion")
    implementation("org.jline:jline:$jlineVersion")
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("dev.aerogel.loader.AerogelMain")
    applicationName = "aerogel"
}

tasks.processResources {
    val projectVersion = project.version.toString()
    val minecraftVersion: String by project
    val mixinVersion: String by project
    inputs.property("version", projectVersion)
    inputs.property("minecraftVersion", minecraftVersion)
    inputs.property("mixinVersion", mixinVersion)
    filesMatching("aerogel-build.properties") {
        expand(
            "version" to projectVersion,
            "minecraftVersion" to minecraftVersion,
            "mixinVersion" to mixinVersion
        )
    }
}

tasks.jar {
    archiveBaseName.set("aerogel-loader")
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Title"] = "Aerogel Loader"
        attributes["Implementation-Version"] = project.version
    }
}

val standaloneJar by tasks.registering(Jar::class) {
    group = "distribution"
    description = "Builds a standalone executable server JAR."
    archiveBaseName.set("Aerogel")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Title"] = "Aerogel Loader"
        attributes["Implementation-Version"] = project.version
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { artifact ->
            if (artifact.isDirectory) artifact else zipTree(artifact)
        }
    })

    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/*.EC")

    from(rootProject.file("LICENSE")) {
        into("META-INF/licenses/aerogel")
        rename { "LICENSE.txt" }
    }
    from(rootProject.file("THIRD_PARTY_NOTICES.md")) {
        into("META-INF/licenses/aerogel")
    }
    from(rootProject.file("THIRD_PARTY_LICENSES")) {
        into("META-INF/licenses/aerogel/third-party")
    }
}

tasks.assemble {
    dependsOn(standaloneJar)
}

distributions {
    main {
        distributionBaseName.set("aerogel")
        contents {
            from(rootProject.file("README.md"))
            from(rootProject.file("LICENSE"))
            from(rootProject.file("THIRD_PARTY_NOTICES.md"))
            from(rootProject.file("THIRD_PARTY_LICENSES")) {
                into("THIRD_PARTY_LICENSES")
            }
            from(rootProject.file("docs")) {
                into("docs")
            }
            from(project(":example-plugin").tasks.named("jar")) {
                into("examples")
            }
        }
    }
}
