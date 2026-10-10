"""Change only the requested active companion quota with offline/hash/recovery guards."""
import argparse, json, re, sys
from pathlib import Path
from datetime import datetime

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'client-mods/playerbots'))
import stage_companion_update as shared
from remove_follow_recovery_offline import stopped


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--value', type=int, required=True)
    p.add_argument('--expected', type=int, required=True)
    p.add_argument('--roster', type=int, required=True)
    p.add_argument('--expected-roster', type=int, required=True)
    a = p.parse_args()
    if not 0 <= a.value <= 23:
        raise ValueError('Native alliance permits at most 23 controlled companions')
    if not a.value <= a.roster <= 2147483647:
        raise ValueError('Saved roster must accommodate the selected active limit')
    config = ROOT / 'target-deploy/game-server/config/main/playerbots.properties'
    pattern = rb'(?m)^(gameserver\.playerbots\.(max_per_owner|generated\.max_per_account)\s*=\s*)([0-9]+)([ \t]*\r?$)'
    before = config.read_bytes()
    matches = list(re.finditer(pattern, before))
    expected = {b'max_per_owner': a.expected, b'generated.max_per_account': a.expected_roster}
    values = {b'max_per_owner': a.value, b'generated.max_per_account': a.roster}
    if len(matches) != 2 or {m.group(2): int(m.group(3)) for m in matches} != expected:
        raise RuntimeError('Companion quota changed or is ambiguous; reread before configuring')
    stopped()
    receipt = shared.DEV_ROOT / 'archives/server/game-server/backups' / ('playerbots-active-limit-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    receipt.mkdir(parents=True, exist_ok=False)
    backup = receipt / 'playerbots.properties'
    backup.write_bytes(before)
    assert shared.sha(backup) == shared.sha(config)
    after = re.sub(pattern, lambda m: m.group(1) + str(values[m.group(2)]).encode() + m.group(4), before)
    # Verify literally every other setting/comment/byte stays unchanged.
    assert re.sub(pattern, b'<active-quota>', before) == re.sub(pattern, b'<active-quota>', after)
    stopped()
    assert config.read_bytes() == before, 'Configuration changed after backup'
    try:
        config.write_bytes(after)
        assert config.read_bytes() == after
    except Exception:
        config.write_bytes(before)
        raise
    (receipt / 'manifest.json').write_text(json.dumps(dict(feature='playerbots-active-limit',
        path=str(config), original=shared.sha(backup), installed=shared.sha(config),
        previousValue=a.expected, configuredValue=a.value, otherBytesPreserved=True,
        previousRoster=a.expected_roster, configuredRoster=a.roster,
        mode='offline configuration only; no component startup/attach'), indent=2))
    print('OK: active quota', a.expected, '->', a.value, '; saved roster', a.expected_roster, '->', a.roster, '; recovery:', receipt)


if __name__ == '__main__':
    main()
