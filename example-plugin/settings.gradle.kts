pluginManagement {
    val localAerogel = sequenceOf(file(".."), file("../Aerogel"))
        .firstOrNull { it.resolve("aerogel-gradle-plugin").isDirectory }
    if (localAerogel != null) {
        includeBuild(localAerogel)
    }

    repositories {
        maven("https://raw.githubusercontent.com/AerogelMC/Aerogel/maven/")
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "aerogel-example-plugin"
