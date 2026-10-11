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

FEATURE_CONFIGURATION = 'config/playerbots/features.properties'


def initial_feature_configuration(target):
    """Deliver the builder default once; retain every existing owner's choice."""
    assert not target.exists() or target.is_file(), 'Feature configuration path is not a regular file: ' + str(target)
    return not target.exists()


def verify_original_resource(target, original):
    """A missing baseline means genuine absence, never a directory or link."""
    if original is None:
        assert not target.exists() and not target.is_symlink(), 'Expected absent resource: ' + str(target)
    else:
        assert target.is_file() and shared.sha(target) == original, 'Resource baseline changed: ' + str(target)


def allowed_resource(rel):
    return ('..' not in Path(rel).parts and '\\' not in rel
        and (rel == FEATURE_CONFIGURATION or rel.startswith('data/') and not rel.startswith('data/geo/')))


def retained_methods(before, after):
    """ACC_SYNCHRONIZED controls locking, not a JVM method name/descriptor.

    Keep every other qualifier, name, return type and parameter type exact. The
    report still records modifier changes and compares their bytecode normally.
    """
    identity = lambda key: re.sub(r'\bsynchronized\s+', '', key)
    by_identity = {identity(key): key for key in after}
    assert len(by_identity) == len(after), 'Ambiguous method identity'
    return {key: by_identity[identity(key)] for key in before if identity(key) in by_identity}


def bundled_media_source(name, data):
    """Allow only existing menu media copied byte-for-byte by the normal builder."""
    allowed = {'playerbots/media/' + file for file in ('bots.html', 'bots.css', 'bots.js')}
    if name not in allowed:
        return None
    source = ROOT / 'game-server/config' / name
    assert source.is_file() and source.read_bytes() == data, 'Menu output differs from maintained source: ' + name
    return str(source.relative_to(ROOT))


def validate_companion_configuration(before, after, previous, configured, previous_roster, configured_roster):
    """Accept only explicit backed-up quota changes; retain every other config byte."""
    pattern = rb'(?m)^(gameserver\.playerbots\.(max_per_owner|generated\.max_per_account)\s*=\s*)([0-9]+)([ \t]*\r?$)'
    read = lambda data: [(m.group(2), int(m.group(3))) for m in re.finditer(pattern, data)]
    assert 0 <= configured <= 23 and configured <= configured_roster <= 2147483647, 'Invalid native companion quota'
    assert sorted(read(before)) == sorted([(b'max_per_owner', previous), (b'generated.max_per_account', previous_roster)]), 'Previous quota differs from configuration review'
    assert sorted(read(after)) == sorted([(b'max_per_owner', configured), (b'generated.max_per_account', configured_roster)]), 'Configured quota differs from review'
    assert re.sub(pattern, b'<companion-quota>', before) == re.sub(pattern, b'<companion-quota>', after), 'Unrelated configuration changed'


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
    p.add_argument('--config-review', type=Path, help='Explicit backed-up companion quota configuration receipt')
    a = p.parse_args()
    build, out = a.build.resolve(), a.output.resolve()
    shared.validate_output(build); shared.validate_output(out)
    out.mkdir(parents=True, exist_ok=True)
    server = ROOT / 'target-deploy/game-server'
    game = build / 'game-server/game-server-4.8-SNAPSHOT.jar'
    build_meta.verify(build)
    regression = a.release_plan.resolve().parent / 'regression'
    import_module('check-source-build').validate(build, regression)
    browser = a.release_plan.resolve().parent / 'browser'
    import_module('check-source-browser').validate(build, browser)
    plan = json.loads(a.release_plan.read_text())
    assert plan['buildManifestSha256'] == shared.sha(build / 'build-manifest.json'), 'Release plan belongs to another build'
    for attribute, key in [('resource', 'resources'), ('invalidate_cache', 'invalidateCaches'), ('changed_class', 'changedClasses')]:
        explicit = getattr(a, attribute)
        assert not explicit or set(explicit) == set(plan[key]), 'Incomplete delivery override: ' + attribute
        setattr(a, attribute, plan[key])
    assert plan['handlerCount'] > 0 and plan['handlerCompile'].startswith('passed'), 'Complete handler compilation is required'
    runtime_inputs = plan['runtimeResourceInputs']
    for rel, digest in runtime_inputs.items():
        if rel == FEATURE_CONFIGURATION:
            initial_feature_configuration(server / rel)
        verify_original_resource(server / rel, digest)
    old = server / 'libs/game-server-4.8-SNAPSHOT.jar'
    assert 'BUILD SUCCESS' in (build / 'maven.log').read_text(), 'Normal builder did not succeed'
    assert not (server / 'libs/playerbot-recruitment-fix.jar').exists(), 'Reconcile the override in source first'
    prior = sorted(shared.receipt_paths(server, 'playerbots-source-build-*/manifest.json'))[-1]
    baseline = json.loads(prior.read_text())
    assert str(prior) == plan['priorReceipt'], 'Another chat delivered a newer component; rebuild/review against it'
    configuration_changes = []
    if a.config_review:
        a.config_review = a.config_review.resolve(); shared.validate_output(a.config_review)
        evidence = json.loads(a.config_review.read_text())
        rel = 'config/main/playerbots.properties'
        assert evidence['feature'] in {'playerbots-active-limit','playerbots-field-placement','playerbots-user-population-limits'} and Path(evidence['path']).resolve() == (server / rel).resolve()
        if evidence['feature']=='playerbots-user-population-limits':
            matches = [e for e in baseline['guardFiles'] if e['path'] == rel]
            assert len(matches)==1
            reviewed = import_module('review-playerbot-population-config').verified(a.config_review,server,matches[0]['installed'])
            baseline['guardFiles'] = [dict(e, installed=reviewed['installed']) if e['path']==rel else e for e in baseline['guardFiles']]
            configuration_changes.append(reviewed)
        else:
            original = a.config_review.parent / 'playerbots.properties'
            assert shared.sha(original) == evidence['original'] and shared.sha(server / rel) == evidence['installed']
            field = evidence['feature']=='playerbots-field-placement'
            if field:
                assert a.scope=='PB-REPAIR-FIELD-015' and evidence['configuredValue']==0
                import_module('configure-playerbot-field').validate(original.read_bytes(), (server / rel).read_bytes(), evidence['previousValue'])
            else:
                validate_companion_configuration(original.read_bytes(), (server / rel).read_bytes(), evidence['previousValue'], evidence['configuredValue'], evidence['previousRoster'], evidence['configuredRoster'])
            matches = [e for e in baseline['guardFiles'] if e['path'] == rel]
            assert len(matches) == 1 and matches[0]['installed'] == evidence['original'], 'Configuration review belongs to another installed baseline'
            baseline['guardFiles'] = [dict(e, installed=evidence['installed']) if e['path'] == rel else e for e in baseline['guardFiles']]
            configuration_changes.append(dict(path=rel, original=evidence['original'], installed=evidence['installed'],
                review=str(a.config_review), reviewSha256=shared.sha(a.config_review), recovery=str(original),
                previousValue=evidence['previousValue'], configuredValue=evidence['configuredValue'], fieldCombat=field,
                **({} if field else dict(previousRoster=evidence['previousRoster'], configuredRoster=evidence['configuredRoster']))))
    # Only exact reviewed config changes are recognized; other guards stay enabled.
    rel = 'config/main/playerbots.properties'
    config_guards = [e for e in baseline['guardFiles'] if e['path'] == rel]
    if len(config_guards) == 1 and shared.sha(server / rel) != config_guards[0]['installed']:
        documentation = import_module('document-playerbot-config').verified_receipt(server, shared.DEV_ROOT / 'archives/server/game-server/backups')
        baseline['guardFiles'] = [dict(e, installed=documentation['installed']) if e['path'] == rel else e for e in baseline['guardFiles']]
        configuration_changes.append(documentation)
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
            source = bundled_media_source(name, built.read(name))
            if source is not None:
                comparisons.append(dict(resourceName=name, source=source,
                    previousSha256=hashlib.sha256(installed.read(name)).hexdigest(),
                    installedSha256=hashlib.sha256(built.read(name)).hexdigest()))
                continue
            assert name.endswith('.class') and name[:-6].split('$')[0] in roots, 'Unrelated source output changed: ' + name
            before, after = shared.methods(old, name[:-6]), shared.methods(game, name[:-6])
            matched = retained_methods(before, after)
            removed = before.keys() - matched.keys()
            assert all(k.startswith('private ') and 'lambda$' in k for k in removed), 'Installed methods removed: ' + name
            comparisons.append(dict(className=name[:-6], addedMethods=sorted(after.keys() - set(matched.values())),
                retiredUnusedSynthetics=sorted(removed),
                synchronizationChanges=sorted(k+' -> '+v for k,v in matched.items() if k!=v),
                changedMethods=sorted(k for k,v in matched.items() if k!=v or before[k] != after[v]),
                preservedMethods=sum(k==v and before[k] == after[v] for k,v in matched.items())))
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
    assert static_evidence['handlerInventoryToolSha256'] == shared.sha(ROOT / 'tools/quest-handler-inventory.py'), 'Native quest inventory tool changed'
    assert static_evidence['reportSha256'] == shared.sha(a.static_check), 'Static-data report changed'
    assert static_evidence['builderJarSha256'] == shared.sha(game), 'Static data was checked against a different builder JAR'
    coverage = a.static_check.parent / 'quest-coverage.json'
    assert shared.sha(coverage) == static_evidence['questCoverageSha256'], 'Native quest coverage evidence changed'
    country = re.search(r'^\s*gameserver\.country\.code\s*=\s*(\d+)',
        (server / 'config/main/gameserver.properties').read_text(), re.MULTILINE)
    assert country and int(country.group(1)) == static_evidence['countryCode'], 'Runtime country code changed after static-data validation'
    static_inputs = static_evidence['unchangedRuntimeInputs']
    assert all(shared.sha(server / rel) == digest for rel, digest in static_inputs.items()), 'Static data changed after the isolated load'
    if a.resource:
        with zipfile.ZipFile(build / 'game-server/game-server.zip') as assembly:
            for rel in a.resource:
                assert allowed_resource(rel), rel
                if rel == FEATURE_CONFIGURATION:
                    assert initial_feature_configuration(server / rel), 'Preserve existing owner feature configuration: ' + rel
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
        clientFiles=import_module('verify-source-delivery').protected_client_files(inventory['clientFiles']), geometrySha256=shared.sha(server / 'data/geo/models.mesh'),
        staticDataInputs=static_inputs,
        runtimeResourceInputs=runtime_inputs, releasePlanSha256=shared.sha(a.release_plan),
        buildManifestSha256=shared.sha(build / 'build-manifest.json'), handlerCount=plan['handlerCount'],
        classComparison=comparisons or [dict(sourceBuiltBaselinePreserved=True, previousReceipt=str(prior))],
        preservedJarEntries=preserved, fullRuntimeLinkage=result.stdout.strip(), gameAcceptance='pending user testing')
    manifest['requestedConfigurationChanges'] = configuration_changes
    manifest['regressionEvidenceSha256'] = shared.sha(regression / 'evidence.json')
    manifest['browserEvidenceSha256'] = shared.sha(browser / 'evidence.json')
    manifest['questCoverageSha256'] = shared.sha(coverage)
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2))
    if not a.install:
        print('OK: reviewed normal builder output;', preserved, 'unchanged entries;', result.stdout.strip()); return

    def guard():
        stopped()
        build_meta.verify(build)
        import_module('check-source-build').validate(build, regression)
        assert shared.sha(regression / 'evidence.json') == manifest['regressionEvidenceSha256']
        import_module('check-source-browser').validate(build, browser)
        assert shared.sha(browser / 'evidence.json') == manifest['browserEvidenceSha256']
        assert shared.sha(coverage) == manifest['questCoverageSha256']
        assert shared.sha(a.release_plan) == manifest['releasePlanSha256']
        assert shared.receipt_paths(server, 'playerbots-source-build-*/manifest.json')[-1] == prior, 'Another release completed after review'
        for e in files:
            target = server / e['path']
            if e['path'] == FEATURE_CONFIGURATION:
                initial_feature_configuration(target)
            verify_original_resource(target, e['original'])
            assert shared.sha(Path(e['source'])) == e['installed']
        assert all(shared.sha(server / e['path']) == e['original'] for e in caches)
        assert all(shared.sha(server / e['path']) == e['installed'] for e in guard_files)
        assert all(shared.sha(server / rel) == digest for rel, digest in config.items())
        for change in configuration_changes:
            assert shared.sha(Path(change['review'])) == change['reviewSha256']
            assert shared.sha(Path(change['recovery'])) == change['original'] and shared.sha(server / change['path']) == change['installed']
        assert all(shared.sha(ROOT / rel) == digest for rel, digest in source.items())
        assert all(shared.sha(ROOT / rel) == digest for rel, digest in resource_hashes.items())
        assert all(shared.sha(server / rel) == digest for rel, digest in static_inputs.items())
        for rel, digest in runtime_inputs.items():
            if rel == FEATURE_CONFIGURATION:
                initial_feature_configuration(server / rel)
            verify_original_resource(server / rel, digest)
        assert shared.sha(server / 'data/geo/models.mesh') == manifest['geometrySha256']
        import_module('verify-source-delivery').verify_client_files(shared.CLIENT_ROOT, manifest['clientFiles'])
        assert all(not (server / rel).exists() for rel in manifest['retiredFiles'])

    guard()
    backup = shared.DEV_ROOT / 'archives/server/game-server/backups' / ('playerbots-source-build-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    backup.mkdir(parents=True, exist_ok=False)
    for e in files + caches:
        if e['original'] is None: continue
        dest = backup / e['path']; dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(server / e['path'], dest)
        assert shared.sha(dest) == e['original']
    for change in configuration_changes:
        dest = backup / change['path']; dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(change['recovery'], dest)
        assert shared.sha(dest) == change['original']
    guard()
    shutil.copytree(regression, backup / 'regression')
    assert shared.sha(backup / 'regression/evidence.json') == manifest['regressionEvidenceSha256']
    shutil.copytree(browser, backup / 'browser')
    assert shared.sha(backup / 'browser/evidence.json') == manifest['browserEvidenceSha256']
    try:
        for e in files:
            target = server / e['path']; target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(e['source'], target)
            assert shared.sha(target) == e['installed']
        for e in caches: (server / e['path']).unlink()
        assert all(shared.sha(server / e['path']) == e['installed'] for e in guard_files)
        assert all(shared.sha(server / rel) == digest for rel, digest in config.items())
        import_module('verify-source-delivery').verify_client_files(shared.CLIENT_ROOT, manifest['clientFiles'])
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
    shutil.copy2(coverage, backup / 'quest-coverage.json')
    if a.static_check: shutil.copy2(a.static_check, backup / 'static-data-load.txt')
    print('OK: complete unchanged Maven GameServer output delivered; receipt:', backup)


if __name__ == '__main__':
    main()
