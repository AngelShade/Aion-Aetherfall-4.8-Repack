"""Build Warden road data offline from native static data and authenticated navigation tiles.

Writes only to an explicit external output. Review the resulting TSV before copying it
to game-server/config/playerbots/warden-beats.tsv. Does not start game components or SQL.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--server', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--relabel', action='store_true', help='revalidate and label existing output without repeating mesh searches')
    p.add_argument('--audit', action='store_true', help='validate every existing output point and write settlement endpoint setbacks')
    p.add_argument('--prune', action='store_true', help='audit a reviewed merged catalogue, retaining only native-valid distinct rows and logging rejections')
    p.add_argument('--map', help='bound generation to native map ids, separated by commas')
    a = p.parse_args()
    out = a.output.resolve()
    if out.is_relative_to(ROOT) or out.is_relative_to(a.server.resolve()):
        raise SystemExit('Use an external output directory')
    out.mkdir(parents=True, exist_ok=True)
    sources = [ROOT/'game-server/src/com/aionemu/gameserver/configs/main/PlayerBotWardenConfig.java',
               ROOT/'game-server/src/com/aionemu/gameserver/services/playerbot/PlayerBotPatrolRoutes.java',
               ROOT/'game-server/src/com/aionemu/gameserver/services/playerbot/PlayerBotWorldPlacement.java',
               ROOT/'game-server/src/com/aionemu/gameserver/services/playerbot/PlayerBotWardenBeats.java',
               ROOT/'tools/java/PlayerBotWardenBeatBuilder.java']
    cp = str(a.server.resolve()/'libs/*')
    sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
    inputs = {str(s.relative_to(ROOT)): sha(s) for s in sources}
    subprocess.run(['javac', '-cp', cp, '-d', str(out/'classes'), *map(str, sources)], check=True)
    command = ['java', '-Xmx5g']
    if a.map:
        command.append('-Dwarden.beats.map='+str(a.map))
    command += ['-cp', str(out/'classes')+';'+cp,
               'com.aionemu.gameserver.services.playerbot.PlayerBotWardenBeatBuilder',
               str(ROOT/'game-server/data/static_data'), str(a.server.resolve()/'data/navmesh'), str(out)]
    if sum((a.relabel, a.audit, a.prune)) > 1:
        p.error('--relabel, --audit and --prune are mutually exclusive')
    if a.prune:
        command.append('--prune')
    elif a.audit:
        command.append('--audit')
    elif a.relabel:
        command.append('--relabel')
    with (out/'builder.log').open('w') as log:
        subprocess.run(command, cwd=out, stdout=log, stderr=subprocess.STDOUT, check=True)
    changed = [str(s.relative_to(ROOT)) for s in sources if sha(s) != inputs[str(s.relative_to(ROOT))]]
    (out/'inputs.json').write_text(json.dumps({'sources': inputs, 'sourceChangesDuringRun': changed,
        'navmeshCatalog': sha(a.server/'data/navmesh/report.properties'), 'output': sha(out/'warden-beats.tsv'),
        'command': command}, indent=2))
    print(out/'warden-beats.tsv')


if __name__ == '__main__':
    main()
