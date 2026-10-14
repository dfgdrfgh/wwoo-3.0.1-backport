"""Ensure WWOO no longer bundles cloud rendering or cloud-specific palette data."""
from pathlib import Path
import json, zipfile
ROOT=Path(__file__).resolve().parent.parent
with zipfile.ZipFile(ROOT/'wwoo-fabric-1.21.1-3.0.1-backport.jar') as z:
    assert json.loads(z.read('fabric.mod.json'))['version']=='3.0.1+backport.1.21.1.17'
    mixins=json.loads(z.read('wwoo-content.client.mixins.json'))
    assert mixins['client']==['WWOOConfigScreenMixin']
    assert not any('Cloud' in n or 'CirrusShaderProgramMixin' in n for n in z.namelist() if n.endswith('.class'))
    for entry in json.loads(z.read('data/wwoocontent/biome_attributes.json')).values():
        assert 'minecraft:visual/cloud_color' not in entry.get('attributes',{})
print('NO_CLOUD_FEATURES_PASS: no cloud classes, hooks, or palette; config screen retained')
