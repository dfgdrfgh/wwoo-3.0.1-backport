# WWOO 3.0.1 backport — Forge 1.20.1

Development branch: `forge-1.20.1-port`. Source content baseline: main `3f712fe`.
Existing Fabric and biome-size branches remain untouched.

This is a source and data port, using Forge's registry events and bundled pack
APIs. It does not load Fabric classes or require Cristel Lib. The source retains
3.0.1's content conversion, the seven complete older biome definitions, autumnal
Windswept Forest completions, modern noise/rule/template/placement codecs,
plant-support behavior, bonemeal, Pale Garden surface spawning rules and
packed/blue-ice carving protection. There are no cloud hooks or biome-size
features in this Forge target.

Requires Java 17, Minecraft 1.20.1, Forge 47.3+, and the companion patched
TrueVanillaBackport Forge port (version 1.2+). Modern blocks, entities, foliage
colors and their registry codecs come from that companion mod.

The `removeOres` option is now `config/wwoo/config.toml`, defaults to true and
controls the native bundled removal pack. Restart after changing it. This
removes the same underground material patches; ordinary ores remain.

Build with `./gradlew build`. Python 3 is required for lossless content preparation;
Minecraft binaries are downloaded from Mojang and checked against SHA-1 metadata.
The release JAR is under `build/libs/`. GitHub Actions uploads the JAR and resource
audit in `WWOO-3.0.1-backport-Forge-1.20.1`.

Validation status is tracked in the PR and Actions run. Unit conversion checks
and compilation do not establish full world generation or modpack compatibility.
Use a new disposable world for integration validation; a complete playable
modpack has not been supplied. Do not load an existing valuable world until the
companion-mod integration and dedicated-server smoke tests pass.

Upstream author and license metadata are preserved in the input JARs and bundled
resources. The backport does not change upstream content licensing.
