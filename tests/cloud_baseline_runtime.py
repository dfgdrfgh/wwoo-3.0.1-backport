"""Check the restored release mixins in the real Fabric client, with and without addon presence."""
from pathlib import Path
import concurrent.futures,json,os,platform,shutil,subprocess,urllib.request,zipfile
ROOT=Path(__file__).resolve().parent.parent
JAVA=Path(os.environ['WWOO_JAVA_HOME'])/'bin' if 'WWOO_JAVA_HOME' in os.environ else Path(shutil.which('javac')).resolve().parent
JAR=ROOT/'wwoo-fabric-1.21.1-3.0.1-backport.jar'
folder=ROOT/'test-cloud-baseline';shutil.rmtree(folder,ignore_errors=True);(folder/'mods').mkdir(parents=True)
with zipfile.ZipFile(JAR) as z:
    helpers={n:z.read(n) for n in z.namelist() if n.startswith((
        'com/zachminecraft/wwoocontent/CloudAttributes',
        'com/zachminecraft/wwoocontent/mixin/CloudColorMixin',
        'com/zachminecraft/wwoocontent/mixin/CloudRendererAccess',
        'com/zachminecraft/wwoocontent/mixin/CloudRendererMixin'))}
    attributes=z.read('data/wwoocontent/biome_attributes.json')
manifest=json.load(urllib.request.urlopen('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
version=json.load(urllib.request.urlopen(next(x['url'] for x in manifest['versions'] if x['id']=='1.21.1')))
native_os={'Linux':'linux','Darwin':'macos','Windows':'windows'}[platform.system()]
suffix='-arm64' if platform.machine().lower() in {'aarch64','arm64'} else ''
classifier='natives-'+native_os+suffix
artifacts=[lib['downloads']['artifact'] for lib in version['libraries']
    if ('natives-' not in lib['name'] or lib['name'].endswith(':'+classifier)) and 'artifact' in lib.get('downloads',{})]
def download(a):
    p=ROOT/'test-runtime/libraries'/a['path'];p.parent.mkdir(parents=True,exist_ok=True)
    if not p.exists():urllib.request.urlretrieve(a['url'],p)
    return str(p)
with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:client_libraries=list(pool.map(download,artifacts))
classes=folder/'classes';classes.mkdir()
cp=os.pathsep.join([str(ROOT/'port/helpers-named.jar'),str(ROOT/'minecraft-client-named.jar'),
    (ROOT/'test-runtime/launch-classpath.txt').read_text(),*client_libraries])
subprocess.run([str(JAVA/'javac'),'-proc:none','--release','21','-cp',cp,'-d',str(classes),
    str(ROOT/'tests/com/zachminecraft/wwoocontent/CloudBaselineProbe.java')],check=True)
raw=folder/'probe-named.jar'
with zipfile.ZipFile(raw,'w',zipfile.ZIP_DEFLATED) as z:
    for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
mcp=(ROOT/'mapping-classpath.txt').read_text()+os.pathsep+str(ROOT)
official=folder/'probe-official.jar';inter=folder/'probe-intermediary.jar'
for a,b,m,src,dst,mc in [
    (raw,official,'client-mojmap.tiny','named','official',ROOT/'minecraft-client-named.jar'),
    (official,inter,'intermediary.tiny','official','intermediary',ROOT/'toolchain/client-1.21.1.jar')]:
    subprocess.run([str(JAVA/'java'),'-cp',mcp,'MappingTool','remap',str(a),str(b),str(ROOT/m),src,dst,str(mc)],check=True)
with zipfile.ZipFile(folder/'mods/cloud-baseline-probe.jar','w',zipfile.ZIP_DEFLATED) as z:
    for n,v in helpers.items():z.writestr(n,v)
    with zipfile.ZipFile(inter) as p:
        for n in p.namelist():z.writestr(n,p.read(n))
    z.writestr('data/wwoocontent/biome_attributes.json',attributes)
    z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'wwoo_cloud_baseline_probe','version':'1',
        'environment':'client','entrypoints':{'preLaunch':['com.zachminecraft.wwoocontent.CloudBaselineProbe']},'mixins':['cloud-baseline.mixins.json']}))
    z.writestr('cloud-baseline.mixins.json',json.dumps({'required':True,'package':'com.zachminecraft.wwoocontent.mixin',
        'compatibilityLevel':'JAVA_21','client':['CloudColorMixin','CloudRendererAccess','CloudRendererMixin'],'injectors':{'defaultRequire':1}}))
launch_cp=os.pathsep.join([(ROOT/'test-runtime/launch-classpath.txt').read_text(),str(ROOT/'toolchain/lib/intermediary.jar'),*client_libraries])
command=[str(JAVA/'java'),'-Xmx2G','-Dfabric.gameJarPath='+str(ROOT/'toolchain/client-1.21.1.jar'),
    '-cp',launch_cp,'net.fabricmc.loader.impl.launch.knot.KnotClient']
for bypass in [False,True]:
    if bypass:
        # This fixture tests only loader-presence handoff, not the standalone mod's own renderer.
        with zipfile.ZipFile(folder/'mods/addon-presence.jar','w') as z:
            z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'biome_cloud_transitions','version':'1.0.0','environment':'client'}))
    result=subprocess.run(command,cwd=folder,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=180)
    (folder/('addon-presence.log' if bypass else 'baseline.log')).write_text(result.stdout)
    print(result.stdout[-15000:])
    assert result.returncode==0 and 'CLOUD_BASELINE_RUNTIME_PASS' in result.stdout
    assert 'bypass='+str(bypass).lower() in result.stdout
print('CLOUD_BASELINE_HANDOFF_PASS: real restored hooks tested with ordinary cloud behavior and addon-presence bypass')
