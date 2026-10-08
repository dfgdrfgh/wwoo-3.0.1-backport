"""Guard the Pale Garden overhaul against persisted compatibility pack order."""
from pathlib import Path
import zipfile,json,sys
jar=Path(sys.argv[1]) if len(sys.argv)>1 else Path(__file__).resolve().parent.parent/'wwoo-fabric-1.21.1-3.0.1-backport.jar'
with zipfile.ZipFile(jar) as z:
 main='resources/wwoo_main/data/minecraft/worldgen/biome/pale_garden.json'
 compat=main.replace('wwoo_main','vanilla_backport_compat')
 assert compat not in z.namelist(),'Compatibility pack would override the modern biome in existing worlds'
 v=json.loads(z.read(main))
 assert 'wwoocontent:pale_garden/landscaping' in v['features'][0]
 assert 'wwoocontent:pale_garden/shrubbery' in v['features'][9]
 tree_tag=json.loads(z.read('resources/wwoo_main/data/minecraft/tags/block/replaceable_by_trees.json'))
 assert '#wwoocontent:air_and_plants' in tree_tag['values']
 assert '#wythers:air_and_plants' not in tree_tag['values']
 for order in [('wwoo_main','vanilla_backport_compat'),('vanilla_backport_compat','wwoo_main')]:
  loaded=[f'resources/{p}/data/minecraft/worldgen/biome/pale_garden.json' for p in order]
  loaded=[p for p in loaded if p in z.namelist()]
  assert loaded[-1]==main
 print('PALE_GARDEN_PACK_PRIORITY_PASS')
