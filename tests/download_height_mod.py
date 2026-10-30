"""Fetch the genuine Modrinth Extended World Height Fabric mod for MC 1.21.1."""
from pathlib import Path
import json, hashlib, sys, urllib.request, urllib.parse, zipfile
target=Path(sys.argv[1])
target.mkdir(parents=True, exist_ok=True)
headers={"User-Agent":"WWOO-Fabric-1.21.1-world-height-test/1.0"}
api="https://api.modrinth.com/v2/project/extended-world-height/version?"
query=urllib.parse.urlencode({"loaders":json.dumps(["fabric"]),"game_versions":json.dumps(["1.21.1"])})
with urllib.request.urlopen(urllib.request.Request(api+query,headers=headers),timeout=45) as response:
    versions=json.load(response)
version=next((v for v in versions if v.get("version_number")=="4.1+mod"),None)
assert version is not None, "Cannot find Extended World Height 4.1+mod Fabric 1.21.1"
assert "fabric" in version.get("loaders",[]) and "1.21.1" in version.get("game_versions",[]),version
files=version["files"]
selected=next((f for f in files if f.get("primary") and f.get("filename","").endswith(".jar")),None)
if not selected:
    selected=next((f for f in files if f.get("filename","").endswith(".jar")),None)
assert selected is not None,"Modrinth release has no Fabric JAR"
with urllib.request.urlopen(urllib.request.Request(selected["url"],headers=headers),timeout=90) as response:
    data=response.read(10*1024*1024+1)
assert len(data)<=10*1024*1024
for name,expected in selected.get("hashes",{}).items():
    if name in ("sha512","sha1"):
        assert hashlib.new(name,data).hexdigest()==expected, "Modrinth "+name+" mismatch"
path=target/"extended-world-height-modrinth-4.1.jar"
path.write_bytes(data)
with zipfile.ZipFile(path) as z:
    assert z.testzip() is None
    meta=json.loads(z.read("fabric.mod.json"))
print("MODRINTH_HEIGHT_MOD_SELECTED",json.dumps({
    "version_id":version["id"],"version":version["version_number"],
    "filename":selected["filename"],"mod_id":meta["id"],
    "mod_version":meta.get("version"),
    "sha512":hashlib.sha512(data).hexdigest(),
    "bytes":len(data)
}),flush=True)
