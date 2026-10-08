"""Download public build dependencies and prepare the 1.21.1 named compile classpath."""
from pathlib import Path
import urllib.request,json,zipfile,subprocess,os,shutil,concurrent.futures
ROOT=Path(__file__).resolve().parent.parent
LIB=ROOT/'toolchain/lib';LIB.mkdir(parents=True,exist_ok=True)
def download(url,path):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
    if not path.exists():urllib.request.urlretrieve(url,path)
    return path
def java_bin():
    if 'WWOO_JAVA_HOME' in os.environ:return Path(os.environ['WWOO_JAVA_HOME'])/'bin'
    if (ROOT/'toolchain/jdk/bin').exists():return ROOT/'toolchain/jdk/bin'
    p=shutil.which('javac')
    if not p:raise SystemExit('Install JDK 21 or set WWOO_JAVA_HOME to its directory.')
    return Path(p).resolve().parent
jdk=java_bin()
artifacts=[('net/fabricmc/mapping-io/0.8.0/mapping-io-0.8.0.jar','mapping-io.jar'),('net/fabricmc/tiny-remapper/0.14.0/tiny-remapper-0.14.0.jar','tiny-remapper.jar'),('net/fabricmc/fabric-loader/0.18.5/fabric-loader-0.18.5.jar','fabric-loader.jar')]
artifacts.extend(('org/ow2/asm/'+a+'/9.9/'+a+'-9.9.jar',a+'.jar') for a in ['asm','asm-analysis','asm-commons','asm-tree','asm-util'])
with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:list(pool.map(lambda a:download('https://maven.fabricmc.net/'+a[0],LIB/a[1]),artifacts))
cp=os.pathsep.join(str(p.resolve()) for p in LIB.glob('*.jar'));(ROOT/'mapping-classpath.txt').write_text(cp)
subprocess.run([str(jdk/'javac'),'--release','21','-cp',cp,str(ROOT/'MappingTool.java')],check=True,cwd=ROOT)
manifest=json.load(urllib.request.urlopen('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
entry=next(x for x in manifest['versions'] if x['id']=='1.21.1');version=json.load(urllib.request.urlopen(entry['url']))
runtime=ROOT/'test-runtime';runtime.mkdir(exist_ok=True)
download('https://maven.fabricmc.net/net/fabricmc/sponge-mixin/0.17.0+mixin.0.8.7/sponge-mixin-0.17.0+mixin.0.8.7.jar',runtime/'libraries/net/fabricmc/sponge-mixin/0.17.0+mixin.0.8.7/sponge-mixin-0.17.0+mixin.0.8.7.jar')
server=runtime/'versions/1.21.1/server-1.21.1.jar';server.parent.mkdir(parents=True,exist_ok=True)
if not server.exists() or not (runtime/'libraries').exists():
    bundle=download(version['downloads']['server']['url'],ROOT/'toolchain/server-bundle.jar')
    with zipfile.ZipFile(bundle) as z:
        if not server.exists():server.write_bytes(z.read('META-INF/versions/1.21.1/server-1.21.1.jar'))
        for name in z.namelist():
            if name.startswith('META-INF/libraries/') and name.endswith('.jar'):
                p=runtime/'libraries'/name.removeprefix('META-INF/libraries/');p.parent.mkdir(parents=True,exist_ok=True)
                if not p.exists():p.write_bytes(z.read(name))
mappings=download(version['downloads']['server_mappings']['url'],ROOT/'toolchain/server-mappings.txt')
subprocess.run([str(jdk/'java'),'-cp',cp+os.pathsep+str(ROOT),'MappingTool','mappings',str(mappings),str(ROOT/'mojmap.tiny')],check=True,cwd=ROOT)
intermediary=download('https://maven.fabricmc.net/net/fabricmc/intermediary/1.21.1/intermediary-1.21.1.jar',LIB/'intermediary.jar')
with zipfile.ZipFile(intermediary) as z:(ROOT/'intermediary.tiny').write_bytes(z.read('mappings/mappings.tiny'))
if not (ROOT/'minecraft-named.jar').exists():
    subprocess.run([str(jdk/'java'),'-Xmx2G','-cp',cp+os.pathsep+str(ROOT),'MappingTool','remap',str(server),str(ROOT/'minecraft-named.jar'),str(ROOT/'mojmap.tiny'),'official','named'],check=True,cwd=ROOT)
client=download(version['downloads']['client']['url'],ROOT/'toolchain/client-1.21.1.jar')
client_mappings=download(version['downloads']['client_mappings']['url'],ROOT/'toolchain/client-mappings.txt')
subprocess.run([str(jdk/'java'),'-cp',cp+os.pathsep+str(ROOT),'MappingTool','mappings',str(client_mappings),str(ROOT/'client-mojmap.tiny')],check=True,cwd=ROOT)
if not (ROOT/'minecraft-client-named.jar').exists():
    subprocess.run([str(jdk/'java'),'-Xmx2G','-cp',cp+os.pathsep+str(ROOT),'MappingTool','remap',str(client),str(ROOT/'minecraft-client-named.jar'),str(ROOT/'client-mojmap.tiny'),'official','named'],check=True,cwd=ROOT)
modern_metadata=ROOT/'toolchain/26.3-version.json'
if not modern_metadata.exists():
    entry=next(x for x in manifest['versions'] if x['id']=='26.3')
    download(entry['url'],modern_metadata)
modern=json.loads(modern_metadata.read_text())
if not (ROOT/'toolchain/minecraft-26.3.jar').exists():
    bundle=download(modern['downloads']['server']['url'],ROOT/'toolchain/server-26.3-bundle.jar')
    with zipfile.ZipFile(bundle) as z:
        (ROOT/'toolchain/minecraft-26.3.jar').write_bytes(z.read('META-INF/versions/'+modern['id']+'/server-'+modern['id']+'.jar'))
compilecp=os.pathsep.join([str(ROOT/'minecraft-named.jar'),cp,*[str(p.resolve()) for p in (runtime/'libraries').rglob('*.jar')]])
(ROOT/'compile-classpath.txt').write_text(compilecp)
(runtime/'launch-classpath.txt').write_text(os.pathsep.join([*[str(p.resolve()) for p in (runtime/'libraries').rglob('*.jar')],cp]))
print('Ready. Run python3 port/build.py from the project directory.')
