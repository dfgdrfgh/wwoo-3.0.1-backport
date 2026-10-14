"""Ensure WWOO no longer bundles custom cloud rendering while preserving the original biome cloud-color data."""
from pathlib import Path
import json, zipfile
ROOT=Path(__file__).resolve().parent.parent
with zipfile.ZipFile(ROOT/'wwoo-fabric-1.21.1-3.0.1-backport.jar') as z:
    assert json.loads(z.read('fabric.mod.json'))['version']=='3.0.1+backport.1.21.1.18'
    mixins=json.loads(z.read('wwoo-content.client.mixins.json'))
    assert mixins['client']==['WWOOConfigScreenMixin']
    assert not any('Cloud' in n or 'CirrusShaderProgramMixin' in n for n in z.namelist() if n.endswith('.class'))
    palette={biome:entry['attributes']['minecraft:visual/cloud_color']
        for biome,entry in json.loads(z.read('data/wwoocontent/biome_attributes.json')).items()
        if 'minecraft:visual/cloud_color' in entry.get('attributes',{})}
    assert len(palette)==22
    assert palette['minecraft:pale_garden']=='#66828282'
    assert palette['minecraft:desert']=='#11ffffff'
print('NO_CLOUD_FEATURES_PASS: no cloud classes or hooks; 22 original biome cloud colors and config screen retained')
