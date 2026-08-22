<div align="center">

<img src="logo.png" width="128" height="128" alt="Aerogel logo"/>

# Aerogel

Minecraft Java Edition **server plugin platform** for **Minecraft 26.2**.

[![Version](https://img.shields.io/github/v/release/AerogelMC/Aerogel?include_prereleases&sort=semver&display_name=tag&style=for-the-badge)](https://github.com/AerogelMC/Aerogel/releases/latest)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-2b84ff?style=for-the-badge)](README.md)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge)](https://www.oracle.com/java/technologies/javase/jdk25-archive-downloads.html)
[![License](https://img.shields.io/badge/License-Apache--2.0-6A0DAD?style=for-the-badge)](LICENSE)
[![Repository](https://img.shields.io/badge/GitHub-AerogelMC/Aerogel-181717?style=for-the-badge&logo=github)](https://github.com/AerogelMC/Aerogel)
[![Discord](https://img.shields.io/badge/Discord-Join%20Chat-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/ZgYFHyP8hK)

[Features](#features) · [How it works](#how-it-works) · [Compatibility](#compatibility) · [Installation](#installation) · [Build](#build) · [Contributing](#contributing) · [Report a bug](https://github.com/AerogelMC/Aerogel/issues)

</div>

---

## What is Aerogel?

Aerogel is a plugin loader that runs on top of the official Mojang server jar.
It replaces the vanilla bootstrap with a runtime that:

- loads and runs plugins from the `plugins` directory,
- injects mixins to support high-level plugin behavior,
- exposes a high-level Java/Kotlin API for server, world, player, inventory, events, commands, and utilities.

Aerogel is not a client mod. It is a server-side runtime only.

## Features

- Plugin-first architecture with classpath isolation and lifecycle control.
- Automatic Minecraft runtime handling from Mojang manifests.
- Command registration and command execution support.
- Event system for server, player, world, entity, inventory, block, and command workflows.
- Mixin support for advanced behavior and compatibility.
- Kotlin-friendly API surface and optional Kotlin DSL for mixins.
- Multi-language chat and message handling hooks.
- Built-in diagnostics, restart/reload flow, and plugin lifecycle logging.

## How it works

```text
Minecraft jar selected
        -> runtime bootstrap
        -> runtime class loader / transforms
        -> plugin discovery + dependency scan
        -> mixin configuration + API init
        -> server boot + plugin load
        -> runtime events
```

### Runtime model

1. Aerogel downloads or reuses a pinned Minecraft `server.jar` version.
2. The runtime loads Aerogel core and then boots the server.
3. Plugins are discovered from `plugins/` and loaded through Aerogel's plugin API.
4. Mixins are applied to target vanilla internals so plugin code can interact at server level.
5. Plugins receive API views (`plugin.minecraft()`, `plugin.worlds()`, command/event registries, etc.).

### Plugin and API philosophy

- Plugin code should be simple to write and practical for server operators.
- Advanced users can go deep with mixins and raw vanilla interactions.
- High-level APIs are preferred for common tasks; low-level access remains available when needed.

## Compatibility

| Component | Status |
|---|---|
| Minecraft | 26.2 |
| Java | 25 |
| Plugin languages | Java / Kotlin |
| Platform | Server-side only |

## Installation

1. Install **JDK 25**.
2. Copy `Aerogel-26.2-9.jar` to a fresh server directory.
3. Start once:

```bash
java -Xms2G -Xmx4G -jar Aerogel-26.2-9.jar nogui
```

4. On first run, `eula.txt` is generated. Accept EULA if you agree.
5. Put plugin JAR files into `plugins/`.
6. Restart server.

If you are using the example project, run in `example-plugin` with its own Gradle setup.

## Build

```bash
./gradlew clean build
```

On Windows:

```powershell
.\gradlew.bat clean build
```

Artifacts:
- `aerogel-loader/build/libs/Aerogel-26.2-9.jar`
- `aerogel-loader/build/libs/Aerogel-26.2-9-all.jar` (standalone, depending on selected task)

## Configuration

Runtime and plugin options are controlled from:

- server arguments in the launch command
- `aerogel` config files written under the server runtime directory
- plugin-provided configuration via the API

Outbound zlib work uses one ordered lane per connection and runs across a
dedicated worker pool. Its default worker count is the JVM's available processor
count. Override it with
`--jvm-arg=-Daerogel.network.compression.workers=<positive-count>` when CPU
allocation is managed externally.

Player-driven chunk loading replaces vanilla's fixed four in-flight chunks with
the Context pool's live worker headroom. Ready chunk packets have no artificial
per-tick or unacknowledged-batch quota; Context ownership, ordered compression,
and the network socket provide the remaining backpressure.

## Development

- Read the full development guide: [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)
- API reference: [docs/API.md](docs/API.md)
- Event reference: [docs/EVENTS.md](docs/EVENTS.md)
- Command/mixin guides:
  - [docs/MIXINS.md](docs/MIXINS.md)
  - [docs/GRADLE_PLUGIN.md](docs/GRADLE_PLUGIN.md)

You can start from [`example-plugin`](example-plugin) for a minimal project.

## Contributing

Bug reports and pull requests are welcome:
- Use clear repro steps and server logs
- Include Minecraft and Java version
- Mention whether the issue is observed in plugin loading, events, or command handling
- Community + help: [Discord](https://discord.gg/ZgYFHyP8hK)

## License

Aerogel is released under the [Apache License 2.0](LICENSE).

Minecraft server code is not redistributed by Aerogel. This project only downloads official Mojang server artifacts.
Third-party license notices are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
