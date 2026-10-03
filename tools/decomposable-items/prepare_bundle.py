"""Prepare a cohesive decomposable rework; never install into the running server.

Compiles only the upstream rework and its Season Pass API integration against
the deployed libraries. Unrelated JAR entries are retained byte for byte.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PREFIX = 'com/aionemu/gameserver/'
SOURCES = ['dataholders/DataManager', 'dataholders/DecomposableItemsData', 'dataholders/StaticData',
           'model/templates/item/DecomposableItemInfo', 'model/templates/item/DecomposableSet',
           'model/templates/item/DecomposedBundle', 'model/templates/item/DecomposedItem',
           'model/templates/item/DecomposedReward', 'model/templates/item/ReturnLocList',
           'model/templates/item/actions/DecomposeAction', 'network/aion/clientpackets/CM_SELECT_DECOMPOSABLE',
           'network/aion/serverpackets/SM_FIRST_SHOW_DECOMPOSABLE', 'network/aion/serverpackets/SM_SECONDARY_SHOW_DECOMPOSABLE',
           'network/aion/serverpackets/SM_SYSTEM_MESSAGE', 'services/SeasonPassService']
RETIRED = ['ExtractedItemsCollection', 'RandomItem', 'RandomType', 'ResultedItem', 'ResultedItemsCollection']
DATA = ['data/static_data/decomposable_items/' + name for name in
        ['decomposable_items.xml', 'custom_decomposable_items.xml', 'local_decomposable_items.xml', 'decomposable_items.xsd']]
DATA += ['data/static_data/items/item_templates.xml', 'data/handlers/admincommands/Reload.java', 'data/handlers/playercommands/Decompose.java']


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    out = args.output.resolve()
    if out.exists():
        raise ValueError('Use a fresh output directory')
    if not out.is_relative_to(ROOT / 'output'):
        raise ValueError('Prepared bundle must stay under the workspace output directory')
    deployment = ROOT / 'target-deploy/game-server'
    base = deployment / 'libs/game-server-4.8-SNAPSHOT.jar'
    original_hash = digest(base)
    out.mkdir(parents=True)
    classes = out / 'classes'
    paths = [ROOT / 'game-server/src' / (PREFIX + name + '.java') for name in SOURCES]
    subprocess.run(['javac', '--release', '25', '-encoding', 'UTF-8', '-sourcepath', '', '-cp',
                    str(deployment / 'libs/*'), '-d', str(classes), *map(str, paths)], check=True)
    scopes = [PREFIX + name for name in SOURCES]
    replacements = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')
                    if any(p.relative_to(classes).as_posix() == scope + '.class'
                           or p.relative_to(classes).as_posix().startswith(scope + '$') for scope in scopes)}
    # The only changes to this shared service are its two decomposable API consumers.
    sys.path.insert(0, str(ROOT / 'client-mods/season-pass'))
    from incremental_server import methods
    service = PREFIX + 'services/SeasonPassService'
    old, new = methods(base, service), methods(classes, service)
    for method in old.keys() | new.keys():
        if old.get(method) == new.get(method):
            continue
        if not any(name in method for name in ['validateRewardItem(', 'boxPreview(', 'lambda$validateRewardItem$', 'lambda$boxPreview$']):
            raise ValueError('Unrelated deployed Season Pass method differs: ' + method)
    target = out / 'libs/game-server-4.8-SNAPSHOT.jar'
    target.parent.mkdir()
    def retired(name):
        return any(name == PREFIX + 'model/templates/item/' + stem + '.class'
                   or name.startswith(PREFIX + 'model/templates/item/' + stem + '$') for stem in RETIRED)
    with zipfile.ZipFile(base) as original, zipfile.ZipFile(target, 'w') as merged:
        merged.comment = original.comment
        names = set(original.namelist())
        for info in original.infolist():
            if not retired(info.filename):
                merged.writestr(copy.copy(info), replacements.get(info.filename, original.read(info.filename)))
        for name in sorted(replacements.keys() - names):
            merged.writestr(name, replacements[name], compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(base) as original, zipfile.ZipFile(target) as merged:
        assert merged.testzip() is None
        for name in original.namelist():
            if name not in replacements and not retired(name):
                assert merged.read(name) == original.read(name), name
        for name, data in replacements.items():
            assert merged.read(name) == data, name
        assert not any(retired(name) for name in merged.namelist())
    for relative in DATA:
        dest = out / relative
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / 'game-server' / relative, dest)
    # Preserve the deployed import registry's unrelated quest/client configuration.
    registry = deployment / 'data/static_data/static_data.xml'
    text = registry.read_text(encoding='utf-8')
    old_import = '<import file="decomposable_items/decomposable_items.xml" />'
    new_import = '<import file="decomposable_items" singleRootTag="true" />'
    if old_import in text:
        assert text.count(old_import) == 1
        text = text.replace(old_import, new_import)
    else:
        assert text.count(new_import) == 1
    (out / 'data/static_data/static_data.xml').write_text(text, encoding='utf-8')
    files = []
    for relative in ['libs/game-server-4.8-SNAPSHOT.jar', *DATA, 'data/static_data/static_data.xml']:
        deployed = deployment / relative
        files.append({'path': relative, 'sha256': digest(out / relative),
                      'original': digest(deployed) if deployed.exists() else None})
    assert digest(base) == original_hash, 'Deployed JAR changed while staging; regenerate'
    dependencies = [{'path': 'data/static_data/items/item_templates.xsd',
                     'sha256': digest(deployment / 'data/static_data/items/item_templates.xsd')}]
    manifest = {'feature': 'decomposable-pr200-backport', 'deployment': str(deployment),
                'upstream_commit': json.loads(Path(__file__).with_name('sources.json').read_text(encoding='utf-8'))['upstream']['commit'],
                'files': files, 'dependencies': dependencies, 'classes': sorted(replacements),
                'removed_legacy_classes': RETIRED, 'source_verification': 'docs/DECOMPOSABLE_PR_REVIEW.md'}
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(f'OK: prepared {len(files)} cohesive files and {len(replacements)} class replacements; unrelated JAR entries preserved. Deployment untouched.')


if __name__ == '__main__':
    main()
