"""Verify and deliver unchanged Maven GameServer code/resources against a source-built runtime."""
import argparse, hashlib, json, re, shutil, subprocess, sys, zipfile
from datetime import datetime
from pathlib import Path
from importlib import import_module

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'client-mods/playerbots'))
import stage_companion_update as shared
from remove_follow_recovery_offline import stopped
build_meta = import_module('build-manifest')


def main():
    if not __debug__: raise RuntimeError('Validation cannot run with Python optimization enabled')
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--build', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--scope', required=True)
    p.add_argument('--release-plan', type=Path, required=True, help='Complete automatic resource/dependency/handler review from tools/prepare-source-release.py')
    p.add_argument('--changed-class', action='append', default=[])
    p.add_argument('--resource', action='append', default=[], help='Explicit data file to copy unchanged from the Maven assembly')
    p.add_argument('--invalidate-cache', action='append', default=[], help='Exact generated cache file to archive and invalidate')
    p.add_argument('--static-check', type=Path, required=True, help='Successful complete offline static-data load report from tools/check-source-static-data.py')
    p.add_argument('--install', action='store_true')
    a = p.parse_args()
    build, out = a.build.resolve(), a.output.resolve()
    shared.validate_output(build); shared.validate_output(out)
    out.mkdir(parents=True, exist_ok=True)
    server = ROOT / 'target-deploy/game-server'
    game = build / 'game-server/game-server-4.8-SNAPSHOT.jar'
    build_meta.verify(build)
    plan = json.loads(a.release_plan.read_text())
    assert plan['buildManifestSha256'] == shared.sha(build / 'build-manifest.json'), 'Release plan belongs to another build'
    for attribute, key in [('resource', 'resources'), ('invalidate_cache', 'invalidateCaches'), ('changed_class', 'changedClasses')]:
        explicit = getattr(a, attribute)
        assert not explicit or set(explicit) == set(plan[key]), 'Incomplete delivery override: ' + attribute
        setattr(a, attribute, plan[key])
    assert plan['handlerCount'] > 0 and plan['handlerCompile'].startswith('passed'), 'Complete handler compilation is required'
    runtime_inputs = plan['runtimeResourceInputs']
    assert all((shared.sha(server / rel) if (server / rel).is_file() else None) == digest for rel, digest in runtime_inputs.items()), 'Runtime resources changed after release planning'
    old = server / 'libs/game-server-4.8-SNAPSHOT.jar'
    assert 'BUILD SUCCESS' in (build / 'maven.log').read_text(), 'Normal builder did not succeed'
    assert not (server / 'libs/playerbot-recruitment-fix.jar').exists(), 'Reconcile the override in source first'
    prior = sorted(shared.receipt_paths(server, 'playerbots-source-build-*/manifest.json'))[-1]
    baseline = json.loads(prior.read_text())
    assert str(prior) == plan['priorReceipt'], 'Another chat delivered a newer component; rebuild/review against it'
    assert all(shared.sha(server / e['path']) == e['installed'] for e in baseline['files'])
    assert all(shared.sha(server / e['path']) == e['installed'] for e in baseline['guardFiles'])
    assert all(not (server / rel).exists() for rel in baseline['retiredFiles'])
    launcher = server / 'start.bat'
    assert b'-cp "libs/*"' in launcher.read_bytes()
    roots = set(a.changed_class)
    comparisons = []
    with zipfile.ZipFile(old) as installed, zipfile.ZipFile(game) as built:
        assert built.testzip() is None
        names = set(installed.namelist())
        new_names = set(built.namelist())
        assert not names - new_names, 'Previously installed package entries were removed'
        added = new_names - names
        assert all((n.endswith('.class') and n[:-6].split('$')[0] in roots) or
            (n.endswith('/') and any(root.startswith(n) for root in roots)) for n in added), 'Unreviewed new package entries'
        changed = [n for n in sorted(names) if installed.read(n) != built.read(n)]
        for name in changed:
            if name == 'META-INF/MANIFEST.MF':
                continue
            assert name.endswith('.class') and name[:-6].split('$')[0] in roots, 'Unrelated source output changed: ' + name
            before, after = shared.methods(old, name[:-6]), shared.methods(game, name[:-6])
            removed = before.keys() - after.keys()
            assert all(k.startswith('private ') and 'lambda$' in k for k in removed), 'Installed methods removed: ' + name
            comparisons.append(dict(className=name[:-6], addedMethods=sorted(after.keys() - before.keys()),
                retiredUnusedSynthetics=sorted(removed),
                changedMethods=sorted(k for k in before.keys() & after.keys() if before[k] != after[k]),
                preservedMethods=sum(before[k] == after[k] for k in before.keys() & after.keys())))
        preserved = len(names) - len(changed)
        media = {n: hashlib.sha256(built.read('playerbots/media/' + n)).hexdigest() for n in ['bots.html', 'bots.css', 'bots.js']}
    linkage = out / 'linkage'; linkage.mkdir(exist_ok=True)
    subprocess.run(['javac', '-d', str(linkage), str(ROOT / 'client-mods/playerbots/java/PlayerBotLinkageCheck.java')], check=True, capture_output=True, text=True)
    commons = build / 'commons/commons-4.8-SNAPSHOT.jar'
    libs = [f for f in sorted((server / 'libs').glob('*.jar')) if f.name not in {'game-server-4.8-SNAPSHOT.jar', 'commons-4.8-SNAPSHOT.jar', 'playerbot-recruitment-fix.jar'}]
    result = subprocess.run(['java', '-Xverify:all', '-cp', ';'.join(map(str, [linkage, game, commons, *libs, server / 'cache/classes'])),
        'PlayerBotLinkageCheck', str(game)], cwd=out, check=True, capture_output=True, text=True)
    (out / 'linkage.txt').write_text(result.stdout + result.stderr)
    inventory = json.loads((ROOT / 'docs/INSTALLED_MODS.json').read_text())
    assert all(inventory['checks'].values()) and inventory['serverJarSha256'] == shared.sha(old)
    config = {str(f.relative_to(server)): shared.sha(f) for f in (server / 'config').rglob('*') if f.is_file()}
    source = {str(f.relative_to(ROOT)): shared.sha(f) for component in ['game-server', 'commons']
        for f in (ROOT / component / 'src').rglob('*') if f.is_file()}
    assert all((ROOT / rel).stat().st_mtime_ns <= game.stat().st_mtime_ns for rel in source), 'Source changed after the build; rebuild before delivery'
    files = [dict(path='libs/game-server-4.8-SNAPSHOT.jar', source=str(game), original=shared.sha(old), installed=shared.sha(game))]
    if plan['dependencyChanged']:
        current = server / 'libs/commons-4.8-SNAPSHOT.jar'
        with zipfile.ZipFile(current) as x, zipfile.ZipFile(commons) as y:
            assert not set(x.namelist()) - set(y.namelist()), 'Installed Commons entries removed'
            for name in set(x.namelist()) | set(y.namelist()):
                if name == 'META-INF/MANIFEST.MF' or name.endswith('/'): continue
                if name in x.namelist() and name in y.namelist() and x.read(name) == y.read(name): continue
                assert name.endswith('.class') and name[:-6].split('$')[0] in roots, 'Unreviewed Commons dependency change: ' + name
        files.append(dict(path='libs/commons-4.8-SNAPSHOT.jar', source=str(commons), original=shared.sha(current), installed=shared.sha(commons)))
    resource_hashes = {}
    assert 'OK: complete static XML merge, schema and production JAXB load;' in a.static_check.read_text(), 'Complete static-data compatibility must pass before delivery'
    static_evidence = json.loads(a.static_check.with_suffix('.json').read_text())
    assert static_evidence['reportSha256'] == shared.sha(a.static_check), 'Static-data report changed'
    assert static_evidence['builderJarSha256'] == shared.sha(game), 'Static data was checked against a different builder JAR'
    country = re.search(r'^\s*gameserver\.country\.code\s*=\s*(\d+)',
        (server / 'config/main/gameserver.properties').read_text(), re.MULTILINE)
    assert country and int(country.group(1)) == static_evidence['countryCode'], 'Runtime country code changed after static-data validation'
    static_inputs = static_evidence['unchangedRuntimeInputs']
    assert all(shared.sha(server / rel) == digest for rel, digest in static_inputs.items()), 'Static data changed after the isolated load'
    if a.resource:
        with zipfile.ZipFile(build / 'game-server/game-server.zip') as assembly:
            for rel in a.resource:
                assert rel.startswith('data/') and '..' not in Path(rel).parts and '\\' not in rel and not rel.startswith('data/geo/'), rel
                built_bytes = assembly.read('game-server/' + rel)
                source_path = ROOT / 'game-server' / rel
                assert built_bytes == source_path.read_bytes(), 'Resource changed after build: ' + rel
                if rel.startswith('data/static_data/'):
                    assert hashlib.sha256(built_bytes).hexdigest() == static_evidence['builderResources'][rel], 'Checked resource differs from builder output'
                dest = out / 'builder-resources' / rel; dest.parent.mkdir(parents=True, exist_ok=True); dest.write_bytes(built_bytes)
                resource_hashes[str(source_path.relative_to(ROOT))] = shared.sha(source_path)
                target = server / rel
                files.append(dict(path=rel, source=str(dest), original=shared.sha(target) if target.is_file() else None, installed=shared.sha(dest)))
    caches = []
    for rel in a.invalidate_cache:
        assert rel.startswith('cache/') and '..' not in Path(rel).parts and '\\' not in rel, rel
        target = server / rel
        if target.is_file(): caches.append(dict(path=rel, original=shared.sha(target)))
    replaced = {e['path'] for e in files} | {e['path'] for e in caches}
    guard_files = [e for e in baseline['files'] + baseline['guardFiles'] if e['path'] not in replaced]
    manifest = dict(feature='playerbots-source-build', scope=a.scope, deployment=str(server), previousReceipt=str(prior),
        builder='tools/build-components.ps1 normal Maven output; copied unchanged', files=files, guardFiles=guard_files,
        retiredFiles=baseline['retiredFiles'], invalidatedCaches=caches, resourceSourceHashes=resource_hashes,
        bundledMediaSha256=media, configFiles=config, sourceHashes=source,
        clientFiles=inventory['clientFiles'], geometrySha256=shared.sha(server / 'data/geo/models.mesh'),
        staticDataInputs=static_inputs,
        runtimeResourceInputs=runtime_inputs, releasePlanSha256=shared.sha(a.release_plan),
        buildManifestSha256=shared.sha(build / 'build-manifest.json'), handlerCount=plan['handlerCount'],
        classComparison=comparisons or [dict(sourceBuiltBaselinePreserved=True, previousReceipt=str(prior))],
        preservedJarEntries=preserved, fullRuntimeLinkage=result.stdout.strip(), gameAcceptance='pending user testing')
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2))
    if not a.install:
        print('OK: reviewed normal builder output;', preserved, 'unchanged entries;', result.stdout.strip()); return

    def guard():
        stopped()
        build_meta.verify(build)
        assert shared.sha(a.release_plan) == manifest['releasePlanSha256']
        assert shared.receipt_paths(server, 'playerbots-source-build-*/manifest.json')[-1] == prior, 'Another release completed after review'
        for e in files:
            target = server / e['path']
            assert (shared.sha(target) if target.is_file() else None) == e['original'] and shared.sha(Path(e['source'])) == e['installed']
        assert all(shared.sha(server / e['path']) == e['original'] for e in caches)
        assert all(shared.sha(server / e['path']) == e['installed'] for e in guard_files)
        assert all(shared.sha(server / rel) == digest for rel, digest in config.items())
        assert all(shared.sha(ROOT / rel) == digest for rel, digest in source.items())
        assert all(shared.sha(ROOT / rel) == digest for rel, digest in resource_hashes.items())
        assert all(shared.sha(server / rel) == digest for rel, digest in static_inputs.items())
        assert all((shared.sha(server / rel) if (server / rel).is_file() else None) == digest for rel, digest in runtime_inputs.items())
        assert shared.sha(server / 'data/geo/models.mesh') == manifest['geometrySha256']
        assert all(shared.sha(shared.CLIENT_ROOT / e['path']) == e['sha256'] for e in manifest['clientFiles'])
        assert all(not (server / rel).exists() for rel in manifest['retiredFiles'])

    guard()
    backup = shared.DEV_ROOT / 'archives/server/game-server/backups' / ('playerbots-source-build-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    backup.mkdir(parents=True, exist_ok=False)
    for e in files + caches:
        if e['original'] is None: continue
        dest = backup / e['path']; dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(server / e['path'], dest)
        assert shared.sha(dest) == e['original']
    guard()
    try:
        for e in files:
            target = server / e['path']; target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(e['source'], target)
            assert shared.sha(target) == e['installed']
        for e in caches: (server / e['path']).unlink()
        assert all(shared.sha(server / e['path']) == e['installed'] for e in guard_files)
        assert all(shared.sha(server / rel) == digest for rel, digest in config.items())
        assert all(shared.sha(shared.CLIENT_ROOT / e['path']) == e['sha256'] for e in manifest['clientFiles'])
    except Exception:
        for e in files + caches:
            target = server / e['path']
            if e['original'] is None:
                if target.exists(): target.unlink()
            else:
                shutil.copy2(backup / e['path'], target)
                assert shared.sha(target) == e['original']
        (backup / 'failed-manifest.json').write_text(json.dumps(manifest, indent=2)); raise
    (backup / 'manifest.json').write_text(json.dumps(manifest, indent=2))
    (backup / 'installed.json').write_text(json.dumps(dict(installedAt=datetime.now().isoformat(), mode='offline source builder output; no startup/attach', files=files), indent=2))
    shutil.copy2(out / 'linkage.txt', backup / 'linkage.txt')
    shutil.copy2(a.release_plan, backup / 'release-plan.json')
    shutil.copy2(build / 'build-manifest.json', backup / 'build-manifest.json')
    if a.static_check: shutil.copy2(a.static_check, backup / 'static-data-load.txt')
    print('OK: complete unchanged Maven GameServer output delivered; receipt:', backup)


if __name__ == '__main__':
    main()
