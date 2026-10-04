"""Transplant only reviewed camp methods into the latest cumulative installation."""
import argparse,copy,json,shutil,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'client-mods/playerbots'))
import stage_companion_update as shared
SCOPES={shared.PREFIX+'model/base/Base':{'spawnBySpawnHandler','getOccupier'},shared.PREFIX+'services/TribeRelationService':{'isFriend'}}
def stage(classes,out):
    server=ROOT/'target-deploy/game-server';latest=sorted((server/'backups').glob('playerbots-recruitment-*/manifest.json'))[-1];receipt=json.loads(latest.read_text())
    assert all(shared.sha(server/e['path'])==e['installed'] for e in receipt['files'])
    assert shared.sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==receipt['baseJarSha256']
    out.mkdir(parents=True,exist_ok=False);baseline=out/'baseline';baseline.mkdir();review=[];plan=[]
    with zipfile.ZipFile(server/'libs/playerbot-recruitment-fix.jar') as old,zipfile.ZipFile(server/'libs/game-server-4.8-SNAPSHOT.jar') as base:
        for name,allowed in SCOPES.items():
            rel=name+'.class';dest=baseline/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(old.read(rel) if rel in old.namelist() else base.read(rel))
            before=shared.methods(baseline,name);after=shared.methods(classes,name)
            assert before.keys()==after.keys(),name
            changed=[k for k in before if before[k]!=after[k]];assert all(shared.method_name(k) in allowed for k in changed),changed
            assert changed,name
            plan.append(name+'\t'+','.join(sorted({shared.method_name(k) for k in changed})));review.append(dict(path=rel,methods=changed))
    (out/'methods.tsv').write_text('\n'.join(plan));(out/'method-review.json').write_text(json.dumps(review,indent=2))
    tools=out/'tools';tools.mkdir();subprocess.run(['javac','-d',str(tools),str(ROOT/'game-server/tools/StagePlayerBotCompanionPatch.java')],check=True)
    staged=out/'classes';subprocess.run(['java','-cp',str(tools)+';'+str(classes)+';'+str(server/'libs/*'),'StagePlayerBotCompanionPatch',str(baseline),str(classes),str(staged),str(out/'methods.tsv')],check=True)
    for e in review:
        name=e['path'][:-6];before=shared.methods(baseline,name);after=shared.methods(staged,name)
        assert before.keys()==after.keys() and {k for k in before if before[k]!=after[k]}==set(e['methods'])
    new=[]
    for p in (classes/(shared.PREFIX+'services/base')).glob('LegacyCampBattle*.class'):
        rel=p.relative_to(classes);dest=staged/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dest);new.append(rel.as_posix())
    payload=out/'libs/playerbot-recruitment-fix.jar';payload.parent.mkdir()
    replacements={p.relative_to(staged).as_posix():p.read_bytes() for p in staged.rglob('*.class')}
    with zipfile.ZipFile(server/'libs/playerbot-recruitment-fix.jar') as old,zipfile.ZipFile(payload,'w') as merged:
        assert not set(new)&set(old.namelist())
        for entry in old.infolist():merged.writestr(copy.copy(entry),replacements.pop(entry.filename,old.read(entry)))
        for name,data in replacements.items():merged.writestr(name,data,compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(out/'rollback.jar','w') as rollback:
        for e in review:rollback.write(baseline/e['path'],e['path'])
    files={e['path']:shared.sha(server/e['path']) for e in receipt['files']}
    for rel in files:
        if rel!='libs/playerbot-recruitment-fix.jar':dest=out/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/rel,dest)
    data=ROOT/'target/base-camps/data';changes=json.loads((data/'changes.json').read_text())
    for rel,h in changes['files'].items():
        assert shared.sha(server/rel)==h['original'];files[rel]=h['original'];dest=out/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(data/rel,dest)
    manifest=dict(feature='playerbot-companion-flight-quests-travel-care',deployment=str(server),baseJarSha256=receipt['baseJarSha256'],previousReceipt=str(latest.relative_to(server)),scope='Eight legacy camps: grounded spawns, ordinary-world overlap removal, native specific hostility and advancing faction assaults',incrementalChangedMethods=review,changedMethods=receipt['changedMethods']+[e['path']+': '+k for e in review for k in e['methods']],newClasses=new,rollbackSha256=shared.sha(out/'rollback.jar'),files=[dict(path=rel,original=h,installed=shared.sha(out/rel)) for rel,h in files.items()])
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2));shutil.copy2(data/'changes.json',out/'camp-data-changes.json')
    with zipfile.ZipFile(server/'libs/playerbot-recruitment-fix.jar') as old,zipfile.ZipFile(payload) as merged:
        selected={e['path'] for e in review};assert all(old.read(n)==merged.read(n) for n in old.namelist() if n not in selected)
    print('OK: three reviewed existing methods and new scoped camp helper; unrelated override entries, media, launcher and client preserved.')
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--classes',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();stage(a.classes.resolve(),a.output.resolve())
