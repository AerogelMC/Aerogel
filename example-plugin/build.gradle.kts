plugins {
    java
}

val mixinVersion: String by project

dependencies {
    compileOnly(project(":aerogel-api"))
    compileOnly("net.fabricmc:sponge-mixin:$mixinVersion")
}

tasks.jar {
    archiveBaseName.set("aerogel-example-plugin")
}
