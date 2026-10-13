"""WWOO 3.0.1 backport with isolated WWOO 2.6.7 biome completions."""
from pathlib import Path
import zipfile,json,subprocess,sys,collections,copy,hashlib,os,shutil
sys.path.insert(0,str(Path(__file__).parent));import nbt
ROOT=Path(__file__).resolve().parent.parent
BASE=ROOT/'inputs/wwoo-fabric-2.6.7.jar';NEW=ROOT/'inputs/wwoo-fabric-26.3-3.0.1.jar'
old=zipfile.ZipFile(BASE);new=zipfile.ZipFile(NEW);PREFIX='resources/wwoo_main/data/'
NS='wwoocontent';entries={n:old.read(n) for n in old.namelist() if not n.endswith('/')}
entries={n:v for n,v in entries.items() if not any(n.startswith('resources/'+pack+'/') for pack in ['navigable_rivers_add_on','cliffs_and_coves_add_on','towering_tepuis_add_on'])}
selected={};templates=set();generated_tags={};tag_queue=set()
def strings(v):
    if isinstance(v,str):yield v
    elif isinstance(v,dict):
        for x in v.values():yield from strings(x)
    elif isinstance(v,list):
        for x in v:yield from strings(x)
REMAP={
 **{'wythers:fauna/fish/discus/'+color:'wwoocontent:fauna/fish/discus/'+color+'_discus' for color in ['teal','silver_teal','yellow','red_white','golden','orange','red_sky','sky_red','sky']},
 **{'wythers:feature/fauna/parrots/'+color+'_parakeets':'wwoocontent:fauna/parrots/'+color+'_parakeets' for color in ['green','scarlet','hyacinth','blue_and_yellow']}
}
vanilla_new=zipfile.ZipFile(ROOT/'toolchain/minecraft-26.3.jar')
vanilla_old=zipfile.ZipFile(ROOT/'minecraft-named.jar')
vanilla_legacy=set()
FEATURE_KEYS={'feature','features','default','vegetation_feature','data'}
vanilla_imports={}
vanilla_templates=set()
def keyed_strings(v,key=''):
    if isinstance(v,str):yield v,key
    elif isinstance(v,dict):
        for k,x in v.items():yield from keyed_strings(x,k)
    elif isinstance(v,list):
        for x in v:yield from keyed_strings(x,key)
def collect(n):
    if n in selected:return
    source=new if PREFIX+n in new.namelist() else vanilla_new
    src=PREFIX+n if source is new else 'data/'+n
    v=json.loads(source.read(src))
    # Vanilla's unchanged support features use their target-version codecs. WWOO overrides always use 3.0.1.
    legacy='data/'+n.replace('/worldgen/feature/','/worldgen/configured_feature/')
    if source is vanilla_new and '/worldgen/feature/' in n and legacy in vanilla_old.namelist() and v.get('type') in ['minecraft:ore','minecraft:geode','minecraft:dripstone_cluster','minecraft:sculk_patch']:
        v=json.loads(vanilla_old.read(legacy));vanilla_legacy.add(n)
    selected[n]=v
    for resource,key in keyed_strings(v):
        namespace,path=resource.lstrip('#').split(':',1) if ':' in resource else ('minecraft',resource)
        if namespace=='minecraft' and 'data/minecraft/structure/'+path+'.nbt' in vanilla_new.namelist():vanilla_templates.add(path)
        if namespace=='wythers':
            for category in ['worldgen/placed_feature','worldgen/feature','worldgen/block_state_provider']:
                dep='wythers/'+category+'/'+path+'.json'
                if PREFIX+dep in new.namelist():collect(dep)
            if PREFIX+'wythers/structure/'+path+'.nbt' in new.namelist():templates.add(path)
        elif namespace=='minecraft' and key in FEATURE_KEYS:
            for category in ['worldgen/placed_feature','worldgen/feature']:
                dep='minecraft/'+category+'/'+path+'.json'
                if PREFIX+dep in new.namelist() or 'data/'+dep in vanilla_new.namelist():
                    vanilla_imports[resource]=NS+':vanilla/'+path
                    collect(dep)
for n in new.namelist():
    if n.startswith(PREFIX) and n.endswith('.json') and n.removeprefix(PREFIX).split('/')[1]=='worldgen':collect(n.removeprefix(PREFIX))
for resource in json.loads(new.read(PREFIX+'minecraft/tags/worldgen/feature/can_spawn_from_bone_meal.json'))['values']:
    vanilla_imports[resource]=NS+':vanilla/'+resource.split(':',1)[1]
    collect(resource.replace(':','/worldgen/feature/')+'.json')
FEATURE_TYPES={'tree','disk','overlay','sequence','template','simple_block','simple_random_selector','random_selector','vegetation_patch','block_column','no_op','ore','lake','spike'}
FEATURE_TYPES.update(v['type'].removeprefix('minecraft:') for n,v in selected.items() if '/worldgen/feature/' in n and 'type' in v)
def nested_feature_types(v,key=''):
    if isinstance(v,dict):
        if key=='feature' and 'type' in v:FEATURE_TYPES.add(v['type'].removeprefix('minecraft:'))
        for k,x in v.items():nested_feature_types(x,k)
    elif isinstance(v,list):
        for x in v:nested_feature_types(x,key)
for v in selected.values():nested_feature_types(v)
PROVIDER_KEYS={'trunk_provider','foliage_provider','dirt_provider','below_trunk_provider','provider','block_provider','block_state_provider','ground_state','state_provider','then','fallback','to_place','source','barrier','fluid','trunk','leaves','body_provider','cap_provider','stem_provider','outer_layer_provider','middle_layer_provider','inner_layer_provider','alternate_inner_layer_provider','filling_provider','root_state_provider','hanging_root_state_provider','root_provider','muddy_roots_provider','above_root_provider'}
def put(n,v):entries[n]=json.dumps(v,indent=2).encode()+b'\n'
def state(v):
    if isinstance(v,str):return {'Name':v if ':' in v else 'minecraft:'+v}
    return {'Name':v.get('id',v.get('Name')),**({'Properties':v.get('properties',v.get('Properties'))} if 'properties' in v or 'Properties' in v else {})}
def tag_name(values):
    key=hashlib.sha256(json.dumps(values,sort_keys=True).encode()).hexdigest()[:12]
    generated_tags[key]={'values':values};return NS+':replaceable_'+key
def convert(v,key=''):
    if isinstance(v,str):
        if key in FEATURE_KEYS and v in vanilla_imports:return vanilla_imports[v]
        if key in PROVIDER_KEYS:
            namespace,path=v.split(':',1) if ':' in v else ('minecraft',v)
            resource=PREFIX+namespace+'/worldgen/block_state_provider/'+path+'.json'
            if resource in new.namelist():return convert(json.loads(new.read(resource)))
            vanilla_resource='data/'+namespace+'/worldgen/block_state_provider/'+path+'.json'
            if vanilla_resource in vanilla_new.namelist():return convert(json.loads(vanilla_new.read(vanilla_resource)))
        if key in PROVIDER_KEYS:return {'type':'minecraft:simple_state_provider','state':state(v)}
        if key in ('state','block_state','inner_placements','output_state','base_block','pointed_block'):return state(v)
        if v in REMAP:return REMAP[v]
        if v.startswith('#wythers:'):
            return '#'+NS+':'+v.split(':',1)[1]
        if v.startswith('wythers:'):
            path=v.split(':',1)[1]
            # All imported resource ids and tags are isolated from the 2.6.7 pack.
            return NS+':'+path
        if v in ['minecraft:grass_blocks','minecraft:supports_vegetation']:return NS+':'+v.split(':')[1]
        return v
    if isinstance(v,list):return [convert(x,key) for x in v]
    if not isinstance(v,dict):return v
    if key in ('state','block_state','inner_placements','output_state','base_block','pointed_block') or ('id' in v and len(v.keys()-{'id','properties'})==0 and key in PROVIDER_KEYS):
        s=state(v)
        return {'type':'minecraft:simple_state_provider','state':s} if key in PROVIDER_KEYS else s
    t=v.get('type','').removeprefix('minecraft:')
    if not t and key in PROVIDER_KEYS and 'id' in v:return {'type':'minecraft:simple_state_provider','state':state(v)}
    if 'relative_to_sea_level' in v:return {'absolute':63+v['relative_to_sea_level']}
    out={k:convert(x,k) for k,x in v.items()}
    if t=='simple':out['type']='minecraft:simple_state_provider'
    elif t=='randomized_int':out['type']='minecraft:randomized_int_state_provider'
    elif t=='noise_threshold':
        out['type']='minecraft:noise_threshold_provider';out['noise']={'firstOctave':v['noise'].get('base_octave',0),'amplitudes':v['noise'].get('amplitude_modifiers',[1.0])}
        for field in ['default_state','low_states','high_states']:out[field]=[state(x) for x in v[field]] if isinstance(v[field],list) else state(v[field])
    elif t=='weighted':
        out['type']='minecraft:weighted_state_provider'
        out['entries']=[{'weight':e['weight'],'data':state(e['data'])} for e in v['entries']]
    elif t in ['noise','dual_noise']:
        out['type']='minecraft:dual_noise_provider' if t=='dual_noise' else 'minecraft:noise_provider';noise=v['noise']
        out['noise']={'firstOctave':noise.get('base_octave',noise.get('firstOctave')),'amplitudes':noise.get('amplitude_modifiers',noise.get('amplitudes',[1.0]))}
        out['states']=[state(x) for x in v['states']]
        if t=='dual_noise':
            out['slow_noise']={'firstOctave':v['slow_noise'].get('base_octave',v['slow_noise'].get('firstOctave')),'amplitudes':v['slow_noise'].get('amplitude_modifiers',v['slow_noise'].get('amplitudes',[1.0]))}
            if isinstance(v['variety'],int):out['variety']={'min_inclusive':v['variety'],'max_inclusive':v['variety']}
            elif isinstance(v['variety'],list):out['variety']={'min_inclusive':v['variety'][0],'max_inclusive':v['variety'][1]}
    elif t=='rule_based':out['type']=NS+':rule_based'
    elif t=='count' and (isinstance(v.get('count'),int) and v['count']>256):out['type']=NS+':count'
    elif t in ['offset','cuboid','unobstructed','randomly_selected']:out['type']=NS+':'+t
    elif t in ['matching_biomes','volume_match','random_chance'] or (t=='height_range' and 'height' not in v):
        out['type']=NS+':'+t
        if t=='matching_biomes':
            out['biomes']=[x if ':' in x or x.startswith('#') else 'minecraft:'+x for x in (v['biomes'] if isinstance(v['biomes'],list) else [v['biomes']])]
    elif t=='height_range' and isinstance(v.get('height'),dict) and 'type' not in v['height']:
        out['height']={'type':'minecraft:constant','value':convert(v['height'])}
    elif t=='place_on_ground':out['type']=NS+':place_on_ground'
    if t in ['noise','dual_noise','noise_threshold']:
        out['kind']=t;out.pop('type',None);out['noise']=v['noise']
        if 'slow_noise' in v:out['slow_noise']=v['slow_noise']
        return {'type':NS+':modern_noise','settings':out}
    if t=='biome' and 'predicate' in v:
        # New biome placement accepts an additional predicate; preserve both checks.
        out.pop('predicate')
    if t in FEATURE_TYPES:
        out.pop('type',None)
        if t in ['overlay','sequence','template','spike','simple_block']:t=NS+':'+t
        else:t='minecraft:'+t
        t={'minecraft:block_blob':'minecraft:forest_rock','minecraft:speleothem':'minecraft:speleothem'}.get(t,t)
        if t=='minecraft:forest_rock':out={'state':out.get('state',out.get('block_state'))}
        if t=='minecraft:tree':
            if 'below_trunk_provider' in out:out['dirt_provider']=out.pop('below_trunk_provider')
            out.setdefault('dirt_provider',{'type':'minecraft:simple_state_provider','state':{'Name':'minecraft:dirt'}})
        if t in ['minecraft:dripstone_cluster','minecraft:speleothem_cluster']:
            for src,dst in [('max_distance_from_edge_affecting_chance_of_speleothem','max_distance_from_edge_affecting_chance_of_speleothem_column'),('chance_of_speleothem_at_max_distance_from_center','chance_of_speleothem_column_at_max_distance_from_center')]:
                if src in out:out[dst]=out.pop(src)
        if t=='minecraft:sculk_patch':out.setdefault('catalyst_chance',0.0);out.setdefault('extra_rare_growths',0)
        if t=='minecraft:disk':
            out['state_provider']={'fallback':out['state_provider'],'rules':[]}
        if t=='minecraft:vegetation_patch' and isinstance(out.get('replaceable'),list):out['replaceable']=tag_name(out['replaceable'])
        if t=='minecraft:vegetation_patch' and isinstance(out.get('replaceable'),str) and not out['replaceable'].startswith('#'):out['replaceable']='#'+out['replaceable']
        if t in [NS+':overlay',NS+':sequence','minecraft:simple_random_selector'] and not isinstance(out.get('features'),list):out['features']=[out['features']]
        out={'type':t,'config':out}
    if key=='placement' and isinstance(v,list):raise AssertionError()
    return out
def fix_placements(v):
    if isinstance(v,dict):
        for k,x in list(v.items()):
            if k=='placement':
                extra=[]
                for p in x:
                    extra.append(p)
                    if p.get('type')=='minecraft:biome' and 'predicate' in p:
                        pred=p.pop('predicate');extra.append({'type':'minecraft:block_predicate_filter','predicate':pred})
                    if p.get('type')=='minecraft:random_chance':
                        extra[-1]={'type':'minecraft:block_predicate_filter','predicate':p}
                v[k]=extra
            fix_placements(v[k])
    elif isinstance(v,list):
        for x in v:fix_placements(x)
def context(f):return {'type':NS+':context','config':{'feature':f}}
for n,v in selected.items():
    if '/biome/' in n or '/block_state_provider/' in n:continue
    vv=copy.deepcopy(v);fix_placements(vv);result=vv if n in vanilla_legacy else convert(vv)
    if '/worldgen/feature/' in n:
        dest=(n.replace('wythers/','wwoocontent/',1) if n.startswith('wythers/') else n.replace('minecraft/worldgen/feature/','wwoocontent/worldgen/feature/vanilla/',1)).replace('/worldgen/feature/','/worldgen/configured_feature/')
        result=context(result)
    else:
        dest=n.replace('wythers/','wwoocontent/',1) if n.startswith('wythers/') else n.replace('minecraft/worldgen/placed_feature/','wwoocontent/worldgen/placed_feature/vanilla/',1)
        if isinstance(result.get('feature'),dict):result['feature']=context(result['feature'])
        # Named feature entries are already wrapped when imported.
    put(PREFIX+dest,result)
# Convert every biome; incomplete upstream biomes use their full 2.6.7 definition.
EXCEPTIONS={'ice_spikes','mangrove_swamp','mushroom_fields','swamp','windswept_hills','windswept_gravelly_hills','windswept_savanna'}
visual_attributes={}
for n,v in selected.items():
    if '/worldgen/biome/' not in n:continue
    biome=Path(n).stem
    if biome in EXCEPTIONS:
        put(PREFIX+n,json.loads(old.read(PREFIX+n)))
        continue
    attr=v.get('attributes',{});effects=copy.deepcopy(v.get('effects',{}))
    # Keep visual-only fields before the target biome codec removes them.
    # Sharing effects here lost dry-foliage overrides through effects.pop().
    visual_attributes['minecraft:'+biome]={'attributes':copy.deepcopy(attr),'effects':copy.deepcopy(effects)}
    effects.pop('dry_foliage_color',None)
    for field in ['sky_color','fog_color','water_fog_color']:
        if 'minecraft:visual/'+field in attr:effects[field]=attr['minecraft:visual/'+field]
    effects.setdefault('sky_color',7907327);effects.setdefault('fog_color',12638463);effects.setdefault('water_fog_color',329011)
    for k,x in list(effects.items()):
        if isinstance(x,str) and x.startswith('#'):effects[k]=int(x[1:],16)
    music=attr.get('minecraft:audio/background_music',{}).get('default')
    if music:effects['music']={**music,'replace_current_music':False}
    effects.setdefault('mood_sound',{'sound':'minecraft:ambient.cave','tick_delay':6000,'block_search_extent':8,'offset':2.0})
    spawns=attr.get('minecraft:gameplay/natural_mob_spawns',{});spawns=spawns.get('argument',spawns)
    spawners={}
    for category,sp in spawns.get('spawns_by_category',{}).items():
        rows=[]
        for spawn in sp:
            count=spawn['count'];lo=count['min_inclusive'] if isinstance(count,dict) else count;hi=count['max_inclusive'] if isinstance(count,dict) else count
            rows.append({'type':spawn['type'],'weight':spawn['weight'],'minCount':lo,'maxCount':hi})
        spawners[category]=rows
    features=[[convert(f,'features') for f in step] for step in v['features']]
    if biome=='windswept_forest':
        completed=json.loads(old.read(PREFIX+n))
        # Keep 3.0.1 autumnal identity and vegetation; fill older missing geological details.
        for step in [0,1,2,3,4,5,6,7,8,10]:
            for feature in completed['features'][step]:
                if feature not in features[step]:features[step].append(feature)
        for feature in completed['features'][9]:
            if feature.startswith('wythers:terrain/') and feature not in features[9]:features[9].append(feature)
    result={'has_precipitation':v['has_precipitation'],'temperature':v['temperature'],'downfall':v['downfall'],'effects':effects,'carvers':{'air':v['carvers']},'features':features,'spawners':spawners,'spawn_costs':spawns.get('spawn_costs',{})}
    for field in ['temperature_modifier','creature_spawn_probability']:
        if field in v:result[field]=v[field]
    if 'minecraft:gameplay/creature_world_gen_spawn_probability' in attr:result['creature_spawn_probability']=attr['minecraft:gameplay/creature_world_gen_spawn_probability']
    put(PREFIX+n,result)
put('data/wwoocontent/biome_attributes.json',visual_attributes)
# Move the autumn campsite style with the forest and use cherry camps in the Asian landscape.
for tag in ['has_structure/abandoned_camp_dappled_forest','has_structure/abandoned_camp_cherry_grove']:
    source=PREFIX+'minecraft/tags/worldgen/biome/'+tag+'.json';v=json.loads(new.read(source));v['replace']=True;put(source,v)
# Import just the referenced tag definitions; newer vanilla tags receive private aliases.
for v in selected.values():
    def tags(x):
        if isinstance(x,dict):
            if 'tag' in x:tag_queue.add(x['tag'])
            for y in x.values():tags(y)
        elif isinstance(x,list):
            for y in x:tags(y)
    tags(v)
for t in sorted(tag_queue):
    namespace,path=t.lstrip('#').split(':',1) if ':' in t else ('minecraft',t)
    if namespace=='wythers' or t in ['minecraft:supports_vegetation','minecraft:grass_blocks']:
        location=PREFIX+namespace+'/tags/block/'+path+'.json'
        vanilla_location='data/'+namespace+'/tags/block/'+path+'.json'
        v=json.loads(new.read(location)) if location in new.namelist() else json.loads(vanilla_new.read(vanilla_location))
        if namespace=='minecraft' and not v.get('replace',False) and vanilla_location in vanilla_new.namelist():
            v['values']=json.loads(vanilla_new.read(vanilla_location))['values']+v['values'] if location in new.namelist() else v['values']
        # New blocks in this list are optional when a fork toggle disables registration.
        values=[]
        for s in v['values']:
            if isinstance(s,str) and not s.startswith('#'):s={'id':s,'required':False}
            values.append(s)
        v['values']=values;put(PREFIX+NS+'/tags/block/'+path+'.json',v)
for k,v in generated_tags.items():put(PREFIX+NS+'/tags/block/replaceable_'+k+'.json',v)
# Convert selected templates' palettes and entity attributes; never retain saved UUIDs.
templates.update(n.removeprefix(PREFIX+'wythers/structure/').removesuffix('.nbt') for n in new.namelist() if n.startswith(PREFIX+'wythers/structure/') and n.endswith('.nbt'))
for namespace,path in sorted([('wythers',p) for p in templates]+[('minecraft',p) for p in vanilla_templates]):
    source=new if namespace=='wythers' else vanilla_new
    location=PREFIX+'wythers/structure/'+path+'.nbt' if namespace=='wythers' else 'data/minecraft/structure/'+path+'.nbt'
    name,root=nbt.read(source.read(location))
    def palette(p):
        if 'id' in p:p['Name']=p.pop('id')
        if 'properties' in p:p['Properties']=p.pop('properties')
    if 'palette' in root:
        for p in root['palette'][1][1]:palette(p)
    if 'palettes' in root:
        for _,pal in root['palettes'][1][1]:
            for p in pal:palette(p)
    if 'entities' in root:
        for entity in root['entities'][1][1]:
            e=entity['nbt'][1];e.pop('UUID',None)
            if 'attributes' in e:
                attrs=[]
                for a in e.pop('attributes')[1][1]:
                    attr=a['id'][1].removeprefix('minecraft:');a=copy.deepcopy(a);a['id']=(8,'minecraft:generic.'+attr);attrs.append(a)
                e['attributes']=(9,(10,attrs))
            if 'fall_distance' in e:e['FallDistance']=e.pop('fall_distance')
            if 'has_egg' in e:e['HasEgg']=e.pop('has_egg')
            for src,dst in [('home_pos','HomePos'),('travel_pos','TravelPos')]:
                if src in e:
                    position=e.pop(src)[1]
                    for axis,coordinate in zip('XYZ',position):e[dst+axis]=(3,coordinate)
    root['DataVersion']=(3,3955)
    entries[PREFIX+(NS if namespace=='wythers' else 'minecraft')+'/structure/'+path+'.nbt']=nbt.write(name,root)
import resources
resources.apply_resources(entries,old,new,vanilla_new,put,visual_attributes,convert)
meta=json.loads(old.read('fabric.mod.json'));meta['version']='3.0.1+backport.1.21.1.10';meta['entrypoints']['main'].append('com.zachminecraft.wwoocontent.ContentPort');meta['depends']['minecraft']='~1.21.1';meta['depends']['java']='>=21';meta['depends']['vanillabackport']='>=1.2.0.2638.9'
meta['mixins']=meta.get('mixins',[])+['wwoo-content.common.mixins.json',{'config':'wwoo-content.client.mixins.json','environment':'client'}]
put('fabric.mod.json',meta)
put('wwoo-content.common.mixins.json',{'required':True,'package':'com.zachminecraft.wwoocontent.mixin','compatibilityLevel':'JAVA_21','mixins':['PaleGardenSpawnMixins$Placement','PaleGardenSpawnMixins$SpawnReason','PaleGardenSpawnMixins$ServerInsertion','PaleGardenSpawnMixins$GenerationInsertion','BonemealMixin','PlantSupportMixins$Vegetation','PlantSupportMixins$Crops','PlantSupportMixins$Cocoa','PlantSupportMixins$Cactus','PlantSupportMixins$SugarCane','PlantSupportMixins$LeafSupport'],'injectors':{'defaultRequire':1}})
put('wwoo-content.client.mixins.json',{'required':True,'package':'com.zachminecraft.wwoocontent.mixin','compatibilityLevel':'JAVA_21','client':['CloudRendererMixin','WWOOConfigScreenMixin'],'injectors':{'defaultRequire':1}})
# Compile and remap only the added helpers; original mod classes remain byte-for-byte intact.
jdk=Path(os.environ['WWOO_JAVA_HOME'])/'bin' if 'WWOO_JAVA_HOME' in os.environ else (ROOT/'toolchain/jdk/bin' if (ROOT/'toolchain/jdk/bin').exists() else Path(shutil.which('javac')).parent)
cp=(ROOT/'compile-classpath.txt').read_text().replace(str(ROOT/'minecraft-named.jar'),str(ROOT/'minecraft-client-named.jar'))+os.pathsep+str(next((ROOT/'test-dependencies').glob('cristellib-*.jar')))+os.pathsep+str(next((ROOT/'test-runtime/libraries/net/fabricmc/sponge-mixin').rglob('*.jar')));classes=ROOT/'port/classes'
if classes.exists():shutil.rmtree(classes)
subprocess.run([str(jdk/'javac'),'-proc:none','--release','21','-cp',cp,'-d',str(classes),*[str(p) for p in (ROOT/'port/src').rglob('*.java')]],check=True,cwd=ROOT)
raw=ROOT/'port/helpers-named.jar'
with zipfile.ZipFile(raw,'w',zipfile.ZIP_DEFLATED) as z:
    for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
mcp=(ROOT/'mapping-classpath.txt').read_text()+os.pathsep+str(ROOT)
for source,target,mapping,srcns,dstns in [('helpers-named.jar','helpers-official.jar','client-mojmap.tiny','named','official'),('helpers-official.jar','helpers-intermediary.jar','intermediary.tiny','official','intermediary')]:
    mc_classpath=ROOT/'minecraft-client-named.jar' if srcns=='named' else ROOT/'toolchain/client-1.21.1.jar'
    subprocess.run([str(jdk/'java'),'-cp',mcp,'MappingTool','remap',str(ROOT/'port'/source),str(ROOT/'port'/target),str(ROOT/mapping),srcns,dstns,str(mc_classpath)],check=True,cwd=ROOT)
with zipfile.ZipFile(ROOT/'port/helpers-intermediary.jar') as z:
    for n in z.namelist():
        if n.endswith('.class'):entries[n]=z.read(n)

# Preserve existing packed/blue ice when native disk features carve air, water,
# or invisible light markers. Keep the original providers, placements and biomes.
from preserve_ice import patch_entries
patch_entries(entries)

out=ROOT/'wwoo-fabric-1.21.1-3.0.1-backport.jar'
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
    for n,data in entries.items():z.writestr(n,data)
unchanged=[n for n in old.namelist() if not n.endswith('/') and old.read(n)==entries.get(n)]
print(json.dumps({'output':str(out),'imported_json':len(selected),'templates':len(templates),'unchanged_base_files':len(unchanged),'changed_base_files':[n for n in old.namelist() if not n.endswith('/') and old.read(n)!=entries.get(n)]},indent=2))

