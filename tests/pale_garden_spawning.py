"""Test the release's exact spawn mixins on an isolated Minecraft 1.21.1 server.

A test-only entity with the creaking ID checks exemption; it does not simulate a heart.
No WWOO/VBP content is substituted in the installable mod.
"""
from pathlib import Path
import json,os,shutil,subprocess,time,zipfile
ROOT=Path(__file__).resolve().parent.parent
JAVA=Path(os.environ['WWOO_JAVA_HOME'])/'bin' if 'WWOO_JAVA_HOME' in os.environ else Path(shutil.which('javac')).resolve().parent
JAR=ROOT/'wwoo-fabric-1.21.1-3.0.1-backport.jar'
MIXINS=['PaleGardenSpawnMixins$'+n for n in ('Placement','SpawnReason','ServerInsertion','GenerationInsertion')]
with zipfile.ZipFile(JAR) as z:
    biome=json.loads(z.read('resources/wwoo_main/data/minecraft/worldgen/biome/pale_garden.json'))
    assert all(row['type'] in {'minecraft:creaking','minecraft:phantom'} for rows in biome['spawners'].values() for row in rows)
    assert all(n in json.loads(z.read('wwoo-content.common.mixins.json'))['mixins'] for n in MIXINS)
    helpers={n:z.read(n) for n in z.namelist() if n.startswith('com/zachminecraft/wwoocontent/PaleGardenSpawning') or n.startswith('com/zachminecraft/wwoocontent/mixin/PaleGardenSpawnMixins')}
folder=ROOT/'test-spawning';shutil.rmtree(folder,ignore_errors=True)
(folder/'mods').mkdir(parents=True)
classes=folder/'classes';classes.mkdir()
cp=(ROOT/'compile-classpath.txt').read_text()+os.pathsep+str(ROOT/'port/helpers-named.jar')
subprocess.run([str(JAVA/'javac'),'-proc:none','--release','21','-cp',cp,'-d',str(classes),str(ROOT/'tests/com/zachminecraft/wwoocontent/SpawnProbe.java'),str(ROOT/'tests/com/zachminecraft/wwoocontent/mixin/SpawnProbeAttributes.java')],check=True)
raw=folder/'probe-named.jar'
with zipfile.ZipFile(raw,'w',zipfile.ZIP_DEFLATED) as z:
    for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
mcp=(ROOT/'mapping-classpath.txt').read_text()+os.pathsep+str(ROOT)
inter=folder/'probe-intermediary.jar';official=folder/'probe-official.jar'
for a,b,m,src,dst,mc in [(raw,official,ROOT/'mojmap.tiny','named','official',ROOT/'minecraft-named.jar'),(official,inter,ROOT/'intermediary.tiny','official','intermediary',ROOT/'test-runtime/versions/1.21.1/server-1.21.1.jar')]:
    subprocess.run([str(JAVA/'java'),'-cp',mcp,'MappingTool','remap',str(a),str(b),str(m),src,dst,str(mc)],check=True)
with zipfile.ZipFile(folder/'mods/spawn-probe.jar','w',zipfile.ZIP_DEFLATED) as z:
    for n,v in helpers.items():z.writestr(n,v)
    with zipfile.ZipFile(inter) as p:
        for n in p.namelist():z.writestr(n,p.read(n))
    z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'wwoo_spawn_probe','version':'1','environment':'server','entrypoints':{'main':['com.zachminecraft.wwoocontent.SpawnProbe']},'mixins':['spawn-probe.mixins.json']}))
    z.writestr('spawn-probe.mixins.json',json.dumps({'required':True,'package':'com.zachminecraft.wwoocontent.mixin','compatibilityLevel':'JAVA_21','mixins':MIXINS+['SpawnProbeAttributes'],'injectors':{'defaultRequire':1}}))
pack=folder/'world/datapacks/spawning'
def put(path,value):
    p=pack/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value))
put('pack.mcmeta',{'pack':{'pack_format':48,'description':'Spawn restriction runtime fixture'}})
with zipfile.ZipFile(ROOT/'minecraft-named.jar') as z:
    fixture=json.loads(z.read('data/minecraft/worldgen/biome/plains.json'))
fixture['features']=[[] for _ in fixture['features']]
fixture['features'][9]=['audit:spawn_probe']
put('data/minecraft/worldgen/biome/pale_garden.json',fixture)
put('data/audit/worldgen/configured_feature/spawn_probe.json',{'type':'audit:spawn_probe','config':{}})
put('data/audit/worldgen/placed_feature/spawn_probe.json',{'feature':'audit:spawn_probe','placement':[]})
for name,biome_id in [('pale','pale_garden'),('plains','plains')]:
    put(f'data/audit/dimension/{name}.json',{'type':'minecraft:overworld','generator':{'type':'minecraft:noise','settings':'minecraft:overworld','biome_source':{'type':'minecraft:fixed','biome':'minecraft:'+biome_id}}})
(folder/'eula.txt').write_text('eula=true\n')
(folder/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nview-distance=2\nsimulation-distance=2\nmax-tick-time=-1\nlevel-seed=1234567\nlevel-name=world\nspawn-protection=0\n')
log=folder/'console.log';markers=['WWOO_SPAWN_'+m+'_PASS' for m in ('WORLDGEN','PALE','PLAINS')]
with log.open('w') as out:
    p=subprocess.Popen([str(JAVA/'java'),'-Xmx3G','-Dfabric.gameJarPath='+str(ROOT/'test-runtime/versions/1.21.1/server-1.21.1.jar'),'-cp',(ROOT/'test-runtime/launch-classpath.txt').read_text()+os.pathsep+str(ROOT/'toolchain/lib/intermediary.jar'),'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui'],cwd=folder,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True)
    try:
        start=time.monotonic();sent=False;stopped=False
        while p.poll() is None:
            output=log.read_text(errors='replace')
            if 'Done (' in output and not sent:
                p.stdin.write('gamerule doMobSpawning false\ntime set noon\nexecute in audit:pale run forceload add 0 0\nexecute in audit:plains run forceload add 0 0\nexecute in audit:pale run place feature audit:spawn_probe 0 120 0\nexecute in audit:plains run place feature audit:spawn_probe 0 120 0\n');p.stdin.flush();sent=True
            if all(marker in output for marker in markers) and not stopped:
                p.stdin.write('stop\n');p.stdin.flush();stopped=True
            if 'WWOO_SPAWN_FAIL' in output or time.monotonic()-start>360:raise RuntimeError('Spawn runtime test failed or timed out')
            time.sleep(1)
        if p.returncode or not all(marker in log.read_text() for marker in markers):raise RuntimeError('Spawn runtime markers missing')
    except Exception:
        print(log.read_text(errors='replace')[-18000:]);raise
    finally:
        if p.poll() is None:p.kill();p.wait()
print('Pale Garden spawning runtime checks passed: standard/modded mobs, phantoms, events, generation, creaking whitelist, manual spawns, and consumed flags.')
