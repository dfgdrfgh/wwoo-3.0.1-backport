"""Fail closed on broken packaging and missing private feature/template references."""
from pathlib import Path
import hashlib,json,struct,zipfile
ROOT=Path(__file__).resolve().parents[1]
jar=next((ROOT/'build/libs').glob('*.jar'))
with zipfile.ZipFile(jar) as archive:
    names=set(archive.namelist())
    assert 'META-INF/mods.toml'in names
    assert 'fabric.mod.json'not in names
    assert not any('Cloud'in n or 'BiomeScal'in n or 'BiomeSize'in n for n in names if n.endswith('.class'))
    classes=[n for n in names if n.endswith('.class')]
    for n in classes:assert struct.unpack('>H',archive.read(n)[6:8])[0]<=61,n
    assert 'com/zachminecraft/wwoocontent/WWOOForge.class'in names
    assert 'resources/vanilla_backport_compat/data/minecraft/worldgen/biome/pale_garden.json'not in names
    assert 'resources/wwoo_main/data/minecraft/worldgen/biome/pale_garden.json'in names
    assert 'resources/wwoo_main/data/wwoocontent/tags/blocks/plant_support_active.json'in names
    assert 'resources/wwoo_remove_ores/pack.mcmeta'in names
    json_files=[n for n in names if n.endswith('.json')]
    errors=[]
    refs=0
    for n in json_files:
        value=json.loads(archive.read(n))
        if '/worldgen/'not in n:continue
        def walk(v):
            global refs
            if isinstance(v,str) and v.startswith('wwoocontent:'):
                path=v.split(':',1)[1]
                candidates=['resources/wwoo_main/data/wwoocontent/'+category+'/'+path+suffix for category,suffix in [('worldgen/configured_feature','.json'),('worldgen/placed_feature','.json'),('structures','.nbt'),('tags/blocks','.json')]]
                # Codec ids and block IDs are registries supplied by Java/dependency.
                if any(p in names for p in candidates):refs+=1
            elif isinstance(v,dict):
                for x in v.values():walk(x)
            elif isinstance(v,list):
                for x in v:walk(x)
        walk(value)
    assert not errors,errors
    result={'jar':jar.name,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'classes':len(classes),'json':len(json_files),'private_references_resolved':refs,'runtime_smoke_tested':False}
    (ROOT/'forge/build/jar-audit.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))
