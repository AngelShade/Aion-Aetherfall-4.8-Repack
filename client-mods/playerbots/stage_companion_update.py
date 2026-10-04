"""Stage bounded companion changes on the actual installed override and base JAR."""
import argparse,copy,hashlib,json,re,shutil,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'client-mods/season-pass'))
def methods(path,name):
    # Numeric switch labels are branches, not opcodes. Counting them as
    # instructions makes harmless constant-pool/ldc width changes look like edits.
    dump=subprocess.check_output(['javap','-c','-p','-s','-cp',str(path),name.replace('/','.')],text=True)
    chunks={};key=None
    for line in dump.splitlines():
        if re.match(r'^  \S',line) and (('(' in line and line.endswith(';')) or line.strip()=='static {};'):
            key=line.strip();chunks[key]=[]
        elif key is not None:chunks[key].append(line)
    result={}
    for key,lines in chunks.items():
        instructions=[re.match(r'^\s+(\d+):\s+([A-Za-z_]\w*)(.*)$',line) for line in lines]
        offsets={int(m[1]):index for index,m in enumerate(m for m in instructions if m)}
        def target(value):return str(offsets.get(int(value),'END'))
        normalized=[];exceptions=False
        for line,m in zip(lines,instructions):
            line=re.sub(r'#\d+','#',line)
            if m:
                op=m[2].replace('ldc_w','ldc');argument=re.sub(r'#\d+','#',m[3]).strip()
                if op.startswith(('if','goto','jsr')):argument=target(argument)
                normalized.append(op+' '+re.sub(r'\s+',' ',argument))
            elif re.match(r'^\s*(default|-?\d+):\s+\d+\s*$',line):
                label,value=line.strip().split(':');normalized.append(label+':'+target(value.strip()))
            elif line.strip()=='Exception table:':exceptions=True;normalized.append('exceptions')
            elif exceptions and re.match(r'^\s+\d+\s+\d+\s+\d+',line):
                a,b,c,tail=line.strip().split(None,3);normalized.append('|'.join([target(a),target(b),target(c),tail]))
            elif line.strip() and line.strip() not in ['Code:','from    to  target type']:normalized.append(re.sub(r'\s+',' ',line.strip()))
        if normalized and normalized[-1]=='}':normalized.pop()
        result[key]=normalized
    return result
PREFIX='com/aionemu/gameserver/'
SCOPES={
 PREFIX+'services/playerbot/PlayerBotSession':{'snapshot','tick','markClosing','lambda$snapshot$1','lambda$snapshot$2'},
 PREFIX+'services/playerbot/PlayerBotSession$1':{'isUseful'},
 PREFIX+'services/playerbot/PlayerBotService':{'relocate','acceptSharedQuest','create','generate'},
 PREFIX+'services/player/PlayerService':{'newPlayer','storeNewPlayer'},
 PREFIX+'services/playerbot/PlayerBotGenerationOptions':{'initialize'},
 PREFIX+'services/playerbot/PlayerBotNavigation':{'follow','move','record'},
 PREFIX+'services/playerbot/PlayerBotEquipment':{'upgrades'},
 PREFIX+'services/playerbot/PlayerBotPreferences':{'load'},
 PREFIX+'services/playerbot/PlayerBotQuestSync':{'tick','wanted','close'},
 PREFIX+'services/playerbot/PlayerBotCare':{'lambda$perform$3'},
 PREFIX+'services/playerbot/PlayerBotLoot':{'passRoll'},
 PREFIX+'services/playerbot/PlayerBotQuests':{'choose','interact','hunt','missionReward','nearby','rewardChoice'},
 PREFIX+'controllers/FlyController':{'canFly'},
 PREFIX+'services/PlayerBotHttpService':{'snapshot','action'},
 'playercommands/Bot':{'<init>','execute'},
}
HELPERS={'PlayerBotTravel','PlayerBotFlight','PlayerBotQuestSync','PlayerBotQuestMirror','PlayerBotQuestMetadata','PlayerBotQuestJournal','PlayerBotGear','PlayerBotCare','PlayerBotGearPolicy','PlayerBotGenerationOptions'}
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def method_name(key):
    before=key.split('(')[0];name=before.split()[-1]
    return '<init>' if '.' in name else name
def stage(classes,out,generation_fix=False):
    server=ROOT/'target-deploy/game-server';override=server/'libs/playerbot-recruitment-fix.jar';base=server/'libs/game-server-4.8-SNAPSHOT.jar'
    previous=sorted((server/'backups').glob('playerbots-recruitment-*/manifest.json'))[-1];receipt=json.loads(previous.read_text())
    # A later, separately receipted panel update is part of the effective baseline.
    # Verify the latest successful receipt for every file, never relax hash guards.
    expected={e['path']:e['installed'] for e in receipt['files']}
    ui_receipts=sorted((server/'backups').glob('playerbots-ui-*/installed.json'))
    for ui in ui_receipts:
        if ui.parent.name.split('playerbots-ui-')[-1]>previous.parent.name.split('playerbots-recruitment-')[-1]:
            evidence=json.loads(ui.read_text())
            assert all(e['path'].startswith('config/playerbots/media/') for e in evidence['files'])
            for e in evidence['files']:expected[e['path']]=e['installed']
    assert sha(base)==receipt['baseJarSha256'] and all(sha(server/path)==value for path,value in expected.items()),'Installed baseline changed'
    assert b'-cp "libs/playerbot-recruitment-fix.jar;libs/*"' in (server/'start.bat').read_bytes()
    out.mkdir(parents=True,exist_ok=False);baseline=out/'baseline';baseline.mkdir();plan=[];review=[]
    with zipfile.ZipFile(override) as effective,zipfile.ZipFile(base) as original:
        selected_scopes={name:allowed for name,allowed in SCOPES.items() if not generation_fix or name in {PREFIX+'services/playerbot/PlayerBotService',PREFIX+'services/player/PlayerService',PREFIX+'services/PlayerBotHttpService',PREFIX+'services/playerbot/PlayerBotGenerationOptions'}}
        for name,allowed in selected_scopes.items():
            rel=name+'.class';target=baseline/rel;target.parent.mkdir(parents=True,exist_ok=True)
            source=server/'cache/classes'/rel if name=='playercommands/Bot' else None
            if rel not in effective.namelist() and rel not in original.namelist() and not source:continue
            target.write_bytes(source.read_bytes() if source else (effective.read(rel) if rel in effective.namelist() else original.read(rel)))
            before=methods(baseline,name);after=methods(classes,name)
            assert not after.keys()-before.keys(),f'New methods require restart/schema review: {name} {after.keys()-before.keys()}'
            changed=[key for key in before.keys() & after.keys() if before[key]!=after[key]]
            retained_differences=[]
            if generation_fix:
                bounded={'PlayerBotService':{'create','generate'},'PlayerService':{'newPlayer','storeNewPlayer'},'PlayerBotHttpService':{'snapshot','action'},'PlayerBotGenerationOptions':{'initialize'}}[name.rsplit('/',1)[-1]]
                retained_differences=[key for key in changed if method_name(key) not in bounded and not (name.endswith('PlayerBotHttpService') and method_name(key).startswith('lambda$snapshot$'))]
                changed=[key for key in changed if key not in retained_differences]
            for key in changed:
                method=method_name(key)
                is_owned_lambda=(name.endswith('PlayerBotSession') and method.startswith('lambda$tick$')) or (name.endswith('PlayerBotQuests') and method.startswith(('lambda$choose$','lambda$hunt$'))) or (name.endswith('PlayerBotHttpService') and method.startswith('lambda$snapshot$'))
                assert method in allowed or is_owned_lambda,f'Unreviewed deployed method differs: {name}: {key}'
            if changed:plan.append(name+'\t'+','.join(sorted({method_name(key) for key in changed})))
            review.append(dict(path=rel,methods=changed,retainedInstalledOnlyMethods=sorted(before.keys()-after.keys()),retainedUnselectedMethodDifferences=retained_differences))
    (out/'methods.tsv').write_text('\n'.join(plan));(out/'method-review.json').write_text(json.dumps(review,indent=2))
    tools=out/'tools';tools.mkdir();subprocess.run(['javac','-d',str(tools),str(ROOT/'game-server/tools/StagePlayerBotCompanionPatch.java')],check=True)
    staged=out/'classes'
    subprocess.run(['java','-cp',str(tools)+';'+str(classes)+';'+str(server/'libs/*'),'StagePlayerBotCompanionPatch',str(baseline),str(classes),str(staged),str(out/'methods.tsv')],check=True)
    for entry in review:
        name=entry['path'][:-6]
        if not (staged/entry['path']).exists():continue
        before=methods(baseline,name);after=methods(staged,name)
        assert before.keys()==after.keys(),f'Installed schema changed: {name}'
        assert {k for k in before if before[k]!=after[k]}==set(entry['methods']),f'Transplant changed other methods: {name}'
    new=[]
    with zipfile.ZipFile(override) as installed:existing=set(installed.namelist())
    for path in (classes/(PREFIX+'services/playerbot')).glob('*.class'):
        if path.stem.split('$')[0] in ({'PlayerBotGenerationOptions'} if generation_fix else HELPERS):
            rel=path.relative_to(classes);target=staged/rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,target)
            if rel.as_posix() not in existing:new.append(rel.as_posix())
    for path in (classes/(PREFIX+'services/player')).glob('PlayerCreationRecipes*.class'):
        rel=path.relative_to(classes);target=staged/rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,target)
        if rel.as_posix() not in existing:new.append(rel.as_posix())
    payload=out/'libs/playerbot-recruitment-fix.jar';payload.parent.mkdir()
    replacements={p.relative_to(staged).as_posix():p.read_bytes() for p in staged.rglob('*.class') if not p.relative_to(staged).as_posix().startswith('playercommands/')}
    with zipfile.ZipFile(override) as source,zipfile.ZipFile(payload,'w') as merged:
        merged.comment=source.comment
        for entry in source.infolist():merged.writestr(copy.copy(entry),replacements.pop(entry.filename,source.read(entry)))
        for name,content in replacements.items():merged.writestr(name,content,compress_type=zipfile.ZIP_DEFLATED)
    rollback=out/'rollback.jar'
    with zipfile.ZipFile(rollback,'w') as jar:
        for entry in review:
            if entry['methods']:jar.write(baseline/entry['path'],entry['path'])
    files=['libs/playerbot-recruitment-fix.jar','data/handlers/playercommands/Bot.java','cache/classes/playercommands/Bot.class']
    cached=staged/'playercommands/Bot.class'
    if generation_fix:cached=server/'cache/classes/playercommands/Bot.class'
    elif not cached.exists():cached=baseline/'playercommands/Bot.class'
    for file,source in [(files[1],server/files[1] if generation_fix else ROOT/'game-server/data/handlers/playercommands/Bot.java'),(files[2],cached)]:
        target=out/file;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
    for source in (ROOT/'game-server/config/playerbots/media').glob('bots.*'):
        rel='config/playerbots/media/'+source.name;target=out/rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target);files.append(rel)
    # Launcher is retained as part of the cumulative override receipt.
    shutil.copy2(server/'start.bat',out/'start.bat');files.append('start.bat')
    manifest=dict(feature='playerbot-companion-flight-quests-travel-care',generationFix=generation_fix,deployment=str(server),baseJarSha256=sha(base),previousReceipt=str(previous.relative_to(server)),
        incrementalChangedMethods=[e for e in review if e['methods']],changedMethods=receipt['changedMethods']+[e['path']+': '+method for e in review for method in e['methods']],
        newClasses=sorted(new),rollbackSha256=sha(rollback),files=[dict(path=rel,original=sha(server/rel),installed=sha(out/rel)) for rel in files])
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2))
    print('OK: effective installed methods preserved, bounded companion overrides and media staged:',out)
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--classes',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);parser.add_argument('--generation-fix',action='store_true');args=parser.parse_args();stage(args.classes.resolve(),args.output.resolve(),args.generation_fix)
