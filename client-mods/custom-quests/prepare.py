"""Build one Custom Quests support folder from maintained packages and installed data."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(__file__).resolve().parent
ARCHIVES = ('Data/Quest/Quest.pak','L10N/ENU/data/data.pak')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare(client, launcher_stage, output, backup_root):
    assert not output.exists() and ROOT not in output.parents and client not in output.parents
    assert not backup_root.is_relative_to(client)
    output.mkdir(parents=True)
    folder = output/'Mods/CustomQuests'
    shutil.copytree(SOURCE/'Quests',folder/'Quests')
    for name in ('README.txt','mod.json'):
        shutil.copy2(SOURCE/name,folder/name)
    (folder/'settings.json').write_text(json.dumps(dict(BackupRoot=str(backup_root)),indent=2)+'\n')
    launcher = launcher_stage/'Aetherfall Launcher.exe'
    shutil.copy2(launcher,output/launcher.name)
    # A small external client copy validates adoption through the actual built
    # loader. No game process, DLL attachment or full client copy is involved.
    fixture = output/'verification-client'
    shutil.copytree(output/'Mods',fixture/'Mods')
    originals = {}
    for name in ARCHIVES:
        source = client/name
        target = fixture/name
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(source,target)
        originals[name] = sha(source)
    report = output/'adoption.txt'
    result = subprocess.run([str(launcher),'--custom-quests-sync','--adopt-installed','--client',str(fixture),'--report',str(report)],capture_output=True,text=True)
    assert result.returncode == 0, report.read_text() if report.exists() else result.stderr
    assert all(sha(fixture/name)==expected for name,expected in originals.items()), 'Adoption rewrote a native archive'
    shutil.copy2(fixture/'Mods/CustomQuests/installed.json',folder/'installed.json')
    # The runtime loader's normal unchanged path must do no resource writes.
    before = {name:(fixture/name).stat().st_mtime_ns for name in ARCHIVES}
    report = output/'unchanged.txt'
    result = subprocess.run([str(launcher),'--custom-quests-sync','--client',str(fixture),'--report',str(report)],capture_output=True,text=True)
    assert result.returncode == 0 and 'unchanged' in report.read_text(), report.read_text()
    assert all((fixture/name).stat().st_mtime_ns==value for name,value in before.items())
    payloads = [p for p in (output/'Mods').rglob('*') if p.is_file()]+[output/launcher.name]
    sources = [p for p in SOURCE.rglob('*') if p.is_file() and '__pycache__' not in p.parts]
    sources += [ROOT/'client-mods/launcher'/name for name in ('Launcher.cs','CustomQuests.cs','QuestPakKeys.cs','build.ps1')]
    manifest = dict(feature='custom-quests-folder',client=str(client),originalOnly=True,
        folder='Mods/CustomQuests',initialQuestIds=[3999],nativeArchivesPreserved=originals,
        files=[dict(path=p.relative_to(output).as_posix(),after=sha(p)) for p in payloads],
        sourceHashes={str(p.relative_to(ROOT)):sha(p) for p in sources},
        validation=dict(adoption='actual built loader; zero archive writes',unchanged='no archive timestamp changes'),
        installed=False,gameplayVerified=False)
    (output/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print('Prepared one Custom Quests folder and normal launcher output; adoption and unchanged-startup checks passed.')


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('client','launcher-stage','output','backup-root'):
        p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args()
    prepare(a.client.resolve(strict=True),a.launcher_stage.resolve(strict=True),a.output.resolve(),a.backup_root.resolve())
