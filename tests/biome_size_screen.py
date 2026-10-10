"""Exercise screen fields/save/reset against the real Cloth Config builder API.

Window-backed entries are replaced with capture-only builders; this verifies
screen logic, not rendering. Run after bootstrap and the mod build.
"""
from pathlib import Path
import os, shutil, subprocess, zipfile

root = Path(__file__).resolve().parent.parent
jdk = Path(os.environ['WWOO_JAVA_HOME'])/'bin' if 'WWOO_JAVA_HOME' in os.environ else Path(shutil.which('javac')).resolve().parent
classes = root/'tests/biome-size-screen-classes'; classes.mkdir(exist_ok=True)
cp = (root/'compile-classpath.txt').read_text().replace(str(root/'minecraft-named.jar'),str(root/'minecraft-compile-widened.jar'))
cp += os.pathsep+str(root/'port/classes')+os.pathsep+str(next((root/'test-dependencies').glob('cristellib-*.jar')))
with zipfile.ZipFile(root/'toolchain/client-api/cloth-config.jar') as jar:
    for name in jar.namelist():
        if name.startswith('META-INF/jars/') and name.endswith('.jar'):
            target=classes/Path(name).name; target.write_bytes(jar.read(name)); cp+=os.pathsep+str(target)
source = root/'tests/com/zachminecraft/wwoocontent/BiomeSizeScreenProbe.java'
subprocess.run([str(jdk/'javac'),'-proc:none','--release','21','-cp',cp,'-d',str(classes),str(source)],check=True)
subprocess.run([str(jdk/'java'),'-cp',cp+os.pathsep+str(classes),'com.zachminecraft.wwoocontent.BiomeSizeScreenProbe'],check=True)
