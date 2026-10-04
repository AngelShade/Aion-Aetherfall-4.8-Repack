"""Stage reviewed party-follow/quest methods on the latest effective installation."""
import argparse,json,shutil
from pathlib import Path
import stage_companion_update as shared

SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'tick','explicitTarget','order'},
 shared.PREFIX+'services/playerbot/PlayerBotService':{'relocate'},
 shared.PREFIX+'services/playerbot/PlayerBotTravel':{'followTeleport'},
 shared.PREFIX+'services/playerbot/PlayerBotQuestSync':{'snapshot','close'},
 shared.PREFIX+'services/playerbot/PlayerBotQuestMetadata':{'rewardNpcIds'},
 shared.PREFIX+'services/playerbot/PlayerBotQuestJournal':{'describe'},
 shared.PREFIX+'services/playerbot/PlayerBotQuests':{'interact','ordinary','hunt'},
 shared.PREFIX+'controllers/movement/PlayerBotMoveController':{'moveStep'},
 shared.PREFIX+'services/QuestService':{'finishQuest'},
 shared.PREFIX+'services/PlayerBotHttpService':{'action'},
 'playercommands/Bot':{'<init>','execute'},
}
HELPERS={'PlayerBotPartyBehavior','PlayerBotQuestObjects','PlayerBotPartyCompletion'}

def stage(classes,out):
 server=shared.ROOT/'target-deploy/game-server'
 tracked=['libs/playerbot-recruitment-fix.jar','libs/game-server-4.8-SNAPSHOT.jar','start.bat','data/handlers/playercommands/Bot.java','cache/classes/playercommands/Bot.class']+['config/playerbots/media/'+n for n in ['bots.html','bots.css','bots.js']]
 before={rel:shared.sha(server/rel) for rel in tracked}
 scratch=out.parent/(out.name+'-inputs');scratch.mkdir(exist_ok=False)
 for name in SCOPES:
  target=scratch/(name+'.class');target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(classes/(name+'.class'),target)
 for source in (classes/(shared.PREFIX+'services/playerbot')).glob('*.class'):
  if source.stem.split('$')[0] in HELPERS:
   target=scratch/source.relative_to(classes);target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
 original=shared.methods
 baseline=str(server/'libs/playerbot-recruitment-fix.jar')+';'+str(server/'libs/game-server-4.8-SNAPSHOT.jar')
 def reviewed(path,name):
  actual=original(path,name)
  if Path(path)==scratch:
   previous=original(baseline,name) if name!='playercommands/Bot' else original(server/'cache/classes',name)
   for key in previous:
    method=shared.method_name(key)
    owned=name.endswith('PlayerBotSession') and method.startswith('lambda$tick$') or name.endswith('PlayerBotQuestJournal') and method.startswith('lambda$describe$')
    if method not in SCOPES[name] and not owned:actual[key]=previous[key]
  return actual
 shared.methods=reviewed;shared.SCOPES=SCOPES;shared.HELPERS=HELPERS
 shared.stage(scratch,out)
 # Preserve the actual tabbed UI and only add this update's toggle/help.
 media=out/'config/playerbots/media';live=server/'config/playerbots/media'
 for name in ['bots.html','bots.css']:shutil.copy2(live/name,media/name)
 text=(live/'bots.js').read_text(encoding='utf-8')
 if "['questCombat','Nearby quest combat']" not in text:
  needle="['questing','Auto quests']";assert text.count(needle)==1;text=text.replace(needle,needle+",['questCombat','Nearby quest combat']")
  anchor="    } else if (botTab === 'quests') {";assert text.count(anchor)==1
  text=text.replace(anchor,anchor+"\n      button(body, 'Nearby quest combat: ' + (bot.questCombat ? 'On' : 'Off'), { action: 'setting', name: bot.name, setting: 'questCombat', enabled: !bot.questCombat }, bot.closing, bot.questCombat ? 'on' : '');\n      body.appendChild(node('p', 'When On, the party tackles isolated quest targets within 25 m of you. Tanks lead; healers and support keep their roles. Untouched nearby packs are left for you to pull.', 'hint'));")
 (media/'bots.js').write_text(text,encoding='utf-8')
 manifest=json.loads((out/'manifest.json').read_text());assert all(shared.sha(server/rel)==value for rel,value in before.items()),'Baseline changed during staging'
 for entry in manifest['files']:entry['installed']=shared.sha(out/entry['path'])
 manifest['scope']='party catch-up, native quest objects, owner turn-in witnesses and nearby quest combat toggle'
 (out/'manifest.json').write_text(json.dumps(manifest,indent=2))
 print('OK: bounded party/quest update staged; all unselected installed methods retained')
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--classes',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
 args=parser.parse_args();stage(args.classes.resolve(),args.output.resolve())
