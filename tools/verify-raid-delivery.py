"""Read-only receipt verification; no game, database or process attachment."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEV = Path('D:/Proiecte/Project Restructure/Aion Development Workspace')
BACKUP = DEV / 'archives/server/game-server/backups/playerbots-source-build-20261010-031529-868535'
REVIEW = DEV / 'staging/output/release-20261010-030907-577836'

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    m = json.loads((BACKUP / 'manifest.json').read_text())
    server = Path(m['deployment'])
    for e in m['files']:
        assert sha(server / e['path']) == sha(Path(e['source'])) == e['installed']
        assert sha(BACKUP / e['path']) == e['original']
    for e in m['guardFiles']:
        assert sha(server / e['path']) == e['installed'], e['path']
    # These three native persistence files were saved after delivery during user testing.
    # Report their new hashes; never restore historical world progress.
    mutable = {'config/playerbots/world-bots.properties', 'config/playerbots/world-population-events.json',
               'config/playerbots/world-server-population.json'}
    later_saves = []
    for rel, digest in m['configFiles'].items():
        if sha(server / rel) != digest:
            assert rel.replace('\\', '/') in mutable, rel
            assert (server / rel).stat().st_mtime > (BACKUP / 'installed.json').stat().st_mtime, rel
            later_saves.append({'path': rel, 'sha256': sha(server / rel)})
    previous = json.loads(Path(m['previousReceipt']).read_text())
    differences = [rel for rel, digest in m['configFiles'].items() if previous['configFiles'][rel] != digest]
    assert [p.replace('\\', '/') for p in differences] == ['config/main/playerbots.properties'], differences
    from importlib.util import spec_from_file_location, module_from_spec
    spec = spec_from_file_location('delivery', ROOT / 'tools/deliver-source-component.py')
    delivery = module_from_spec(spec)
    spec.loader.exec_module(delivery)
    for change in m['requestedConfigurationChanges']:
        assert sha(Path(change['review'])) == change['reviewSha256']
        assert sha(Path(change['recovery'])) == sha(BACKUP / change['path']) == change['original']
        assert sha(server / change['path']) == change['installed']
        delivery.validate_companion_configuration(Path(change['recovery']).read_bytes(), (server / change['path']).read_bytes(),
            change['previousValue'], change['configuredValue'], change['previousRoster'], change['configuredRoster'])
    for rel, digest in m['sourceHashes'].items():
        assert sha(ROOT / rel) == digest, rel
    for rel, digest in m['staticDataInputs'].items():
        assert sha(server / rel) == digest, rel
    for rel, digest in m['runtimeResourceInputs'].items():
        assert (sha(server / rel) if (server / rel).is_file() else None) == digest, rel
    inventory = json.loads((ROOT / 'docs/INSTALLED_MODS.json').read_text())
    assert all(inventory['checks'].values())
    assert inventory['serverJarSha256'] == m['files'][0]['installed']
    later_client_settings = []
    for e in m['clientFiles']:
        path = Path(inventory['clientRoot']) / e['path']
        if sha(path) != e['sha256']:
            assert e['path'] == 'system.cfg' and path.stat().st_mtime > (BACKUP / 'installed.json').stat().st_mtime
            later_client_settings.append({'path': e['path'], 'sha256': sha(path)})
    for e in inventory['clientFiles']:
        assert sha(Path(inventory['clientRoot']) / e['path']) == e['sha256']
    assert sha(server / 'data/geo/models.mesh') == m['geometrySha256']
    assert all(not (server / rel).exists() for rel in m['retiredFiles'])
    assert sha(BACKUP / 'release-plan.json') == m['releasePlanSha256']
    assert sha(BACKUP / 'build-manifest.json') == m['buildManifestSha256']
    refs = ROOT / 'third-party/playerbots/scope-references'
    manifest = json.loads((refs / 'manifest.json').read_text())
    blobs = 0
    for e in manifest['references']:
        raw = (refs / e['path']).read_bytes()
        assert hashlib.sha256(raw).hexdigest() == e['sha256'], e['path']
        if e.get('gitBlob'):
            assert hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() == e['gitBlob'], e['path']
            blobs += 1
    browser = json.loads((DEV / 'diagnostics/raid-20261010/menu-check/checks.json').read_text())
    assert browser['checks'] == 49 and not browser['errors'] and browser['nativeGameServerContact'] is False
    print('OK: source/artifact/recovery, 70 guards, 93 unchanged configs plus three later native world saves, 30 preserved client hashes plus later system.cfg, static data and retired override')
    print('Later native saves preserved:', json.dumps(later_saves))
    print('Later client settings preserved:', json.dumps(later_client_settings))
    print(f"OK: {len(manifest['references'])} pinned SHA references / {blobs} Git blobs; 49 simulated-response browser checks")
    print(f"OK: {len(inventory['checks'])} installed-mod checks / {len(inventory['clientReceipts'])} client / {len(inventory['serverReceipts'])} server receipts")

if __name__ == '__main__':
    main()
