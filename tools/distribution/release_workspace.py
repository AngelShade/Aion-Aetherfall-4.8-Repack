"""Resolve the owner-configured release workspace without embedding machine paths."""
import argparse
import json
import os
from pathlib import Path

LOCAL_CONFIG = Path(__file__).with_name('release-workspace.local.json')


def _config():
    if not LOCAL_CONFIG.is_file():
        return {}
    value = json.loads(LOCAL_CONFIG.read_text(encoding='utf-8-sig'))
    if value.get('schema') != 1 or not isinstance(value.get('releaseRoot'), str) or not value['releaseRoot'].strip():
        raise ValueError('Invalid local release workspace configuration: ' + str(LOCAL_CONFIG))
    return value


def _absolute(value, repo):
    path = Path(value).expanduser()
    return (path if path.is_absolute() else repo / path).resolve()


def release_root(repo=None):
    repo = Path(repo).resolve() if repo else Path(__file__).resolve().parents[2]
    explicit = os.environ.get('AION_RELEASE_ROOT')
    if explicit:
        return _absolute(explicit, repo)
    config = _config()
    if config:
        return _absolute(config['releaseRoot'], repo)
    dev = os.environ.get('AION_DEV_ROOT')
    if dev:
        workspace = _absolute(dev, repo)
        for candidate in (workspace / 'Aion Aetherfall Repack Client + Server/Distribution', workspace / 'distribution'):
            if (candidate / 'Maintenance/release-layout.json').is_file():
                return candidate
        raise FileNotFoundError('Configure the release workspace in ' + str(LOCAL_CONFIG))
    # Existing installations can be migrated without changing their defaults
    # before the external copy is verified. Never recreate a deleted legacy root.
    legacy = repo / 'releases'
    if (legacy / 'Maintenance/release-layout.json').is_file():
        return legacy
    raise FileNotFoundError('Configure AION_RELEASE_ROOT, AION_DEV_ROOT, or ' + str(LOCAL_CONFIG))


def release_path(value, repo=None):
    """Rebase explicit legacy workspace paths after a verified relocation."""
    repo = Path(repo).resolve() if repo else Path(__file__).resolve().parents[2]
    if value is None:
        return release_root(repo)
    path = _absolute(value, repo)
    config = _config()
    for old in config.get('legacyRoots', []):
        legacy = _absolute(old, repo)
        if path == legacy or path.is_relative_to(legacy):
            return release_root(repo) / path.relative_to(legacy)
    return path


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--layout', action='store_true', help='Print the active release-layout.json path')
    args = parser.parse_args()
    print(release_root() / 'Maintenance/release-layout.json' if args.layout else release_root())
