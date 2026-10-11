"""Test an external Modrinth pack without changing it or uploading its contents.

After bootstrap/build: python3 tests/smp_pack.py /absolute/path/pack.mrpack
Server-unsupported client mods are excluded according to the pack manifest.
The sample fixture changes config temporarily; real generation/parity fixtures
run separately without the probe, avoiding config changes during chunk workers.
"""
from pathlib import Path
import gzip,struct,zlib
import argparse,concurrent.futures,hashlib,json,os,shutil,subprocess,sys,tempfile,time,urllib.request,zipfile
root=Path(__file__).resolve().parent.parent
sys.path.insert(0,str(root/'port'))
import nbt
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('mrpack',type=Path)
parser.add_argument('--seed',type=int)
parser.add_argument('--modes',nargs='+',default=['sample','original','default','shrink','expand','global2'])
args=parser.parse_args()
fixtures=Path(tempfile.mkdtemp(prefix='smp-pack-audit-',dir=root/'test-runtime'))
print('Fixtures:',fixtures,flush=True)
base=fixtures/'pack';base.mkdir()
with zipfile.ZipFile(args.mrpack) as pack:
    index=json.loads(pack.read('modrinth.index.json'))
    assert index['dependencies']['minecraft']=='1.21.1','This audit targets Minecraft 1.21.1'
    for prefix in ['overrides/','server-overrides/']:
        for name in pack.namelist():
            if not name.startswith(prefix) or name.endswith('/'):continue
            relative=Path(name.removeprefix(prefix))
            assert not relative.is_absolute() and '..' not in relative.parts
            if relative.parts[0] not in ['mods','config']:continue
            destination=base/relative;destination.parent.mkdir(parents=True,exist_ok=True)
            destination.write_bytes(pack.read(name))
    seed=args.seed
    saved=[name for name in pack.namelist() if name.startswith('overrides/saves/') and name.endswith('/level.dat')]
    if seed is None and len(saved)==1:
        seed=nbt.plain(nbt.read(pack.read(saved[0]))[1])['Data']['WorldGenSettings']['seed']
    if seed is None:seed=1234567
unsupported={Path(f['path']).name for f in index['files'] if f.get('env',{}).get('server')=='unsupported'}
def fetch(f):
    relative=Path(f['path']);assert not relative.is_absolute() and '..' not in relative.parts
    destination=base/relative;destination.parent.mkdir(parents=True,exist_ok=True)
    for url in f['downloads']:
        try:
            data=urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'WWOO-modpack-audit/1.0'}),timeout=90).read()
            assert all(hashlib.new(algorithm,data).hexdigest()==digest for algorithm,digest in f['hashes'].items()),'Pack download checksum mismatch'
            destination.write_bytes(data);return
        except Exception as error:last_error=error
    raise last_error
files=[f for f in index['files'] if f['path'].startswith('mods/') and Path(f['path']).name not in unsupported]
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:list(pool.map(fetch,files))
loader=index['dependencies']['fabric-loader']
profile=json.load(urllib.request.urlopen(f'https://meta.fabricmc.net/v2/versions/loader/1.21.1/{loader}/server/json'))
libraries=[]
for library in profile['libraries']:
    group,artifact,version=library['name'].split(':')
    relative=f'{group.replace(".","/")}/{artifact}/{version}/{artifact}-{version}.jar'
    destination=fixtures/'libraries'/relative;destination.parent.mkdir(parents=True,exist_ok=True)
    data=urllib.request.urlopen(library['url']+relative).read()
    if 'sha256' in library:assert hashlib.sha256(data).hexdigest()==library['sha256']
    destination.write_bytes(data);libraries.append(str(destination))
classpath=(root/'test-runtime/launch-classpath.txt').read_text().split(os.pathsep)
classpath=os.pathsep.join([p for p in classpath if '/test-runtime/libraries/' in p and '/net/fabricmc/' not in p]+libraries)
jdk=Path(os.environ['WWOO_JAVA_HOME'])/'bin' if 'WWOO_JAVA_HOME' in os.environ else Path(shutil.which('java')).resolve().parent
subprocess.run([sys.executable,str(root/'tests/build_smp_pack_probe.py')],check=True)
jar=root/'wwoo-fabric-1.21.1-3.0.1-backport.jar'
with zipfile.ZipFile(jar) as z:assert z.testzip() is None
jar_names=[]
for path in (base/'mods').glob('*.jar'):
    with zipfile.ZipFile(path) as z:
        if 'fabric.mod.json' in z.namelist() and json.loads(z.read('fabric.mod.json')).get('id')=='wwoo':jar_names.append(path.name)
assert len(jar_names)==1,'Pack must include exactly one WWOO JAR'
def verify_saved_chunks(folder,targets):
    regions={};checked=0
    for dimension,bx,bz in targets:
        directory={'minecraft:overworld':'region','minecraft:the_nether':'DIM-1/region','minecraft:the_end':'DIM1/region','the_sift:the_sift':'dimensions/the_sift/the_sift/region'}[dimension]
        for dx in range(2):
            for dz in range(2):
                x=bx//16+dx;z=bz//16+dz
                path=folder/'world'/directory/f'r.{x//32}.{z//32}.mca'
                if path not in regions:regions[path]=path.read_bytes()
                data=regions[path];location=struct.unpack_from('>I',data,4*(x%32+(z%32)*32))[0]
                assert location,(dimension,x,z,'chunk not saved')
                offset=(location>>8)*4096;length=struct.unpack_from('>I',data,offset)[0]
                compression=data[offset+4];payload=data[offset+5:offset+4+length]
                assert compression in [1,2,3],'Unsupported region compression'
                if compression==1:payload=gzip.decompress(payload)
                elif compression==2:payload=zlib.decompress(payload)
                chunk=nbt.plain(nbt.read(payload)[1])
                assert chunk['Status']=='minecraft:full',(dimension,x,z,'generation incomplete')
                checked+=1
    return checked
summary={'pack':index['name'],'version':index['versionId'],'pack_sha256':hashlib.sha256(args.mrpack.read_bytes()).hexdigest(),'loader':loader,'seed':seed,'candidate_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'server_jars':[],'scenarios':[]}
for path in (base/'mods').glob('*.jar'):
    if path.name not in unsupported:summary['server_jars'].append({'file':path.name,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
for mode in args.modes:
    assert mode in ['sample','original','default','shrink','expand','global2']
    folder=fixtures/mode;mods=folder/'mods';mods.mkdir(parents=True)
    shutil.copytree(base/'config',folder/'config',dirs_exist_ok=True)
    for path in (base/'mods').glob('*.jar'):
        if path.name not in unsupported:shutil.copy2(path,mods/path.name)
    if mode!='original':shutil.copy2(jar,mods/jar_names[0])
    config=folder/'config/wwoo';config.mkdir(parents=True,exist_ok=True)
    (config/'biome_size.json5').write_text(json.dumps({'sizeMultiplier':2 if mode=='global2' else 1,'biomeScales':{'minecraft:pale_garden':0.5 if mode=='shrink' else 2} if mode in ['shrink','expand'] else {}}))
    probe=mode=='sample'
    if probe:
        shutil.copy2(root/'test-runtime/smp-pack-probe/probe.jar',mods/'smp-pack-probe.jar')
        pack=folder/'world/datapacks/audit';pack.mkdir(parents=True)
        (pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':'Loaded modpack audit'}}))
        feature=pack/'data/audit/worldgen/configured_feature/pack_biomes.json';feature.parent.mkdir(parents=True)
        feature.write_text('{"type":"audit:pack_biomes","config":{}}')
    (folder/'eula.txt').write_text('eula=true\n')
    (folder/'server.properties').write_text(f'online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nview-distance=2\nsimulation-distance=2\nmax-tick-time=-1\nlevel-name=world\nlevel-seed={seed}\nlevel-type=minecraft:normal\ngenerator-settings={{}}\n')
    chunks=[(d,x,z) for d in ['minecraft:overworld','minecraft:the_nether','minecraft:the_end','the_sift:the_sift'] for x,z in [(0,0),(100032,-80032)]]
    # Omit the optional custom dimension for other external packs.
    if not any(path.name.startswith('mielons-the-sift-') for path in (base/'mods').glob('*.jar')):chunks=[c for c in chunks if c[0]!='the_sift:the_sift']
    log=folder/'console.log'
    with log.open('w') as output:
        process=subprocess.Popen([str(jdk/'java'),'-Xmx4G','-XX:ActiveProcessorCount=4','-Dfabric.gameJarPath='+str(root/'test-runtime/versions/1.21.1/server-1.21.1.jar'),'-cp',classpath,'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui'],cwd=folder,stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,text=True)
        sent=stopped=chunks_sent=False;probe_done=not probe;start=last_check=time.monotonic()
        try:
            while process.poll() is None:
                text=log.read_text(errors='replace');now=time.monotonic()
                if 'Done (' in text and not sent:
                    process.stdin.write('gamerule spawnChunkRadius 0\ngamerule randomTickSpeed 0\ngamerule doMobSpawning false\nforceload add 0 0 31 31\n')
                    if probe:process.stdin.write('place feature audit:pack_biomes 0 64 0\n')
                    process.stdin.flush();sent=True;last_check=now
                if sent and probe and not probe_done and 'SMP_PACK_STARTED' not in text and now-last_check>5:
                    process.stdin.write('place feature audit:pack_biomes 0 64 0\n');process.stdin.flush();last_check=now
                if 'SMP_PACK_PASS' in text:probe_done=True
                if sent and probe_done and not chunks_sent:
                    for dimension,x,z in chunks:process.stdin.write(f'execute in {dimension} run forceload add {x} {z} {x+31} {z+31}\n')
                    process.stdin.flush();chunks_sent=True;last_check=0
                if chunks_sent and not stopped and now-last_check>3:
                    for i,(dimension,x,z) in enumerate(chunks):process.stdin.write(f'execute in {dimension} if loaded {x} 64 {z} if loaded {x+31} 64 {z+31} run say SMP_CHUNKS_PASS_{i}\n')
                    process.stdin.flush();last_check=now
                if chunks_sent and not stopped and all('SMP_CHUNKS_PASS_'+str(i) in text for i in range(len(chunks))):
                    process.stdin.write('save-all flush\nstop\n');process.stdin.flush();stopped=True
                if 'AssertionError' in text or 'Mixin apply for mod' in text or now-start>600:raise RuntimeError('Pack test failed: '+str(log))
                time.sleep(.5)
            assert process.returncode==0 and sent and stopped,log
        finally:
            if process.poll() is None:process.kill();process.wait()
    if probe:assert log.read_text().count('SMP_PACK_STARTED')==1,'Probe was queued twice'
    result={'mode':mode,'passed':True,'full_saved_target_chunks':verify_saved_chunks(folder,chunks),'log':str(log)}
    summary['scenarios'].append(result);print('SMP_SCENARIO_PASS',mode,flush=True)
(fixtures/'summary.json').write_text(json.dumps(summary,indent=2))
print('Summary:',fixtures/'summary.json',flush=True)
