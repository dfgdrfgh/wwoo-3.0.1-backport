"""Read-only comparison of Sift's Sonorous Deepslate with DnT 1.21.1 city centers."""
from io import BytesIO
from pathlib import Path
import sys, urllib.request, zipfile

sys.path.insert(0, str(Path("port").resolve()))
from nbt import read

URL = "https://cdn.modrinth.com/data/DNuNq5bb/versions/khb6eZvR/dungeons-and-taverns-ancient-city-overhaul-v2.jar"
SIFT = "https://raw.githubusercontent.com/dfgdrfgh/Mielon-s-The-Sift-1.21.1-Patch/main/src/main/resources/"

def download(url):
    req = urllib.request.Request(url, headers={"User-Agent": "WWOO-Minecraft-compatibility-audit"})
    with urllib.request.urlopen(req, timeout=60) as response:
        return response.read()

def template(data):
    _, root = read(data)
    names = [p["Name"][1] for p in root["palette"][1][1]]
    blocks = root["blocks"][1][1]
    positions = {tuple(b["pos"][1][1]): names[b["state"][1]] for b in blocks}
    return tuple(root["size"][1][1]), positions

with zipfile.ZipFile(BytesIO(download(URL))) as jar:
    for n in (1, 2, 3):
        path = f"data/minecraft/structure/ancient_city/city_center/city_center_{n}.nbt"
        sift_size, sift = template(download(SIFT + path))
        dnt_size, dnt = template(jar.read(path))
        targets = sorted(pos for pos, block in sift.items() if block == "the_sift:sonorous_deepslate")
        print(f"CITY_CENTER_{n}: Sift size {sift_size}, DnT size {dnt_size}, matching={sift_size == dnt_size}")
        for pos in targets:
            print(f"  {pos}: Sift=sonorous_deepslate; DnT={dnt.get(pos, 'ABSENT')}")
        print(f"  total Sift placements={len(targets)}; missing target coordinates={sum(pos not in dnt for pos in targets)}")
