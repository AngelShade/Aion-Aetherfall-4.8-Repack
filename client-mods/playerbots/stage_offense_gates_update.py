"""PB-PORT-001/002: transplant only final cast and resource usefulness gates."""
import argparse
import json
import shutil
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared

# Existing helper and nested-action methods require explicit runtime scopes.
# Keep the legacy ClassCombat and every cached enum/synthetic member intact.
base.SCOPES = {
    shared.PREFIX + 'services/playerbot/PlayerBotSession$CastAction': {'isUseful'},
    shared.PREFIX + 'services/playerbot/PlayerBotOffense': {'useful'},
    'playercommands/Bot': set(),
}
base.HELPERS = set()

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classes', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    out = args.output.resolve()
    base.stage(args.classes.resolve(), out)
    server = shared.ROOT / 'target-deploy/game-server'
    shutil.copy2(server / 'data/handlers/playercommands/Bot.java', out / 'data/handlers/playercommands/Bot.java')
    path = out / 'manifest.json'
    manifest = json.loads(path.read_text())
    manifest['scope'] = 'Native offense final-gate reconciliation: PB-PORT-001/002'
    for entry in manifest['files']:
        entry['installed'] = shared.sha(out / entry['path'])
    path.write_text(json.dumps(manifest, indent=2))
