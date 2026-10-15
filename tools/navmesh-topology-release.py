"""Bind native directed topology to the normal GameServer artifact and preserved mesh catalog."""
import hashlib, re, subprocess
from pathlib import Path
from importlib import import_module

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def prepare(build, topology, out, server):
    topology = topology.resolve()
    assert not topology.is_relative_to(ROOT), 'Use external topology output'
    helper = import_module('navmesh-release')
    index = topology / 'topology.properties'
    before = {str(p): sha(p) for p in sorted(topology.iterdir()) if p.suffix in {'.properties', '.pbtopology'}}
    data = helper.properties(index)
    assert data['format'] == 'aion-topology-1'
    catalog = server / 'data/navmesh/report.properties'
    assert data['report.sha256'] == sha(catalog), 'Topology differs from installed geometry catalog'
    maps = helper.properties(catalog)
    ids = sorted(int(k.split('.')[1]) for k in maps if k.endswith('.directory') and k.startswith('map.'))
    assert {int(k.split('.')[1]) for k in data if k.startswith('map.') and k.endswith('.sha256')} == set(ids)
    libs = import_module('build-manifest').libraries(build)
    jars = [build / 'game-server/game-server-4.8-SNAPSHOT.jar', build / 'commons/commons-4.8-SNAPSHOT.jar', *libs]
    cp = ';'.join(map(str, jars))
    log = out / 'topology-verification.txt'
    # The product utility reconstructs directed SCCs and every arc from authenticated native tiles.
    with log.open('w') as output:
        subprocess.run(['java', '-Xmx2g', '-cp', cp,
                        'com.aionemu.gameserver.services.playerbot.PlayerBotNavmeshTopology',
                        str(server / 'data/navmesh'), str(topology), '--verify'],
                       stdout=output, stderr=subprocess.STDOUT, check=True)
    assert 'PASS: complete topology/native directed links and all arrays verified' in log.read_text()
    assert before == {str(p): sha(p) for p in sorted(topology.iterdir()) if p.suffix in {'.properties', '.pbtopology'}}
    selected = [index, *(topology / (str(i) + '.pbtopology') for i in ids)]
    assert set(map(str, selected)) == set(before), 'Unexpected topology package members'
    files = []
    for source in selected:
        assert not source.is_symlink() and source.resolve().parent == topology
        if source != index:
            ident = source.stem
            assert sha(source) == data['map.' + ident + '.sha256']
            assert data['map.' + ident + '.fingerprint'] == Path(maps['map.' + ident + '.directory']).name
        rel = 'data/navmesh/topology/' + source.name
        target = helper.target_path(server, rel)
        files.append(dict(path=rel, source=str(source), original=sha(target) if target.exists() else None, installed=sha(source)))
    return dict(files=files, catalog=str(catalog), catalogSha256=sha(catalog), maps=len(ids),
                verification=str(log), verificationSha256=sha(log),
                buildManifestSha256=sha(build / 'build-manifest.json'))


def validate(evidence, build, server, installed=False):
    helper = import_module('navmesh-release')
    assert sha(build / 'build-manifest.json') == evidence['buildManifestSha256']
    assert sha(server / 'data/navmesh/report.properties') == evidence['catalogSha256']
    assert sha(evidence['verification']) == evidence['verificationSha256']
    assert len(evidence['files']) == evidence['maps'] + 1
    assert len({e['path'] for e in evidence['files']}) == len(evidence['files'])
    for entry in evidence['files']:
        path = entry['path']
        assert re.fullmatch(r'data/navmesh/topology/(topology\.properties|\d+\.pbtopology)', path)
        assert '..' not in Path(path).parts and '\\' not in path
        helper.target_path(server, path)
        assert sha(entry['source']) == entry['installed'], 'Topology changed after verified preparation'
