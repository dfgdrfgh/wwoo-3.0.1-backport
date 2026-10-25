"""Verify WWOO 3.0.1 stays active without VanillaBackport."""
import json
import zipfile
with zipfile.ZipFile("wwoo-fabric-1.21.1-3.0.1-backport.jar") as jar:
    meta=json.loads(jar.read("fabric.mod.json"))
    assert "vanillabackport" not in meta["depends"],meta["depends"]
    assert meta.get("suggests",{}).get("vanillabackport")=="*",meta.get("suggests")
    packs=json.loads(jar.read("data/cristellib/data_pack/load_packs.json"))["packs"]
    find=lambda loc:next(p for p in packs if p["location"]==loc)
    present={"type":"mod_loaded","modid":"vanillabackport"}
    main=find("wwoo:resources/wwoo_main")
    compat=find("wwoo:resources/vanilla_backport_compat")
    standalone=find("wwoo:resources/wwoo_standalone")
    assert main["condition"]==compat["condition"]==present
    assert standalone["condition"]=={"type":"not","condition":present}
    assert packs.index(compat)<packs.index(main)
    assert find("wwoo:resources/wwoo_remove_ores")["condition"]["type"]=="config_value"
    prefix="resources/wwoo_main/data/"
    fallback="resources/wwoo_standalone/data/"
    assert "resources/wwoo_standalone/pack.mcmeta" in jar.namelist()
    for biome in ("plains","forest","birch_forest","cherry_grove","meadow"):
        original=prefix+"minecraft/worldgen/biome/"+biome+".json"
        alternative=fallback+"minecraft/worldgen/biome/"+biome+".json"
        assert json.loads(jar.read(original))==json.loads(jar.read(alternative)),biome
    for biome in ("pale_garden",):
        assert fallback+"minecraft/worldgen/biome/"+biome+".json" not in jar.namelist(),biome
    modern={n[len(prefix):] for n in jar.namelist() if n.startswith(prefix) and n.endswith(".json")}
    native={n[len(fallback):] for n in jar.namelist() if n.startswith(fallback) and n.endswith(".json")}
    assert len(native)>100,(len(native),len(modern))
    assert len(modern&native)>100
    print("WWOO_STANDALONE_301_PARITY_PASS",len(native),"standalone resources")
    print("WWOO_OPTIONAL_BACKPORT_METADATA_PASS no version gate; 3.0.1 biome definitions unchanged")
