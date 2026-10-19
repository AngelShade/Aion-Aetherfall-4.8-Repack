"""Deliver the recorded original 0.93 repairs to a repack without copying owner data."""
import argparse
import copy
from datetime import datetime
import hashlib
import json
from pathlib import Path
import shutil
from refresh_original_repack import preserve_repack_network


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def sha(data):
    return hashlib.sha256(data).hexdigest()


def encoded(value, compact=False):
    return (json.dumps(value, **({'separators': (',', ':')} if compact else {'indent': 2}))+'\n').encode()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace', type=Path, required=True)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--package', type=Path, required=True)
    parser.add_argument('--backup', type=Path, required=True)
    args=parser.parse_args()
    repo=Path(__file__).resolve().parents[2]
    dev,client,package,backup=(p.resolve() for p in (args.workspace,args.client,args.package,args.backup))
    if backup.exists() or backup.is_relative_to(package) or backup.is_relative_to(client):
        raise ValueError('Use a fresh external backup directory')
    if (package/'Server/database/data').exists() or read(package/'release.json')['Version']!='0.92.3':
        raise ValueError('Expected the uninitialized full 0.92.3 repack')
    build=dev/'staging/target/companion-range-20261018'
    build_manifest=read(build/'build-manifest.json')
    expected_server=build_manifest['artifacts']['game-server/game-server-4.8-SNAPSHOT.jar']
    sources={}
    receipts=[
        'archives/server/backups/continuous-navigation093-original-20261018/manifest.json',
        'archives/client/stigma-label-original-20261018/receipt.json',
        'archives/client/launcher-graphics-path-repair-20261018/receipt.json',
        'archives/server/backups/warning-repair-20261018/manifest.json',
        'archives/server/backups/companion-range-20261018/manifest.json',
    ]
    for relative in receipts:
        receipt=read(dev/relative)
        for entry in receipt['files']:
            source=Path(entry['path'])
            if not source.is_absolute():
                source=client/source
            if source.is_relative_to(client):
                rel='Client/'+source.relative_to(client).as_posix()
            elif source.is_relative_to(repo/'target-deploy'):
                rel='Server/'+source.relative_to(repo/'target-deploy').as_posix()
            else:
                continue
            if preserve_repack_network(rel):
                continue
            if rel in ['Client/Mods/InstalledMods.json','Client/Mods/release.json']:
                continue
            if rel.endswith('/ReShade.ini'):
                # ReShade rewrites overlay preferences while playing. Keep the
                # repack's own already-portable settings rather than owner UI state.
                continue
            sources[rel]=(source,entry.get('after') or entry.get('staged'))
    # This cohort includes the unchanged public key and archives paired with the
    # current signed companion controls. Never mix an old signature/key baseline.
    for rel in ['Pub.key','Addon.key','bin32/bin32.pak.sig','Data/func_pet/func_pet.pak.sig',
                'Plugin/RelicCalc/RelicCalc.pak','Plugin/RelicCalc/RelicCalc.pak.sig']:
        path=client/rel
        sources.setdefault('Client/'+rel,(path,sha(path.read_bytes())))
    server=repo/'target-deploy/game-server/libs/game-server-4.8-SNAPSHOT.jar'
    if sha(server.read_bytes())!=expected_server or sha((build/'game-server/game-server-4.8-SNAPSHOT.jar').read_bytes())!=expected_server:
        raise ValueError('Original GameServer no longer matches the current normal builder output')
    sources['Server/game-server/libs/game-server-4.8-SNAPSHOT.jar']=(build/'game-server/game-server-4.8-SNAPSHOT.jar',expected_server)
    if len([p for p in sources if p.endswith('.pbroads')])!=155:
        raise ValueError('Expected all 155 recorded route resources')
    changes={}
    metadata=set(read(package/'Client/Mods/portable-client.json')['metadataFiles'])
    original_root=str(client)
    portable_root=str(package/'Client')
    def portable(value):
        if isinstance(value,dict):return {k:portable(v) for k,v in value.items()}
        if isinstance(value,list):return [portable(v) for v in value]
        if isinstance(value,str):return value.replace(original_root,portable_root).replace(client.as_posix(),(package/'Client').as_posix())
        return value
    for rel,(source,expected) in sources.items():
        data=source.read_bytes()
        if sha(data)!=expected:
            raise ValueError('Original changed after recorded delivery: '+str(source))
        if rel.startswith('Client/Mods/') and source.suffix=='.json':
            value=read(source);updated=portable(value)
            data=encoded(updated)
            if updated!=value:metadata.add(rel[len('Client/'):])
        elif rel.endswith('/ReShade.ini'):
            data=portable(data.decode('utf-8-sig')).encode()
        changes[rel]=data
    changes['CHANGELOG.md']=(repo/'CHANGELOG.md').read_bytes()
    release=read(repo/'release.json')
    if release['Version']!='0.93':raise ValueError('Expected original release 0.93')
    changes['release.json']=encoded(release)
    changes['Client/Mods/release.json']=encoded(release)
    inventory=copy.deepcopy(read(client/'Mods/InstalledMods.json'))
    for entries in inventory['features'].values():
        for entry in entries:
            rel='Client/'+entry['path']
            if rel in changes:entry.update(installedSha256=sha(changes[rel]),bytes=len(changes[rel]))
    changes['Client/Mods/InstalledMods.json']=encoded(portable(inventory))
    state=read(package/'Client/Mods/portable-client.json')
    state.update(preparedRoot=portable_root,gameDllSha256=sha(changes['Client/bin64/Game.dll']),
                 aionBinSha256=sha((package/'Client/bin64/aion.bin').read_bytes()),metadataFiles=sorted(metadata),release='0.93')
    changes['Client/Mods/portable-client.json']=encoded(state)
    identity=read(package/'REPACK.json')
    identity.update(release='093',productVersion='0.93',gameServerSha256=expected_server,updatedAt=datetime.now().astimezone().isoformat())
    identity['original093Sync']=dict(build=str(build),receipts=receipts,originalClient=str(client),
                                    routeMaps=155,sourceRowsCopied=0,receipt=str(backup/'receipt.json'))
    changes['REPACK.json']=encoded(identity)
    readme=(package/'README.txt').read_text(encoding='utf-8-sig').replace('REPACK 0.92.3','REPACK 0.93',1)
    readme+='''\nORIGINAL DEVELOPMENT UPDATE 0.93\nPrepared routes for 155 maps, continuous bot navigation, improved companion\nfollowing and role spacing, party-wide 2/4/8 m distance controls, and magical\nquest reward corrections. Profile > Stigma supports equip/remove and an Upgrade\nbutton with its English label; native costs and materials remain. Earlier pet,\nbroker, population and Setup shutdown repairs are retained. See CHANGELOG.md.\nThese files come from the current normal original build. Movement, quest, Stigma\nand performance acceptance remain in-game checks. No personal database is copied.\n'''
    changes['README.txt']=readme.encode()
    index=read(package/'FILES.json');entries={e['path'].casefold():e for e in index['files']}
    # Only hash the known affected destinations; unchanged bulk archives are not scanned.
    writes={}
    for rel,data in changes.items():
        target=package/rel
        if not target.resolve().is_relative_to(package):raise ValueError('Invalid destination')
        if not target.is_file() or target.stat().st_size!=len(data) or sha(target.read_bytes())!=sha(data):
            writes[rel]=data
        entry=entries.get(rel.casefold())
        if entry is None:
            entry=dict(path=rel,mutable=False);index['files'].append(entry);entries[rel.casefold()]=entry
        entry.update(sha256=sha(data),bytes=len(data))
    index['fileCount']=len(index['files'])
    writes['FILES.json']=encoded(index,True)
    backup.mkdir(parents=True)
    records=[]
    for rel,data in writes.items():
        target=package/rel;saved=backup/rel
        before=sha(target.read_bytes()) if target.is_file() else None
        if before:
            saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(target,saved)
            if sha(saved.read_bytes())!=before:raise ValueError('Backup checksum mismatch')
        records.append(dict(path=rel,before=before,sha256=sha(data),bytes=len(data)))
    copied=[]
    try:
        for record in records:
            rel=record['path'];target=package/rel
            if (sha(target.read_bytes()) if target.is_file() else None)!=record['before']:
                raise ValueError('Repack changed during delivery: '+rel)
            copied.append(record);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(writes[rel])
            if sha(target.read_bytes())!=record['sha256']:raise ValueError('Copied checksum mismatch: '+rel)
    except BaseException:
        for record in reversed(copied):
            target=package/record['path'];saved=backup/record['path']
            if record['before']:shutil.copy2(saved,target)
            elif target.exists():target.unlink()
        raise
    receipt=dict(version='0.93',build=str(build),gameServerSha256=expected_server,fullRepack=str(package),
                 files=records,routeMaps=155,originalChanged=False,installedTestUpdated=False,
                 sqlExecuted=False,testsRun=False,publicUpload=False,
                 verification='Recorded original delivery hashes, exact normal GS builder artifact and affected copied hashes',
                 gameplayAcceptance='Pending player checks')
    (backup/'receipt.json').write_bytes(encoded(receipt))
    print('Full repack 0.93 installed:',len(records),'copied hashes;',receipt['routeMaps'],'route maps; no owner data copied.')
    print('Receipt:',backup/'receipt.json')


if __name__=='__main__':main()
