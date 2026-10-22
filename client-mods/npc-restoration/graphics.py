"""Keep Graphics Menu ownership and a new restore baseline in step with the NPC English archive."""
import copy
from datetime import datetime, timezone
import json
from pathlib import Path
import runpy
from prepare import ROOT, read_pak, extend_archive, sha


def prepare_graphics(client, stage):
    path = stage / 'manifest.json'
    manifest = json.loads(path.read_text())
    state_path = client / 'Mods/GraphicsMenu/installed.json'
    if not state_path.exists():
        return
    state = json.loads(state_path.read_text(encoding='utf-8-sig'))
    assert not state.get('restoredAt'), 'Graphics Menu must have active ownership'
    package_path = client / 'Mods/GraphicsMenu/package/manifest.json'
    package = json.loads(package_path.read_text(encoding='utf-8-sig'))
    assert state['files'] == package['files']
    helper = runpy.run_path(str(ROOT / 'client-mods/transmog-menu/graphics_compat.py'))
    old_backup = helper['owned_renderer_backup'](client, state['backupRoot'])
    locale = next(e for e in state['files'] if e['path'].casefold() == 'l10n/enu/data/data.pak')
    active = next(e for e in manifest['files'] if e['path'].casefold() == locale['path'].casefold())
    assert locale['installed'] == active['before'] == sha((client / active['path']).read_bytes())
    assert sha((client / 'Mods/GraphicsMenu/package' / locale['path']).read_bytes()) == active['before']
    baseline = old_backup / locale['path']
    assert sha(baseline.read_bytes()) == locale['original']
    with read_pak(client / active['path']) as before, read_pak(stage / active['path']) as after, read_pak(baseline) as old:
        changes = {}
        for name in active['members']:
            assert old.read(name) == before.read(name), 'Restore baseline has a distinct owned record: ' + name
            changes[name] = after.read(name)
        new_baseline = extend_archive(baseline.read_bytes(), changes, {})
    new_root = 'Mods/Backups/Renderer/service-menu-graphics-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    assert not (client / new_root).exists()

    def stage_file(relative, data):
        target = stage / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        existing = client / relative
        manifest['files'].append({'path': relative, 'before': sha(existing.read_bytes()) if existing.exists() else None,
                                  'after': sha(data), 'graphicsCompatibility': True})

    for entry in state['files']:
        if entry['original'] is None:
            continue
        old = (old_backup / entry['path']).read_bytes()
        assert sha(old) == entry['original'], 'Existing Graphics restore baseline changed'
        data = new_baseline if entry is locale else old
        stage_file(new_root + '/' + entry['path'], data)
        if entry is locale:
            entry['original'] = sha(data)
            entry['installed'] = active['after']
    state['backupRoot'] = str(client / new_root)
    state['rebasedAt'] = datetime.now(timezone.utc).isoformat()
    package['files'] = copy.deepcopy(state['files'])
    stage_file('Mods/GraphicsMenu/package/' + locale['path'], (stage / active['path']).read_bytes())
    stage_file('Mods/GraphicsMenu/installed.json', (json.dumps(state, indent=2) + '\n').encode())
    stage_file('Mods/GraphicsMenu/package/manifest.json', (json.dumps(package, indent=2) + '\n').encode())
    manifest['graphicsCompatibility'] = {'previousBackup': str(old_backup), 'newBackup': str(client / new_root),
        'oldBaselinesPreserved': True, 'nativeExecutablesChanged': False}
    manifest['sourceHashes']['graphics.py'] = sha(Path(__file__).read_bytes())
    path.write_text(json.dumps(manifest, indent=2) + '\n')
    print('Prepared Graphics Menu package, ownership and fresh restore baseline; previous backups retained.')


if __name__ == '__main__':
    import argparse
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', type=Path, required=True)
    p.add_argument('--stage', type=Path, required=True)
    a = p.parse_args()
    prepare_graphics(a.client.resolve(strict=True), a.stage.resolve(strict=True))
