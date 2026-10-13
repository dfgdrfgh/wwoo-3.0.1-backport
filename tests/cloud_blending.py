"""Validate release palettes; native sky-blend logic is checked in the Fabric client renderer test."""
from pathlib import Path
import zipfile,json
ROOT=Path(__file__).resolve().parent.parent
JAR=ROOT/'wwoo-fabric-1.21.1-3.0.1-backport.jar'
with zipfile.ZipFile(JAR) as z:
    assert 'com/zachminecraft/wwoocontent/CloudBlend.class' in z.namelist()
    assert not any('CloudBlendCheck' in n for n in z.namelist())
    # Preserve encoded biome data; rendering uses RGB without animating its opacity.
    attributes=json.loads(z.read('data/wwoocontent/biome_attributes.json'))
    assert attributes['minecraft:pale_garden']['attributes']['minecraft:visual/cloud_color']=='#66828282'
    assert attributes['minecraft:desert']['attributes']['minecraft:visual/cloud_color']=='#11ffffff'
print('CLOUD_ATTRIBUTES_PASS: encoded palettes retained; native spatial checks run in tests/cloud_rendering.py')
