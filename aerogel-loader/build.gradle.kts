plugins {
    application
    `java-library`
}

val mixinVersion: String by project
val minecraftVersion: String by project
val asmVersion: String by project
val gsonVersion: String by project
val jlineVersion: String by project
val junitVersion: String by project
val aerogelApiProject = project(":aerogel-api")
val apiMinecraftStubs = aerogelApiProject.extensions
    .getByType<SourceSetContainer>()["minecraftStubs"]

dependencies {
    implementation(project(":aerogel-api"))
    implementation(project(":aerogel-mixin-dsl"))
    compileOnly(apiMinecraftStubs.output)
    testCompileOnly(apiMinecraftStubs.output)
    testRuntimeOnly(apiMinecraftStubs.output)
    implementation("net.fabricmc:sponge-mixin:$mixinVersion")
    implementation("org.ow2.asm:asm:$asmVersion")
    implementation("org.ow2.asm:asm-tree:$asmVersion")
    implementation("org.ow2.asm:asm-util:$asmVersion")
    implementation("com.google.code.gson:gson:$gsonVersion")
    implementation("org.jline:jline:$jlineVersion")
    compileOnly("org.slf4j:slf4j-api:2.0.17")
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.slf4j:slf4j-api:2.0.17")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.compileJava {
    dependsOn(":aerogel-api:minecraftStubsClasses")
}

application {
    mainClass.set("dev.aerogel.loader.AerogelMain")
    applicationName = "aerogel"
}

tasks.processResources {
    val projectVersion = project.version.toString()
    val targetMinecraftVersion = minecraftVersion
    val targetMixinVersion = mixinVersion
    inputs.property("version", projectVersion)
    inputs.property("minecraftVersion", targetMinecraftVersion)
    inputs.property("mixinVersion", targetMixinVersion)
    filesMatching("aerogel-build.properties") {
        expand(
            "version" to projectVersion,
            "minecraftVersion" to targetMinecraftVersion,
            "mixinVersion" to targetMixinVersion
        )
    }
}

tasks.jar {
    archiveBaseName.set("aerogel-loader")
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Title"] = "Aerogel Loader"
        attributes["Implementation-Version"] = project.version
        attributes["Premain-Class"] = "org.spongepowered.tools.agent.MixinAgent"
        attributes["Agent-Class"] = "org.spongepowered.tools.agent.MixinAgent"
        attributes["Can-Redefine-Classes"] = "true"
        attributes["Can-Retransform-Classes"] = "true"
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
        attributes["Premain-Class"] = "org.spongepowered.tools.agent.MixinAgent"
        attributes["Agent-Class"] = "org.spongepowered.tools.agent.MixinAgent"
        attributes["Can-Redefine-Classes"] = "true"
        attributes["Can-Retransform-Classes"] = "true"
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
        }
    }
}
