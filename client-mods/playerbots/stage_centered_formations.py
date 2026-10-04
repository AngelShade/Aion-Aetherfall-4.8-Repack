"""Bounded centered formations on the latest installed cumulative override."""
import argparse,json,shutil,subprocess
from pathlib import Path
import stage_formation_update as base
import stage_companion_update as shared
base.SCOPES={shared.PREFIX+'services/playerbot/PlayerBotFormation':{'destination'},'playercommands/Bot':{'execute','<init>'}}
base.HELPERS={'PlayerBotFormationLayout'}

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    classes=a.classes.resolve();out=a.output.resolve();server=shared.ROOT/'target-deploy/game-server'
    # Compile the actual installed command source, retaining newer generation,
    # care and inventory behavior that can differ from repository source.
    source=server/'data/handlers/playercommands/Bot.java';original=source.read_text()
    before=classes/'command-baseline';before.mkdir(parents=True,exist_ok=True)
    cp=str(classes)+';'+str(server/'libs/playerbot-recruitment-fix.jar')+';'+str(server/'libs/*')
    subprocess.run(['javac','--release','25','-encoding','UTF-8','-cp',cp,'-d',str(before),str(source)],check=True)
    assert shared.methods(before,'playercommands/Bot')==shared.methods(server/'cache/classes','playercommands/Bot'),'Installed command source differs from effective command; inspect before editing'
    overlay=classes/'source/Bot.java';overlay.parent.mkdir(parents=True,exist_ok=True)
    text=original.replace('\t\ttry {','\t\ttry {\n\t\t\tif (PlayerBotFormationLayout.command(owner, params)) return;',1)
    anchor='\t\t\tfollow|stay|guard|passive [name|all] - Change companion orders (default all).'
    assert text!=original and text.count(anchor)==1
    text=text.replace(anchor,anchor+'\n\t\t\tformation circle|box|line|spread - Center following companions around you; regroup after combat.')
    overlay.write_text(text)
    subprocess.run(['javac','--release','25','-encoding','UTF-8','-cp',cp,'-d',str(classes),str(overlay)],check=True)
    base.stage(classes,out)
    shutil.copy2(overlay,out/'data/handlers/playercommands/Bot.java')
    f=out/'manifest.json';d=json.loads(f.read_text());d['scope']='Owner-centered Circle/Box/Line/Spread following formations; existing combat/session strategies retained'
    for e in d['files']:e['installed']=shared.sha(out/e['path'])
    f.write_text(json.dumps(d,indent=2))
