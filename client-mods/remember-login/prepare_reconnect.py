"""Stage the evidence-based Reset hook relocation from the cumulative client."""
import argparse,json
from pathlib import Path
from patch_reset import relocate,HOOK
from prepare_reset import DEV_ROOT,sha
from reconnect_graphics import prepare as prepare_graphics

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--inventory',type=Path,required=True)
    a=p.parse_args();root=a.client.resolve();out=a.output.resolve()
    if not out.is_relative_to(DEV_ROOT/'staging/output') or out.exists():raise ValueError('Use fresh external staging directory')
    inventory=json.loads(a.inventory.read_text())
    client_checks={'all_six_native_browser_routes','existing_menu_actions','native_wardrobe_entries','base_and_english_layouts',
                   'remember_login_english','season_pass_ticket_both_huds','native_window_queue_batching',
                   'remember_login_and_speech_hooks','native_icon_index_matches_items','graphics_package_and_recovery',
                   'cursor_tracking_and_recovery','browser_probe_remains_removed','playerbot_party_bar',
                   'remember_login_reset_and_notice_repair'}
    if Path(inventory['clientRoot']).resolve()!=root or not all(inventory['checks'].get(k) for k in client_checks):
        raise ValueError('Every current client inventory check must pass')
    for e in inventory['clientFiles']:
        if sha(root/e['path'])!=e['sha256']:raise ValueError('Client changed since inventory: '+e['path'])
    receipt=json.loads((root/'RememberLogin-backups/20261003-190013-967/manifest.json').read_text())
    source=(root/'bin64/Game.dll').read_bytes();updated,hook=relocate(source,receipt)
    out.mkdir(parents=True);updated,graphics=prepare_graphics(root,out,updated,hook['iat'])
    # Keep the production extension byte-identical; this crash was in Game.dll.
    for name,data in [('bin64/Game.dll',updated),('bin64/AionRememberLogin.dll',(root/'bin64/AionRememberLogin.dll').read_bytes())]:
        f=out/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
    entries=[dict(path=f.relative_to(out).as_posix(),original=sha(root/f.relative_to(out)),staged=sha(f)) for f in sorted(out.rglob('*')) if f.is_file()]
    changes={e['path'].lower() for e in entries}
    preserved=[dict(path=e['path'],sha256=e['sha256']) for e in inventory['clientFiles'] if e['path'].lower() not in changes]
    m=dict(feature='remember-login-reconnect-v3',clientRoot=str(root),graphicsCompatibility=graphics,hook=hook,
           files=entries,preservedFiles=preserved,legacyHook=HOOK,
           status='staged; AFK reconnect and ordinary logout/login acceptance pending',
           faultEvidence=dict(exception='0x80000004',rva='0x144ec36',runtimeStorage='0x144ec00'),
           baselineClientChecks={k:inventory['checks'][k] for k in sorted(client_checks)},
           unrelatedServerChecks={k:v for k,v in inventory['checks'].items() if k not in client_checks})
    (out/'manifest.json').write_text(json.dumps(m,indent=2))
    print('OK: staged Reset relocation to dedicated .rreturn section;',len(entries),'transaction files;',len(preserved),'preserved hashes')
if __name__=='__main__':main()
