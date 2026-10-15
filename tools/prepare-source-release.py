"""Discover all managed resource changes in a complete normal Maven assembly."""
import argparse, hashlib, json, re, subprocess, sys, zipfile
from pathlib import Path
from importlib import import_module

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'client-mods/playerbots'))
import stage_companion_update as shared
build_meta = import_module('build-manifest')
delivery = import_module('deliver-source-component')


def main():
    if not __debug__: raise RuntimeError('Validation cannot run with Python optimization enabled')
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--build', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--scope', required=True)
    p.add_argument('--navmesh', type=Path, help='Complete external generated navigation batch')
    p.add_argument('--navmesh-tool', type=Path, help='Exact normal generator JAR for that batch')
    p.add_argument('--navmesh-topology', type=Path, help='Complete external directed topology for the installed mesh catalog')
    p.add_argument('--navmesh-animated', type=Path, help='Complete authored moving physics and native dock package')
    a = p.parse_args(); build, out = a.build.resolve(), a.output.resolve()
    shared.validate_output(build); shared.validate_output(out)
    manifest = build_meta.verify(build)
    out.mkdir(parents=True, exist_ok=True)
    server = ROOT / 'target-deploy/game-server'
    prior = shared.receipt_paths(server, 'playerbots-source-build-*/manifest.json')[-1]
    baseline = json.loads(prior.read_text())
    prior_source = {key.replace('\\', '/'): value for key, value in baseline['sourceHashes'].items()}
    resources, inputs, staged_handlers, cache = [], {}, [], set()
    with zipfile.ZipFile(build / 'game-server/game-server.zip') as assembly:
        managed = {n.removeprefix('game-server/'): n for n in assembly.namelist()
            if not n.endswith('/') and (n.startswith('game-server/data/') and not n.startswith('game-server/data/geo/')
                or n.removeprefix('game-server/') in delivery.OWNER_CONFIGURATIONS)}
        assert delivery.OWNER_CONFIGURATIONS <= set(managed), 'Normal assembly omitted documented Playerbots owner configuration'
        extras = [f.relative_to(server).as_posix() for folder in ['data/handlers', 'data/static_data', 'data/scripts']
            for f in (server / folder).rglob('*') if f.is_file() and f.suffix in {'.java', '.xml', '.xsd'} and f.relative_to(server).as_posix() not in managed]
        assert not extras, 'Reconcile runtime-only features into authoritative source before delivery: ' + str(extras)
        for rel, member in sorted(managed.items()):
            data = assembly.read(member)
            assert data == (ROOT / 'game-server' / rel).read_bytes(), 'Assembly differs from maintained source: ' + rel
            target = server / rel
            original = shared.sha(target) if target.is_file() else None
            inputs[rel] = original
            if rel in delivery.OWNER_CONFIGURATIONS:
                relocating = rel == delivery.PLAYERBOT_CONFIGURATION and (server / 'config/main/playerbots.properties').is_file() and not target.exists()
                if delivery.initial_feature_configuration(target) and not relocating: resources.append(rel)
            elif original != hashlib.sha256(data).hexdigest(): resources.append(rel)
            if rel.startswith('data/handlers/') and rel.endswith('.java'):
                dest = out / 'handler-source' / rel; dest.parent.mkdir(parents=True, exist_ok=True); dest.write_bytes(data)
                staged_handlers.append(dest)
                if rel in resources:
                    package = re.search(rb'^\s*package\s+([\w.]+)\s*;', data, re.MULTILINE)
                    assert package, rel
                    folder = server / 'cache/classes' / package.group(1).decode().replace('.', '/')
                    stem = Path(rel).stem
                    cache.update(f.relative_to(server).as_posix() for f in folder.glob(stem + '*.class')
                        if f.stem == stem or f.stem.startswith(stem + '$'))
    changed_classes = []
    for rel, digest in manifest['inputs'].items():
        if rel.startswith(('game-server/src/', 'commons/src/')) and rel.endswith('.java') and prior_source.get(rel) != digest:
            changed_classes.append(rel.split('/src/', 1)[1][:-5])
    core_changed = False
    dependency_changed = False
    for component in ['game-server', 'commons']:
        new = build / component / (component + '-4.8-SNAPSHOT.jar')
        old = server / 'libs' / new.name
        with zipfile.ZipFile(old) as x, zipfile.ZipFile(new) as y:
            old_names, new_names = set(x.namelist()), set(y.namelist())
            differs = old_names != new_names or any(x.read(n) != y.read(n) for n in old_names & new_names if n != 'META-INF/MANIFEST.MF')
            if component == 'game-server': core_changed = differs
            else: dependency_changed = differs
    libs = build_meta.libraries(build)
    additions = []
    for library in libs:
        target = server / 'libs' / library.name
        if target.exists():
            assert target.is_file() and shared.sha(target) == shared.sha(library), 'Existing dependency upgrade needs separate source review: ' + library.name
        else:
            additions.append(dict(path='libs/' + library.name, source=str(library), original=None, installed=shared.sha(library)))
    cp = ';'.join(map(str, [build / 'game-server/game-server-4.8-SNAPSHOT.jar', build / 'commons/commons-4.8-SNAPSHOT.jar', *libs]))
    args_file = out / 'handlers.args'
    args_file.write_text('\n'.join('"' + f.as_posix() + '"' for f in staged_handlers), encoding='utf-8')
    with (out / 'handler-compile.txt').open('w') as log:
        result = subprocess.run(['javac', '-J-Xmx2g', '-encoding', 'UTF-8', '-Xlint:deprecation,unchecked', '-Werror', '-cp', cp, '-d', str(out / 'handler-classes'), '@' + str(args_file)], cwd=out, stdout=log, stderr=subprocess.STDOUT)
    assert result.returncode == 0, 'Complete source handler compilation failed; see ' + str(out / 'handler-compile.txt')
    if core_changed or dependency_changed:
        # The native cache checks handler source mtime, not its core-JAR dependencies.
        # Only invalidate classes rebuilt successfully from maintained handler source.
        cache.update((Path('cache/classes') / f.relative_to(out / 'handler-classes')).as_posix()
            for f in (out / 'handler-classes').rglob('*.class') if (server / 'cache/classes' / f.relative_to(out / 'handler-classes')).is_file())
    if any(rel.startswith('data/static_data/') for rel in resources):
        cache.update(rel for rel in ['cache/static_data.xml', 'cache/static_data.xml.properties'] if (server / rel).exists())
    plan = dict(scope=a.scope, resources=resources, changedClasses=sorted(changed_classes), invalidateCaches=sorted(cache),
        runtimeResourceInputs=inputs, priorReceipt=str(prior), dependencyChanged=dependency_changed,
        buildManifestSha256=shared.sha(build / 'build-manifest.json'), libraryAdditions=additions, handlerCount=len(staged_handlers),
        handlerClasses=sum(1 for _ in (out / 'handler-classes').rglob('*.class')), handlerCompile='passed; no handlers executed')
    target = server / delivery.FEATURE_CONFIGURATION
    if target.is_file():
        with zipfile.ZipFile(build / 'game-server/game-server.zip') as assembly:
            maintained = assembly.read('game-server/' + delivery.FEATURE_CONFIGURATION)
        original = target.read_bytes()
        updated = delivery.world_activity_defaults(original, maintained)
        if updated != original:
            staged = out / 'owner-defaults/features.properties'; staged.parent.mkdir(exist_ok=True)
            staged.write_bytes(updated)
            plan['worldActivityDefaults'] = [dict(path=delivery.FEATURE_CONFIGURATION, source=str(staged),
                original=hashlib.sha256(original).hexdigest(), installed=shared.sha(staged), worldActivityDefaults=True)]
    assert bool(a.navmesh) == bool(a.navmesh_tool), 'Supply both navigation batch and its generator'
    if a.navmesh:
        plan['navigationPackage'] = import_module('navmesh-release').prepare(build, a.navmesh, a.navmesh_tool, out, server)
    if a.navmesh_topology:
        assert not a.navmesh, 'Install verified base meshes before deriving topology'
        plan['navigationTopology'] = import_module('navmesh-topology-release').prepare(build, a.navmesh_topology, out, server)
    if a.navmesh_animated:
        assert a.navmesh_topology, 'Bind authored rides to the same verified directed topology'
        plan['navigationAnimated'] = import_module('navmesh-animated-release').prepare(build, a.navmesh_animated, a.navmesh_topology, out, server)
    build_meta.verify(build)
    (out / 'release-plan.json').write_text(json.dumps(plan, indent=2))
    print('OK: complete distribution audited;', len(managed), 'managed files;', len(resources), 'changed resources;', len(staged_handlers), 'handlers compile')


if __name__ == '__main__': main()
