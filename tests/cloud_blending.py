"""Run timed biome-entry regression checks against the exact CloudBlend classes in the released mod."""
from pathlib import Path
import os,shutil,subprocess,tempfile,zipfile,json
ROOT=Path(__file__).resolve().parent.parent
JAR=ROOT/'wwoo-fabric-1.21.1-3.0.1-backport.jar'
JAVA=Path(os.environ['WWOO_JAVA_HOME'])/'bin' if 'WWOO_JAVA_HOME' in os.environ else Path(shutil.which('javac')).resolve().parent
with zipfile.ZipFile(JAR) as z:
    assert 'com/zachminecraft/wwoocontent/CloudBlend.class' in z.namelist()
    assert not any('CloudBlendCheck' in n for n in z.namelist())
    # Preserve encoded biome data; rendering uses RGB without animating its opacity.
    attributes=json.loads(z.read('data/wwoocontent/biome_attributes.json'))
    assert attributes['minecraft:pale_garden']['attributes']['minecraft:visual/cloud_color']=='#66828282'
    assert attributes['minecraft:desert']['attributes']['minecraft:visual/cloud_color']=='#11ffffff'
with tempfile.TemporaryDirectory(prefix='wwoo-cloud-check-') as folder:
    source=ROOT/'tests/com/zachminecraft/wwoocontent/CloudBlendCheck.java'
    subprocess.run([str(JAVA/'javac'),'-proc:none','--release','21','-cp',str(JAR),'-d',folder,str(source)],check=True)
    palettes=['ccffffff']+[entry['attributes']['minecraft:visual/cloud_color'].removeprefix('#')
        for entry in attributes.values() if 'minecraft:visual/cloud_color' in entry['attributes']]
    subprocess.run([str(JAVA/'java'),'-cp',folder+os.pathsep+str(JAR),'com.zachminecraft.wwoocontent.CloudBlendCheck',*palettes],check=True)
