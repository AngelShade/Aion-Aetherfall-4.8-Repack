"""PB-REPAIR-FORMATION-002 / PB-CONFIG-001: bounded offline follow and summon update."""
import argparse,copy,json,re,shutil,zipfile
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared

base.SCOPES={
 shared.PREFIX+'controllers/movement/PlayerBotMoveController':{'moveStep','abortMove'},
 shared.PREFIX+'services/playerbot/PlayerBotFormation':{'destination','following','speed','speedMultiplier','close'},
 shared.PREFIX+'services/playerbot/PlayerBotNavigation':{'follow','move'},
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'tick'},
 shared.PREFIX+'services/playerbot/PlayerBotTravel':{'summon','summonAll'},
 shared.PREFIX+'services/playerbot/PlayerBotRecovery':{'ready','tick'},
 'playercommands/Bot':set(),
}
base.HELPERS={'PlayerBotFollowIntent','PlayerBotSummonPolicy'}

def config(content):
 text=content.decode('utf-8-sig')
 text=text.replace('# Real player companions. PvE only. Commands: .bot help','# Real player companions. Current AI focuses on PvE; no PvP map restrictions.\n# Commands: .bot help')
 text=text.replace("# Instance access requires explicit map approval AND registration to the owner's party.\n# Keep off until the corresponding instance handlers have been tested with headless players.","# Legacy options retained for configuration compatibility. The owner's native\n# map admission is authoritative; these no longer gate bot recruitment/following.")
 text=re.sub(r'(?m)^gameserver\.playerbots\.enable\s*=.*$', 'gameserver.playerbots.enable = true',text)
 if not re.search(r'(?m)^gameserver\.playerbots\.summon\.enable\s*=',text):
  text=text.replace('gameserver.playerbots.enable = true','''# Master module switch. False prevents recruitment and safely dismisses active bots.
gameserver.playerbots.enable = true
# Enables individual/party Summon commands and menu buttons. Out of combat only;
# native ownership, party, map, cast, trade, loot and item-use checks still apply.
# Automatic map following and wipe recovery remain independent of this switch.
gameserver.playerbots.summon.enable = true''')
 else:text=re.sub(r'(?m)^gameserver\.playerbots\.summon\.enable\s*=.*$','gameserver.playerbots.summon.enable = true',text)
 return text.replace('\r\n','\n').replace('\n','\r\n').encode('utf-8')

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 out=a.output.resolve();shared.validate_output(out);server=shared.ROOT/'target-deploy/game-server'
 prepared=out.parent/(out.name+'-compiled-inputs');prepared.mkdir(exist_ok=False)
 for name in base.SCOPES:
  source=server/'cache/classes/playercommands/Bot.class' if name=='playercommands/Bot' else a.classes/(name+'.class')
  dest=prepared/(name+'.class');dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
 for path in (a.classes/(shared.PREFIX+'services/playerbot')).glob('*.class'):
  if path.stem.split('$')[0] in base.HELPERS:
   dest=prepared/path.relative_to(a.classes);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,dest)
 base.stage(prepared,out)
 shutil.copy2(server/'data/handlers/playercommands/Bot.java',out/'data/handlers/playercommands/Bot.java')
 m=json.loads((out/'manifest.json').read_text());previous=Path(m['previousReceipt'])
 if not previous.is_absolute():previous=server/previous
 prior=json.loads(previous.read_text());known={e['path'] for e in m['files']}
 for entry in prior['files']:
  rel=entry['path']
  if rel in known:continue
  assert shared.sha(server/rel)==entry['installed'],rel
  dest=out/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/rel,dest)
  m['files'].append(dict(path=rel,original=entry['installed'],installed=entry['installed']))
 # New config field requires cold loading. Never use this package for attach.
 configclass=shared.PREFIX+'configs/main/PlayerBotConfig.class';payload=out/'libs/playerbot-recruitment-fix.jar';temporary=out/'config-merged.jar'
 with zipfile.ZipFile(payload) as source,zipfile.ZipFile(temporary,'w') as merged:
  merged.comment=source.comment
  for entry in source.infolist():
   if entry.filename!=configclass:merged.writestr(copy.copy(entry),source.read(entry))
  merged.write(a.classes/configclass,configclass)
 temporary.replace(payload)
 with zipfile.ZipFile(server/'libs/playerbot-recruitment-fix.jar') as old:
  if configclass not in old.namelist():m['newClasses'].append(configclass)
 relative='config/main/playerbots.properties';dest=out/relative;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(config((server/relative).read_bytes().replace(b'\r\n',b'\n')))
 existing=next((e for e in m['files'] if e['path']==relative),None)
 if existing is None:m['files'].append(dict(path=relative,original=shared.sha(server/relative),installed=shared.sha(dest)))
 for e in m['files']:e['installed']=shared.sha(out/e['path'])
 changed={e['path'] for e in m['files'] if e['original']!=e['installed']}
 assert 'libs/playerbot-recruitment-fix.jar' in changed and changed<={'libs/playerbot-recruitment-fix.jar',relative}
 with zipfile.ZipFile(server/'libs/playerbot-recruitment-fix.jar') as old,zipfile.ZipFile(payload) as new:
  m['offlineReplacedHelpers']=[name for name in old.namelist() if name.startswith(shared.PREFIX+'services/playerbot/PlayerBotFollowIntent') and old.read(name)!=new.read(name)]
 m['refinementOf']=m['previousReceipt'] if prior['feature']=='playerbot-follow-summon-controls' else None
 m.update(feature='playerbot-follow-summon-controls',scope='PB-REPAIR-FORMATION-002 / PB-CONFIG-001: continuous direct follow intent, formation error speed, travel priority and configured safe summons',offlineOnly=True,configSchemaAddition='PlayerBotConfig.SUMMON_ENABLED',unchangedGeometrySha256=shared.sha(server/'data/geo/models.mesh'))
 shutil.copy2(shared.ROOT/'docs/INSTALLED_MODS.json',out/'inventory-before.json')
 from verify_runtime_linkage import verify
 m['runtimeLinkage']=verify(payload,out/'final-linkage')
 (out/'manifest.json').write_text(json.dumps(m,indent=2));print('OK: follow/summon update staged; cold-load config field; prior mods preserved')
if __name__=='__main__':main()
