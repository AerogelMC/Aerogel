plugins {
    application
    `java-library`
}

evaluationDependsOn(":example-plugin")

val mixinVersion: String by project
val asmVersion: String by project
val gsonVersion: String by project
val junitVersion: String by project

dependencies {
    implementation(project(":aerogel-api"))
    implementation("net.fabricmc:sponge-mixin:$mixinVersion")
    implementation("org.ow2.asm:asm:$asmVersion")
    implementation("org.ow2.asm:asm-tree:$asmVersion")
    implementation("org.ow2.asm:asm-util:$asmVersion")
    implementation("com.google.code.gson:gson:$gsonVersion")

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
