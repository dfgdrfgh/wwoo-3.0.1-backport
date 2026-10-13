"""Build a removable client-only measurement add-on; the installed WWOO JAR stays unchanged."""
from pathlib import Path
import os,shutil,subprocess,json,zipfile,urllib.request,concurrent.futures
ROOT=Path(__file__).resolve().parent.parent
JAVA=Path(os.environ['WWOO_JAVA_HOME'])/'bin' if 'WWOO_JAVA_HOME' in os.environ else Path(shutil.which('javac')).resolve().parent
folder=ROOT/'diagnostics/build';shutil.rmtree(folder,ignore_errors=True);classes=folder/'classes';classes.mkdir(parents=True)
manifest=json.load(urllib.request.urlopen('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
version=json.load(urllib.request.urlopen(next(x['url'] for x in manifest['versions'] if x['id']=='1.21.1')))
artifacts=[lib['downloads']['artifact'] for lib in version['libraries'] if lib['name'].startswith('org.lwjgl:') and 'natives' not in lib['name']]
def dependency(a):
 p=ROOT/'test-runtime/libraries'/a['path'];p.parent.mkdir(parents=True,exist_ok=True)
 if not p.exists():urllib.request.urlretrieve(a['url'],p)
 return str(p)
with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:lwjgl=list(pool.map(dependency,artifacts))
mcp=(ROOT/'mapping-classpath.txt').read_text()+os.pathsep+str(ROOT)
# Compile against the exact API shipped in the user's pack, remapped to Mojang names.
api=ROOT/'toolchain/fabric-api-0.116.17+1.21.1.jar'
if not api.exists():urllib.request.urlretrieve('https://cdn.modrinth.com/data/P7dR8mSH/versions/Mys3P7lK/fabric-api-0.116.17%2B1.21.1.jar',api)
import hashlib,io
assert hashlib.sha512(api.read_bytes()).hexdigest()=='98c478217da19181f0e4df3ea6c2da6bdbda271c7544a4aea048513a49b444f1ff5b71dc0df6303017c48e8ff69b7bb6af6179bdf0d0e09ab3c1ee08411857a8'
api_raw=folder/'fabric-api-intermediary.jar'
with zipfile.ZipFile(api) as source,zipfile.ZipFile(api_raw,'w',zipfile.ZIP_DEFLATED) as z:
 for n in source.namelist():
  if n.startswith('META-INF/jars/') and any(token in n for token in ('api-base-','key-binding-api-v1-','lifecycle-events-v1-','command-api-v2-','rendering-v1-')):
   with zipfile.ZipFile(io.BytesIO(source.read(n))) as module:
    for entry in module.namelist():
     if entry.endswith('.class'):z.writestr(entry,module.read(entry))
mc_inter=folder/'minecraft-client-intermediary.jar'
subprocess.run([str(JAVA/'java'),'-Xmx2G','-cp',mcp,'MappingTool','remap',str(ROOT/'toolchain/client-1.21.1.jar'),str(mc_inter),str(ROOT/'intermediary.tiny'),'official','intermediary'],check=True)
api_official=folder/'fabric-api-official.jar';api_named=folder/'fabric-api-named.jar'
for a,b,m,src,dst,mc in [(api_raw,api_official,ROOT/'intermediary.tiny','intermediary','official',mc_inter),(api_official,api_named,ROOT/'client-mojmap.tiny','official','named',ROOT/'toolchain/client-1.21.1.jar')]:
 subprocess.run([str(JAVA/'java'),'-cp',mcp,'MappingTool','remap',str(a),str(b),str(m),src,dst,str(mc)],check=True)
cp=(ROOT/'compile-classpath.txt').read_text().replace(str(ROOT/'minecraft-named.jar'),str(ROOT/'minecraft-client-named.jar'))+os.pathsep+str(ROOT/'wwoo-fabric-1.21.1-3.0.1-backport.jar')+os.pathsep+str(api_named)+os.pathsep+os.pathsep.join(lwjgl)
subprocess.run([str(JAVA/'javac'),'-proc:none','--release','21','-cp',cp,'-d',str(classes),*[str(p) for p in sorted((ROOT/'diagnostics/src').rglob('*.java'))]],check=True)
raw=folder/'diagnostic-named.jar'
with zipfile.ZipFile(raw,'w',zipfile.ZIP_DEFLATED) as z:
 for p in sorted(classes.rglob('*.class')):z.write(p,p.relative_to(classes))
official=folder/'diagnostic-official.jar';inter=folder/'diagnostic-intermediary.jar'
for a,b,m,src,dst,mc in [(raw,official,ROOT/'client-mojmap.tiny','named','official',ROOT/'minecraft-client-named.jar'),(official,inter,ROOT/'intermediary.tiny','official','intermediary',ROOT/'toolchain/client-1.21.1.jar')]:
 subprocess.run([str(JAVA/'java'),'-cp',mcp,'MappingTool','remap',str(a),str(b),str(m),src,dst,str(mc)],check=True)
out=ROOT/'wwoo-cloud-diagnostics-fabric-1.21.1.jar'
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
 with zipfile.ZipFile(inter) as i:
  for n in i.namelist():z.writestr(n,i.read(n))
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'wwoo_cloud_diagnostics','version':'0.2.0-temp','name':'WWOO Cloud Diagnostics (temporary)','description':'Local GPU cloud alpha recorder. Configurable capture key and /wwoocloud capture; remove after testing.','environment':'client','entrypoints':{'client':['com.zachminecraft.wwoo.diagnostics.DiagnosticsClient']},'depends':{'fabricloader':'>=0.19.2','fabric-api':'*','minecraft':'~1.21.1','java':'>=21','wwoo':'=3.0.1+backport.1.21.1.15','cirrus':'1.4.1'},'mixins':['wwoo-cloud-diagnostics.mixins.json']}))
 z.writestr('wwoo-cloud-diagnostics.mixins.json',json.dumps({'required':True,'package':'com.zachminecraft.wwoo.diagnostics.mixin','compatibilityLevel':'JAVA_21','client':['CirrusDiagnosticMixin'],'injectors':{'defaultRequire':1}}))
 z.writestr('assets/wwoo_cloud_diagnostics/lang/en_us.json',json.dumps({'key.categories.wwoo_cloud_diagnostics':'WWOO Cloud Diagnostics','key.wwoo_cloud_diagnostics.capture':'Start/stop cloud capture'}))
 z.write(ROOT/'diagnostics/README.md','README-cloud-diagnostics.txt')
# Stable archive metadata makes identical code produce identical installable bytes.
with zipfile.ZipFile(out) as z:entries={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def package(destination):
 with zipfile.ZipFile(destination,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for n in sorted(entries):
   info=zipfile.ZipInfo(n,(1980,1,1,0,0,0));info.create_system=3;info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED
   z.writestr(info,entries[n],compresslevel=9)
package(out)
check=folder/'repeat-package.jar';package(check)
assert out.read_bytes()==check.read_bytes(),'Repeat packaging must be byte-identical'
check.unlink()
print('DIAGNOSTIC_PACKAGE_PASS: repeat packaging is byte-identical')
print('Built '+str(out))
