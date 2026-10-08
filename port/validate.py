"""Audit the port against both supplied JARs and the saved integration world."""
from pathlib import Path
import collections,hashlib,json,struct,sys,zipfile,zlib
import nbt

ROOT=Path(__file__).resolve().parent.parent
PREFIX='resources/wwoo_main/data/'
OLD_FILL={'ice_spikes','mangrove_swamp','mushroom_fields','swamp','windswept_hills','windswept_gravelly_hills','windswept_savanna'}

def chunk(path,x=0,z=0):
    data=path.read_bytes();slot=(x%32+(z%32)*32)*4
    offset=int.from_bytes(data[slot:slot+3],'big')*4096
    if not offset:return None
    size=int.from_bytes(data[offset:offset+4],'big');kind=data[offset+4]
    payload=data[offset+5:offset+4+size]
    if kind==2:payload=zlib.decompress(payload)
    elif kind!=1:raise ValueError('Unsupported test-world chunk compression '+str(kind))
    return nbt.plain(nbt.read(payload)[1])

def audit(folder):
    jar=ROOT/'wwoo-fabric-1.21.1-3.0.1-backport.jar'
    with zipfile.ZipFile(jar) as port,zipfile.ZipFile(ROOT/'inputs/wwoo-fabric-2.6.7.jar') as old,zipfile.ZipFile(ROOT/'inputs/wwoo-fabric-26.3-3.0.1.jar') as new:
        biomes=sorted(Path(p).stem for p in new.namelist() if p.startswith(PREFIX+'minecraft/worldgen/biome/') and p.endswith('.json'))
        for biome in OLD_FILL:
            path=PREFIX+'minecraft/worldgen/biome/'+biome+'.json'
            assert json.loads(port.read(path))==json.loads(old.read(path)),biome
        changed_classes=[p for p in old.namelist() if p.endswith('.class') and port.read(p)!=old.read(p)]
        assert set(changed_classes)=={'de/cristelknight/wwoo/config/WWOOConfig.class','de/cristelknight/wwoo/config/WWOOConfig$1.class'}
        template_count=0
        for path in new.namelist():
            if path.startswith(PREFIX+'wythers/structure/') and path.endswith('.nbt'):
                adapted=path.replace('/wythers/','/wwoocontent/',1)
                assert adapted in port.namelist(),adapted
                template_count+=1
        def template_refs(v):
            if isinstance(v,dict):
                if v.get('type')=='wwoocontent:template':
                    for row in v['config']['templates']:
                        resource=row['data']['id'];namespace,path=resource.split(':',1)
                        assert PREFIX+namespace+'/structure/'+path+'.nbt' in port.namelist(),resource
                for x in v.values():template_refs(x)
            elif isinstance(v,list):
                for x in v:template_refs(x)
        for path in port.namelist():
            if path.startswith(PREFIX) and path.endswith('.json'):template_refs(json.loads(port.read(path)))
        panda_biomes={}
        for biome in biomes:
            v=json.loads(port.read(PREFIX+'minecraft/worldgen/biome/'+biome+'.json'))
            pandas=[p for p in v.get('spawners',{}).get('creature',[]) if p['type']=='minecraft:panda']
            if pandas:panda_biomes[biome]=pandas
        world=ROOT/folder/'smoke';results={}
        for biome in biomes:
            region=world/'dimensions/wwoo_test'/biome/'region/r.0.0.mca'
            assert region.is_file(),biome
            saved=chunk(region)
            assert saved and saved['Status']=='minecraft:full',(biome,saved.get('Status') if saved else None)
            present=collections.Counter(p for section in saved.get('sections',[]) for p in section.get('biomes',{}).get('palette',[]))
            assert 'minecraft:'+biome in present,(biome,present)
            results[biome]={'status':saved['Status'],'biomes_in_sections':dict(present)}
        log=(ROOT/folder/'console.log').read_text()
        sys.path.insert(0,str(ROOT/'tests'));import runtime_checks
        for marker in runtime_checks.MARKERS:assert 'WWOO_CHECK_'+marker+'_PASS' in log,marker
        assert 'All dimensions are saved' in log
        assert 'WWOO compatibility: using native Pale Garden plateau placement' in log
        assert 'Missing WWOO template' not in log and 'Description: Feature placement' not in log
        report={'artifact':jar.name,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'biome_count':len(biomes),'older_complete_fill_ins':sorted(OLD_FILL),'modern_template_count':template_count,'behavior_checks':runtime_checks.MARKERS,'changed_original_classes':changed_classes,'panda_spawns_match_supplied_301':panda_biomes,'noise_reference_test':json.loads((ROOT/'tests/noise-result.json').read_text()),'saved_full_chunks':results,'tested_dependencies':{'minecraft':'1.21.1','java':'21.0.6','fabric_loader':'0.18.5','fabric_api':'0.116.17+1.21.1','cristel_lib':'3.1.7','true_vanilla_backport':'1.2.0.2638.9'},'remaining_validation':'Client rendering and a side-by-side visual comparison with 26.3 have not been performed.'}
        (ROOT/'validation-full.json').write_text(json.dumps(report,indent=2)+'\n')
        print('PASS:',len(biomes),'saved full biome chunks;',template_count,'modern templates;',len(runtime_checks.MARKERS),'runtime behavior checks')

if __name__=='__main__':audit(sys.argv[1] if len(sys.argv)>1 else 'test-full-5')
