"""Check the installable jar's ice guards and approved config presentation."""
from pathlib import Path
import json
import sys
import zipfile

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'port'))
from preserve_ice import PROTECTED, REMOVAL_STATES, disk_configs, possible_matches, state_names

jar = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'wwoo-fabric-1.21.1-3.0.1-backport.jar'
checked = 0
with zipfile.ZipFile(jar) as z:
    assert z.testzip() is None
    for name in z.namelist():
        if not name.startswith('resources/wwoo_main/data/') or '/worldgen/' not in name or not name.endswith('.json'):
            continue
        for disk in disk_configs(json.loads(z.read(name))):
            if REMOVAL_STATES.intersection(state_names(disk['state_provider'])):
                for block in PROTECTED:
                    assert True not in possible_matches(disk['target'], block), (name, block)
                checked += 1
    # Retain both actual iceberg generators; preservation must not disable them.
    for ice in ('packed', 'blue'):
        name = f'resources/wwoo_main/data/wwoocontent/worldgen/placed_feature/ocean/frozen/iceberg_{ice}.json'
        assert f'wwoocontent:vanilla/iceberg_{ice}' in z.read(name).decode(), name
        name = f'resources/wwoo_main/data/wwoocontent/worldgen/configured_feature/vanilla/iceberg_{ice}.json'
        generator = json.loads(z.read(name))['config']['feature']
        assert generator['type'] == 'minecraft:iceberg', name
        block = 'minecraft:packed_ice' if ice == 'packed' else 'minecraft:blue_ice'
        assert generator['config']['state']['Name'] == block, name
    settings = json.loads(z.read('wwoo-content.client.mixins.json'))
    assert settings['client'].count('WWOOConfigScreenMixin') == 1
    mixin = z.read('com/zachminecraft/wwoocontent/mixin/WWOOConfigScreenMixin.class')
    assert b'Remove Underground Patches' in mixin
    assert b'removeOres' in mixin
    config = z.read('de/cristelknight/wwoo/config/WWOOConfig$1.class')
    assert b'Removes underground andesite, diorite, granite, gravel, and dirt patches. Requires a game restart.' in config
assert checked > 0
print(f'Validated {checked} removal-capable disk features, both iceberg generators, and config label/tooltip.')
