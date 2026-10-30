"""Fetch Fabric Platform 1.4.2 for the 1.21.1 VanillaBackport test only."""
import hashlib
import json
from pathlib import Path
import sys
import urllib.parse
import urllib.request
import zipfile

dest=Path(sys.argv[1])
dest.mkdir(parents=True,exist_ok=True)
headers={"User-Agent":"WWOO-1.21.1-compatibility-test/1.0"}
url="https://api.modrinth.com/v2/project/platform/version?" + urllib.parse.urlencode({
    "loaders":json.dumps(["fabric"]),
    "game_versions":json.dumps(["1.21.1"]),
})
modrinth_file=None
try:
    with urllib.request.urlopen(urllib.request.Request(url,headers=headers),timeout=40) as response:
        versions=json.load(response)
    for release in versions:
        for f in release.get("files", []):
            if f.get("filename","").lower()=="platform-fabric-1.21.1-1.4.2.jar":
                modrinth_file=f
                break
        if modrinth_file:break
except (OSError,ValueError) as e:
    print("Modrinth Platform lookup unavailable:",repr(e),flush=True)

download=modrinth_file["url"] if modrinth_file else "https://mediafilez.forgecdn.net/files/9318/521/Platform-fabric-1.21.1-1.4.2.jar"
with urllib.request.urlopen(urllib.request.Request(download,headers=headers),timeout=90) as response:
    raw=response.read(3*1024*1024+1)
assert len(raw)<3*1024*1024,"Unexpected Platform JAR size"
if modrinth_file:
    for algo in ("sha512","sha1"):
        expected=modrinth_file.get("hashes",{}).get(algo)
        if expected:
            assert hashlib.new(algo,raw).hexdigest()==expected,"Platform hash mismatch"
path=dest/"platform-fabric-1.21.1-1.4.2.jar"
path.write_bytes(raw)
with zipfile.ZipFile(path) as jar:
    assert jar.testzip() is None
    meta=json.loads(jar.read("fabric.mod.json"))
assert meta["id"]=="platform",(path,meta.get("id"))
assert str(meta["version"]).startswith("1.4.2"),meta["version"]
print("PLATFORM_FOR_TEST",json.dumps({"version":meta["version"],"size":len(raw),"sha256":hashlib.sha256(raw).hexdigest()}),flush=True)
