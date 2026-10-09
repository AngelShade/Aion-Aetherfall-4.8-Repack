"""Bind normal Maven output to unchanged maintained inputs; detect edits from other chats."""
import argparse, hashlib, json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def inputs():
    paths = [ROOT / 'pom.xml']
    for name in ['commons', 'game-server', 'login-server', 'chat-server']:
        folder = ROOT / name
        paths.extend(p for p in folder.glob('*.xml') if p.is_file())
        for part in ['src', 'data', 'config', 'dist', 'sql']:
            paths.extend(p for p in (folder / part).rglob('*') if p.is_file())
    return {p.relative_to(ROOT).as_posix(): digest(p) for p in sorted(set(paths))}


def verify(build):
    manifest = json.loads((build / 'build-manifest.json').read_text())
    if manifest['inputs'] != inputs():
        raise RuntimeError('Maintained source/resources changed after or during build. Build a fresh release.')
    if not all(digest(build / rel) == value for rel, value in manifest['artifacts'].items()):
        raise RuntimeError('Normal builder output changed after build.')
    return manifest


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=['snapshot', 'finalize', 'verify'])
    p.add_argument('--build', type=Path, required=True)
    a = p.parse_args(); build = a.build.resolve()
    if a.mode == 'snapshot':
        (build / 'source-inputs-before.json').write_text(json.dumps(inputs(), indent=2))
    elif a.mode == 'finalize':
        before = json.loads((build / 'source-inputs-before.json').read_text())
        if before != inputs():
            raise RuntimeError('Source/resources changed while Maven was building. Rejecting mixed build.')
        artifacts = {p.relative_to(build).as_posix(): digest(p) for p in build.rglob('*') if p.is_file() and p.suffix in {'.jar', '.zip'}}
        if not artifacts: raise RuntimeError('No normal builder artifacts')
        (build / 'build-manifest.json').write_text(json.dumps(dict(inputs=before, artifacts=artifacts), indent=2))
        print('OK: immutable source/build manifest;', len(before), 'inputs;', len(artifacts), 'artifacts')
    else:
        verify(build); print('OK: source and builder artifacts still match the build manifest')


if __name__ == '__main__': main()
