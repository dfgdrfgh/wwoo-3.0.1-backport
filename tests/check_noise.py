from pathlib import Path
import subprocess,os,zipfile,json
r=Path(__file__).resolve().parent.parent
patched=r/'toolchain/reference-compile.jar'
with zipfile.ZipFile(r/'toolchain/minecraft-26.3.jar') as src,zipfile.ZipFile(patched,'w',zipfile.ZIP_DEFLATED) as out:
 for n in src.namelist():
  b=src.read(n)
  if n.endswith('.class'):b=b[:6]+(65).to_bytes(2,'big')+b[8:]
  out.writestr(n,b)
libs=os.pathsep.join(str(p.resolve()) for p in (r/'toolchain/reference-libraries').rglob('*.jar'))
subprocess.run([str(r/'toolchain/jdk/bin/javac'),'-cp',str(patched)+os.pathsep+libs,'-d','tests/classes','tests/ReferenceNoise.java'],check=True,cwd=r)
subprocess.run([str(r/'toolchain/jre25/bin/java'),'-cp',str(r/'toolchain/minecraft-26.3.jar')+os.pathsep+libs+os.pathsep+'tests/classes','ReferenceNoise','toolchain/noise-configs.json','tests/reference-noise.json'],check=True,cwd=r)
cp=(r/'compile-classpath.txt').read_text()+os.pathsep+'port/classes'
subprocess.run([str(r/'toolchain/jdk/bin/javac'),'-proc:none','-cp',cp,'-d','tests/classes','tests/com/zachminecraft/wwoocontent/BackportNoise.java'],check=True,cwd=r)
subprocess.run([str(r/'toolchain/jdk/bin/java'),'-cp',cp+os.pathsep+'tests/classes','com.zachminecraft.wwoocontent.BackportNoise','toolchain/noise-configs.json','tests/backport-noise.json'],check=True,cwd=r)
ref=json.loads((r/'tests/reference-noise.json').read_text());out=json.loads((r/'tests/backport-noise.json').read_text());diff=[(a,i,x,y) for a,(rr,o) in enumerate(zip(ref,out)) for i,(x,y) in enumerate(zip(rr,o)) if x!=y]
print('Compared',sum(map(len,ref)),'float samples; differences:',len(diff));print(diff[:6]);(r/'tests/noise-result.json').write_text(json.dumps({'samples':sum(map(len,ref)),'bitwise_differences':len(diff)}))
