"""Fetch the author's actual 1.21.1-compatible Modrinth 2032 datapack.

The query is constrained to the datapack loader and Minecraft 1.21.1.
Keep the upstream ZIP unmodified to reveal datapack/WWOO incompatibilities.
"""
import hashlib
import json
import pathlib
import sys
import urllib.parse
import urllib.request
import zipfile

ROOT = pathlib.Path(sys.argv[1])
ROOT.mkdir(parents=True, exist_ok=True)
BASE = "https://api.modrinth.com/v2/project/extended-world-height/version"
query = urllib.parse.urlencode({
    "loaders": json.dumps(["datapack"]),
    "game_versions": json.dumps(["1.21.1"]),
})
headers = {"User-Agent": "ww00-world-height-compatibility-test/1.0"}
with urllib.request.urlopen(urllib.request.Request(BASE + "?" + query, headers=headers), timeout=45) as response:
    versions = json.load(response)
assert versions, "No 1.21.1 datapack versions found on Modrinth"
releases = [v for v in versions if v.get("version_number") == "4.1"]
assert releases, "Expected 4.1 2032-height datapack release is unavailable for 1.21.1"
chosen = releases[0]
assert "datapack" in chosen.get("loaders", []), chosen
assert "1.21.1" in chosen.get("game_versions", []), chosen
files = chosen.get("files", [])
selected = next((f for f in files if f.get("primary") and f.get("filename","").endswith(".zip")), None)
if selected is None:
    selected = next((f for f in files if f.get("filename","").endswith(".zip")), None)
assert selected, "No datapack ZIP file in selected release"
url = selected["url"]
assert urllib.parse.urlparse(url).scheme == "https"
with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=90) as response:
    raw = response.read(20 * 1024 * 1024 + 1)
assert len(raw) <= 20 * 1024 * 1024, "Refusing unexpectedly large datapack"
for algorithm in ("sha512", "sha1"):
    expected = selected.get("hashes", {}).get(algorithm)
    if expected:
        actual = hashlib.new(algorithm, raw).hexdigest()
        assert actual == expected, f"Datapack {algorithm} mismatch"
path = ROOT / "extended-world-height-2032-modrinth.zip"
path.write_bytes(raw)
with zipfile.ZipFile(path) as pack:
    assert "pack.mcmeta" in pack.namelist(), "Datapack root missing pack.mcmeta"
    assert pack.testzip() is None
    overworld = "data/minecraft/dimension_type/overworld.json"
    assert overworld in pack.namelist(), "Expected Overworld dimension type missing"
    dimension = json.loads(pack.read(overworld))
    assert dimension["height"] >= 4000 and dimension["min_y"] <= -1000, dimension
print("MODRINTH_HEIGHT_SELECTED", json.dumps({
    "version_id": chosen["id"], "version_number": chosen["version_number"],
    "filename": selected["filename"], "sha512": hashlib.sha512(raw).hexdigest(),
    "overworld": {"min_y": dimension["min_y"], "height": dimension["height"]},
    "file_size": len(raw)
}), flush=True)
