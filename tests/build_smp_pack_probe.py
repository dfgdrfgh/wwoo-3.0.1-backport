"""Compile the test-only loaded-modpack biome probe after bootstrap/build."""
from pathlib import Path
import subprocess,zipfile,json,os
repo=Path(__file__).resolve().parent.parent;dest=repo/'test-runtime/smp-pack-probe';dest.mkdir(parents=True,exist_ok=True)
jdk=Path(os.environ['WWOO_JAVA_HOME'])/'bin' if 'WWOO_JAVA_HOME' in os.environ else Path(__import__('shutil').which('javac')).resolve().parent;classes=dest/'classes';classes.mkdir(exist_ok=True)
cp=(repo/'compile-classpath.txt').read_text().replace(str(repo/'minecraft-named.jar'),str(repo/'minecraft-compile-widened.jar'))+':'+str(repo/'port/classes')+':'+str(next((repo/'test-dependencies').glob('cristellib-*.jar')))
subprocess.run([str(jdk/'javac'),'-proc:none','--release','21','-cp',cp,'-d',str(classes),str(repo/'tests/com/zachminecraft/wwoocontent/PackBiomeProbe.java')],check=True)
raw=dest/'named.jar'
with zipfile.ZipFile(raw,'w',zipfile.ZIP_DEFLATED) as z:
 for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'wwoo_smp_pack_probe','version':'1','environment':'server','entrypoints':{'main':['com.zachminecraft.wwoocontent.PackBiomeProbe']},'depends':{'wwoo':'*'}}))
mcp=(repo/'mapping-classpath.txt').read_text()+':'+str(repo)
for a,b,m,src,dst,mc in [(raw,dest/'official.jar',repo/'mojmap.tiny','named','official',repo/'minecraft-named.jar'),(dest/'official.jar',dest/'probe.jar',repo/'intermediary.tiny','official','intermediary',repo/'test-runtime/versions/1.21.1/server-1.21.1.jar')]:
 subprocess.run([str(jdk/'java'),'-cp',mcp,'MappingTool','remap',str(a),str(b),str(m),src,dst,str(mc)],check=True)
