"""Apply the requested field-combat placement preference only; retain every other live config byte."""
import argparse, importlib, json, re, shutil, sys
from pathlib import Path
from datetime import datetime

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'client-mods/playerbots'))
import stage_companion_update as shared
from remove_follow_recovery_offline import stopped
PATTERN = rb'(?m)^(gameserver\.playerbots\.population\.city_percent[ \t]*=[ \t]*)([0-9]+)([ \t]*\r?$)'

def field_bytes(before, expected):
    matches = list(re.finditer(PATTERN, before))
    assert len(matches) == 1 and int(matches[0].group(2)) == expected and 0 <= expected <= 100
    return re.sub(PATTERN, lambda m: m.group(1) + b'0' + m.group(3), before)

def validate(before, after, expected):
    assert after == field_bytes(before, expected), 'Only the requested city_percent=0 field preference may change'

def verified_receipt(server, archives, original_hash):
    assert server.resolve() == (ROOT/'target-deploy/game-server').resolve()
    config=server/'config/main/playerbots.properties'
    receipts=sorted(archives.glob('playerbots-field-placement-*/manifest.json'))
    if not receipts: return None
    path=receipts[-1]; m=json.loads(path.read_text()); original=path.parent/'playerbots.properties'
    if m['installed']!=shared.sha(config) or m['original']!=original_hash: return None
    assert m['feature']=='playerbots-field-placement' and Path(m['path']).resolve()==config.resolve()
    assert shared.sha(original)==m['original'] and m['configuredValue']==0
    validate(original.read_bytes(),config.read_bytes(),m['previousValue'])
    return dict(path='config/main/playerbots.properties',installed=m['installed'])

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--expected', type=int, required=True)
    a = p.parse_args()
    config = ROOT / 'target-deploy/game-server/config/main/playerbots.properties'
    before = config.read_bytes(); after = field_bytes(before, a.expected)
    validate(before, after, a.expected)
    stopped()
    receipt = shared.DEV_ROOT / 'archives/server/game-server/backups' / ('playerbots-field-placement-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    receipt.mkdir(parents=True, exist_ok=False)
    backup = receipt / 'playerbots.properties'; shutil.copy2(config, backup)
    assert shared.sha(backup) == shared.sha(config)
    stopped(); assert config.read_bytes() == before, 'Live settings changed during review'
    try:
        config.write_bytes(after); assert config.read_bytes() == after
    except Exception:
        shutil.copy2(backup, config); raise
    (receipt / 'manifest.json').write_text(json.dumps(dict(feature='playerbots-field-placement', path=str(config),
        original=shared.sha(backup), installed=shared.sha(config), previousValue=a.expected, configuredValue=0,
        otherBytesPreserved=True, mode='offline requested field placement only; no component startup/attach'), indent=2))
    print('OK: city_percent', a.expected, '-> 0; all other config bytes preserved; recovery:', receipt)

if __name__ == '__main__': main()
