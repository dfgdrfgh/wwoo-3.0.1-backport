"""Convert the complete main-branch WWOO content to Minecraft 1.20.1 schemas.

No Fabric classes are copied into the Forge mod. The shared converter preserves
all selected 3.0.1 content, older completed biomes, namespacing and ice protection.
"""
from pathlib import Path
import hashlib,json,os,re,shutil,subprocess,sys,urllib.request,zipfile
ROOT=Path(__file__).resolve().parents[1]
GENERATED=ROOT/'forge/build/generated-resources'

def download(url,path,sha1=None):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
    if not path.exists():
        with urllib.request.urlopen(url,timeout=90) as response:path.write_bytes(response.read())
    if sha1 and hashlib.sha1(path.read_bytes()).hexdigest()!=sha1:
        path.unlink();raise ValueError('Checksum mismatch: '+str(path))
    return path

def target_path(path):
    for current,legacy in [('tags/block/','tags/blocks/'),('tags/item/','tags/items/'),('tags/entity_type/','tags/entity_types/'),('tags/fluid/','tags/fluids/'),('tags/game_event/','tags/game_events/'),('/loot_table/','/loot_tables/'),('/structure/','/structures/'),('/advancement/','/advancements/'),('/recipe/','/recipes/')]:
        path=path.replace(current,legacy)
    return path

def target_json(value):
    if isinstance(value,str):
        return {'minecraft:short_grass':'minecraft:grass'}.get(value,value)
    if isinstance(value,list):return [target_json(v) for v in value]
    if not isinstance(value,dict):return value
    result={k:target_json(v) for k,v in value.items()}
    if result.get('function')=='minecraft:enchanted_count_increase':
        if result.pop('enchantment')!='minecraft:looting':raise ValueError('Unsupported enchanted count bonus')
        result['function']='minecraft:looting_enchant'
    if result.get('condition')=='minecraft:match_tool':
        predicate=result.get('predicate',{})
        modern=predicate.pop('predicates',{})
        for name,entries in modern.items():
            if name!='minecraft:enchantments':raise ValueError('Unsupported modern tool predicate '+name)
            predicate['enchantments']=[{('enchantment' if k=='enchantments' else k):v for k,v in entry.items()} for entry in entries]
        if isinstance(predicate.get('items'),str):predicate['items']=[predicate['items']]
    # 1.20.1 loot conditions have only any_of/inverted; all_of is conjunction
    # expressed by De Morgan without changing nested semantics.
    if result.get('condition')=='minecraft:all_of':
        return {'condition':'minecraft:inverted','term':{'condition':'minecraft:any_of','terms':[{'condition':'minecraft:inverted','term':t} for t in result['terms']]}}
    if result.get('function')=='minecraft:set_components':
        components=result.pop('components')
        tag={}
        for name,data in components.items():
            if name in ('minecraft:custom_name','minecraft:item_name'):tag.setdefault('display',{})['Name']=data
            elif name=='minecraft:food':
                # Custom food properties require a gameplay implementation in
                # the companion backport, never silently discard components.
                tag['wwoo_food']=data
            else:raise ValueError('Unsupported item component '+name)
        # SNBT compounds accept JSON syntax for this string/numeric subset.
        result['function']='minecraft:set_nbt';result['tag']=json.dumps(tag,separators=(',',':'))
    return result

def main():
    metadata=json.loads((ROOT/'toolchain/26.3-version.json').read_text())
    bundle=download(metadata['downloads']['server']['url'],ROOT/'toolchain/server-26.3-bundle.jar',metadata['downloads']['server']['sha1'])
    modern=ROOT/'toolchain/minecraft-26.3.jar'
    if not modern.exists():
        with zipfile.ZipFile(bundle) as z:modern.write_bytes(z.read('META-INF/versions/'+metadata['id']+'/server-'+metadata['id']+'.jar'))
    with urllib.request.urlopen('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json',timeout=90) as response:manifest=json.load(response)
    entry=next(v for v in manifest['versions'] if v['id']=='1.20.1')
    with urllib.request.urlopen(entry['url'],timeout=90) as response:target_meta=json.load(response)
    target=download(target_meta['downloads']['client']['url'],ROOT/'toolchain/target-1.20.1.jar',target_meta['downloads']['client']['sha1'])
    intermediate=ROOT/'forge/build/content-1.21.1.zip'
    env={**os.environ,'WWOO_RESOURCES_ONLY':str(intermediate),'WWOO_TARGET_RESOURCES':str(target)}
    subprocess.run([sys.executable,str(ROOT/'port/build.py')],env=env,check=True,cwd=ROOT)
    if GENERATED.exists():shutil.rmtree(GENERATED)
    GENERATED.mkdir(parents=True)
    report={'source_commit':'3f712fe','target':'1.20.1-forge','files':0,'templates':0,'biomes':[]}
    sys.path.insert(0,str(ROOT/'port'));import nbt
    with zipfile.ZipFile(intermediate) as archive:
        for name in archive.namelist():
            if not (name.startswith(('resources/','data/','assets/')) or name.lower().startswith('license')):continue
            # Forge supplies native packs; Cristel's loader metadata is obsolete.
            if name.startswith('data/cristellib/'):continue
            path=GENERATED/target_path(name)
            path.parent.mkdir(parents=True,exist_ok=True)
            data=archive.read(name)
            if name.endswith('.json'):
                data=(json.dumps(target_json(json.loads(data)),indent=2)+'\n').encode()
            elif name.endswith('pack.mcmeta'):
                meta=json.loads(data);meta['pack']['pack_format']=15;data=json.dumps(meta).encode()
            elif name.endswith('.nbt'):
                root_name,value=nbt.read(data)
                # 1.21 uses plural lower-case attributes; 1.20.1 uses Attributes
                # with Name/Base. Leave template-local entity custom NBT intact.
                def convert_nbt(tag):
                    if isinstance(tag,dict):
                        if 'attributes'in tag:
                            attrs=tag.pop('attributes')
                            for entry in attrs[1][1]:
                                if 'id'in entry:entry['Name']=entry.pop('id')
                                if 'base'in entry:entry['Base']=entry.pop('base')
                            tag['Attributes']=attrs
                        for k,(kind,v) in list(tag.items()):
                            if kind==8 and isinstance(v,str):tag[k]=(kind,target_json(v))
                            elif kind==10:convert_nbt(v)
                            elif kind==9 and v[0]==10:
                                for child in v[1]:convert_nbt(child)
                    return tag
                convert_nbt(value);value['DataVersion']=(3,3465);data=nbt.write(root_name,value);report['templates']+=1
            path.write_bytes(data);report['files']+=1
            if '/worldgen/biome/'in name:report['biomes'].append(name)
    (ROOT/'forge/build/content-report.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({k:v if k!='biomes' else len(v) for k,v in report.items()}))
if __name__=='__main__':main()
