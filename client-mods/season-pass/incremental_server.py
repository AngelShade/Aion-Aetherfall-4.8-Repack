"""Compose only Season Pass classes/hooks onto the deployed JAR, preserving other work."""
import re
import copy
import subprocess
import tempfile
import zipfile
from pathlib import Path

SOURCES=[
 'controllers/GatherableController','controllers/NpcController',
 'model/team/common/service/PlayerTeamDistributionService',
 'services/craft/CraftService','services/MarketplaceService',
 'services/player/PlayerEnterWorldService','services/PvpService','services/QuestService',
 'services/SeasonPassHttpService','services/SeasonPassRules','services/SeasonPassService']
PREFIX='com/aionemu/gameserver/'

def methods(path,name):
    dump=subprocess.check_output(['javap','-c','-p','-s','-cp',str(path),name.replace('/','.')],text=True)
    chunks={};key=None
    for line in dump.splitlines():
        if re.match(r'^  \S',line) and (('(' in line and line.endswith(';')) or line.strip()=='static {};'):
            key=line.strip();chunks[key]=[]
        elif key is not None:chunks[key].append(line)
    return {k:normalize(v) for k,v in chunks.items()}

def normalize(lines):
    # Compare code semantically across constant-pool/ldc-width/debug differences.
    instructions=[re.match(r'^\s+(\d+):\s+(\w+)(.*)$',line) for line in lines]
    offsets={int(m[1]):index for index,m in enumerate(m for m in instructions if m)}
    def target(value):return str(offsets.get(int(value),'END'))
    result=[];exceptions=False
    for line,m in zip(lines,instructions):
        line=re.sub(r'#\d+','#',line)
        if m:
            op=m[2].replace('ldc_w','ldc');arg=re.sub(r'#\d+','#',m[3]).strip()
            if op.startswith(('if','goto','jsr')):arg=target(arg)
            result.append(op+' '+re.sub(r'\s+',' ',arg))
        elif re.match(r'^\s*(default|-?\d+):\s+\d+\s*$',line):
            label,value=line.strip().split(':');result.append(label+':'+target(value.strip()))
        elif line.strip()=='Exception table:':exceptions=True;result.append('exceptions')
        elif exceptions and re.match(r'^\s+\d+\s+\d+\s+\d+',line):
            a,b,c,tail=line.strip().split(None,3);result.append('|'.join([target(a),target(b),target(c),tail]))
        elif line.strip() and line.strip() not in ['Code:','from    to  target type']:
            result.append(re.sub(r'\s+',' ',line.strip()))
    return result

def compose(root,out):
    base=root/'target-deploy/game-server/libs/game-server-4.8-SNAPSHOT.jar'
    classes=Path(tempfile.mkdtemp(prefix=out.name+'-classes-',dir=out.parent))
    source_files=[str(root/'game-server/src'/PREFIX/(s+'.java')) for s in SOURCES]
    subprocess.run(['javac','-encoding','UTF-8','-sourcepath','','-cp',str(root/'target-deploy/game-server/libs/*'),'-d',str(classes),*source_files],check=True)
    scopes=[PREFIX+s for s in SOURCES]
    replacements={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')
                  if any(f.relative_to(classes).as_posix()==s+'.class' or f.relative_to(classes).as_posix().startswith(s+'$') for s in scopes)}
    with zipfile.ZipFile(base) as original:
        original_names=set(original.namelist())
        for name in sorted(replacements):
            if name not in original_names:
                assert name.startswith(PREFIX+'services/SeasonPass'),'Unexpected new non-pass class: '+name
                continue
            old=methods(base,name[:-6]);new=methods(classes,name[:-6])
            for method in old.keys()|new.keys():
                if old.get(method)==new.get(method):continue
                assert new.get(method) is not None,'Removed deployed method: '+name+' '+method
                is_credit_guard=name==PREFIX+'controllers/NpcController.class' and method=='public synchronized boolean markSeasonPassCredit(int);' and method not in old
                assert is_credit_guard or any('SeasonPass' in instruction for instruction in new[method]),'Unrelated deployed method differs: '+name+' '+method
        target=out/'libs/game-server-4.8-SNAPSHOT.jar';target.parent.mkdir(parents=True)
        with zipfile.ZipFile(target,'w') as merged:
            merged.comment=original.comment
            for info in original.infolist():merged.writestr(copy.copy(info),replacements.get(info.filename,original.read(info.filename)))
            for name in sorted(replacements.keys()-original_names):merged.writestr(name,replacements[name],compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(target) as merged:
            assert merged.testzip() is None
            for name in original_names:
                if name not in replacements:assert merged.read(name)==original.read(name),name
            for name,data in replacements.items():assert merged.read(name)==data,name
    print('OK: incremental pass JAR; unrelated deployed entries unchanged; only pass methods differ in shared hook classes.')
    return dict(baseJar=str(base),classes=sorted(replacements),preservedEntries=len(original_names-replacements.keys()))
