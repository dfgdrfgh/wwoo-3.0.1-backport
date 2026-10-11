# WWOO 3.0.1 backport for Fabric 1.21.1

Replacement WWOO JAR using the supplied 3.0.1 rewrite as its base. The supplied 2.6.7 version completes the biome overhauls identified as missing or unfinished. Existing TrueVanillaBackport compatibility is retained.

## Installation

Remove the previous WWOO JAR and install `wwoo-fabric-1.21.1-3.0.1-backport.jar`. Keep your patched TrueVanillaBackport Fabric JAR installed. This build requires Minecraft 1.21.1, Java 21 or newer, Fabric Loader, Fabric API, and Cristel Lib.

Original broad validation: Fabric Loader 0.18.5, Fabric API 0.116.17+1.21.1, Cristel Lib 3.1.7, and TrueVanillaBackport 1.2.0.2638.9. Compatibility with unpatched upstream VanillaBackport has not been established. No changes were made to the 1.20.1 version.

Use a new test world to inspect the rewrite. Worldgen changes affect newly generated chunks.

## Biome size

Open **WWOO Config** (with Cloth Config/Mod Menu installed). All settings are on
one page, with no category tabs. **WWOO Config** and its underground-patches
setting are shown first, followed by the expandable **Biome Size** section.
Biome Size contains the global scale followed by one flat alphabetical list
of every WWOO Overworld biome, including Pale Garden. There
are no separate forest/ocean/etc. groups. All scales default to `1.0`.
You can also edit `config/wwoo/biome_size.json5`:

```json5
{
  "sizeMultiplier": 1.0,
  "biomeScales": {
    "minecraft:pale_garden": 2.0,
    "minecraft:forest": 0.5
  }
}
```

The example expands Pale Garden and shrinks Forest. Omitted biomes retain `1.0`;
the saved file lists all 56 supplied Overworld biomes. Namespaced custom keys
are preserved and appear in the same biome list in the screen. A setting only
affects a biome if your world generator actually places that biome.

Individual scales range from **0.25–8.0**: above `1.0` expands the biome's climate
region boundaries, below `1.0` shrinks them. Growing a biome takes space from
its neighbors and can make it more common. These are relative boundary controls,
not guaranteed width multipliers. They retain the climate noise and terrain
shape, use a bounded logarithmic adjustment to climate-selection fitness
so that each increase still expands relative boundaries without letting an
`8.0` setting overwhelm unrelated climates. The original biome selection is
preserved exactly when all individual scales are `1.0`. Individual scales are
not literal block-width multipliers; unusually large regions may still occur
naturally or when combined with Global Biome Scale.
Natural variation remains; no setting fixes tree counts, minimum area or width.
Very rare biomes may disappear in a particular area when made smaller.

The separate **Global Biome Scale** also ranges from **0.25–8.0**. Its saved key
is still `sizeMultiplier`, so existing config files retain their setting.
`1.0` retains current generation, `2.0` generally makes
biome regions twice as wide, and `0.5` generally makes them half as wide. This
scales their horizontal climate-noise frequencies; it does not prescribe a
fixed width, minimum area or tree count. Pale Gardens and other biomes still
vary naturally from small patches to larger regions. Narrow rivers, transition
biomes and climate-dependent regions retain their underlying placement rules.

The global setting applies to Overworld generators using vanilla climate noises,
including Default, Large Biomes and Amplified. Large Biomes keeps its existing
scale before this additional multiplier. Climate noises shared with terrain
are scaled together so mountains/coastlines remain consistent with biome
selection. Height limits, decoration density, cave/aquifer/ore detail noises,
Nether and End generation are preserved. Disabling WWOO's main pack disables
this scaling. Individual settings apply to Minecraft's multi-noise biome sources
and leave Nether/End presets unchanged. Generators supplied by other mods with
their own biome-placement systems may not use these hooks.

After saving in the screen, close and reopen the world. After editing the file,
restart the game/server. Existing chunks keep their terrain and biomes; changing
the multiplier in an existing world can create seams at new chunk boundaries.
The default has no generation changes. The original WWOO config file and its
remove-underground-patches setting retain their existing values.

`tests/biome_size.py` checks the missing-file default, both range limits, smaller
and larger regions, preserved existing settings, unchanged noise registries,
all three Overworld presets, Nether/End, finite terrain samples and disabled
packs using the actual dedicated-server mixins. It also checks per-biome defaults
and migration, custom-key persistence, monotonic Pale Garden expansion/shrinking,
unchanged unrelated regions, the normal world's biome source, and mixed scales
against an exhaustive climate search. The test probe is never bundled
in the released mod. Run it after `tests/build_biome_size_probe.py`, with the
normal test dependencies installed.

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

## Validation

See `validation-full.json` for the original broad-validation JAR hash, per-biome saved chunk checks, dependency versions, and runtime assertions. The conversion also checks that every referenced imported template exists and that the requested older biome definitions are retained exactly.

Build `3.0.1+backport.1.21.1.2` additionally tests Pale Garden pack priority, native tree tag references, and saved fixed-biome land generation with Fabric Loader 0.19.5, Tectonic 3.0.28, and Lithostitched 1.8.0. See `tests/biome-parity-result.json` for this build’s hash, generated block counts, and runtime checks. The previous broad report applies to the unchanged Java helpers and worldgen definitions, not the corrected effective Pale Garden definition.

Build `3.0.1+backport.1.21.1.3` additionally restores 28 dry-foliage overrides lost through a shared dictionary during biome conversion, and corrects 196 imported feature files that referenced older block tags. The dry-foliage rules use priority 100 in the supplied VanillaBackport loader. See `tests/resource-conflicts-result.json` for the conflict audit and validation.

Build `3.0.1+backport.1.21.1.4` fixes the removal-pack condition and plant behavior when built-in packs are disabled. Six dedicated-server scenarios check explicit true/false settings, omitted settings, disabled packs, empty support tags, and an inactive marker with empty tag holders. The test probe inspects the effective placements for all eight affected resources in both namespaces. The 12 existing behavior cases are also rerun. See `tests/general-behavior-result.json`.

The noise sampler was compared with the supplied 26.3 runtime over 20,000 samples with no bitwise differences. Dedicated-server integration exercises normal generation, fixed-biome generation, TrueVanillaBackport compatibility, saving, and shutdown. Behavior cases cover optional/nested rules, sequence and overlay placement, vegetation support, moss carpets, template animals, leaf support, and the desert striped-wolf selector.

Client rendering and a side-by-side visual comparison with 26.3 have not been performed. The target version retains its own rendering engine and ordinary vanilla support-feature implementations, so this is not a claim that every pixel or underlying Minecraft engine behavior is identical to 26.3.

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
