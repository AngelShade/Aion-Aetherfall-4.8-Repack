"""Prepare/install native quest text and tracking through the existing loader."""
import argparse
import copy
from datetime import datetime, timezone
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
from xml.etree import ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


resources = module('unbroken_field_resources', HERE / 'prepare.py')
sha, save, copy_file = resources.sha, resources.write_json, resources.copy_file
ENGLISH = resources.ARCHIVES[1]


def prepare(client, output, tracking=False):
    assert not output.exists() and not output.is_relative_to(client) and not output.is_relative_to(ROOT)
    output.mkdir(parents=True)
    arc = json.loads((HERE / 'arc.json').read_text())
    quests = arc['quests']
    changes = {}
    for q in quests:
        folder = str(q['id']) + '-' + q['slug']
        names = ('dialog.html', 'quest_script_monster.csv') if tracking else ('dialog.html',)
        for name in names:
            source = HERE.parent / 'Quests' / folder / name
            installed = client / 'Mods/CustomQuests/Quests' / folder / name
            if not tracking or sha(source) != sha(installed):
                changes.setdefault(folder, []).append(name)
    if tracking:
        quests = [q for q in quests if str(q['id']) + '-' + q['slug'] in changes]
        assert quests, 'No tracking changes to deliver'
    folders = list(changes)
    members = {f"dialogs/quest_q{q['id']}.html" for q in quests
               if 'dialog.html' in changes[str(q['id']) + '-' + q['slug']]}
    expected_ownership = {('d:' if name == 'dialog.html' else 'c:') + folder.split('-')[0]
                          for folder, names in changes.items() for name in names}
    graphics = json.loads((client / 'Mods/GraphicsMenu/installed.json').read_text())
    package = json.loads((client / 'Mods/GraphicsMenu/package/manifest.json').read_text())
    assert graphics['files'] == package['files'] and not graphics.get('restoredAt')
    restore = resources.owned_renderer_backup(client, graphics['backupRoot'])
    locale = next(e for e in graphics['files'] if e['path'].casefold() == ENGLISH.casefold())
    assert sha(client / ENGLISH) == sha(client / 'Mods/GraphicsMenu/package' / ENGLISH) == locale['installed']
    assert sha(restore / ENGLISH) == locale['original']
    captured, writes, sources = {}, [], {}

    def watch(path):
        captured[str(path)] = sha(path)

    for name in [*resources.ARCHIVES, 'Aetherfall Launcher.exe', 'Mods/CustomQuests/installed.json',
                 'Mods/CustomQuests/settings.json', 'Mods/GraphicsMenu/installed.json',
                 'Mods/GraphicsMenu/package/manifest.json', 'Mods/GraphicsMenu/package/' + ENGLISH,
                 'Mods/InstalledMods.json', 'bin64/Game.dll', 'bin64/AetherfallCore.dll',
                 'Data/ui/game/game.pak', 'Plugin/RelicCalc/RelicCalc.pak',
                 'Mods/Launcher/connection.json', 'Mods/Launcher/preferences.json',
                 'Data/Items/Items.pak', 'bin64/AionIconBridge.index']:
        if (client / name).exists():
            watch(client / name)
    watch(restore / ENGLISH)
    watch(ROOT / 'target-deploy/game-server/libs/game-server-4.8-SNAPSHOT.jar')
    watch(ROOT / 'target-deploy/game-server/data/static_data/quest_data/quest_data.xml')
    if tracking:
        watch(ROOT / 'target-deploy/game-server/data/static_data/items/item_templates.xml')
        for q in arc['quests']:
            watch(ROOT / 'target-deploy/game-server/data/handlers/quest/verteron' / ('_' + str(q['id']) + q['slug'] + '.java'))
    for source in [HERE / 'generate.py', HERE / 'arc.json', HERE / 'bindings.json', Path(__file__)]:
        sources[source.relative_to(ROOT).as_posix()] = sha(source)
    original_ledger = json.loads((client / 'Mods/CustomQuests/installed.json').read_text())
    results = {}
    for kind in ('active', 'restore'):
        fixture = output / kind
        for relative in resources.ARCHIVES:
            source = restore / relative if kind == 'restore' and relative == ENGLISH else client / relative
            copy_file(source, fixture / relative)
        shutil.copytree(client / 'Mods/CustomQuests', fixture / 'Mods/CustomQuests')
        for folder in folders:
            for name in changes[folder]:
                source = HERE.parent / 'Quests' / folder / name
                installed = client / 'Mods/CustomQuests/Quests' / folder / name
                watch(installed)
                sources[source.relative_to(ROOT).as_posix()] = sha(source)
                copy_file(source, fixture / 'Mods/CustomQuests/Quests' / folder / name)
        save(fixture / 'Mods/CustomQuests/settings.json', dict(BackupRoot=str(output / 'loader-backups')))
        report = output / (kind + '-loader.txt')
        loaded = subprocess.run([str(client / 'Aetherfall Launcher.exe'), '--custom-quests-sync', '--client', str(fixture), '--report', str(report)], capture_output=True, text=True)
        text = report.read_text() if report.exists() else loaded.stderr
        assert loaded.returncode == 0, text
        if tracking:
            before = client / resources.ARCHIVES[0]
            after = fixture / resources.ARCHIVES[0]
            old_quest, _, _, _ = resources.parts(before.read_bytes())
            new_quest, _, _, _ = resources.parts(after.read_bytes())
            assert list(old_quest) == list(new_quest)
            assert all(old_quest[n][0] == new_quest[n][0] for n in old_quest if n != 'quest_script_monster.csv')
            with resources.read_pak(before) as a, resources.read_pak(after) as b:
                original = a.read('quest_script_monster.csv').decode('utf-8-sig').splitlines(keepends=True)
                current = b.read('quest_script_monster.csv').decode('utf-8-sig').splitlines(keepends=True)
                ids = {folder.split('-')[0] for folder in folders if 'quest_script_monster.csv' in changes[folder]}
                assert [l for l in original if l.split(',')[0] not in ids] == [l for l in current if l.split(',')[0] not in ids]
                for folder in folders:
                    if 'quest_script_monster.csv' in changes[folder]:
                        wanted = (HERE.parent / 'Quests' / folder / 'quest_script_monster.csv').read_bytes().decode('utf-8')
                        assert ''.join(l for l in current if l.startswith(folder.split('-')[0] + ',')) == wanted
        else:
            assert sha(fixture / resources.ARCHIVES[0]) == captured[str(client / resources.ARCHIVES[0])], 'Quest metadata archive changed'
        old_path = (restore if kind == 'restore' else client) / ENGLISH
        old_parts, _, _, _ = resources.parts(old_path.read_bytes())
        new_parts, _, _, _ = resources.parts((fixture / ENGLISH).read_bytes())
        assert list(old_parts) == list(new_parts), 'Archive member order/set changed'
        assert all(old_parts[name][0] == new_parts[name][0] for name in old_parts if name not in members)
        with resources.read_pak(old_path) as old, resources.read_pak(fixture / ENGLISH) as new:
            changed = {name for name in members if old.read(name) != new.read(name)}
            assert changed == members, 'Loader did not apply every dialogue update'
            objective_count = 0
            for q in quests:
                name = f"dialogs/quest_q{q['id']}.html"
                if name not in members:
                    continue
                old_xml = ET.fromstring(resources.journal.decode_html(old.read(name), Path(name).name))
                text = resources.journal.decode_html(new.read(name), Path(name).name)
                authored = ET.parse(HERE.parent / 'Quests' / (str(q['id']) + '-' + q['slug']) / 'dialog.html').getroot()
                actual = ET.fromstring(text)
                assert ET.tostring(actual) == ET.tostring(authored)
                comparable = copy.deepcopy(actual)
                if tracking:
                    # Only the live hunt counter changes. Collection markup,
                    # dialogue, actions, story and other objective text stay intact.
                    for index, step in enumerate(q['steps']):
                        if step['type'] == 'hunt':
                            paragraph = comparable.findall("HtmlPage[@name='quest_summary']//steps/step/p")[index]
                            counter = '([%' + str(index * 3 + 2) + ']/' + str(step['count']) + ')'
                            font = paragraph.find('font')
                            assert counter in font.text
                            fixed = '(' + str(step['count']) + ')'
                            if fixed in step['objective']:
                                font.text = font.text.replace(counter, fixed)
                            else:
                                font.text = font.text.replace(' ' + counter + '.', '.')
                else:
                    for paragraph in comparable.findall("HtmlPage[@name='quest_summary']//steps/step/p"):
                        assert paragraph.attrib.pop('align') == 'left'
                assert resources.normalize(comparable) == resources.normalize(old_xml)
                for paragraph in actual.findall("HtmlPage[@name='quest_summary']//steps/step/p"):
                    assert paragraph.text is None and paragraph.find('font').tail is None
                    objective_count += 1
        ledger = json.loads((fixture / 'Mods/CustomQuests/installed.json').read_text())
        changed_ownership = {k for k in original_ledger['Owned'] if original_ledger['Owned'][k] != ledger['Owned'][k]}
        assert changed_ownership == expected_ownership
        results[kind] = dict(loader=report.read_text().strip(), changedDialogues=len(changed),
                             compactObjectiveParagraphs=objective_count, preservedCompressedMembers=len(old_parts) - len(members),
                             narrativeAndActionsPreserved=True, questMetadataAndRegistrationsPreserved=True,
                             changedOwnership=sorted(changed_ownership), otherGuidanceRowsPreserved=True)

    def payload(relative, source=None, data=None):
        stage = output / 'client' / relative
        stage.parent.mkdir(parents=True, exist_ok=True)
        if source is not None:
            copy_file(source, stage)
        else:
            stage.write_bytes(data)
        target = client / relative
        assert target.exists()
        watch(target)
        writes.append(dict(path=relative, original=sha(target), installed=sha(stage)))

    for folder in folders:
        for name in changes[folder]:
            payload('Mods/CustomQuests/Quests/' + folder + '/' + name, source=HERE.parent / 'Quests' / folder / name)
    if tracking:
        payload(resources.ARCHIVES[0], source=output / 'active' / resources.ARCHIVES[0])
    payload(ENGLISH, source=output / 'active' / ENGLISH)
    payload('Mods/GraphicsMenu/package/' + ENGLISH, source=output / 'active' / ENGLISH)
    payload(restore.relative_to(client).as_posix() + '/' + ENGLISH, source=output / 'restore' / ENGLISH)
    payload('Mods/CustomQuests/installed.json', source=output / 'active/Mods/CustomQuests/installed.json')
    for relative in ('Mods/GraphicsMenu/installed.json', 'Mods/GraphicsMenu/package/manifest.json'):
        data = json.loads((client / relative).read_text())
        entry = next(e for e in data['files'] if e['path'].casefold() == ENGLISH.casefold())
        entry.update(installed=sha(output / 'active' / ENGLISH), original=sha(output / 'restore' / ENGLISH))
        payload(relative, data=(json.dumps(data, indent=2) + '\n').encode())
    mods = json.loads((client / 'Mods/InstalledMods.json').read_text())
    by_name = {entry['path'].casefold(): entry for entry in writes}
    for entries in mods['features'].values():
        for entry in entries:
            updated = by_name.get(entry['path'].casefold())
            if updated:
                entry.update(installedSha256=updated['installed'], bytes=(output / 'client' / updated['path']).stat().st_size)
    payload('Mods/InstalledMods.json', data=(json.dumps(mods, indent=2) + '\n').encode())
    targets = {str(client / record['path']).casefold() for record in writes}
    preserved = {name: expected for name, expected in captured.items() if name.casefold() not in targets}
    assert all(sha(Path(name)) == expected for name, expected in captured.items()), 'Installed baseline changed during preparation'
    assert all(sha(ROOT / name) == expected for name, expected in sources.items()), 'Authoring inputs changed during preparation'
    save(output / 'manifest.json', dict(feature='the-unbroken-field-tracking' if tracking else 'the-unbroken-field-dialogues', originalOnly=True, client=str(client),
                                      questIds=[q['id'] for q in quests], files=writes, sources=sources,
                                      liveInputs=captured, preserved=preserved, validation=results,
                                      installed=False, gameplayVerified=False, serverChanged=False, coreChanged=False))
    print('Prepared ' + ('quest tracking/counters' if tracking else 'dialogue formatting') + ': ' + str(len(writes))
          + ' client files; both existing-loader merges, native resource and narrative preservation passed. No live files changed.')


def install(stage, backup):
    manifest = json.loads((stage / 'manifest.json').read_text())
    assert manifest['feature'] in ('the-unbroken-field-dialogues', 'the-unbroken-field-tracking') and not manifest['installed']
    tracking = manifest['feature'] == 'the-unbroken-field-tracking'
    client = Path(manifest['client'])
    assert not backup.exists() and not backup.is_relative_to(ROOT) and not backup.is_relative_to(client)
    # This update replaces client text only. Reuse the standing client closure
    # guard, without requiring an unrelated GameServer shutdown.
    checks = module('standing_client_checks', ROOT / 'client-mods/aetherfall-core/install.py')
    for name, expected in manifest['sources'].items():
        assert sha(ROOT / name) == expected, 'Maintained source changed: ' + name
    for name, expected in manifest['liveInputs'].items():
        assert sha(Path(name)) == expected, 'Installed resource changed: ' + name
    for entry in manifest['files']:
        assert sha(stage / 'client' / entry['path']) == entry['installed']
    checks.closed()
    closure = dict(checkedAt=datetime.now(timezone.utc).isoformat(), clientAndLauncherClosed=True,
                   method='Successful full Win32_Process enumeration', gameServerClosureRequired=False)
    backup.mkdir(parents=True)
    for entry in manifest['files']:
        copy_file(client / entry['path'], backup / 'files' / entry['path'])
    receipt = dict(feature=manifest['feature'], status='prepared', client=str(client), stage=str(stage),
                   originalOnly=True, questIds=manifest['questIds'], files=manifest['files'], closure=closure,
                   validation=manifest['validation'], preserved=manifest['preserved'], sources=manifest['sources'],
                   serverChanged=False, coreChanged=False, componentsStarted=False, gameplayVerified=False)
    save(backup / 'receipt.json', receipt)
    checks.closed()
    copied = []
    try:
        for entry in manifest['files']:
            target = client / entry['path']
            assert sha(target) == entry['original'], 'Target changed during preparation'
            copy_file(stage / 'client' / entry['path'], target)
            copied.append(entry)
        receipt['checks'] = checks.checks(client, backup)
        assert all(check['rc'] == 0 for check in receipt['checks'].values()), 'Client compatibility guard failed'
        assert receipt['checks']['custom-quests']['output'] == 'Custom quests unchanged (17).'
        assert all(sha(client / e['path']) == e['installed'] for e in copied)
        assert all(sha(Path(name)) == expected for name, expected in manifest['preserved'].items())
    except Exception as error:
        receipt.update(status='failed', error=str(error))
        save(backup / 'receipt.json', receipt)
        checks.closed()
        for entry in reversed(copied):
            assert sha(client / entry['path']) == entry['installed'], 'Rollback stopped: newer target detected'
            copy_file(backup / 'files' / entry['path'], client / entry['path'])
        receipt['status'] = 'rolled-back'
        save(backup / 'receipt.json', receipt)
        raise
    receipt.update(status='installed', installedAt=datetime.now(timezone.utc).isoformat(), copiedHashesVerified=len(copied))
    save(backup / 'receipt.json', receipt)
    inventory_path = ROOT / 'docs/INSTALLED_MODS.json'
    inventory = json.loads(inventory_path.read_text())
    entries = {e['path'].casefold(): e for e in inventory['clientFiles']}
    for record in copied:
        assert record['path'].casefold() in entries
        entries[record['path'].casefold()]['sha256'] = record['installed']
    inventory['lastUnbrokenFieldTrackingDelivery' if tracking else 'lastUnbrokenFieldDialogueDelivery'] = dict(installedAt=receipt['installedAt'], receipt=str(backup / 'receipt.json'),
                                                        stage=str(stage), originalOnly=True, questIds=manifest['questIds'],
                                                        verification=manifest['validation'], coreChanged=False, serverChanged=False,
                                                        gameplayAcceptance='Owner live hunt/collection counters and map markers after partial progress/relog pending' if tracking else 'Owner automatic objective-update size/alignment/appearance pending')
    inventory['clientReceipts'].append(str(backup / 'receipt.json'))
    save(inventory_path, inventory)
    manifest.update(installed=True, receipt=str(backup / 'receipt.json'))
    save(stage / 'manifest.json', manifest)
    print('Installed quest ' + ('tracking/counter' if tracking else 'dialogue formatting') + ' correction; '
          + str(len(copied)) + ' copied hashes and three compatibility checks passed. Core UI and server preserved. Receipt: ' + str(backup / 'receipt.json'))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='mode', required=True)
    prep = sub.add_parser('prepare')
    for name in ('client', 'output'):
        prep.add_argument('--' + name, type=Path, required=True)
    prep.add_argument('--tracking', action='store_true', help='Update live counters and native map guidance; client resources only')
    deliver = sub.add_parser('install')
    for name in ('stage', 'backup'):
        deliver.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    if args.mode == 'prepare':
        prepare(args.client.resolve(strict=True), args.output.resolve(), args.tracking)
    else:
        install(args.stage.resolve(strict=True), args.backup.resolve())
