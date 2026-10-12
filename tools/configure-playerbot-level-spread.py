"""Install only the requested two uniform-level weights from a normal builder configuration, offline."""
import argparse, importlib, json, re, shutil, sys, hashlib, zipfile
from datetime import datetime
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'client-mods/playerbots'))
import stage_companion_update as shared
from remove_follow_recovery_offline import stopped
PATTERN=rb'(?m)^(gameserver\.playerbots\.population\.(min_level_percent|max_level_percent)[ \t]*=[ \t]*)([0-9]+)([ \t]*\r?$)'
def values(data):
    rows=[(m.group(2).decode(),int(m.group(3))) for m in re.finditer(PATTERN,data)]
    assert len(rows)==len(dict(rows))==2,'Level weights missing or duplicated'
    result=dict(rows)
    assert all(0<=n<=100 for n in result.values()) and sum(result.values())<=100
    return result
def replacement(before, builder):
    values(before)
    assert values(builder)=={'min_level_percent':0,'max_level_percent':0},'Builder does not request uniform levels'
    return re.sub(PATTERN,lambda m:m.group(1)+b'0'+m.group(4),before)
def validate(before, after, builder):
    assert after==replacement(before,builder),'Only the two requested level probabilities may change'
def builder_bytes(archive):
    with zipfile.ZipFile(archive) as assembly:
        current='game-server/'+importlib.import_module('playerbot-config-paths').CURRENT
        return assembly.read(current)
def verified(path,server,original,build):
    shared.validate_output(path);m=json.loads(path.read_text());rel=importlib.import_module('playerbot-config-paths').active(server).relative_to(server).as_posix()
    assert m['feature']=='playerbots-level-spread' and Path(m['path']).resolve()==(server/rel).resolve()
    recovery=path.parent/'playerbots.properties';source=build/'game-server/game-server.zip';builder=builder_bytes(source)
    assert m['original']==original==shared.sha(recovery)
    assert m['installed']==shared.sha(server/rel)
    assert m['builderConfiguration']==str(source) and m['builderSha256']==shared.sha(source)
    assert m['builderEntrySha256']==hashlib.sha256(builder).hexdigest()
    assert m['buildManifestSha256']==shared.sha(build/'build-manifest.json')
    assert m['jarSha256']==shared.sha(server/'libs/game-server-4.8-SNAPSHOT.jar')
    validate(recovery.read_bytes(),(server/rel).read_bytes(),builder)
    assert m['previous']==values(recovery.read_bytes()) and m['current']==values((server/rel).read_bytes())
    return dict(path=rel,original=original,installed=m['installed'],recovery=str(recovery),
                review=str(path),reviewSha256=shared.sha(path),levelSpread=True,
                builderConfiguration=str(source),builderSha256=m['builderSha256'],builderEntrySha256=m['builderEntrySha256'],previous=m['previous'],current=m['current'])
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True)
    p.add_argument('--expected-min',type=int,required=True);p.add_argument('--expected-max',type=int,required=True);a=p.parse_args()
    build=a.build.resolve();shared.validate_output(build);importlib.import_module('build-manifest').verify(build)
    server=ROOT/'target-deploy/game-server';config=importlib.import_module('playerbot-config-paths').active(server);source=build/'game-server/game-server.zip';builder=builder_bytes(source)
    before=config.read_bytes();assert values(before)==dict(min_level_percent=a.expected_min,max_level_percent=a.expected_max)
    after=replacement(before,builder);stopped()
    receipt=shared.DEV_ROOT/'archives/server/game-server/backups'/('playerbots-level-spread-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    receipt.mkdir(parents=True,exist_ok=False);backup=receipt/'playerbots.properties';shutil.copy2(config,backup)
    assert backup.read_bytes()==before;stopped();assert config.read_bytes()==before,'Owner changed level configuration during review'
    try:config.write_bytes(after);assert config.read_bytes()==after
    except Exception:shutil.copy2(backup,config);raise
    path=receipt/'manifest.json';path.write_text(json.dumps(dict(feature='playerbots-level-spread',path=str(config),original=shared.sha(backup),
        installed=shared.sha(config),previous=values(before),current=values(after),builderConfiguration=str(source),builderSha256=shared.sha(source),
        builderEntrySha256=hashlib.sha256(builder).hexdigest(),buildManifestSha256=shared.sha(build/'build-manifest.json'),jarSha256=shared.sha(server/'libs/game-server-4.8-SNAPSHOT.jar'),otherBytesPreserved=True),indent=2))
    verified(path,server,shared.sha(backup),build);print('OK: requested uniform initial level weights only; review:',path)
if __name__=='__main__':main()
