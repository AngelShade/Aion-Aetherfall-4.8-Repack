"""Record reviewed runtime recompilation/settings drift without replacing runtime files."""
import copy,json,shutil,subprocess
from datetime import datetime
from stage_companion_update import ROOT,DEV_ROOT,sha,methods,receipt_paths

def main():
    server=ROOT/'target-deploy/game-server';previous=receipt_paths(server,'playerbots-recruitment-*/manifest.json')[-1]
    prior=json.loads(previous.read_text());evidence=[]
    assert sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==prior['baseJarSha256']
    for e in prior['files']:
        actual=sha(server/e['path'])
        if actual==e['installed']:continue
        path=e['path'];old=previous.parent/path;assert sha(old)==e['original'],path
        if path.startswith('cache/classes/ai/instance/rakes/') and path.endswith('.class'):
            name=path.removeprefix('cache/classes/').removesuffix('.class')
            assert methods(server/'cache/classes',name)==methods(previous.parent/'cache/classes',name),path
            shape=lambda root:subprocess.check_output(['javap','-p','-s','-cp',str(root),name.replace('/','.')],text=True)
            assert shape(server/'cache/classes')==shape(previous.parent/'cache/classes'),path
            reason='Native script recompilation; all executable methods and member schemas identical'
        elif path=='config/main/playerbots.properties':
            # One observed, preserved setting change; do not accept arbitrary drift.
            assert e['original']==e['installed']
            assert old.read_bytes().replace(b'gameserver.playerbots.instance_follow = false',b'gameserver.playerbots.instance_follow = true')==(server/path).read_bytes()
            reason='Preserve observed legacy instance_follow false -> true; enable/summon remain true'
        else:raise AssertionError('Unreviewed installed change: '+path)
        evidence.append(dict(path=path,previous=e['installed'],current=actual,reason=reason))
    assert len(evidence)==8 and sum(e['path'].endswith('.class') for e in evidence)==7
    backup=DEV_ROOT/'archives/server/game-server/backups'/('playerbots-recruitment-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    backup.mkdir(parents=True);m=copy.deepcopy(prior)
    for e in m['files']:
        dest=backup/e['path'];dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(server/e['path'],dest)
        e['original']=e['installed']=sha(dest);assert sha(server/e['path'])==e['installed']
    m.update(feature='playerbot-runtime-baseline-reconciliation',scope='PB-REPAIR-PACKAGING-001 prerequisite: verified current cache/settings baseline',previousReceipt=str(previous),baselineReconciliation=evidence,incrementalChangedMethods=[],newClasses=[])
    # This is a read-only checkpoint, not installation or acceptance of the faulty tick.
    (backup/'manifest.json').write_text(json.dumps(m,indent=2))
    (backup/'installed.json').write_text(json.dumps(dict(mode='read-only baseline checkpoint; runtime unchanged; missing Appearance tick unresolved',files=m['files']),indent=2))
    print('OK: reviewed seven equivalent script cache classes and one preserved setting; runtime unchanged:',backup)

if __name__=='__main__':main()
