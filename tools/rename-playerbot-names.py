"""Guarded offline legacy world-bot rename, with review/recovery and independent native SQL verification."""
import argparse, importlib, json, msvcrt, shutil, subprocess, sys
from datetime import datetime
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'client-mods/playerbots'))
import stage_companion_update as shared
from remove_follow_recovery_offline import stopped
SERVER=ROOT/'target-deploy/game-server'
CLASS='com.aionemu.gameserver.services.playerbot.PlayerBotNameMigration'

def execute(output,meta,mode):
    log=output/(mode+'.log')
    with log.open('w',encoding='utf-8') as stream:
        result=subprocess.run(['java','-Xverify:all','-cp',meta['classpath'],CLASS,mode,str(SERVER),str(output)],cwd=output,stdout=stream,stderr=subprocess.STDOUT)
    if result.returncode: raise RuntimeError('Native bot naming '+mode+' refused; retain recovery: '+str(output))
    print(log.read_text(encoding='utf-8').strip())

def guard(output,meta):
    stopped()
    build=Path(meta['build']);importlib.import_module('build-manifest').verify(build)
    assert shared.sha(SERVER/'libs/game-server-4.8-SNAPSHOT.jar')==meta['gameJarSha256']==shared.sha(build/'game-server/game-server-4.8-SNAPSHOT.jar')
    assert shared.sha(ROOT/'tools/java/PlayerBotNameMigration.java')==meta['sourceSha256']
    assert shared.sha(ROOT/'tools/java/PlayerBotNameMigrationCheck.java')==meta['checkSourceSha256']
    assert shared.sha(build/'names-migration-check.txt')==meta['offlineMigrationChecksSha256']
    assert 'OK: 16 migration transaction/rollback/fingerprint/history/replay checks; synthetic JDBC only' in (build/'names-migration-check.txt').read_text()
    for rel,digest in meta['classes'].items(): assert shared.sha(Path(meta['classesRoot'])/rel)==digest
    for path,digest in meta['libraries'].items(): assert shared.sha(Path(path))==digest
    for rel,digest in meta['configuration'].items(): assert shared.sha(SERVER/rel)==digest
    for name,digest in meta['plan'].items(): assert shared.sha(output/name)==digest
    checks=json.loads((Path(meta['review'])/'regression/checks.json').read_text())
    expected=importlib.import_module('check-source-build').TESTS
    assert set(checks)==set(expected)|{'ShutdownHookCheck'}
    for name,count in dict(expected,ShutdownHookCheck=14).items(): assert checks[name].startswith('OK: '+str(count)+' ')

def main():
    if not __debug__: raise RuntimeError('Verification requires enabled assertions')
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--review',type=Path,required=True);p.add_argument('--apply',action='store_true');a=p.parse_args()
    review=a.review.resolve();shared.validate_output(review)
    build=Path(json.loads((review/'release-context.json').read_text())['build'])
    assert json.loads((review/'release-plan.json').read_text())['scope']=='PB-REPAIR-NAMES-017'
    importlib.import_module('build-manifest').verify(build);stopped()
    lock_path=shared.DEV_ROOT/'tooling/locks/game-server-release.lock'
    with lock_path.open('a+b') as lock:
        if lock_path.stat().st_size==0: lock.write(b'0');lock.flush()
        lock.seek(0)
        try: msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
        except OSError: raise RuntimeError('Another chat is building/delivering GameServer; wait before native names migration')
        try: process(review,build,a.apply)
        finally: lock.seek(0);msvcrt.locking(lock.fileno(),msvcrt.LK_UNLCK,1)

def process(review,build,apply):
    directory=review/'names-migration'
    if (directory/'prepared.json').is_file():
        meta=json.loads((directory/'prepared.json').read_text())
        assert meta['review']==str(review) and meta['build']==str(build) and meta['scope']=='PB-REPAIR-NAMES-017'
        guard(directory,meta)
        if not apply:
            print('Existing names review; '+('prior commit attempt retained, no replay' if (directory/'commit-attempted.txt').exists() else 'SQL unchanged')+':',directory)
            return
        return commit(directory,meta)
    directory.mkdir(exist_ok=False)
    libs=[f for f in sorted((SERVER/'libs').glob('*.jar')) if f.name not in {'game-server-4.8-SNAPSHOT.jar','commons-4.8-SNAPSHOT.jar','playerbot-recruitment-fix.jar'}]
    cp=';'.join(map(str,[directory/'classes',build/'game-server/game-server-4.8-SNAPSHOT.jar',build/'commons/commons-4.8-SNAPSHOT.jar',*libs]))
    source=ROOT/'tools/java/PlayerBotNameMigration.java'
    subprocess.run(['javac','-encoding','UTF-8','-Xlint:deprecation,unchecked','-Werror','-cp',cp,'-d',str(directory/'classes'),str(source)],check=True)
    meta=dict(scope='PB-REPAIR-NAMES-017',review=str(review),build=str(build),classpath=cp,classesRoot=str(directory/'classes'),
        sourceSha256=shared.sha(source),checkSourceSha256=shared.sha(ROOT/'tools/java/PlayerBotNameMigrationCheck.java'),
        offlineMigrationChecksSha256=shared.sha(build/'names-migration-check.txt'),gameJarSha256=shared.sha(build/'game-server/game-server-4.8-SNAPSHOT.jar'),
        classes={f.relative_to(directory/'classes').as_posix():shared.sha(f) for f in (directory/'classes').rglob('*.class')},
        libraries={str(f):shared.sha(f) for f in libs},configuration={rel:shared.sha(SERVER/rel) for rel in ('config/network/database.properties','config/main/name.properties','config/mygs.properties')},plan={})
    # Only a verified installed current source release may write existing native names.
    receipt=shared.receipt_paths(SERVER,'playerbots-source-build-*/manifest.json')[-1]
    installed=json.loads(receipt.read_text());assert installed['scope']==meta['scope'] and (receipt.parent/'installed.json').is_file()
    assert installed['files'][0]['installed']==meta['gameJarSha256'];meta['sourceReleaseReceipt']=str(receipt.parent)
    guard(directory,meta);execute(directory,meta,'plan')
    meta['plan']={name:shared.sha(directory/name) for name in ('names.tsv','before.txt','expected.txt','restore-names.sql')}
    (directory/'prepared.json').write_text(json.dumps(meta,indent=2))
    if not apply: print('Names review ready; native SQL unchanged:',directory);return
    commit(directory,meta)

def commit(directory,meta):
    assert not (directory/'commit-attempted.txt').exists(), 'Do not replay an attempted commit; verify its recorded native state first'
    guard(directory,meta)
    backup=shared.DEV_ROOT/'archives/server/game-server/backups'/('playerbots-names-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    backup.mkdir(exist_ok=False)
    for name in (*meta['plan'],'prepared.json'): shutil.copy2(directory/name,backup/name)
    for name,digest in meta['plan'].items(): assert shared.sha(backup/name)==digest
    meta['recovery']=str(backup);(directory/'prepared.json').write_text(json.dumps(meta,indent=2));shutil.copy2(directory/'prepared.json',backup/'prepared.json')
    guard(directory,meta)
    try:
        execute(directory,meta,'apply')
        execute(directory,meta,'verify') # New JVM/connection reads committed native state.
    finally:
        for name in ('apply.log','verify.log','history-ids.txt','commit-attempted.txt','restore-committed.sql','verified.txt'):
            if (directory/name).is_file(): shutil.copy2(directory/name,backup/name)
    manifest=dict(meta,artifacts={f.name:shared.sha(f) for f in backup.iterdir() if f.is_file()})
    (backup/'committed.json').write_text(json.dumps(manifest,indent=2))
    print('OK: independently verified name-only migration; recovery:',backup)

if __name__=='__main__': main()
