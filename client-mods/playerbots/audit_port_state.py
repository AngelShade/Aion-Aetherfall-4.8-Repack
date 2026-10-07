"""Read-only evidence for the 2026-10-04 upstream port review; never attaches a VM.

Checks the cached upstream hashes and effective installed methods against the
last compiled source snapshot. Output is evidence, not gameplay acceptance.
"""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path

from stage_companion_update import methods, method_name, receipt_paths, receipt_label

ROOT = Path(__file__).resolve().parents[2]
PREFIX = 'com.aionemu.gameserver.services.playerbot.'
SCOPES = {
    'PlayerBotSession': {'tick', 'snapshot', 'priority', 'recipient', 'hasActualBuff', 'equip', 'markClosing'},
    'PlayerBotSession$CastAction': {'isUseful','isPossible','prerequisites','execute'},
    'PlayerBotSession$ReachAction': {'isUseful'},
    'PlayerBotFormation': {'destination','close'},
    'PlayerBotFormationLayout': {'point','destination'},
    'PlayerBotNavigation': {'approach'},
    'PlayerBotCombatPosition': {'formationOrder','ranged','desired','allowSpellApproach','tooClose','point'},
    'PlayerBotSpacing': {'validate','values','configure','snapshot','attack','nativeDistance','formation','observe','canRetreat','retreat','close'},
    'PlayerBotCoordination': {'tankFacing','spread','away'},
    'PlayerBotOffense': {'finisher', 'refresh', 'useful', 'routine'},
    'PlayerBotSorcerer': {'applies','strategy','singleTarget','offensiveBoost','vulnerability','sameFamily','band','damage','support'},
    'PlayerBotCombatBuffs': {'useful'},
    'PlayerBotTrade': {'session','ready','request','participants','trading','guard','locked','rights','confirm','hold','equip','tick','closed','finishing','close'},
    'PlayerBotTradeStore': {'source','balance','currency','transfer','commit'},
    'PlayerBotClassCombat': {'useful', 'shouldBurst'},
    'PlayerBotQuestRoutes': {'choose', 'trigger', 'stillWanted', 'leash'},
    'PlayerBotQuests': {'choose', 'interact', 'hunt'},
    'PlayerBotQuestConversations': {'tick'},
    'PlayerBotQuestObjects': {'tick'},
    'PlayerBotQuestObjectives': {'choose','prepare','matches','job','pull','objectMatch','objectStarted','objectFinished','rewardAttempted','peers','valid','attempted','trigger'},
    'PlayerBotPartyBehavior': {'hunt'},
    'PlayerBotEngine': {'tick'},
}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classes', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    upstream = ROOT / 'third-party/playerbots'
    checked = []
    for line in (upstream / 'SHA256SUMS').read_text().splitlines():
        if not line.strip():
            continue
        expected, relative = line.split(None, 1)
        path = upstream / relative.strip().lstrip('*')
        actual = sha(path)
        assert actual == expected, f'Upstream reference changed: {relative}'
        checked.append({'path': relative, 'sha256': actual})
    server = ROOT / 'target-deploy/game-server'
    override = server / 'libs/playerbot-recruitment-fix.jar'
    base = server / 'libs/game-server-4.8-SNAPSHOT.jar'
    cp = str(override) + ';' + str(base)
    receipt_path = receipt_paths(server,'playerbots-recruitment-*/manifest.json')[-1]
    receipt = json.loads(receipt_path.read_text())
    assert sha(base) == receipt['baseJarSha256'], 'Base JAR differs from receipt'
    for entry in receipt['files']:
        assert sha(server / entry['path']) == entry['installed'], entry['path']
    assert b'-cp "libs/playerbot-recruitment-fix.jar;libs/*"' in (server / 'start.bat').read_bytes()
    args.output.mkdir(parents=True, exist_ok=True)
    evidence = []
    retained = []
    active_legacy_gate = False
    for short, wanted in SCOPES.items():
        name = PREFIX + short
        installed = methods(cp, name)
        if short == 'PlayerBotSession$CastAction':
            active_legacy_gate = any('PlayerBotClassCombat.useful' in line
                                    for key, value in installed.items()
                                    if method_name(key) == 'isUseful' for line in value)
        compiled = methods(args.classes, name)
        selected = {key: value for key, value in installed.items()
                    if method_name(key) in wanted or any(
                        method_name(key).startswith('lambda$' + parent + '$') for parent in wanted)}
        assert wanted <= {method_name(key) for key in selected}, f'Missing methods: {short}'
        for key, value in selected.items():
            if key not in compiled and method_name(key).startswith('lambda$'):
                # Bounded transplants preserve obsolete installed schema members.
                # Record them separately; do not confuse them with active source methods.
                retained.append({'class': name, 'method': key})
                continue
            assert compiled.get(key) == value, f'Effective/source snapshot mismatch: {name}: {key}'
            evidence.append({'class': name, 'method': key,
                             'sha256': hashlib.sha256('\n'.join(value).encode()).hexdigest()})
        dump = subprocess.check_output(['javap', '-c', '-p', '-s', '-cp', cp, name], text=True)
        (args.output / (short + '.javap.txt')).write_text(dump, encoding='utf-8')
    # Only pure scalar helpers are invoked. No actors, databases, IDs or world initialization.
    source = '''
public class PortGateCheck {
 public static void main(String[] args) throws Exception {
  var f=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotOffense")
   .getDeclaredMethod("finisher",int.class,double.class,long.class,long.class,boolean.class);
  var b=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotClassCombat")
   .getDeclaredMethod("shouldBurst",int.class,int.class,double.class,long.class);
  f.setAccessible(true); b.setAccessible(true);
  Object[][] rows={
   {"zero/no builder",0,100d,10000L,1000L,false,8d,false},
   {"four of five",4,100d,10000L,1000L,true,25d,false},
   {"partial/no builder",2,100d,10000L,1000L,false,12d,false},
   {"cast-aware expiry",2,100d,3000L,2000L,true,24d,false},
   {"full stack control",5,100d,10000L,1000L,true,25d,true},
   {"dying target control",2,24d,10000L,1000L,true,24d,true}};
  for(var r:rows) {
   double priority=(double)f.invoke(null,r[1],r[2],r[3],r[4],r[5]);
   boolean gate=(boolean)b.invoke(null,r[1],5,r[2],r[3]);
   if(priority!=(double)r[6] || gate!=(boolean)r[7]) throw new AssertionError(r[0]);
   System.out.println(r[0]+": priority="+priority+", legacy gate="+gate);
  }
 }
}
'''
    fixture = args.output / 'PortGateCheck.java'
    fixture.write_text(source, encoding='utf-8')
    subprocess.run(['javac', '-d', str(args.output), str(fixture)], check=True)
    result = subprocess.check_output(['java', '-Xverify:all', '-cp',
                                     str(args.output) + ';' + cp + ';' + str(server / 'libs/*'),
                                     'PortGateCheck'], text=True)
    (args.output / 'gate-output.txt').write_text(result, encoding='utf-8')
    report = {'upstreamPin': '037c01418b5d01506917a3db9b44fd56ac5f965c',
              'referenceHashes': checked, 'receipt': receipt_label(receipt_path,ROOT),
              'overrideSha256': sha(override), 'baseSha256': sha(base),
              'compiledSourceSnapshot': str(args.classes), 'methodFingerprints': evidence,
              'retainedInstalledOnlyLambdas': retained,
              'gateCases': 6, 'legacyScalarVetoes': 4,
              'activeLegacyRuneGate': active_legacy_gate,
              'confirmedContradictions': 4 if active_legacy_gate else 0,
              'limits': 'Read-only disk bytecode and scalar helpers; no native actor casts, runtime attach or client acceptance.'}
    (args.output / 'report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(f'PASS: {len(checked)} reference hashes, {len(evidence)} effective methods, six gate cases')
    print(result, end='')


if __name__ == '__main__':
    main()
