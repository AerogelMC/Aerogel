# Aerogel

Aerogel is a Minecraft: Java Edition server plugin loader for Minecraft 26.2 and later.

> Aerogel is in early development. Expect breaking changes before the first stable release.

## Run a server

Install JDK 25, place `Aerogel-26.2-1.jar` in an empty server directory, and run:

```shell
java -Xms2G -Xmx4G -jar Aerogel-26.2-1.jar nogui
```

On the first run, Minecraft creates `eula.txt` and stops. Read the [Minecraft EULA](https://aka.ms/MinecraftEULA), set `eula=true` if you agree, then run the same command again.

Place plugin JARs in the `plugins` directory.

## Create a plugin

The [`example-plugin`](example-plugin) project is the quickest starting point. See the [API guide](docs/API.md), [event guide](docs/EVENTS.md), and [Mixin guide](docs/MIXINS.md).

## Build from source

```shell
./gradlew clean build
```

On Windows:

```powershell
.\gradlew.bat clean build
```

The standalone server JAR is written to `aerogel-loader/build/libs/Aerogel-26.2-1.jar`.

## Versioning

Releases use `Minecraft version-build number`. For example, the first Minecraft 26.2 release is `26.2-1`, followed by `26.2-2`. The build number starts at `1` again for each Minecraft version.

## License

Aerogel is licensed under the [Apache License 2.0](LICENSE).

Aerogel does not distribute Minecraft server code. It downloads the official server files from Mojang when needed and verifies their published hashes. Minecraft is licensed separately by Mojang and Microsoft. Aerogel is not an official Minecraft product and is not approved by or associated with Mojang or Microsoft.

Third-party licenses and notices are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
