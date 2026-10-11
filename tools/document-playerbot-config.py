"""Expose/document every compiled PlayerBot setting without changing effective live values."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
from datetime import datetime

ROOT = Path(__file__).resolve().parents[1]
DEV = Path('D:/Proiecte/Project Restructure/Aion Development Workspace')
SOURCE = ROOT / 'game-server/config/main/playerbots.properties'
FEATURE_SOURCE = ROOT / 'game-server/config/playerbots/features.properties'
JAVA = ROOT / 'game-server/src/com/aionemu/gameserver/configs/main/PlayerBotConfig.java'
SERVER = ROOT / 'target-deploy/game-server'
LIVE = SERVER / 'config/main/playerbots.properties'
JAR = SERVER / 'libs/game-server-4.8-SNAPSHOT.jar'
LINE = re.compile(r'^(gameserver\.playerbots\.[\w.]+)[ \t]*=[ \t]*([^\r\n]*)$', re.MULTILINE)

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def properties(text):
    pairs = [(m.group(1), m.group(2).strip()) for m in LINE.finditer(text)]
    assert len(dict(pairs)) == len(pairs), 'Duplicate bot configuration key'
    for line in text.splitlines():
        assert not line.strip() or line.lstrip().startswith('#') or LINE.fullmatch(line), 'Unrecognized configuration content: ' + line
    return dict(pairs)

def compiled_defaults():
    # Inspect bytes only: javap does not load or initialize the game/config class.
    output = subprocess.run(['javap', '-v', '-classpath', str(JAR), 'com.aionemu.gameserver.configs.main.PlayerBotConfig'],
        capture_output=True, text=True, check=True).stdout
    pairs = re.findall(r'key="([^"]+)"\s+defaultValue="([^"]*)"', output)
    result = dict(pairs)
    assert result and len(result) == len(pairs), 'Compiled setting catalogue is empty or ambiguous'
    return result, output

def complete(template, values):
    result = LINE.sub(lambda m: m.group(1) + ' =' + (' ' + values[m.group(1)] if values[m.group(1)] else ''), template)
    assert properties(result) == values
    lines = result.splitlines()
    for index, line in enumerate(lines):
        if LINE.fullmatch(line):
            assert index > 0 and lines[index-1].startswith('#'), 'Missing inline setting explanation'
    return result

def source_catalogue():
    main = properties(SOURCE.read_text(encoding='utf-8'))
    features = properties(FEATURE_SOURCE.read_text(encoding='utf-8'))
    assert not set(main) & set(features), 'Playerbots settings must have one documented home'
    complete(SOURCE.read_text(encoding='utf-8'), main)
    complete(FEATURE_SOURCE.read_text(encoding='utf-8'), features)
    return main | features

def main_defaults(defaults):
    # This legacy documentation action owns only the existing main file.
    # The normal builder/delivery adds features.properties without replacing owner edits.
    return {key: value for key, value in defaults.items() if key in properties(SOURCE.read_text(encoding='utf-8'))}

def unchanged_effective(before, after, defaults):
    old = properties(before)
    new = properties(after)
    assert set(old) <= set(defaults) and set(new) == set(defaults), 'Unknown or missing configuration keys'
    assert new == defaults | old, 'Configuration documentation changed an effective setting'
    return new

def effective_with_repair(defaults, before, evidence=None):
    result = defaults | before
    if evidence:
        path = Path(evidence['path'])
        assert sha(path) == evidence['sha256'], 'Formatting-repair evidence changed'
        bug = json.loads(path.read_text())
        assert bug['feature'] == 'playerbots-config-documentation' and Path(bug['live']) == LIVE
        original = path.parent / 'playerbots.properties'
        assert sha(original) == bug['original']
        old = properties(original.read_text())
        for key in evidence['keys']:
            assert key in {'gameserver.playerbots.allowed_instance_maps', 'gameserver.playerbots.population.maps'}
            assert old.get(key, '') == '' and before[key] == bug['effective'][key] and before[key].startswith('#')
            result[key] = ''
    return result

def verified_receipt(server, archives):
    """Recognize only this fixed config's backed-up, value-preserving documentation receipt."""
    assert server.resolve() == SERVER.resolve()
    paths = sorted(archives.glob('playerbots-config-docs-*/manifest.json'))
    assert paths, 'Documented configuration has no installed receipt'
    path = paths[-1]
    m = json.loads(path.read_text())
    assert m['feature'] == 'playerbots-config-documentation' and Path(m['live']).resolve() == LIVE.resolve()
    assert m['jarSha256'] == sha(JAR) and m['installed'] == m['stagedSha256'] == sha(LIVE)
    original = path.parent / 'playerbots.properties'
    assert sha(original) == m['original']
    defaults, _ = compiled_defaults()
    defaults = main_defaults(defaults)
    assert properties(LIVE.read_text()) == m['effective'] == effective_with_repair(defaults, properties(original.read_text()), m.get('formattingRepair'))
    return dict(path='config/main/playerbots.properties', original=m['original'], installed=m['installed'],
        review=str(path), reviewSha256=sha(path), recovery=str(original), documentationOnly=True)

def prepare(repair_review=None):
    template = SOURCE.read_text(encoding='utf-8')
    source_values = properties(template)
    keys = re.findall(r'@Property\(key = "([^"]+)"', JAVA.read_text())
    defaults, output = compiled_defaults()
    assert len(keys) == len(set(keys)) == len(defaults) and set(keys) == set(source_catalogue()) == set(defaults)
    defaults = main_defaults(defaults)
    before = properties(LIVE.read_text(encoding='utf-8'))
    assert set(before) <= set(defaults)
    repair = None
    if repair_review:
        repair = dict(path=str(repair_review), sha256=sha(repair_review), keys=['gameserver.playerbots.allowed_instance_maps'])
    effective = effective_with_repair(defaults, before, repair)
    planned = complete(template, effective)
    assert properties(planned) == effective
    review = DEV / 'staging/output' / ('playerbots-config-docs-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    review.mkdir(parents=True, exist_ok=False)
    target = review / 'playerbots.properties'
    target.write_bytes(planned.replace('\n', '\r\n').encode('utf-8'))
    (review / 'compiled-defaults.txt').write_text(output)
    manifest = dict(feature='playerbots-config-documentation', source=str(SOURCE), live=str(LIVE),
        sourceSha256=sha(SOURCE), javaSha256=sha(JAVA), jarSha256=sha(JAR), original=sha(LIVE),
        staged=str(target), stagedSha256=sha(target), preservedExplicit=before,
        addedCompiledDefaults={k: v for k, v in defaults.items() if k not in before}, effective=effective,
        settings=len(defaults), explanationCoverage=len(source_values), effectiveValuesUnchanged=repair is None, originalUserValuesPreserved=True, formattingRepair=repair)
    (review / 'manifest.json').write_text(json.dumps(manifest, indent=2))
    print('OK: all', len(defaults), 'settings documented;', len(before), 'explicit values retained;', len(defaults)-len(before), 'missing compiled defaults exposed')
    print('Review:', review)

def install(review):
    sys.path.insert(0, str(ROOT / 'client-mods/playerbots'))
    from remove_follow_recovery_offline import stopped
    m = json.loads((review / 'manifest.json').read_text())
    assert m['feature'] == 'playerbots-config-documentation' and Path(m['live']) == LIVE and Path(m['source']) == SOURCE
    stage = Path(m['staged'])
    assert stage.resolve().parent == review.resolve()
    defaults, _ = compiled_defaults()
    defaults = main_defaults(defaults)
    assert properties(stage.read_text()) == m['effective'] == effective_with_repair(defaults, m['preservedExplicit'], m.get('formattingRepair'))
    protected = {path: sha(path) for path in SERVER.joinpath('libs').glob('*.jar')}
    def guard():
        stopped()
        assert sha(LIVE) == m['original'] and properties(LIVE.read_text()) == m['preservedExplicit']
        assert sha(SOURCE) == m['sourceSha256'] and sha(JAVA) == m['javaSha256'] and sha(JAR) == m['jarSha256']
        assert sha(stage) == m['stagedSha256'] and all(sha(p) == h for p, h in protected.items())
    guard()
    backup = DEV / 'archives/server/game-server/backups' / ('playerbots-config-docs-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    backup.mkdir(parents=True, exist_ok=False)
    shutil.copy2(LIVE, backup / 'playerbots.properties')
    shutil.copy2(review / 'manifest.json', backup / 'review.json')
    assert sha(backup / 'playerbots.properties') == m['original']
    guard()
    try:
        shutil.copy2(stage, LIVE)
        assert sha(LIVE) == m['stagedSha256'] and properties(LIVE.read_text()) == m['effective']
        assert all(sha(p) == h for p, h in protected.items())
    except Exception:
        shutil.copy2(backup / 'playerbots.properties', LIVE)
        assert sha(LIVE) == m['original']
        raise
    receipt = m | dict(installed=sha(LIVE), review=str(review / 'manifest.json'), installedAt=datetime.now().isoformat(),
        mode='offline configuration documentation only; no Java/JAR replacement or component startup/attach')
    (backup / 'manifest.json').write_text(json.dumps(receipt, indent=2))
    print('OK: documented configuration installed; all effective values unchanged; recovery:', backup)

def rollback(receipt):
    sys.path.insert(0, str(ROOT / 'client-mods/playerbots'))
    from remove_follow_recovery_offline import stopped
    m = json.loads((receipt / 'manifest.json').read_text())
    assert m['feature'] == 'playerbots-config-documentation' and Path(m['live']) == LIVE
    stopped()
    assert sha(LIVE) == m['installed'] and sha(receipt / 'playerbots.properties') == m['original']
    shutil.copy2(receipt / 'playerbots.properties', LIVE)
    assert sha(LIVE) == m['original']
    m['rolledBack'] = True
    (receipt / 'manifest.json').write_text(json.dumps(m, indent=2))
    print('OK: exact original configuration restored; failed documentation receipt marked rolled back')

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--review', type=Path)
    p.add_argument('--install', action='store_true')
    p.add_argument('--rollback', type=Path)
    p.add_argument('--repair-review', type=Path)
    args = p.parse_args()
    if args.rollback:
        assert args.review is None and not args.install
        rollback(args.rollback)
    elif args.install:
        assert args.review, 'Prepare an explicit review first'
        install(args.review)
    else:
        assert args.review is None
        prepare(args.repair_review)
