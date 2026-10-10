"""Guarded native population import and exact retirement, called by the canonical release lock."""
import argparse
import importlib
import json
import shutil
import subprocess
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'client-mods/playerbots'))
import stage_companion_update as shared
from remove_follow_recovery_offline import stopped
from retire_metadata_files_offline import unused_files

FILES = ('world-bots.properties', 'world-server-population.json', 'world-population-events.json', 'world-population.json')
CLASS = 'com.aionemu.gameserver.services.playerbot.PlayerBotPopulationMigration'
SERVER = ROOT / 'target-deploy/game-server'


def legacy():
    directory = SERVER / 'config/playerbots'
    return {name: shared.sha(directory / name) if (directory / name).is_file() else None for name in FILES}


def prepare(review, build):
    review, build = Path(review).resolve(), Path(build).resolve()
    shared.validate_output(review); shared.validate_output(build)
    importlib.import_module('build-manifest').verify(build)
    output = review / 'population-import'
    output.mkdir(exist_ok=False)
    source = ROOT / 'tools/java/PlayerBotPopulationMigration.java'
    schema = ROOT / 'game-server/sql/playerbot_population.sql'
    libraries = [f for f in sorted((SERVER / 'libs').glob('*.jar')) if f.name not in {'game-server-4.8-SNAPSHOT.jar', 'commons-4.8-SNAPSHOT.jar', 'playerbot-recruitment-fix.jar'}]
    cp = ';'.join(map(str, [output / 'classes', build / 'game-server/game-server-4.8-SNAPSHOT.jar', build / 'commons/commons-4.8-SNAPSHOT.jar', *libraries]))
    subprocess.run(['javac', '-Xlint:deprecation,unchecked', '-Werror', '-encoding', 'UTF-8', '-cp', cp, '-d', str(output / 'classes'), str(source)], check=True)
    shutil.copy2(schema, output / 'playerbot_population.sql')
    metadata = dict(scope='PB-SCOPE-012D', build=str(build), classpath=cp, legacy=legacy(),
                    sourceSha256=shared.sha(source), schemaSha256=shared.sha(schema),
                    classes={p.relative_to(output).as_posix(): shared.sha(p) for p in (output / 'classes').rglob('*.class')},
                    jarSha256=shared.sha(build / 'game-server/game-server-4.8-SNAPSHOT.jar'),
                    libraries={str(p): shared.sha(p) for p in libraries})
    (output / 'prepared.json').write_text(json.dumps(metadata, indent=2))
    print('OK: offline population import tool/schema staged; SQL untouched; legacy files retained')


def guard(review):
    stopped()
    output = Path(review) / 'population-import'
    m = json.loads((output / 'prepared.json').read_text())
    build = Path(m['build'])
    importlib.import_module('build-manifest').verify(build)
    assert shared.sha(ROOT / 'tools/java/PlayerBotPopulationMigration.java') == m['sourceSha256']
    assert shared.sha(output / 'playerbot_population.sql') == m['schemaSha256']
    assert shared.sha(build / 'game-server/game-server-4.8-SNAPSHOT.jar') == m['jarSha256']
    assert all(shared.sha(output / rel) == digest for rel, digest in m['classes'].items())
    assert all(shared.sha(Path(path)) == digest for path, digest in m['libraries'].items())
    assert legacy() == m['legacy'], 'Population state changed after staging; refresh the import snapshot, never overwrite it'
    expected = importlib.import_module('check-source-build').TESTS
    checks = json.loads((Path(review) / 'regression/checks.json').read_text())
    assert set(checks) == set(expected) | {'ShutdownHookCheck'}, 'Complete offline regression review is required before SQL import'
    for test, count in dict(expected, ShutdownHookCheck=14).items():
        assert checks[test].startswith('OK: ' + str(count) + ' '), test
    return output, m


def run(output, m, mode, report):
    log = report.with_suffix('.log')
    with log.open('w') as stream:
        result = subprocess.run(['java', '-Xverify:all', '-cp', m['classpath'], CLASS, mode,
            str(SERVER), str(output / 'playerbot_population.sql'), str(report)], cwd=output, stdout=stream, stderr=subprocess.STDOUT)
    assert result.returncode == 0, 'Native population ' + mode + ' refused; see ' + str(log)
    print(next(line for line in log.read_text().splitlines() if line.startswith('OK: population ')))


def begin(review):
    output, m = guard(review)
    run(output, m, 'verify', output / 'preflight.txt')  # Read-only ownership/readiness validation.
    guard(review)
    backup = shared.DEV_ROOT / 'archives/server/game-server/backups' / ('playerbots-population-import-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    backup.mkdir(parents=True, exist_ok=False)
    for name, digest in m['legacy'].items():
        if digest is not None:
            shutil.copy2(SERVER / 'config/playerbots' / name, backup / name)
            assert shared.sha(backup / name) == digest
    shutil.copy2(output / 'prepared.json', backup / 'prepared.json')
    shutil.copy2(output / 'playerbot_population.sql', backup / 'playerbot_population.sql')
    (output / 'active-backup.json').write_text(json.dumps(dict(path=str(backup)), indent=2))
    guard(review)
    run(output, m, 'apply', backup / 'import.txt')
    # New process/connection must read durable state before any file is retired.
    run(output, m, 'retire', backup / 'database-verified.txt')
    (backup / 'committed.json').write_text(json.dumps(dict(verifiedAt=datetime.now().isoformat(), legacy=m['legacy'], characterMutation=False), indent=2))
    print('Population import recovery:', backup)


def finish(review):
    output, m = guard(review)
    backup = Path(json.loads((output / 'active-backup.json').read_text())['path'])
    assert (backup / 'committed.json').is_file()
    assert shared.sha(SERVER / 'libs/game-server-4.8-SNAPSHOT.jar') == m['jarSha256'], 'Native reader must be installed before legacy retirement'
    receipt = shared.receipt_paths(SERVER, 'playerbots-source-build-*/manifest.json')[-1]
    installed = json.loads(receipt.read_text())
    assert installed['scope'] == 'PB-SCOPE-012D' and (receipt.parent / 'installed.json').is_file()
    run(output, m, 'retire', backup / 'database-before-retirement.txt')
    guard(review)
    rows = [dict(path=name, sha256=digest) for name, digest in m['legacy'].items() if digest is not None]
    directory = (SERVER / 'config/playerbots').resolve()
    for row in rows: assert shared.sha(backup / row['path']) == row['sha256']
    try:
        with unused_files(directory, rows):
            for row in rows:
                target = (directory / row['path']).resolve()
                assert target.parent == directory and target.name in FILES
                target.unlink()
        assert all(value is None for value in legacy().values())
    except Exception:
        for row in rows:
            target = directory / row['path']
            if not target.exists(): shutil.copy2(backup / row['path'], target)
        raise
    retired = ['config/playerbots/' + name for name in FILES]
    # Preserve the original receipt and record only the exactly verified retirement exception.
    shutil.copy2(receipt, backup / 'delivery-manifest-before-retirement.json')
    installed['configFiles'] = {rel: digest for rel, digest in installed['configFiles'].items() if rel.replace('\\', '/') not in retired}
    installed['retiredFiles'] = sorted(set(installed['retiredFiles']) | set(retired))
    installed['populationImport'] = dict(recovery=str(backup), preparedSha256=shared.sha(output / 'prepared.json'), files=rows,
        schemaSha256=m['schemaSha256'], databaseVerificationSha256=shared.sha(backup / 'database-before-retirement.txt'))
    receipt.write_text(json.dumps(installed, indent=2))
    (backup / 'retired.json').write_text(json.dumps(dict(files=rows, receipt=str(receipt), retiredAt=datetime.now().isoformat()), indent=2))
    print('OK: native population installed; exact legacy state files archived and retired; recovery:', backup)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=['prepare'])
    p.add_argument('--review', required=True, type=Path)
    p.add_argument('--build', required=True, type=Path)
    a = p.parse_args(); prepare(a.review, a.build)


if __name__ == '__main__': main()
