"""Non-worldgen resources from 3.0.1, converted without importing old add-ons."""
import copy,json

def apply_resources(entries,old,new,vanilla,put,visual_attributes,convert):
    prefix='resources/wwoo_main/data/'
    put(prefix+'wwoocontent/tags/block/plant_support_active.json',{'values':['minecraft:stone']})
    # New version's actual block/biome/structure tags, not just the forest subset.
    for n in new.namelist():
        if not n.endswith('.json') or not n.startswith(prefix):continue
        relative=n.removeprefix(prefix)
        if '/tags/' in relative:
            value=json.loads(new.read(n))
            if relative.startswith('minecraft/tags/block/') and not value.get('replace',False):
                base='data/'+relative
                if base in vanilla.namelist():
                    value['values']=json.loads(vanilla.read(base)).get('values',[])+value['values']
            if relative.startswith('minecraft/tags/block/'):
                # Native tree placement must use the imported modern plant set,
                # rather than the older fill-in tag with the same wythers name.
                value['values']=[v.replace('#wythers:','#wwoocontent:',1) if isinstance(v,str) else {**v,'id':v['id'].replace('#wythers:','#wwoocontent:',1)} for v in value['values']]
            if relative.startswith('wythers/tags/block/'):
                alias=relative.replace('wythers/','wwoocontent/',1)
                value['values']=[v.replace('#wythers:','#wwoocontent:',1) if isinstance(v,str) else v for v in value['values']]
                put(prefix+alias,value)
            else:
                if '/tags/worldgen/feature/' in relative:
                    put(n.replace('/worldgen/feature/','/worldgen/configured_feature/'),{'replace':value.get('replace',False),'values':[convert(x,'features') for x in value['values']]})
                    continue
                if relative.startswith('minecraft/tags/worldgen/biome/spawns_'):
                    base='data/'+relative
                    inherited=json.loads(vanilla.read(base)).get('values',[]) if base in vanilla.namelist() else []
                    if not value.get('replace',False):value['values']=list(dict.fromkeys(inherited+value['values']))
                    value['replace']=True
                if '/has_structure/abandoned_camp_' in relative:value['replace']=True
                put(n,value)
        elif '/wolf_variant/' in relative:
            value=json.loads(new.read(n));assets=value['assets']
            conditions=value['spawn_conditions'];biomes=next((c['condition']['biomes'] for c in conditions if 'condition' in c),None)
            if biomes is None:biomes='#wwoocontent:pale_wolf_spawn'
            put(n,{'wild_texture':assets['wild'],'tame_texture':assets['tame'],'angry_texture':assets['angry'],'biomes':biomes})
            # Preserve new baby assets for VanillaBackport's variant rendering.
            put('data/wwoocontent/wolf_baby_assets/'+relative.split('/')[-1],value['baby_assets'])
        elif '/loot_table/' in relative:
            put(n,loot(json.loads(new.read(n))))
    # Every supported dry-foliage override is fed into the existing compatibility API.
    for biome,value in visual_attributes.items():
        color=value['effects'].get('dry_foliage_color')
        if color is not None:
            if isinstance(color,str):color=int(color.lstrip('#'),16)
            put('assets/wwoocontent/dry_foliage_colors/'+biome.split(':')[1]+'.json',{'biome':[biome],'color':color,'priority':100})
    # The target version already has equivalent gameplay and water-fog biome tags.
    for attribute,tag in [('minecraft:gameplay/snow_golem_melts','snow_golem_melts'),('minecraft:gameplay/increased_fire_burnout','increased_fire_burnout'),('minecraft:visual/water_fog_end_distance','has_closer_water_fog')]:
        biomes=[b for b,v in visual_attributes.items() if v['attributes'].get(attribute)]
        put(prefix+'minecraft/tags/worldgen/biome/'+tag+'.json',{'replace':False,'values':biomes})
    # Include recursively referenced newer vanilla block tags used by the rewrite.
    def include_block_tag(identifier,seen):
        identifier=identifier.lstrip('#')
        if ':' not in identifier:identifier='minecraft:'+identifier
        if identifier in seen:return
        seen.add(identifier);namespace,path=identifier.split(':',1)
        source='data/'+namespace+'/tags/block/'+path+'.json';dest=prefix+namespace+'/tags/block/'+path+'.json'
        if namespace=='minecraft' and source in vanilla.namelist() and dest not in entries:
            value=json.loads(vanilla.read(source))
            value['values']=[('minecraft:chain' if v=='minecraft:iron_chain' else {'id':v,'required':False} if v in ['minecraft:test_block','minecraft:test_instance_block'] else v) for v in value['values']]
            put(dest,value)
        if dest in entries:
            for v in json.loads(entries[dest]).get('values',[]):
                v=v['id'] if isinstance(v,dict) else v
                if v.startswith('#'):include_block_tag(v,seen)
    seen=set()
    for n,data in list(entries.items()):
        if '/tags/block/' in n and n.startswith(prefix):
            include_block_tag(n.removeprefix(prefix).replace('/tags/block/',':').removesuffix('.json'),seen)
    include_block_tag('minecraft:blocks_motion_in_heightmap_no_leaves',seen)
    # Names used by the 1.21.1 native checks; point them at the complete modern tags.
    for modern,legacy in [('supports_bamboo','bamboo_plantable_on'),('supports_big_dripleaf','big_dripleaf_placeable'),('supports_small_dripleaf','small_dripleaf_placeable'),('supports_dry_vegetation','dead_bush_may_place_on'),('cannot_support_snow_layer','snow_layer_cannot_survive_on')]:
        include_block_tag('minecraft:'+modern,seen)
        put(prefix+'minecraft/tags/block/'+legacy+'.json',{'replace':True,'values':['#minecraft:'+modern]})
    for tag in ['supports_vegetation','supports_crops','supports_stem_crops','supports_cocoa','supports_cactus','supports_sugar_cane','supports_sugar_cane_adjacently','prevents_nearby_leaf_decay']:
        include_block_tag('minecraft:'+tag,seen)
    # Replace the retired ore-removal definitions, including private imported placements.
    ore_prefix='resources/wwoo_remove_ores/data/'
    for n in list(entries):
        if n.startswith(ore_prefix):del entries[n]
    for n in new.namelist():
        if n.startswith(ore_prefix) and n.endswith('.json'):
            value=json.loads(new.read(n));put(n,value)
            alias=n.replace('/minecraft/worldgen/placed_feature/','/wwoocontent/worldgen/placed_feature/vanilla/')
            put(alias,convert(value))
    # Select the 3.0.1 pack set, plus the requested existing Pale Garden compatibility.
    packs=json.loads(new.read('data/cristellib/data_pack/load_packs.json'))
    compat=next(p for p in json.loads(old.read('data/cristellib/data_pack/load_packs.json'))['packs'] if 'vanilla_backport_compat' in p['location'])
    # Keep 3.0.1 unchanged when VanillaBackport is available. Without it,
    # reuse the original standalone 1.21.1 WWOO terrain pack verbatim.
    present={'type':'mod_loaded','modid':'vanillabackport'}
    absent={'type':'not','condition':present}
    modern=next(p for p in packs['packs'] if p['location']=='wwoo:resources/wwoo_main')
    modern['condition']=present
    packs['packs'].append({'location':'wwoo:resources/wwoo_legacy',
                           'display_name':'WWOO original 1.21.1 standalone worldgen',
                           'condition':absent})
    for name in old.namelist():
        if name.startswith('resources/wwoo_main/') and not name.endswith('/'):
            entries['resources/wwoo_legacy/'+name[len('resources/wwoo_main/'):]]=old.read(name)
    packs['packs'].insert(1,compat);put('data/cristellib/data_pack/load_packs.json',packs)
    # Existing worlds/configs can load compat after main. Its old Pale Garden
    # biome would hide all 3.0.1 landscaping and vegetation. Keep other compat
    # resources, but give the biome one authoritative definition in main.
    entries.pop('resources/vanilla_backport_compat/data/minecraft/worldgen/biome/pale_garden.json',None)
    for pack in ['wwoo_main','wwoo_remove_ores']:
        put('resources/'+pack+'/pack.mcmeta',{'pack':{'pack_format':48,'description':'William Wythers Overhauled Overworld 3.0.1 — 1.21.1 backport'}})
    # Wolf selectors in 1.21.1 have no priorities: make the pale fallback disjoint.
    selectors=[]
    for n in new.namelist():
        if '/wolf_variant/' in n and n.endswith('.json'):
            for c in json.loads(new.read(n))['spawn_conditions']:
                if 'condition' in c:
                    b=c['condition']['biomes'];selectors.extend(b if isinstance(b,list) else [b])
    def tag_values(identifier,seen=None):
        seen=set() if seen is None else seen
        if identifier in seen:return set()
        seen.add(identifier);namespace,path=identifier.split(':',1)
        n=prefix+namespace+'/tags/worldgen/biome/'+path+'.json';base='data/'+namespace+'/tags/worldgen/biome/'+path+'.json'
        values=json.loads(entries[n]).get('values',[]) if n in entries else (json.loads(vanilla.read(base)).get('values',[]) if base in vanilla.namelist() else [])
        result=set()
        for v in values:
            v=v['id'] if isinstance(v,dict) else v
            if v.startswith('#'):result.update(tag_values(v[1:],seen))
            else:result.add(v)
        return result
    all_biomes={n.removeprefix(prefix+'minecraft/worldgen/biome/').removesuffix('.json') for n in entries if n.startswith(prefix+'minecraft/worldgen/biome/')}
    occupied=set()
    for b in selectors:occupied.update(tag_values(b[1:]) if b.startswith('#') else [b])
    put(prefix+'wwoocontent/tags/worldgen/biome/pale_wolf_spawn.json',{'values':sorted('minecraft:'+b for b in all_biomes if 'minecraft:'+b not in occupied)})

def loot(v,key=''):
    if isinstance(v,str):
        if key=='conditions':
            if v=='minecraft:tool/can_silk_touch':return {'condition':'minecraft:match_tool','predicate':{'predicates':{'minecraft:enchantments':[{'enchantments':'minecraft:silk_touch','levels':{'min':1}}]}}}
            if v=='minecraft:tool/is_shears':return {'condition':'minecraft:match_tool','predicate':{'items':'minecraft:shears'}}
            return {'condition':'minecraft:reference','name':v}
        return v
    if isinstance(v,list):return [loot(x,key) for x in v]
    if not isinstance(v,dict):return v
    out={}
    for k,x in v.items():
        if k=='condition':out['conditions']=loot(x if isinstance(x,list) else [x],'conditions')
        elif k=='modifier':out['functions']=loot(x if isinstance(x,list) else [x],'functions')
        elif k in ['terms','term']:out[k]=loot(x,'conditions')
        else:out[k]=loot(x,k)
    if key in ['conditions','predicate'] and 'type' in out:out['condition']=out.pop('type')
    elif key=='functions' and 'type' in out:out['function']=out.pop('type')
    if out.get('type')=='text':out.pop('type')
    if out.get('condition')=='minecraft:match_block':
        blocks=out.pop('blocks');out['condition']='minecraft:block_state_property';out['block']=blocks[0] if isinstance(blocks,list) else blocks
        if 'state' in out:out['properties']=out.pop('state')
    if out.get('type')=='minecraft:weighted_list':out={'type':'wwoocontent:weighted_list','distribution_data':{'distribution':out['distribution']}}
    if key=='components' and 'minecraft:use_remainder' in out:
        # In 1.21.1 the same food remainder lives inside FoodProperties.
        remainder=out.pop('minecraft:use_remainder')
        out['minecraft:food']={'nutrition':6,'saturation':7.2,'can_always_eat':True,'using_converts_to':remainder}
    if key=='components':
        for component in ['minecraft:custom_name','minecraft:item_name']:
            if component in out and not isinstance(out[component],str):out[component]=json.dumps(out[component],separators=(',',':'))
    return out
