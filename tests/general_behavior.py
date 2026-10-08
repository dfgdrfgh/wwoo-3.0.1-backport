"""Assert general behavior in an isolated dedicated-server fixture.

Usage: python3 tests/general_behavior.py FIXTURE LABEL JAR [MODES...]
Fixture inputs: server/server-game.jar, server/classpath.txt, mods/,
controls-server/config/, jdk-21.0.2/, and general-probe.jar (created by
build_general_probe.py). This writes private server configurations and worlds
inside FIXTURE; it does not modify the supplied configurations or mod JAR.
"""
from pathlib import Path
import json,shutil,subprocess,time,sys

root=Path(sys.argv[1]).resolve()
label=sys.argv[2]
jar=Path(sys.argv[3]).resolve()
server=root/('general-server-'+label)
server.mkdir(exist_ok=True)
(server/'mods').mkdir(exist_ok=True)
for p in (root/'mods').glob('*.jar'):shutil.copyfile(p,server/'mods'/p.name)
shutil.copyfile(jar,server/'mods/wwoo-fabric-1.21.1-3.0.1-backport.jar')
shutil.copytree(root/'controls-server/config',server/'config',dirs_exist_ok=True)
(server/'eula.txt').write_text('eula=true\n')
probe=root/'general-probe.jar'
if probe.exists():shutil.copyfile(probe,server/'mods/general-probe.jar')
template=server
for mode in (sys.argv[4:] or ['enabled','ores_off','default','disabled','empty_tags','inactive_marker']):
    server=root/('general-server-'+label+'-'+mode)
    server.mkdir(exist_ok=True)
    shutil.copytree(template/'mods',server/'mods',dirs_exist_ok=True)
    shutil.copytree(template/'config',server/'config',dirs_exist_ok=True)
    (server/'eula.txt').write_text('eula=true\n')
    (server/'config/wwoo/config.json5').write_text(json.dumps({} if mode=='default' else {'removeOres':mode!='ores_off'}))
    ids=['wwoo:resources/wwoo_main','wwoo:resources/vanilla_backport_compat','wwoo:resources/wwoo_remove_ores']
    (server/'config/cristellib/built_in_packs.json5').write_text(json.dumps({'defaultPacks':[] if mode=='disabled' else ids,'disabledPacks':ids if mode=='disabled' else [],'hideAllPacksInScreen':False}))
    world=server/('general-'+label+'-'+mode)
    pack=world/'datapacks/audit'
    def write(path,v):
        p=pack/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(v if isinstance(v,str) else json.dumps(v))
    write('pack.mcmeta',{'pack':{'pack_format':48,'description':'General behavior audit'}})
    write('data/minecraft/tags/function/load.json',{'values':['audit:load']})
    write('data/audit/function/load.mcfunction','gamerule spawnChunkRadius 0\n')
    if mode=='empty_tags':write('data/minecraft/tags/block/supports_vegetation.json',{'replace':True,'values':[]})
    if mode=='inactive_marker':
        write('data/wwoocontent/tags/block/plant_support_active.json',{'replace':True,'values':[]})
        write('data/minecraft/tags/block/supports_vegetation.json',{'replace':True,'values':[]})
    if probe.exists():write('data/audit/worldgen/configured_feature/probe.json',{'type':'audit:probe','config':{}})
    commands=['forceload add 0 0 31 15','gamerule doMobSpawning false','fill 0 99 0 20 102 2 minecraft:air']
    for x,block,ground in [(0,'oak_sapling','grass_block'),(4,'wheat','farmland'),(8,'sugar_cane','sand')]:
        write('data/audit/worldgen/configured_feature/'+block+'.json',{'type':'minecraft:simple_block','config':{'to_place':{'type':'minecraft:simple_state_provider','state':{'Name':'minecraft:'+block}}}})
        commands += [f'setblock {x} 99 0 minecraft:{ground}']
        if block=='sugar_cane':commands += ['setblock 8 99 1 minecraft:water']
        commands += [f'place feature audit:{block} {x} 100 0',f'execute if block {x} 100 0 minecraft:{block} run say AUDIT_{block.upper()}_SURVIVES',f'execute unless block {x} 100 0 minecraft:{block} run say AUDIT_{block.upper()}_FAILS']
    if probe.exists():commands += ['place feature audit:probe 20 100 0']
    commands += ['say AUDIT_CHECKS_DONE']
    write('data/audit/function/checks.mcfunction','\n'.join(commands)+'\n')
    flat={'layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:dirt','height':2},{'block':'minecraft:grass_block','height':1}],'biome':'minecraft:plains','structure_overrides':[]}
    (server/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nview-distance=2\nsimulation-distance=2\nmax-tick-time=-1\nlevel-seed=6539758231619421816\nlevel-name=general-'+label+'-'+mode+'\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(flat)+'\n')
    log=root/('general-'+label+'-'+mode+'.log')
    # Each run uses a fresh server fixture; discard its temporary remap cache.
    cache=server/'.fabric/remappedJars'
    if cache.exists():shutil.rmtree(cache)
    with log.open('w') as out:
        p=subprocess.Popen([str(root/'jdk-21.0.2/bin/java'),'-Xmx3G','-Dfabric.gameJarPath='+str(root/'server/server-game.jar'),'-cp',(root/'server/classpath.txt').read_text(),'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui'],cwd=server,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True)
        sent=False;stop=False;start=time.monotonic()
        try:
            while p.poll() is None:
                s=log.read_text(errors='replace')
                if 'Done (' in s and not sent:
                    p.stdin.write('datapack list enabled\nfunction audit:checks\n');p.stdin.flush();sent=True
                if 'AUDIT_CHECKS_DONE' in s and not stop:
                    p.stdin.write('save-all flush\nstop\n');p.stdin.flush();stop=True
                if time.monotonic()-start>180:raise TimeoutError(mode)
                time.sleep(1)
            print(mode,'EXIT',p.returncode,flush=True)
            if p.returncode or not sent or not stop:raise RuntimeError('Server fixture failed: '+mode)
            s=log.read_text()
            for plant in ['OAK_SAPLING','WHEAT','SUGAR_CANE']:
                outcome='FAILS' if mode=='empty_tags' and plant=='OAK_SAPLING' else 'SURVIVES'
                assert 'AUDIT_'+plant+'_'+outcome in s,(mode,plant)
            condition=mode!='ores_off'
            assert 'AUDIT_REMOVE_CONDITION='+str(condition).lower() in s,mode
            packs=next(line for line in s.splitlines() if 'data pack(s) enabled' in line)
            active=mode not in ['ores_off','disabled']
            assert ('wwoo:resources/wwoo_remove_ores' in packs)==active,mode
            rows=[line.split('AUDIT_ORE=',1)[1].split(' ',1) for line in s.splitlines() if 'AUDIT_ORE=' in line]
            assert len(rows)==(8 if mode=='disabled' else 16),(mode,len(rows))
            for name,data in rows:
                counts=[p['count'] for p in json.loads(data) if p['type']=='minecraft:count']
                assert (bool(counts) and all(n==0 for n in counts)) if active else all(n!=0 for n in counts),(mode,name,counts)
            print('GENERAL_BEHAVIOR_PASS',mode,flush=True)
            for line in log.read_text().splitlines():
                if 'AUDIT_' in line or 'data pack(s) enabled' in line:print(line,flush=True)
        finally:
            if p.poll() is None:p.kill();p.wait()
