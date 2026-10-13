"""Load the exact release cloud mixin into the real Fabric client without creating a window.

The pre-launch probe invokes its transformed draw hook using actual RenderSystem
shader state. No GPU draw, game assets, substitute Minecraft classes or world required.
"""
from pathlib import Path
import concurrent.futures,json,os,platform,shutil,subprocess,urllib.request,zipfile

ROOT=Path(__file__).resolve().parent.parent
JAVA=Path(os.environ['WWOO_JAVA_HOME'])/'bin' if 'WWOO_JAVA_HOME' in os.environ else Path(shutil.which('javac')).resolve().parent
JAR=ROOT/'wwoo-fabric-1.21.1-3.0.1-backport.jar'
folder=ROOT/'test-cloud-rendering';shutil.rmtree(folder,ignore_errors=True)
(folder/'mods').mkdir(parents=True)
with zipfile.ZipFile(JAR) as z:
    config=json.loads(z.read('wwoo-content.client.mixins.json'))
    assert 'CloudRendererMixin' in config['client']
    assert not {'CloudColorMixin','CloudRendererAccess'} & set(config['client'])
    helpers={n:z.read(n) for n in z.namelist() if n.startswith((
        'com/zachminecraft/wwoocontent/CloudBlend',
        'com/zachminecraft/wwoocontent/CloudAttributes',
        'com/zachminecraft/wwoocontent/mixin/CloudRendererMixin'))}
    attributes=z.read('data/wwoocontent/biome_attributes.json')
    assert 'com/zachminecraft/wwoocontent/CloudTint.class' not in z.namelist()
    assert not any('CloudRenderProbe' in n for n in z.namelist())

manifest=json.load(urllib.request.urlopen('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
version=json.load(urllib.request.urlopen(next(x['url'] for x in manifest['versions'] if x['id']=='1.21.1')))
# RenderSystem initializes Minecraft's native memory allocator, even without a window.
native_os={'Linux':'linux','Darwin':'macos','Windows':'windows'}[platform.system()]
native_suffix='-arm64' if platform.machine().lower() in {'aarch64','arm64'} else ''
native_classifier='natives-'+native_os+native_suffix
artifacts=[lib['downloads']['artifact'] for lib in version['libraries']
    if ('natives-' not in lib['name'] or lib['name'].endswith(':'+native_classifier))
    and 'artifact' in lib.get('downloads',{})]
def download(artifact):
    path=ROOT/'test-runtime/libraries'/artifact['path']
    if not path.exists():
        path.parent.mkdir(parents=True,exist_ok=True)
        urllib.request.urlretrieve(artifact['url'],path)
    return str(path)
with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
    client_libraries=list(pool.map(download,artifacts))

classes=folder/'classes';classes.mkdir()
cp=os.pathsep.join([str(JAR),str(ROOT/'minecraft-client-named.jar'),
    (ROOT/'test-runtime/launch-classpath.txt').read_text(),*client_libraries])
subprocess.run([str(JAVA/'javac'),'-proc:none','--release','21','-cp',cp,'-d',str(classes),
    str(ROOT/'tests/com/zachminecraft/wwoocontent/CloudRenderProbe.java')],check=True)
raw=folder/'probe-named.jar'
with zipfile.ZipFile(raw,'w',zipfile.ZIP_DEFLATED) as z:
    for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
mcp=(ROOT/'mapping-classpath.txt').read_text()+os.pathsep+str(ROOT)
official=folder/'probe-official.jar';inter=folder/'probe-intermediary.jar'
for a,b,m,src,dst,mc in [
    (raw,official,ROOT/'client-mojmap.tiny','named','official',ROOT/'minecraft-client-named.jar'),
    (official,inter,ROOT/'intermediary.tiny','official','intermediary',ROOT/'toolchain/client-1.21.1.jar')]:
    subprocess.run([str(JAVA/'java'),'-cp',mcp,'MappingTool','remap',str(a),str(b),str(m),src,dst,str(mc)],check=True)
with zipfile.ZipFile(folder/'mods/cloud-probe.jar','w',zipfile.ZIP_DEFLATED) as z:
    for n,v in helpers.items():z.writestr(n,v)
    with zipfile.ZipFile(inter) as p:
        for n in p.namelist():z.writestr(n,p.read(n))
    z.writestr('data/wwoocontent/biome_attributes.json',attributes)
    z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'wwoo_cloud_probe','version':'1',
        'environment':'client','entrypoints':{'preLaunch':['com.zachminecraft.wwoocontent.CloudRenderProbe']},
        'mixins':['cloud-probe.mixins.json']}))
    z.writestr('cloud-probe.mixins.json',json.dumps({'required':True,
        'package':'com.zachminecraft.wwoocontent.mixin','compatibilityLevel':'JAVA_21',
        'client':['CloudRendererMixin'],'injectors':{'defaultRequire':1}}))
launch_cp=os.pathsep.join([(ROOT/'test-runtime/launch-classpath.txt').read_text(),
    str(ROOT/'toolchain/lib/intermediary.jar'),*client_libraries])
result=subprocess.run([str(JAVA/'java'),'-Xmx2G',
    '-Dfabric.gameJarPath='+str(ROOT/'toolchain/client-1.21.1.jar'),
    '-cp',launch_cp,'net.fabricmc.loader.impl.launch.knot.KnotClient'],
    cwd=folder,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=180)
(folder/'console.log').write_text(result.stdout)
print(result.stdout[-22000:])
assert result.returncode==0 and 'CLOUD_RENDER_PASS' in result.stdout,'Client cloud draw regression failed'
