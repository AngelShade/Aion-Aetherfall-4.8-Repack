"""Prepare/install only normal GameServer output and the missing statue timing key."""
import argparse,json,re,shutil,subprocess,zipfile
from datetime import datetime,timezone
from pathlib import Path
from prepare import ROOT,sha

KEY=b'gameserver.hotspot.teleport.cast_time_millis'
JAR='libs/game-server-4.8-SNAPSHOT.jar'
CONFIG='config/main/custom.properties'
STATUE_RESOURCES={'data/static_data/'+p for p in ('npcs/npc_templates.xml','spawns/Npcs/110010000_Sanctum.xml','npc_teleporter.xml','teleport_location.xml','hotspot_template.xml')}
ZIRIUS_RESOURCES={'data/static_data/npcs/npc_templates.xml','data/static_data/spawns/Npcs/210060000_Theobomos.xml',
 'data/handlers/quest/theobomos/_3057CursedZirius.java','data/handlers/ai/worlds/theobomos/CursedZiriusAI.java'}
ALLOWED={'META-INF/MANIFEST.MF','com/aionemu/gameserver/configs/main/CustomConfig.class',
 'com/aionemu/gameserver/network/aion/serverpackets/SM_BIND_POINT_TELEPORT.class',
 'com/aionemu/gameserver/services/teleport/BindPointTeleportService.class',
 'com/aionemu/gameserver/services/teleport/BindPointTeleportService$1.class',
 'com/aionemu/gameserver/services/teleport/BindPointTeleportService$Cooldown.class'}

def digest(path):return sha(path.read_bytes())

def guard_closed():
    query="Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object { $_.Name -in @('aion.bin','aion.exe','java.exe') } | Select-Object Name,ExecutablePath,CommandLine | ConvertTo-Json -Compress"
    result=subprocess.run(['powershell.exe','-NoProfile','-Command',query],capture_output=True,text=True,check=True)
    entries=json.loads(result.stdout) if result.stdout.strip() else []
    if isinstance(entries,dict):entries=[entries]
    for entry in entries:
        if entry['Name'].lower()=='java.exe':
            if not entry.get('CommandLine') or 'com.aionemu.gameserver.GameServer' in entry['CommandLine']:
                raise ValueError('GameServer is running or its closure cannot be verified')

def prepare(build,output,with_statue=False,with_zirius=False):
    build=build.resolve();output=output.resolve();server=ROOT/'target-deploy/game-server'
    if output.exists() or ROOT==output or ROOT in output.parents:raise ValueError('Use fresh external staging')
    normal=json.loads((build/'build-manifest.json').read_text())
    source=build/'game-server/game-server-4.8-SNAPSHOT.jar';prior=server/JAR
    if digest(source)!=normal['artifacts']['game-server/game-server-4.8-SNAPSHOT.jar']:raise ValueError('Normal builder JAR changed')
    with zipfile.ZipFile(prior) as old,zipfile.ZipFile(source) as new:
        if set(old.namelist())!=set(new.namelist()):raise ValueError('Unrelated JAR entries added/removed')
        differences={n for n in old.namelist() if old.read(n)!=new.read(n)}
        if not differences.issubset(ALLOWED):raise ValueError('Unrelated compiled behavior changed: '+str(sorted(differences-ALLOWED)))
        retained=len(old.namelist())-len(differences)
    inputs={n:h for n,h in normal['inputs'].items() if n in {
      'game-server/src/com/aionemu/gameserver/configs/main/CustomConfig.java',
      'game-server/src/com/aionemu/gameserver/services/teleport/BindPointTeleportService.java',
      'game-server/src/com/aionemu/gameserver/network/aion/serverpackets/SM_BIND_POINT_TELEPORT.java',
      'game-server/config/main/custom.properties'}}
    if len(inputs)!=4 or any(digest(ROOT/n)!=h for n,h in inputs.items()):raise ValueError('Timing source changed after build')
    before=(server/CONFIG).read_bytes()
    existing=re.findall(rb'(?m)^\s*'+re.escape(KEY)+rb'\s*=\s*([^\r\n]+)',before)
    if len(existing)>1:raise ValueError('Duplicate owner timing key')
    if existing:
        value=int(existing[0].strip());after=before
    else:
        value=1000;newline=b'\r\n' if b'\r\n' in before else b'\n'
        after=before+(b'' if before.endswith(b'\n') else newline)+newline+newline.join([
          b'# Statue teleport: server completion and updated native bar share this duration.',
          b'# Milliseconds; below 1 uses 1 ms. Teleport cooldown is separate.',KEY+b' = 1000',b''])
    output.mkdir(parents=True)
    target=output/CONFIG;target.parent.mkdir(parents=True);target.write_bytes(after)
    files=[dict(path=JAR,source=str(source),original=digest(prior),installed=digest(source))]
    if before!=after:files.append(dict(path=CONFIG,source=str(target),original=sha(before),installed=sha(after)))
    resources=(STATUE_RESOURCES if with_statue else set())|(ZIRIUS_RESOURCES if with_zirius else set())
    if resources:
        with zipfile.ZipFile(build/'game-server/game-server.zip') as assembly:
            for rel in sorted(resources):
                payload=assembly.read('game-server/'+rel)
                maintained=ROOT/'game-server'/rel
                if sha(payload)!=digest(maintained) or normal['inputs']['game-server/'+rel]!=sha(payload):raise ValueError('Statue resource differs from normal assembly/source')
                inputs['game-server/'+rel]=sha(payload)
                original=digest(server/rel) if (server/rel).is_file() else None
                if original!=sha(payload):
                    staged=output/rel;staged.parent.mkdir(parents=True,exist_ok=True);staged.write_bytes(payload)
                    files.append(dict(path=rel,source=str(staged),original=original,installed=sha(payload)))
    manifest=dict(feature='hotspot-server-timing',serverRoot=str(server),build=str(build),sourceHashes=inputs,
      files=files,configurationGuard=sha(before),castMilliseconds=max(1,value),changedJarEntries=sorted(differences),unchangedJarEntries=retained,
      fullSuitesRun=False,newTestFiles=False,gameStarted=False,repackChanged=False,
      gameplayAcceptance='User statue bar, cancellation and configured completion timing pending')
    if with_statue:manifest.update(statueResources=sorted(STATUE_RESOURCES),gameplayAcceptance='User Protectors Hall statue, native map labels/markers, outgoing/return trips, landing and cast timing pending')
    if with_zirius:manifest.update(ziriusResources=sorted(ZIRIUS_RESOURCES),gameplayAcceptance='User statue routes/timing plus Zirius night/day transition, low-HP lure, spring cure and reward pending')
    (output/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print('Prepared exact normal JAR and missing timing default;',retained,'unrelated JAR entries retained; owner config preserved.')

def install(output,backup):
    output=output.resolve();backup=backup.resolve();m=json.loads((output/'manifest.json').read_text());server=ROOT/'target-deploy/game-server'
    if m['feature']!='hotspot-server-timing' or Path(m['serverRoot'])!=server:raise ValueError('Wrong original server cohort')
    if backup.exists() or ROOT==backup or ROOT in backup.parents:raise ValueError('Use fresh external backup')
    def guard():
        guard_closed()
        if any(digest(ROOT/n)!=h for n,h in m['sourceHashes'].items()):raise ValueError('Timing source changed')
        if digest(server/CONFIG)!=m['configurationGuard']:raise ValueError('Owner configuration changed')
        allowed={JAR,CONFIG}|(STATUE_RESOURCES if m.get('statueResources')==sorted(STATUE_RESOURCES) else set())|(ZIRIUS_RESOURCES if m.get('ziriusResources')==sorted(ZIRIUS_RESOURCES) else set())
        for e in m['files']:
            target=server/e['path'];current=digest(target) if target.is_file() else None
            if e['path'] not in allowed or current!=e['original'] or digest(Path(e['source']))!=e['installed']:
                raise ValueError('Runtime or normal artifact changed')
    guard();backup.mkdir(parents=True)
    for e in m['files']:
        if e['original'] is not None:
            target=backup/e['path'];target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/e['path'],target)
            if digest(target)!=e['original']:raise ValueError('Backup hash mismatch')
    guard();copied=[]
    try:
        for e in sorted(m['files'],key=lambda e:e['path']==JAR):
            (server/e['path']).parent.mkdir(parents=True,exist_ok=True)
            copied.append(e);shutil.copy2(e['source'],server/e['path'])
            if digest(server/e['path'])!=e['installed']:raise ValueError('Copied file hash mismatch')
    except Exception:
        for e in reversed(copied):
            if e['original'] is not None:
                shutil.copy2(backup/e['path'],server/e['path'])
                if digest(server/e['path'])!=e['original']:raise RuntimeError('Rollback hash mismatch')
            else:
                # Exact newly delivered handler only; never remove a pre-existing file.
                target=server/e['path']
                if target.is_file() and digest(target)==e['installed']:target.unlink()
        raise
    m.update(installedAt=datetime.now(timezone.utc).isoformat(),receipt=str(backup/'manifest.json'),copiedHashesVerified=True)
    (backup/'manifest.json').write_text(json.dumps(m,indent=2)+'\n')
    print('Installed original GameServer timing and configuration; backup/copied hashes verified:',backup/'manifest.json')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=['prepare','install']);p.add_argument('--build',type=Path)
    p.add_argument('--output',type=Path,required=True);p.add_argument('--backup',type=Path);p.add_argument('--with-statue',action='store_true');p.add_argument('--with-zirius',action='store_true');a=p.parse_args()
    if a.mode=='prepare':prepare(a.build,a.output,a.with_statue,a.with_zirius)
    else:install(a.output,a.backup)
