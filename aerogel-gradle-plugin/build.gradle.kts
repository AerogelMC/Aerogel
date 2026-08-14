plugins {
    `java-gradle-plugin`
    `maven-publish`
}

val gsonVersion: String by project
val asmVersion: String by project
val junitVersion: String by project

dependencies {
    implementation(project(":aerogel-api"))
    implementation("com.google.code.gson:gson:$gsonVersion")
    implementation("org.ow2.asm:asm:$asmVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        create("aerogelPlugin") {
            id = "dev.aerogel.plugin"
            implementationClass = "dev.aerogel.gradle.AerogelGradlePlugin"
            displayName = "Aerogel Plugin Development"
            description = "Configures Minecraft server plugin development for Aerogel."
        }
    }
}

tasks.jar {
    archiveBaseName.set("aerogel-gradle-plugin")
}

publishing {
    repositories {
        maven {
            name = "AerogelBuild"
            url = rootProject.layout.buildDirectory.dir("aerogel-maven").get().asFile.toURI()
        }
    }
}
