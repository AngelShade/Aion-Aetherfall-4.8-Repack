"""Build and deliver An Honest Seal's item through native loot and Custom Quests.

Preparation uses external fixtures and the installed, unchanged quest loader.
Installation requires normal client, launcher and GameServer closure.
"""
import argparse
import copy
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import subprocess
import shutil
from xml.etree import ElementTree as ET
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
FOLDER = '14983-AnHonestSeal'
ITEMS = 'Data/Items/Items.pak'
ENGLISH = 'L10N/ENU/data/data.pak'
INDEX = 'bin64/AionIconBridge.index'
SERVER_FILES = ('game-server/data/handlers/quest/verteron/_14983AnHonestSeal.java',
                'game-server/data/static_data/quest_data/quest_data.xml',
                'game-server/data/static_data/items/item_templates.xml')


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


resources = module('seal_resources', HERE / 'prepare.py')
delivery = module('seal_delivery', HERE / 'install.py')
sha, save, copy_file = resources.sha, resources.write_json, resources.copy_file


def patch_archive(path, member, update):
    before = path.read_bytes()
    with resources.read_pak(path) as pak:
        tree = resources.binary_xml(pak.read(member))
    update(tree)
    patched = resources.journal.extend_archive(before, {member: resources.encode_binary_xml(tree)}, {})
    path.write_bytes(patched)
    old, _, _, _ = resources.parts(before)
    new, _, _, _ = resources.parts(patched)
    assert list(old) == list(new)
    assert all(old[n][0] == new[n][0] for n in old if n != member)


def preservation(before, after, allowed, table_ids):
    old, _, _, _ = resources.parts(before.read_bytes())
    new, _, _, _ = resources.parts(after.read_bytes())
    assert list(old) == list(new)
    assert all(old[n][0] == new[n][0] for n in old if n not in allowed)
    with resources.read_pak(before) as a, resources.read_pak(after) as b:
        changed = [n for n in allowed if a.read(n) != b.read(n)]
        assert set(changed) == set(allowed)
        for member, ids in table_ids.items():
            original = resources.binary_xml(a.read(member))
            result = resources.binary_xml(b.read(member))
            untouched = {n.findtext('id'): resources.normalize(n) for n in result if n.findtext('id') not in ids}
            expected = {n.findtext('id'): resources.normalize(n) for n in original if n.findtext('id') not in ids}
            assert untouched == expected, 'Unrelated table row changed: ' + member
            assert len(result) == len(original) + sum(i not in {n.findtext('id') for n in original} for i in ids)
    return dict(changedMembers=sorted(changed), preservedCompressedMembers=len(old) - len(allowed),
                unrelatedTableRowsPreserved=True)


def prepare(client, build, output):
    assert not output.exists() and not output.is_relative_to(ROOT) and not output.is_relative_to(client)
    output.mkdir(parents=True)
    item = json.loads((HERE / 'loot-item.json').read_text())
    captured, writes = {}, []

    def watch(path):
        captured[str(path)] = sha(path) if path.exists() else None

    def payload(target, source):
        watch(target)
        stage = output / 'payload' / str(len(writes))
        copy_file(source, stage)
        writes.append(dict(target=str(target), stage=stage.relative_to(output).as_posix(),
                           before=captured[str(target)], after=sha(stage)))

    def json_payload(relative, value):
        path = output / 'metadata' / relative
        save(path, value)
        payload(client / relative, path)

    graphics = json.loads((client / 'Mods/GraphicsMenu/installed.json').read_text())
    package = json.loads((client / 'Mods/GraphicsMenu/package/manifest.json').read_text())
    assert graphics['files'] == package['files'] and not graphics.get('restoredAt')
    restore = resources.owned_renderer_backup(client, graphics['backupRoot'])
    locale = next(e for e in graphics['files'] if e['path'].casefold() == ENGLISH.casefold())
    assert sha(client / ENGLISH) == sha(client / 'Mods/GraphicsMenu/package' / ENGLISH) == locale['installed']
    assert sha(restore / ENGLISH) == locale['original']
    for relative in (*resources.ARCHIVES, ITEMS, INDEX, 'Aetherfall Launcher.exe',
                     'Mods/CustomQuests/installed.json', 'Mods/CustomQuests/settings.json',
                     'Mods/GraphicsMenu/installed.json', 'Mods/GraphicsMenu/package/manifest.json',
                     'Mods/GraphicsMenu/package/' + ENGLISH, 'Mods/InstalledMods.json',
                     'bin64/Game.dll', 'bin64/AetherfallCore.dll', 'bin64/AionIconBridge.dll',
                     'Data/ui/game/game.pak', 'Plugin/RelicCalc/RelicCalc.pak',
                     'Mods/Launcher/connection.json', 'Mods/Launcher/preferences.json'):
        watch(client / relative)
    watch(restore / ENGLISH)
    watch(ROOT / 'target-deploy/game-server/libs/game-server-4.8-SNAPSHOT.jar')
    for relative in ('data/handlers/ai/quests/QuestItemNpcAI.java', 'data/static_data/npcs/npc_templates.xml',
                     'data/static_data/spawns/Npcs/210030000_Verteron.xml'):
        watch(ROOT / 'target-deploy/game-server' / relative)
    source_paths = [HERE / n for n in ('generate.py', 'arc.json', 'bindings.json', 'loot-item.json', 'update_loot.py')]
    source_paths += list((HERE.parent / 'Quests' / FOLDER).iterdir())
    source_paths += [ROOT / n for n in SERVER_FILES]
    sources = {p.relative_to(ROOT).as_posix(): sha(p) for p in source_paths if p.is_file()}
    build_manifest = json.loads((build / 'build-manifest.json').read_text())
    with zipfile.ZipFile(build / 'game-server/game-server.zip') as assembly:
        for relative in SERVER_FILES:
            assert build_manifest['inputs'][relative] == sha(ROOT / relative)
            data = assembly.read(relative)
            assert hashlib.sha256(data).hexdigest() == sha(ROOT / relative)
            path = output / 'server' / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
            payload(ROOT / 'target-deploy' / relative, path)

    # A genuine quest item, with the restrictions and native art of the Tolbas
    # seal. Its distinct ID leaves Q1155's supplies and Q1158's seal untouched.
    active = output / 'active'
    copy_file(client / ITEMS, active / ITEMS)
    def add_item(tree):
        assert all(n.findtext('id') != str(item['id']) and n.findtext('name') != item['clientName'] for n in tree)
        prototype = next(n for n in tree if n.findtext('id') == str(item['prototype']))
        assert prototype.findtext('icon_name') == item['icon']
        added = copy.deepcopy(prototype)
        for field, value in dict(id=item['id'], name=item['clientName'], desc=item['nameKey'], desc_long=item['descriptionKey']).items():
            added.find(field).text = str(value)
        tree.append(added)
    patch_archive(active / ITEMS, 'client_items_etc.xml', add_item)
    old_index = (client / INDEX).read_bytes()
    magic, old_count, textures, length, digest = struct.unpack_from('<8sIIQ32s', old_index)
    assert magic == b'AICON002' and length == (client / ITEMS).stat().st_size and digest == bytes.fromhex(sha(client / ITEMS))
    indexer = module('seal_item_index', ROOT / 'client-mods/native-icon-bridge/prepare_index.py')
    index_result = indexer.prepare(active, active / INDEX)
    new_index = (active / INDEX).read_bytes()
    _, new_count, new_textures, _, _ = struct.unpack_from('<8sIIQ32s', new_index)
    old_mapping = dict(struct.iter_unpack('<II', old_index[56:56 + old_count * 8]))
    new_mapping = dict(struct.iter_unpack('<II', new_index[56:56 + new_count * 8]))
    assert new_count == old_count + 1 and textures == new_textures
    assert item['id'] not in old_mapping and new_mapping.pop(item['id']) == old_mapping[item['prototype']]
    assert new_mapping == old_mapping
    for i in range(textures):
        a, b = 56 + old_count * 8 + i * 56, 56 + new_count * 8 + i * 56
        assert old_index[a + 4:a + 56] == new_index[b + 4:b + 56]

    original_ledger = json.loads((client / 'Mods/CustomQuests/installed.json').read_text())
    validation = {'nativeItemIconIndex': index_result}
    for kind in ('active', 'restore'):
        fixture = output / kind
        for relative in resources.ARCHIVES:
            copy_file(restore / relative if kind == 'restore' and relative == ENGLISH else client / relative, fixture / relative)
        shutil.copytree(client / 'Mods/CustomQuests', fixture / 'Mods/CustomQuests')
        for path in (HERE.parent / 'Quests' / FOLDER).iterdir():
            watch(client / 'Mods/CustomQuests/Quests' / FOLDER / path.name)
            copy_file(path, fixture / 'Mods/CustomQuests/Quests' / FOLDER / path.name)
        def add_strings(tree):
            for ident, key, body in ((item['nameStringId'], item['nameKey'], item['name']),
                                     (item['descriptionStringId'], item['descriptionKey'], item['description'])):
                assert all(n.findtext('id') != str(ident) and n.findtext('name') != key for n in tree)
                n = ET.SubElement(tree, 'string')
                for field, value in dict(id=ident, name=key, body=body).items():
                    ET.SubElement(n, field).text = str(value)
        patch_archive(fixture / ENGLISH, 'strings/client_strings_item.xml', add_strings)
        save(fixture / 'Mods/CustomQuests/settings.json', dict(BackupRoot=str(output / 'loader-backups')))
        report = output / (kind + '-loader.txt')
        result = subprocess.run([str(client / 'Aetherfall Launcher.exe'), '--custom-quests-sync', '--client', str(fixture), '--report', str(report)], capture_output=True, text=True)
        message = report.read_text() if report.exists() else result.stderr
        assert result.returncode == 0, message
        ledger = json.loads((fixture / 'Mods/CustomQuests/installed.json').read_text())
        assert ledger['Owned'].keys() == original_ledger['Owned'].keys()
        assert {k for k in ledger['Owned'] if ledger['Owned'][k] != original_ledger['Owned'][k]} == {'q:14983', 'r:14983', 'd:14983'}
        validation[kind] = dict(loader=message.strip(),
            questArchive=preservation(client / resources.ARCHIVES[0], fixture / resources.ARCHIVES[0],
                                     {'quest.xml', 'data_driven_quest.xml'},
                                     {'quest.xml': {'14983'}, 'data_driven_quest.xml': {'14983'}}),
            englishArchive=preservation(restore / ENGLISH if kind == 'restore' else client / ENGLISH, fixture / ENGLISH,
                                       {'strings/client_strings_item.xml', 'dialogs/quest_q14983.html'},
                                       {'strings/client_strings_item.xml': {str(item['nameStringId']), str(item['descriptionStringId'])}}))
    validation['itemsArchive'] = preservation(client / ITEMS, active / ITEMS, {'client_items_etc.xml'},
                                              {'client_items_etc.xml': {str(item['id'])}})
    for path in (HERE.parent / 'Quests' / FOLDER).iterdir():
        target = client / 'Mods/CustomQuests/Quests' / FOLDER / path.name
        if sha(path) != sha(target):
            payload(target, path)
    for relative in (*resources.ARCHIVES, ITEMS, INDEX, 'Mods/CustomQuests/installed.json'):
        payload(client / relative, active / relative)
    payload(client / 'Mods/GraphicsMenu/package' / ENGLISH, active / ENGLISH)
    payload(restore / ENGLISH, output / 'restore' / ENGLISH)
    for relative in ('Mods/GraphicsMenu/installed.json', 'Mods/GraphicsMenu/package/manifest.json'):
        data = json.loads((client / relative).read_text())
        entry = next(e for e in data['files'] if e['path'].casefold() == ENGLISH.casefold())
        entry.update(installed=sha(active / ENGLISH), original=sha(output / 'restore' / ENGLISH))
        json_payload(relative, data)
    mods = json.loads((client / 'Mods/InstalledMods.json').read_text())
    by_name = {Path(e['target']).relative_to(client).as_posix().casefold(): e for e in writes if Path(e['target']).is_relative_to(client)}
    for entries in mods['features'].values():
        for entry in entries:
            updated = by_name.get(entry['path'].casefold())
            if updated:
                entry.update(installedSha256=updated['after'], bytes=(output / updated['stage']).stat().st_size)
    json_payload('Mods/InstalledMods.json', mods)
    assert all((sha(Path(p)) if Path(p).exists() else None) == v for p, v in captured.items()), 'Installed baseline changed during preparation'
    assert all(sha(ROOT / p) == v for p, v in sources.items()), 'Authoring changed during preparation; use a fresh stage'
    save(output / 'manifest.json', dict(feature='an-honest-seal-loot', originalOnly=True, client=str(client), build=str(build),
         questIds=[14983], item=item, files=writes, sourceHashes=sources, liveInputs=captured,
         validation=validation, installed=False, coreChanged=False, gameplayVerified=False))
    print('Prepared genuine quest item, native crate loot, pickup progression, journal and recovery resources; '
          + str(len(writes)) + ' affected files. Original installations untouched.')


def install(stage, backup):
    manifest = json.loads((stage / 'manifest.json').read_text())
    assert manifest['feature'] == 'an-honest-seal-loot' and not manifest['installed']
    client, server = Path(manifest['client']), ROOT / 'target-deploy/game-server'
    assert not backup.exists() and not backup.is_relative_to(ROOT) and not backup.is_relative_to(client)
    for name, expected in manifest['sourceHashes'].items():
        assert sha(ROOT / name) == expected, 'Source changed: ' + name
    for name, expected in manifest['liveInputs'].items():
        assert (sha(Path(name)) if Path(name).exists() else None) == expected, 'Installed resource changed: ' + name
    files = manifest['files']
    assert len({str(Path(e['target']).resolve()).casefold() for e in files}) == len(files)
    for e in files:
        assert Path(e['target']).is_relative_to(client) or Path(e['target']).is_relative_to(server)
        assert sha(stage / e['stage']) == e['after']
    closure = delivery.closed()
    backup.mkdir(parents=True)
    for i, e in enumerate(files):
        if e['before'] is not None:
            copy_file(Path(e['target']), backup / 'files' / str(i))
            e['backup'] = str(backup / 'files' / str(i))
    targets = {e['target'].casefold() for e in files}
    preserved = {p: v for p, v in manifest['liveInputs'].items() if p.casefold() not in targets}
    receipt = dict(feature=manifest['feature'], status='prepared', originalOnly=True, stage=str(stage),
                   build=manifest['build'], files=files, closure=closure, preserved=preserved,
                   validation=manifest['validation'], coreChanged=False, serverJarChanged=False,
                   componentsStarted=False, gameplayVerified=False, repackChanged=False)
    save(backup / 'receipt.json', receipt)
    receipt['closureBeforeCopy'] = delivery.closed()
    copied = []
    try:
        for e in files:
            assert sha(Path(e['target'])) == e['before'], 'Newer installed file detected'
            copy_file(stage / e['stage'], Path(e['target']))
            copied.append(e)
        checks = module('seal_client_checks', ROOT / 'client-mods/aetherfall-core/install.py')
        receipt['clientChecks'] = checks.checks(client, backup)
        assert all(c['rc'] == 0 for c in receipt['clientChecks'].values())
        assert receipt['clientChecks']['custom-quests']['output'] == 'Custom quests unchanged (17).'
        assert all(sha(Path(e['target'])) == e['after'] for e in files)
        assert all((sha(Path(p)) if Path(p).exists() else None) == v for p, v in preserved.items())
    except Exception as error:
        receipt.update(status='failed', error=str(error))
        save(backup / 'receipt.json', receipt)
        delivery.closed()
        for e in reversed(copied):
            assert sha(Path(e['target'])) == e['after'], 'Rollback stopped on newer file'
            copy_file(Path(e['backup']), Path(e['target']))
        receipt['status'] = 'rolled-back'
        save(backup / 'receipt.json', receipt)
        raise
    receipt.update(status='installed', installedAt=datetime.now(timezone.utc).isoformat(), copiedHashesVerified=len(files))
    save(backup / 'receipt.json', receipt)
    inventory_path = ROOT / 'docs/INSTALLED_MODS.json'
    inventory = json.loads(inventory_path.read_text())
    known = {e['path'].casefold(): e for e in inventory['clientFiles']}
    for e in files:
        p = Path(e['target'])
        if p.is_relative_to(client):
            relative = p.relative_to(client).as_posix()
            assert relative.casefold() in known
            known[relative.casefold()]['sha256'] = e['after']
    inventory['lastHonestSealLootDelivery'] = dict(installedAt=receipt['installedAt'], receipt=str(backup / 'receipt.json'),
        stage=str(stage), build=manifest['build'], originalOnly=True, questId=14983, itemId=manifest['item']['id'],
        coreChanged=False, serverJarChanged=False,
        gameplayAcceptance='Owner: crate loot/pickup, Santenius turn-in, native Q1155 coexistence, relog and abandon/reaccept pending')
    for key in ('clientReceipts', 'serverReceipts'):
        inventory[key].append(str(backup / 'receipt.json'))
    save(inventory_path, inventory)
    manifest.update(installed=True, receipt=str(backup / 'receipt.json'))
    save(stage / 'manifest.json', manifest)
    print('Installed An Honest Seal loot correction; ' + str(len(files)) + ' copied hashes and client compatibility checks passed. Receipt: ' + str(backup / 'receipt.json'))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='mode', required=True)
    prep = sub.add_parser('prepare')
    for name in ('client', 'build', 'output'):
        prep.add_argument('--' + name, type=Path, required=True)
    deliver = sub.add_parser('install')
    for name in ('stage', 'backup'):
        deliver.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    if args.mode == 'prepare':
        prepare(args.client.resolve(strict=True), args.build.resolve(strict=True), args.output.resolve())
    else:
        install(args.stage.resolve(strict=True), args.backup.resolve())
