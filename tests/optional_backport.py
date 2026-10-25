"""Production-jar regression check: WWOO works without VanillaBackport metadata dependency.

Cristel Lib already owns the mod_loaded gate for the separate compatibility pack.
Never turn that pack on unconditionally or change WWOO's main pack order.
"""
import json
import zipfile
from pathlib import Path

path = Path("wwoo-fabric-1.21.1-3.0.1-backport.jar")
with zipfile.ZipFile(path) as jar:
    mod = json.loads(jar.read("fabric.mod.json"))
    assert "vanillabackport" not in mod["depends"], mod["depends"]
    assert mod.get("suggests", {}).get("vanillabackport") == ">=1.2", mod.get("suggests")
    packs = json.loads(jar.read("data/cristellib/data_pack/load_packs.json"))["packs"]
    main = next(p for p in packs if p["location"] == "wwoo:resources/wwoo_main")
    compat = next(p for p in packs if p["location"] == "wwoo:resources/vanilla_backport_compat")
    assert "condition" not in main
    assert compat.get("condition") == {"type": "mod_loaded", "modid": "vanillabackport"}, compat
    assert packs.index(compat) < packs.index(main), "Compatibility pack ordering must be preserved"
    assert "resources/vanilla_backport_compat/pack.mcmeta" in jar.namelist()
    assert "resources/wwoo_main/pack.mcmeta" in jar.namelist()
    print("WWOO_OPTIONAL_BACKPORT_METADATA_PASS: main always-on, integration gated by Cristel Lib")
