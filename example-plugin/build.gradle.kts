plugins {
    id("dev.aerogel.plugin") version "26.2-3"
}

group = "dev.aerogel.example"
version = "1.0.0"

aerogel {
    minecraft.set("26.2")

    plugin {
        id.set("aerogel_example")
        name.set("Aerogel Example Plugin")
        entrypoint("dev.aerogel.example.ExamplePlugin")
    }
}

tasks.jar {
    archiveBaseName.set("aerogel-example-plugin")
}
