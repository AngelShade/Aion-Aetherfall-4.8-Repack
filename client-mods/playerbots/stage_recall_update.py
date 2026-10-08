"""PB-REPAIR-SUMMON-001 / PB-CUSTOM-RECALL-001: cancellable summon and distance recall."""
import argparse,json,shutil
from pathlib import Path
import stage_companion_update as shared
import stage_formation_update as base

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();out=a.output.resolve();shared.validate_output(out)
    name=shared.PREFIX+'services/playerbot/PlayerBotSession'
    base.SCOPES={
        name:{'tick','order'},
        shared.PREFIX+'services/playerbot/PlayerBotTransfers':{'relocate'},
        shared.PREFIX+'services/playerbot/PlayerBotRecovery':{'ready'},
        shared.PREFIX+'services/playerbot/PlayerBotSummonPolicy':{'preflight','regroup'},
        shared.PREFIX+'services/playerbot/PlayerBotPartyBehavior':{'close'},
        shared.PREFIX+'controllers/movement/PlayerBotMoveController':{'abortMove'},
        'playercommands/Bot':set(),
    };base.HELPERS={'PlayerBotRecall'}
    server=shared.ROOT/'target-deploy/game-server'
    prepared=out.parent/(out.name+'-compiled-inputs');prepared.mkdir(exist_ok=False)
    for n in base.SCOPES:
        entry=n+'.class';source=server/'cache/classes/playercommands/Bot.class' if n=='playercommands/Bot' else a.classes/entry
        dest=prepared/entry;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
    for path in (a.classes/(shared.PREFIX+'services/playerbot')).glob('PlayerBotRecall*.class'):
        dest=prepared/path.relative_to(a.classes);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,dest)
    base.stage(prepared,out)
    m=json.loads((out/'manifest.json').read_text());previous=Path(m['previousReceipt'])
    if not previous.is_absolute():previous=server/previous
    prior=json.loads(previous.read_text());known={e['path'] for e in m['files']}
    for e in prior['files']:
        if e['path'] in known:continue
        assert shared.sha(server/e['path'])==e['installed']
        dest=out/e['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/e['path'],dest)
        m['files'].append(dict(path=e['path'],original=e['installed'],installed=e['installed']))
    # Keep the actual deployed command source, rather than unfinished source UI.
    shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
    for e in m['files']:e['installed']=shared.sha(out/e['path'])
    assert [e['path'] for e in m['files'] if e['original']!=e['installed']]==['libs/playerbot-recruitment-fix.jar']
    m.update(feature='playerbot-summon-distance-recall',scope='PB-REPAIR-SUMMON-001 / PB-CUSTOM-RECALL-001',offlineOnly=True,
             unchangedGeometrySha256=shared.sha(server/'data/geo/models.mesh'))
    shutil.copy2(shared.ROOT/'docs/INSTALLED_MODS.json',out/'inventory-before.json')
    (out/'manifest.json').write_text(json.dumps(m,indent=2));print('OK: summon and early distance recall staged; all unselected installed methods retained')

if __name__=='__main__':main()
