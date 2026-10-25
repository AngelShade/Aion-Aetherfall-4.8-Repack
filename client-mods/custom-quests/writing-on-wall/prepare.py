"""Prepare this quest chain through the installed, unchanged Custom Quests loader.

All output is external. Running this tool never installs files or starts Aion or
GameServer. If Aion is open, rerun with the same output after normal closure.
"""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
from xml.etree import ElementTree as ET
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(ROOT / 'client-mods/expanded-warehouse'))
sys.path.insert(0, str(ROOT / 'client-mods/bundle-opening'))
sys.path.insert(0, str(ROOT / 'client-mods/transmog-menu'))
from codec import read_pak, binary_xml, encode_binary_xml
from prepare_client import parts
from graphics_compat import owned_renderer_backup

spec = importlib.util.spec_from_file_location('existing_quest_journal', ROOT / 'client-mods/quest-red-journal/prepare.py')
journal = importlib.util.module_from_spec(spec)
spec.loader.exec_module(journal)

ARCHIVES = ('Data/Quest/Quest.pak', 'L10N/ENU/data/data.pak')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')


def copy_file(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)
    assert sha(source) == sha(target)


def normalize(node):
    node = copy.deepcopy(node)
    for child in node.iter():
        child.tail = None
        if child.text is not None and not child.text.strip():
            child.text = None
    return ET.tostring(node)


def reconcile_restore_3999(before, restore, output):
    """Restore the already-installed quest's missing English assets, with proof.

    Older Graphics recovery images predate Custom Quests. Their missing 3999
    records are not an independent edit to the live ownership ledger. Repair
    only verified missing records from maintained source before normal Sync;
    never delete or relax an ownership record.
    """
    path = restore / ARCHIVES[1]
    with read_pak(before / ARCHIVES[1]) as active, read_pak(path) as recovery:
        active_strings = binary_xml(active.read('strings/client_strings_quest.xml'))
        recovery_strings = binary_xml(recovery.read('strings/client_strings_quest.xml'))
        wanted = ET.parse(ROOT / 'client-mods/custom-quests/Quests/3999-TheUnfinishedEntry/strings.xml').getroot()[0]
        title = next(n for n in active_strings if n.findtext('id') == '1801364')
        assert normalize(title) == normalize(wanted)
        matches = [n for n in recovery_strings if n.findtext('id') == '1801364' or n.findtext('name') == 'STR_QUEST_NAME_Q3999']
        dialogue = 'dialogs/quest_q3999.html'
        text = journal.decode_html(active.read(dialogue), Path(dialogue).name)
        authored = ET.parse(ROOT / 'client-mods/custom-quests/Quests/3999-TheUnfinishedEntry/dialog.html').getroot()
        assert normalize(ET.fromstring(text)) == normalize(authored)
        replacements, additions = {}, {}
        if not matches:
            recovery_strings.append(copy.deepcopy(title))
            replacements['strings/client_strings_quest.xml'] = encode_binary_xml(recovery_strings)
        else:
            assert len(matches) == 1 and normalize(matches[0]) == normalize(wanted)
        if dialogue not in recovery.namelist():
            additions[dialogue] = active.read(dialogue)
        else:
            assert recovery.read(dialogue) == active.read(dialogue)
        if not replacements and not additions:
            return False
        original = path.read_bytes()
        patched = journal.extend_archive(original, replacements, additions)
        old_parts, _, _, _ = parts(original)
        new_parts, _, _, _ = parts(patched)
        assert all(old_parts[name][0] == new_parts[name][0] for name in old_parts if name not in replacements)
        path.write_bytes(patched)
        write_json(output / 'existing-3999-recovery-reconciliation.json', dict(questId=3999,
                   missingTitleRestored=bool(replacements), missingDialogueRestored=bool(additions),
                   before=hashlib.sha256(original).hexdigest(), after=sha(path),
                   ownershipRecordsChanged=False, existingNativeCompressedMembersPreserved=True))
        return True


def check_archives(before, after, quest_ids, recovered_3999=False):
    result = []
    for relative in ARCHIVES:
        old_parts, _, _, _ = parts((before / relative).read_bytes())
        new_parts, _, _, _ = parts((after / relative).read_bytes())
        allowed = {'quest.xml', 'data_driven_quest.xml', 'quest_script_monster.csv'} if relative == ARCHIVES[0] else {'strings/client_strings_quest.xml'}
        added = set() if relative == ARCHIVES[0] else {f'dialogs/quest_q{i}.html' for i in quest_ids}
        if recovered_3999 and relative == ARCHIVES[1]:
            added.add('dialogs/quest_q3999.html')
        assert set(new_parts) == set(old_parts) | added
        assert not set(old_parts) & added, 'Native dialogue ID collision'
        for name, record in old_parts.items():
            if name not in allowed:
                assert record[0] == new_parts[name][0], 'Unrelated compressed member changed: ' + name
        with read_pak(before / relative) as old, read_pak(after / relative) as new:
            tables = ['quest.xml', 'data_driven_quest.xml'] if relative == ARCHIVES[0] else ['strings/client_strings_quest.xml']
            for table in tables:
                original, changed = binary_xml(old.read(table)), binary_xml(new.read(table))
                old_ids = {n.findtext('id') for n in original}
                assert len(changed) == len(original) + 16 + int(recovered_3999 and relative == ARCHIVES[1])
                preserved = {n.findtext('id'): normalize(n) for n in changed if n.findtext('id') in old_ids}
                assert all(preserved[n.findtext('id')] == normalize(n) for n in original)
            if relative == ARCHIVES[0]:
                original = old.read('quest_script_monster.csv')
                changed = new.read('quest_script_monster.csv')
                assert changed.startswith(original), 'Existing monster guidance changed'
        result.append(dict(path=relative, preservedCompressedMembers=len(old_parts) - len(allowed), addedMembers=len(added)))
    return result


def prepare(client, build, output):
    assert not output.is_relative_to(ROOT) and not output.is_relative_to(client)
    arc = json.loads((HERE / 'arc.json').read_text())
    quest_ids = [q['id'] for q in arc['quests']]
    assert quest_ids == list(range(14980, 14996))
    state_path = output / 'preparation.json'
    if not state_path.exists():
        assert not output.exists(), 'Use a fresh output directory'
        output.mkdir(parents=True)
        graphics = json.loads((client / 'Mods/GraphicsMenu/installed.json').read_text())
        package = json.loads((client / 'Mods/GraphicsMenu/package/manifest.json').read_text())
        assert graphics['files'] == package['files'] and not graphics.get('restoredAt')
        restore = owned_renderer_backup(client, graphics['backupRoot'])
        locale = next(e for e in graphics['files'] if e['path'].casefold() == ARCHIVES[1].casefold())
        assert sha(client / ARCHIVES[1]) == locale['installed']
        assert sha(client / 'Mods/GraphicsMenu/package' / ARCHIVES[1]) == locale['installed']
        assert sha(restore / ARCHIVES[1]) == locale['original']
        before = {}

        def watch(path):
            before[str(path)] = sha(path) if path.exists() else None

        for name in [*ARCHIVES, 'Mods/GraphicsMenu/installed.json', 'Mods/GraphicsMenu/package/manifest.json',
                     'Mods/GraphicsMenu/package/' + ARCHIVES[1], 'Mods/InstalledMods.json', 'Aetherfall Launcher.exe']:
            watch(client / name)
        watch(restore / ARCHIVES[1])
        for path in (client / 'Mods/CustomQuests').rglob('*'):
            if path.is_file():
                watch(path)
        for name in ('bin64/Game.dll', 'Data/ui/game/game.pak', 'Mods/Renderer/installed.json',
                     'Mods/Launcher/connection.json', 'Mods/Launcher/preferences.json'):
            if (client / name).exists():
                watch(client / name)
        sources = [p for p in HERE.rglob('*') if p.is_file() and '__pycache__' not in p.parts]
        sources += [ROOT / 'client-mods/custom-quests/README.txt']
        sources += list((ROOT / 'client-mods/custom-quests/Quests/3999-TheUnfinishedEntry').iterdir())
        for q in arc['quests']:
            sources += list((HERE.parent / 'Quests' / (str(q['id']) + '-' + q['slug'])).iterdir())
        server_sources = [ROOT / 'game-server/data/static_data/quest_data/quest_data.xml']
        server_sources += [ROOT / 'game-server/data/handlers/quest/verteron' / ('_' + str(q['id']) + q['slug'] + '.java') for q in arc['quests']]
        sources += server_sources
        for kind in ('before-active', 'before-restore', 'active', 'restore'):
            fixture = output / kind
            for relative in ARCHIVES:
                source = restore / relative if kind.endswith('restore') and relative == ARCHIVES[1] else client / relative
                copy_file(source, fixture / relative)
            if kind in ('active', 'restore'):
                shutil.copytree(client / 'Mods/CustomQuests', fixture / 'Mods/CustomQuests')
                for q in arc['quests']:
                    folder = str(q['id']) + '-' + q['slug']
                    assert not (fixture / 'Mods/CustomQuests/Quests' / folder).exists()
                    shutil.copytree(HERE.parent / 'Quests' / folder, fixture / 'Mods/CustomQuests/Quests' / folder)
                write_json(fixture / 'Mods/CustomQuests/settings.json', dict(BackupRoot=str(output / 'loader-backups')))
        server = []
        manifest = json.loads((build / 'build-manifest.json').read_text())
        with zipfile.ZipFile(build / 'game-server/game-server.zip') as assembly:
            for source in server_sources:
                name = source.relative_to(ROOT).as_posix()
                assert manifest['inputs'][name] == sha(source), 'Source changed after normal build: ' + name
                payload = assembly.read(name)
                assert hashlib.sha256(payload).hexdigest() == sha(source), 'Assembly resource differs'
                target = ROOT / 'target-deploy' / name
                watch(target)
                stage = output / 'server' / name
                stage.parent.mkdir(parents=True, exist_ok=True)
                stage.write_bytes(payload)
                server.append(dict(stage=str(stage.relative_to(output)), target=str(target), before=before[str(target)], after=sha(stage)))
        state = dict(client=str(client), build=str(build), before=before, server=server,
                     sourceHashes={p.relative_to(ROOT).as_posix(): sha(p) for p in sources},
                     graphicsRestore=str(restore.relative_to(client)), status='captured', questIds=quest_ids)
        write_json(state_path, state)
    else:
        state = json.loads(state_path.read_text())
        assert state['client'] == str(client) and state['build'] == str(build)
    for path, expected in state['before'].items():
        current = Path(path)
        assert (sha(current) if current.exists() else None) == expected, 'Live input changed; prepare a fresh package: ' + path
    for path, expected in state['sourceHashes'].items():
        assert sha(ROOT / path) == expected, 'Maintained quest input changed: ' + path
    recovered = reconcile_restore_3999(output / 'before-active', output / 'restore', output)
    recovered = recovered or (output / 'existing-3999-recovery-reconciliation.json').exists()
    for kind in ('active', 'restore'):
        report = output / (kind + '-loader.txt')
        result = subprocess.run([str(client / 'Aetherfall Launcher.exe'), '--custom-quests-sync', '--client', str(output / kind), '--report', str(report)], capture_output=True, text=True)
        text = report.read_text() if report.exists() else result.stderr
        if result.returncode:
            if 'Close Aion normally' in text:
                state['status'] = 'awaiting-normal-client-closure'
                write_json(state_path, state)
                print('All packages passed loader record/ownership checks. Close Aion normally, then rerun this command to complete the external merge.')
                return
            raise RuntimeError(text)
    validation = dict(active=check_archives(output / 'before-active', output / 'active', quest_ids),
                      restore=check_archives(output / 'before-restore', output / 'restore', quest_ids, recovered),
                      loader='Installed unchanged launcher; both active and Graphics restore merge', gameplayVerified=False)
    writes = []

    def payload(relative, source=None, data=None):
        stage = output / 'client' / relative
        stage.parent.mkdir(parents=True, exist_ok=True)
        if source is not None:
            copy_file(source, stage)
        else:
            stage.write_bytes(data)
        target = client / relative
        writes.append(dict(stage=str(stage.relative_to(output)), target=str(target), before=sha(target) if target.exists() else None, after=sha(stage)))

    for q in arc['quests']:
        folder = str(q['id']) + '-' + q['slug']
        for source in (HERE.parent / 'Quests' / folder).iterdir():
            payload('Mods/CustomQuests/Quests/' + folder + '/' + source.name, source=source)
    payload('Mods/CustomQuests/README.txt', source=HERE.parent / 'README.txt')
    payload('Mods/CustomQuests/installed.json', source=output / 'active/Mods/CustomQuests/installed.json')
    for name in ARCHIVES:
        payload(name, source=output / 'active' / name)
    payload('Mods/GraphicsMenu/package/' + ARCHIVES[1], source=output / 'active' / ARCHIVES[1])
    payload(state['graphicsRestore'] + '/' + ARCHIVES[1], source=output / 'restore' / ARCHIVES[1])
    for relative in ('Mods/GraphicsMenu/installed.json', 'Mods/GraphicsMenu/package/manifest.json'):
        graphics = json.loads((client / relative).read_text())
        entry = next(e for e in graphics['files'] if e['path'].casefold() == ARCHIVES[1].casefold())
        entry.update(installed=sha(output / 'active' / ARCHIVES[1]), original=sha(output / 'restore' / ARCHIVES[1]))
        payload(relative, data=(json.dumps(graphics, indent=2) + '\n').encode())
    mods = json.loads((client / 'Mods/InstalledMods.json').read_text())
    records = {str(Path(e['target'])).casefold(): e for e in writes}
    custom = mods['features']['CustomQuests']
    custom_paths = {e['path'].casefold() for e in custom}
    for record in writes:
        relative = Path(record['target']).relative_to(client).as_posix()
        if relative.startswith('Mods/CustomQuests/') and relative.casefold() not in custom_paths:
            custom.append(dict(path=relative))
    for entries in mods['features'].values():
        for entry in entries:
            record = records.get(str(client / entry['path']).casefold())
            if record:
                entry.update(installedSha256=record['after'], bytes=(output / record['stage']).stat().st_size)
    payload('Mods/InstalledMods.json', data=(json.dumps(mods, indent=2) + '\n').encode())
    manifest = dict(feature='the-unbroken-field', originalOnly=True, client=str(client), build=str(build), questIds=quest_ids,
                    sourceHashes=state['sourceHashes'], liveInputs=state['before'], files=state['server'] + writes,
                    validation=validation, installed=False, coreChanged=False, serverJarChanged=False,
                    componentsStarted=False, repackChanged=False, gameplayVerified=False)
    write_json(output / 'manifest.json', manifest)
    state['status'] = 'ready-for-guarded-installation'
    write_json(state_path, state)
    print('Prepared 17 exact server resources and ' + str(len(writes)) + ' client cohort files through the existing loader. Native entries and unrelated compressed members preserved. Nothing installed.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'build', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    prepare(args.client.resolve(strict=True), args.build.resolve(strict=True), args.output.resolve())
