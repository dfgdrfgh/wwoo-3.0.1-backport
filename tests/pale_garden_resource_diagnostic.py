"""Inspect Pale Garden's actual resource definitions in both source and built WWOO JARs.

This diagnostic is intentionally read-only and does not modify the climate/noise map.
"""
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = [
    ("ww2.6.7", ROOT / "inputs/wwoo-fabric-2.6.7.jar"),
    ("ww3.0.1", ROOT / "inputs/wwoo-fabric-26.3-3.0.1.jar"),
    ("generated", ROOT / "wwoo-fabric-1.21.1-3.0.1-backport.jar"),
]
BIOME = "resources/wwoo_main/data/minecraft/worldgen/biome/pale_garden.json"
COMPAT = "resources/vanilla_backport_compat/data/minecraft/worldgen/biome/pale_garden.json"

def describe(label, path):
    with zipfile.ZipFile(path) as jar:
        names = set(jar.namelist())
        print(f"=== {label} Pale Garden ===")
        print("compat biome:", COMPAT in names)
        for location in [BIOME, COMPAT]:
            if location not in names:
                continue
            biome = json.loads(jar.read(location))
            print(location)
            for i, step in enumerate(biome.get("features", [])):
                if step:
                    print(f"  step {i}: {json.dumps(step, separators=(',', ':'))[:4000]}")
            refs = []
            seen = set()

            def walk(value):
                if isinstance(value, str) and ':' in value and not value.startswith('#'):
                    if value not in seen:
                        seen.add(value)
                        refs.append(value)
                elif isinstance(value, dict):
                    for v in value.values():
                        walk(v)
                elif isinstance(value, list):
                    for v in value:
                        walk(v)

            for step in biome.get("features", []):
                walk(step)
            for generation in range(3):
                next_refs = []
                for ref in refs:
                    ns, n = ref.split(':', 1)
                    candidates = [
                        f"resources/wwoo_main/data/{ns}/worldgen/placed_feature/{n}.json",
                        f"resources/wwoo_main/data/{ns}/worldgen/feature/{n}.json",
                        f"resources/wwoo_main/data/{ns}/worldgen/configured_feature/{n}.json",
                    ]
                    for candidate in candidates:
                        if candidate not in names:
                            continue
                        value = json.loads(jar.read(candidate))
                        if any(t in ref.lower() for t in ("pale_garden", "pale_oak", "spruce", "shrub")):
                            print("  ref", ref, "=>", json.dumps(value, separators=(',', ':'))[:1500])
                        walk(value)
                # refs expands as the graph is walked.
            terms = [ref for ref in refs if any(t in ref for t in ("spruce", "pale_oak", "pale_garden"))]
            print("  traced notable refs:", terms[:120])
        for location in [
            "data/cristellib/data_pack/load_packs.json",
            "resources/wwoo_main/data/minecraft/tags/block/replaceable_by_trees.json",
        ]:
            if location in names:
                print(location, jar.read(location).decode()[:4000])

for name, file in SOURCES:
    if file.exists():
        describe(name, file)
