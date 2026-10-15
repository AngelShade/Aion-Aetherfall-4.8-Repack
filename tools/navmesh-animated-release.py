"""Bind authored moving physics and all-map native docking derivatives to one normal build."""
import hashlib, re, subprocess, sys
from pathlib import Path
from importlib import import_module

ROOT = Path(__file__).resolve().parents[1]

def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def prepare(build, package, topology, out, server):
    package, topology = package.resolve(), topology.resolve()
    assert not package.is_relative_to(ROOT), 'Use external animated preparation output'
    helper = import_module('navmesh-release')
    index = helper.properties(package / 'animated.properties')
    assert index['format'] == 'aion-animated-navigation-1'
    assert index['report.sha256'] == sha(server / 'data/navmesh/report.properties')
    assert index['topology.sha256'] == sha(topology / 'topology.properties')
    assert index['scene.sha256'] == sha(package / 'animated-scene.json')
    catalog = helper.properties(server / 'data/navmesh/report.properties')
    ids = sorted(int(k.split('.')[1]) for k in catalog if k.startswith('map.') and k.endswith('.directory'))
    assert {int(k.split('.')[1]) for k in index if re.fullmatch(r'map\.\d+\.sha256', k)} == set(ids)
    selected = [package / 'animated.properties', package / 'animated-scene.json', *(package / (str(i) + '.pbrides') for i in ids)]
    assert set(selected) == set(package.iterdir()), 'Unexpected authored navigation package members'
    before = {str(p): sha(p) for p in selected}
    native_log = out / 'animated-native-inputs.txt'
    with native_log.open('w') as stream:
        subprocess.run([sys.executable, str(ROOT / 'tools/navmesh/animated-platforms.py'), '--client',
                        str(import_module('stage_companion_update').CLIENT_ROOT), '--output', str(package), '--verify'],
                       stdout=stream, stderr=subprocess.STDOUT, check=True)
    assert 'PASS: complete authored scene matches native client inputs and maintained exporter' in native_log.read_text()
    jars = [build / 'game-server/game-server-4.8-SNAPSHOT.jar', build / 'commons/commons-4.8-SNAPSHOT.jar', *import_module('build-manifest').libraries(build)]
    log = out / 'animated-verification.txt'
    with log.open('w') as stream:
        subprocess.run(['java', '-Xmx2g', '-cp', ';'.join(map(str, jars)),
                        'com.aionemu.gameserver.services.playerbot.PlayerBotAnimatedLinks',
                        str(server / 'data/navmesh'), str(topology), str(package / 'animated-scene.json'), str(package), '--verify'],
                       stdout=stream, stderr=subprocess.STDOUT, check=True)
    assert 'PASS: complete authored animation and native docking package verified' in log.read_text()
    assert before == {str(p): sha(p) for p in selected}
    files = []
    for source in selected:
        assert not source.is_symlink() and source.resolve().parent == package
        rel = 'data/navmesh/animated/' + source.name
        target = helper.target_path(server, rel)
        files.append(dict(path=rel, source=str(source), original=sha(target) if target.exists() else None, installed=sha(source)))
    return dict(files=files, maps=len(ids), catalogSha256=index['report.sha256'], topologySha256=index['topology.sha256'],
                verification=str(log), verificationSha256=sha(log), nativeVerification=str(native_log),
                nativeVerificationSha256=sha(native_log), buildManifestSha256=sha(build / 'build-manifest.json'))

def validate(evidence, build, server, installed=False):
    helper = import_module('navmesh-release')
    assert sha(build / 'build-manifest.json') == evidence['buildManifestSha256']
    assert sha(server / 'data/navmesh/report.properties') == evidence['catalogSha256']
    assert sha(evidence['verification']) == evidence['verificationSha256']
    assert sha(evidence['nativeVerification']) == evidence['nativeVerificationSha256']
    assert len(evidence['files']) == evidence['maps'] + 2
    assert len({e['path'] for e in evidence['files']}) == len(evidence['files'])
    for entry in evidence['files']:
        assert re.fullmatch(r'data/navmesh/animated/(animated\.properties|animated-scene\.json|\d+\.pbrides)', entry['path'])
        assert '\\' not in entry['path'] and '..' not in Path(entry['path']).parts
        target = helper.target_path(server, entry['path'])
        assert sha(entry['source']) == entry['installed'], 'Animated package changed after preparation'
        if installed: assert sha(target) == entry['installed']
