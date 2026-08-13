# Third-party notices

Aerogel does not contain or redistribute Minecraft code or `server.jar`.
At setup time it downloads the requested release from Mojang's official version
manifest, verifies the published digest, and stores it in the server operator's
own runtime directory. Use of Minecraft remains subject to the Minecraft EULA
and Usage Guidelines.

Aerogel uses these runtime libraries through Gradle dependency resolution:

- SpongePowered Mixin, FabricMC-maintained distribution (`net.fabricmc:sponge-mixin`), MIT License.
  This is the bytecode Mixin engine only. Aerogel does not depend on or embed Fabric Loader.
- ASM, Copyright OW2, BSD 3-Clause License.
- Gson, Copyright Google, Apache License 2.0.
- JLine, Copyright the JLine project contributors, BSD 3-Clause License.
- Error Prone annotations, Copyright Google, Apache License 2.0.

The complete dependency archives and license metadata are included in a built
application distribution's `lib` directory or are obtainable from the Maven
coordinates recorded in Gradle's dependency report.
