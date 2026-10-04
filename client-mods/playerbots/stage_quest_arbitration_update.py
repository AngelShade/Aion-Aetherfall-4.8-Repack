"""PB-PORT-003: bounded shared quest objective execution over the current installation."""
import argparse,json,shutil
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared

base.SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'tick'},
 shared.PREFIX+'services/playerbot/PlayerBotQuestRoutes':{'choose','trigger','close','ids','leash'},
 shared.PREFIX+'services/playerbot/PlayerBotQuests':{'choose','interact'},
 shared.PREFIX+'services/playerbot/PlayerBotQuestConversations':{'tick'},
 shared.PREFIX+'services/playerbot/PlayerBotQuestObjects':{'tick'},
 shared.PREFIX+'services/playerbot/PlayerBotPartyBehavior':{'hunt'},
 'playercommands/Bot':set(),
}
# Include existing helper lambdas in runtime scopes as well as their enclosing methods.
# A newly added existing-class method still fails the shared schema guard.
server=shared.ROOT/'target-deploy/game-server'
baseline=str(server/'libs/playerbot-recruitment-fix.jar')+';'+str(server/'libs/game-server-4.8-SNAPSHOT.jar')
import zipfile
with zipfile.ZipFile(server/'libs/playerbot-recruitment-fix.jar') as installed:
 if shared.PREFIX+'services/playerbot/PlayerBotQuestObjectives.class' in installed.namelist():
  # The follow-up changes one existing helper method. Keep its runtime scope explicit.
  base.SCOPES[shared.PREFIX+'services/playerbot/PlayerBotQuestObjectives']={'peers'}
for cls,enclosing in [('PlayerBotQuests','choose'),('PlayerBotQuestObjects','tick'),('PlayerBotPartyBehavior','hunt')]:
 name=shared.PREFIX+'services/playerbot/'+cls
 for key in shared.methods(baseline,name):
  method=shared.method_name(key)
  if method.startswith('lambda$'+enclosing+'$'):base.SCOPES[name].add(method)
base.HELPERS={'PlayerBotQuestObjectives'}

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 out=a.output.resolve();base.stage(a.classes.resolve(),out)
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 path=out/'manifest.json';manifest=json.loads(path.read_text());manifest['scope']='Shared native quest objective arbitration: PB-PORT-003'
 for entry in manifest['files']:entry['installed']=shared.sha(out/entry['path'])
 path.write_text(json.dumps(manifest,indent=2))
