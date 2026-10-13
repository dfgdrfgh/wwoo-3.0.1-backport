"""Check released cloud hooks in vanilla, Sodium and Cirrus, including actual shader pixels."""
from pathlib import Path
import concurrent.futures,hashlib,json,os,platform,shutil,subprocess,urllib.request,zipfile

ROOT=Path(__file__).resolve().parent.parent
JAVA=Path(os.environ['WWOO_JAVA_HOME'])/'bin' if 'WWOO_JAVA_HOME' in os.environ else Path(shutil.which('javac')).resolve().parent
JAR=ROOT/'wwoo-fabric-1.21.1-3.0.1-backport.jar'
folder=ROOT/'test-cloud-rendering';shutil.rmtree(folder,ignore_errors=True)
(folder/'mods').mkdir(parents=True)
with zipfile.ZipFile(JAR) as z:
    release_version=json.loads(z.read('fabric.mod.json'))['version']
    config=json.loads(z.read('wwoo-content.client.mixins.json'))
    assert 'CloudRendererMixin' in config['client']
    assert 'SodiumCloudRendererMixin' in config['client']
    assert 'CirrusCloudRendererMixin' in config['client']
    assert not {'CloudColorMixin','CloudRendererAccess'} & set(config['client'])
    helpers={n:z.read(n) for n in z.namelist() if n.startswith((
        'com/zachminecraft/wwoocontent/CloudBlend',
        'com/zachminecraft/wwoocontent/CloudAttributes',
        'com/zachminecraft/wwoocontent/mixin/CloudRendererMixin',
        'com/zachminecraft/wwoocontent/mixin/SodiumCloudRendererMixin',
        'com/zachminecraft/wwoocontent/mixin/CirrusCloudRendererMixin'))}
    attributes=z.read('data/wwoocontent/biome_attributes.json')
    biome_attributes=json.loads(attributes)
    opacity_palettes={}
    for biome in ['forest','pale_garden']:
        definition=json.loads(z.read('resources/wwoo_main/data/minecraft/worldgen/biome/'+biome+'.json'))
        cloud=biome_attributes.get('minecraft:'+biome,{}).get('attributes',{}).get('minecraft:visual/cloud_color','#ccffffff')
        opacity_palettes[biome]={'sky':definition['effects']['sky_color'],'cloud':int(cloud.removeprefix('#'),16)}
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
cp=os.pathsep.join([str(JAR),str(ROOT/'minecraft-client-named.jar'),str(ROOT/'diagnostics/build/diagnostic-named.jar'),
    (ROOT/'test-runtime/launch-classpath.txt').read_text(),*client_libraries])
subprocess.run([str(JAVA/'javac'),'-proc:none','--release','21','-cp',cp,'-d',str(classes),
    str(ROOT/'tests/com/zachminecraft/wwoocontent/CloudRenderProbe.java'),
    str(ROOT/'tests/com/zachminecraft/wwoocontent/CloudBlendCheck.java'),
    str(ROOT/'tests/com/zachminecraft/wwoocontent/SodiumCloudRenderProbe.java'),
    str(ROOT/'tests/com/zachminecraft/wwoocontent/CirrusCloudRenderProbe.java')],check=True)
raw=folder/'probe-named.jar'
with zipfile.ZipFile(raw,'w',zipfile.ZIP_DEFLATED) as z:
    for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
mcp=(ROOT/'mapping-classpath.txt').read_text()+os.pathsep+str(ROOT)
official=folder/'probe-official.jar';inter=folder/'probe-intermediary.jar'
for a,b,m,src,dst,mc in [
    (raw,official,ROOT/'client-mojmap.tiny','named','official',ROOT/'minecraft-client-named.jar'),
    (official,inter,ROOT/'intermediary.tiny','official','intermediary',ROOT/'toolchain/client-1.21.1.jar')]:
    subprocess.run([str(JAVA/'java'),'-cp',mcp,'MappingTool','remap',str(a),str(b),str(m),src,dst,str(mc)],check=True)
def fixture(entrypoint):
    with zipfile.ZipFile(folder/'mods/cloud-probe.jar','w',zipfile.ZIP_DEFLATED) as z:
        for n,v in helpers.items():z.writestr(n,v)
        with zipfile.ZipFile(inter) as p:
            for n in p.namelist():z.writestr(n,p.read(n))
        z.writestr('data/wwoocontent/biome_attributes.json',attributes)
        z.writestr('cloud-probe-palettes.json',json.dumps(opacity_palettes))
        z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'wwoo','version':release_version,
            'environment':'client','entrypoints':{'preLaunch':['com.zachminecraft.wwoocontent.'+entrypoint]},
            'mixins':['cloud-probe.mixins.json']}))
        z.writestr('cloud-probe.mixins.json',json.dumps({'required':True,
            'package':'com.zachminecraft.wwoocontent.mixin','compatibilityLevel':'JAVA_21',
            'client':['CloudRendererMixin','SodiumCloudRendererMixin','CirrusCloudRendererMixin'],'injectors':{'defaultRequire':1}}))
launch_cp=os.pathsep.join([(ROOT/'test-runtime/launch-classpath.txt').read_text(),
    str(ROOT/'toolchain/lib/intermediary.jar'),*client_libraries])
command=[str(JAVA/'java'),'-Xmx2G','-Dmixin.debug.export=true',
    '-Dfabric.gameJarPath='+str(ROOT/'toolchain/client-1.21.1.jar'),
    '-cp',launch_cp,'net.fabricmc.loader.impl.launch.knot.KnotClient']
def run(command,marker,name):
    result=subprocess.run(command,cwd=folder,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=180)
    (folder/(name+'.log')).write_text(result.stdout)
    print(result.stdout[-22000:])
    assert result.returncode==0 and marker in result.stdout,name+' cloud draw regression failed'
    if name=='vanilla':assert 'CLOUD_SKY_BLEND_PASS' in result.stdout
    if name=='cirrus':
        assert 'CIRRUS_OPACITY_COMPOSITE_PASS' in result.stdout
        assert 'CLOUD_DIAGNOSTIC_PASS' in result.stdout
fixture('CloudRenderProbe')
run(command,'CLOUD_RENDER_PASS','vanilla')

# Use the exact renderer versions and hashes from the user's supplied modpack.
sodium_versions=[
    ('sodium-fabric-0.8.13+mc1.21.1.jar','https://cdn.modrinth.com/data/AANobbMI/versions/SMxNOGZ6/sodium-fabric-0.8.13%2Bmc1.21.1.jar',
     '8614a374593698069a80ac74de8cd9c28f3928d16819a0e0d6320b538e823f7e54e8be06d5ea5a9c268beb0e02073209d1f831a02dede851588b972367680853'),
    ('sodium-extra-fabric-0.9.4+mc1.21.1.jar','https://cdn.modrinth.com/data/PtjYWJkn/versions/YEWu6red/sodium-extra-fabric-0.9.4%2Bmc1.21.1.jar',
     '824b06ddaa7ab697ff252ee47c6278725fc2ed6eeaaa027c32c0ee8bbb9ab416c23dfdd3c89a2ca7f4618cb106ef55694a6163cd09abc248219d14e9eca3d7d3')]
for name,url,sha in sodium_versions:
    path=folder/'mods'/name;urllib.request.urlretrieve(url,path)
    assert hashlib.sha512(path.read_bytes()).hexdigest()==sha,name+' modpack hash'
with zipfile.ZipFile(folder/'mods'/sodium_versions[0][0]) as z:
    for n in z.namelist():
        if n.startswith('assets/minecraft/shaders/') and not n.endswith('/'):
            p=folder/'shader-resources'/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(n))
with zipfile.ZipFile(ROOT/'toolchain/client-1.21.1.jar') as z:
    for n in z.namelist():
        if n.startswith('assets/minecraft/shaders/include/') and not n.endswith('/'):
            p=folder/'shader-resources'/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(n))
fixture('SodiumCloudRenderProbe')
run(['xvfb-run','-a',*command],'SODIUM_CLOUD_RENDER_PASS','sodium')

# Cirrus cancels the vanilla/Sodium draw and has a separate, layered cloud shader.
# Load the renderer-affecting mods and Cirrus settings from the user's updated pack.
cirrus_versions=[('BadOptimizations-2.4.1-1.21.1.jar', 'https://cdn.modrinth.com/data/g96Z4WVZ/versions/S2qthD5S/BadOptimizations-2.4.1-1.21.1.jar', '2b66428bd98bd5ec0e09f2ec6af801cefb7b9b99cc4f0993daeaffe8d0ccc5ee0c2a73e9f38d4f92540ead0181704d2c9e946837ec05cf710fc3b20fce3b0cac'), ('polytone-1.21-5.1.2-fabric.jar', 'https://cdn.modrinth.com/data/3qAYkBMB/versions/UAryir4p/polytone-1.21-5.1.2-fabric.jar', 'be4d802d98b88a44579e815267e1b22191d3cc804173ba9daa74a8dfb2f9ce628d918815e32734962f007f191bd615dd441e7a19f5b10f01a7ef7683a44ed0bc'), ('yet_another_config_lib_v3-3.8.2+1.21.1-fabric.jar', 'https://cdn.modrinth.com/data/1eAoo2KR/versions/o3cDn8Vp/yet_another_config_lib_v3-3.8.2%2B1.21.1-fabric.jar', '353169f85fa63e73ce9bdd0d4ddce54e3cbc4d71153a93c982f74c68fd93325986663f94fc2f2b10f4427e8b15a0485c608c5587b3840d933204b50ea4cb6d4c'), ('architectury-13.0.11-fabric.jar', 'https://cdn.modrinth.com/data/lhGA9TYQ/versions/Pzc2FP5K/architectury-13.0.11-fabric.jar', '53314eb34890b11ad3c06c6f3ba2d01ae1ca756235ce4f0574e4b0544952a2ab3c628c81b521653d6487d62c5dc3055e06fed6367a3a499bafad909512f0c716'), ('ImmediatelyFast-Fabric-1.6.14+1.21.1.jar', 'https://cdn.modrinth.com/data/5ZwdcRci/versions/7XKnsIC8/ImmediatelyFast-Fabric-1.6.14%2B1.21.1.jar', 'd3f2748ac2b51548157d0acf9d44e99ab786beab5322cbc452ad7999b187ead2a4ee6bb2aee1a179dc72dbb7bb2b3945b38c529acee454fe4b526648eefab0b4'), ('cirrus-fabric-1.21.1-1.4.1.jar', 'https://cdn.modrinth.com/data/SJDzFJH7/versions/lxmciEpD/cirrus-fabric-1.21.1-1.4.1.jar', '948c6ae6298fee3a6cdf75dd99373e4b552efc3d2715610769483dbbd8183bc4f56158fc810ccea22032e91f522b32be20b01bcc8a57f4c496e24e66f9070df5'), ('fabric-api-0.116.17+1.21.1.jar', 'https://cdn.modrinth.com/data/P7dR8mSH/versions/Mys3P7lK/fabric-api-0.116.17%2B1.21.1.jar', '98c478217da19181f0e4df3ea6c2da6bdbda271c7544a4aea048513a49b444f1ff5b71dc0df6303017c48e8ff69b7bb6af6179bdf0d0e09ab3c1ee08411857a8')]
for name,url,sha in cirrus_versions:
    path=folder/'mods'/name;urllib.request.urlretrieve(url,path)
    assert hashlib.sha512(path.read_bytes()).hexdigest()==sha,name+' modpack hash'
with zipfile.ZipFile(folder/'mods/cirrus-fabric-1.21.1-1.4.1.jar') as z:
    for n in z.namelist():
        if n.startswith('assets/cirrus/shaders/') and not n.endswith('/'):
            p=folder/'shader-resources'/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(n))
with zipfile.ZipFile(folder/'mods/polytone-1.21-5.1.2-fabric.jar') as z:
    for n in z.namelist():
        if n.startswith('assets/polytone/shaders/') and not n.endswith('/'):
            p=folder/'shader-resources'/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(n))
(folder/'config').mkdir(exist_ok=True)
shutil.copyfile(ROOT/'tests/fixtures/cirrus-client.json',folder/'config/cirrus-client.json')
# Match the pack's loader; Cirrus requires >=0.19.2. Leave the earlier optional-mod cases on 0.18.5.
profile=json.load(urllib.request.urlopen('https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.19.5/profile/json'))
loader_cp=[]
for lib in profile['libraries']:
    group,artifact,version=lib['name'].split(':')
    relative=group.replace('.','/')+'/'+artifact+'/'+version+'/'+artifact+'-'+version+'.jar'
    path=folder/'loader'/relative;path.parent.mkdir(parents=True,exist_ok=True)
    urllib.request.urlretrieve(lib['url']+relative,path)
    if 'sha512' in lib:assert hashlib.sha512(path.read_bytes()).hexdigest()==lib['sha512']
    loader_cp.append(str(path))
# Remove old copies of loader/Mixin/ASM before using the pack's complete loader runtime.
old_cp=[p for p in launch_cp.split(os.pathsep) if not any(token in p for token in
    ('fabric-loader.jar','sponge-mixin/','/org/ow2/asm/','/toolchain/lib/asm'))]
cirrus_command=[*command]
cirrus_command[cirrus_command.index('-cp')+1]=os.pathsep.join([*loader_cp,*old_cp])
shutil.copyfile(ROOT/'wwoo-cloud-diagnostics-fabric-1.21.1.jar',folder/'mods/wwoo-cloud-diagnostics-fabric-1.21.1.jar')
fixture('CirrusCloudRenderProbe')
run(['xvfb-run','-a',*cirrus_command],'CIRRUS_CLOUD_RENDER_PASS','cirrus')
