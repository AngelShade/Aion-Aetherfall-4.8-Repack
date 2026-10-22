"""Ship active recovery files, not the original installation's development history."""
import copy
import json
from pathlib import Path


def texture_release_state(state):
    state = copy.deepcopy(state)
    state.pop('backupRoot', None)
    state.pop('graphicsFiles', None)
    state['rollbackAvailable'] = False
    state['repackBaseline'] = True
    return state


def active_recovery_files(package, staged=None):
    client = (package / 'Client').resolve()
    allowed = set()

    def current(path):
        candidate = staged / path.relative_to(package) if staged is not None else path
        return candidate if candidate.is_file() else path

    def include(path):
        path = Path(path).resolve()
        if not path.is_relative_to(client / 'Mods' / 'Backups'):
            raise ValueError('Active recovery path escapes client backups: ' + str(path))
        allowed.add(('Client/' + path.relative_to(client).as_posix()).casefold())

    for folder in (client / 'Mods').iterdir():
        if not folder.is_dir() or folder.name.casefold() in {'backups', 'textureupscale'}:
            continue
        paths = {p.name: p for p in folder.glob('installed*.json')}
        if staged is not None:
            incoming = staged / folder.relative_to(package)
            paths.update({p.name: folder / p.name for p in incoming.glob('installed*.json')})
        for path in paths.values():
            state = json.loads(current(path).read_text(encoding='utf-8-sig'))
            if state.get('restored') or state.get('rollbackAvailable') is False:
                continue
            backup = state.get('backupRoot')
            records = state.get('files', [])
            if backup and state.get('packageId') and not records:
                manifest = current(folder / 'packages' / state['packageId'] / 'manifest.json')
                if manifest.is_file():
                    records = json.loads(manifest.read_text(encoding='utf-8-sig')).get('files', [])
            if backup:
                for entry in records:
                    if entry.get('original'):
                        include(Path(backup) / entry['path'])
            for entry in state.get('nativeCursorPatch', {}).get('files', []):
                if entry.get('original') and entry.get('backupPath'):
                    include(entry['backupPath'])
    return allowed


def validate_recovery_payload(package, entries):
    recovery = [e['path'] for e in entries if e['path'].casefold().startswith('client/mods/backups/')]
    if not recovery:
        return
    allowed = active_recovery_files(package)
    unexpected = [path for path in recovery if path.casefold() not in allowed]
    if unexpected:
        raise ValueError('Development backup history cannot be distributed; keep it externally: ' + ', '.join(unexpected[:5]))
