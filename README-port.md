# WWOO 3.0.1 backport for Fabric 1.21.1

Replacement WWOO JAR using the supplied 3.0.1 rewrite as its base. The supplied 2.6.7 version completes the biome overhauls identified as missing or unfinished. Existing TrueVanillaBackport compatibility is retained.

## Installation

Remove the previous WWOO JAR and install `wwoo-fabric-1.21.1-3.0.1-backport.jar`. Keep your patched TrueVanillaBackport Fabric JAR installed. This build requires Minecraft 1.21.1, Java 21 or newer, Fabric Loader, Fabric API, and Cristel Lib.

Original broad validation: Fabric Loader 0.18.5, Fabric API 0.116.17+1.21.1, Cristel Lib 3.1.7, and TrueVanillaBackport 1.2.0.2638.9. Compatibility with unpatched upstream VanillaBackport has not been established. No changes were made to the 1.20.1 version.

Use a new test world to inspect the rewrite. Worldgen changes affect newly generated chunks.

## Content and compatibility

- The complete supplied 3.0.1 biome definitions, worldgen features, block-state providers, weighted structure templates, loot tables, and biome/block/structure tags are converted to 1.21.1 formats. This includes the newer bedrock sealing and cliff generation.
- Ice Spikes, Mangrove Swamp, Mushroom Fields, Swamp, Windswept Hills, Windswept Gravelly Hills, and Windswept Savanna retain their complete 2.6.7 definitions and supporting resources.
- Windswept Forest keeps 3.0.1's autumnal vegetation and colors, with older geological and terrain details filling its unfinished parts.
- Imported 3.0.1 features and templates use the `wwoocontent` namespace. Older fill-ins retain their separate `wythers` dependencies.
- New codecs preserve nested rule fallthrough, skipped placement, sequence/overlay behavior, modern floating-point noise, placement modifiers, template animals, and scheduled block ticks. New plant-support tags are supported by the target version's block checks. Entity-only templates count as successful placements.
- Modern biome colors and supported gameplay settings are converted. Cloud RGB/opacity use client hooks, and dry foliage colors use TrueVanillaBackport's existing support. Frozen-ocean temperature modifiers and root-level creature spawn probabilities are preserved.
- Wolf biome selectors are converted to 1.21.1's registry format, including a disjoint pale-wolf fallback. Baby wolf textures are already supplied and selected by the tested backport dependency.
- Panda spawning matches the actual supplied 3.0.1 data: Dappled Forest has weight 10, Jungle has weight 1, and Bamboo Jungle/Sparse Jungle omit pandas. The source JAR does not make Dappled Forest completely exclusive.
- The compatibility pack retains its supporting resources, but its older Pale Garden biome definition is removed. This prevents existing pack ordering from overriding the 3.0.1 landscaping and vegetation. TrueVanillaBackport's native Pale Garden plateau-placement compatibility fix remains active.
- Imported features reference the isolated 3.0.1 block tags, including the branch-replacement set. Hashed tag references no longer resolve to the smaller 2.6.7 definitions.
- Native tree replacement checks reference the imported 3.0.1 plant tag, including mushrooms, saplings, and newer plants, rather than the smaller 2.6.7 tag.
- The remove-ores setting stays explicit in the encoded config, allowing Cristel Lib to activate its pack. Existing configs without the setting still use the original default of `true`; `false` keeps the pack disabled.
- Plant support overrides run only while their main-pack marker is populated and the relevant support tags exist. Disabling the built-in packs restores ordinary plant checks; deliberate empty support tags remain restrictive.
- The obsolete 2.6.7 add-on switches and packs are retired to match 3.0.1's pack/config selection. The remove-ores switch remains available.

## Cloud transitions

Entering a biome with a different cloud color or opacity starts a two-second fade to that biome’s exact palette. The fade follows elapsed time and completes even if the player stops moving. Crossing another boundary before it finishes starts a new fade from the currently displayed color. Only the camera’s current biome is sampled; there is no distance-based range or neighboring-biome averaging. Color and opacity blend independently, and vanilla daylight/weather shading is preserved. New worlds and dimension changes initialize to their own palette. The existing cloud mesh receives an interpolated shader tint at draw time, so white changes through gray shades without rebuilding geometry for each fade step. Both fancy-cloud passes use one frame sample. Shader state is restored after each draw, including exceptions.

## Pale Garden spawning

Natural biome spawning, initial animal generation, patrols, and event spawners are blocked at the surface of `minecraft:pale_garden`, except for creakings and phantoms. Underground spawning retains the biome’s normal spawn lists and vanilla/mod spawn conditions. Surface means at or above the highest solid ground block in the local column, ignoring leaves and logs; it is not a fixed Y cutoff or a sky-visibility test. The rule uses the mob’s spawn position and applies to vanilla and modded mobs using the standard spawn lifecycle. WWOO template animals follow the same exception list. Creaking-heart spawning, spawn eggs, commands, breeding, and mob spawners remain allowed. Existing mobs are not removed and can enter the biome. Runtime checks use generation-region biome data without requesting chunks through the backing server world.

## Validation

See `validation-full.json` for the original broad-validation JAR hash, per-biome saved chunk checks, dependency versions, and runtime assertions. The conversion also checks that every referenced imported template exists and that the requested older biome definitions are retained exactly.

Build `3.0.1+backport.1.21.1.2` additionally tests Pale Garden pack priority, native tree tag references, and saved fixed-biome land generation with Fabric Loader 0.19.5, Tectonic 3.0.28, and Lithostitched 1.8.0. See `tests/biome-parity-result.json` for this build’s hash, generated block counts, and runtime checks. The previous broad report applies to the unchanged Java helpers and worldgen definitions, not the corrected effective Pale Garden definition.

Build `3.0.1+backport.1.21.1.3` additionally restores 28 dry-foliage overrides lost through a shared dictionary during biome conversion, and corrects 196 imported feature files that referenced older block tags. The dry-foliage rules use priority 100 in the supplied VanillaBackport loader. See `tests/resource-conflicts-result.json` for the conflict audit and validation.

Build `3.0.1+backport.1.21.1.4` fixes the removal-pack condition and plant behavior when built-in packs are disabled. Six dedicated-server scenarios check explicit true/false settings, omitted settings, disabled packs, empty support tags, and an inactive marker with empty tag holders. The test probe inspects the effective placements for all eight affected resources in both namespaces. The 12 existing behavior cases are also rerun. See `tests/general-behavior-result.json`.

The noise sampler was compared with the supplied 26.3 runtime over 20,000 samples with no bitwise differences. Dedicated-server integration exercises normal generation, fixed-biome generation, TrueVanillaBackport compatibility, saving, and shutdown. Behavior cases cover optional/nested rules, sequence and overlay placement, vegetation support, moss carpets, template animals, leaf support, and the desert striped-wolf selector.

Build `3.0.1+backport.1.21.1.5` adds the Pale Garden spawning restriction. `tests/pale_garden_spawning.py` runs the exact new release mixin classes on an isolated dedicated server, checking biome boundaries, generation, modded mobs, event spawning, manual spawning, and transient spawn flags. Its test-only creaking ID validates the whitelist; it does not simulate creaking-heart behavior.

Build `3.0.1+backport.1.21.1.6` limits the rule to the surface and restores the original biome spawn lists for underground spawning. Runtime regression checks cover caves, sloped terrain, leaves/logs above the surface, and partial ground blocks, as well as the existing exceptions and manual spawn cases.

Build `3.0.1+backport.1.21.1.7` adds spatial cloud color/opacity blending. Regression checks exercise continuous camera movement, negative coordinates, diagonal boundaries, constant-biome colors, transparent neighbors, and a bounded sample count.

Build `3.0.1+backport.1.21.1.8` strengthens nearby biome colors and removes opacity bias from the RGB blend. Boundary regressions check equal spatial color contributions despite different opacity and stronger local-biome tint after moving four and eight blocks across a straight test boundary.

Build `3.0.1+backport.1.21.1.9` replaces spatial blending with a timed biome-entry transition. Tests cover stationary completion, rapid retargeting, reverse crossings, repeated reads, frame-rate independence, exact destination colors/opacity, and world-state reset.

Build `3.0.1+backport.1.21.1.10` applies the fade through the cloud shader instead of repeatedly recreating tinted vertex buffers. The client regression loads the release’s exact cloud mixin into Minecraft 1.21.1 through Fabric, invokes its injected draw hook, and checks white-to-gray progression, pass consistency, original draw arguments, opacity scaling, and restoration of the actual RenderSystem shader state. It runs before window creation; it does not replace an in-game visual check or test third-party cloud renderers.

A side-by-side visual comparison with 26.3 has not been performed. The target version retains its own rendering engine and ordinary vanilla support-feature implementations, so this is not a claim that every pixel or underlying Minecraft engine behavior is identical to 26.3.

The three inverted cave-disk height ranges from 2.6.7 remain in the preserved older resources. They can log `Empty height range: [-55 absolute--60 absolute]`; they are not conversion or registry errors.

## Rebuilding

The source archive includes the two supplied WWOO input JARs, the tested Cristel Lib compile dependency, the conversion and validation scripts, Java helpers, the mapping utility, and 26.3 download metadata. It does not include Minecraft binaries, mappings, generated test worlds, or a bundled JDK.

Install Python 3 and JDK 21 or newer. Set `WWOO_JAVA_HOME` to the JDK directory if it is not on PATH. From the extracted source directory run:

```sh
python3 port/bootstrap.py
python3 port/build.py
```

Bootstrap downloads the public Minecraft and Fabric build dependencies. The JAR is written in the project root.

For integration checks, add the tested Fabric API and TrueVanillaBackport JARs to `test-dependencies`, then run:

```sh
python3 port/smoke.py test-full-validation all
python3 port/validate.py test-full-validation
```

Re-running the optional cross-version noise test also requires Java 25 and the 26.3 runtime libraries. The recorded comparison is included.

The tests create their own local, offline dedicated-server world. Upstream author metadata and the upstream WWOO license are retained in the replacement JAR and both supplied inputs.

