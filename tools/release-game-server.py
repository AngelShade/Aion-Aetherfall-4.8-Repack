"""Canonical locked source -> normal build -> full offline verification -> guarded runtime delivery."""
import argparse, importlib, json, msvcrt, subprocess, sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'client-mods/playerbots'))
import stage_companion_update as shared


def run(*args):
    subprocess.run(list(map(str, args)), cwd=ROOT, check=True)


def main():
    if not __debug__: raise RuntimeError('Validation cannot run with Python optimization enabled')
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--scope', required=True)
    p.add_argument('--install', action='store_true')
    p.add_argument('--review', type=Path, help='Use an already fully prepared review; all source/build/runtime guards are rechecked')
    p.add_argument('--config-review', type=Path, help='Explicit backed-up active/saved companion quota changes only')
    p.add_argument('--population-import', action='store_true', help='PB-SCOPE-012D: validated offline SQL import and exact archived file retirement')
    a = p.parse_args()
    lock_path = shared.DEV_ROOT / 'tooling/locks/game-server-release.lock'
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    with lock_path.open('a+b') as lock:
        if lock_path.stat().st_size == 0: lock.write(b'0'); lock.flush()
        lock.seek(0)
        try: msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        except OSError: raise RuntimeError('Another chat is building/delivering GameServer. Wait for that release to finish.')
        try:
            if a.review:
                review = a.review.resolve(); shared.validate_output(review)
                build = Path(json.loads((review / 'release-context.json').read_text())['build'])
            else:
                stamp = datetime.now().strftime('%Y%m%d-%H%M%S-%f')
                build = shared.DEV_ROOT / 'staging/target' / ('release-' + stamp)
                review = shared.DEV_ROOT / 'staging/output' / ('release-' + stamp)
                run('pwsh', '-NoProfile', '-File', ROOT / 'tools/build-components.ps1', '-Modules', 'game-server', '-OutputRoot', build)
                run(sys.executable, ROOT / 'tools/prepare-source-release.py', '--build', build, '--output', review, '--scope', a.scope)
                (review / 'release-context.json').write_text(json.dumps(dict(build=str(build)), indent=2))
            plan = json.loads((review / 'release-plan.json').read_text())
            if plan['scope'] != a.scope: raise RuntimeError('Use the scope recorded in this release review.')
            static = review / 'static-check'
            if not a.review:
                run(sys.executable, ROOT / 'tools/check-source-build.py', '--build', build, '--output', review / 'regression')
                run(sys.executable, ROOT / 'tools/check-source-static-data.py', '--build', build, '--output', static, '--release-plan', review / 'release-plan.json')
                run(sys.executable, ROOT / 'tools/check-source-browser.py', '--build', build, '--output', review / 'browser', '--browser-bin', shared.CLIENT_ROOT / 'bin64')
            importlib.import_module('check-source-build').validate(build, review / 'regression')
            importlib.import_module('check-source-browser').validate(build, review / 'browser')
            delivery = [sys.executable, ROOT / 'tools/deliver-source-component.py', '--build', build, '--output', review / 'delivery',
                '--scope', a.scope, '--release-plan', review / 'release-plan.json', '--static-check', static / 'static-data-load.txt']
            if a.config_review: delivery.extend(['--config-review', a.config_review])
            population = a.population_import or (review / 'population-import/prepared.json').is_file()
            if population:
                assert a.scope == 'PB-SCOPE-012D', 'Population migration is an explicit bounded release'
                # Complete all ordinary review/verification before any production SQL mutation.
                run(*delivery)
                migration = importlib.import_module('migrate-playerbot-population')
                if not (review / 'population-import/prepared.json').is_file(): migration.prepare(review, build)
                if a.install: migration.begin(review)
            if a.install: delivery.append('--install')
            if not population or a.install: run(*delivery)
            if a.install:
                if population: migration.finish(review)
                run(sys.executable, ROOT / 'client-mods/diagnostics/inventory_mods.py', '--client', shared.CLIENT_ROOT,
                    '--server', ROOT / 'target-deploy/game-server', '--output', ROOT / 'docs/INSTALLED_MODS.json')
            print('Release review:', review)
        finally:
            lock.seek(0); msvcrt.locking(lock.fileno(), msvcrt.LK_UNLCK, 1)


if __name__ == '__main__': main()
