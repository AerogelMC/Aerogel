plugins {
    `java-library`
    `maven-publish`
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
            url = rootProject.layout.buildDirectory.dir("aerogel-maven").get().asFile.toURI()
        }
    }
}
