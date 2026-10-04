"""Bounded companion resurrection animation update on the latest installation."""
import argparse, json, shutil, zipfile
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES = {shared.PREFIX+'services/playerbot/PlayerBotSession': {'tick','markClosing'}, shared.PREFIX+'services/player/PlayerReviveService': {'revive'}, 'playercommands/Bot': set()}
base.HELPERS = {'PlayerBotRevival'}
if __name__ == '__main__':
    p=argparse.ArgumentParser(); p.add_argument('--classes',type=Path,required=True); p.add_argument('--output',type=Path,required=True); a=p.parse_args()
    classes=a.classes.resolve(); out=a.output.resolve(); server=shared.ROOT/'target-deploy/game-server'
    with zipfile.ZipFile(server/'libs/playerbot-recruitment-fix.jar') as installed:
        if shared.PREFIX+'services/playerbot/PlayerBotRevival.class' in installed.namelist():
            base.SCOPES[shared.PREFIX+'services/playerbot/PlayerBotRevival']={'ready','refresh','beginRecovery','close'}
    command=classes/'playercommands/Bot.class'; command.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(server/'cache/classes/playercommands/Bot.class',command)
    base.stage(classes,out)
    shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
    f=out/'manifest.json'; d=json.loads(f.read_text()); d['scope']='Allow native death/resurrection animations to complete before companion actions; refresh living observer state without resource changes'
    for e in d['files']: e['installed']=shared.sha(out/e['path'])
    f.write_text(json.dumps(d,indent=2))
