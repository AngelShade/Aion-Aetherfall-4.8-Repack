"""Reproduce the installed cast-clear race or verify the native repaired controller without World/SQL/IDs."""
import argparse
import importlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SHARED = importlib.import_module('check-database-shutdown')
SOURCE = ROOT / 'game-server/src/com/aionemu/gameserver/controllers/PlayerController.java'
FIXTURE = ROOT / 'tools/java/PlayerSkillCancellationCheck.java'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--build', type=Path)
    mode.add_argument('--installed', action='store_true')
    parser.add_argument('--expect-race', action='store_true')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    assert not args.expect_race or args.installed, 'Race reproduction must use the installed native artifact'
    output = args.output.resolve()
    client = (Path.home() / 'Downloads/aion-4.8-na/Aion 4.8 NA').resolve()
    assert not output.is_relative_to(ROOT) and not output.is_relative_to(client), 'Use an external diagnostic output directory'
    output.mkdir(parents=True, exist_ok=False)
    compiled = output / 'classes'; compiled.mkdir()
    stub = output / 'stubs/com/aionemu/gameserver/utils/PacketSendUtility.java'; stub.parent.mkdir(parents=True)
    stub.write_text('''package com.aionemu.gameserver.utils;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.AionServerPacket;
import com.aionemu.gameserver.controllers.PlayerSkillCancellationCheck;
public class PacketSendUtility {
 public static void sendPacket(Player player, AionServerPacket packet){PlayerSkillCancellationCheck.packet(player,packet,false);}
 public static void broadcastPacket(Player player, AionServerPacket packet, boolean self){if(!self)throw new AssertionError("Native self-broadcast changed");PlayerSkillCancellationCheck.packet(player,packet,true);}
}
''', encoding='utf-8')
    libraries = sorted((ROOT / 'target-deploy/game-server/libs').glob('*.jar'))
    if args.build:
        libraries = [p for p in libraries if p.name not in {'game-server-4.8-SNAPSHOT.jar', 'commons-4.8-SNAPSHOT.jar'}]
        build = args.build.resolve()
        libraries += [build / 'commons/commons-4.8-SNAPSHOT.jar', build / 'game-server/game-server-4.8-SNAPSHOT.jar']
    sources = [FIXTURE, stub] + ([] if args.build or args.installed else [SOURCE])
    inputs = libraries + sources + [Path(__file__), Path(SHARED.__file__)]
    before = {str(p): SHARED.sha(p) for p in inputs}
    cp = os.pathsep.join(map(str, libraries))
    argfile = output / 'javac.args'
    argfile.write_text('\n'.join('"' + str(p).replace('\\', '/') + '"' for p in sources if p != stub), encoding='utf-8')
    result = subprocess.run(['javac', '-encoding', 'UTF-8', '-Xlint:deprecation,unchecked', '-Werror', '-cp', cp, '-d', str(compiled), '@' + str(argfile)], capture_output=True, text=True)
    (output / 'compile.txt').write_text(result.stdout + result.stderr, encoding='utf-8')
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(['javac', '-encoding', 'UTF-8', '-Xlint:deprecation,unchecked', '-Werror', '-cp', str(compiled) + os.pathsep + cp, '-d', str(compiled), str(stub)], capture_output=True, text=True)
    with (output / 'compile.txt').open('a', encoding='utf-8') as log: log.write(result.stdout + result.stderr)
    assert result.returncode == 0, result.stdout + result.stderr
    name = 'com.aionemu.gameserver.utils.PacketSendUtility'
    inert = SHARED.method_descriptors(str(compiled) + os.pathsep + cp, name)
    assert inert <= SHARED.method_descriptors(cp, name), 'Fixture packet boundary differs from native descriptors'
    (output / 'abi.json').write_text(json.dumps(sorted(inert), indent=2), encoding='utf-8')
    command = ['java', '-Xverify:all', '-cp', str(compiled) + os.pathsep + cp, 'com.aionemu.gameserver.controllers.PlayerSkillCancellationCheck']
    if args.expect_race: command += ['--expect-race']
    result = subprocess.run(command, cwd=output, capture_output=True, text=True, timeout=30)
    (output / 'checks.txt').write_text(result.stdout + result.stderr, encoding='utf-8')
    assert result.returncode == 0, result.stdout + result.stderr
    assert before == {str(p): SHARED.sha(p) for p in inputs}, 'Verification inputs changed'
    conclusion = next(line for line in result.stdout.splitlines() if line.startswith('OK: '))
    evidence = {'mode': 'normal-builder-artifact' if args.build else 'installed-artifact' if args.installed else 'native-controller-source', 'result': conclusion, 'count': int(conclusion.split()[1]), 'inputs': before, 'checksSha256': SHARED.sha(output / 'checks.txt'), 'compileSha256': SHARED.sha(output / 'compile.txt'), 'abiSha256': SHARED.sha(output / 'abi.json'), 'classes': {str(p.relative_to(compiled)): SHARED.sha(p) for p in sorted(compiled.rglob('*.class'))}}
    (output / 'evidence.json').write_text(json.dumps(evidence, indent=2), encoding='utf-8')
    print(conclusion)


if __name__ == '__main__': main()
