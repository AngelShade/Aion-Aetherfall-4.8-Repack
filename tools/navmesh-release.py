"""Bind a complete offline navigation batch to one guarded GameServer release."""
import hashlib, json, re, subprocess, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIG = 'config/playerbots/navigation.properties'
PREFIX = b'gameserver.playerbots.navigation.mesh.'


def target_path(server, rel):
    target = server/rel
    assert target.resolve().is_relative_to(server.resolve()), 'Navigation target escapes the server'
    for path in [target, *target.parents]:
        if path == server: break
        assert not path.is_symlink() and not path.is_junction(), 'Linked navigation destination: '+str(path)
    return target


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def properties(path):
    # Only read our generator's simple status/path/hash keys, never arbitrary Java values.
    return dict(line.split('=', 1) for line in Path(path).read_text().splitlines()
                if '=' in line and not line.startswith(('#', '!')))


def owner_keys(data):
    """Java Properties.load(InputStream) key syntax, including continuations/escapes."""
    lines = re.split(r'\r\n|\r|\n', data.decode('latin-1'))
    keys = set(); logical = ''; continued = False
    def unescape(key):
        result = []; index = 0
        while index < len(key):
            char = key[index]; index += 1
            if char == '\\' and index < len(key):
                char = key[index]; index += 1
                if char == 'u':
                    code = key[index:index+4]
                    assert re.fullmatch('[0-9a-fA-F]{4}', code), 'Invalid native property Unicode escape'
                    char = chr(int(code, 16)); index += 4
                else: char = {'t':'\t', 'r':'\r', 'n':'\n', 'f':'\f'}.get(char, char)
            result.append(char)
        return ''.join(result)
    def accept(line):
        line = line.lstrip(' \t\f')
        if not line or line[0] in '#!': return
        escaped = False; end = len(line)
        for index, char in enumerate(line):
            if escaped: escaped = False
            elif char == '\\': escaped = True
            elif char in '=:\t\f ': end = index; break
        keys.add(unescape(line[:end]))
    for line in lines:
        if not continued:
            if line.lstrip(' \t\f').startswith(('#','!')): continue
            logical = line
        else: logical += line.lstrip(' \t\f')
        slashes = len(logical)-len(logical.rstrip('\\'))
        continued = slashes % 2 == 1
        if continued: logical = logical[:-1]
        else: accept(logical); logical = ''
    if continued: accept(logical)
    return keys


def config_bytes(before, built):
    """Append only missing mesh settings and their builder documentation; preserve owner bytes."""
    blocks = built.replace(b'\r\n', b'\n').split(b'\n\n')
    existing = owner_keys(before)
    additions = []
    for block in blocks:
        keys = re.findall(rb'(?m)^(' + re.escape(PREFIX) + rb'[\w.]+)\s*[=:]', block)
        if not keys:
            continue
        assert len(keys) == 1
        if keys[0].decode('ascii') not in existing:
            additions.append(block)
    if not additions:
        return before
    newline = b'\r\n' if b'\r\n' in before else b'\n'
    suffix = b'\n\n'.join(additions).replace(b'\n', newline)
    return before + (newline if before and not before.endswith(b'\n') else b'') + newline + suffix + newline


def inputs(server):
    paths = list((server / 'data/geo').glob('*'))
    paths += [server / name for name in ['data/static_data/world_maps.xml', 'config/main/world.properties', 'config/mygs.properties']]
    return {str(p): sha(p) for p in paths if p.is_file()}


def batch_files(batch):
    selected = [batch/'report.properties']
    for key, value in properties(selected[0]).items():
        if re.fullmatch(r'map\.\d+\.directory', key):
            assert re.fullmatch(r'\d+/[0-9a-f]{64}', value), 'Unsafe map revision path'
            folder = batch/value
            assert not folder.is_symlink() and folder.resolve().is_relative_to(batch)
            selected.extend(p for p in folder.iterdir() if p.is_file() and p.suffix in {'.properties', '.navtile'})
    for path in selected:
        assert not path.is_symlink() and path.resolve().is_relative_to(batch)
    return {str(path): sha(path) for path in sorted(selected)}


def prepare(build, batch, tool, out, server):
    batch, tool = batch.resolve(), tool.resolve()
    assert not batch.is_relative_to(ROOT) and not tool.is_relative_to(ROOT), 'Use external generated outputs'
    report = batch / 'report.properties'
    # Verify before accepting any generated artifacts. The Java verifier reloads native geometry,
    # checks every source/tool fingerprint, decodes every tile and validates all polygons/links.
    directory = out / 'navmesh'; directory.mkdir(exist_ok=True)
    before = inputs(server)
    artifacts = batch_files(batch)
    tool_hash = sha(tool)
    classes = directory / 'verifier'; classes.mkdir(exist_ok=True)
    sources = [ROOT / 'tools/navmesh/verification' / name for name in ['VerifyBatch.java', 'VerifyMap.java']]
    subprocess.run(['javac', '--release', '17', '-cp', str(tool), '-d', str(classes), *map(str, sources)], check=True)
    with (directory / 'verification.log').open('w') as log:
        subprocess.run(['java', '-Xmx2g', '-cp', str(classes)+';'+str(tool),
                        'com.aionemu.tools.navmesh.VerifyBatch', str(server/'data/geo'), str(batch),
                        str(directory/'verification.properties')], stdout=log, stderr=subprocess.STDOUT, check=True)
    assert inputs(server) == before and sha(tool) == tool_hash, 'Geometry/tool changed during verification'
    assert batch_files(batch) == artifacts, 'Generated package changed during verification'
    proof = properties(directory/'verification.properties')
    assert proof['status'] == 'VERIFIED_OFFLINE_NOT_RUNTIME_ACCEPTANCE'
    data = properties(report)
    assert data['tool.sha256'] == tool_hash == proof['tool.sha256']
    selected = [report]
    maps = 0
    for key, value in data.items():
        if re.fullmatch(r'map\.\d+\.directory', key):
            assert re.fullmatch(r'\d+/[0-9a-f]{64}', value), 'Unsafe map revision path'
            folder = batch / value
            assert folder.resolve().is_relative_to(batch) and not folder.is_symlink()
            assert proof[key.removesuffix('.directory')+'.manifest.sha256'] == sha(folder/'map.properties')
            selected.extend(p for p in folder.iterdir() if p.is_file() and p.suffix in {'.properties', '.navtile'})
            maps += 1
    assert maps == int(proof['maps.verified']) == int(data['maps.discovered'])
    files = []
    for path in sorted(selected):
        assert not path.is_symlink() and path.resolve().is_relative_to(batch)
        rel = 'data/navmesh/' + path.relative_to(batch).as_posix()
        target = target_path(server, rel)
        assert not target.is_symlink() and (not target.exists() or target.is_file())
        assert str(path) in artifacts and sha(path) == artifacts[str(path)], 'Generated artifact changed after verification'
        files.append(dict(path=rel, source=str(path), original=sha(target) if target.exists() else None, installed=artifacts[str(path)]))
    with zipfile.ZipFile(tool) as generator:
        license_bytes = generator.read('META-INF/LICENSE-recast4j.txt')
    license_source = directory/'LICENSE-recast4j.txt'; license_source.write_bytes(license_bytes)
    license_target = target_path(server,'data/navmesh/LICENSE-recast4j.txt')
    assert not license_target.is_symlink() and (not license_target.exists() or license_target.is_file())
    files.append(dict(path='data/navmesh/LICENSE-recast4j.txt', source=str(license_source),
                      original=sha(license_target) if license_target.exists() else None, installed=sha(license_source)))
    with zipfile.ZipFile(build/'game-server/game-server.zip') as assembly:
        built = assembly.read('game-server/'+CONFIG)
    assert built == (ROOT/'game-server'/CONFIG).read_bytes()
    target = target_path(server,CONFIG)
    assert target.is_file() and not target.is_symlink()
    owner = target.read_bytes(); merged = config_bytes(owner, built)
    if merged != owner:
        source = directory/'navigation.properties'; source.write_bytes(merged)
        files.append(dict(path=CONFIG, source=str(source), original=sha(target), installed=sha(source), navigationDefaults=True))
    evidence = dict(files=files, geometryInputs=before, tool=str(tool), toolSha256=tool_hash,
                    verification=str(directory/'verification.properties'), verificationSha256=sha(directory/'verification.properties'),
                    reportSha256=sha(report), maps=maps)
    (directory/'package.json').write_text(json.dumps(evidence, indent=2))
    return evidence


def validate(evidence, build, server, installed=False):
    assert sha(evidence['tool']) == evidence['toolSha256']
    assert sha(evidence['verification']) == evidence['verificationSha256']
    assert inputs(server) == evidence['geometryInputs'], 'Native mesh inputs changed after verification'
    assert len({e['path'] for e in evidence['files']}) == len(evidence['files'])
    for entry in evidence['files']:
        rel = entry['path']
        assert '..' not in Path(rel).parts and '\\' not in rel
        assert rel == CONFIG or rel.startswith('data/navmesh/')
        target_path(server, rel)
        assert sha(entry['source']) == entry['installed'], 'Prepared mesh package changed: '+rel
        if rel == CONFIG:
            assert entry.get('navigationDefaults') and entry['original'] is not None
            if not installed:
                with zipfile.ZipFile(build/'game-server/game-server.zip') as assembly:
                    expected = config_bytes((server/rel).read_bytes(), assembly.read('game-server/'+CONFIG))
                assert expected == Path(entry['source']).read_bytes(), 'Unrelated owner configuration change'
