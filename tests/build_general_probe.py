"""Compile the read-only server test mod after port/bootstrap.py has run.

Usage: python3 tests/build_general_probe.py /path/to/fixture/general-probe.jar
The probe is a test dependency, not part of the released WWOO JAR.
"""
from pathlib import Path
import json,os,shutil,subprocess,sys,zipfile

root=Path(__file__).resolve().parent.parent
target=Path(sys.argv[1]).resolve()
jdk=Path(os.environ['WWOO_JAVA_HOME'])/'bin' if 'WWOO_JAVA_HOME' in os.environ else Path(shutil.which('javac')).resolve().parent
classes=root/'tests/probe-classes';classes.mkdir(exist_ok=True)
cp=(root/'compile-classpath.txt').read_text()+os.pathsep+str(next((root/'test-dependencies').glob('cristellib-*.jar')))
source=root/'tests/com/zachminecraft/wwoocontent/GeneralProbe.java'
subprocess.run([str(jdk/'javac'),'-proc:none','--release','21','-cp',cp,'-d',str(classes),str(source)],check=True)
raw=root/'tests/general-probe-named.jar'
with zipfile.ZipFile(raw,'w',zipfile.ZIP_DEFLATED) as z:
    for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
    z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'wwoo_general_probe','version':'1','environment':'server','entrypoints':{'main':['com.zachminecraft.wwoocontent.GeneralProbe']},'depends':{'wwoo':'*'}}))
official=root/'tests/general-probe-official.jar'
target.parent.mkdir(parents=True,exist_ok=True)
mcp=(root/'mapping-classpath.txt').read_text()+os.pathsep+str(root)
for a,b,m,src,dst,mc in [(raw,official,root/'mojmap.tiny','named','official',root/'minecraft-named.jar'),(official,target,root/'intermediary.tiny','official','intermediary',root/'test-runtime/versions/1.21.1/server-1.21.1.jar')]:
    subprocess.run([str(jdk/'java'),'-cp',mcp,'MappingTool','remap',str(a),str(b),str(m),src,dst,str(mc)],check=True)
print('GENERAL_PROBE_READY',target)
