from pathlib import Path
import subprocess,shutil,time,signal,sys
root=Path(__file__).resolve().parent.parent
folder=root/(sys.argv[1] if len(sys.argv)>1 else 'test-full-1');folder.mkdir(exist_ok=True)
mods=folder/'mods';mods.mkdir(exist_ok=True)
for p in (root/'test-dependencies').glob('*.jar'):shutil.copy2(p,mods/p.name)
shutil.copy2(root/'wwoo-fabric-1.21.1-3.0.1-backport.jar',mods/'wwoo.jar')
(folder/'eula.txt').write_text('eula=true\n')
(folder/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nview-distance=2\nsimulation-distance=2\nmax-tick-time=-1\nlevel-seed=1234567\nlevel-name=smoke\n')
biomes=[]
if len(sys.argv)>2 and sys.argv[2]=='all':
 import zipfile,json
 z=zipfile.ZipFile(root/'wwoo-fabric-1.21.1-3.0.1-backport.jar')
 biomes=sorted(Path(n).stem for n in z.namelist() if n.startswith('resources/wwoo_main/data/minecraft/worldgen/biome/') and n.endswith('.json'))
 pack=folder/'smoke/datapacks/validation';pack.mkdir(parents=True,exist_ok=True)
 (pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':'WWOO fixed-biome integration checks'}}))
 sys.path.insert(0,str(root/'tests'));import runtime_checks
 runtime_checks.install(pack)
 for biome in biomes:
  f=pack/'data/wwoo_test/dimension'/f'{biome}.json';f.parent.mkdir(parents=True,exist_ok=True)
  f.write_text(json.dumps({'type':'minecraft:overworld','generator':{'type':'minecraft:noise','settings':'minecraft:overworld','biome_source':{'type':'minecraft:fixed','biome':'minecraft:'+biome}}}))
log=folder/'console.log'
with log.open('w') as out:
 p=subprocess.Popen([str(root/'toolchain/jdk/bin/java'),'-Xmx3G','-Djava.security.egd=file:/dev/./urandom','-Dfabric.gameJarPath='+str(root/'test-runtime/versions/1.21.1/server-1.21.1.jar'),'-cp',(root/'test-runtime/launch-classpath.txt').read_text(),'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui'],cwd=folder,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True)
 try:
  start=time.monotonic();sent=False;last=0;dump=False;stop_at=None;stop_sent=False;late_checked=False
  while p.poll() is None:
   text=log.read_text(errors='replace');elapsed=time.monotonic()-start
   if len(text)!=last:
    print(text[last:][-1600:],flush=True);last=len(text)
   if 'Done (' in text and not sent:
    p.stdin.write('gamerule spawnChunkRadius 0\nlocate biome minecraft:dappled_forest\nlocate biome minecraft:pale_garden\n')
    if biomes:p.stdin.write('function wwoo_test:checks\n')
    for biome in biomes:p.stdin.write(f'execute in wwoo_test:{biome} run forceload add 0 0 15 15\n')
    p.stdin.write('execute in minecraft:overworld run forceload add -3264 -16 -3233 15\nexecute in minecraft:overworld run forceload add 416 -1200 447 -1169\nsay WWOO_GENERATION_COMMANDS_DONE\n')
    p.stdin.flush();sent=True
   if sent and not stop_sent and stop_at is None and 'WWOO_GENERATION_COMMANDS_DONE' in text:stop_at=elapsed+20
   if biomes and stop_at is not None and not late_checked and elapsed>stop_at-5:
    p.stdin.write('execute in minecraft:overworld run function wwoo_test:delayed_checks\n');p.stdin.flush();late_checked=True
   if stop_at is not None and elapsed>stop_at:
    p.stdin.write('save-all flush\nstop\n');p.stdin.flush();stop_at=None;stop_sent=True
   if elapsed>450 and not sent and not dump:
    p.send_signal(signal.SIGQUIT);dump=True;print('Captured startup thread dump',flush=True)
   if elapsed>1200:raise TimeoutError('Server startup or shutdown exceeded 1200 seconds')
   time.sleep(1)
  print('Exit',p.returncode,'started',sent,flush=True)
  if p.returncode!=0 or not sent:raise RuntimeError('WWOO integration server failed')
  if biomes:
   missing=[marker for marker in runtime_checks.MARKERS if 'WWOO_CHECK_'+marker+'_PASS' not in log.read_text()]
   if missing:raise RuntimeError('Runtime behavior checks failed: '+', '.join(missing))
 finally:
  if p.poll() is None:p.kill();p.wait()
