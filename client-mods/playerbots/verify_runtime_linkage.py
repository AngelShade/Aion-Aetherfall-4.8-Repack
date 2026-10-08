"""Fail closed on executable references absent from the actual runtime classpath."""
import argparse, subprocess
from pathlib import Path
import stage_companion_update as shared

def verify(payload, output):
    shared.validate_output(output)
    output.mkdir(parents=True, exist_ok=True)
    source = Path(__file__).parent/'java/PlayerBotLinkageCheck.java'
    subprocess.run(['javac', '-d', str(output), str(source)], check=True, capture_output=True, text=True)
    server = shared.ROOT/'target-deploy/game-server'
    # Payload first, then actual native libraries. Source build output is excluded.
    cp = ';'.join(map(str, [output, payload, server/'libs/*', server/'cache/classes']))
    result = subprocess.run(['java', '-Xverify:all', '-cp', cp, 'PlayerBotLinkageCheck', str(payload)],
                            cwd=output, capture_output=True, text=True)
    (output/'linkage.txt').write_text(result.stdout+result.stderr, encoding='utf-8')
    if result.returncode:
        raise RuntimeError('Runtime linkage rejected package:\n'+result.stdout+result.stderr)
    return result.stdout.strip()

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--jar', type=Path, required=True); p.add_argument('--output', type=Path, required=True)
    a=p.parse_args(); print(verify(a.jar.resolve(), a.output.resolve()))
