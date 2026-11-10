"""Run the focused offline interface checks against a read-only client framework."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

HERE = Path(__file__).resolve().parent
SOURCE = HERE.parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, help='fresh external directory for traces and check logs')
    args = parser.parse_args()
    client = args.client.resolve(strict=True)
    if args.output:
        output = args.output.resolve()
        if output.is_relative_to(SOURCE) or output.is_relative_to(client):
            parser.error('Use a fresh output directory outside source and client')
        output.mkdir(parents=True, exist_ok=False)
    else:
        output = Path(tempfile.mkdtemp(prefix='aetherfall-interface-checks-'))
    environment = dict(os.environ, PYTHONDONTWRITEBYTECODE='1')
    # Existing measurement tools also run with the same external Lua dependency
    # folder; the wrapper neither edits their modules nor writes site-packages.
    wrapper = ("import os,runpy,sys; p=os.environ.get('AION_LUA_TEST_DEPS'); "
               "sys.path[:0]=[p] if p else []; f=sys.argv.pop(1); "
               "sys.path.insert(0,os.path.dirname(f)); runpy.run_path(f,run_name='__main__')")
    checks = [
        ('trace', HERE / 'trace/run.py', ['--client', str(client), '--output', str(output / 'current-trace.jsonl.gz')]),
        ('isolated', HERE / 'isolated.py', ['--client', str(client)]),
        ('lint', HERE / 'lint.py', []),
        ('load-budget', SOURCE / 'measure_calls.py', ['--client', str(client)]),
        ('packaging', HERE / 'packaging.py', []),
    ]
    failed = []
    for name, script, arguments in checks:
        print(f'Running {name}...', flush=True)
        result = subprocess.run([sys.executable, '-B', '-c', wrapper, str(script), *arguments],
                                cwd=output, env=environment, capture_output=True, text=True, errors='replace')
        text = result.stdout + result.stderr
        (output / (name + '.log')).write_text(text, encoding='utf-8')
        if text:
            print(text[-12000:].rstrip(), flush=True)
        if result.returncode:
            failed.append(name)
        elif name == 'load-budget':
            total = re.search(r'total at load: (\d+)', text)
            if not total or int(total[1]) > 4000:
                failed.append(name)
                print('FAIL: missing queued-call count or more than 4000 pending calls')
    print('Check output:', output)
    if failed:
        raise SystemExit('FAILED: ' + ', '.join(failed))
    print('PASS: trace, isolation, dependency lint, load budget and packaging')


if __name__ == '__main__':
    main()
