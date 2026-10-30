"""Install a prepare_wardrobe_dyes.py package while Aion is closed.

Backs up every replaced file to a fresh directory, copies and hash-checks the staged files,
keeps the Graphics menu's data.pak record and package copy in step, verifies addon signatures
and preserved files, and restores the backup on any failure.
"""
import argparse, hashlib, json, shutil, subprocess
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
GRAPHICS_STATE, GRAPHICS_MANIFEST, GRAPHICS_PACKAGE = 'Mods/GraphicsMenu/installed.json', 'Mods/GraphicsMenu/package/manifest.json', 'Mods/GraphicsMenu/package'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def closed():
    query = "@(Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object { $_.Name -in @('aion.bin','aion.exe') }).Count"
    result = subprocess.run(['powershell.exe', '-NoProfile', '-Command', query], capture_output=True, text=True, check=True)
    if result.stdout.strip() != '0':
        raise SystemExit('Aion is running; close every client normally before installing.')


def graphics_plan(client, files):
    if not (client / GRAPHICS_STATE).exists():
        return None
    state = json.loads((client / GRAPHICS_STATE).read_text(encoding='utf-8-sig'))
    manifest = json.loads((client / GRAPHICS_MANIFEST).read_text(encoding='utf-8-sig'))
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


def install(package, client, backup):
    manifest = json.loads((package / 'manifest.json').read_text(encoding='utf-8'))
    files = manifest['files']
    if backup.exists():
        raise SystemExit('Use a fresh backup directory')
    closed()
    for f in files:
        if sha(client / f['path']) != f['original']:
            raise SystemExit(f['path'] + ' changed since the package was built; rebuild it.')
        if sha(package / f['path']) != f['staged']:
            raise SystemExit('Staged ' + f['path'] + ' is damaged.')
    graphics = graphics_plan(client, files)
    touched = [f['path'] for f in files] + ([GRAPHICS_STATE, GRAPHICS_MANIFEST] + [GRAPHICS_PACKAGE + '/' + p for p, _ in graphics[2]] if graphics else [])
    for rel in touched:
        (backup / rel).parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(client / rel, backup / rel)
    shutil.copy2(package / 'manifest.json', backup / 'manifest.json')
    done = []
    try:
        for f in files:
            closed()
            shutil.copyfile(package / f['path'], client / f['path'])
            done.append(f['path'])
            if sha(client / f['path']) != f['staged']:
                raise IOError('Copied hash differs: ' + f['path'])
        if graphics:
            state, gmanifest, plan = graphics
            for rel, f in plan:
                shutil.copyfile(package / f['path'], client / GRAPHICS_PACKAGE / rel)
                done.append(GRAPHICS_PACKAGE + '/' + rel)
                for record in (state['files'], gmanifest['files']):
                    next(e for e in record if e['path'] == rel)['installed'] = f['staged']
            state['rebasedAt'] = datetime.now().isoformat()
            for rel, value in ((GRAPHICS_MANIFEST, gmanifest), (GRAPHICS_STATE, state)):
                done.append(rel)
                (client / rel).write_text(json.dumps(value), encoding='utf-8')
        subprocess.run(['java', str(ROOT / 'client-mods/season-pass/VerifySignatures.java'), str(client), str(client)], check=True)
        subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
                        str(client / 'Mods/GraphicsMenu/Install-GraphicsMenu.ps1'), '-ClientPath', str(client), '-VerifyOnly'], check=True)
        rebased = {rel.lower() for rel in touched[len(files):]}
        for entry in manifest['preservedFiles']:
            if entry['path'].lower() not in rebased and sha(client / entry['path']) != entry['sha256']:
                raise IOError('Preserved client file changed: ' + entry['path'])
    except BaseException:
        for rel in done:
            shutil.copy2(backup / rel, client / rel)
        raise
    receipt = dict(feature=manifest['feature'], revision=manifest['revision'], installedAt=datetime.now(timezone.utc).isoformat(),
                   client=str(client), backup=str(backup), files=files, graphicsMenuRebased=[p for p, _ in graphics[2]] if graphics else [])
    (backup / 'receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    inventory_path = ROOT / 'docs/INSTALLED_MODS.json'
    inventory = json.loads(inventory_path.read_text(encoding='utf-8-sig'))
    staged = {f['path'].lower(): f['staged'] for f in files}
    staged.update({(GRAPHICS_PACKAGE + '/' + p).lower(): f['staged'] for p, f in (graphics[2] if graphics else [])})
    for entry in inventory['clientFiles']:
        if entry['path'].lower() in staged:
            entry['sha256'] = staged[entry['path'].lower()]
    inventory['lastWardrobeDyesDelivery'] = receipt
    inventory_path.write_text(json.dumps(inventory, indent=2) + '\n', encoding='utf-8')
    print('Installed', len(files), 'hash-verified files; signatures, Graphics menu and preserved files verified. Receipt:', backup / 'receipt.json')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', type=Path, required=True)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--backup', type=Path, required=True)
    args = parser.parse_args()
    install(args.package.resolve(), args.client.resolve(), args.backup.resolve())
