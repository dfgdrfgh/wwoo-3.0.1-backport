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
    modern_ores=find("wwoo:resources/wwoo_remove_ores")
    legacy_ores=find("wwoo:resources/wwoo_standalone_remove_ores")
    config={"type":"config_value","class_name":"de.cristelknight.wwoo.config.WWOOConfig",
            "key":"removeOres","expected":True}
    assert modern_ores["condition"]==[config,present], modern_ores
    assert legacy_ores["condition"]==[config,fallback["condition"]], legacy_ores
    assert "resources/wwoo_standalone_remove_ores/pack.mcmeta" in jar.namelist()
    old_ores=0
    for name in original.namelist():
        if name.startswith("resources/wwoo_remove_ores/") and not name.endswith("/") and not name.endswith("/pack.mcmeta"):
            mapped=name.replace("resources/wwoo_remove_ores/","resources/wwoo_standalone_remove_ores/",1)
            assert jar.read(mapped)==original.read(name),mapped
            old_ores+=1
    assert old_ores, "Missing original independent ore-removal resources"
    copied=0
    for name in original.namelist():
        if name.startswith("resources/wwoo_main/") and not name.endswith("/") and not name.endswith("/pack.mcmeta"):
            mapped=name.replace("resources/wwoo_main/","resources/wwoo_standalone/",1)
            assert jar.read(mapped)==original.read(name),mapped
            copied+=1
    assert copied>100,copied
    assert "resources/wwoo_standalone/pack.mcmeta" in jar.namelist()
    print("WWOO_STANDALONE_PACK_PASS",copied,"main and",old_ores,"ore","native 1.21.1 resources with no VanillaBackport")
    print("WWOO_OPTIONAL_BACKPORT_METADATA_PASS no version requirement, mutually exclusive packs")
