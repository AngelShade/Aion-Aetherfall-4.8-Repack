"""Install the single Custom Quests folder and normal launcher output on the original."""
import argparse
from datetime import datetime,timezone
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

ROOT=Path(__file__).resolve().parents[2]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def encode(value):
    return (json.dumps(value,indent=2)+'\n').encode()


def closed():
    query="Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object { $_.Name -in @('aion.bin','aion.exe','Aetherfall Launcher.exe') } | Select-Object Name,ExecutablePath,CommandLine | ConvertTo-Json -Compress"
    result=subprocess.run(['powershell.exe','-NoProfile','-Command',query],capture_output=True,text=True,check=True)
    assert not result.stdout.strip(),'Close the client and its launcher normally before installation'
    return dict(checkedAt=datetime.now(timezone.utc).isoformat(),clientAndLauncherClosed=True,method='Successful Win32_Process enumeration')


def install(stage,backup):
    manifest=json.loads((stage/'manifest.json').read_text())
    assert manifest['feature']=='custom-quests-folder' and not manifest['installed']
    client=Path(manifest['client'])
    assert not backup.exists() and ROOT not in backup.parents and client not in backup.parents
    assert not (client/'Mods/CustomQuests').exists(),'Custom Quests is already installed; reconcile the existing folder'
    for name,expected in manifest['sourceHashes'].items():
        assert sha(ROOT/name)==expected,'Prepared source changed: '+name
    for entry in manifest['files']:
        assert sha(stage/entry['path'])==entry['after'],'Staged file changed'
    for name,expected in manifest['nativeArchivesPreserved'].items():
        assert sha(client/name)==expected,'Installed native archive changed: '+name
    validation=stage.parents[2]/'diagnostics/custom-quests-folder-20261019/validation.json'
    checks=json.loads(validation.read_text())
    assert checks['newQuestAutomaticMerge'].startswith('passed') and checks['duplicateNativeId'].startswith('rejected')
    writes={client/e['path']:(stage/e['path']).read_bytes() for e in manifest['files']}
    readme=ROOT/'client-mods/launcher/README.md'
    writes[client/'Mods/Launcher/README.md']=readme.read_bytes()
    mods_path=client/'Mods/InstalledMods.json'
    mods=json.loads(mods_path.read_text())
    group=[dict(path=e['path'],installedSha256=e['after'],bytes=(stage/e['path']).stat().st_size)
           for e in manifest['files'] if e['path'].startswith('Mods/CustomQuests/')]
    mods['features']['CustomQuests']=group
    for e in mods['features'].get('Launcher',[]):
        target=client/e['path']
        if target in writes:
            e.update(installedSha256=hashlib.sha256(writes[target]).hexdigest(),bytes=len(writes[target]))
    writes[mods_path]=encode(mods)
    guide=client/'Mods/README.txt'
    text=guide.read_text()
    assert 'CustomQuests' not in text
    text=text.replace('Runtime locations','Custom Quests      All custom quests under Mods/CustomQuests/Quests; loaded automatically before Play.\n\nRuntime locations',1)
    writes[guide]=text.encode()
    preserved={n:sha(client/n) for n in ('Data/Quest/Quest.pak','L10N/ENU/data/data.pak',
        'bin64/Game.dll','Mods/Launcher/connection.json','Mods/Launcher/preferences.json')}
    server=ROOT/'target-deploy/game-server/libs/game-server-4.8-SNAPSHOT.jar'
    server_hash=sha(server)
    closure=closed()
    backup.mkdir(parents=True)
    records=[]
    for index,(target,data) in enumerate(writes.items()):
        before=sha(target) if target.exists() else None
        saved=backup/str(index)
        if before is not None:
            shutil.copy2(target,saved)
            assert sha(saved)==before
        records.append(dict(path=str(target),before=before,after=hashlib.sha256(data).hexdigest(),backup=str(saved) if before else None))
    receipt=dict(feature=manifest['feature'],status='prepared',client=str(client),files=records,closure=closure,
        preserved=preserved,preservedServerJar=server_hash,validation=str(validation),questIds=[3999],originalOnly=True,
        deliverySourceSha256=sha(Path(__file__)),
        componentsStarted=False,repackChanged=False,sqlExecuted=False,gameplayVerified=False)
    receipt_path=backup/'receipt.json'
    receipt_path.write_bytes(encode(receipt))
    closed()
    for record in records:
        target=Path(record['path'])
        assert (sha(target) if target.exists() else None)==record['before'],'Target changed during preparation'
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(writes[target])
        assert sha(target)==record['after']
    assert all(sha(client/n)==expected for n,expected in preserved.items())
    assert sha(server)==server_hash
    receipt.update(status='installed',installedAt=datetime.now(timezone.utc).isoformat())
    receipt_path.write_bytes(encode(receipt))
    ip=ROOT/'docs/INSTALLED_MODS.json'
    inventory=json.loads(ip.read_text())
    by_path={e['path'].lower():e for e in inventory['clientFiles']}
    for target,data in writes.items():
        name=target.relative_to(client).as_posix()
        entry=by_path.get(name.lower())
        if entry is None:
            entry=dict(path=name);inventory['clientFiles'].append(entry)
        entry['sha256']=hashlib.sha256(data).hexdigest()
    inventory['lastCustomQuestsFolderDelivery']=dict(checkedAt='2026-10-19',receipt=str(receipt_path),
        folder='Mods/CustomQuests',questIds=[3999],source='client-mods/custom-quests',originalOnly=True,
        verification='Normal launcher build; actual loader adoption/unchanged/additional-package/conflict checks; native archive/setting/server preservation; copied hashes',
        gameplayAcceptance='User launcher Mods entry, next Play and quest/menu/marker acceptance pending')
    inventory['clientReceipts'].append(str(receipt_path));ip.write_bytes(encode(inventory))
    manifest['installed']=True;manifest['receipt']=str(receipt_path)
    (stage/'manifest.json').write_bytes(encode(manifest))
    print('Installed Custom Quests folder and launcher; '+str(len(records))+' copied hashes verified. Native archives, settings and server JAR preserved. Receipt: '+str(receipt_path))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--stage',type=Path,required=True)
    p.add_argument('--backup',type=Path,required=True)
    a=p.parse_args()
    install(a.stage.resolve(strict=True),a.backup.resolve())
