"""Read-only verification of an exact installed source-built release and its recovery."""
import argparse
import hashlib
import importlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def protected_client_files(entries):
    """The client regenerates root system.cfg; it is not an installed mod."""
    return [entry for entry in entries if entry['path'].replace('\\', '/').casefold() != 'system.cfg']


def verify_client_files(client, entries):
    for entry in protected_client_files(entries):
        assert sha(Path(client) / entry['path']) == entry['sha256'], entry['path']


def verify_runtime_resource_inputs(server, manifest):
    replacements = {entry['path']: entry for entry in manifest['files']}
    assert len(replacements) == len(manifest['files']), 'Duplicate delivered file'
    for rel, original in manifest['runtimeResourceInputs'].items():
        expected = original
        if rel in replacements:
            # Inputs describe the pre-install baseline. Only a recorded builder
            # replacement may change it; main separately verifies source/backup.
            entry = replacements[rel]
            assert entry['original'] == original, rel
            expected = entry['installed']
        target = server / rel
        assert (sha(target) if target.is_file() else None) == expected, rel


def main():
    if not __debug__:
        raise RuntimeError('Verification cannot run with Python optimization enabled')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--receipt', type=Path, required=True)
    parser.add_argument('--review', type=Path, required=True)
    parser.add_argument('--documentation', type=Path, help='Exact subsequent value-preserving configuration documentation receipt')
    args = parser.parse_args()
    receipt, review = args.receipt.resolve(), args.review.resolve()
    m = json.loads((receipt / 'manifest.json').read_text())
    assert (receipt / 'installed.json').is_file(), 'Release was not installed'
    server = Path(m['deployment'])
    assert server.resolve() == ROOT / 'target-deploy/game-server'
    build = Path(json.loads((review / 'release-context.json').read_text())['build'])
    static_evidence = json.loads((review / 'static-check/static-data-load.json').read_text())
    if 'handlerInventoryToolSha256' in static_evidence:
        assert sha(ROOT / 'tools/quest-handler-inventory.py') == static_evidence['handlerInventoryToolSha256'], 'Native quest inventory tool changed'
    sys.path.insert(0, str(ROOT / 'tools'))
    importlib.import_module('build-manifest').verify(build)
    documentation = None
    if args.documentation:
        docs = importlib.import_module('document-playerbot-config')
        sys.path.insert(0, str(ROOT / 'client-mods/playerbots'))
        shared = importlib.import_module('stage_companion_update')
        documentation = docs.verified_receipt(server, shared.DEV_ROOT / 'archives/server/game-server/backups')
        assert Path(documentation['review']).resolve() == (args.documentation / 'manifest.json').resolve()
        rel = documentation['path']
        assert next(e['installed'] for e in m['guardFiles'] if e['path']==rel) == documentation['original']
        assert next(digest for path,digest in m['configFiles'].items() if path.replace('\\','/')==rel) == documentation['original']
    def expected_configuration(rel, digest):
        if documentation and rel.replace('\\','/') == documentation['path']:
            assert digest == documentation['original']
            return documentation['installed']
        return digest
    for e in m['files']:
        assert sha(server / e['path']) == sha(e['source']) == e['installed'], e['path']
        if e['original'] is not None:
            assert sha(receipt / e['path']) == e['original'], e['path']
    for e in m['guardFiles']:
        assert sha(server / e['path']) == expected_configuration(e['path'],e['installed']), e['path']
    for key, base in [('configFiles', server), ('sourceHashes', ROOT),
                      ('resourceSourceHashes', ROOT), ('staticDataInputs', server)]:
        for rel, digest in m[key].items():
            assert sha(base / rel) == (expected_configuration(rel,digest) if key=='configFiles' else digest), rel
    verify_runtime_resource_inputs(server, m)
    inventory = json.loads((ROOT / 'docs/INSTALLED_MODS.json').read_text())
    assert all(inventory['checks'].values())
    verify_client_files(inventory['clientRoot'], m['clientFiles'])
    assert sha(server / 'data/geo/models.mesh') == m['geometrySha256']
    for rel in m['retiredFiles']:
        assert not (server / rel).exists(), rel
    if 'populationImport' in m:
        migration = m['populationImport']; recovery = Path(migration['recovery'])
        assert (recovery / 'committed.json').is_file() and (recovery / 'retired.json').is_file()
        for row in migration['files']:
            assert sha(recovery / row['path']) == row['sha256']
            assert not (server / 'config/playerbots' / row['path']).exists()
        assert sha(recovery / 'playerbot_population.sql') == migration['schemaSha256']
        assert sha(recovery / 'database-before-retirement.txt') == migration['databaseVerificationSha256']
    for e in m['invalidatedCaches']:
        assert not (server / e['path']).exists(), e['path']
        assert sha(receipt / e['path']) == e['original'], e['path']
    for name, digest in [('release-plan.json', m['releasePlanSha256']),
                         ('build-manifest.json', m['buildManifestSha256'])]:
        assert sha(receipt / name) == digest
    for change in m['requestedConfigurationChanges']:
        assert sha(change['review']) == change['reviewSha256']
        assert sha(change['recovery']) == sha(receipt / change['path']) == change['original']
        assert sha(server / change['path']) == expected_configuration(change['path'],change['installed'])
        reviewed_config = Path(documentation['recovery']) if documentation and change['path']==documentation['path'] else server / change['path']
        if change.get('userEditsOnly'):
            assert sha(change['baseline']) == change['baselineSha256']
            previous, current = importlib.import_module('review-playerbot-population-config').validate(Path(change['baseline']).read_bytes(), reviewed_config.read_bytes())
            assert previous == change['previous'] and current == change['current']
        if change.get('fieldCombat'):
            assert m['scope']=='PB-REPAIR-FIELD-015' and change['configuredValue']==0
            importlib.import_module('configure-playerbot-field').validate(Path(change['recovery']).read_bytes(), (server / change['path']).read_bytes(), change['previousValue'])
        if change.get('documentationOnly'):
            docs = importlib.import_module('document-playerbot-config')
            values = docs.properties(reviewed_config.read_text())
            record = json.loads(Path(change['review']).read_text())
            assert values == record['effective'] == record['addedCompiledDefaults'] | record['preservedExplicit']
            assert len(values) == record['settings'] and values['gameserver.playerbots.allowed_instance_maps'] == ''
    checks = json.loads((review / 'regression/checks.json').read_text())
    if 'browserEvidenceSha256' in m:
        assert sha(receipt / 'browser/evidence.json') == sha(review / 'browser/evidence.json') == m['browserEvidenceSha256']
        importlib.import_module('check-source-browser').validate(build, receipt / 'browser')
        print('OK: 43 native-browser actions and exact builder media/renderer/visual evidence verified')
    if 'regressionEvidenceSha256' in m:
        assert sha(receipt / 'regression/evidence.json') == sha(review / 'regression/evidence.json') == m['regressionEvidenceSha256']
        importlib.import_module('check-source-build').validate(build, receipt / 'regression')
        assert sha(receipt / 'quest-coverage.json') == sha(review / 'static-check/quest-coverage.json') == m['questCoverageSha256']
    else:
        print('Legacy receipt: regression reports are not artifact-bound; no new installation is allowed with this evidence.')
        # Historical suites predate the current registry. Check their actual
        # archived reports without claiming current coverage or exit evidence.
        assert checks and all(line in (review/'regression'/(name+'.txt')).read_text().splitlines() for name,line in checks.items())
    counts = [int(line.split()[1]) for line in checks.values()]
    if 'regressionEvidenceSha256' in m:
        expected = importlib.import_module('check-source-build').TESTS
        assert set(checks) == set(expected) | {'ShutdownHookCheck'}
        assert sum(counts) == sum(expected.values()) + 14
    else:
        assert all(line.startswith('OK: ') and count>0 for line,count in zip(checks.values(),counts))
    print('OK:', len(m['files']), 'unchanged builder files;', len(m['guardFiles']),
          'guards;', len(m['configFiles']), 'configs;', len(m['clientFiles']), 'client hashes;',
          len(m['invalidatedCaches']), 'backed-up invalidated caches; recovery/source/static/linkage receipts verified')
    print('OK:', len(checks), 'offline suites /', sum(counts), 'checks; no startup/attach/world/DB/IDs')
    print('GameServer SHA-256:', sha(server / 'libs/game-server-4.8-SNAPSHOT.jar'))


if __name__ == '__main__':
    main()
