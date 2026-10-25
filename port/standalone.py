"""Generate a 3.0.1 standalone pack, omitting only unavailable vanilla-backported content.

Keep the converted 3.0.1 definitions for all native Minecraft 1.21.1 biomes.
Only replace placed/configured features that need blocks that do not exist in
1.21.1's vanilla registry, rather than reverting every biome to WWOO 2.6.7.
"""
import copy
import json
import re
import subprocess
from pathlib import Path

PREFIX="resources/wwoo_main/data/"
DEST="resources/wwoo_standalone/data/"

def builtin_ids(root, type_name):
    path=root/"minecraft-named.jar"
    jdk=root/"toolchain/jdk/bin/javap"
    tool=str(jdk) if jdk.exists() else "javap"
    text=subprocess.check_output([tool,"-classpath",str(path),
        "net.minecraft.world.level."+type_name],text=True)
    ids=set()
    for line in text.splitlines():
        if " public static final " in " "+line and ";" in line:
            match=re.search(r"\b([A-Z][A-Z0-9_]+);$",line.strip())
            if match:ids.add(match.group(1).lower())
    if len(ids)<40:
        raise RuntimeError("Cannot inspect vanilla Minecraft 1.21.1 "+type_name+" registry: "+repr(sorted(ids)))
    return ids

UNAVAILABLE_1211_CODECS={
    "minecraft:attached_to_logs","minecraft:shelf_mushroom",
    "minecraft:poplar_foliage_placer","minecraft:poplar_trunk_placer",
    "minecraft:weighted_random_selector","minecraft:fallen_tree",
    "minecraft:speleothem_cluster","minecraft:speleothem",
}

def unavailable_block(v, blocks, key=""):
    if isinstance(v,dict):
        if v.get("type") in UNAVAILABLE_1211_CODECS:return True
        for k,value in v.items():
            if unavailable_block(value,blocks,k):return True
    elif isinstance(v,list):
        return any(unavailable_block(item,blocks,key) for item in v)
    elif isinstance(v,str):
        if key=="type" and v in UNAVAILABLE_1211_CODECS:return True
        if key in ("Name","block","blocks","state","output_state","base_block","pointed_block"):
            name=v.removeprefix("minecraft:")
            return ":" not in name and name not in blocks
    return False

def compatible(v):
    if isinstance(v,dict):
        if v.get("type")=="minecraft:trapezoid" and (
                ("min" in v and "max" in v) or
                ("min_inclusive" in v and "max_inclusive" in v)):
            # Only int providers have min/max bounds. Do not rewrite unrelated
            # worldgen elements that happen to share this type name.
            return {"type":"minecraft:uniform",
                    "min_inclusive":v.get("min",v.get("min_inclusive")),
                    "max_inclusive":v.get("max",v.get("max_inclusive"))}
        return {k:compatible(value) for k,value in v.items()}
    if isinstance(v,list):return [compatible(item) for item in v]
    return v

def install(entries, old, root, put):
    blocks=builtin_ids(root,"block.Blocks")
    vanilla_biomes=builtin_ids(root,"biome.Biomes")
    copied=0;skipped=[];substituted=[]
    for path,raw in list(entries.items()):
        if not path.startswith(PREFIX):continue
        rel=path[len(PREFIX):]
        target=DEST+rel
        if rel.startswith("minecraft/worldgen/biome/"):
            biome=rel.split("/")[-1].removesuffix(".json")
            if biome not in vanilla_biomes:
                skipped.append(biome)
                continue
        if path.endswith(".json"):
            try:v=json.loads(raw)
            except (ValueError,UnicodeDecodeError):entries[target]=raw;continue
            v=compatible(v)
            if unavailable_block(v,blocks):
                if "/worldgen/configured_feature/" in rel:
                    v={"type":"minecraft:no_op","config":{}}
                elif "/worldgen/placed_feature/" in rel:
                    v={"feature":{"type":"minecraft:no_op","config":{}},"placement":[]}
                else:
                    # Other incompatible worldgen resources require special handling:
                    # retain the old known-compatible definition where available.
                    legacy=path
                    if legacy in old.namelist():
                        v=json.loads(old.read(legacy))
                    else:
                        raise ValueError("Unadapted absent block in standalone "+rel)
                substituted.append(rel)
            put(target,v)
        else:entries[target]=raw
        copied+=1
    put("resources/wwoo_standalone/pack.mcmeta",{"pack":{"pack_format":48,
        "description":"WWOO 3.0.1 — native 1.21.1 biomes (no VanillaBackport)"}})
    print("WWOO_STANDALONE_MODERN",json.dumps({"converted_301_resources":copied,
        "omitted_unavailable_biomes":sorted(skipped),
        "optional_feature_fallbacks":len(substituted),
        "sample":substituted[:30]}))
    assert "pale_garden" in skipped
    assert copied>100
