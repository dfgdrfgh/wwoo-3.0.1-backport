"""Integration cases run through the real dedicated server's feature codecs."""
import json

MARKERS=['RULE_SKIP','SEQUENCE_SKIP','OVERLAY','NESTED_RULE','CROPS','COCOA','CACTUS','SUGAR_CANE','MOSS','TEMPLATE','LEAF_SUPPORT','WOLF_VARIANT']

def install(pack):
    def write(path,value):
        p=pack/'data/wwoo_test'/path;p.parent.mkdir(parents=True,exist_ok=True)
        p.write_text(json.dumps(value) if not isinstance(value,str) else value)
    def simple(block):
        return {'type':'wwoocontent:simple_block','config':{'to_place':{'type':'minecraft:simple_state_provider','state':{'Name':block}}}}
    skip={'type':'wwoocontent:simple_block','config':{'to_place':{'type':'wwoocontent:rule_based','rules':[]}}}
    features={'skip':skip}
    for name,kind in [('sequence','sequence'),('overlay','overlay')]:
        features[name]={'type':'wwoocontent:'+kind,'config':{'features':[{'feature':f,'placement':[]} for f in [skip,simple('minecraft:gold_block')]]}}
    nested={'type':'wwoocontent:rule_based','rules':[{'if_true':{'type':'minecraft:true'},'then':{'type':'wwoocontent:rule_based','rules':[]}},{'if_true':{'type':'minecraft:true'},'then':{'type':'minecraft:simple_state_provider','state':{'Name':'minecraft:gold_block'}}}]}
    features['nested']={'type':'wwoocontent:simple_block','config':{'to_place':nested}}
    for name,block in [('crops','wheat'),('cocoa','cocoa'),('cactus','cactus'),('sugar_cane','sugar_cane'),('moss','pale_moss_carpet')]:features[name]=simple('minecraft:'+block)
    features['template']={'type':'wwoocontent:template','config':{'templates':[{'weight':1,'data':{'id':'wwoocontent:fauna/fish/koi/red_white_dasher','rotations':['none']}}]}}
    for name,feature in features.items():write('worldgen/configured_feature/'+name+'.json',{'type':'wwoocontent:context','config':{'feature':feature}})
    commands=['scoreboard objectives add wwoo_check dummy','forceload add 0 0 63 15','fill 0 199 0 63 203 4 minecraft:air']
    def run(name,x,marker,block,ground='minecraft:stone',extras=(),score=1):
        commands.append(f'setblock {x} 199 0 {ground}')
        commands.extend(extras)
        commands.append(f'execute store result score {name} wwoo_check run place feature wwoo_test:{name} {x} 200 0')
        commands.append(f'execute if score {name} wwoo_check matches {score} if block {x} 200 0 {block} run say WWOO_CHECK_{marker}_PASS')
    run('skip',0,'RULE_SKIP','minecraft:air',score=0)
    run('sequence',2,'SEQUENCE_SKIP','minecraft:air',score=0)
    run('overlay',4,'OVERLAY','minecraft:gold_block')
    run('nested',6,'NESTED_RULE','minecraft:gold_block')
    run('crops',8,'CROPS','minecraft:wheat','minecraft:suspicious_sand')
    run('cocoa',10,'COCOA','minecraft:cocoa',extras=['setblock 10 200 -1 minecraft:acacia_wood'])
    run('cactus',12,'CACTUS','minecraft:cactus','minecraft:rooted_dirt')
    run('sugar_cane',14,'SUGAR_CANE','minecraft:sugar_cane','minecraft:soul_soil',extras=['setblock 14 199 1 minecraft:water'])
    run('moss',16,'MOSS','minecraft:pale_moss_carpet')
    commands.extend(['kill @e[type=minecraft:wolf,x=28,y=200,z=0,distance=..8]','fillbiome 24 196 -4 31 207 3 minecraft:desert','fill 24 199 -1 31 199 1 minecraft:stone','setblock 24 200 0 minecraft:dispenser[facing=east]{Items:[{Slot:0b,id:"minecraft:wolf_spawn_egg",count:1}]}','setblock 24 201 0 minecraft:redstone_block'])
    commands.extend(['fill 38 199 -2 42 202 2 minecraft:water','execute store result score template wwoo_check run place feature wwoo_test:template 40 200 0','execute if score template wwoo_check matches 1 if entity @e[type=minecraft:tropical_fish,x=40,y=200,z=0,distance=..6] run say WWOO_CHECK_TEMPLATE_PASS','setblock 21 200 0 minecraft:oak_leaves[distance=7,persistent=false]','setblock 20 200 0 minecraft:bamboo_block','schedule function wwoo_test:delayed_checks 5t'])
    write('function/checks.mcfunction','\n'.join(commands)+'\n')
    write('function/delayed_checks.mcfunction','execute if block 21 200 0 minecraft:oak_leaves[distance=1] run say WWOO_CHECK_LEAF_SUPPORT_PASS\nexecute if score template wwoo_check matches 1 if entity @e[type=minecraft:tropical_fish,x=40,y=200,z=0,distance=..6] run say WWOO_CHECK_TEMPLATE_PASS\nexecute if entity @e[type=minecraft:wolf,x=28,y=200,z=0,distance=..8,nbt={variant:"minecraft:striped"}] run say WWOO_CHECK_WOLF_VARIANT_PASS\n')
