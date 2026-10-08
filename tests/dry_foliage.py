"""Exercise the build's biome loop and compare packaged color overrides to 3.0.1."""
from pathlib import Path
import ast,copy,json,sys,zipfile
root=Path(__file__).resolve().parent.parent
jar=Path(sys.argv[1]) if len(sys.argv)>1 else root/'wwoo-fabric-1.21.1-3.0.1-backport.jar'
prefix='resources/wwoo_main/data/'
old=zipfile.ZipFile(root/'inputs/wwoo-fabric-2.6.7.jar')
original=zipfile.ZipFile(root/'inputs/wwoo-fabric-26.3-3.0.1.jar')
selected={p.removeprefix(prefix):json.loads(original.read(p)) for p in original.namelist() if p.startswith(prefix+'minecraft/worldgen/biome/') and p.endswith('.json')}
excluded={'ice_spikes','mangrove_swamp','mushroom_fields','swamp','windswept_hills','windswept_gravelly_hills','windswept_savanna'}
tree=ast.parse((root/'port/build.py').read_text())
# Run the production biome conversion loop without compiling unchanged Java.
loop=next(n for n in tree.body if isinstance(n,ast.For) and 'effects.pop' in ast.unparse(n))
captured={}
env={'selected':selected,'Path':Path,'EXCEPTIONS':excluded,'PREFIX':prefix,'old':old,'copy':copy,'json':json,'visual_attributes':{},'put':lambda n,v:captured.update({n:v}),'convert':lambda v,k='':v}
exec(compile(ast.Module(body=[loop],type_ignores=[]),'build-biome-loop','exec'),env)
expected={}
for n,v in selected.items():
 biome=Path(n).stem;color=v.get('effects',{}).get('dry_foliage_color')
 if biome in excluded or color is None:continue
 assert env['visual_attributes']['minecraft:'+biome]['effects']['dry_foliage_color']==color,biome
 color=int(color[1:],16) if isinstance(color,str) else color
 expected[biome]=color
with zipfile.ZipFile(jar) as port:
 names=[n for n in port.namelist() if n.startswith('assets/wwoocontent/dry_foliage_colors/') and n.endswith('.json')]
 assert len(names)==len(expected),(len(names),len(expected))
 for biome,color in expected.items():
  actual=json.loads(port.read('assets/wwoocontent/dry_foliage_colors/'+biome+'.json'))
  assert actual=={'biome':['minecraft:'+biome],'color':color,'priority':100},biome
 print('DRY_FOLIAGE_PASS',len(expected),'source-matched biome overrides')
