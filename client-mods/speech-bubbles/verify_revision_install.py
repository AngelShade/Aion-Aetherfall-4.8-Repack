"""Run the real revision installer against a separate disposable fixture."""
import argparse,hashlib,json,os,shutil,subprocess,time
from pathlib import Path

def main():
    p=argparse.ArgumentParser();p.add_argument('package',type=Path);a=p.parse_args()
    package=a.package.resolve();m=json.loads((package/'manifest.json').read_text());client=Path(m['clientRoot'])
    fixture=package.parent/('menu-install-fixture-'+str(time.time_ns()));fixture.mkdir()
    for e in m['files']:
        if e['original'] is None:continue
        source=client/e['path'];target=fixture/e['path'];target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
    prepared=fixture/'prepared';prepared.mkdir()
    for e in m['files']:
        target=prepared/e['path'];target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(package/e['path'],target)
    m['clientRoot']=str(fixture);(prepared/'manifest.json').write_text(json.dumps(m))
    environment=dict(os.environ);environment['PSModulePath']=str(Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/Modules')
    result=subprocess.run(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(Path(__file__).with_name('apply_menu_revision.ps1').resolve()),'-PreparedPath',str(prepared)],capture_output=True,text=True,env=environment)
    assert result.returncode==0,(result.stdout,result.stderr)
    sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
    for e in m['files']:assert sha(fixture/e['path'])==e['installed'],e['path']
    backup=next((fixture/'SpeechBubbles-backups').glob('menu-revision-*'))
    for e in m['files']:
        if e['original'] is not None:assert sha(backup/e['path'])==e['original'],e['path']
    # A second application must reject the changed preconditions before writing.
    before={e['path']:sha(fixture/e['path']) for e in m['files']}
    result=subprocess.run(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(Path(__file__).with_name('apply_menu_revision.ps1').resolve()),'-PreparedPath',str(prepared)],capture_output=True,text=True,env=environment)
    assert result.returncode!=0 and 'Client or prepared patch changed' in result.stderr
    assert all(sha(fixture/rel)==h for rel,h in before.items())
    # Removal of the complete speech patch must also recover both new artwork
    # archives from the originals added to the pre-speech backup receipt.
    receipt_path=fixture/m['speechReceipt'];receipt=json.loads(receipt_path.read_text())
    receipt['clientRoot']=str(fixture);receipt_path.write_text(json.dumps(receipt))
    source_backup=(client/m['speechReceipt']).parent;fixture_backup=receipt_path.parent
    for e in receipt['files']:
        if e['original'] is None:continue
        destination=fixture_backup/e['path']
        if destination.exists():continue
        destination.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source_backup/e['path'],destination)
    result=subprocess.run(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(Path(__file__).with_name('restore.ps1').resolve()),'-BackupPath',str(fixture_backup)],capture_output=True,text=True,env=environment)
    assert result.returncode==0,(result.stdout,result.stderr)
    for e in receipt['files']:
        if e['original'] is None:assert not (fixture/e['path']).exists()
        else:assert sha(fixture/e['path'])==e['original'],e['path']
    print('PASS: real revision installer, complete recovery backup, installed hashes, and later-change rejection. Real client untouched.')
    print('PASS: complete speech removal restores both original artwork archives, DLLs, menus, and launcher records.')
if __name__=='__main__':main()
