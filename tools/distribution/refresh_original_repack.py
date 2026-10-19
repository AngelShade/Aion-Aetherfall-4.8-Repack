"""Refresh known original build changes into full/test repacks; never publish or run games."""
import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import zipfile


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def sha(data):
    return hashlib.sha256(data).hexdigest()


def encoded(value, compact=False):
    return (json.dumps(value, **({'separators': (',', ':')} if compact else {'indent': 2}))+'\n').encode()


def preserve_repack_network(path):
    """Keep original-only binds/ports and client connections out of repack syncs."""
    relative = str(path).replace('\\', '/').casefold().removeprefix('server/')
    return relative.startswith(tuple(component+'/config/network/' for component in
                                     ('game-server', 'login-server', 'chat-server'))) or relative == 'client/mods/launcher/connection.json'


def properties(data):
    return dict((m[1],m[2]) for line in data.decode('utf-8-sig').splitlines()
                if (m:=re.match(r'^\s*([^#!\s][^=]*?)\s*=\s*(.*?)\s*$',line)))


def merge_defaults(current, before, after):
    """Upgrade inherited defaults, retaining values the owner changed."""
    old,new,live=properties(before),properties(after),properties(current)
    desired=dict(live)
    for key,value in new.items():
        if key not in live or live[key]==old.get(key):desired[key]=value
    lines=[];seen=set()
    for line in current.decode('utf-8-sig').splitlines():
        m=re.match(r'^(\s*([^#!\s][^=]*?)\s*=\s*)(.*?)\s*$',line)
        if m:
            key=m[2];seen.add(key)
            if desired[key]!=live[key]:line=m[1]+desired[key]
        lines.append(line)
    added=[k for k in desired if k not in seen]
    if added:
        lines+=['','# Additional navigation defaults; existing owner overrides retained.']
        lines += [key+' = '+desired[key] for key in added]
    return ('\n'.join(lines)+'\n').encode()


def assert_closed(roots):
    query="$ErrorActionPreference='Stop'; @(Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('java.exe','javaw.exe','aion.bin','aion.exe','Aetherfall Hub.exe','Aetherfall Setup.exe') } | Select-Object ProcessId,Name,ExecutablePath) | ConvertTo-Json -Compress"
    raw=subprocess.check_output(['powershell.exe','-NoProfile','-NonInteractive','-Command',query],text=True).strip()
    rows=json.loads(raw) if raw else []
    if isinstance(rows,dict):rows=[rows]
    for row in rows:
        if not row.get('ExecutablePath'):raise RuntimeError('Cannot verify process path; no replacement allowed')
        if any(Path(row['ExecutablePath']).resolve().is_relative_to(root) for root in roots):
            raise RuntimeError('Close the affected repack normally: '+str(row['ProcessId']))


def deliver(root,changes,backup):
    if backup.exists():raise ValueError('Use a fresh external backup')
    backup.mkdir(parents=True)
    records=[]
    for rel,data in changes.items():
        target=root/rel
        if not target.resolve().is_relative_to(root):raise ValueError('Unsafe destination')
        before=sha(target.read_bytes()) if target.is_file() else None
        if before==sha(data):continue
        if before:
            saved=backup/rel;saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(target,saved)
            if sha(saved.read_bytes())!=before:raise ValueError('Backup mismatch')
        records.append(dict(path=rel,before=before,sha256=sha(data),bytes=len(data)))
    copied=[]
    try:
        for entry in records:
            rel=entry['path'];target=root/rel
            if (sha(target.read_bytes()) if target.is_file() else None)!=entry['before']:
                raise ValueError('Destination changed during preparation: '+rel)
            copied.append(entry);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(changes[rel])
            if sha(target.read_bytes())!=entry['sha256']:raise ValueError('Copied hash mismatch: '+rel)
    except BaseException:
        for entry in reversed(copied):
            target=root/entry['path']
            if entry['before']:shutil.copy2(backup/entry['path'],target)
            elif target.exists():target.unlink()
        raise
    return records


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['package','installed','build','previous-build','backup']:parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--version',required=True)
    args=parser.parse_args();repo=Path(__file__).resolve().parents[2]
    full,installed,build,previous,backup=(p.resolve() for p in (args.package,args.installed,args.build,args.previous_build,args.backup))
    if backup.exists() or any(backup.is_relative_to(p) for p in [repo,full,installed]):raise ValueError('Use fresh external backups')
    if (full/'Server/database/data').exists():raise ValueError('Full repack must remain uninitialized')
    old_version=read(full/'release.json')['Version']
    if old_version!=read(installed/'release.json')['Version']:raise ValueError('Repack versions differ; reconcile separately')
    if tuple(map(int,args.version.split('.'))) <= tuple(map(int,old_version.split('.'))):raise ValueError('New version must advance')
    current,prior=read(build/'build-manifest.json'),read(previous/'build-manifest.json')
    gs='Server/game-server/libs/game-server-4.8-SNAPSHOT.jar'
    jar=build/'game-server/game-server-4.8-SNAPSHOT.jar';expected=current['artifacts']['game-server/game-server-4.8-SNAPSHOT.jar']
    original=repo/'target-deploy/game-server/libs/game-server-4.8-SNAPSHOT.jar'
    if sha(jar.read_bytes())!=expected or sha(original.read_bytes())!=expected:raise ValueError('Original and exact normal build differ')
    assert_closed([full,installed])
    changes={gs:jar.read_bytes()};config_merges={}
    with zipfile.ZipFile(build/'game-server/game-server.zip') as assembly,zipfile.ZipFile(previous/'game-server/game-server.zip') as previous_assembly,zipfile.ZipFile(jar) as jar_resources:
        for key,digest in current['inputs'].items():
            if prior['inputs'].get(key)==digest or not key.startswith(('game-server/data/','game-server/config/','game-server/dist/')):continue
            if preserve_repack_network(key):continue
            name=key.replace('game-server/dist/','game-server/',1)
            if name not in assembly.namelist() and key.startswith('game-server/config/'):
                resource=key[len('game-server/config/'):]
                if resource in jar_resources.namelist() and sha(jar_resources.read(resource))==digest:
                    continue  # Native dashboard media is packaged in the exact builder JAR.
            data=assembly.read(name)
            if sha(data)!=digest:raise ValueError('Normal resource differs from build manifest: '+key)
            target='Server/'+name
            if key.startswith('game-server/config/') and key.endswith('.properties'):
                before=previous_assembly.read(name);existing=(full/target).read_bytes()
                data=merge_defaults(existing,before,data)
                config_merges[target]=(before,assembly.read(name))
            changes[target]=data
        # Portable launcher retains bundled Java and shutdown behavior; only the
        # recorded heap setup is taken from the normal built Windows launcher.
        built_start=assembly.read('game-server/start.bat').decode('utf-8-sig')
        setup_start=built_start.index('IF EXIST ');setup_end=built_start.index(':START')
        heap_setup=built_start[setup_start:setup_end].strip()
        portable=(full/'Server/game-server/start.bat').read_text(encoding='utf-8-sig')
        if portable.count('-Xms1024m -Xmx4g')!=1 or portable.count(':START')!=1:raise ValueError('Unexpected portable launcher baseline')
        portable=portable.replace(':START',heap_setup+'\n\n:START',1).replace('-Xms1024m -Xmx4g','-Xms%AETHERFALL_GS_MIN_HEAP% %AETHERFALL_GS_HEAP_ARGS%',1)
        changes['Server/game-server/start.bat']=portable.replace('\r\n','\n').replace('\n','\r\n').encode()
        if 'Server/game-server/start-visible.cmd' in changes:
            # The original console helper uses host Java. The repack must always
            # enter its own bundled-Java launcher, including this optional entry.
            changes['Server/game-server/start-visible.cmd']=b'@echo off\r\ncall "%~dp0start.bat"\r\n'
    # Only build-stamp metadata changed in Commons. Keep the existing executable
    # dependency cohort, proving all actual code/resources are identical.
    with zipfile.ZipFile(build/'assembly-libs/commons-4.8-SNAPSHOT.jar') as new,zipfile.ZipFile(full/'Server/game-server/libs/commons-4.8-SNAPSHOT.jar') as old:
        if set(new.namelist())!=set(old.namelist()):raise ValueError('Commons dependency upgrade needs coordination')
        for name in new.namelist():
            if name!='META-INF/MANIFEST.MF' and new.read(name)!=old.read(name):raise ValueError('Commons executable dependency changed')
    release=encoded(dict(Product='Aion Aetherfall Repack',Version=args.version))
    changes.update({'release.json':release,'Client/Mods/release.json':release,'CHANGELOG.md':(repo/'CHANGELOG.md').read_bytes()})
    identity=read(full/'REPACK.json');identity.update(release=args.version.replace('.',''),productVersion=args.version,gameServerSha256=expected,updatedAt=datetime.now().astimezone().isoformat())
    identity['latestOriginalRefresh']=dict(version=args.version,build=str(build),receipt=str(backup/'receipt.json'),sourceRowsCopied=0)
    changes['REPACK.json']=encoded(identity)
    portable_state=read(full/'Client/Mods/portable-client.json');portable_state['release']=args.version
    changes['Client/Mods/portable-client.json']=encoded(portable_state)
    readme=(full/'README.txt').read_text(encoding='utf-8-sig').replace('REPACK '+old_version,'REPACK '+args.version,1)
    readme+='\nUPDATE '+args.version+'\nCurrent original companion arrival/follow/Attack, lift and quest 3074 repairs,\nshared route reuse, forced-movement control-state restoration and recipe lookup\nindexes. GameServer heap and navigation memory are configurable; explicit owner\nlimits remain available. Companion header decoration removed. Existing Stigma,\npet, broker and Hub fixes retained. See CHANGELOG.md. Database contents and local\nconnection settings are preserved. Actual gameplay and CPU/RAM checks remain.\n'
    changes['README.txt']=readme.encode()
    index=read(full/'FILES.json');entries={e['path'].casefold():e for e in index['files']}
    for rel,data in changes.items():
        entry=entries.get(rel.casefold())
        if entry is None:entry=dict(path=rel,mutable=False);index['files'].append(entry)
        entry.update(sha256=sha(data),bytes=len(data))
    index['fileCount']=len(index['files']);changes['FILES.json']=encoded(index,True)
    local=dict(changes)
    for rel,(_,new) in config_merges.items():local[rel]=merge_defaults((installed/rel).read_bytes(),(full/rel).read_bytes(),changes[rel])
    jvm='Server/game-server/config/jvm-options.cmd'
    if (installed/jvm).exists():local.pop(jvm,None)
    old_start=(full/'Server/game-server/start.bat').read_bytes()
    if (installed/'Server/game-server/start.bat').read_bytes()!=old_start:
        raise ValueError('Installed launcher has owner changes; review before updating it')
    local_state=read(installed/'Client/Mods/portable-client.json');local_state['release']=args.version
    local['Client/Mods/portable-client.json']=encoded(local_state)
    protected=['Server/game-server/config/mygs.properties','Server/login-server/config/myls.properties','Server/chat-server/config/mycs.properties','Client/Mods/Launcher/connection.json','Client/Mods/Launcher/preferences.json']
    protected_hashes={rel:sha((installed/rel).read_bytes()) for rel in protected if (installed/rel).is_file()}
    original_before=sha(original.read_bytes())
    full_records=deliver(full,changes,backup/'full-before')
    local_records=deliver(installed,local,backup/'installed-before')
    for rel,digest in protected_hashes.items():
        if sha((installed/rel).read_bytes())!=digest:raise ValueError('Protected local setting changed: '+rel)
    if sha(original.read_bytes())!=original_before:raise ValueError('Original changed during repack delivery; review current state')
    receipt=dict(version=args.version,previousVersion=old_version,build=str(build),previousBuild=str(previous),gameServerSha256=expected,
                 fullRepack=str(full),installedTest=str(installed),fullFiles=full_records,installedFiles=local_records,
                 protectedSettings=protected_hashes,commonsCodeAndResourcesIdentical=True,originalChanged=False,
                 sqlExecuted=False,testsRun=False,componentsStartedOrStopped=False,publicUpload=False,
                 gameplayAcceptance='Pending companion/lift/quest and CPU/RAM checks')
    (backup/'receipt.json').write_bytes(encoded(receipt))
    print('Updated full and installed repacks to',args.version,':',len(full_records),'and',len(local_records),'copied hashes verified.')
    print('Receipt:',backup/'receipt.json')


if __name__=='__main__':main()
