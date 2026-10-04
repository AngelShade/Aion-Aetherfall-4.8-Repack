"""Stage the companion implementation over the actual deployed server; preserve unrelated entries."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'client-mods/season-pass'))
from incremental_server import methods
PREFIX='com/aionemu/gameserver/'
EXTRA=['model/gameobjects/player/PlayerAppearance','model/team/TemporaryPlayerTeam','controllers/effect/EffectController',
       'questEngine/handlers/template/MonsterHunt','dataholders/PetSkillData','world/geo/GeoService',
       'skillengine/condition/ChainCondition','skillengine/effect/AbstractDispelEffect','skillengine/effect/AbstractOverTimeEffect',
       'network/aion/clientpackets/CM_ENTER_WORLD']
def sha(data):return hashlib.sha256(data).hexdigest()
def package(output):
    classes=output/'classes'; payload=output/'payload';payload.mkdir(parents=True,exist_ok=False)
    base=ROOT/'target-deploy/game-server/libs/game-server-4.8-SNAPSHOT.jar'; server=base.parent.parent
    report=json.loads((output/'method-review.json').read_text(encoding='utf-8'))
    # Manual review of the initial installation: false entries are new read-only planners,
    # lambda relocation from login/target guards, normal charge callback delegation,
    # headless party membership and HTTP route registration. Projectile timing stays deployed.
    reviewed={'controllers/CreatureController','controllers/effect/EffectController','dao/InventoryDAO',
              'dataholders/PetSkillData','model/gameobjects/player/Player','model/gameobjects/player/PlayerAppearance',
              'model/team/TemporaryPlayerTeam','questEngine/QuestEngine','questEngine/handlers/template/MonsterHunt',
              'services/MarketplaceService','services/player/PlayerEnterWorldService','skillengine/condition/ChainCondition',
              'skillengine/effect/AbstractDispelEffect','skillengine/effect/AbstractOverTimeEffect',
              'skillengine/model/Skill','world/geo/GeoService'}
    for change in report:
        if not change['botReference']:
            assert change['className'] in {PREFIX+s+'.class' for s in reviewed},'Unreviewed method: '+str(change)
        assert not change['removed'] or 'lambda$' in change['method'],'Removed deployed method: '+str(change)
    target=payload/'libs'/base.name;target.parent.mkdir()
    replacements={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
    assert methods(base,PREFIX+'skillengine/model/Skill')['protected void updateHitTime(boolean);']==methods(classes,PREFIX+'skillengine/model/Skill')['protected void updateHitTime(boolean);']
    with zipfile.ZipFile(base) as original:
        names=set(original.namelist())
        with zipfile.ZipFile(target,'w') as merged:
            merged.comment=original.comment
            for info in original.infolist():merged.writestr(copy.copy(info),replacements.get(info.filename,original.read(info.filename)))
            for name in sorted(replacements.keys()-names):
                assert 'PlayerBot' in name,'Unexpected new class: '+name
                merged.writestr(name,replacements[name],compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(target) as merged:
            assert merged.testzip() is None
            for name in names:
                assert merged.read(name)==(replacements[name] if name in replacements else original.read(name)),name
            # All previously installed standalone services, including any AFK classes, remain untouched.
            preserved=len(names-replacements.keys())
    for rel,source in [('data/handlers/playercommands/Bot.java',ROOT/'game-server/data/handlers/playercommands/Bot.java'),
                       ('config/main/playerbots.properties',ROOT/'game-server/config/main/playerbots.properties'),
                       ('sql/playerbots.sql',ROOT/'game-server/sql/playerbots.sql')]:
        path=payload/rel;path.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,path)
    for source in (ROOT/'game-server/config/playerbots/media').iterdir():
        if source.is_file():
            path=payload/'config/playerbots/media'/source.name;path.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,path)
    commands=server/'config/administration/commands.properties';text=commands.read_text(encoding='utf-8-sig')
    assert not re.search(r'^\s*bot\s*=',text,re.M),'Later companion command registration already exists'
    path=payload/'config/administration/commands.properties';path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(text.rstrip()+'\n\n# Owned PvE player companions\nbot = 0\n',encoding='utf-8')
    files=[dict(path=p.relative_to(payload).as_posix(),sha256=sha(p.read_bytes()),original=sha((server/p.relative_to(payload)).read_bytes()) if (server/p.relative_to(payload)).is_file() else None) for p in sorted(payload.rglob('*')) if p.is_file()]
    (payload/'manifest.json').write_text(json.dumps(dict(feature='player-companions-server',deployment=str(server),files=files,replacedClasses=sorted(replacements),preservedJarEntries=preserved),indent=2),encoding='utf-8')
    print('OK: incremental companion server payload:',payload,'; unrelated JAR entries preserved:',preserved)
def stage(output):
    output.mkdir(parents=True,exist_ok=False);classes=output/'classes';classes.mkdir()
    sources=[p for p in (ROOT/'game-server/src').rglob('*.java') if 'PlayerBot' in p.read_text(encoding='utf-8-sig') or 'isPlaying(' in p.read_text(encoding='utf-8-sig')]
    sources+= [ROOT/'game-server/src'/PREFIX/(name+'.java') for name in EXTRA]
    sources=sorted(set(sources));scope=[p.relative_to(ROOT/'game-server/src').as_posix()[:-5] for p in sources]
    subprocess.run(['javac','--release','25','-encoding','UTF-8','-sourcepath','','-cp',str(ROOT/'target-deploy/game-server/libs/*'),'-d',str(classes),*[str(p) for p in sources]],check=True)
    base=ROOT/'target-deploy/game-server/libs/game-server-4.8-SNAPSHOT.jar'
    replacements={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
    report=[]
    with zipfile.ZipFile(base) as original:
        names=set(original.namelist())
        for name in sorted(replacements):
            if name not in names:continue
            before=methods(base,name[:-6]);after=methods(classes,name[:-6])
            for key in before.keys()|after.keys():
                if before.get(key)!=after.get(key):
                    report.append(dict(className=name,method=key,removed=key not in after,botReference=any('PlayerBot' in line or 'isPlaying' in line for line in after.get(key,[])),old=before.get(key),new=after.get(key)))
        (output/'method-review.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print('Compiled focused companion cohort; method review:',output/'method-review.json')
    for r in report:print(('BOT ' if r['botReference'] else 'REVIEW ')+r['className']+' '+r['method'])

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',type=Path,required=True);parser.add_argument('--package-reviewed',action='store_true');args=parser.parse_args()
    if args.package_reviewed:package(args.output.resolve())
    else:stage(args.output.resolve())
