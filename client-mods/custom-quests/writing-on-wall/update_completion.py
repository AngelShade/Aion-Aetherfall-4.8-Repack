"""Deliver native completion-dialogue fixes for the existing custom quest scripts.

Only exact normal assembly resources are installed. No core JAR, client resource,
quest progress or component lifecycle changes are performed.
"""
import argparse
from datetime import datetime, timezone
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
SERVER = ROOT / 'target-deploy/game-server'


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


resources = module('completion_resources', HERE / 'prepare.py')
sha, save, copy_file = resources.sha, resources.write_json, resources.copy_file


def closed():
    result = subprocess.run(['powershell.exe', '-NoProfile', '-Command', r"""
$ErrorActionPreference='Stop'
$all=@(Get-CimInstance Win32_Process -ErrorAction Stop)
[pscustomobject]@{checkedAt=[DateTime]::UtcNow.ToString('o');enumerationSucceeded=$true;affected=@($all | Where-Object {
 $_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -match 'com\.aionemu\.gameserver'
} | Select-Object ProcessId,Name)} | ConvertTo-Json -Compress
"""], capture_output=True, text=True, check=True)
    evidence = json.loads(result.stdout)
    assert evidence['enumerationSucceeded'] and not evidence['affected'], 'Close GameServer normally before installing its quest scripts'
    return evidence


def prepare(build, output):
    assert not output.exists() and not output.is_relative_to(ROOT)
    output.mkdir(parents=True)
    arc = json.loads((HERE / 'arc.json').read_text())
    files, live, sources = [], {}, {}
    build_manifest = json.loads((build / 'build-manifest.json').read_text())
    for name in ('generate.py', 'arc.json', 'bindings.json', 'update_completion.py'):
        path = HERE / name
        sources[path.relative_to(ROOT).as_posix()] = sha(path)
    with zipfile.ZipFile(build / 'game-server/game-server.zip') as assembly:
        for q in arc['quests']:
            relative = 'data/handlers/quest/verteron/_' + str(q['id']) + q['slug'] + '.java'
            source = ROOT / 'game-server' / relative
            key = source.relative_to(ROOT).as_posix()
            assert build_manifest['inputs'][key] == sha(source), 'Source changed after normal build: ' + key
            stage = output / 'server' / relative
            stage.parent.mkdir(parents=True, exist_ok=True)
            stage.write_bytes(assembly.read(key))
            assert sha(stage) == sha(source), 'Assembly differs from source: ' + key
            target = SERVER / relative
            files.append(dict(path=relative, original=sha(target), installed=sha(stage)))
            live[str(target)] = sha(target)
            sources[key] = sha(source)
    for name in ('libs/game-server-4.8-SNAPSHOT.jar', 'data/static_data/quest_data/quest_data.xml',
                 'data/static_data/items/item_templates.xml', 'data/handlers/ai/quests/QuestItemNpcAI.java'):
        live[str(SERVER / name)] = sha(SERVER / name)
    assert len(files) == 16
    save(output / 'manifest.json', dict(feature='the-unbroken-field-completion', originalOnly=True, build=str(build),
        files=files, questIds=[q['id'] for q in arc['quests']], sources=sources, liveInputs=live,
        coreChanged=False, serverJarChanged=False, clientChanged=False, installed=False, gameplayVerified=False))
    print('Prepared 16 exact normal assembly quest scripts; no original installation files changed.')


def install(stage, backup):
    manifest = json.loads((stage / 'manifest.json').read_text())
    assert manifest['feature'] == 'the-unbroken-field-completion' and not manifest['installed']
    assert not backup.exists() and not backup.is_relative_to(ROOT)
    for path, expected in manifest['sources'].items():
        assert sha(ROOT / path) == expected, 'Maintained source changed: ' + path
    for path, expected in manifest['liveInputs'].items():
        assert sha(Path(path)) == expected, 'Newer runtime resource detected: ' + path
    for entry in manifest['files']:
        assert entry['path'].startswith('data/handlers/quest/verteron/_149') and entry['path'].endswith('.java')
        assert sha(stage / 'server' / entry['path']) == entry['installed']
    closure = closed()
    backup.mkdir(parents=True)
    for entry in manifest['files']:
        copy_file(SERVER / entry['path'], backup / 'files' / entry['path'])
    targets = {str(SERVER / e['path']) for e in manifest['files']}
    preserved = {p: v for p, v in manifest['liveInputs'].items() if p not in targets}
    receipt = dict(feature=manifest['feature'], status='prepared', originalOnly=True, stage=str(stage),
        build=manifest['build'], questIds=manifest['questIds'], files=manifest['files'], closure=closure,
        preserved=preserved, sources=manifest['sources'], coreChanged=False, serverJarChanged=False,
        clientChanged=False, componentsStarted=False, gameplayVerified=False)
    save(backup / 'receipt.json', receipt)
    receipt['closureBeforeCopy'] = closed()
    copied = []
    try:
        for entry in manifest['files']:
            target = SERVER / entry['path']
            assert sha(target) == entry['original'], 'Target changed before copying'
            copy_file(stage / 'server' / entry['path'], target)
            copied.append(entry)
            cached = SERVER / 'cache/classes/quest/verteron' / (target.stem + '.class')
            # Native ScriptCompilerCache recompiles scripts newer than their
            # cached classes; let the normal next start refresh that cache.
            if cached.exists():
                assert target.stat().st_mtime_ns > cached.stat().st_mtime_ns, 'Source timestamp does not invalidate native cache'
        assert all(sha(SERVER / e['path']) == e['installed'] for e in copied)
        assert all(sha(Path(p)) == v for p, v in preserved.items())
    except Exception as error:
        receipt.update(status='failed', error=str(error))
        save(backup / 'receipt.json', receipt)
        closed()
        for entry in reversed(copied):
            assert sha(SERVER / entry['path']) == entry['installed'], 'Rollback stopped on newer file'
            copy_file(backup / 'files' / entry['path'], SERVER / entry['path'])
        receipt['status'] = 'rolled-back'
        save(backup / 'receipt.json', receipt)
        raise
    receipt.update(status='installed', installedAt=datetime.now(timezone.utc).isoformat(), copiedHashesVerified=len(copied),
                   nativeScriptCacheRefreshOnNextNormalStart=True)
    save(backup / 'receipt.json', receipt)
    inventory_path = ROOT / 'docs/INSTALLED_MODS.json'
    inventory = json.loads(inventory_path.read_text())
    inventory['lastUnbrokenFieldCompletionDelivery'] = dict(installedAt=receipt['installedAt'], receipt=str(backup / 'receipt.json'),
        stage=str(stage), build=manifest['build'], questIds=manifest['questIds'], originalOnly=True,
        copiedHashesVerified=len(copied), coreChanged=False, serverJarChanged=False, clientChanged=False,
        gameplayAcceptance='Owner: completion handoff and late/repeated reward confirmation at end NPC; no page 23 load failure')
    inventory['serverReceipts'].append(str(backup / 'receipt.json'))
    save(inventory_path, inventory)
    manifest.update(installed=True, receipt=str(backup / 'receipt.json'))
    save(stage / 'manifest.json', manifest)
    print('Installed 16 custom quest completion scripts; copied hashes and native cache freshness verified. Receipt: ' + str(backup / 'receipt.json'))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='mode', required=True)
    prep = sub.add_parser('prepare')
    for name in ('build', 'output'):
        prep.add_argument('--' + name, type=Path, required=True)
    deliver = sub.add_parser('install')
    for name in ('stage', 'backup'):
        deliver.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    if args.mode == 'prepare':
        prepare(args.build.resolve(strict=True), args.output.resolve())
    else:
        install(args.stage.resolve(strict=True), args.backup.resolve())
