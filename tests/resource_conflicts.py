"""Keep imported resources isolated from older WWOO definitions."""
from pathlib import Path
import json,sys,zipfile
root=Path(__file__).resolve().parent.parent
jar=Path(sys.argv[1]) if len(sys.argv)>1 else root/'wwoo-fabric-1.21.1-3.0.1-backport.jar'
def legacy_refs(v):
 if isinstance(v,str):return [v] if v.startswith(('wythers:','#wythers:')) else []
 if isinstance(v,dict):return [s for x in v.values() for s in legacy_refs(x)]
 if isinstance(v,list):return [s for x in v for s in legacy_refs(x)]
 return []
with zipfile.ZipFile(jar) as z:
 main={n.removeprefix('resources/wwoo_main/'):n for n in z.namelist() if n.startswith('resources/wwoo_main/data/') and n.endswith('.json')}
 compat={n.removeprefix('resources/vanilla_backport_compat/'):n for n in z.namelist() if n.startswith('resources/vanilla_backport_compat/data/') and n.endswith('.json')}
 assert not (main.keys()&compat.keys()),'Main pack is shadowed by compatibility resources'
 count=0
 for n in z.namelist():
  if n.endswith('.json') and n.startswith(('resources/wwoo_main/data/wwoocontent/worldgen/','resources/wwoo_main/data/minecraft/tags/block/')):
   assert not legacy_refs(json.loads(z.read(n))),n
   count+=1
 with zipfile.ZipFile(root/'inputs/wwoo-fabric-2.6.7.jar') as old:
  for biome in ['ice_spikes','mangrove_swamp','mushroom_fields','swamp','windswept_hills','windswept_gravelly_hills','windswept_savanna']:
   n='resources/wwoo_main/data/minecraft/worldgen/biome/'+biome+'.json'
   assert json.loads(z.read(n))==json.loads(old.read(n)),biome
 print('RESOURCE_CONFLICT_PASS',count,'resources; older biome definitions preserved')
