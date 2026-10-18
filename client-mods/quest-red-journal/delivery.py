"""Prepare/install the original-only Red Journal continuation with guarded backups."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import shutil
import subprocess
import zipfile
from xml.etree import ElementTree as ET
from prepare import ROOT, ASSETS, sha

SERVER = ROOT / 'target-deploy/game-server'
RESOURCES = ['data/handlers/quest/theobomos/_3999TheUnfinishedEntry.java',
             'data/static_data/quest_data/quest_data.xml',
             'data/static_data/quest_script_data/theobomos.xml']


def digest(path):
    return sha(path.read_bytes())


def check_prior(relative, payload, live):
    if relative == RESOURCES[0]:
        assert not live.exists(), 'Quest handler already installed'
    elif relative == RESOURCES[1]:
        current, previous = ET.fromstring(payload), ET.fromstring(live.read_bytes())
        assert previous.find("quest[@id='3999']") is None
        new = current.find("quest[@id='3999']")
        assert new is not None
        current.remove(new)
        assert ET.tostring(current) == ET.tostring(previous), 'Existing server quest data differs'
    else:
        without = re.sub(rb'(?m)^\s*3999 \[SCRIPT\][^\r\n]*\r?\n', b'', payload)
        assert without.strip() == live.read_bytes().strip(), 'Existing Theobomos script inventory differs'


def prepare(build, client_package, output, validation_path):
    assert not output.exists() and ROOT not in output.parents, 'Use fresh external staging'
    normal = json.loads((build/'build-manifest.json').read_text())
    cm = json.loads((client_package/'manifest.json').read_text())
    assert cm['feature'] == 'red-journal-continuation'
    validation = json.loads(validation_path.read_text())
    assert validation['isolatedPackagedQuestCompilation'] == 'passed' and validation['schema'] == 'passed'
    assert validation['compiledHandlerSha256'] == digest(ROOT/'game-server'/RESOURCES[0])
    assert validation['questDataSha256'] == digest(ROOT/'game-server'/RESOURCES[1])
    assert validation['dialogueSha256'] == digest(ASSETS/'dialog.html')
    assert validation['independentNativeDecoder'].startswith('AIONdisasm 1.6.5 passed')
    for name, expected in cm['sourceHashes'].items():
        assert digest(ROOT/name) == expected, 'Prepared source changed: '+name
    archive = build/'game-server/game-server.zip'
    assert digest(archive) == normal['artifacts']['game-server/game-server.zip']
    output.mkdir(parents=True)
    files = []
    with zipfile.ZipFile(archive) as z:
        for relative in RESOURCES:
            source = 'game-server/'+relative
            payload = z.read(source)
            assert sha(payload) == normal['inputs'][source] == digest(ROOT/source)
            live = SERVER/relative
            check_prior(relative, payload, live)
            staged = output/'server'/relative
            staged.parent.mkdir(parents=True, exist_ok=True)
            staged.write_bytes(payload)
            files.append(dict(destination=str(live), staged=str(staged), before=digest(live) if live.exists() else None,
                              after=sha(payload), backupRelative='server/'+relative))
    client = Path(cm['client'])
    inventory = json.loads((ROOT/'docs/INSTALLED_MODS.json').read_text())
    recorded = {entry['path'].lower(): entry['sha256'] for entry in inventory['clientFiles']}
    for entry in cm['files']:
        live = client/entry['path']
        staged = client_package/entry['path']
        assert digest(live) == entry['before'] and digest(staged) == entry['after']
        files.append(dict(destination=str(live), staged=str(staged), before=entry['before'], after=entry['after'],
                          backupRelative='client/'+entry['path'], inventoryBefore=recorded.get(entry['path'].lower())))
    manifest = dict(checkedAt=datetime.now(timezone.utc).isoformat(), feature=cm['feature'], questId=3999,
                    title=cm['title'], originalOnly=True, build=str(build), clientPackage=str(client_package),
                    files=files, sourceHashes=cm['sourceHashes'],
                    preservedGameServerJar=digest(SERVER/'libs/game-server-4.8-SNAPSHOT.jar'),
                    verification='Normal Maven resources; isolated quest compile; native encrypted dialogue and existing quest/string/compressed-member preservation',
                    gameplayVerified=False, installed=False)
    manifest['validation'] = str(validation_path)
    for name in ('client-mods/quest-red-journal/delivery.py','client-mods/quest-red-journal/README.md'):
        manifest['sourceHashes'][name] = digest(ROOT/name)
    (output/'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print('Prepared three exact builder resources and two checked client archives. No live files replaced.')


def guard_closed():
    query = "Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object { $_.Name -in @('aion.bin','aion.exe','java.exe','javaw.exe') } | Select-Object Name,ExecutablePath,CommandLine | ConvertTo-Json -Compress"
    result = subprocess.run(['powershell.exe','-NoProfile','-Command',query],capture_output=True,text=True,check=True)
    entries = json.loads(result.stdout) if result.stdout.strip() else []
    if isinstance(entries,dict):
        entries = [entries]
    for entry in entries:
        if entry['Name'].lower() in ('aion.bin','aion.exe'):
            raise RuntimeError('Client is running; close it normally before installation')
        command = entry.get('CommandLine')
        if not command or 'com.aionemu.gameserver.GameServer' in command:
            raise RuntimeError('GameServer is running or closure cannot be verified')
    return entries


def install(package, backup):
    assert not backup.exists() and ROOT not in backup.parents, 'Use fresh external backup'
    manifest = json.loads((package/'manifest.json').read_text())
    assert manifest['originalOnly'] and not manifest['installed']
    for name, expected in manifest['sourceHashes'].items():
        assert digest(ROOT/name) == expected, 'Prepared source changed: '+name
    for entry in manifest['files']:
        live, staged = Path(entry['destination']), Path(entry['staged'])
        assert digest(staged) == entry['after'], 'Staged file changed'
        assert (digest(live) if live.exists() else None) == entry['before'], 'Live file changed: '+str(live)
    assert digest(SERVER/'libs/game-server-4.8-SNAPSHOT.jar') == manifest['preservedGameServerJar'], 'Runtime changed; review and refresh preparation'
    manifest['processSnapshot'] = guard_closed()
    backup.mkdir(parents=True)
    # Back up the entire affected cohort before the first replacement.
    for entry in manifest['files']:
        if entry['before'] is not None:
            saved = backup/entry['backupRelative']
            saved.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(entry['destination'],saved)
            assert digest(saved) == entry['before']
            entry['backup'] = str(saved)
    (backup/'receipt.json').write_text(json.dumps(manifest,indent=2)+'\n')
    guard_closed()
    for entry in manifest['files']:
        live = Path(entry['destination'])
        assert (digest(live) if live.exists() else None) == entry['before']
        live.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(entry['staged'],live)
        entry['copiedSha256'] = digest(live)
        assert entry['copiedSha256'] == entry['after']
    assert digest(SERVER/'libs/game-server-4.8-SNAPSHOT.jar') == manifest['preservedGameServerJar']
    manifest.update(installed=True, installedAt=datetime.now(timezone.utc).isoformat(), componentsStarted=False,
                    repackChanged=False, sqlExecuted=False)
    receipt = backup/'receipt.json'
    receipt.write_text(json.dumps(manifest,indent=2)+'\n')
    inventory_path = ROOT/'docs/INSTALLED_MODS.json'
    inventory = json.loads(inventory_path.read_text())
    by_path = {entry['destination'].lower(): entry for entry in manifest['files']}
    client_root = json.loads((Path(manifest['clientPackage'])/'manifest.json').read_text())['client']
    for entry in inventory['clientFiles']:
        installed = by_path.get(str(Path(client_root)/entry['path']).lower())
        if installed:
            entry['sha256'] = installed['after']
    inventory['lastRedJournalContinuationDelivery'] = dict(checkedAt='2026-10-19', questId=3999, title=manifest['title'],
        receipt=str(receipt), build=manifest['build'], originalOnly=True, files=5,
        verification=manifest['verification'], gameplayAcceptance='User quest visibility, dialogue, kill credit, save/resume and one-time Rosina reward pending')
    for name in ('clientReceipts','serverReceipts'):
        if str(receipt) not in inventory[name]:
            inventory[name].append(str(receipt))
    inventory_path.write_text(json.dumps(inventory,indent=2)+'\n')
    print('Installed quest 3999 on the original client/GameServer; five copied hashes verified. Receipt: '+str(receipt))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest='mode',required=True)
    prep = sub.add_parser('prepare')
    for name in ('build','client-package','output','validation'):
        prep.add_argument('--'+name,type=Path,required=True)
    inst = sub.add_parser('install')
    inst.add_argument('--package',type=Path,required=True)
    inst.add_argument('--backup',type=Path,required=True)
    a = p.parse_args()
    if a.mode == 'prepare':
        prepare(a.build.resolve(),a.client_package.resolve(),a.output.resolve(),a.validation.resolve())
    else:
        install(a.package.resolve(),a.backup.resolve())
