plugins {
    java
}

val mixinVersion: String by project
val aerogelApiProject = project(":aerogel-api")
val apiMinecraftStubs = aerogelApiProject.extensions
    .getByType<SourceSetContainer>()["minecraftStubs"]

dependencies {
    compileOnly(project(":aerogel-api"))
    compileOnly(apiMinecraftStubs.output)
    compileOnly("net.fabricmc:sponge-mixin:$mixinVersion")
}

tasks.compileJava {
    dependsOn(":aerogel-api:minecraftStubsClasses")
}

tasks.jar {
    archiveBaseName.set("aerogel-example-plugin")
}
