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
cp=(ROOT/'compile-classpath.txt').read_text().replace(str(ROOT/'minecraft-named.jar'),str(ROOT/'minecraft-client-named.jar'))+os.pathsep+str(ROOT/'wwoo-fabric-1.21.1-3.0.1-backport.jar')+os.pathsep+os.pathsep.join(lwjgl)
subprocess.run([str(JAVA/'javac'),'-proc:none','--release','21','-cp',cp,'-d',str(classes),*[str(p) for p in (ROOT/'diagnostics/src').rglob('*.java')]],check=True)
raw=folder/'diagnostic-named.jar'
with zipfile.ZipFile(raw,'w',zipfile.ZIP_DEFLATED) as z:
 for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
mcp=(ROOT/'mapping-classpath.txt').read_text()+os.pathsep+str(ROOT)
official=folder/'diagnostic-official.jar';inter=folder/'diagnostic-intermediary.jar'
for a,b,m,src,dst,mc in [(raw,official,ROOT/'client-mojmap.tiny','named','official',ROOT/'minecraft-client-named.jar'),(official,inter,ROOT/'intermediary.tiny','official','intermediary',ROOT/'toolchain/client-1.21.1.jar')]:
 subprocess.run([str(JAVA/'java'),'-cp',mcp,'MappingTool','remap',str(a),str(b),str(m),src,dst,str(mc)],check=True)
out=ROOT/'wwoo-cloud-diagnostics-fabric-1.21.1.jar'
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
 with zipfile.ZipFile(inter) as i:
  for n in i.namelist():z.writestr(n,i.read(n))
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'wwoo_cloud_diagnostics','version':'0.1.0-temp','name':'WWOO Cloud Diagnostics (temporary)','description':'Local GPU opacity and sky-contrast capture for Cirrus Fast/Fancy clouds. F8 starts/stops; remove after testing.','environment':'client','depends':{'fabricloader':'>=0.19.2','minecraft':'~1.21.1','java':'>=21','wwoo':'=3.0.1+backport.1.21.1.15','cirrus':'1.4.1'},'mixins':['wwoo-cloud-diagnostics.mixins.json']}))
 z.writestr('wwoo-cloud-diagnostics.mixins.json',json.dumps({'required':True,'package':'com.zachminecraft.wwoo.diagnostics.mixin','compatibilityLevel':'JAVA_21','client':['ClientDiagnosticMixin','GuiDiagnosticMixin','CirrusDiagnosticMixin'],'injectors':{'defaultRequire':1}}))
 z.write(ROOT/'diagnostics/README.md','README-cloud-diagnostics.txt')
print('Built '+str(out))
