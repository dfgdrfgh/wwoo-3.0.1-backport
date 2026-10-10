"""Dedicated-server integration: sizes/default migration/dimensions/disabled pack.

Requires bootstrap, mod build, build_biome_size_probe.py, and the normal test
dependencies (Fabric API, Cristel Lib and patched TrueVanillaBackport).
"""
from pathlib import Path
import json, os, shutil, subprocess, sys, time, tempfile

root = Path(__file__).resolve().parent.parent
jdk = Path(os.environ['WWOO_JAVA_HOME'])/'bin' if 'WWOO_JAVA_HOME' in os.environ else Path(shutil.which('java')).resolve().parent
fixtures = Path(tempfile.mkdtemp(prefix='biome-size-audit-', dir=root/'test-runtime'))
print('Fixtures:', fixtures, flush=True)
for mode, multiplier, enabled in [('default',1.0,True), ('small',0.5,True), ('large',2.0,True), ('minimum',0.25,True), ('maximum',8.0,True), ('disabled',2.0,False), ('worldgen',2.0,True)]:
    if sys.argv[1:] and mode not in sys.argv[1:]: continue
    folder = fixtures / mode
    mods = folder/'mods'; mods.mkdir(parents=True)
    for p in (root/'test-dependencies').glob('*.jar'): shutil.copy2(p,mods/p.name)
    shutil.copy2(root/'wwoo-fabric-1.21.1-3.0.1-backport.jar',mods/'wwoo.jar')
    shutil.copy2(root/'tests/biome-size-probe.jar',mods/'biome-size-probe.jar')
    config = folder/'config/wwoo'; config.mkdir(parents=True)
    (config/'config.json5').write_text('{"removeOres":false}')
    if mode != 'default': (config/'biome_size.json5').write_text(json.dumps({'sizeMultiplier':multiplier}))
    if not enabled:
        p = folder/'config/cristellib/built_in_packs.json5'; p.parent.mkdir(parents=True,exist_ok=True)
        ids = ['wwoo:resources/wwoo_main','wwoo:resources/vanilla_backport_compat','wwoo:resources/wwoo_remove_ores']
        p.write_text(json.dumps({'defaultPacks':[],'disabledPacks':ids,'hideAllPacksInScreen':False}))
    (folder/'eula.txt').write_text('eula=true\n')
    flat = {'layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:grass_block','height':1}], 'biome':'minecraft:plains','structure_overrides':[]}
    generator = 'level-type=minecraft:normal\ngenerator-settings={}' if mode == 'worldgen' else 'level-type=minecraft:flat\ngenerator-settings='+json.dumps(flat)
    (folder/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nview-distance=2\nsimulation-distance=2\nmax-tick-time=-1\nlevel-name=world\nlevel-seed=1234567\n'+generator+'\n')
    pack = folder/'world/datapacks/audit'; pack.mkdir(parents=True)
    (pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':'Biome size integration test'}}))
    feature = pack/'data/audit/worldgen/configured_feature/probe.json'; feature.parent.mkdir(parents=True)
    feature.write_text('{"type":"audit:biome_size","config":{}}')
    log = folder/'console.log'
    with log.open('w') as out:
        p = subprocess.Popen([str(jdk/'java'),'-Xmx2G','-Dwwoo.audit.multiplier='+str(multiplier),'-Dwwoo.audit.enabled='+str(enabled).lower(),'-Dwwoo.audit.worldgen='+str(mode=='worldgen').lower(),'-Dfabric.gameJarPath='+str(root/'test-runtime/versions/1.21.1/server-1.21.1.jar'),'-cp',(root/'test-runtime/launch-classpath.txt').read_text(),'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui'],cwd=folder,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True)
        sent = stopped = False; start = time.monotonic(); last_probe = start
        try:
            while p.poll() is None:
                text = log.read_text(errors='replace')
                if 'Done (' in text and not sent:
                    p.stdin.write('gamerule spawnChunkRadius 0\nforceload add 0 0 31 31\nplace feature audit:probe 0 64 0\n'); p.stdin.flush(); sent=True
                    last_probe = time.monotonic()
                # Forced chunks finish loading asynchronously in a normal world.
                if sent and not stopped and 'BIOME_SIZE_PASS' not in text and time.monotonic()-last_probe > 2:
                    p.stdin.write('place feature audit:probe 0 64 0\n'); p.stdin.flush(); last_probe=time.monotonic()
                if 'BIOME_SIZE_PASS' in text and not stopped:
                    p.stdin.write('save-all flush\nstop\n'); p.stdin.flush(); stopped=True
                if 'AssertionError' in text or 'Mixin apply for mod' in text or time.monotonic()-start > 180:
                    raise RuntimeError('Biome-size test failed: '+mode+'; see '+str(log))
                time.sleep(0.5)
            assert p.returncode == 0 and sent and stopped, log
        finally:
            if p.poll() is None: p.kill(); p.wait()
    print(mode, 'BIOME_SIZE_PASS', flush=True)
    for line in log.read_text().splitlines():
        if 'BIOME_SIZE_CHECK' in line: print(line,flush=True)
