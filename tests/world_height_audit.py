"""Inspect WWOO input and generated data-pack height-sensitive resources.

Read-only CI diagnostic; no runtime registry data is modified.
"""
from pathlib import Path
import json
import zipfile

ROOT = Path(__file__).resolve().parent.parent
SOURCES = [
    ROOT / "inputs/wwoo-fabric-2.6.7.jar",
    ROOT / "inputs/wwoo-fabric-26.3-3.0.1.jar",
    ROOT / "wwoo-fabric-1.21.1-3.0.1-backport.jar",
]

def report(archive):
    with zipfile.ZipFile(archive) as z:
        names = z.namelist()
        data = []
        for name in names:
            if not name.endswith(".json") or "/data/minecraft/" not in name:
                continue
            relevant = any("/" + part + "/" in name for part in [
                "dimension_type", "dimension", "noise_settings", "density_function",
                "world_preset"
            ])
            if not relevant:
                continue
            try:
                value = json.loads(z.read(name))
            except (ValueError, UnicodeDecodeError):
                continue
            if not isinstance(value, dict):
                continue
            miny, height, logical = None, None, None
            if "noise" in value and isinstance(value["noise"], dict):
                miny, height = value["noise"].get("min_y"), value["noise"].get("height")
            else:
                miny, height = value.get("min_y"), value.get("height")
            logical = value.get("logical_height")
            if ("/dimension_type/" in name or
                "/noise_settings/" in name or
                "/dimension/" in name or
                "/world_preset/" in name or
                any(word in name for word in ["overworld", "height", "vertical"])):
                data.append({
                    "path": name,
                    "min_y": miny,
                    "height": height,
                    "logical_height": logical,
                    "sea_level": value.get("sea_level"),
                    "generator": value.get("generator"),
                })
        print("=== ", archive.name, " ===")
        print(json.dumps(data, indent=2, default=str))
        print("minecraft density function JSON count:", sum(
            "/data/minecraft/worldgen/density_function/" in n
            for n in names
        ))

if __name__ == "__main__":
    for source in SOURCES:
        report(source)
