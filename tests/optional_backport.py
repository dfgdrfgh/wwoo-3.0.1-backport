"""Verify WWOO worldgen packs are mutually exclusive and no version is hard-gated."""
import json
import zipfile
from pathlib import Path
with zipfile.ZipFile("inputs/wwoo-fabric-2.6.7.jar") as original, zipfile.ZipFile("wwoo-fabric-1.21.1-3.0.1-backport.jar") as jar:
    meta=json.loads(jar.read("fabric.mod.json"))
    assert "vanillabackport" not in meta["depends"],meta["depends"]
    assert meta.get("suggests",{}).get("vanillabackport")=="*",meta.get("suggests")
    packs=json.loads(jar.read("data/cristellib/data_pack/load_packs.json"))["packs"]
    find=lambda location: next(p for p in packs if p["location"]==location)
    present={"type":"mod_loaded","modid":"vanillabackport"}
    main=find("wwoo:resources/wwoo_main")
    compat=find("wwoo:resources/vanilla_backport_compat")
    fallback=find("wwoo:resources/wwoo_standalone")
    assert main["condition"]==compat["condition"]==present
    assert fallback["condition"]=={"type":"not","condition":present}
    assert packs.index(compat)<packs.index(main)
    copied=0
    for name in original.namelist():
        if name.startswith("resources/wwoo_main/") and not name.endswith("/") and not name.endswith("/pack.mcmeta"):
            mapped=name.replace("resources/wwoo_main/","resources/wwoo_standalone/",1)
            assert jar.read(mapped)==original.read(name),mapped
            copied+=1
    assert copied>100,copied
    assert "resources/wwoo_standalone/pack.mcmeta" in jar.namelist()
    print("WWOO_STANDALONE_PACK_PASS",copied,"native 1.21.1 resources with no VanillaBackport")
    print("WWOO_OPTIONAL_BACKPORT_METADATA_PASS no version requirement, mutually exclusive packs")
