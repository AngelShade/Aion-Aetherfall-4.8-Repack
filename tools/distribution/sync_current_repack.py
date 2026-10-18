"""Synchronize the recorded original 0.93.2 cohort, without game lifecycle actions."""
from pathlib import Path
import zipfile
from refresh_original_repack import read, sha, encoded, merge_defaults, deliver, assert_closed


def complete_existing():
    """Finish the recorded copy with the later normally packaged quest repair."""
    repo = Path(__file__).resolve().parents[2]
    workspace = Path(r'D:\Proiecte\Project Restructure\Aion Development Workspace')
    copied_receipt = workspace/'archives/repack/original0932-refresh-20261019/receipt.json'
    copied = read(copied_receipt)
    repair_receipt = workspace/'archives/server/backups/zirius-script-context-20261019/receipt.json'
    repair = read(repair_receipt)
    backup = workspace/'archives/repack/original0932-completion-20261019'
    if backup.exists():
        raise ValueError('Completion already has a receipt or backup; review before retrying')
    roots = [Path(d['root']) for d in copied['destinations']]
    expected_full = Path(read(repo/'releases/Maintenance/release-layout.json')['repackFolder'])
    if roots != [expected_full, repo/'releases/Aetherfall-Downloader/Games/Aetherfall-0.86']:
        raise ValueError('Recorded destinations differ from current layout')
    original = repo/'target-deploy/game-server'
    jar = original/'libs/game-server-4.8-SNAPSHOT.jar'
    if sha(jar.read_bytes()) != copied['gameServerSha256']:
        raise ValueError('Original GameServer changed; reconcile its newer build')
    build, previous = Path(repair['build']), Path(copied['build'])
    current, prior = read(build/'build-manifest.json'), read(previous/'build-manifest.json')
    key = 'game-server/data/handlers/quest/theobomos/_3057CursedZirius.java'
    if {k for k in set(current['inputs']) | set(prior['inputs'])
            if current['inputs'].get(k) != prior['inputs'].get(k)} != {key}:
        raise ValueError('Later build contains additional changes requiring review')
    with zipfile.ZipFile(build/'game-server/game-server.zip') as assembly:
        data = assembly.read(key)
    if sha(data) != repair['installedSha256'] or sha((repo/key).read_bytes()) != sha(data) or sha((repo/'target-deploy'/key).read_bytes()) != sha(data):
        raise ValueError('Normal packaged quest, maintained source and original differ')
    plans = []
    for destination in copied['destinations']:
        root = Path(destination['root'])
        if read(root/'release.json')['Version'] != '0.93.2':
            raise ValueError('Repack version changed')
        for entry in destination['files']:
            if sha((root/entry['path']).read_bytes()) != entry['sha256']:
                raise ValueError('Recorded copy changed: '+entry['path'])
        guards = destination['protectedSettings']
        if any(sha((root/rel).read_bytes()) != digest for rel, digest in guards.items()):
            raise ValueError('Repack settings changed since the recorded copy')
        changes = {'Server/'+key: data, 'CHANGELOG.md': (repo/'CHANGELOG.md').read_bytes()}
        identity = read(root/'REPACK.json')
        identity['latestOriginalRefresh'].update(scriptRepairBuild=str(build), completionReceipt=str(backup/'receipt.json'))
        changes['REPACK.json'] = encoded(identity)
        index = read(root/'FILES.json')
        entries = {entry['path']: entry for entry in index['files']}
        for rel, content in changes.items():
            entries[rel].update(sha256=sha(content), bytes=len(content))
        changes['FILES.json'] = encoded(index, True)
        plans.append((root, changes, guards))
    assert_closed(roots)
    results = []
    for number, (root, changes, guards) in enumerate(plans):
        assert_closed(roots)
        records = deliver(root, changes, backup/('full-before' if number == 0 else 'installed-before'))
        if any(sha((root/rel).read_bytes()) != digest for rel, digest in guards.items()):
            raise ValueError('Protected settings changed during delivery')
        results.append(dict(root=str(root), files=records, protectedSettings=guards))
    if sha(jar.read_bytes()) != copied['gameServerSha256']:
        raise ValueError('Original GameServer changed during completion')
    receipt = dict(version='0.93.2', earlierCopyReceipt=str(copied_receipt),
                   normalScriptBuild=str(build), originalRepairReceipt=str(repair_receipt),
                   destinations=results, originalChanged=False, sqlExecuted=False,
                   componentsStartedOrStopped=False, publicUpload=False,
                   gameplayAcceptance='Pending user companion/statue/Zirius checks')
    (backup/'receipt.json').write_bytes(encoded(receipt))
    print('Completed 0.93.2:', [len(r['files']) for r in results], 'copied hashes verified; receipt:', backup/'receipt.json')


def main():
    repo = Path(__file__).resolve().parents[2]
    workspace = Path(r'D:\Proiecte\Project Restructure\Aion Development Workspace')
    full = Path(read(repo/'releases/Maintenance/release-layout.json')['repackFolder'])
    installed = repo/'releases/Aetherfall-Downloader/Games/Aetherfall-0.86'
    backup = workspace/'archives/repack/original0932-refresh-20261019'
    client = (Path.home() / 'Downloads/Aion Client Development Server 4.8 NA')
    receipt = read(workspace/'archives/delivery/teleport-statue-zirius-20261019.json')
    build = workspace/'staging/target/teleport-statue-zirius-20261018'
    previous = Path(read(workspace/'archives/repack/original0931-refresh-20261018/receipt.json')['build'])
    current, prior = read(build/'build-manifest.json'), read(previous/'build-manifest.json')
    assert not backup.exists()
    assert not (full/'Server/database/data').exists()
    assert all(read(root/'release.json')['Version'] == '0.93.1' for root in (full, installed))
    assert_closed([full, installed])
    jar = build/'game-server/game-server-4.8-SNAPSHOT.jar'
    digest = current['artifacts']['game-server/game-server-4.8-SNAPSHOT.jar']
    assert sha(jar.read_bytes()) == digest == sha((repo/'target-deploy/game-server/libs'/jar.name).read_bytes())
    common = {'Server/game-server/libs/'+jar.name: jar.read_bytes()}
    configurations = {}
    with zipfile.ZipFile(build/'game-server/game-server.zip') as new, zipfile.ZipFile(previous/'game-server/game-server.zip') as old:
        for key, value in current['inputs'].items():
            if prior['inputs'].get(key) == value or not key.startswith(('game-server/data/', 'game-server/config/')):
                continue
            if key not in new.namelist():
                with zipfile.ZipFile(jar) as resources:
                    assert sha(resources.read(key[len('game-server/config/'):])) == value
                continue
            data = new.read(key)
            assert sha(data) == value
            rel = 'Server/'+key
            if key.endswith('.properties'):
                configurations[rel] = (old.read(key), data)
            else:
                assert sha((repo/'target-deploy'/key).read_bytes()) == value
                common[rel] = data
    with zipfile.ZipFile(build/'assembly-libs/commons-4.8-SNAPSHOT.jar') as new, zipfile.ZipFile(full/'Server/game-server/libs/commons-4.8-SNAPSHOT.jar') as old:
        assert set(new.namelist()) == set(old.namelist())
        assert all(new.read(name) == old.read(name) for name in new.namelist() if name != 'META-INF/MANIFEST.MF')
    client_data = {}
    for rel in ['Pub.key', 'Addon.key', 'bin32/bin32.pak.sig', 'Data/func_pet/func_pet.pak.sig', 'Plugin/RelicCalc/RelicCalc.pak', 'Plugin/RelicCalc/RelicCalc.pak.sig']:
        assert all(sha((root/'Client'/rel).read_bytes()) == sha((client/rel).read_bytes()) for root in (full, installed)), 'Signing cohort differs: '+rel
    for entry in receipt['clientFiles']:
        data = (client/entry['path']).read_bytes()
        assert sha(data) == entry['installed'], entry['path']
        client_data['Client/'+entry['path']] = data
    results = []
    for root, label in ((full, 'full'), (installed, 'installed')):
        changes = dict(common)
        for rel, (before, after) in configurations.items():
            changes[rel] = merge_defaults((root/rel).read_bytes(), before, after)
        def portable(value):
            if isinstance(value, dict): return {k: portable(v) for k, v in value.items()}
            if isinstance(value, list): return [portable(v) for v in value]
            if isinstance(value, str): return value.replace(str(client), str(root/'Client')).replace(client.as_posix(), (root/'Client').as_posix())
            return value
        state = read(root/'Client/Mods/portable-client.json')
        metadata = set(state['metadataFiles'])
        for rel, data in client_data.items():
            if rel.endswith('.json'):
                import json
                data = encoded(portable(json.loads(data.decode('utf-8-sig'))))
                metadata.add(rel[len('Client/'):])
            changes[rel] = data
        state.update(preparedRoot=str(root/'Client'), gameDllSha256=sha(client_data['Client/bin64/Game.dll']), metadataFiles=sorted(metadata), release='0.93.2')
        changes['Client/Mods/portable-client.json'] = encoded(state)
        inventory = read(root/'Client/Mods/InstalledMods.json')
        for entries in inventory['features'].values():
            for entry in entries:
                rel = 'Client/'+entry['path']
                if rel in changes: entry.update(installedSha256=sha(changes[rel]), bytes=len(changes[rel]))
        inventory['lastOriginalRepackRefresh'] = dict(version='0.93.2', receipt=str(backup/'receipt.json'))
        changes['Client/Mods/InstalledMods.json'] = encoded(inventory)
        release = encoded(dict(Product='Aion Aetherfall Repack', Version='0.93.2'))
        changes.update({'release.json': release, 'Client/Mods/release.json': release, 'CHANGELOG.md': (repo/'CHANGELOG.md').read_bytes()})
        identity = read(root/'REPACK.json')
        identity.update(release='0932', productVersion='0.93.2', gameServerSha256=digest)
        identity['latestOriginalRefresh'] = dict(version='0.93.2', build=str(build), receipt=str(backup/'receipt.json'), sourceRowsCopied=0)
        changes['REPACK.json'] = encoded(identity)
        changes['README.txt'] = ((root/'README.txt').read_text(encoding='utf-8-sig').replace('REPACK 0.93.1', 'REPACK 0.93.2', 1)+'\nUPDATE 0.93.2\nImproved companion pursuit and catch-up; server-controlled statue teleport timing;\nProtectors Hall teleport statue; Cursed Zirius quest repair. See CHANGELOG.md.\nPrepared locally. Gameplay acceptance and publication remain separate.\n').encode()
        index = read(root/'FILES.json')
        entries = {entry['path'].casefold(): entry for entry in index['files']}
        for rel, data in changes.items():
            entry = entries.get(rel.casefold())
            if entry is None:
                entry = dict(path=rel, mutable=False)
                index['files'].append(entry)
            entry.update(sha256=sha(data), bytes=len(data))
        index['fileCount'] = len(index['files'])
        changes['FILES.json'] = encoded(index, True)
        protected = ['Server/game-server/config/mygs.properties', 'Server/login-server/config/myls.properties', 'Server/chat-server/config/mycs.properties', 'Client/Mods/Launcher/connection.json', 'Client/Mods/Launcher/preferences.json']
        guards = {rel: sha((root/rel).read_bytes()) for rel in protected if (root/rel).is_file()}
        assert_closed([full, installed])
        records = deliver(root, changes, backup/(label+'-before'))
        assert all(sha((root/rel).read_bytes()) == value for rel, value in guards.items())
        results.append(dict(root=str(root), files=records, protectedSettings=guards))
    assert sha((repo/'target-deploy/game-server/libs'/jar.name).read_bytes()) == digest
    (backup/'receipt.json').write_bytes(encoded(dict(version='0.93.2', build=str(build), previousBuild=str(previous), gameServerSha256=digest, destinations=results, originalChanged=False, sqlExecuted=False, publicUpload=False, gameplayAcceptance='Pending user testing')))
    print(str(backup/'receipt.json'))
    print([(r['root'], len(r['files'])) for r in results])


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--complete-existing', action='store_true', help='Finish the recorded 0.93.2 copy with its later quest compiler repair')
    args = parser.parse_args()
    complete_existing() if args.complete_existing else main()
