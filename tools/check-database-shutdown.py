"""Verify actual shutdown classes with inert services; never opens a SQL connection or native World."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PRODUCTS = [ROOT / path for path in [
    'commons/src/com/aionemu/commons/database/DatabaseFactory.java',
    'game-server/src/com/aionemu/gameserver/utils/ThreadPoolManager.java',
    'game-server/src/com/aionemu/gameserver/ShutdownHook.java']]
FIXTURE = ROOT / 'tools/java/DatabaseShutdownCheck.java'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def method_descriptors(classpath, name):
    result = subprocess.run(['javap', '-s', '-classpath', classpath, name], capture_output=True, text=True, check=True)
    methods = set()
    declaration = None
    for line in result.stdout.splitlines():
        line = line.strip()
        if '(' in line and line.endswith(';'):
            declaration = line
        elif line.startswith('descriptor:') and declaration:
            method = declaration.split('(', 1)[0].split()[-1]
            if method != name:  # Inert service construction is private to the fixture.
                methods.add((method, line.removeprefix('descriptor:').strip(), 'static' in declaration.split()))
            declaration = None
    return methods


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build', type=Path, help='Complete normal builder root; tests exact JAR classes when supplied')
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    client = (Path.home() / 'Downloads/aion-4.8-na/Aion 4.8 NA').resolve()
    assert not output.is_relative_to(ROOT) and not output.is_relative_to(client), 'Use an external diagnostic output directory'
    output.mkdir(parents=True, exist_ok=False)
    staged = output / 'stubs'
    compiled = output / 'classes'
    compiled.mkdir()
    # Shadow only external game service dependencies. All three repaired classes remain actual source/JAR classes.
    sources = {
        'com.aionemu.gameserver.GameServer': 'public static void shutdownNioServer(){event("network-close");}',
        'com.aionemu.gameserver.services.MarketplaceService': 'public static void stop(){event("market-stop");}',
        'com.aionemu.gameserver.services.player.PlayerLeaveWorldService': 'public static void processPendingLeaveWorldTasks(){event("players-save");}',
        'com.aionemu.gameserver.services.playerbot.PlayerBotService': 'public static PlayerBotService getInstance(){return new PlayerBotService();} public void shutdown(){event("bots-save");}',
        'com.aionemu.gameserver.services.PeriodicSaveService': 'public static PeriodicSaveService getInstance(){return new PeriodicSaveService();} public void onShutdown(){event("periodic-save");}',
        'com.aionemu.gameserver.services.GameTimeService': 'public static GameTimeService getInstance(){return new GameTimeService();} public boolean saveGameTime(){event("time-save"); return true;}',
        'com.aionemu.gameserver.services.cron.CronService': 'public static CronService getInstance(){return new CronService();} public void shutdown(){event("cron-stop");} public org.quartz.JobDetail schedule(Runnable task, Class<? extends RunnableRunner> runner, org.quartz.CronExpression expression, boolean longRunning){throw new AssertionError("Cron scheduling forbidden");}',
        'com.aionemu.commons.utils.concurrent.RunnableStatsManager': 'public enum SortBy{AVG} public static void dumpClassStats(SortBy sort){event("stats");}',
        'com.aionemu.commons.utils.concurrent.DeadLockDetector': 'public static Thread start(java.time.Duration interval, Runnable action){return Thread.ofPlatform().unstarted(action);}',
    }
    files = []
    for name, body in sources.items():
        package, simple = name.rsplit('.', 1)
        path = staged / (name.replace('.', '/') + '.java')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text('package ' + package + ';\nimport static com.aionemu.gameserver.DatabaseShutdownCheck.event;\npublic class ' + simple + '{' + body + '}\n', encoding='utf-8')
        files.append(path)
    libraries = sorted((ROOT / 'target-deploy/game-server/libs').glob('*.jar'))
    if args.build:
        build = args.build.resolve()
        libraries = [p for p in libraries if p.name not in {'game-server-4.8-SNAPSHOT.jar', 'commons-4.8-SNAPSHOT.jar'}]
        libraries += [build / 'commons/commons-4.8-SNAPSHOT.jar', build / 'game-server/game-server-4.8-SNAPSHOT.jar']
        inputs = libraries + [FIXTURE, Path(__file__), *files]
    else:
        files += PRODUCTS
        inputs = libraries + [FIXTURE, Path(__file__), *files]
    before = {str(p): sha(p) for p in inputs}
    cp = os.pathsep.join(map(str, libraries))
    argfile = output / 'javac.args'
    argfile.write_text('\n'.join('"' + str(p).replace('\\', '/') + '"' for p in [FIXTURE, *files]), encoding='utf-8')
    result = subprocess.run(['javac', '-encoding', 'UTF-8', '-Xlint:deprecation,unchecked', '-Werror', '-cp', cp, '-d', str(compiled), '@' + str(argfile)], capture_output=True, text=True)
    (output / 'compile.txt').write_text(result.stdout + result.stderr, encoding='utf-8')
    assert result.returncode == 0, result.stdout + result.stderr
    abi = {}
    for name in sources:
        inert = method_descriptors(str(compiled) + os.pathsep + cp, name)
        native = method_descriptors(cp, name)
        assert inert <= native, 'Inert service has a different native method descriptor: ' + name + ': ' + str(inert - native)
        abi[name] = sorted(inert)
    (output / 'abi.json').write_text(json.dumps(abi, indent=2), encoding='utf-8')
    try:
        result = subprocess.run(['java', '-Xverify:all', '-cp', str(compiled) + os.pathsep + cp, 'com.aionemu.gameserver.DatabaseShutdownCheck'], cwd=output, capture_output=True, text=True, timeout=30)
    except subprocess.TimeoutExpired as error:
        (output / 'checks.txt').write_bytes((error.stdout or b'') + (error.stderr or b''))
        raise
    (output / 'checks.txt').write_text(result.stdout + result.stderr, encoding='utf-8')
    assert result.returncode == 0, result.stdout + result.stderr
    assert before == {str(p): sha(p) for p in inputs}, 'Inputs changed during verification'
    conclusion = next(line for line in result.stdout.splitlines() if line.startswith('OK: '))
    (output / 'evidence.json').write_text(json.dumps({'mode': 'normal-builder-artifact' if args.build else 'three-source-classes', 'inputs': before, 'result': conclusion, 'count': int(conclusion.split()[1]), 'reportSha256': sha(output / 'checks.txt'), 'compileSha256': sha(output / 'compile.txt'), 'abiSha256': sha(output / 'abi.json'), 'classes': {str(p.relative_to(compiled)): sha(p) for p in sorted(compiled.rglob('*.class'))}}, indent=2), encoding='utf-8')
    print(conclusion)


if __name__ == '__main__':
    main()
