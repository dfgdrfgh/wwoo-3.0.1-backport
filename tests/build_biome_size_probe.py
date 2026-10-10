"""Build the test-only biome-size probe after bootstrap and the mod build."""
from pathlib import Path
import json, os, shutil, subprocess, zipfile

root = Path(__file__).resolve().parent.parent
jdk = Path(os.environ['WWOO_JAVA_HOME']) / 'bin' if 'WWOO_JAVA_HOME' in os.environ else Path(shutil.which('javac')).resolve().parent
classes = root / 'tests/biome-size-probe-classes'
classes.mkdir(exist_ok=True)
cp = (root / 'compile-classpath.txt').read_text().replace(str(root/'minecraft-named.jar'),str(root/'minecraft-compile-widened.jar')) + os.pathsep + str(root / 'port/classes') + os.pathsep + str(next((root / 'test-dependencies').glob('cristellib-*.jar')))
subprocess.run([str(jdk/'javac'), '-proc:none', '--release', '21', '-cp', cp, '-d', str(classes), str(root/'tests/com/zachminecraft/wwoocontent/BiomeSizeProbe.java')], check=True)
raw = root / 'tests/biome-size-probe-named.jar'
with zipfile.ZipFile(raw, 'w', zipfile.ZIP_DEFLATED) as z:
    for p in classes.rglob('*.class'): z.write(p, p.relative_to(classes))
    z.writestr('fabric.mod.json', json.dumps({'schemaVersion':1, 'id':'wwoo_biome_size_probe', 'version':'1', 'environment':'server', 'entrypoints':{'main':['com.zachminecraft.wwoocontent.BiomeSizeProbe']}, 'depends':{'wwoo':'*'}}))
official = root / 'tests/biome-size-probe-official.jar'
target = root / 'tests/biome-size-probe.jar'
mcp = (root / 'mapping-classpath.txt').read_text() + os.pathsep + str(root)
for a,b,m,src,dst,mc in [(raw,official,root/'mojmap.tiny','named','official',root/'minecraft-named.jar'), (official,target,root/'intermediary.tiny','official','intermediary',root/'test-runtime/versions/1.21.1/server-1.21.1.jar')]:
    subprocess.run([str(jdk/'java'), '-cp', mcp, 'MappingTool', 'remap', str(a), str(b), str(m), src, dst, str(mc)], check=True)
