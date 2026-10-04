"""Stage only reviewed equipment methods while preserving concurrent feature work."""
import argparse, json, shutil, subprocess, zipfile
from pathlib import Path
import stage_companion_update as shared

SCOPES={
 shared.PREFIX+'services/playerbot/PlayerBotSession':{'snapshot','tick'},
 shared.PREFIX+'services/playerbot/PlayerBotEquipment':{'upgrades'},
 shared.PREFIX+'services/playerbot/PlayerBotQuestSync':{'close'},
 shared.PREFIX+'services/playerbot/PlayerBotCare':{'lambda$perform$3'},
 shared.PREFIX+'services/playerbot/PlayerBotLoot':{'passRoll'},
 shared.PREFIX+'services/playerbot/PlayerBotQuests':{'rewardChoice'},
 shared.PREFIX+'services/PlayerBotHttpService':{'action'},
 'playercommands/Bot':{'<init>','execute'},
}
def stage(classes,out):
    server=shared.ROOT/'target-deploy/game-server'
    tracked=['libs/playerbot-recruitment-fix.jar','libs/game-server-4.8-SNAPSHOT.jar','start.bat','data/handlers/playercommands/Bot.java','cache/classes/playercommands/Bot.class']+['config/playerbots/media/'+n for n in ['bots.html','bots.css','bots.js']]
    before_hashes={rel:shared.sha(server/rel) for rel in tracked}
    scratch=out.parent/(out.name+'-inputs');scratch.mkdir(exist_ok=False)
    for name in SCOPES:
        target=scratch/(name+'.class');target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(classes/(name+'.class'),target)
    for source in (classes/(shared.PREFIX+'services/playerbot')).glob('PlayerBotGearPolicy*.class'):
        target=scratch/source.relative_to(classes);target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
    # Another chat is changing character generation. Keep its source untouched,
    # but retain the currently installed generate action in this gear-only update.
    source=shared.ROOT/'game-server/src/com/aionemu/gameserver/services/PlayerBotHttpService.java'
    overlay=scratch/'source/PlayerBotHttpService.java';overlay.parent.mkdir()
    text=source.read_text(encoding='utf-8')
    with zipfile.ZipFile(server/'libs/playerbot-recruitment-fix.jar') as jar:
        if shared.PREFIX+'services/playerbot/PlayerBotGenerationOptions.class' not in jar.namelist():
            text=text.replace('PlayerBotGenerationOptions.generate(owner, name, pc, values.getOrDefault("start", "matched"))','service.generate(owner, name, pc)')
    overlay.write_text(text,encoding='utf-8')
    subprocess.run(['javac','--release','25','-encoding','UTF-8','-cp',str(classes)+';'+str(shared.ROOT/'target-deploy/game-server/libs/*'),'-d',str(scratch),str(overlay)],check=True)
    # Shared transplantation still verifies every changed selected method. It
    # reports all other source differences as retained installed behavior.
    original=shared.methods
    baseline=str(server/'libs/playerbot-recruitment-fix.jar')+';'+str(server/'libs/game-server-4.8-SNAPSHOT.jar')
    def reviewed(path,name):
        actual=original(path,name)
        if Path(path)==scratch:
            before=original(baseline,name) if name!='playercommands/Bot' else original(shared.ROOT/'target-deploy/game-server/cache/classes',name)
            allowed=SCOPES[name]
            for key in before:
                method=shared.method_name(key)
                owned=name.endswith('PlayerBotSession') and method.startswith('lambda$tick$')
                if method not in allowed and not owned:actual[key]=before[key]
        return actual
    shared.methods=reviewed;shared.SCOPES=SCOPES;shared.HELPERS={'PlayerBotGearPolicy'}
    shared.stage(scratch,out)
    # Apply only the equipment pane to the latest installed tabbed UI. Keep
    # concurrent, not-yet-installed generation controls out of this transaction.
    live=shared.ROOT/'target-deploy/game-server/config/playerbots/media'
    source_js=(shared.ROOT/'game-server/config/playerbots/media/bots.js').read_text(encoding='utf-8')
    start=source_js.index('  function renderGearPolicy(');end=source_js.index('  function renderCare(',start)
    current=(live/'bots.js').read_text(encoding='utf-8')
    anchor="    } else if (botTab === 'equipment') {";assert current.count(anchor)==1
    if 'renderGearPolicy(body, bot);' not in current:current=current.replace(anchor,anchor+'\n      renderGearPolicy(body, bot);')
    if '  function renderGearPolicy(' in current:
        old_start=current.index('  function renderGearPolicy(');old_end=current.index('  function renderCare(',old_start)
        current=current[:old_start]+source_js[start:end]+current[old_end:]
    else:current=current.replace('  function renderCare(',source_js[start:end]+'  function renderCare(',1)
    media=out/'config/playerbots/media'
    for name in ['bots.html','bots.css']:shutil.copy2(live/name,media/name)
    (media/'bots.js').write_text(current,encoding='utf-8')
    manifest=json.loads((out/'manifest.json').read_text())
    assert all(shared.sha(server/rel)==value for rel,value in before_hashes.items()),'Installed baseline changed during equipment staging; rebuild from its latest receipt'
    for entry in manifest['files']:entry['installed']=shared.sha(out/entry['path'])
    manifest['scope']='equipment acquisition and preferences; concurrent generation methods retained'
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2))
    print('OK: gear-only patch; concurrent generation source and installed methods retained')
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--classes',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();stage(args.classes.resolve(),args.output.resolve())
