"""Inspect WWOO's existing runtime datapack selection and optional-mod references in CI."""
import json
import zipfile
from pathlib import Path

for path in [Path("inputs/wwoo-fabric-2.6.7.jar"), Path("inputs/wwoo-fabric-26.3-3.0.1.jar"), Path("wwoo-fabric-1.21.1-3.0.1-backport.jar")]:
    if not path.exists():
        continue
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        meta = json.loads(archive.read("fabric.mod.json"))
        print("JAR", path.name, "DEPENDS", json.dumps(meta.get("depends", {}), sort_keys=True))
        for name in names:
            if name.endswith("load_packs.json") and "cristellib" in name:
                print("PACKS", name, archive.read(name).decode()[:7000])
        matches = [n for n in names if "vanilla_backport_compat/" in n]
        print("COMPAT_FILES", len(matches), "PREFIXES", matches[:18])
        paths = [n for n in names if n.startswith("resources/wwoo_main/data/minecraft/worldgen/biome/")]
        print("MAIN_BIOMES",len(paths),"SAMPLE",paths[:9])
