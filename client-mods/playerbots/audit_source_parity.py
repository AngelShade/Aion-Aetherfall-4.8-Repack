"""Compare source-built methods with the current cumulative deployment, without running server classes."""
import argparse
import concurrent.futures
import json
import zipfile
from pathlib import Path
import stage_companion_update as shared


def audit(classes, output):
    shared.validate_output(output)
    installed = shared.ROOT / 'target-deploy/game-server/libs/playerbot-recruitment-fix.jar'
    with zipfile.ZipFile(installed) as archive:
        names = sorted(n[:-6] for n in archive.namelist() if n.endswith('.class'))

    def compare(name):
        if not (classes / (name + '.class')).exists():
            return dict(className=name, missingSourceClass=True)
        before = shared.methods(installed, name)
        source = shared.methods(classes, name)
        changed = [k for k in before.keys() & source.keys() if before[k] != source[k]]
        return dict(className=name, changedMethods=sorted(changed),
                    installedOnlyMethods=sorted(before.keys() - source.keys()),
                    sourceOnlyMethods=sorted(source.keys() - before.keys()))

    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        comparisons = list(pool.map(compare, names))
    differences = [r for r in comparisons if r.get('missingSourceClass') or
                   r['changedMethods'] or r['installedOnlyMethods'] or r['sourceOnlyMethods']]
    report = dict(installedSha256=shared.sha(installed), sourceClasses=str(classes),
                  comparedClasses=len(names), matchingMethodClasses=len(names)-len(differences),
                  differences=differences, methodParity=not differences, deploymentSafe=False,
                  limitation='Method comparison only; schemas, data, scripts and client mods also need reconciliation before replacing the full runtime.')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(f'OK: compared {len(names)} deployed classes; {len(differences)} classes differ from source build; report: {output}')
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classes', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    audit(args.classes.resolve(), args.output.resolve())
