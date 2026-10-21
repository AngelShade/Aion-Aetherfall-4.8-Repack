"""Install a staged Aetherfall Boons client package while Aion is closed.

Copies the three staged archives, keeps the Graphics menu's data.pak record and package copy in
step (as the launcher does for Aetherfall Interface entries), lists the mod in the launcher's Mods
window and records the delivery. Backups go to a fresh external directory; any failure restores them.
"""
import argparse, hashlib, json, shutil, subprocess
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GRAPHICS_STATE, GRAPHICS_MANIFEST, GRAPHICS_PACKAGE = 'Mods/GraphicsMenu/installed.json', 'Mods/GraphicsMenu/package/manifest.json', 'Mods/GraphicsMenu/package'
MOD = 'Mods/AetherfallBoons/mod.json'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def closed():
    query = "@(Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object { $_.Name -in @('aion.bin','aion.exe') }).Count"
    result = subprocess.run(['powershell.exe', '-NoProfile', '-Command', query], capture_output=True, text=True, check=True)
    if result.stdout.strip() != '0':
        raise SystemExit('Aion is running; close every client normally before installing.')


def graphics_plan(client, files):
    state_path, manifest_path = client / GRAPHICS_STATE, client / GRAPHICS_MANIFEST
    if not state_path.exists():
        return None
    state = json.loads(state_path.read_text(encoding='utf-8-sig'))
    manifest = json.loads(manifest_path.read_text(encoding='utf-8-sig'))
    if state.get('restoredAt') or state['files'] != manifest['files']:
        raise SystemExit('Graphics menu records are inactive or disagree; resolve them first.')
    plan = []
    for entry in state['files']:
        mine = [f for f in files if f['path'].lower() == entry['path'].lower()]
        if not mine:
            continue
        f = mine[0]
        if entry['installed'].lower() != f['original'] or sha(client / GRAPHICS_PACKAGE / entry['path']) != f['original']:
            raise SystemExit('Graphics menu record for ' + entry['path'] + ' differs from the live archive; not changing it.')
        plan.append((entry['path'], f))
    return state, manifest, plan


def install(stage, client, backup):
    manifest = json.loads((stage / 'manifest.json').read_text(encoding='utf-8'))
    files = manifest['files']
    if backup.exists():
        raise SystemExit('Use a fresh backup directory')
    closed()
    for f in files:
        if sha(client / f['path']) != f['original']:
            raise SystemExit(f['path'] + ' changed since the package was built; rebuild it.')
        if sha(stage / 'files' / f['path']) != f['installed']:
            raise SystemExit('Staged ' + f['path'] + ' is damaged.')
    graphics = graphics_plan(client, files)
    touched = [f['path'] for f in files] + ([GRAPHICS_STATE, GRAPHICS_MANIFEST] + [GRAPHICS_PACKAGE + '/' + p for p, _ in graphics[2]] if graphics else [])
    for rel in touched:
        (backup / rel).parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(client / rel, backup / rel)
    done = []
    try:
        for f in files:
            closed()
            shutil.copyfile(stage / 'files' / f['path'], client / f['path'])
            done.append(f['path'])
            if sha(client / f['path']) != f['installed']:
                raise IOError('Copied hash differs: ' + f['path'])
        if graphics:
            state, gmanifest, plan = graphics
            for rel, f in plan:
                shutil.copyfile(stage / 'files' / f['path'], client / GRAPHICS_PACKAGE / rel)
                done.append(GRAPHICS_PACKAGE + '/' + rel)
                for record in (state['files'], gmanifest['files']):
                    next(e for e in record if e['path'] == rel)['installed'] = f['installed']
            state['rebasedAt'] = datetime.now().isoformat()
            for rel, value in ((GRAPHICS_MANIFEST, gmanifest), (GRAPHICS_STATE, state)):
                done.append(rel)
                (client / rel).write_text(json.dumps(value), encoding='utf-8')
    except BaseException:
        for rel in done:
            shutil.copy2(backup / rel, client / rel)
        raise
    mod = client / MOD
    mod.parent.mkdir(parents=True, exist_ok=True)
    mod.write_text(json.dumps({'Title': 'Aetherfall Boons', 'Listed': True,
                               'Description': 'Profile > Account shows your earned Aetherfall boons: Shugo Trade Charter, Grace of the Tower, '
                                              'Aetherfall Renown, Chronicle of Atreia and Daeva Veteran\'s Coffer.',
                               'Files': [f['path'] for f in files]}, indent=1), encoding='utf-8')
    receipt = dict(feature='aetherfall-boons', installedAt=datetime.now(timezone.utc).isoformat(), client=str(client), backup=str(backup),
                   files=files, graphicsMenuRebased=[p for p, _ in graphics[2]] if graphics else [])
    (backup / 'receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    inventory_path = ROOT / 'docs/INSTALLED_MODS.json'
    inventory = json.loads(inventory_path.read_text(encoding='utf-8-sig'))
    inventory['lastAetherfallBoonsDelivery'] = receipt
    inventory_path.write_text(json.dumps(inventory, indent=2) + '\n', encoding='utf-8')
    print('Installed', len(files), 'hash-verified archives; Graphics menu record follows data.pak. Receipt:', backup / 'receipt.json')


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--stage', required=True, type=Path)
    ap.add_argument('--client', required=True, type=Path)
    ap.add_argument('--backup', required=True, type=Path)
    a = ap.parse_args()
    install(a.stage.resolve(), a.client.resolve(), a.backup.resolve())
