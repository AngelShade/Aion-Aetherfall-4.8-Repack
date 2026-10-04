"""Mirror only the hash-verified five camp repairs into source static data."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
plan = json.loads((ROOT / 'target/base-camps/package/camp-data-changes.json').read_text())
pending = []
for relative, hashes in plan['files'].items():
    source = ROOT / 'game-server' / relative
    deployed = ROOT / 'target-deploy/game-server' / relative
    payload = deployed.read_bytes()
    assert hashlib.sha256(payload).hexdigest() == hashes['installed'], relative
    current = hashlib.sha256(source.read_bytes()).hexdigest()
    assert current in (hashes['original'], hashes['installed']), ('Source differs; preserve and merge manually', relative)
    if current != hashes['installed']:
        pending.append((source, payload))
for source, payload in pending:
    source.write_bytes(payload)
print('OK:', len(pending), 'exact verified XML repairs mirrored into source.')
