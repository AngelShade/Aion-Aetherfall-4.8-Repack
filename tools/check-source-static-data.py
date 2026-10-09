"""Load an isolated copy of actual runtime XML with a normal GameServer build before delivery."""
import argparse, hashlib, json, re, shutil, subprocess, sys, zipfile
from pathlib import Path
from importlib import import_module

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'client-mods/playerbots'))
import stage_companion_update as shared
build_meta = import_module('build-manifest')


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    if not __debug__: raise RuntimeError('Validation cannot run with Python optimization enabled')
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--build', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--resource', action='append', default=[], help='Static data file to overlay unchanged from the normal Maven assembly')
    p.add_argument('--release-plan', type=Path, help='Automatically overlay the complete static-resource change list')
    a = p.parse_args()
    build, out = a.build.resolve(), a.output.resolve()
    shared.validate_output(build); shared.validate_output(out)
    server = ROOT / 'target-deploy/game-server'
    game = build / 'game-server/game-server-4.8-SNAPSHOT.jar'
    build_meta.verify(build)
    if a.release_plan:
        plan = json.loads(a.release_plan.read_text())
        assert plan['buildManifestSha256'] == digest(build / 'build-manifest.json')
        complete = [rel for rel in plan['resources'] if rel.startswith('data/static_data/')]
        assert not a.resource or set(a.resource) == set(complete), 'Incomplete static-resource override'
        a.resource = complete
    assert 'BUILD SUCCESS' in (build / 'maven.log').read_text()
    out.mkdir(parents=True, exist_ok=False)
    staged = out / 'static-runtime/data/static_data'
    shutil.copytree(server / 'data/static_data', staged)
    checked_resources = {}
    if a.resource:
        with zipfile.ZipFile(build / 'game-server/game-server.zip') as assembly:
            for rel in a.resource:
                assert rel.startswith('data/static_data/') and '..' not in Path(rel).parts and '\\' not in rel
                data = assembly.read('game-server/' + rel)
                assert data == (ROOT / 'game-server' / rel).read_bytes(), 'Resource changed after build: ' + rel
                dest = out / 'static-runtime' / rel; dest.parent.mkdir(parents=True, exist_ok=True); dest.write_bytes(data)
                checked_resources[rel] = digest(dest)
    inputs = {str(f.relative_to(server)).replace('\\', '/'): digest(f) for f in (server / 'data/static_data').rglob('*')
        if f.is_file() and str(f.relative_to(server)).replace('\\', '/') not in checked_resources}
    assert all(digest(out / 'static-runtime' / rel) == value for rel, value in inputs.items())
    config = (server / 'config/main/gameserver.properties').read_text()
    country = re.search(r'^\s*gameserver\.country\.code\s*=\s*(\d+)', config, re.MULTILINE)
    assert country, 'Runtime country code unavailable'
    libs = [f for f in sorted((server / 'libs').glob('*.jar')) if f.name not in {'game-server-4.8-SNAPSHOT.jar', 'commons-4.8-SNAPSHOT.jar', 'playerbot-recruitment-fix.jar'}]
    cp = ';'.join(map(str, [out / 'checks', game, build / 'commons/commons-4.8-SNAPSHOT.jar', *libs]))
    subprocess.run(['javac', '-cp', cp, '-d', str(out / 'checks'), str(ROOT / 'tools/java/StaticDataBuildCheck.java')], check=True)
    report = out / 'static-data-load.txt'
    with report.open('w') as stream:
        result = subprocess.run(['java', '-Xmx4g', '-cp', cp, 'com.aionemu.gameserver.dataholders.StaticDataBuildCheck',
            str(staged), str(out / 'static-runtime/cache/static_data.xml'), country.group(1)], cwd=out, stdout=stream, stderr=subprocess.STDOUT)
    assert result.returncode == 0, 'Offline static-data load failed; see ' + str(report)
    summary = next(line for line in report.read_text().splitlines() if line.startswith('OK: complete static XML merge'))
    assert all(digest(server / rel) == value for rel, value in inputs.items()), 'Runtime static data changed during check'
    build_meta.verify(build)
    report.with_suffix('.json').write_text(json.dumps(dict(reportSha256=digest(report), builderJarSha256=digest(game),
        unchangedRuntimeInputs=inputs, builderResources=checked_resources, countryCode=int(country.group(1))), indent=2))
    print(summary)
    print('Report:', report)


if __name__ == '__main__':
    main()
