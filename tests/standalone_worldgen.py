"""Test WWOO standalone world generation on Minecraft 1.21.1 Fabric.
Use only WWOO, Cristel Lib and Fabric API, WITHOUT VanillaBackport.
Validate original 1.21.1-compatible WWOO native-biome generation and overworld chunks.
"""
from __future__ import annotations

import collections
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "port"))
from nbt import read, plain
import zlib

folder = ROOT / "test-standalone-wwoo"
mods = folder / "mods"
mods.mkdir(parents=True, exist_ok=True)
for src in (
    ROOT / "wwoo-fabric-1.21.1-3.0.1-backport.jar",
    ROOT / "test-dependencies" / "cristellib-fabric-1.21.1-3.1.7.jar",
    ROOT / "test-standalone-deps" / "fabric-api.jar",
):
    assert src.is_file(), "Missing test input: " + str(src)
    shutil.copy2(src, mods / src.name)
assert not any("vanillabackport" in p.name.lower() for p in mods.iterdir()), "VanillaBackport must be absent"
print("Verified standalone loadout: WWOO, Cristel Lib, Fabric API; no VanillaBackport", flush=True)
with zipfile.ZipFile(ROOT / "wwoo-fabric-1.21.1-3.0.1-backport.jar") as z:
    packs = json.loads(z.read("data/cristellib/data_pack/load_packs.json"))["packs"]
    main = next(x for x in packs if x["location"] == "wwoo:resources/wwoo_main")
    assert main["condition"] == {"type": "mod_loaded", "modid": "vanillabackport"}, main
    legacy = next(x for x in packs if x["location"] == "wwoo:resources/wwoo_legacy")
    assert legacy["condition"] == {"type": "not", "condition": {"type": "mod_loaded", "modid": "vanillabackport"}}, legacy
    assert "resources/wwoo_legacy/data/minecraft/worldgen/biome/old_growth_birch_forest.json" in z.namelist(), "Missing original native biome"
    print("Original 1.21.1 WWOO standalone datapack available and conditionally selected", flush=True)

(folder / "eula.txt").write_text("eula=true\n")
(folder / "server.properties").write_text(
    "online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n"
    "view-distance=3\nsimulation-distance=3\nmax-tick-time=-1\n"
    "level-seed=4523263735266840700\nlevel-name=world\n"
)
pack = folder / "world" / "datapacks" / "dappled-test"
test_dimension = pack / "data" / "wwootest" / "dimension" / "old_growth_birch_forest.json"
test_dimension.parent.mkdir(parents=True, exist_ok=True)
(pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 48, "description": "WWOO Old Growth Birch Forest generation test"}}))
test_dimension.write_text(json.dumps({
    "type": "minecraft:overworld",
    "generator": {
        "type": "minecraft:noise",
        "settings": "minecraft:overworld",
        "biome_source": {"type": "minecraft:fixed", "biome": "minecraft:old_growth_birch_forest"}
    }
}))

javadir = os.environ.get("WWOO_JAVA_HOME")
java = str(Path(javadir) / "bin" / "java") if javadir else (
    str(ROOT / "toolchain/jdk/bin/java") if (ROOT / "toolchain/jdk/bin/java").is_file()
    else shutil.which("java")
)
assert java
log = folder / "console.log"
# Use Fabric's supported production server launcher. The bootstrap's raw
# Mojang jar is in the official namespace, not the intermediary runtime expected
# by Fabric API mods.
launcher = ROOT / "test-standalone-deps" / "fabric-server-launch.jar"
assert launcher.is_file(), "Missing official Fabric server launcher"
shutil.copy2(launcher, folder / "fabric-server-launch.jar")
command = [java, "-Xmx4G", "-Djava.security.egd=file:/dev/./urandom",
           "-jar", "fabric-server-launch.jar", "nogui"]
started = False
sent = False
stopped = False
start = time.monotonic()
with log.open("w") as out:
    process = subprocess.Popen(command, cwd=folder, stdin=subprocess.PIPE,
                               stdout=out, stderr=subprocess.STDOUT, text=True)
    try:
        while process.poll() is None:
            trace = log.read_text(errors="replace")
            elapsed = time.monotonic() - start
            if not sent and "Done (" in trace:
                started = True
                print("Server started; generating Old Growth Birch Forest and normal Overworld chunks", flush=True)
                process.stdin.write(
                    "gamerule spawnChunkRadius 0\n"
                    "execute in wwootest:old_growth_birch_forest run forceload add 0 0 31 31\n"
                    "execute in minecraft:overworld run forceload add 0 0 31 31\n"
                    "execute in minecraft:overworld run locate biome minecraft:old_growth_birch_forest\n"
                    "say WWOO_STANDALONE_TEST_COMMANDS_SENT\n"
                )
                process.stdin.flush()
                sent = True
                issued_at = elapsed
            if sent and not stopped and elapsed > issued_at + 105:
                process.stdin.write("save-all flush\nstop\n")
                process.stdin.flush()
                stopped = True
            if elapsed > 850:
                raise TimeoutError("Two-fork world generation exceeded 850 seconds")
            time.sleep(1)
        print("Server exit code", process.returncode, flush=True)
        if process.returncode != 0 or not started or not stopped:
            print("STANDALONE_SERVER_FAILURE_LOG_TAIL:\n" + "\n".join(log.read_text(errors="replace").splitlines()[-180:]), flush=True)
            raise RuntimeError("Two-fork server failed before completing world generation")
    finally:
        if process.poll() is None:
            process.kill()
            process.wait()

trace = log.read_text(errors="replace")
assert "WWOO_STANDALONE_TEST_COMMANDS_SENT" in trace, "Server never processed test commands"
print("BIRCH_NATURAL_LOCATE_OUTPUT", [line for line in trace.splitlines() if "old_growth_birch_forest" in line or "Could not find" in line][-15:], flush=True)
assert "Saving chunks for level" in trace or "All dimensions are saved" in trace, "World was not saved"
assert 'minecraft:overworld' in trace and 'wwootest:old_growth_birch_forest' in trace, "Both overworld and fixed native-biome dimension must save"
assert "Unknown dimension" not in trace and "Unknown registry key" not in trace, "Registry/dimension errors found in console"

overworld_region = folder / "world" / "region" / "r.0.0.mca"
assert overworld_region.is_file(), "Normal Overworld did not generate terrain"
region = folder / "world" / "dimensions" / "wwootest" / "old_growth_birch_forest" / "region" / "r.0.0.mca"
assert region.is_file(), "Old Growth Birch Forest test dimension generated no region file"

def chunk_values(path: Path):
    data = path.read_bytes()
    for chunk_index in range(1024):
        offset = int.from_bytes(data[chunk_index * 4:chunk_index * 4 + 3], "big") * 4096
        if not offset:
            continue
        count = int.from_bytes(data[offset:offset + 4], "big")
        method = data[offset + 4]
        payload = data[offset + 5:offset + 4 + count]
        if method == 2: payload = zlib.decompress(payload)
        elif method != 1: continue
        yield plain(read(payload)[1])

biomes = collections.Counter()
blocks = collections.Counter()
full_chunks = 0
for chunk in chunk_values(region):
    if chunk.get("Status") == "minecraft:full":
        full_chunks += 1
    for section in chunk.get("sections", []):
        biomes.update(section.get("biomes", {}).get("palette", []))
        blocks.update(
            x["Name"] for x in section.get("block_states", {}).get("palette", [])
            if "Name" in x
        )

assert full_chunks >= 1, ("No fully generated Old Growth Birch Forest chunks", full_chunks)
assert biomes["minecraft:old_growth_birch_forest"] > 0, biomes
assert any(blocks[b] for b in ("minecraft:stone", "minecraft:dirt", "minecraft:grass_block")), blocks.most_common(15)
print("WWOO_STANDALONE_BIRCH_FOREST_PASS", json.dumps({
    "full_chunks": full_chunks,
    "biome_palette": biomes.most_common(8),
    "block_palette": blocks.most_common(25)
}), flush=True)

# Also inspect the normal Overworld save rather than relying solely on fixed-biome test.
overworld_full = sum(chunk.get("Status")=="minecraft:full" for chunk in chunk_values(overworld_region))
assert overworld_full >= 1, ("Normal Overworld generated no full chunks", overworld_full)
print("WWOO_STANDALONE_OVERWORLD_PASS", overworld_full, "full Overworld chunks", flush=True)
