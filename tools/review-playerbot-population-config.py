"""Record existing user population-limit edits without writing runtime configuration."""
import argparse, importlib, json, re, shutil, sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'client-mods/playerbots'))
import stage_companion_update as shared
PATTERN = rb'(?m)^(gameserver\.playerbots\.(world\.max_active|population\.min|population\.max)[ \t]*=[ \t]*)([0-9]+)([ \t]*\r?$)'

def validate(before, after):
    def values(data):
        pairs = [(m.group(2).decode(), int(m.group(3))) for m in re.finditer(PATTERN, data)]
        assert len(pairs) == len(dict(pairs)) == 3, 'Population limits missing or duplicated'
        result = dict(pairs)
        assert 0 <= result['population.min'] <= result['population.max'] <= 3000
        assert 1 <= result['world.max_active'] <= 3000
        return result
    previous, current = values(before), values(after)
    assert re.sub(PATTERN, b'<population-limit>', before) == re.sub(PATTERN, b'<population-limit>', after), 'Unrelated live configuration changed'
    return previous, current

def verified(path, server, baseline_hash):
    shared.validate_output(path)
    m = json.loads(path.read_text())
    live = importlib.import_module('playerbot-config-paths').active(server)
    assert server.resolve() == (ROOT / 'target-deploy/game-server').resolve()
    assert m['feature'] == 'playerbots-user-population-limits' and Path(m['path']).resolve() == live.resolve()
    assert m['baselineSha256'] == baseline_hash and shared.sha(Path(m['baseline'])) == baseline_hash
    assert m['jarSha256'] == shared.sha(server / 'libs/game-server-4.8-SNAPSHOT.jar')
    original = path.parent / 'playerbots.properties'
    assert shared.sha(original) == m['installed'] == shared.sha(live)
    previous, current = validate(Path(m['baseline']).read_bytes(), live.read_bytes())
    assert previous == m['previous'] and current == m['current']
    return dict(path=importlib.import_module('playerbot-config-paths').active(server).relative_to(server).as_posix(), original=m['installed'], installed=m['installed'],
        review=str(path), reviewSha256=shared.sha(path), recovery=str(original), userEditsOnly=True,
        baseline=m['baseline'], baselineSha256=baseline_hash, previous=previous, current=current)

def main():
    server = ROOT / 'target-deploy/game-server'
    prior = shared.receipt_paths(server, 'playerbots-source-build-*/manifest.json')[-1]
    installed = json.loads(prior.read_text())
    expected = next(e['installed'] for e in installed['guardFiles'] if e['path'] == importlib.import_module('playerbot-config-paths').active(server).relative_to(server).as_posix())
    archives = shared.DEV_ROOT / 'archives/server/game-server/backups'
    sources = [Path(json.loads(p.read_text())['staged']) for p in archives.glob('playerbots-config-docs-*/manifest.json')]
    baseline = next(p for p in sources if p.is_file() and shared.sha(p) == expected)
    live = importlib.import_module('playerbot-config-paths').active(server)
    previous, current = validate(baseline.read_bytes(), live.read_bytes())
    review = shared.DEV_ROOT / 'staging/output' / ('playerbots-user-population-limits-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    review.mkdir(parents=True, exist_ok=False)
    shutil.copy2(live, review / 'playerbots.properties')
    manifest = dict(feature='playerbots-user-population-limits', path=str(live), baseline=str(baseline),
        baselineSha256=expected, installed=shared.sha(live), jarSha256=shared.sha(server / 'libs/game-server-4.8-SNAPSHOT.jar'),
        previous=previous, current=current, runtimeWritten=False)
    path = review / 'manifest.json'; path.write_text(json.dumps(manifest, indent=2))
    verified(path, server, expected)
    print('OK: existing user edits backed up and verified; no runtime write:', current)
    print('Review:', path)

if __name__ == '__main__': main()
