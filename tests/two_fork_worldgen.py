"""Check ONLY WWOO and TrueVanillaBackport on a real 1.21.1 Fabric server.

Uses a fixed Dappled Forest test dimension, so we can test its feature/terrain
generation independent of whether the seed places that biome near spawn.
The Overworld is also generated normally.
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

folder = ROOT / "test-two-forks"
mods = folder / "mods"
mods.mkdir(parents=True, exist_ok=True)
for src in (
    ROOT / "wwoo-fabric-1.21.1-3.0.1-backport.jar",
    ROOT / "test-dependencies" / "cristellib-fabric-1.21.1-3.1.7.jar",
    ROOT / "test-two-forks-deps" / "VanillaBackport.jar",
    ROOT / "test-two-forks-deps" / "fabric-api.jar",
):
    assert src.is_file(), "Missing test input: " + str(src)
    shutil.copy2(src, mods / src.name)
with zipfile.ZipFile(ROOT / "test-two-forks-deps" / "VanillaBackport.jar") as z:
    m = json.loads(z.read("fabric.mod.json"))
    assert m["id"] == "vanillabackport", m["id"]
    print("Testing VanillaBackport", m["version"], flush=True)
with zipfile.ZipFile(ROOT / "wwoo-fabric-1.21.1-3.0.1-backport.jar") as z:
    packs = json.loads(z.read("data/cristellib/data_pack/load_packs.json"))["packs"]
    main = next(x for x in packs if x["location"] == "wwoo:resources/wwoo_main")
    assert main["condition"] == {"type": "mod_loaded", "modid": "vanillabackport"}, main
    assert "resources/wwoo_main/data/minecraft/worldgen/biome/dappled_forest.json" in z.namelist()
    print("WWOO's 3.0.1 Dappled Forest definition exists; condition permits it", flush=True)

(folder / "eula.txt").write_text("eula=true\n")
(folder / "server.properties").write_text(
    "online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n"
    "view-distance=3\nsimulation-distance=3\nmax-tick-time=-1\n"
    "level-seed=4523263735266840700\nlevel-name=world\n"
)
pack = folder / "world" / "datapacks" / "dappled-test"
test_dimension = pack / "data" / "wwootest" / "dimension" / "dappled_forest.json"
test_dimension.parent.mkdir(parents=True, exist_ok=True)
(pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 48, "description": "WWOO Dappled Forest generation test"}}))
test_dimension.write_text(json.dumps({
    "type": "minecraft:overworld",
    "generator": {
        "type": "minecraft:noise",
        "settings": "minecraft:overworld",
        "biome_source": {"type": "minecraft:fixed", "biome": "minecraft:dappled_forest"}
    }
}))

javadir = os.environ.get("WWOO_JAVA_HOME")
java = str(Path(javadir) / "bin" / "java") if javadir else (
    str(ROOT / "toolchain/jdk/bin/java") if (ROOT / "toolchain/jdk/bin/java").is_file()
    else shutil.which("java")
)
assert java
log = folder / "console.log"
command = [
    java, "-Xmx4G", "-Djava.security.egd=file:/dev/./urandom",
    "-Dfabric.gameJarPath=" + str(ROOT / "test-runtime/versions/1.21.1/server-1.21.1.jar"),
    "-cp", (ROOT / "test-runtime/launch-classpath.txt").read_text(),
    "net.fabricmc.loader.impl.launch.knot.KnotServer", "nogui",
]
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
                print("Server started; generating Dappled Forest and normal Overworld chunks", flush=True)
                process.stdin.write(
                    "gamerule spawnChunkRadius 0\n"
                    "execute in wwootest:dappled_forest run forceload add 0 0 31 31\n"
                    "execute in minecraft:overworld run forceload add 0 0 31 31\n"
                    "say WWOO_TWO_FORK_TEST_COMMANDS_SENT\n"
                )
                process.stdin.flush()
                sent = True
                issued_at = elapsed
            if sent and not stopped and elapsed > issued_at + 45:
                process.stdin.write("save-all flush\nstop\n")
                process.stdin.flush()
                stopped = True
            if elapsed > 850:
                raise TimeoutError("Two-fork world generation exceeded 850 seconds")
            time.sleep(1)
        print("Server exit code", process.returncode, flush=True)
        if process.returncode != 0 or not started or not stopped:
            print("TWO_FORK_SERVER_FAILURE_LOG_TAIL:\n" + "\n".join(log.read_text(errors="replace").splitlines()[-180:]), flush=True)
            raise RuntimeError("Two-fork server failed before completing world generation")
    finally:
        if process.poll() is None:
            process.kill()
            process.wait()

trace = log.read_text(errors="replace")
assert "WWOO_TWO_FORK_TEST_COMMANDS_SENT" in trace, "Server never processed test commands"
assert "Saving chunks for level" in trace or "All dimensions are saved" in trace, "World was not saved"
assert "Unknown dimension" not in trace and "Unknown registry key" not in trace, "Registry/dimension errors found in console"

region = folder / "world" / "dimensions" / "wwootest" / "dappled_forest" / "region" / "r.0.0.mca"
assert region.is_file(), "Dappled Forest test dimension generated no region file"

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

assert full_chunks >= 1, ("No fully generated Dappled Forest chunks", full_chunks)
assert biomes["minecraft:dappled_forest"] > 0, biomes
assert any(blocks[b] for b in ("minecraft:stone", "minecraft:dirt", "minecraft:grass_block")), blocks.most_common(15)
print("WWOO_TWO_FORK_DAPPLED_FOREST_PASS", json.dumps({
    "full_chunks": full_chunks,
    "biome_palette": biomes.most_common(8),
    "block_palette": blocks.most_common(25)
}), flush=True)
