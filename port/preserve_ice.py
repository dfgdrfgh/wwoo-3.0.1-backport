"""Protect existing packed/blue ice from disk-based carving."""
import json

PROTECTED = {'minecraft:packed_ice', 'minecraft:blue_ice'}
REMOVAL_STATES = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air', 'minecraft:water', 'minecraft:light'}


def state_names(value):
    if isinstance(value, dict):
        if 'Name' in value:
            name = value['Name']
            yield name if ':' in name else 'minecraft:' + name
        for child in value.values():
            yield from state_names(child)
    elif isinstance(value, list):
        for child in value:
            yield from state_names(child)


def possible_matches(predicate, current_block):
    """Evaluate current-position block tests; keep surroundings/biomes unknown."""
    kind = predicate.get('type')
    if kind == 'minecraft:matching_blocks' and predicate.get('offset', [0, 0, 0]) == [0, 0, 0]:
        blocks = predicate['blocks']
        if isinstance(blocks, str):
            blocks = [blocks]
        if any(b.startswith('#') for b in blocks):
            return {True, False}
        return {current_block in {b if ':' in b else 'minecraft:' + b for b in blocks}}
    if kind in ('minecraft:all_of', 'minecraft:any_of'):
        results = [possible_matches(p, current_block) for p in predicate['predicates']]
        if kind == 'minecraft:all_of':
            return ({True} if all(True in r for r in results) else set()) | ({False} if any(False in r for r in results) else set())
        return ({True} if any(True in r for r in results) else set()) | ({False} if all(False in r for r in results) else set())
    if kind == 'minecraft:not':
        return {not v for v in possible_matches(predicate['predicate'], current_block)}
    if kind == 'minecraft:true':
        return {True}
    return {True, False}


def protect_disks(value):
    changed = 0
    if isinstance(value, dict):
        if value.get('type') == 'minecraft:disk':
            config = value.get('config', value)
            target = config['target']
            if REMOVAL_STATES.intersection(state_names(config['state_provider'])) and any(True in possible_matches(target, block) for block in PROTECTED):
                config['target'] = {
                    'type': 'minecraft:all_of',
                    'predicates': [target, {
                        'type': 'minecraft:not',
                        'predicate': {'type': 'minecraft:matching_blocks', 'blocks': sorted(PROTECTED)},
                    }],
                }
                changed += 1
        for child in value.values():
            changed += protect_disks(child)
    elif isinstance(value, list):
        for child in value:
            changed += protect_disks(child)
    return changed


def patch_entries(entries):
    changes = {}
    for name, raw in list(entries.items()):
        if not name.startswith('resources/wwoo_main/data/') or '/worldgen/' not in name or not name.endswith('.json'):
            continue
        value = json.loads(raw)
        count = protect_disks(value)
        if count:
            entries[name] = (json.dumps(value, indent=2) + '\n').encode()
            changes[name] = count
    return changes


def disk_configs(value):
    if isinstance(value, dict):
        if value.get('type') == 'minecraft:disk':
            yield value.get('config', value)
        for child in value.values():
            yield from disk_configs(child)
    elif isinstance(value, list):
        for child in value:
            yield from disk_configs(child)


