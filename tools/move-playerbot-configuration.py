"""Backed-up offline move of the owner's exact bytes to the only supported configuration home."""
import argparse, hashlib, importlib, json, shutil, sys
from datetime import datetime
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'client-mods/playerbots'))
import stage_companion_update as shared
from remove_follow_recovery_offline import stopped
paths=importlib.import_module('playerbot-config-paths')
SCOPE='PB-CONFIG-DIRECTORY-057'
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def original_guard(server):
    prior=shared.receipt_paths(server,'playerbots-source-build-*/manifest.json')[-1]
    baseline=json.loads(prior.read_text());guards=[e for e in baseline['guardFiles'] if e['path']==paths.LEGACY]
    assert len(guards)==1,'Legacy file is not part of the installed guarded baseline'
    return prior,guards[0]['installed']
def documentation_bridge(server,expected,actual):
    if actual==expected:return None
    candidates=sorted((shared.DEV_ROOT/'archives/server/game-server/backups').glob('playerbots-config-docs-*/manifest.json'))
    for receipt in reversed(candidates):
        m=json.loads(receipt.read_text())
        if m.get('original')!=expected or m.get('installed')!=actual:continue
        assert m['feature']=='playerbots-config-documentation' and Path(m['live']).resolve()==(server/paths.LEGACY).resolve()
        assert sha(receipt.parent/'playerbots.properties')==expected and sha(m['staged'])==actual
        assert m['jarSha256']==sha(server/'libs/game-server-4.8-SNAPSHOT.jar')
        docs=importlib.import_module('document-playerbot-config');before=docs.properties((receipt.parent/'playerbots.properties').read_text(encoding='utf-8'))
        current=docs.properties((server/paths.LEGACY).read_text(encoding='utf-8'))
        assert current==m['effective']==m['addedCompiledDefaults']|before
        return dict(path=str(receipt),sha256=sha(receipt))
    raise AssertionError('Legacy configuration changed without a matching preservation receipt')
def validate_locations(server):
    server=Path(server);old=server/paths.LEGACY;new=server/paths.CURRENT
    assert old.is_file() and not old.is_symlink(),'Missing or redirected legacy configuration'
    assert not new.exists() and not new.is_symlink(),'New configuration already exists; refuse to overwrite owner choices'
    assert new.resolve().is_relative_to(server.resolve()) and old.resolve().is_relative_to(server.resolve())
    return old,new
def verified(receipt,server,expected,build,plan):
    shared.validate_output(receipt);m=json.loads(receipt.read_text());old=server/paths.LEGACY;new=server/paths.CURRENT
    assert m['feature']=='playerbots-configuration-location' and m['scope']==SCOPE
    assert m['path']==str(new) and m['previousPath']==paths.LEGACY and m['baselineSha256']==expected
    assert not old.exists() and not old.is_symlink() and new.is_file() and not new.is_symlink()
    assert sha(receipt.parent/'playerbots.properties')==sha(new)==m['original']==m['installed']
    assert m['buildManifestSha256']==sha(build/'build-manifest.json') and m['jarSha256']==sha(server/'libs/game-server-4.8-SNAPSHOT.jar')
    assert plan['runtimeResourceInputs'][paths.CURRENT] is None and paths.CURRENT not in plan['resources']
    if m['documentation']:
        proof=m['documentation'];assert sha(proof['path'])==proof['sha256']
        doc=json.loads(Path(proof['path']).read_text());assert doc['original']==expected and doc['installed']==m['installed']
    else:assert expected==m['installed']
    return dict(path=paths.CURRENT,previousPath=paths.LEGACY,original=m['original'],installed=m['installed'],
        recovery=str(receipt.parent/'playerbots.properties'),review=str(receipt),reviewSha256=sha(receipt),configurationRelocation=True,
        baselineSha256=expected,documentation=m['documentation'])
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--review',type=Path,required=True);a=p.parse_args()
    review=a.review.resolve();shared.validate_output(review);plan=json.loads((review/'release-plan.json').read_text());assert plan['scope']==SCOPE
    build=Path(json.loads((review/'release-context.json').read_text())['build']);importlib.import_module('build-manifest').verify(build)
    importlib.import_module('check-source-build').validate(build,review/'regression');importlib.import_module('check-source-browser').validate(build,review/'browser')
    server=ROOT/'target-deploy/game-server';old,new=validate_locations(server);prior,expected=original_guard(server)
    assert str(prior)==plan['priorReceipt'];data=old.read_bytes();digest=sha(old);documentation=documentation_bridge(server,expected,digest)
    stopped();backup=shared.DEV_ROOT/'archives/server/game-server/backups'/('playerbots-config-location-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'));backup.mkdir(parents=True,exist_ok=False)
    shutil.copy2(old,backup/'playerbots.properties');assert (backup/'playerbots.properties').read_bytes()==data
    stopped();validate_locations(server);assert old.read_bytes()==data
    new.parent.mkdir(parents=True,exist_ok=True)
    try:
        with new.open('xb') as target:target.write(data)
        assert new.read_bytes()==data;old.unlink()
    except Exception:
        if not old.exists():shutil.copy2(backup/'playerbots.properties',old)
        if new.is_file() and new.read_bytes()==data:new.unlink()
        raise
    receipt=backup/'manifest.json';receipt.write_text(json.dumps(dict(feature='playerbots-configuration-location',scope=SCOPE,path=str(new),previousPath=paths.LEGACY,
        baselineSha256=expected,original=digest,installed=digest,documentation=documentation,buildManifestSha256=sha(build/'build-manifest.json'),
        jarSha256=sha(server/'libs/game-server-4.8-SNAPSHOT.jar'),sourceRemoved=True,ownerBytesUnchanged=True),indent=2),encoding='utf-8')
    verified(receipt,server,expected,build,plan);print('OK: owner configuration moved byte-for-byte; old location removed; receipt:',receipt)
if __name__=='__main__':main()
