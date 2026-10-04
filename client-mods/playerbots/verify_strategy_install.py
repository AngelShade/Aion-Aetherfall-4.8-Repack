"""Read-only post-install continuity check, including settings and both receipts."""
import argparse,json
from pathlib import Path
import stage_companion_update as shared

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package',type=Path,required=True)
    parser.add_argument('--first-receipt',type=Path,required=True)
    parser.add_argument('--final-receipt',type=Path,required=True)
    parser.add_argument('--intermediate-receipt',type=Path)
    parser.add_argument('--before-inventory',type=Path,required=True)
    parser.add_argument('--after-inventory',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();server=shared.ROOT/'target-deploy/game-server'
    before=json.loads(args.before_inventory.read_text());after=json.loads(args.after_inventory.read_text())
    assert before['clientFiles']==after['clientFiles'],'Client files changed'
    assert before['serverJarSha256']==after['serverJarSha256'],'Base server JAR changed'
    assert before['serverLauncherSha256']==after['serverLauncherSha256'],'Launcher changed'
    assert before['deployedConfig']==after['deployedConfig'],'Deployed configuration changed'
    assert all(after['checks'].values()),'Installed mod preservation failed'
    receipts=[args.first_receipt.resolve()]
    if args.intermediate_receipt:receipts.append(args.intermediate_receipt.resolve())
    receipts.append(args.final_receipt.resolve())
    latest=None
    for receipt in receipts:
        evidence=json.loads((receipt/'manifest.json').read_text())
        if latest:
            prior={e['path']:e['installed'] for e in latest['files']}
            assert all(e['original']==prior[e['path']] for e in evidence['files']),'Receipt chain differs'
        assert json.loads((receipt/'installed.json').read_text())['mode']=='server stopped'
        for entry in evidence['files']:
            assert shared.sha(receipt/entry['path'])==entry['original'],'Backup original differs'
        def tree(root):return {p.relative_to(root).as_posix():shared.sha(p) for p in root.rglob('*') if p.is_file()}
        assert tree(receipt/'preferences-before')==tree(server/'config/playerbots'),'Settings/presets/archives changed'
        latest=evidence
    for entry in latest['files']:
        assert shared.sha(server/entry['path'])==entry['installed'],'Installed payload differs'
        assert shared.sha(args.package/entry['path'])==entry['installed'],'Reviewed package differs'
    assert shared.sha(server/'libs/game-server-4.8-SNAPSHOT.jar')==latest['baseJarSha256']
    report=dict(finalReceipt=str(receipts[-1].relative_to(server)),receiptsVerified=len(receipts),clientHashesPreserved=len(before['clientFiles']),
        modChecks=len(after['checks']),settingsFilesPreserved=len(tree(server/'config/playerbots')),
        baseJarPreserved=True,launcherPreserved=True,reviewedPayloadInstalled=True,
        acceptance='Offline/disk continuity only; GameServer startup/native/client validation remains pending')
    args.output.write_text(json.dumps(report,indent=2));print('OK:',json.dumps(report))
if __name__=='__main__':main()
