#!/usr/bin/env python3
"""Read-only audit of official Ancient City Overhaul 1.21.1 files; no redistribution."""
from collections import Counter
from io import BytesIO
import json
import urllib.request
import zipfile

VERSIONS = {
    "v2_datapack": "https://cdn.modrinth.com/data/DNuNq5bb/versions/5AAIrzsA/DnT%20Ancient%20City%20Overhaul%20v2.zip",
    "v2_fabric": "https://cdn.modrinth.com/data/DNuNq5bb/versions/khb6eZvR/dungeons-and-taverns-ancient-city-overhaul-v2.jar",
}

with zipfile.ZipFile("inputs/wwoo-fabric-26.3-3.0.1.jar") as wwoo:
    wwoo_worldgen = {
        n.removeprefix("resources/wwoo_main/")
        for n in wwoo.namelist()
        if n.startswith("resources/wwoo_main/data/") and n.endswith(".json")
    }
    print("WWOO source worldgen files:", len(wwoo_worldgen))

for label, url in VERSIONS.items():
    req = urllib.request.Request(url, headers={"User-Agent": "WWOO-Minecraft-1.21.1-compatibility-audit"})
    with urllib.request.urlopen(req, timeout=60) as response:
        data = response.read()
    print("\\n=== ", label, "bytes", len(data), "===")
    with zipfile.ZipFile(BytesIO(data)) as archive:
        assert archive.testzip() is None
        paths = [p for p in archive.namelist() if not p.endswith("/")]
        print("Files:", len(paths))
        print("Root files:", [p for p in paths if "/" not in p][:40])
        roots = sorted({p.split("data/")[0] for p in paths if "data/" in p})
        print("Data roots:", roots[:15])
        minecraft_files = [p for p in paths if "/data/minecraft/" in p or p.startswith("data/minecraft/")]
        print("Minecraft override paths:", len(minecraft_files))
        for path in sorted(minecraft_files):
            print("MINECRAFT", path)
        print("Worldgen type distribution:", Counter(p.split("/worldgen/")[1].split("/")[0] for p in paths if "/worldgen/" in p))
        for path in sorted(paths):
            if not path.endswith(".json"):
                continue
            if "worldgen/structure/" in path or "worldgen/structure_set/" in path or "tags/worldgen/biome/" in path or "worldgen/noise_settings/" in path or "worldgen/dimension/" in path:
                try:
                    contents = json.loads(archive.read(path))
                except Exception as exc:
                    print("INVALID JSON", path, str(exc))
                    continue
                print("WORLDGEN", path, json.dumps(contents, separators=(",", ":"))[:4500])
        normalized = {p[p.find("data/"):] for p in paths if "data/" in p}
        print("OVERLAP_WWOO", sorted(normalized & wwoo_worldgen)[:120])
        print("BIOME_REPLACEMENTS", sorted(p for p in normalized if "/worldgen/biome/" in p and p.endswith(".json"))[:120])
        print("DIMENSION_REPLACEMENTS", sorted(p for p in normalized if "/worldgen/noise_settings/" in p or "/dimension/" in p)[:120])
