"""Prepare pinned real-mod dependencies for the dedicated-server sizing audit."""
from pathlib import Path
import hashlib, json, urllib.request, zipfile, shutil, subprocess

root = Path(__file__).resolve().parent.parent
mods = root/'test-dependencies'
# The artifact is supplied by download-artifact in Actions (or nightly.link
# locally), pinned to the tested TrueVanillaBackport 1.21.1 build.
artifact = root/'test-runtime/vanillabackport-artifact'
if not any(artifact.rglob('*.jar')):
    # Actions artifacts expire. Rebuild the same public 1.21.1 source revision
    # if the pinned artifact is no longer available, rather than changing the
    # test dependency version or disabling integration checks.
    commit = '3fafeea8c11d10092d70959ba4c3cf8265e4b619'
    archive = root/'test-runtime/vanillabackport-source.zip'
    urllib.request.urlretrieve('https://github.com/dfgdrfgh/TrueVanillaBackport/archive/'+commit+'.zip', archive)
    source = root/'test-runtime/vanillabackport-source'
    source.mkdir(exist_ok=True)
    with zipfile.ZipFile(archive) as z: z.extractall(source)
    checkout = source/('TrueVanillaBackport-'+commit)
    subprocess.run(['bash','gradlew',':fabric:build','-x','test','--no-daemon'],cwd=checkout,check=True)
    artifact.mkdir(parents=True,exist_ok=True)
    for jar in (checkout/'fabric/build/libs').glob('*.jar'):
        if not any(label in jar.name for label in ('-sources','-dev','-javadoc')):
            shutil.copy2(jar,artifact/jar.name)
candidates = []
for p in artifact.rglob('*.jar'):
    with zipfile.ZipFile(p) as z:
        if 'fabric.mod.json' not in z.namelist(): continue
        meta = json.loads(z.read('fabric.mod.json'))
        if meta.get('id') == 'vanillabackport': candidates.append(p)
assert len(candidates) == 1, [str(p) for p in candidates]
shutil.copy2(candidates[0], mods/'vanillabackport-runtime.jar')
version = json.load(urllib.request.urlopen('https://api.modrinth.com/v2/version/Mys3P7lK'))
assert version['version_number'] == '0.116.17+1.21.1'
f = next(f for f in version['files'] if f['primary'])
data = urllib.request.urlopen(f['url']).read()
assert hashlib.sha512(data).hexdigest() == f['hashes']['sha512'], 'Fabric API checksum mismatch'
(mods/f['filename']).write_bytes(data)
print('Prepared runtime dependencies:', candidates[0].name, f['filename'])
